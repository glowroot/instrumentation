/**
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
package org.glowroot.instrumentation.ejb;

import java.io.Serializable;

import javax.ejb.Remote;
import javax.ejb.Stateless;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;

// NOTE EJB Remote instrumentation is currently implemented in core since the hooks needed are not
// yet exposed via the instrumentation api
public class EjbRemoteIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.create();
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
    public void shouldCaptureARemoteBeanOne() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteARemoteBeanOne.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: ARemoteOne#one");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$ARemoteOne.one()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureARemoteBeanTwo() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteARemoteBeanTwo.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: ARemoteTwo#two");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$ARemoteTwo.two()");
    }

    @Test
    public void shouldCaptureA2RemoteBeanOne() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteA2RemoteBeanOne.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: ARemoteOne#one");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$ARemoteOne.one()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureA2RemoteBeanTwo() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteA2RemoteBeanTwo.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: ARemoteTwo#two");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$ARemoteTwo.two()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureA3RemoteBeanOne() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteA3RemoteBeanOne.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: ARemoteOne#one");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$ARemoteOne.one()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureA3RemoteBeanTwo() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteA3RemoteBeanTwo.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: ARemoteTwo#two");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$ARemoteTwo.two()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureBRemoteBeanOne() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteBRemoteBeanOne.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: BRemoteOne#one");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$BRemoteOne.one()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureBRemoteBeanTwo() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteBRemoteBeanTwo.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: BRemoteTwo#two");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$BRemoteTwo.two()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureB2RemoteBeanOne() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteB2RemoteBeanOne.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: BRemoteOne#one");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$BRemoteOne.one()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureB2RemoteBeanTwo() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteB2RemoteBeanTwo.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: BRemoteTwo#two");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$BRemoteTwo.two()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureB3RemoteBeanOne() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteB3RemoteBeanOne.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: BRemoteOne#one");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$BRemoteOne.one()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureB3RemoteBeanTwo() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteB3RemoteBeanTwo.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: BRemoteTwo#two");
        assertThat(incomingSpan.message()).isEqualTo(
                "EJB remote: org.glowroot.instrumentation.ejb.EjbRemoteIT$BRemoteTwo.two()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureCRemoteSessionBeanOne() throws Exception {
        IncomingSpan incomingSpan = container.execute(ExecuteCRemoteSessionBeanOne.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Web");
        assertThat(incomingSpan.transactionName()).isEqualTo("EJB remote: CRemoteSessionOne#one");
        assertThat(incomingSpan.message()).isEqualTo("EJB remote:"
                + " org.glowroot.instrumentation.ejb.EjbRemoteIT$CRemoteSessionOne.one()");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    public static class ExecuteARemoteBeanOne implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new ARemoteBean().one();
        }
    }

    public static class ExecuteARemoteBeanTwo implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new ARemoteBean().two(2);
        }
    }

    public static class ExecuteA2RemoteBeanOne implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new A2RemoteBean().one();
        }
    }

    public static class ExecuteA2RemoteBeanTwo implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new A2RemoteBean().two(2);
        }
    }

    public static class ExecuteA3RemoteBeanOne implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new A3RemoteBean().one();
        }
    }

    public static class ExecuteA3RemoteBeanTwo implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new A3RemoteBean().two(2);
        }
    }

    public static class ExecuteBRemoteBeanOne implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new BRemoteBean().one();
        }
    }

    public static class ExecuteBRemoteBeanTwo implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new BRemoteBean().two(2);
        }
    }

    public static class ExecuteB2RemoteBeanOne implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new B2RemoteBean().one();
        }
    }

    public static class ExecuteB2RemoteBeanTwo implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new B2RemoteBean().two(2);
        }
    }

    public static class ExecuteB3RemoteBeanOne implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new B3RemoteBean().one();
        }
    }

    public static class ExecuteB3RemoteBeanTwo implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new B3RemoteBean().two(2);
        }
    }

    public static class ExecuteCRemoteSessionBeanOne implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            new CRemoteSessionBean().one();
        }
    }

    @Remote({ARemoteOne.class, ARemoteTwo.class})
    public static class ARemoteBean implements ARemoteOne, ARemoteTwo {

        @Override
        public void one() {}

        @Override
        public void two(int x) {}
    }

    @Remote({ARemoteOne.class, ARemoteTwo.class})
    public static class A2RemoteBean extends A2RemoteBase implements ARemoteOne, ARemoteTwo {}

    public static class A2RemoteBase {

        public void one() {}

        public void two(@SuppressWarnings("unused") int x) {}
    }

    @Remote({ARemoteOne.class, ARemoteTwo.class})
    public static class A3RemoteBean extends A3RemoteBase {}

    public static class A3RemoteBase implements ARemoteOne, ARemoteTwo {

        @Override
        public void one() {}

        @Override
        public void two(int x) {}
    }

    public interface ARemoteOne {

        String VALUE = Init.value();

        void one();
    }

    public interface ARemoteTwo {

        void two(int x);
    }

    private static class Init {

        private static String value() {
            return "";
        }
    }

    @Remote
    public static class BRemoteBean implements BRemoteOne, BRemoteTwo {

        @Override
        public void one() {}

        @Override
        public void two(int x) {}
    }

    @Remote
    public static class B2RemoteBean extends B2RemoteBase implements BRemoteOne, BRemoteTwo {}

    public static class B2RemoteBase {

        public void one() {}

        public void two(@SuppressWarnings("unused") int x) {}
    }

    @Remote
    public static class B3RemoteBean extends B3RemoteBase {}

    public static class B3RemoteBase implements BRemoteOne, BRemoteTwo {

        @Override
        public void one() {}

        @Override
        public void two(int x) {}
    }

    @Remote
    public interface BRemoteOne {

        void one();
    }

    @Remote
    public interface BRemoteTwo {

        void two(int x);
    }

    @Stateless
    public static class CRemoteSessionBean implements CRemoteSessionOne {

        @Override
        public void one() {}
    }

    @Remote
    public interface CRemoteSessionOne extends One {}

    public interface One {

        void one();
    }
}
