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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.dbcp.DelegatingConnection;
import org.apache.commons.dbcp.DelegatingPreparedStatement;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.jdbc.Connections.ConnectionType;
import org.glowroot.instrumentation.jdbc.boot.JdbcInstrumentationProperties;
import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;

public class PreparedStatementIT {

    private static final String INSTRUMENTATION_ID = "jdbc";

    private static final List<String> H2_EXTRA_LOB_QUERIES =
            ImmutableList.of("SELECT MAX(LOB) FROM INFORMATION_SCHEMA.LOB_MAP",
                    "SELECT MAX(ID) FROM INFORMATION_SCHEMA.LOBS");

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
    public void testPreparedStatement() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecutePreparedStatementAndIterateOverResults.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("select * from employee where name like ?");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", ImmutableList.of("john%"));
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementQuery() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecutePreparedStatementQueryAndIterateOverResults.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("select * from employee where name like ?");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", ImmutableList.of("john%"));
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementUpdate() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecutePreparedStatementUpdate.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("update employee set name = ?");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", ImmutableList.of("nobody"));
        assertThat(outgoingSpan.detail()).containsEntry("rows", 3L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementInsertWithGeneratedKeys() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecutePreparedStatementInsertWithGeneratedKeys.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name) values (?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", ImmutableList.of("nobody"));
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementLargeParamSetFirst() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecutePreparedStatementLargeParamSetFirst.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .startsWith("select * from employee where name like ? and name like ? ");
        List<String> parameters = Lists.newArrayList();
        for (int j = 0; j < 100; j++) {
            parameters.add("john%");
        }
        assertThat(outgoingSpan.detail()).containsEntry("parameters", parameters);
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementNullSql() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(PreparedStatementNullSql.class,
                Connections.getConnectionType());
        // then
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testPreparedStatementThrowing() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecutePreparedStatementThrowing.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("select * from employee where name like ?");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", ImmutableList.of("john%"));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");
        assertThat(outgoingSpan.exception()).isNotNull();
        assertThat(outgoingSpan.exception().type()).isEqualTo(SQLException.class);
        assertThat(outgoingSpan.exception().message()).isEqualTo("An execute failure");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementWithTonsOfBindParameters() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(
                ExecutePreparedStatementWithTonsOfBindParametersAndIterateOverResults.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        StringBuilder sql = new StringBuilder("select * from employee where name like ?");
        for (int j = 0; j < 200; j++) {
            sql.append(" and name like ?");
        }
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message()).isEqualTo(sql.toString());
        List<String> parameters = Lists.newArrayList();
        for (int j = 0; j < 201; j++) {
            parameters.add("john%");
        }
        assertThat(outgoingSpan.detail()).containsEntry("parameters", parameters);
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementWithoutBindParameters() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.<String>of());

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecutePreparedStatementAndIterateOverResults.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("select * from employee where name like ?");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementWithSetNull() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.of(".*"));

        // whens
        IncomingSpan incomingSpan = container.execute(ExecutePreparedStatementWithSetNull.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name, misc) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", Arrays.asList(null, null));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementWithBinary() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.of(".*"));

        // when
        IncomingSpan incomingSpan = container.execute(ExecutePreparedStatementWithBinary.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name, misc) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters",
                ImmutableList.of("jane", "0x00010203040506070809"));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert /**/ into employee (name, misc) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters",
                ImmutableList.of("jane", "{10 bytes}"));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementWithBinaryUsingSetObject() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.of(".*"));

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecutePreparedStatementWithBinaryUsingSetObject.class,
                        Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name, misc) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters",
                ImmutableList.of("jane", "0x00010203040506070809"));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert /**/ into employee (name, misc) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters",
                ImmutableList.of("jane", "{10 bytes}"));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementWithBinaryStream() throws Exception {

        if (Connections.getConnectionType() == ConnectionType.COMMONS_DBCP_WRAPPED) {
            NoSuchMethodException exception = null;
            try {
                org.apache.commons.dbcp.DelegatingStatement.class.getMethod("setBinaryStream",
                        InputStream.class);
            } catch (NoSuchMethodException e) {
                exception = e;
            }
            Assume.assumeNoException(exception);
        }

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.of(".*"));

        // when
        IncomingSpan incomingSpan = container.execute(
                ExecutePreparedStatementWithBinaryStream.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = getTraceEntriesWithoutH2ExtraLobQueries(incomingSpan).iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name, misc) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters",
                ImmutableList.of("jane", "{stream:ByteArrayInputStream}"));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testPreparedStatementWithCharacterStream() throws Exception {

        if (Connections.getConnectionType() == ConnectionType.COMMONS_DBCP_WRAPPED) {
            NoSuchMethodException exception = null;
            try {
                org.apache.commons.dbcp.DelegatingStatement.class.getMethod("setCharacterStream",
                        Reader.class);
            } catch (NoSuchMethodException e) {
                exception = e;
            }
            Assume.assumeNoException(exception);
        }

        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.of(".*"));

        // when
        IncomingSpan incomingSpan = container.execute(
                ExecutePreparedStatementWithCharacterStream.class, Connections.getConnectionType());

        // then
        Iterator<Span> i = getTraceEntriesWithoutH2ExtraLobQueries(incomingSpan).iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("insert into employee (name, misc2) values (?, ?)");
        assertThat(outgoingSpan.detail()).containsEntry("parameters",
                ImmutableList.of("jane", "{stream:StringReader}"));
        assertThat(outgoingSpan.detail()).doesNotContainKey("rows");

        assertThat(i.hasNext()).isFalse();
    }

    private List<Span> getTraceEntriesWithoutH2ExtraLobQueries(IncomingSpan incomingSpan) {
        List<Span> filtered = Lists.newArrayList();
        for (Span span : incomingSpan.childSpans()) {
            if (!H2_EXTRA_LOB_QUERIES.contains(span.message())) {
                filtered.add(span);
            }
        }
        return filtered;
    }

    @Test
    public void testPreparedStatementWithClear() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureBindParametersIncludes",
                ImmutableList.of(".*"));

        // when
        IncomingSpan incomingSpan = container.execute(ExecutePreparedStatementWithClear.class,
                Connections.getConnectionType());

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.dest()).isEqualTo(Connections.getDest());
        assertThat(outgoingSpan.message())
                .isEqualTo("select * from employee where name like ?");
        assertThat(outgoingSpan.detail()).containsEntry("parameters", ImmutableList.of("john%"));
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecutePreparedStatementAndIterateOverResults
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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("select * from employee where name like ?");
            try {
                preparedStatement.setString(1, "john%");
                preparedStatement.execute();
                ResultSet rs = preparedStatement.getResultSet();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementQueryAndIterateOverResults
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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("select * from employee where name like ?");
            try {
                preparedStatement.setString(1, "john%");
                ResultSet rs = preparedStatement.executeQuery();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementUpdate implements AppUnderTest, TransactionMarker {

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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("update employee set name = ?");
            try {
                preparedStatement.setString(1, "nobody");
                preparedStatement.executeUpdate();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementInsertWithGeneratedKeys
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
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "insert into employee (name) values (?)", Statement.RETURN_GENERATED_KEYS);
            try {
                preparedStatement.setString(1, "nobody");
                preparedStatement.executeUpdate();
                ResultSet rs = preparedStatement.getGeneratedKeys();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementLargeParamSetFirst
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
            String sql = "select * from employee where name like ?";
            for (int i = 0; i < 99; i++) {
                sql += " and name like ?";
            }
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            try {
                preparedStatement.setString(100, "john%");
                for (int i = 0; i < 99; i++) {
                    preparedStatement.setString(i + 1, "john%");
                }
                preparedStatement.execute();
                ResultSet rs = preparedStatement.getResultSet();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class PreparedStatementNullSql implements AppUnderTest, TransactionMarker {

        private Connection delegatingConnection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            Connection connection = Connections.createConnection((ConnectionType) args[0]);
            delegatingConnection = new DelegatingConnection(connection) {
                @Override
                public PreparedStatement prepareStatement(String sql) throws SQLException {
                    return super.prepareStatement("select 1 from employee");
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
            delegatingConnection.prepareStatement(null);
        }
    }

    public static class ExecutePreparedStatementThrowing
            implements AppUnderTest, TransactionMarker {

        private Connection delegatingConnection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            Connection connection = Connections.createConnection((ConnectionType) args[0]);
            delegatingConnection = new DelegatingConnection(connection) {
                @Override
                public PreparedStatement prepareStatement(String sql) throws SQLException {
                    return new DelegatingPreparedStatement(this, super.prepareStatement(sql)) {
                        @Override
                        public boolean execute() throws SQLException {
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
            PreparedStatement preparedStatement = delegatingConnection
                    .prepareStatement("select * from employee where name like ?");
            try {
                preparedStatement.setString(1, "john%");
                preparedStatement.execute();
            } catch (SQLException e) {
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementWithTonsOfBindParametersAndIterateOverResults
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
            StringBuilder sql = new StringBuilder("select * from employee where name like ?");
            for (int i = 0; i < 200; i++) {
                sql.append(" and name like ?");
            }
            PreparedStatement preparedStatement = connection.prepareStatement(sql.toString());
            try {
                for (int i = 1; i < 202; i++) {
                    preparedStatement.setString(i, "john%");
                }
                preparedStatement.execute();
                ResultSet rs = preparedStatement.getResultSet();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementWithSetNull
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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name, misc) values (?, ?)");
            try {
                preparedStatement.setNull(1, Types.VARCHAR);
                preparedStatement.setNull(2, Types.BINARY);
                preparedStatement.execute();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementWithBinary
            implements AppUnderTest, TransactionMarker {

        static {
            JdbcInstrumentationProperties.setDisplayBinaryParameterAsHex(
                    "insert into employee (name, misc) values (?, ?)", 2);
        }
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
            byte[] bytes = new byte[10];
            for (int i = 0; i < 10; i++) {
                bytes[i] = (byte) i;
            }
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name, misc) values (?, ?)");
            PreparedStatement preparedStatement2 = connection
                    .prepareStatement("insert /**/ into employee (name, misc) values (?, ?)");
            try {
                preparedStatement.setString(1, "jane");
                preparedStatement.setBytes(2, bytes);
                preparedStatement.execute();
                preparedStatement2.setString(1, "jane");
                preparedStatement2.setBytes(2, bytes);
                preparedStatement2.execute();
            } finally {
                preparedStatement.close();
                preparedStatement2.close();
            }
        }
    }

    public static class ExecutePreparedStatementWithBinaryUsingSetObject
            implements AppUnderTest, TransactionMarker {

        static {
            JdbcInstrumentationProperties.setDisplayBinaryParameterAsHex(
                    "insert into employee (name, misc) values (?, ?)", 2);
        }
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
            byte[] bytes = new byte[10];
            for (int i = 0; i < 10; i++) {
                bytes[i] = (byte) i;
            }
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name, misc) values (?, ?)");
            PreparedStatement preparedStatement2 = connection
                    .prepareStatement("insert /**/ into employee (name, misc) values (?, ?)");
            try {
                preparedStatement.setString(1, "jane");
                preparedStatement.setObject(2, bytes);
                preparedStatement.execute();
                preparedStatement2.setString(1, "jane");
                preparedStatement2.setObject(2, bytes);
                preparedStatement2.execute();
            } finally {
                preparedStatement.close();
                preparedStatement2.close();
            }
        }
    }

    public static class ExecutePreparedStatementWithBinaryStream
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
            byte[] bytes = new byte[10];
            for (int i = 0; i < 10; i++) {
                bytes[i] = (byte) i;
            }
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name, misc) values (?, ?)");
            try {
                preparedStatement.setString(1, "jane");
                preparedStatement.setBinaryStream(2, new ByteArrayInputStream(bytes));
                preparedStatement.execute();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementWithCharacterStream
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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("insert into employee (name, misc2) values (?, ?)");
            try {
                preparedStatement.setString(1, "jane");
                preparedStatement.setCharacterStream(2, new StringReader("abc"));
                preparedStatement.execute();
            } finally {
                preparedStatement.close();
            }
        }
    }

    public static class ExecutePreparedStatementWithClear
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
            PreparedStatement preparedStatement =
                    connection.prepareStatement("select * from employee where name like ?");
            try {
                preparedStatement.setString(1, "na%");
                preparedStatement.clearParameters();
                preparedStatement.setString(1, "john%");
                preparedStatement.execute();
                ResultSet rs = preparedStatement.getResultSet();
                while (rs.next()) {
                    rs.getString(1);
                }
            } finally {
                preparedStatement.close();
            }
        }
    }
}
