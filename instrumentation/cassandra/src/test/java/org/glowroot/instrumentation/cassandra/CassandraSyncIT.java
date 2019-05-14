/**
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
package org.glowroot.instrumentation.cassandra;

import java.io.Serializable;
import java.util.Iterator;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;

public class CassandraSyncIT {

    private static Container container;

    private static int cassandraPort;

    @BeforeClass
    public static void setUp() throws Exception {
        container = SharedSetupRunListener.getContainer();
        cassandraPort = SharedSetupRunListener.getCassandraPort();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        SharedSetupRunListener.close(container);
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
    }

    @Test
    public void shouldExecuteStatement() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteStatement.class, cassandraPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Cassandra");
        assertThat(outgoingSpan.message()).isEqualTo("SELECT * FROM test.users");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 10L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldExecuteStatementReturningNoRecords() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteStatementReturningNoRecords.class, cassandraPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Cassandra");
        assertThat(outgoingSpan.message())
                .isEqualTo("SELECT * FROM test.users where id = 12345");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 0L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldExecuteStatementReturningNoRecordsCheckIsExhausted() throws Exception {
        // when
        IncomingSpan incomingSpan = container
                .execute(ExecuteStatementReturningNoRecordsCheckIsExhausted.class, cassandraPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Cassandra");
        assertThat(outgoingSpan.message())
                .isEqualTo("SELECT * FROM test.users where id = 12345");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 0L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldIterateUsingOneAndAll() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(IterateUsingOneAndAll.class, cassandraPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Cassandra");
        assertThat(outgoingSpan.message()).isEqualTo("SELECT * FROM test.users");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 10L);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldExecuteBoundStatement() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteBoundStatement.class, cassandraPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Cassandra");
        assertThat(outgoingSpan.message())
                .isEqualTo("INSERT INTO test.users (id,  fname, lname) VALUES (?, ?, ?)");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldExecuteBatchStatement() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteBatchStatement.class, cassandraPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Cassandra");
        assertThat(outgoingSpan.message())
                .isEqualTo("[batch] INSERT INTO test.users (id,  fname, lname)"
                        + " VALUES (100, 'f100', 'l100'),"
                        + " INSERT INTO test.users (id,  fname, lname)"
                        + " VALUES (101, 'f101', 'l101'),"
                        + " 10 x INSERT INTO test.users (id,  fname, lname) VALUES (?, ?, ?),"
                        + " INSERT INTO test.users (id,  fname, lname)"
                        + " VALUES (300, 'f300', 'l300')");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteStatement implements AppUnderTest, TransactionMarker {

        private Session session;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            session = Sessions.createSession((Integer) args[0]);
            transactionMarker();
            Sessions.closeSession(session);
        }

        @Override
        public void transactionMarker() throws Exception {
            ResultSet results = session.execute("SELECT * FROM test.users");
            for (Row row : results) {
                row.getInt("id");
            }
        }
    }

    public static class ExecuteStatementReturningNoRecords
            implements AppUnderTest, TransactionMarker {

        private Session session;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            session = Sessions.createSession((Integer) args[0]);
            transactionMarker();
            Sessions.closeSession(session);
        }

        @Override
        public void transactionMarker() throws Exception {
            ResultSet results = session.execute("SELECT * FROM test.users where id = 12345");
            for (Row row : results) {
                row.getInt("id");
            }
        }
    }

    public static class ExecuteStatementReturningNoRecordsCheckIsExhausted
            implements AppUnderTest, TransactionMarker {

        private Session session;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            session = Sessions.createSession((Integer) args[0]);
            transactionMarker();
            Sessions.closeSession(session);
        }

        @Override
        public void transactionMarker() throws Exception {
            ResultSet results = session.execute("SELECT * FROM test.users where id = 12345");
            if (results.isExhausted()) {
                return;
            }
            for (Row row : results) {
                row.getInt("id");
            }
        }
    }

    public static class IterateUsingOneAndAll implements AppUnderTest, TransactionMarker {

        private Session session;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            session = Sessions.createSession((Integer) args[0]);
            transactionMarker();
            Sessions.closeSession(session);
        }

        @Override
        public void transactionMarker() throws Exception {
            ResultSet results = session.execute("SELECT * FROM test.users");
            results.one();
            results.one();
            results.one();
            results.all();
        }
    }

    public static class ExecuteBoundStatement implements AppUnderTest, TransactionMarker {

        private Session session;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            session = Sessions.createSession((Integer) args[0]);
            transactionMarker();
            Sessions.closeSession(session);
        }

        @Override
        public void transactionMarker() throws Exception {
            PreparedStatement preparedStatement =
                    session.prepare("INSERT INTO test.users (id,  fname, lname) VALUES (?, ?, ?)");
            BoundStatement boundStatement = new BoundStatement(preparedStatement);
            boundStatement.bind(100, "f100", "l100");
            session.execute(boundStatement);
        }
    }

    public static class ExecuteBatchStatement implements AppUnderTest, TransactionMarker {

        private Session session;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            session = Sessions.createSession((Integer) args[0]);
            transactionMarker();
            Sessions.closeSession(session);
        }

        @Override
        public void transactionMarker() throws Exception {
            BatchStatement batchStatement = new BatchStatement();
            batchStatement.add(new SimpleStatement(
                    "INSERT INTO test.users (id,  fname, lname) VALUES (100, 'f100', 'l100')"));
            batchStatement.add(new SimpleStatement(
                    "INSERT INTO test.users (id,  fname, lname) VALUES (101, 'f101', 'l101')"));
            PreparedStatement preparedStatement =
                    session.prepare("INSERT INTO test.users (id,  fname, lname) VALUES (?, ?, ?)");
            for (int i = 200; i < 210; i++) {
                BoundStatement boundStatement = new BoundStatement(preparedStatement);
                boundStatement.bind(i, "f" + i, "l" + i);
                batchStatement.add(boundStatement);
            }
            batchStatement.add(new SimpleStatement(
                    "INSERT INTO test.users (id,  fname, lname) VALUES (300, 'f300', 'l300')"));
            session.execute(batchStatement);
        }
    }
}
