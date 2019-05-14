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
package org.glowroot.instrumentation.executor;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class ExecutorIT {

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
        IncomingSpan incomingSpan = container.execute(DoExecuteRunnable.class);
        // then
        checkServerSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureExecuteFutureTask() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteFutureTask.class);
        // then
        checkServerSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureSubmitCallable() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoSubmitCallable.class);
        // then
        checkServerSpan(incomingSpan, false, true);
    }

    @Test
    public void shouldCaptureSubmitRunnableAndCallable() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoSubmitRunnableAndCallable.class);
        // then
        checkServerSpan(incomingSpan, false, true);
    }

    @Test
    public void shouldNotCaptureSpanForEmptyAuxThread() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoSimpleSubmitRunnableWork.class);

        // then
        assertThat(incomingSpan.auxThreadTimers()).hasSize(3);
        for (IncomingSpan.Timer auxThreadTimer : incomingSpan.auxThreadTimers()) {
            assertThat(auxThreadTimer.name()).isEqualTo("auxiliary thread");
            assertThat(auxThreadTimer.count()).isEqualTo(1);
            assertThat(auxThreadTimer.totalNanos()).isGreaterThan(MILLISECONDS.toNanos(150));
            assertThat(auxThreadTimer.childTimers()).isEmpty();
        }
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldNotCaptureAlreadyCompletedFutureGet() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(CallFutureGetOnAlreadyCompletedFuture.class);

        // then
        assertThat(incomingSpan.mainThreadTimer().childTimers()).isEmpty();
    }

    @Test
    public void shouldCaptureNestedFutureGet() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(CallFutureGetOnNestedFuture.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureInvokeAll() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoInvokeAll.class);
        // then
        checkServerSpan(incomingSpan, false, true);
    }

    @Test
    public void shouldCaptureInvokeAllWithTimeout() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoInvokeAllWithTimeout.class);
        // then
        checkServerSpan(incomingSpan, false, true);
    }

    @Test
    public void shouldCaptureInvokeAny() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoInvokeAny.class);
        // then
        checkServerSpan(incomingSpan, true, false);
    }

    @Test
    public void shouldCaptureInvokeAnyWithTimeout() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoInvokeAnyWithTimeout.class);
        // then
        checkServerSpan(incomingSpan, true, false);
    }

    @Test
    public void shouldCaptureNestedExecute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoNestedExecuteRunnable.class);
        // then
        checkServerSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureNestedSubmit() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoNestedSubmitCallable.class);
        // then
        checkServerSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureDelegatingExecutor() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoDelegatingExecutor.class);
        // then
        checkServerSpan(incomingSpan, false, false);
    }

    private static void checkServerSpan(IncomingSpan incomingSpan, boolean isAny,
            boolean withFuture) {
        if (withFuture) {
            assertThat(incomingSpan.mainThreadTimer().childTimers().size()).isBetween(1, 3);
            for (IncomingSpan.Timer childTimer : incomingSpan.mainThreadTimer().childTimers()) {
                assertThat(childTimer.name()).isEqualTo("wait on future");
                assertThat(childTimer.count()).isEqualTo(1);
                assertThat(childTimer.childTimers()).isEmpty();
            }
        }
        if (isAny) {
            assertThat(incomingSpan.auxThreadTimers().size()).isBetween(1, 3);
        } else {
            assertThat(incomingSpan.auxThreadTimers()).hasSize(3);
        }
        boolean foundOneComplete = false;
        for (IncomingSpan.Timer auxThreadTimer : incomingSpan.auxThreadTimers()) {
            assertThat(auxThreadTimer.name()).isEqualTo("auxiliary thread");
            assertThat(auxThreadTimer.count()).isEqualTo(1);
            long totalNanos = auxThreadTimer.totalNanos();
            if (totalNanos != -1) {
                assertThat(totalNanos).isGreaterThan(MILLISECONDS.toNanos(50));
                foundOneComplete = true;
            }
            assertThat(auxThreadTimer.childTimers()).hasSize(1);
            IncomingSpan.Timer childTimer = auxThreadTimer.childTimers().get(0);
            assertThat(childTimer.name()).isEqualTo("test local span");
            assertThat(childTimer.count()).isEqualTo(1);
        }
        assertThat(foundOneComplete).isTrue();

        List<Span> spans = incomingSpan.childSpans();
        if (isAny) {
            assertThat(spans.size()).isBetween(1, 3);
        } else {
            assertThat(spans).hasSize(3);
        }
        for (Span span : spans) {
            assertSingleLocalSpanMessage(span).isEqualTo("test local span");
        }
    }

    private static ExecutorService createExecutorService() {
        return Executors.newCachedThreadPool();
    }

    public static class DoExecuteRunnable implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            final CountDownLatch latch = new CountDownLatch(3);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            });
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            });
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            });
            latch.await();
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    public static class DoExecuteFutureTask implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            final CountDownLatch latch = new CountDownLatch(3);
            executor.execute(new FutureTask<Void>(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                    return null;
                }
            }));
            executor.submit(new FutureTask<Void>(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                    return null;
                }
            }));
            executor.submit(new FutureTask<Void>(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                    return null;
                }
            }));
            latch.await();
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    public static class DoSubmitCallable implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            Future<Void> future1 = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            Future<Void> future2 = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            Future<Void> future3 = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            future1.get();
            future2.get();
            future3.get();
        }
    }

    public static class DoSubmitRunnableAndCallable implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            Future<Void> future1 = executor.submit((Callable<Void>) new RunnableAndCallableWork());
            Future<Void> future2 = executor.submit((Callable<Void>) new RunnableAndCallableWork());
            Future<Void> future3 = executor.submit((Callable<Void>) new RunnableAndCallableWork());
            future1.get();
            future2.get();
            future3.get();
        }
    }

    public static class DoSimpleSubmitRunnableWork implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            Future<?> future1 = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        MILLISECONDS.sleep(200);
                    } catch (InterruptedException e) {
                    }
                }
            });
            Future<?> future2 = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        MILLISECONDS.sleep(200);
                    } catch (InterruptedException e) {
                    }
                }
            });
            Future<?> future3 = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        MILLISECONDS.sleep(200);
                    } catch (InterruptedException e) {
                    }
                }
            });
            future1.get();
            future2.get();
            future3.get();
        }
    }

    public static class CallFutureGetOnAlreadyCompletedFuture
            implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            Future<Void> future = executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    return null;
                }
            });
            while (!future.isDone()) {
                MILLISECONDS.sleep(1);
            }
            future.get();
        }
    }

    public static class CallFutureGetOnNestedFuture implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            final ExecutorService executor = createExecutorService();
            Future<Void> future = executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    TestSpans.createLocalSpan(100);
                    Future<Void> future = executor.submit(new Callable<Void>() {
                        @Override
                        public Void call() {
                            TestSpans.createLocalSpan(100);
                            return null;
                        }
                    });
                    future.get();
                    return null;
                }
            });
            future.get();
        }
    }

    public static class DoInvokeAll implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            List<Callable<Void>> callables = Lists.newArrayList();
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            for (Future<Void> future : executor.invokeAll(callables)) {
                future.get();
            }
        }
    }

    public static class DoInvokeAllWithTimeout implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            List<Callable<Void>> callables = Lists.newArrayList();
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            for (Future<Void> future : executor.invokeAll(callables, 10, SECONDS)) {
                future.get();
            }
        }
    }

    public static class DoInvokeAny implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            List<Callable<Void>> callables = Lists.newArrayList();
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            executor.invokeAny(callables);
        }
    }

    public static class DoInvokeAnyWithTimeout implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            List<Callable<Void>> callables = Lists.newArrayList();
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    return null;
                }
            });
            executor.invokeAny(callables, 10, SECONDS);
        }
    }

    public static class DoNestedExecuteRunnable implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            MoreExecutors.directExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = createExecutorService();
                    final CountDownLatch latch = new CountDownLatch(3);
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            TestSpans.createLocalSpan(100);
                            latch.countDown();
                        }
                    });
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            TestSpans.createLocalSpan(100);
                            latch.countDown();
                        }
                    });
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            TestSpans.createLocalSpan(100);
                            latch.countDown();
                        }
                    });
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    executor.shutdown();
                    try {
                        executor.awaitTermination(10, SECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    public static class DoNestedSubmitCallable implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            MoreExecutors.newDirectExecutorService().submit(new Callable<Void>() {
                @Override
                public Void call() throws InterruptedException {
                    ExecutorService executor = createExecutorService();
                    final CountDownLatch latch = new CountDownLatch(3);
                    executor.submit(new Callable<Void>() {
                        @Override
                        public Void call() {
                            TestSpans.createLocalSpan(100);
                            latch.countDown();
                            return null;
                        }
                    });
                    executor.submit(new Callable<Void>() {
                        @Override
                        public Void call() {
                            TestSpans.createLocalSpan(100);
                            latch.countDown();
                            return null;
                        }
                    });
                    executor.submit(new Callable<Void>() {
                        @Override
                        public Void call() {
                            TestSpans.createLocalSpan(100);
                            latch.countDown();
                            return null;
                        }
                    });
                    latch.await();
                    executor.shutdown();
                    executor.awaitTermination(10, SECONDS);
                    return null;
                }
            });
        }
    }

    public static class DoDelegatingExecutor implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ExecutorService executor = createExecutorService();
            DelegatingExecutor delegatingExecutor = new DelegatingExecutor(executor);
            final CountDownLatch latch = new CountDownLatch(3);
            delegatingExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            });
            delegatingExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            });
            delegatingExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            });
            latch.await();
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    private static class RunnableAndCallableWork implements Runnable, Callable<Void> {

        @Override
        public Void call() {
            TestSpans.createLocalSpan(100);
            return null;
        }

        @Override
        public void run() {
            TestSpans.createLocalSpan(100);
        }
    }

    private static class DelegatingExecutor implements Executor {

        private final Executor executor;

        private DelegatingExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public void execute(Runnable command) {
            executor.execute(new DelegatingRunnable(command));
        }
    }

    private static class DelegatingRunnable implements Runnable {

        private final Runnable runnable;

        private DelegatingRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            runnable.run();
        }
    }
}
