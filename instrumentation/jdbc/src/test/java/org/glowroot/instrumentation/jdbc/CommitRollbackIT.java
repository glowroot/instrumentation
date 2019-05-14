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
import java.sql.Statement;
import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.Sets;
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
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;

public class CommitRollbackIT {

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
    public void testCommit() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteJdbcCommit.class, Connections.getConnectionType());

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.name()).isEqualTo("mock trace marker");
        assertThat(mainThreadTimer.childTimers()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(mainThreadTimer.childTimers().get(0).name());
        childTimerNames.add(mainThreadTimer.childTimers().get(1).name());
        assertThat(childTimerNames).containsOnly("jdbc query", "jdbc commit");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('john doe')");
        assertThat(outgoingSpan.detail()).isEmpty();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc commit");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testCommitThrowing() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteJdbcCommitThrowing.class,
                Connections.getConnectionType());

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.name()).isEqualTo("mock trace marker");
        assertThat(mainThreadTimer.childTimers()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(mainThreadTimer.childTimers().get(0).name());
        childTimerNames.add(mainThreadTimer.childTimers().get(1).name());
        assertThat(childTimerNames).containsOnly("jdbc query", "jdbc commit");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('john doe')");
        assertThat(outgoingSpan.detail()).isEmpty();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc commit");
        assertThat(localSpan.exception()).isNotNull();
        assertThat(localSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(localSpan.exception().message()).isEqualTo("A commit failure");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testRollback() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteJdbcRollback.class, Connections.getConnectionType());

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.name()).isEqualTo("mock trace marker");
        assertThat(mainThreadTimer.childTimers()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(mainThreadTimer.childTimers().get(0).name());
        childTimerNames.add(mainThreadTimer.childTimers().get(1).name());
        assertThat(childTimerNames).containsOnly("jdbc query", "jdbc rollback");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('john doe')");
        assertThat(outgoingSpan.detail()).isEmpty();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc rollback");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testRollbackThrowing() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteJdbcRollbackThrowing.class,
                Connections.getConnectionType());

        // then
        IncomingSpan.Timer mainThreadTimer = incomingSpan.mainThreadTimer();
        assertThat(mainThreadTimer.childTimers()).hasSize(2);
        // ordering is by total desc, so order is not fixed
        Set<String> childTimerNames = Sets.newHashSet();
        childTimerNames.add(mainThreadTimer.childTimers().get(0).name());
        childTimerNames.add(mainThreadTimer.childTimers().get(1).name());
        assertThat(childTimerNames).containsOnly("jdbc query", "jdbc rollback");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('john doe')");
        assertThat(outgoingSpan.detail()).isEmpty();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("jdbc rollback");
        assertThat(localSpan.exception()).isNotNull();
        assertThat(localSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(localSpan.exception().message()).isEqualTo("A rollback failure");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    public abstract static class ExecuteJdbcCommitBase implements AppUnderTest, TransactionMarker {

        protected Connection connection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            connection = Connections.createConnection((ConnectionType) args[0]);
            connection.setAutoCommit(false);
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }

        void executeInsert() throws Exception {
            Statement statement = connection.createStatement();
            try {
                statement.execute("insert into employee (name) values ('john doe')");
            } finally {
                statement.close();
            }
        }
    }

    public abstract static class ExecuteJdbcCommitThrowingBase
            implements AppUnderTest, TransactionMarker {

        protected Connection delegatingConnection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            Connection connection = Connections.createConnection((ConnectionType) args[0]);
            delegatingConnection = new DelegatingConnection(connection) {

                @Override
                public void commit() throws SQLException {
                    throw new SQLException("A commit failure");
                }

                @Override
                public void rollback() throws SQLException {
                    throw new SQLException("A rollback failure");
                }
            };
            delegatingConnection.setAutoCommit(false);
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }

        void executeInsert() throws Exception {
            Statement statement = delegatingConnection.createStatement();
            try {
                statement.execute("insert into employee (name) values ('john doe')");
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteJdbcCommit extends ExecuteJdbcCommitBase {

        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            connection.commit();
        }
    }

    public static class ExecuteJdbcRollback extends ExecuteJdbcCommitBase {

        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            connection.rollback();
        }
    }

    public static class ExecuteJdbcCommitThrowing extends ExecuteJdbcCommitThrowingBase {

        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            try {
                delegatingConnection.commit();
            } catch (SQLException e) {
            }
        }
    }

    public static class ExecuteJdbcRollbackThrowing extends ExecuteJdbcCommitThrowingBase {

        @Override
        public void transactionMarker() throws Exception {
            executeInsert();
            try {
                delegatingConnection.rollback();
            } catch (SQLException e) {
            }
        }
    }
}
