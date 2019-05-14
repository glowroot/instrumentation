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

import java.util.Iterator;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
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

public class ApacheHttpClient3xIT {

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
    public void shouldCaptureHttpPost() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpPost.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: POST http://localhost:[0-9]+/hello2\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(3);
        assertThat(detail).containsEntry("Method", "POST");
        assertThat((String) detail.get("URI")).matches("http://localhost:[0-9]+/hello2\\?q");
        assertThat(detail).containsEntry("Result", 200);

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteHttpGet extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            HttpClient httpClient = new HttpClient();
            GetMethod httpGet = new GetMethod("http://localhost:" + getPort() + "/hello1?q");
            httpClient.executeMethod(httpGet);
            httpGet.releaseConnection();
            if (httpGet.getStatusCode() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + httpGet.getStatusCode());
            }
            // this it to test header propagation by instrumentation
            Header testHeader = httpGet.getResponseHeader("X-Test-Harness");
            if (testHeader == null || !"Yes".equals(testHeader.getValue())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
        }
    }

    public static class ExecuteHttpPost extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            HttpClient httpClient = new HttpClient();
            PostMethod httpPost = new PostMethod("http://localhost:" + getPort() + "/hello2?q");
            httpClient.executeMethod(httpPost);
            httpPost.releaseConnection();
            if (httpPost.getStatusCode() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + httpPost.getStatusCode());
            }
            // this it to test header propagation by instrumentation
            Header testHeader = httpPost.getResponseHeader("X-Test-Harness");
            if (testHeader == null || !"Yes".equals(testHeader.getValue())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
        }
    }
}
