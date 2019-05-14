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
import java.sql.ResultSet;
import java.sql.Statement;
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
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class ResultSetJava7MethodIT {

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
    public void testResultSetJava7Method() throws Exception {

        assumeTrue(Connections.shouldRun(ConnectionType.COMMONS_DBCP_WRAPPED));

        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteStatementAndIterateOverResultsUsingJava7Method.class);

        // then
        assertThat(incomingSpan.error()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("SQL");
        assertThat(outgoingSpan.message()).isEqualTo("select * from employee");
        assertThat(outgoingSpan.detail()).doesNotContainKey("parameters");
        assertThat(outgoingSpan.detail()).containsEntry("rows", 1L);

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteStatementAndIterateOverResultsUsingJava7Method
            implements AppUnderTest, TransactionMarker {

        private Connection connection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            connection = Connections.createCommonsDbcpWrappedConnection();
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
                rs.next();
                // this method introduced in Java 7, so it should throw AbstractMethodError when
                // executed against org.apache.commons.dbcp.DelegatingResultSet
                try {
                    rs.getObject(1, String.class);
                } catch (AbstractMethodError e) {
                    // this is expected
                    return;
                }
                // this is not expected
                throw new AssertionError("org.apache.commons.dbcp.DelegatingResultSet"
                        + ".getObject(int, Class) did not throw AbstractMethodError");
            } finally {
                statement.close();
            }
        }
    }
}
