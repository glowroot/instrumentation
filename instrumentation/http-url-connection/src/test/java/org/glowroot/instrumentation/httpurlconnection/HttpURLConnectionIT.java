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
package org.glowroot.instrumentation.httpurlconnection;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleOutgoingSpanMessage;

public class HttpURLConnectionIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // need to use javaagent container since HttpURLConnection is in the bootstrap class loader
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
    public void shouldCaptureHttpGet() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGet.class, false);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan)
                .matches("http client request: GET http://localhost:[0-9]+/hello1/");
    }

    @Test
    public void shouldCaptureHttpGetWithQueryString() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGetWithQueryString.class, false);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan).matches(
                "http client request: GET http://localhost:[0-9]+/hello1\\?abc=xyz");
    }

    @Test
    public void shouldCaptureHttpPost() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpPost.class, false);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan)
                .matches("http client request: POST http://localhost:[0-9]+/hello1/");
    }

    @Test
    public void shouldCaptureHttpGetHTTPS() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGet.class, true);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan)
                .matches("http client request: GET https://localhost:[0-9]+/hello1/");
    }

    @Test
    public void shouldCaptureHttpGetWithQueryStringHTTPS() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGetWithQueryString.class, true);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan).matches(
                "http client request: GET https://localhost:[0-9]+/hello1\\?abc=xyz");
    }

    @Test
    public void shouldCaptureHttpPostHTTPS() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpPost.class, true);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan)
                .matches("http client request: POST https://localhost:[0-9]+/hello1/");
    }

    public static class ExecuteHttpGet extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            String protocol = useHttps() ? "https" : "http";
            URL obj = new URL(protocol + "://localhost:" + getPort() + "/hello1/");
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + connection.getResponseCode());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(connection.getHeaderField("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            InputStream content = connection.getInputStream();
            ByteStreams.exhaust(content);
            content.close();
        }
    }

    public static class ExecuteHttpGetWithQueryString extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            String protocol = useHttps() ? "https" : "http";
            URL obj = new URL(protocol + "://localhost:" + getPort() + "/hello1?abc=xyz");
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + connection.getResponseCode());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(connection.getHeaderField("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            InputStream content = connection.getInputStream();
            ByteStreams.exhaust(content);
            content.close();
        }
    }

    public static class ExecuteHttpPost extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            String protocol = useHttps() ? "https" : "http";
            URL obj = new URL(protocol + "://localhost:" + getPort() + "/hello1/");
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setDoOutput(true);
            connection.getOutputStream().write("some data".getBytes());
            connection.getOutputStream().close();
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + connection.getResponseCode());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(connection.getHeaderField("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            InputStream content = connection.getInputStream();
            ByteStreams.exhaust(content);
            content.close();
        }
    }
}
