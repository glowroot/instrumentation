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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class ScheduledExecutorServiceIT {

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
    public void shouldCaptureScheduledRunnable() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoScheduledRunnable.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    @Test
    public void shouldCaptureScheduledCallable() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoScheduledCallable.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    private static ScheduledExecutorService createScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    public static class DoScheduledRunnable implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ScheduledExecutorService executor = createScheduledExecutorService();
            final CountDownLatch latch = new CountDownLatch(1);
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                }
            }, 100, MILLISECONDS);
            latch.await();
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    public static class DoScheduledCallable implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ScheduledExecutorService executor = createScheduledExecutorService();
            final CountDownLatch latch = new CountDownLatch(1);
            executor.schedule(new Callable<Void>() {
                @Override
                public Void call() {
                    TestSpans.createLocalSpan(100);
                    latch.countDown();
                    return null;
                }
            }, 100, MILLISECONDS);
            latch.await();
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }
}
