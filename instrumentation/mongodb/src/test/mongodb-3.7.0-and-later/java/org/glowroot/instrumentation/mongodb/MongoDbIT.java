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
import java.util.Random;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
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

import static org.assertj.core.api.Assertions.assertThat;

public class MongoDbIT {

    private static int mongoPort;
    private static String dockerContainerName;
    private static MongoClient mongoClient;

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        mongoPort = Ports.getAvailable();
        dockerContainerName = Docker.start("mongo", "-p", mongoPort + ":27017");
        mongoClient = MongoClients.create("mongodb://localhost:" + mongoPort);
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
        MongoDatabase database = mongoClient.getDatabase("testdb");
        database.getCollection("test").drop();
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
    public void shouldCaptureDistinct() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteDistinct.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("distinct testdb.test");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureFindZeroRecords() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteFindZeroRecords.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("find testdb.test");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureFindOneRecord() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteFindOneRecord.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("find testdb.test");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureFindWithFilter() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteFindWithFilter.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        String message = outgoingSpan.message();
        System.out.println(message);
        if (!message.equals("find testdb.test {\"test3\": \"?\"}")) {
            // versions 3.7.0 through 3.9.x have extra spaces
            assertThat(message).isEqualTo("find testdb.test { \"test3\" : \"?\" }");
        }

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureFindAndGetMoreRecords() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteFindAndGetMoreRecords.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("find testdb.test");

        outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("getMore testdb.test");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureAggregate() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteAggregate.class, mongoPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("MongoDB");
        assertThat(outgoingSpan.message()).isEqualTo("aggregate testdb.test");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteCount extends DoMongoDB {

        @Override
        @SuppressWarnings("deprecation")
        public void transactionMarker() {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            // intentionally using deprecated method here so it will work with mongo driver 3.7.0
            collection.count();
        }
    }

    public static class ExecuteDistinct extends DoMongoDB {

        @Override
        public void transactionMarker() {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            MongoCursor<String> i = collection.distinct("abc", String.class).iterator();
            while (i.hasNext()) {
            }
        }
    }

    public static class ExecuteFindZeroRecords extends DoMongoDB {

        @Override
        public void transactionMarker() throws InterruptedException {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            MongoCursor<Document> i = collection.find().iterator();
            while (i.hasNext()) {
            }
        }
    }

    public static class ExecuteFindOneRecord extends DoMongoDB {

        @Override
        protected void beforeTransactionMarker() {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            Document document = new Document("test1", "test2")
                    .append("test3", "test4");
            collection.insertOne(document);
        }

        @Override
        public void transactionMarker() throws InterruptedException {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            MongoCursor<Document> i = collection.find().iterator();
            while (i.hasNext()) {
                i.next();
            }
        }
    }

    public static class ExecuteFindWithFilter extends DoMongoDB {

        @Override
        protected void beforeTransactionMarker() {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            Document document = new Document("test1", "test2")
                    .append("test3", "test4");
            collection.insertOne(document);
        }

        @Override
        public void transactionMarker() throws InterruptedException {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            Document filter = new Document();
            filter.append("test3", "test4");
            MongoCursor<Document> i = collection.find(filter).iterator();
            while (i.hasNext()) {
                i.next();
            }
        }
    }

    public static class ExecuteFindAndGetMoreRecords extends DoMongoDB {

        @Override
        protected void beforeTransactionMarker() {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            int size = 1024 * 1024;
            StringBuilder sb = new StringBuilder(size);
            Random random = new Random();
            for (int i = 0; i < 10; i++) {
                sb.setLength(0);
                for (int j = 0; j < size; j++) {
                    sb.append('a' + random.nextInt(26));
                }
                Document document = new Document("test1", "test2")
                        .append("test3", sb.toString());
                collection.insertOne(document);
            }
        }

        @Override
        public void transactionMarker() throws InterruptedException {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            MongoCursor<Document> i = collection.find().iterator();
            while (i.hasNext()) {
                i.next();
            }
        }
    }

    public static class ExecuteAggregate extends DoMongoDB {

        @Override
        public void transactionMarker() {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            MongoCursor<Document> i = collection.aggregate(ImmutableList.<Bson>of()).iterator();
            while (i.hasNext()) {
                i.next();
            }
        }
    }

    public static class ExecuteInsert extends DoMongoDB {

        @Override
        public void transactionMarker() {
            MongoDatabase database = mongoClient.getDatabase("testdb");
            MongoCollection<Document> collection = database.getCollection("test");
            Document document = new Document("test1", "test2")
                    .append("test3", "test4");
            collection.insertOne(document);
        }
    }

    private abstract static class DoMongoDB implements AppUnderTest, TransactionMarker {

        protected MongoClient mongoClient;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            mongoClient = MongoClients.create("mongodb://localhost:" + args[0]);
            beforeTransactionMarker();
            transactionMarker();
        }

        protected void beforeTransactionMarker() {}
    }
}
