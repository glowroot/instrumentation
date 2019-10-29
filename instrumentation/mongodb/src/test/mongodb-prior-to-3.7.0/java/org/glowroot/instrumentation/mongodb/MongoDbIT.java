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
package org.glowroot.instrumentation.mongodb;

import java.io.Serializable;
import java.util.Iterator;

import com.google.common.base.Stopwatch;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.Mongo;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.util.Docker;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class MongoDbIT {

    private static int mongoPort;
    private static String dockerContainerName;
    private static Mongo mongoClient;

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        mongoPort = Ports.getAvailable();
        dockerContainerName = Docker.start("mongo", "-p", mongoPort + ":27017");
        waitForMongoDB(mongoPort);
        mongoClient = new Mongo("localhost", mongoPort);
        container = Containers.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
        Docker.stop(dockerContainerName);
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
        DB database = mongoClient.getDB("testdb");
        database.getCollection("test").drop();
    }

    @Test
    public void shouldCaptureInsert() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteInsert.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("insert testdb.test");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureCount() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteCount.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("count testdb.test");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureFind() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteFind.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("find testdb.test");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    private static void waitForMongoDB(int port) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(SECONDS) < 10) {
            try {
                Mongo mongoClient = new Mongo("localhost", port);
                DB database = mongoClient.getDB("testdb");
                DBCollection collection = database.getCollection("test");
                collection.getCount();
                break;
            } catch (Exception e) {
                // some versions throw IllegalStateException, MongoInternalException
                Thread.sleep(100);
            }
        }
    }

    public static class ExecuteInsert extends DoMongoDB {
        @Override
        public void transactionMarker() {
            @SuppressWarnings("deprecation")
            DB database = mongoClient.getDB("testdb");
            DBCollection collection = database.getCollection("test");
            BasicDBObject document = new BasicDBObject("test1", "test2")
                    .append("test3", "test4");
            collection.insert(document);
        }
    }

    public static class ExecuteCount extends DoMongoDB {
        @Override
        public void transactionMarker() {
            @SuppressWarnings("deprecation")
            DB database = mongoClient.getDB("testdb");
            DBCollection collection = database.getCollection("test");
            collection.getCount();
        }
    }

    public static class ExecuteFind extends DoMongoDB {
        @Override
        public void transactionMarker() {
            @SuppressWarnings("deprecation")
            DB database = mongoClient.getDB("testdb");
            DBCollection collection = database.getCollection("test");
            DBCursor i = collection.find();
            while (i.hasNext()) {
                i.next();
            }
        }
    }

    private abstract static class DoMongoDB implements AppUnderTest, TransactionMarker {

        protected Mongo mongoClient;

        @Override
        @SuppressWarnings("deprecation")
        public void executeApp(Serializable... args) throws Exception {
            mongoClient = new Mongo("localhost", (Integer) args[0]);
            transactionMarker();
        }
    }
}
