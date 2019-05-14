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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class ExecutorWithLambdasIT {

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
        IncomingSpan incomingSpan = container.execute(DoExecuteRunnableWithLambda.class);
        // then
        checkIncomingSpan(incomingSpan);
    }

    @Test
    public void shouldCaptureNestedExecute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoNestedExecuteRunnableWithLambda.class);
        // then
        checkIncomingSpan(incomingSpan);
    }

    private static void checkIncomingSpan(IncomingSpan incomingSpan) {
        assertThat(incomingSpan.auxThreadTimers()).hasSize(3);
        for (IncomingSpan.Timer auxThreadTimer : incomingSpan.auxThreadTimers()) {
            assertThat(auxThreadTimer.name()).isEqualTo("auxiliary thread");
            assertThat(auxThreadTimer.count()).isEqualTo(1);
            assertThat(auxThreadTimer.totalNanos()).isGreaterThan(MILLISECONDS.toNanos(50));
            assertThat(auxThreadTimer.childTimers()).hasSize(1);
            IncomingSpan.Timer childTimer = auxThreadTimer.childTimers().get(0);
            assertThat(childTimer.name()).isEqualTo("test local span");
            assertThat(childTimer.count()).isEqualTo(1);
        }

        List<Span> spans = incomingSpan.childSpans();
        assertThat(spans.size()).isBetween(1, 3);
        for (Span span : spans) {
            assertSingleLocalSpanMessage(span).isEqualTo("test local span");
        }
    }

    public static class DoExecuteRunnableWithLambda implements AppUnderTest, TransactionMarker {

        private ThreadPoolExecutor executor;
        private CountDownLatch latch;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            executor =
                    new ThreadPoolExecutor(1, 1, 60, MILLISECONDS, Queues.newLinkedBlockingQueue());
            // need to pre-create threads, otherwise lambda execution will be captured by the
            // initial thread run, and won't really test lambda execution capture
            executor.prestartAllCoreThreads();
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            latch = new CountDownLatch(3);
            executor.execute(this::run);
            executor.execute(this::run);
            executor.execute(this::run);
            latch.await();
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }

        private void run() {
            TestSpans.createLocalSpan(100);
            latch.countDown();
        }
    }

    public static class DoNestedExecuteRunnableWithLambda
            implements AppUnderTest, TransactionMarker {

        private ThreadPoolExecutor executor;
        private CountDownLatch latch;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            executor =
                    new ThreadPoolExecutor(1, 1, 60, MILLISECONDS, Queues.newLinkedBlockingQueue());
            // need to pre-create threads, otherwise lambda execution will be captured by the
            // initial thread run, and won't really test lambda execution capture
            executor.prestartAllCoreThreads();
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            MoreExecutors.directExecutor().execute(this::outerRun);
        }

        private void outerRun() {
            latch = new CountDownLatch(3);
            executor.execute(this::innerRun);
            executor.execute(this::innerRun);
            executor.execute(this::innerRun);
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

        private void innerRun() {
            TestSpans.createLocalSpan(100);
            latch.countDown();
        }
    }
}
