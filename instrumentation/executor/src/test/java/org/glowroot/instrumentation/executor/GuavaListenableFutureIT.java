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
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class GuavaListenableFutureIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        // need null check in case assumption is false in setUp()
        if (container != null) {
            container.close();
        }
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
    }

    @Test
    public void shouldCaptureListenerAddedBeforeComplete() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(AddListenerBeforeComplete.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    @Test
    public void shouldCaptureListenerAddedAfterComplete() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(AddListenerAfterComplete.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    @Test
    public void shouldCaptureSameExecutorListenerAddedBeforeComplete() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(AddSameExecutorListenerBeforeComplete.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    @Test
    public void shouldCaptureSameExecutorListenerAddedAfterComplete() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(AddSameExecutorListenerAfterComplete.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    public static class AddListenerBeforeComplete implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ListeningExecutorService executor =
                    MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
            ListenableFuture<Void> future1 = executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws InterruptedException {
                    MILLISECONDS.sleep(100);
                    return null;
                }
            });
            future1.addListener(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan();
                }
            }, executor);
            MILLISECONDS.sleep(200);
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    public static class AddListenerAfterComplete implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ListeningExecutorService executor =
                    MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
            ListenableFuture<Void> future1 = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    return null;
                }
            });
            MILLISECONDS.sleep(100);
            future1.addListener(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan();
                }
            }, executor);
            MILLISECONDS.sleep(100);
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    public static class AddSameExecutorListenerBeforeComplete
            implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ListeningExecutorService executor =
                    MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
            ListenableFuture<Void> future1 = executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws InterruptedException {
                    MILLISECONDS.sleep(100);
                    return null;
                }
            });
            future1.addListener(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan();
                }
            }, executor);
            MILLISECONDS.sleep(200);
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }

    public static class AddSameExecutorListenerAfterComplete
            implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            ListeningExecutorService executor =
                    MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
            ListenableFuture<Void> future1 = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    return null;
                }
            });
            MILLISECONDS.sleep(100);
            future1.addListener(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan();
                }
            }, executor);
            MILLISECONDS.sleep(100);
            executor.shutdown();
            executor.awaitTermination(10, SECONDS);
        }
    }
}
