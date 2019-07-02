/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.instrumentation.spring;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncControllerIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // javaagent is required for Executor.execute() weaving
        container = JavaagentContainer.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
    }

    @Test
    public void shouldCaptureCallableAsyncController() throws Exception {
        shouldCaptureCallableAsyncController("");
    }

    @Test
    public void shouldCaptureDeferredResultAsyncController() throws Exception {
        shouldCaptureDeferredResultAsyncController("");
    }

    @Test
    public void shouldCaptureCallableAsyncControllerWithContextPath() throws Exception {
        shouldCaptureCallableAsyncController("/zzz");
    }

    @Test
    public void shouldCaptureDeferredResultAsyncControllerWithContextPath() throws Exception {
        shouldCaptureDeferredResultAsyncController("/zzz");
    }

    private void shouldCaptureCallableAsyncController(String contextPath) throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", contextPath, "/async");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET " + contextPath + "/async");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("spring controller: org.glowroot.instrumentation"
                + ".spring.AsyncControllerIT$CallableAsyncController.test()");

        Iterator<Span> j = localSpan.childSpans().iterator();

        localSpan = (LocalSpan) j.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(j.hasNext()).isFalse();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    private void shouldCaptureDeferredResultAsyncController(String contextPath) throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", contextPath, "/async2");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET " + contextPath + "/async2");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("spring controller: org.glowroot.instrumentation"
                + ".spring.AsyncControllerIT$DeferredResultAsyncController.test()");

        Iterator<Span> j = localSpan.childSpans().iterator();

        localSpan = (LocalSpan) j.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) j.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(j.hasNext()).isFalse();

        assertThat(i.hasNext()).isFalse();
    }

    @Controller
    public static class CallableAsyncController {

        @RequestMapping("async")
        public @ResponseBody Callable<String> test() throws InterruptedException {
            TestSpans.createLocalSpan();
            return new Callable<String>() {
                @Override
                public String call() throws Exception {
                    TestSpans.createLocalSpan();
                    return "async world";
                }
            };
        }
    }

    @Controller
    public static class DeferredResultAsyncController {

        @RequestMapping("async2")
        public @ResponseBody DeferredResult<String> test() throws InterruptedException {
            TestSpans.createLocalSpan();
            final DeferredResult<String> result = new DeferredResult<String>();
            final ExecutorService executor = Executors.newCachedThreadPool();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan();
                    result.setResult("async2 world");
                    executor.shutdown();
                }
            });
            return result;
        }
    }
}
