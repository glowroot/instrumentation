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
package org.glowroot.instrumentation.asynchttpclient;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
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
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleOutgoingSpanMessage;

// TODO test against AsyncHttpClient providers jdk and grizzly (in addition to the default netty)
public class AsyncHttpClient1xIT {

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
                .matches("http client request: GET http://localhost:[0-9]+/hello1/");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureHttpPost() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpPost.class);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan)
                .matches("http client request: POST http://localhost:[0-9]+/hello2");
    }

    @Test
    public void shouldCaptureHttpGetWithAsyncHandler() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteHttpGetWithAsyncHandler.class);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan)
                .matches("http client request: GET http://localhost:[0-9]+/hello3/");
    }

    public static class ExecuteHttpGet extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
            Response response = asyncHttpClient
                    .prepareGet("http://localhost:" + getPort() + "/hello1/").execute().get();
            asyncHttpClient.close();
            if (response.getStatusCode() != 200) {
                throw new IllegalStateException(
                        "Unexpected status code: " + response.getStatusCode());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(response.getHeader("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
        }
    }

    public static class ExecuteHttpPost extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
            Response response = asyncHttpClient
                    .preparePost("http://localhost:" + getPort() + "/hello2").execute().get();
            asyncHttpClient.close();
            if (response.getStatusCode() != 200) {
                throw new IllegalStateException(
                        "Unexpected status code: " + response.getStatusCode());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(response.getHeader("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
        }
    }

    public static class ExecuteHttpGetWithAsyncHandler extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger statusCode = new AtomicInteger();
            final AtomicReference<HttpResponseHeaders> responseHeaders =
                    new AtomicReference<HttpResponseHeaders>();
            asyncHttpClient.prepareGet("http://localhost:" + getPort() + "/hello3/")
                    .execute(new AsyncHandler<Response>() {
                        @Override
                        public STATE onBodyPartReceived(HttpResponseBodyPart part) {
                            return STATE.CONTINUE;
                        }
                        @Override
                        public Response onCompleted() throws Exception {
                            latch.countDown();
                            return null;
                        }
                        @Override
                        public STATE onHeadersReceived(HttpResponseHeaders headers) {
                            responseHeaders.set(headers);
                            return STATE.CONTINUE;
                        }
                        @Override
                        public STATE onStatusReceived(HttpResponseStatus status) {
                            statusCode.set(status.getStatusCode());
                            return STATE.CONTINUE;
                        }
                        @Override
                        public void onThrowable(Throwable t) {
                            t.printStackTrace();
                        }
                    });
            latch.await();
            asyncHttpClient.close();
            if (statusCode.get() != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
            // this it to test header propagation by instrumentation
            HttpResponseHeaders headers = responseHeaders.get();
            if (headers == null
                    || !"Yes".equals(headers.getHeaders().getFirstValue("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
        }
    }
}
