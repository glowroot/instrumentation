/*
 * Copyright 2018-2019 the original author or authors.
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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.IncomingSpan.Timer;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class ThreadIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // tests only work with javaagent container because they need to weave java.lang.Thread
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
    public void shouldCaptureThread() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteThread.class);

        // then
        checkIncomingSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureThreadWithName() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteThreadWithName.class);

        // then
        checkIncomingSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureThreadWithThreadGroup() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteThreadWithThreadGroup.class);

        // then
        checkIncomingSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureThreadWithThreadGroupAndName() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteThreadWithThreadGroupAndName.class);

        // then
        checkIncomingSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureThreadSubclassed() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteThreadSubclassed.class);

        // then
        checkIncomingSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureThreadSubSubclassed() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(DoExecuteThreadSubSubclassed.class);

        // then
        checkIncomingSpan(incomingSpan, false, false);
    }

    @Test
    public void shouldCaptureThreadSubSubclassedWithRunnable() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(DoExecuteThreadSubSubclassedWithRunnable.class);

        // then
        checkIncomingSpan(incomingSpan, false, false);
    }

    private static void checkIncomingSpan(IncomingSpan incomingSpan, boolean isAny,
            boolean withFuture) {
        if (withFuture) {
            assertThat(incomingSpan.mainThreadTimer().childTimers()).hasSize(1);
            Timer childTimer = incomingSpan.mainThreadTimer().childTimers().get(0);
            assertThat(childTimer.name()).isEqualTo("wait on future");
            assertThat(childTimer.count()).isBetween(1L, 3L);
        }
        if (isAny) {
            assertThat(incomingSpan.auxThreadTimers().size()).isBetween(1, 3);
        } else {
            assertThat(incomingSpan.auxThreadTimers()).hasSize(3);
        }
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

        if (isAny) {
            assertThat(spans.size()).isBetween(1, 3);
        } else {
            assertThat(spans).hasSize(3);
        }
        for (Span span : spans) {
            assertSingleLocalSpanMessage(span).isEqualTo("test local span");
        }
    }

    public static class DoExecuteThread implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Thread thread1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            });
            Thread thread2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            });
            Thread thread3 = new Thread(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            });
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
        }
    }

    public static class DoExecuteThreadWithName implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Thread thread1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            }, "one");
            Thread thread2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            }, "two");
            Thread thread3 = new Thread(new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            }, "three");
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
        }
    }

    public static class DoExecuteThreadWithThreadGroup implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Thread thread1 = new Thread(Thread.currentThread().getThreadGroup(), new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            });
            Thread thread2 = new Thread(Thread.currentThread().getThreadGroup(), new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            });
            Thread thread3 = new Thread(Thread.currentThread().getThreadGroup(), new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            });
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
        }
    }

    public static class DoExecuteThreadWithThreadGroupAndName
            implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Thread thread1 = new Thread(Thread.currentThread().getThreadGroup(), new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            }, "one");
            Thread thread2 = new Thread(Thread.currentThread().getThreadGroup(), new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            }, "two");
            Thread thread3 = new Thread(Thread.currentThread().getThreadGroup(), new Runnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            }, "three");
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
        }
    }

    public static class DoExecuteThreadSubclassed implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Thread thread1 = new Thread() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            Thread thread2 = new Thread() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            Thread thread3 = new Thread() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
        }
    }

    public static class DoExecuteThreadSubSubclassed implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Thread thread1 = new LocalSpanMarkerThread() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            Thread thread2 = new LocalSpanMarkerThread() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            Thread thread3 = new LocalSpanMarkerThread() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
        }
    }

    public static class DoExecuteThreadSubSubclassedWithRunnable
            implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Thread thread1 = new LocalSpanMarkerThreadWithRunnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            Thread thread2 = new LocalSpanMarkerThreadWithRunnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            Thread thread3 = new LocalSpanMarkerThreadWithRunnable() {
                @Override
                public void run() {
                    TestSpans.createLocalSpan(100);
                }
            };
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join();
            thread3.join();
        }
    }

    private static class LocalSpanMarkerThread extends Thread {}

    private static class LocalSpanMarkerThreadWithRunnable extends Thread implements Runnable {}
}
