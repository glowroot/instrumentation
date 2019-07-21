/*
 * Copyright 2015-2019 the original author or authors.
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
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbcp.DelegatingConnection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.jdbc.Connections.ConnectionType;
import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class ConnectionAndTxLifecycleIT {

    private static final String INSTRUMENTATION_ID = "jdbc";

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
    public void testConnectionLifecycle() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "captureConnectionLifecycleTraceEntries", true);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteGetConnectionAndConnectionClose.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc get connection");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc connection close");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testConnectionLifecycleDisabled() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureGetConnection", false);
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionClose", false);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteGetConnectionAndConnectionClose.class);

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.childTimers()).isEmpty();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testConnectionLifecyclePartiallyDisabled() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionClose", true);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteGetConnectionAndConnectionClose.class);

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.childTimers()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(mainThreadTimer.childTimers().get(0).name());
        childTimerNames.add(mainThreadTimer.childTimers().get(1).name());
        assertThat(childTimerNames).containsOnly("jdbc get connection", "jdbc connection close");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testConnectionLifecycleGetConnectionThrows() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "captureConnectionLifecycleTraceEntries", true);

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteGetConnectionOnThrowingDataSource.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc get connection");
        assertThat(localSpan.exception()).isNotNull();
        assertThat(localSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(localSpan.exception().message()).isEqualTo("A getconnection failure");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testConnectionLifecycleGetConnectionThrowsDisabled() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureGetConnection", false);
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionClose", false);

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteGetConnectionOnThrowingDataSource.class);

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.childTimers()).isEmpty();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testConnectionLifecycleGetConnectionThrowsPartiallyDisabled() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteGetConnectionOnThrowingDataSource.class);

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.childTimers()).hasSize(1);
        assertThat(mainThreadTimer.childTimers().get(0).name()).isEqualTo("jdbc get connection");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testConnectionLifecycleCloseConnectionThrows() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "captureConnectionLifecycleTraceEntries", true);

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteCloseConnectionOnThrowingDataSource.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc get connection");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc connection close");
        assertThat(localSpan.exception()).isNotNull();
        assertThat(localSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(localSpan.exception().message()).isEqualTo("A close failure");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testConnectionLifecycleCloseConnectionThrowsDisabled() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureGetConnection", false);
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionClose", false);

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteCloseConnectionOnThrowingDataSource.class);

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.childTimers()).isEmpty();
    }

    @Test
    public void testConnectionLifecycleCloseConnectionThrowsPartiallyDisabled() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureConnectionClose", true);

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteCloseConnectionOnThrowingDataSource.class);

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.childTimers()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(mainThreadTimer.childTimers().get(0).name());
        childTimerNames.add(mainThreadTimer.childTimers().get(1).name());
        assertThat(childTimerNames).containsOnly("jdbc get connection", "jdbc connection close");
    }

    @Test
    public void testTransactionLifecycle() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "captureTransactionLifecycleTraceEntries", true);

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteSetAutoCommit.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc set autocommit: false");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc set autocommit: true");
        assertThat(localSpan.childSpans()).isEmpty();

        if (i.hasNext()) {
            localSpan = (LocalSpan) i.next();
            assertThat(localSpan.message()).isEqualTo("jdbc commit");
            assertThat(localSpan.childSpans()).isEmpty();
        }

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testTransactionLifecycleThrowing() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "captureTransactionLifecycleTraceEntries", true);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteSetAutoCommitThrowing.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc set autocommit: false");
        assertThat(localSpan.exception()).isNotNull();
        assertThat(localSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(localSpan.exception().message()).isEqualTo("A setautocommit failure");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc set autocommit: true");
        assertThat(localSpan.exception()).isNotNull();
        assertThat(localSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(localSpan.exception().message()).isEqualTo("A setautocommit failure");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testConnectionLifecycleAndTransactionLifecycleTogether() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.HSQLDB));

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "captureConnectionLifecycleTraceEntries", true);
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "captureTransactionLifecycleTraceEntries", true);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteGetConnectionAndConnectionClose.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc get connection (autocommit: true)");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc connection close");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteGetConnectionAndConnectionClose
            implements AppUnderTest, TransactionMarker {

        private BasicDataSource dataSource;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            dataSource = new BasicDataSource();
            dataSource.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
            dataSource.setUrl("jdbc:hsqldb:mem:test");
            // BasicDataSource opens and closes a test connection on first getConnection(),
            // so just getting that out of the way before starting transaction
            dataSource.getConnection().close();
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            dataSource.getConnection().close();
        }
    }

    public static class ExecuteGetConnectionOnThrowingDataSource
            implements AppUnderTest, TransactionMarker {

        private BasicDataSource dataSource;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            dataSource = new BasicDataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    throw new SQLException("A getconnection failure");
                }
            };
            dataSource.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
            dataSource.setUrl("jdbc:hsqldb:mem:test");
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            try {
                dataSource.getConnection();
            } catch (SQLException e) {
            }
        }
    }

    public static class ExecuteCloseConnectionOnThrowingDataSource
            implements AppUnderTest, TransactionMarker {

        private BasicDataSource dataSource;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            dataSource = new BasicDataSource() {

                private boolean first = true;

                @Override
                public Connection getConnection() throws SQLException {
                    if (first) {
                        // BasicDataSource opens and closes a test connection on first
                        // getConnection()
                        first = false;
                        return super.getConnection();
                    }
                    return new DelegatingConnection(super.getConnection()) {
                        @Override
                        public void close() throws SQLException {
                            throw new SQLException("A close failure");
                        }
                    };
                }
            };
            dataSource.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");
            dataSource.setUrl("jdbc:hsqldb:mem:test");
            // BasicDataSource opens and closes a test connection on first getConnection(),
            // so just getting that out of the way before starting transaction
            dataSource.getConnection().close();
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            try {
                dataSource.getConnection().close();
            } catch (SQLException e) {
            }
        }
    }

    public static class ExecuteSetAutoCommit implements AppUnderTest, TransactionMarker {

        private Connection connection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            connection = Connections.createConnection((ConnectionType) args[0]);
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }

        @Override
        public void transactionMarker() throws Exception {
            connection.setAutoCommit(false);
            connection.setAutoCommit(true);
        }
    }

    public static class ExecuteSetAutoCommitThrowing implements AppUnderTest, TransactionMarker {

        private Connection connection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            connection = new DelegatingConnection(
                    Connections.createConnection((ConnectionType) args[0])) {

                @Override
                public void setAutoCommit(boolean autoCommit) throws SQLException {
                    throw new SQLException("A setautocommit failure");
                }
            };
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }

        @Override
        public void transactionMarker() {
            try {
                connection.setAutoCommit(false);
            } catch (SQLException e) {
            }
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
            }
        }
    }
}
