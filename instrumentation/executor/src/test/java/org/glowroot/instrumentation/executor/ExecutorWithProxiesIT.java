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
package org.glowroot.instrumentation.executor;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class ExecutorWithProxiesIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // tests only work with javaagent container because they need to weave bootstrap classes
        // that implement Executor and ExecutorService
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
    public void shouldCaptureExecute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteRunnableWithProxy.class);

        // then
        assertThat(incomingSpan.auxThreadTimers()).hasSize(1);
        IncomingSpan.Timer auxThreadTimer = incomingSpan.auxThreadTimers().get(0);
        assertThat(auxThreadTimer.name()).isEqualTo("auxiliary thread");
        assertThat(auxThreadTimer.count()).isEqualTo(1);
        // should be 100ms, but margin of error, esp. in travis builds is high
        assertThat(auxThreadTimer.totalNanos()).isGreaterThan(MILLISECONDS.toNanos(50));
        assertThat(auxThreadTimer.childTimers()).hasSize(1);
        IncomingSpan.Timer childTimer = auxThreadTimer.childTimers().get(0);
        assertThat(childTimer.name()).isEqualTo("test local span");
        assertThat(childTimer.count()).isEqualTo(1);

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    private static ExecutorService createExecutorService() {
        return Executors.newCachedThreadPool();
    }

    public static class DoExecuteRunnableWithProxy implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            final CountDownLatch latch = new CountDownLatch(1);
            executor.execute(wrap(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            }));
            latch.await();
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }

        private static Runnable wrap(Runnable runnable) {
            return (Runnable) Proxy.newProxyInstance(
                    DoExecuteRunnableWithProxy.class.getClassLoader(), new Class[] {Runnable.class},
                    new DelegatingInvocationHandler(runnable));
        }
    }

    private static class DelegatingInvocationHandler implements InvocationHandler {

        private final Runnable delegate;

        private DelegatingInvocationHandler(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return method.invoke(delegate, args);
        }
    }
}
