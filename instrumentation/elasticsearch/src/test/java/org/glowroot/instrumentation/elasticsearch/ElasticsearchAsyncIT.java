/**
 * Copyright 2017-2019 the original author or authors.
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
package org.glowroot.instrumentation.elasticsearch;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
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

public class ElasticsearchAsyncIT {

    private static Container container;

    private static int elasticsearchPort;

    @BeforeClass
    public static void setUp() throws Exception {
        container = SharedSetupRunListener.getContainer();
        elasticsearchPort = SharedSetupRunListener.getElasticsearchPort();
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
    public void shouldCaptureDocumentPut() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteDocumentPut.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).isEqualTo("PUT testindex/testtype");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentGet() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteDocumentGet.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).isEqualTo("GET testindex/testtype");
        assertThat(outgoingSpan.detail()).containsKey("id");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldConcurrentlyAsyncExecuteSameStatement() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ConcurrentlyExecuteSameAsyncStatement.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        for (int j = 0; j < 100; j++) {
            OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
            assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
            assertThat(outgoingSpan.message()).isEqualTo("GET testindex/testtype");
            assertThat(outgoingSpan.detail()).containsKey("id");
        }
        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentUpdate() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteDocumentUpdate.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).isEqualTo("PUT testindex/testtype");
        assertThat(outgoingSpan.detail()).containsKey("id");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentDelete() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteDocumentDelete.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).isEqualTo("DELETE testindex/testtype");
        assertThat(outgoingSpan.detail()).containsKey("id");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentSearchWithoutSource() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteDocumentSearchWithoutSource.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).startsWith("SEARCH testindex/testtype {");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentSearchWithoutIndexesWithoutSource() throws Exception {
        // when
        IncomingSpan incomingSpan = container
                .execute(ExecuteDocumentSearchWithoutIndexesWithoutSource.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).startsWith("SEARCH _any/testtype {");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentSearchWithoutIndexesWithoutTypesWithoutSource()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(
                ExecuteDocumentSearchWithoutIndexesWithoutTypesWithoutSource.class,
                elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).startsWith("SEARCH / {");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentSearchWithMultipleIndexesWithMultipleTypesWithoutSource()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(
                ExecuteDocumentSearchWithMultipleIndexesWithMultipleTypesWithoutSource.class,
                elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message())
                .startsWith("SEARCH testindex,testindex2/testtype,testtype2 {");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureDocumentSearchWithSource() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.execute(ExecuteDocumentSearchWithSource.class, elasticsearchPort);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.type()).isEqualTo("Elasticsearch");
        assertThat(outgoingSpan.message()).startsWith("SEARCH testindex/testtype {");
        assertThat(outgoingSpan.detail()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteDocumentPut implements AppUnderTest, TransactionMarker {

        private TransportClient client;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentGet implements AppUnderTest, TransactionMarker {

        private TransportClient client;
        private String documentId;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            IndexResponse response = client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            documentId = response.getId();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareGet("testindex", "testtype", documentId)
                    .execute()
                    .get();
        }
    }

    public static class ConcurrentlyExecuteSameAsyncStatement
            implements AppUnderTest, TransactionMarker {

        private TransportClient client;
        private String documentId;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            IndexResponse response = client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            documentId = response.getId();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            List<ActionFuture<GetResponse>> futures = Lists.newArrayList();
            for (int i = 0; i < 100; i++) {
                futures.add(client.prepareGet("testindex", "testtype", documentId)
                        .execute());
            }
            for (ActionFuture<GetResponse> future : futures) {
                future.get();
            }
        }
    }

    public static class ExecuteDocumentUpdate implements AppUnderTest, TransactionMarker {

        private TransportClient client;
        private String documentId;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            IndexResponse response = client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            documentId = response.getId();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareUpdate("testindex", "testtype", documentId)
                    .setDoc("xyz", "some updated text")
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentDelete implements AppUnderTest, TransactionMarker {

        private TransportClient client;
        private String documentId;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            IndexResponse response = client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            documentId = response.getId();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareDelete("testindex", "testtype", documentId)
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentSearchWithoutSource
            implements AppUnderTest, TransactionMarker {

        private TransportClient client;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareSearch("testindex")
                    .setTypes("testtype")
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentSearchWithoutIndexesWithoutSource
            implements AppUnderTest, TransactionMarker {

        private TransportClient client;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareSearch()
                    .setTypes("testtype")
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentSearchWithoutTypesWithoutSource
            implements AppUnderTest, TransactionMarker {

        private TransportClient client;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareSearch("testindex")
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentSearchWithoutIndexesWithoutTypesWithoutSource
            implements AppUnderTest, TransactionMarker {

        private TransportClient client;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareSearch()
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentSearchWithMultipleIndexesWithMultipleTypesWithoutSource
            implements AppUnderTest, TransactionMarker {

        private TransportClient client;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            client.prepareIndex("testindex2", "testtype2")
                    .setSource("abc2", 11, "xyz2", "some text")
                    .get();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareSearch("testindex", "testindex2")
                    .setTypes("testtype", "testtype2")
                    .execute()
                    .get();
        }
    }

    public static class ExecuteDocumentSearchWithSource implements AppUnderTest, TransactionMarker {

        private TransportClient client;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            client = Util.client(new InetSocketAddress("127.0.0.1", (Integer) args[0]));
            client.prepareIndex("testindex", "testtype")
                    .setSource("abc", 11, "xyz", "some text")
                    .get();
            transactionMarker();
            client.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            client.prepareSearch("testindex")
                    .setTypes("testtype")
                    .setQuery(QueryBuilders.termQuery("xyz", "text"))
                    .execute()
                    .get();
        }
    }
}
