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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Response;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.util.ExecuteHttpBase;

import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleOutgoingSpanMessage;

// TODO test against AsyncHttpClient providers jdk and grizzly (in addition to the default netty)
public class AsyncHttpClient2xIT {

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
        assertSingleOutgoingSpanMessage(incomingSpan)
                .matches("http client request: GET http://localhost:[0-9]+/hello1/");
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
            AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient();
            int statusCode =
                    asyncHttpClient.prepareGet("http://localhost:" + getPort() + "/hello1/")
                            .execute().get().getStatusCode();
            asyncHttpClient.close();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class ExecuteHttpPost extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient();
            int statusCode =
                    asyncHttpClient.preparePost("http://localhost:" + getPort() + "/hello2")
                            .execute().get().getStatusCode();
            asyncHttpClient.close();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class ExecuteHttpGetWithAsyncHandler extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient();
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger statusCode = new AtomicInteger();
            asyncHttpClient.prepareGet("http://localhost:" + getPort() + "/hello3/")
                    .execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(Response response) throws Exception {
                            latch.countDown();
                            return response;
                        }
                        @Override
                        public State onStatusReceived(HttpResponseStatus status) {
                            statusCode.set(status.getStatusCode());
                            return State.CONTINUE;
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
        }
    }
}
