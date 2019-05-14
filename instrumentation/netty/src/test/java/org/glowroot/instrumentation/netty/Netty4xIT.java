/*
 * Copyright 2016-2019 the original author or authors.
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
package org.glowroot.instrumentation.netty;

import java.io.Serializable;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static org.assertj.core.api.Assertions.assertThat;

public class Netty4xIT {

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
        assertThat(incomingSpan.transactionName()).isEqualTo("/abc");
        assertThat(incomingSpan.message()).isEqualTo("GET /abc?xyz=123");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureHttpChunkedResponse() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpChunked.class);
        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/chunked");
        assertThat(incomingSpan.message()).isEqualTo("GET /chunked");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureHttpGetWithException() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGetWithException.class);
        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/exception");
        assertThat(incomingSpan.message()).isEqualTo("GET /exception");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    public static class ExecuteHttpGet implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            int port = Ports.getAvailable();
            Netty4xHttpServer server = new Netty4xHttpServer(port);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet("http://localhost:" + port + "/abc?xyz=123");
            int code = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (code != 200) {
                throw new IllegalStateException("Unexpected response code: " + code);
            }
            server.close();
        }
    }

    public static class ExecuteHttpChunked implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            int port = Ports.getAvailable();
            Netty4xHttpServer server = new Netty4xHttpServer(port);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet("http://localhost:" + port + "/chunked");
            int code = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (code != 200) {
                throw new IllegalStateException("Unexpected response code: " + code);
            }
            server.close();
        }
    }

    public static class ExecuteHttpGetWithException implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            int port = Ports.getAvailable();
            Netty4xHttpServer server = new Netty4xHttpServer(port);
            CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .disableAutomaticRetries()
                    .build();
            HttpGet httpGet = new HttpGet("http://localhost:" + port + "/exception");
            try {
                httpClient.execute(httpGet);
            } catch (NoHttpResponseException e) {
            }
            server.close();
        }
    }
}
