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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.apache.commons.dbcp.DelegatingConnection;
import org.apache.commons.dbcp.DelegatingStatement;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.jdbc.Connections.ConnectionType;
import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class BatchIT {

    private static final String INSTRUMENTATION_ID = "jdbc";

    private static Container container;
    private static boolean driverCapturesBatchRows =
            Connections.getConnectionType() != ConnectionType.ORACLE;

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
    public void testBatchPreparedStatement() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteBatchPreparedStatement.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        Map<String, Object> details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 3);
        List<?> parameters = (List<?>) details.get("parameters");
        assertThat(parameters).hasSize(3);
        assertThat(parameters.get(0)).isEqualTo(ImmutableList.of("huckle"));
        assertThat(parameters.get(1)).isEqualTo(ImmutableList.of("sally"));
        assertThat(parameters.get(2)).isEqualTo(ImmutableList.of("sally"));
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 3L);
        }

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 2);
        parameters = (List<?>) details.get("parameters");
        assertThat(parameters).hasSize(2);
        assertThat(parameters.get(0)).isEqualTo(ImmutableList.of("lowly"));
        assertThat(parameters.get(1)).isEqualTo(ImmutableList.of("pig will"));
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 2L);
        }

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchPreparedExceedingLimitStatement() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(
                ExecuteBatchExceedingLimitPreparedStatement.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        Map<String, Object> details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 2002);
        List<?> parameters = (List<?>) details.get("parameters");
        assertThat(parameters).hasSize(1000);
        for (int j = 0; j < 1000; j++) {
            assertThat(parameters.get(j)).isEqualTo(ImmutableList.of("name" + j));
        }
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 2002L);
        }

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchPreparedStatementWithoutCaptureBindParams() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.<String>of());

        // when
        IncomingSpan incomingSpan = container.execute(ExecuteBatchPreparedStatement.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        Map<String, Object> details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 3);
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 3L);
        }

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 2);
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 2L);
        }

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchPreparedStatementWithoutClear() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(
                ExecuteBatchPreparedStatementWithoutClear.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        Map<String, Object> details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 2);
        List<?> parameters = (List<?>) details.get("parameters");
        assertThat(parameters).hasSize(2);
        assertThat(parameters.get(0)).isEqualTo(ImmutableList.of("huckle"));
        assertThat(parameters.get(1)).isEqualTo(ImmutableList.of("sally"));
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 2L);
        }

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 2);
        parameters = (List<?>) details.get("parameters");
        assertThat(parameters).hasSize(2);
        assertThat(parameters.get(0)).isEqualTo(ImmutableList.of("lowly"));
        assertThat(parameters.get(1)).isEqualTo(ImmutableList.of("pig will"));
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 2L);
        }

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchPreparedStatementWithoutClearWithoutCaptureBindParams() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.<String>of());

        // when
        IncomingSpan incomingSpan = container.execute(
                ExecuteBatchPreparedStatementWithoutClear.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        Map<String, Object> details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 2);
        assertThat(details).doesNotContainKey("parameters");
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 2L);
        }

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        details = outgoingSpan.detail();
        assertThat(details).containsEntry("batchCount", 2);
        assertThat(details).doesNotContainKey("parameters");
        if (driverCapturesBatchRows) {
            assertThat(details).containsEntry("rows", 2L);
        }

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchStatement() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteBatchStatement.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('huckle');"
                        + " insert into employee (name) values ('sally')");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).doesNotContainKey("batchCount");
        assertThat(outgoingSpan.detail()).containsEntry("batchStatement", true);
        assertThat(outgoingSpan.detail()).containsEntry("rows", 2L);

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('lowly');"
                        + " insert into employee (name) values ('pig will')");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).doesNotContainKey("batchCount");
        assertThat(outgoingSpan.detail()).containsEntry("batchStatement", true);
        assertThat(outgoingSpan.detail()).containsEntry("rows", 2L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchStatementNull() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(BatchStatementNull.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values ('1')");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).doesNotContainKey("batchCount");
        assertThat(outgoingSpan.detail()).containsEntry("batchStatement", true);
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchStatementWithNoBatches() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteBatchStatementWithNoBatches.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("[empty batch statement]");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).doesNotContainKey("batchCount");
        assertThat(outgoingSpan.detail()).containsEntry("batchStatement", true);
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchPreparedStatementWithNoBatches() throws Exception {

        // hsqldb driver (and maybe some others) throw error when executing a batch with no batches
        assumeTrue(Connections.getConnectionType() == ConnectionType.H2);

        // when
        IncomingSpan incomingSpan = container.execute(
                ExecuteBatchPreparedStatementWithNoBatches.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        assertThat(outgoingSpan.detail()).containsEntry("batchCount", 0);
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchPreparedStatementWithSingleBatch() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteBatchPreparedStatementWithSingleBatch.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message()).isEqualTo("insert into employee (name) values (?)");
        assertThat(outgoingSpan.detail()).containsEntry("batchCount", 1);
        assertThat(outgoingSpan.detail()).containsEntry("parameters",
                ImmutableList.of(ImmutableList.of("huckle")));

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testBatchStatementWithoutClear() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteBatchStatementWithoutClear.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('huckle');"
                        + " insert into employee (name) values ('sally')");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).doesNotContainKey("batchCount");
        assertThat(outgoingSpan.detail()).containsEntry("batchStatement", true);
        assertThat(outgoingSpan.detail()).containsEntry("rows", 2L);

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values ('lowly');"
                        + " insert into employee (name) values ('pig will')");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).doesNotContainKey("batchCount");
        assertThat(outgoingSpan.detail()).containsEntry("batchStatement", true);
        assertThat(outgoingSpan.detail()).containsEntry("rows", 2L);

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteBatchPreparedStatement implements AppUnderTest, TransactionMarker {

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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name) values (?)");
            try {
                preparedStatement.setString(1, "huckle");
                preparedStatement.addBatch();
                preparedStatement.setString(1, "sally");
                preparedStatement.addBatch();
                // add batch without re-setting params
                preparedStatement.addBatch();
                preparedStatement.executeBatch();
                preparedStatement.clearBatch();
                preparedStatement.clearBatch();
                preparedStatement.setString(1, "lowly");
                preparedStatement.addBatch();
                preparedStatement.setString(1, "pig will");
                preparedStatement.addBatch();
                preparedStatement.executeBatch();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecuteBatchExceedingLimitPreparedStatement
            implements AppUnderTest, TransactionMarker {

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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name) values (?)");
            try {
                for (int i = 0; i < 2002; i++) {
                    preparedStatement.setString(1, "name" + i);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecuteBatchPreparedStatementWithoutClear
            implements AppUnderTest, TransactionMarker {

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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name) values (?)");
            try {
                preparedStatement.setString(1, "huckle");
                preparedStatement.addBatch();
                preparedStatement.setString(1, "sally");
                preparedStatement.addBatch();
                preparedStatement.executeBatch();
                // intentionally not calling preparedStatement.clearBatch()
                preparedStatement.setString(1, "lowly");
                preparedStatement.addBatch();
                preparedStatement.setString(1, "pig will");
                preparedStatement.addBatch();
                preparedStatement.executeBatch();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecuteBatchStatement implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement();
            try {
                statement.addBatch("insert into employee (name) values ('huckle')");
                statement.addBatch("insert into employee (name) values ('sally')");
                statement.executeBatch();
                statement.clearBatch();
                statement.addBatch("insert into employee (name) values ('lowly')");
                statement.addBatch("insert into employee (name) values ('pig will')");
                statement.executeBatch();
            } finally {
                statement.close();
            }
        }
    }

    public static class BatchStatementNull implements AppUnderTest, TransactionMarker {

        private Connection delegatingConnection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            final Connection connection = Connections.createConnection((ConnectionType) args[0]);
            delegatingConnection = new DelegatingConnection(connection) {
                @Override
                public Statement createStatement() throws SQLException {
                    return new DelegatingStatement(this, connection.createStatement()) {
                        @Override
                        public void addBatch(String sql) throws SQLException {
                            super.addBatch("insert into employee (name) values ('1')");
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
                statement.addBatch(null);
                statement.executeBatch();
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteBatchStatementWithNoBatches
            implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement();
            try {
                statement.executeBatch();
            } finally {
                statement.close();
            }
        }
    }

    public static class ExecuteBatchPreparedStatementWithNoBatches
            implements AppUnderTest, TransactionMarker {

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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name) values (?)");
            try {
                preparedStatement.executeBatch();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecuteBatchPreparedStatementWithSingleBatch
            implements AppUnderTest, TransactionMarker {

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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name) values (?)");
            try {
                preparedStatement.setString(1, "huckle");
                preparedStatement.addBatch();
                preparedStatement.executeBatch();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecuteBatchStatementWithoutClear
            implements AppUnderTest, TransactionMarker {

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
            Statement statement = connection.createStatement();
            try {
                statement.addBatch("insert into employee (name) values ('huckle')");
                statement.addBatch("insert into employee (name) values ('sally')");
                statement.executeBatch();
                // intentionally not calling statement.clearBatch()
                statement.addBatch("insert into employee (name) values ('lowly')");
                statement.addBatch("insert into employee (name) values ('pig will')");
                statement.executeBatch();
            } finally {
                statement.close();
            }
        }
    }
}
