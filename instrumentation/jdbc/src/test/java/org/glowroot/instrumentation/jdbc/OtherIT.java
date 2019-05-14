/*
 * Copyright 2011-2019 the original author or authors.
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
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.Iterator;

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
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.TestSpans.DoInSpan;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class OtherIT {

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
    public void testCallableStatement() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteCallableStatement.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name, misc) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", Arrays.asList("jane", null));

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testDefaultStackTraceThreshold() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndIterateOverResults.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.locationStackTraceMillis()).isEqualTo(1000);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testZeroStackTraceThreshold() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "stackTraceThresholdMillis", 0.0);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndIterateOverResults.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.locationStackTraceMillis()).isZero();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testNullStackTraceThreshold() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "stackTraceThresholdMillis",
                (Double) null);

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatementAndIterateOverResults.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.locationStackTraceMillis())
                .isEqualTo(NANOSECONDS.toMillis(Long.MAX_VALUE));

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteStatementAndIterateOverResults
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

    public static class ExecuteLotsOfStatementAndIterateOverResults
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
                for (int i = 0; i < 4000; i++) {
                    statement.execute("select * from employee");
                    ResultSet rs = statement.getResultSet();
                    while (rs.next()) {
                        rs.getString(1);
                    }
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class IterateOverResultsUnderSeparateLocalSpan
            implements AppUnderTest, TransactionMarker {

        private Connection connection;
        private Statement statement;

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
            statement = connection.createStatement();
            try {
                statement.execute("select * from employee");
                TestSpans.createLocalSpan(new DoInSpan() {
                    @Override
                    public void doInSpan() throws Exception {
                        ResultSet rs = statement.getResultSet();
                        while (rs.next()) {
                            rs.getString(1);
                        }
                    }
                });
            } finally {
                statement.close();
            }
        }
    }

    public static class GetResultSetValueUnderSeparateLocalSpan
            implements AppUnderTest, TransactionMarker {

        private Connection connection;
        private ResultSet rs;

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
                statement.execute("select * from employee");
                rs = statement.getResultSet();
                while (rs.next()) {
                    TestSpans.createLocalSpan(new DoInSpan() {
                        @Override
                        public void doInSpan() throws Exception {
                            rs.getString(1);
                        }
                    });
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndIterateOverResultsUsingColumnName
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
                statement.execute("select * from employee");
                ResultSet rs = statement.getResultSet();
                while (rs.next()) {
                    rs.getString("name");
                }
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteStatementAndIterateOverResultsUsingColumnNameUnderSeparateLocalSpan
            implements AppUnderTest, TransactionMarker {

        private Connection connection;
        private Statement statement;

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
            statement = connection.createStatement();
            try {
                statement.execute("select * from employee");
                TestSpans.createLocalSpan(new DoInSpan() {
                    @Override
                    public void doInSpan() throws Exception {
                        ResultSet rs = statement.getResultSet();
                        while (rs.next()) {
                            rs.getString("name");
                        }
                    }
                });
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteCallableStatement implements AppUnderTest, TransactionMarker {

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
            CallableStatement callableStatement =
                    connection.prepareCall("insert into employee (name, misc) values (?, ?)");
            try {
                callableStatement.setString(1, "jane");
                callableStatement.setNull(2, Types.BINARY);
                callableStatement.execute();
            } finally {
                callableStatement.close();
            }
        }
    }

    public static class AccessMetaData implements AppUnderTest, TransactionMarker {

        private Connection connection;

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

        @Override
        public void transactionMarker() throws Exception {
            connection.getMetaData().getTables(null, null, null, null);
        }
    }
}
