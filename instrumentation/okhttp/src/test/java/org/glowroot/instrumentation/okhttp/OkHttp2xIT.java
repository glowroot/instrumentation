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
package org.glowroot.instrumentation.okhttp;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.OutgoingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.util.ExecuteHttpBase;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class OkHttp2xIT {

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
                .matches("http client request: GET http://localhost:[0-9]+/hello1/\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(3);
        assertThat(detail).containsEntry("Method", "GET");
        assertThat((String) detail.get("URI")).matches("http://localhost:[0-9]+/hello1/\\?q");
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

    @Test
    public void shouldCaptureAsyncHttpGet() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteAsyncHttpGet.class);

        // then
        assertThat(incomingSpan.asyncTimers().get(0).name()).isEqualTo("http client request");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: GET http://localhost:[0-9]+/hello3/\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(3);
        assertThat(detail).containsEntry("Method", "GET");
        assertThat((String) detail.get("URI")).matches("http://localhost:[0-9]+/hello3/\\?q");
        assertThat(detail).containsEntry("Result", 200);

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).matches("test local span");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureAsyncHttpPost() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteAsyncHttpPost.class);

        // then
        assertThat(incomingSpan.asyncTimers().get(0).name()).isEqualTo("http client request");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        OutgoingSpan outgoingSpan = (OutgoingSpan) i.next();
        assertThat(outgoingSpan.message())
                .matches("http client request: POST http://localhost:[0-9]+/hello4\\?q");
        Map<String, Object> detail = outgoingSpan.detail();
        assertThat(detail).hasSize(3);
        assertThat(detail).containsEntry("Method", "POST");
        assertThat((String) detail.get("URI")).matches("http://localhost:[0-9]+/hello4\\?q");
        assertThat(detail).containsEntry("Result", 200);

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).matches("test local span");

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteHttpGet extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("http://localhost:" + getPort() + "/hello1/?q")
                    .build();
            Response response = client.newCall(request).execute();
            if (response.code() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + response.code());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(response.header("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            response.body().close();
        }
    }

    public static class ExecuteHttpPost extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            MediaType mediaType = MediaType.parse("text/plain; charset=utf-8");
            OkHttpClient client = new OkHttpClient();
            RequestBody body = RequestBody.create(mediaType, "hello");
            Request request = new Request.Builder()
                    .url("http://localhost:" + getPort() + "/hello2?q")
                    .post(body)
                    .build();
            Response response = client.newCall(request).execute();
            if (response.code() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + response.code());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(response.header("X-Test-Harness"))) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            response.body().close();
        }
    }

    public static class ExecuteAsyncHttpGet extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("http://localhost:" + getPort() + "/hello3/?q")
                    .build();
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger responseStatusCode = new AtomicInteger();
            final AtomicReference<String> testHeader = new AtomicReference<String>();
            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onResponse(Response response) throws IOException {
                    TestSpans.createLocalSpan();
                    responseStatusCode.set(response.code());
                    testHeader.set(response.header("X-Test-Harness"));
                    response.body().close();
                    latch.countDown();
                }

                @Override
                public void onFailure(Request request, IOException e) {
                    TestSpans.createLocalSpan();
                    latch.countDown();
                }
            });
            latch.await();
            if (responseStatusCode.get() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + responseStatusCode.get());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(testHeader.get())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            // need to wait just a bit longer to ensure auxiliary thread capture completes
            MILLISECONDS.sleep(100);
        }
    }

    public static class ExecuteAsyncHttpPost extends ExecuteHttpBase {

        @Override
        public void transactionMarker() throws Exception {
            MediaType mediaType = MediaType.parse("text/plain; charset=utf-8");
            OkHttpClient client = new OkHttpClient();
            RequestBody body = RequestBody.create(mediaType, "hello");
            Request request = new Request.Builder()
                    .url("http://localhost:" + getPort() + "/hello4?q")
                    .post(body)
                    .build();
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger responseStatusCode = new AtomicInteger();
            final AtomicReference<String> testHeader = new AtomicReference<String>();
            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onResponse(Response response) throws IOException {
                    TestSpans.createLocalSpan();
                    responseStatusCode.set(response.code());
                    testHeader.set(response.header("X-Test-Harness"));
                    response.body().close();
                    latch.countDown();
                }

                @Override
                public void onFailure(Request request, IOException e) {
                    TestSpans.createLocalSpan();
                    latch.countDown();
                }
            });
            latch.await();
            if (responseStatusCode.get() != 200) {
                throw new IllegalStateException(
                        "Unexpected response status code: " + responseStatusCode.get());
            }
            // this it to test header propagation by instrumentation
            if (!"Yes".equals(testHeader.get())) {
                throw new IllegalStateException("X-Test-Harness header not recieved");
            }
            // need to wait just a bit longer to ensure auxiliary thread capture completes
            MILLISECONDS.sleep(100);
        }
    }
}
