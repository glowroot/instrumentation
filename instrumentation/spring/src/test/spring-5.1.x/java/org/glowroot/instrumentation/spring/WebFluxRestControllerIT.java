/*
 * Copyright 2019 the original author or authors.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class WebFluxRestControllerIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
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
    public void shouldCaptureWebFlux() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "", "/webflux");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /webflux");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("spring controller: org.glowroot.instrumentation"
                + ".spring.WebFluxRestControllerIT$TestRestController.webflux()");

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

    @RestController
    public static class TestRestController {

        @RequestMapping("webflux")
        public Mono<String> webflux() {
            TestSpans.createLocalSpan();
            CompletableFuture<String> completableFuture = new CompletableFuture<>();
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                    TestSpans.createLocalSpan();
                    completableFuture.complete("hello");
                }
            });
            return Mono.fromFuture(completableFuture);
        }
    }
}
