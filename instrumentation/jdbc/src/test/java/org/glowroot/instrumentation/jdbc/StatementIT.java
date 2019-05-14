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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;

import org.apache.commons.dbcp.DelegatingConnection;
import org.apache.commons.dbcp.DelegatingStatement;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.jdbc.Connections.ConnectionType;
import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;

public class StatementIT {

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
    public void testStatement() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndIterateOverResults.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementQuery() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(
                ExecuteStatementQueryAndIterateOverResults.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementUpdate() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteStatementUpdate.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("update employee set name = 'nobody'");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testNullStatement() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteNullStatement.class, Connections.getConnectionType());
        // then
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testStatementThrowing() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementThrowing.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        for (int j = 0; j < 5000; j++) {
            OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
            assertThat(outgoingSpan.type()).isEqualTo("SQL");
            assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
            assertThat(outgoingSpan.message()).isEqualTo("select " + j + " from employee");
            assertThat(outgoingSpan.detail()).isEmpty();
        }

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).isEmpty();
        assertThat(outgoingSpan.exception()).isNotNull();
        assertThat(outgoingSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(outgoingSpan.exception().message()).isEqualTo("An execute failure");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementUsingPrevious() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndUsePrevious.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementUsingRelativeForward() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndUseRelativeForward.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementUsingRelativeBackward() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndUseRelativeBackward.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementUsingAbsolute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndUseAbsolute.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 2L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementUsingFirst() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndUseFirst.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testStatementUsingLast() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndUseLast.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testIteratingOverResultsInAnotherThread() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteStatementQueryAndIterateOverResultsAfterTransaction.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testExplainPlan() throws Exception {
        // given
        container.setInstrumentationProperty("jdbc", "explainPlanThresholdMillis", 0.0);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndIterateOverResults.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);
        if (Connections.getConnectionType() == ConnectionType.MYSQL
                || Connections.getConnectionType() == ConnectionType.POSTGRES
                || Connections.getConnectionType() == ConnectionType.SQLSERVER
                || Connections.getConnectionType() == ConnectionType.ORACLE
                || Connections.getConnectionType() == ConnectionType.H2) {
            assertThat(outgoingSpan.detail()).containsKey("explainPlan");
        }

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteStatementAndIterateOverResults
            implements AppUnderTest, TransactionMarker {

        private Connection connection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            connection = Connections.createConnection(((ConnectionType) args[0]));
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }

        @Override
        public void transactionMarker() throws Exception {
            Statement statement = connection.createStatement();
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementQueryAndIterateOverResults
            implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement();
            try {
                ResultSet rs = statement.executeQuery("select * from employee");
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementUpdate implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate("update employee set name = 'nobody'");
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteNullStatement implements AppUnderTest, TransactionMarker {

        private Connection delegatingConnection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            final Connection connection = Connections.createConnection((ConnectionType) args[0]);
            delegatingConnection = new DelegatingConnection(connection) {
                @Override
                public Statement createStatement() throws SQLException {
                    return new DelegatingStatement(this, connection.createStatement()) {
                        @Override
                        public boolean execute(String sql) throws SQLException {
                            return super.execute("select 1 from employee");
                        }
                    };
                }
            };
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }

        @Override
        public void transactionMarker() throws Exception {
            Statement statement = delegatingConnection.createStatement();
            try {
                statement.execute(null);
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementThrowing implements AppUnderTest, TransactionMarker {

        private Connection connection;
        private Connection delegatingConnection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            connection = Connections.createConnection((ConnectionType) args[0]);
            delegatingConnection = new DelegatingConnection(connection) {
                @Override
                public Statement createStatement() throws SQLException {
                    return new DelegatingStatement(this, connection.createStatement()) {
                        @Override
                        public boolean execute(String sql) throws SQLException {
                            throw new SQLException("An execute failure");
                        }
                    };
                }
            };
            try {
                transactionMarker();
            } finally {
                Connections.closeConnection(connection);
            }
        }

        @Override
        public void transactionMarker() throws Exception {
            // exceed the limit for distinct aggregated queries in a single incomingSpan
            for (int i = 0; i < 5000; i++) {
                Statement statement = connection.createStatement();
                try {
                    statement.execute("select " + i + " from employee");
                } finally {
                    statement.close();
                }
            }
            Statement statement = delegatingConnection.createStatement();
            try {
                statement.execute("select * from employee");
            } catch (SQLException e) {
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndUsePrevious implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                rs.afterLast();
                while (rs.previous()) {
                    rs.getString(1);
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndUseRelativeForward
            implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                // need to position cursor on a valid row before calling relative(), at least for
                // sqlserver jdbc driver
                rs.next();
                rs.getString(1);
                while (rs.relative(1)) {
                    rs.getString(1);
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndUseRelativeBackward
            implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                rs.afterLast();
                // need to position cursor on a valid row before calling relative(), at least for
                // sqlserver jdbc driver
                rs.previous();
                rs.getString(1);
                while (rs.relative(-1)) {
                    rs.getString(1);
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndUseAbsolute implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                rs.absolute(2);
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndUseFirst implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                rs.first();
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndUseLast implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
            try {
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                rs.last();
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementQueryAndIterateOverResultsAfterTransaction
            implements AppUnderTest, TransactionMarker {

        private Connection connection;
        private Statement statement;
        private ResultSet rs;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            connection = Connections.createConnection((ConnectionType) args[0]);
            statement = connection.createStatement();
            try {
                transactionMarker();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                statement.close();
                Connections.closeConnection(connection);
            }
        }

        @Override
        public void transactionMarker() throws Exception {
            rs = statement.executeQuery("select * from employee");
        }
    }
}
