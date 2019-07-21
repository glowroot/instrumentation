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
package org.glowroot.instrumentation.jdbc;

import java.io.Serializable;
import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.jdbc.Connections.ConnectionType;
import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class ObjectPoolIT {

    private static final String INSTRUMENTATION_ID = "jdbc";

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
    public void testReturningCommonsDbcpConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.COMMONS_DBCP_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(ReturnCommonsDbcpConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingCommonsDbcpConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.COMMONS_DBCP_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakCommonsDbcpConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testReturningCommonsDbcp2Connection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.COMMONS_DBCP2_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(ReturnCommonsDbcp2Connection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingCommonsDbcp2Connection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.COMMONS_DBCP2_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakCommonsDbcp2Connection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testReturningTomcatConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.TOMCAT_JDBC_POOL_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(ReturnTomcatConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingTomcatConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.TOMCAT_JDBC_POOL_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakTomcatConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testReturningTomcatAsyncConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.TOMCAT_JDBC_POOL_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(ReturnTomcatAsyncConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingTomcatAsyncConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.TOMCAT_JDBC_POOL_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakTomcatAsyncConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testReturningGlassfishConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.GLASSFISH_JDBC_POOL_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(ReturnGlassfishConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingGlassfishConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.GLASSFISH_JDBC_POOL_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakGlassfishConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testReturningHikariConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HIKARI_CP_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(ReturnHikariConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingHikariConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HIKARI_CP_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakHikariConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testReturningBitronixConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.BITRONIX_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(ReturnBitronixConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingBitronixConnection() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.BITRONIX_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakBitronixConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingMultipleConnections() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.COMMONS_DBCP_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakMultipleDbcpConnections.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isFalse();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testLeakingConnectionWithLocationStackTrace() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.COMMONS_DBCP_WRAPPED));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeaks",
                true);

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionPoolLeakDetails",
                true);

        // when
        IncomingSpan incomingSpan = container.execute(LeakCommonsDbcpConnection.class);

        // then
        assertThat(incomingSpan.resourceLeakDetected()).isTrue();
        assertThat(incomingSpan.resourceLeakDetectedWithLocation()).isTrue();

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    public static class ReturnCommonsDbcpConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createCommonsDbcpWrappedDataSource();
            ds.getConnection().close();
        }
    }

    public static class LeakCommonsDbcpConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createCommonsDbcpWrappedDataSource();
            ds.getConnection();
        }
    }

    public static class ReturnCommonsDbcp2Connection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createCommonsDbcp2DataSource();
            ds.getConnection().close();
        }
    }

    public static class LeakCommonsDbcp2Connection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createCommonsDbcp2DataSource();
            ds.getConnection();
        }
    }

    public static class ReturnTomcatConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createTomcatJdbcPoolWrappedDataSource();
            ds.getConnection().close();
        }
    }

    public static class LeakTomcatConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createTomcatJdbcPoolWrappedDataSource();
            ds.getConnection();
        }
    }

    public static class ReturnTomcatAsyncConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            org.apache.tomcat.jdbc.pool.DataSource ds =
                    Connections.createTomcatJdbcPoolWrappedDataSource();
            ds.getConnectionAsync().get().close();
        }
    }

    public static class LeakTomcatAsyncConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            org.apache.tomcat.jdbc.pool.DataSource ds =
                    Connections.createTomcatJdbcPoolWrappedDataSource();
            ds.getConnectionAsync().get();
        }
    }

    public static class ReturnGlassfishConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createGlassfishJdbcPoolWrappedDataSource();
            Connection connection = ds.getConnection();
            Connections.hackGlassfishConnection(connection);
            connection.close();
        }
    }

    public static class LeakGlassfishConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createGlassfishJdbcPoolWrappedDataSource();
            Connection connection = ds.getConnection();
            Connections.hackGlassfishConnection(connection);
        }
    }

    public static class ReturnHikariConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createHikariCpDataSource();
            ds.getConnection().close();
        }
    }

    public static class LeakHikariConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createHikariCpDataSource();
            ds.getConnection();
        }
    }

    public static class ReturnBitronixConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createBitronixWrappedDataSource();
            ds.getConnection().close();
        }
    }

    public static class LeakBitronixConnection implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createBitronixWrappedDataSource();
            ds.getConnection();
        }
    }

    public static class LeakMultipleDbcpConnections implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            DataSource ds = Connections.createCommonsDbcpWrappedDataSource();
            ds.getConnection();
            ds.getConnection();
        }
    }
}
