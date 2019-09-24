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
package org.glowroot.instrumentation.apachehttpclient;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

import com.google.common.io.ByteStreams;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.util.ExecuteHttpBase;

import static org.assertj.core.api.Assertions.assertThat;

public class ApacheHttpClient4xIT {

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
    public void shouldCaptureHttpGet() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGet.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: GET http://localhost:[0-9]+/hello1\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(3);
        assertThat(detail).containsEntry("Method", "GET");
        assertThat((String) detail.get("URI")).matches("http://localhost:[0-9]+/hello1\\?q");
        assertThat(detail).containsEntry("Result", 200);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureHttpGetWithResponseHandler() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGetWithResponseHandler.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: GET http://localhost:[0-9]+/hello1\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(4);
        assertThat(detail).containsEntry("Method", "GET");
        assertThat((String) detail.get("Host")).matches("http://localhost:[0-9]+");
        assertThat(detail).containsEntry("URI", "/hello1?q");
        assertThat(detail).containsEntry("Result", 200);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureHttpGetUsingHttpHostArg() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGetUsingHttpHostArg.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: GET http://localhost:[0-9]+/hello2\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(4);
        assertThat(detail).containsEntry("Method", "GET");
        assertThat((String) detail.get("Host")).matches("http://localhost:[0-9]+");
        assertThat(detail).containsEntry("URI", "/hello2?q");
        assertThat(detail).containsEntry("Result", 200);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureHttpPost() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpPost.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: POST http://localhost:[0-9]+/hello3\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(3);
        assertThat(detail).containsEntry("Method", "POST");
        assertThat((String) detail.get("URI")).matches("http://localhost:[0-9]+/hello3\\?q");
        assertThat(detail).containsEntry("Result", 200);

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureHttpPostUsingHttpHostArg() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpPostUsingHttpHostArg.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: POST http://localhost:[0-9]+/hello4\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(4);
        assertThat(detail).containsEntry("Method", "POST");
        assertThat((String) detail.get("Host")).matches("http://localhost:[0-9]+");
        assertThat(detail).containsEntry("URI", "/hello4?q");
        assertThat(detail).containsEntry("Result", 200);

        assertThat(i.hasNext()).isFalse();
    }

    private static HttpClient createHttpClient() throws Exception {
        try {
            return (HttpClient) Class.forName("org.apache.http.impl.client.HttpClients")
                    .getMethod("createDefault").invoke(null);
        } catch (ClassNotFoundException e) {
            // httpclient prior to 4.3.0
            return (HttpClient) Class
                    .forName("org.apache.http.impl.client.DefaultHttpClient").newInstance();
        }
    }

    private static void closeHttpClient(HttpClient httpClient) throws Exception {
        try {
            Class<?> closeableHttpClientClass =
                    Class.forName("org.apache.http.impl.client.CloseableHttpClient");
            Method closeMethod = closeableHttpClientClass.getMethod("close");
            closeMethod.invoke(httpClient);
        } catch (ClassNotFoundException e) {
            Method getConnectionManagerMethod = HttpClient.class.getMethod("getConnectionManager");
            Object connectionManager = getConnectionManagerMethod.invoke(httpClient);
            Class<?> clientConnectionManagerClass =
                    Class.forName("org.apache.http.conn.ClientConnectionManager");
            Method shutdownMethod = clientConnectionManagerClass.getMethod("shutdown");
            shutdownMethod.invoke(connectionManager);
        }
    }

    public static class ExecuteHttpGet extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            HttpClient httpClient = createHttpClient();
            HttpGet httpGet = new HttpGet("http://localhost:" + getPort() + "/hello1?q");
            HttpResponse response = httpClient.execute(httpGet);
            int responseStatusCode = response.getStatusLine().getStatusCode();
            if (responseStatusCode != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + responseStatusCode);
            }
            // this it to test header propagation by instrumentation
            Header testHeader = response.getFirstHeader("X-Test-Harness");
            if (testHeader == null || !"Yes".equals(testHeader.getValue())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            InputStream content = response.getEntity().getContent();
            ByteStreams.exhaust(content);
            content.close();
            closeHttpClient(httpClient);
        }
    }

    public static class ExecuteHttpGetWithResponseHandler extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            HttpClient httpClient = createHttpClient();
            HttpGet httpGet = new HttpGet("http://localhost:" + getPort() + "/hello1?q");
            ResponseData responseData =
                    httpClient.execute(httpGet, new ResponseHandler<ResponseData>() {
                        @Override
                        public ResponseData handleResponse(HttpResponse response)
                                throws IOException {
                            int responseStatusCode = response.getStatusLine().getStatusCode();
                            Header testHeader = response.getFirstHeader("X-Test-Harness");
                            InputStream content = response.getEntity().getContent();
                            ByteStreams.exhaust(content);
                            content.close();
                            return new ResponseData(responseStatusCode, testHeader);
                        }
                    });
            if (responseData.statusCode != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + responseData.statusCode);
            }
            // this it to test header propagation by instrumentation
            Header testHeader = responseData.testHeader;
            if (testHeader == null || !"Yes".equals(testHeader.getValue())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            closeHttpClient(httpClient);
        }
    }

    public static class ExecuteHttpGetUsingHttpHostArg extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            HttpClient httpClient = createHttpClient();
            HttpHost httpHost = new HttpHost("localhost", getPort());
            HttpGet httpGet = new HttpGet("/hello2?q");
            HttpResponse response = httpClient.execute(httpHost, httpGet);
            int responseStatusCode = response.getStatusLine().getStatusCode();
            if (responseStatusCode != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + responseStatusCode);
            }
            // this it to test header propagation by instrumentation
            Header testHeader = response.getFirstHeader("X-Test-Harness");
            if (testHeader == null || !"Yes".equals(testHeader.getValue())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            InputStream content = response.getEntity().getContent();
            ByteStreams.exhaust(content);
            content.close();
            closeHttpClient(httpClient);
        }
    }

    public static class ExecuteHttpPost extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            HttpClient httpClient = createHttpClient();
            HttpPost httpPost = new HttpPost("http://localhost:" + getPort() + "/hello3?q");
            HttpResponse response = httpClient.execute(httpPost);
            int responseStatusCode = response.getStatusLine().getStatusCode();
            if (responseStatusCode != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + responseStatusCode);
            }
            // this it to test header propagation by instrumentation
            Header testHeader = response.getFirstHeader("X-Test-Harness");
            if (testHeader == null || !"Yes".equals(testHeader.getValue())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            InputStream content = response.getEntity().getContent();
            ByteStreams.exhaust(content);
            content.close();
            closeHttpClient(httpClient);
        }
    }

    public static class ExecuteHttpPostUsingHttpHostArg extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            HttpClient httpClient = createHttpClient();
            HttpHost httpHost = new HttpHost("localhost", getPort());
            HttpPost httpPost = new HttpPost("/hello4?q");
            HttpResponse response = httpClient.execute(httpHost, httpPost);
            int responseStatusCode = response.getStatusLine().getStatusCode();
            if (responseStatusCode != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + responseStatusCode);
            }
            // this it to test header propagation by instrumentation
            Header testHeader = response.getFirstHeader("X-Test-Harness");
            if (testHeader == null || !"Yes".equals(testHeader.getValue())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            InputStream content = response.getEntity().getContent();
            ByteStreams.exhaust(content);
            content.close();
            closeHttpClient(httpClient);
        }
    }

    private static class ResponseData {

        private final int statusCode;
        private final Header testHeader;

        private ResponseData(int statusCode, Header testHeader) {
            this.statusCode = statusCode;
            this.testHeader = testHeader;
        }
    }
}
