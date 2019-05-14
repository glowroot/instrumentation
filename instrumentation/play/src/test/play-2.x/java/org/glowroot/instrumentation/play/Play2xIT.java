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
package org.glowroot.instrumentation.play;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.test.TestServer;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.ThrowableInfo;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class Play2xIT {

    private static final boolean PLAY_2_0_X = Boolean.getBoolean("test.play20x");

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // javaagent is required for Executor.execute() weaving
        // -Dlogger.resource is needed to configure play logging (at least on 2.0.8)
        container = JavaagentContainer
                .createWithExtraJvmArgs(ImmutableList.of("-Dlogger.resource=logback-test.xml"));
        // need warmup to avoid capturing rendering of views.html.defaultpages.todo during
        // play.mvc.Results static initializer (at least on play 2.3.10)
        container.execute(GetIndex.class);
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
    public void shouldCaptureIndexRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetIndex.class);

        // then
        if (PLAY_2_0_X) {
            assertThat(incomingSpan.transactionName()).isEqualTo("HomeController#index");
        } else {
            assertThat(incomingSpan.transactionName()).isEqualTo("/");
        }

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("play render: index");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureIndexRouteUsingAltTransactionNaming() throws Exception {
        // given
        container.setInstrumentationProperty("play", "useAltTransactionNaming", true);

        // when
        IncomingSpan incomingSpan = container.execute(GetIndex.class);

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("HomeController#index");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("play render: index");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureAsyncRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetAsync.class);

        // then
        if (PLAY_2_0_X) {
            assertThat(incomingSpan.transactionName())
                    .isEqualTo("AsyncController#message");
        } else {
            assertThat(incomingSpan.transactionName()).isEqualTo("/message");
        }

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    @Test
    public void shouldCaptureStreamRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetStream.class);

        // then
        if (PLAY_2_0_X) {
            assertThat(incomingSpan.transactionName())
                    .isEqualTo("StreamController#stream");
        } else {
            assertThat(incomingSpan.transactionName()).isEqualTo("/stream");
        }

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("test local span");
    }

    @Test
    public void shouldCaptureAssetRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetAsset.class);

        // then
        if (PLAY_2_0_X) {
            assertThat(incomingSpan.transactionName()).isEqualTo("Assets#at");
        } else {
            assertThat(incomingSpan.transactionName()).isEqualTo("/assets/**");
        }
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureError() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetBad.class);

        // then
        if (PLAY_2_0_X) {
            assertThat(incomingSpan.transactionName()).isEqualTo("BadController#bad");
        } else {
            assertThat(incomingSpan.transactionName()).isEqualTo("/bad");
        }
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().exception()).isNotNull();
        ThrowableInfo exception = incomingSpan.error().exception();
        while (exception.cause() != null) {
            exception = exception.cause();
        }
        assertThat(exception.type()).isEqualTo(RuntimeException.class);
        assertThat(exception.message()).isEqualTo("Bad");
    }

    public static class GetIndex implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet("http://localhost:" + PlayWrapper.port + "/");
            int statusCode = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class GetAsset implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(
                    "http://localhost:" + PlayWrapper.port + "/assets/scripts/empty.js");
            int statusCode = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class GetAsync implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet("http://localhost:" + PlayWrapper.port + "/message");
            int statusCode = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class GetBad implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet("http://localhost:" + PlayWrapper.port + "/bad");
            int statusCode = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (statusCode != 500) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class GetStream implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet("http://localhost:" + PlayWrapper.port + "/stream");
            CloseableHttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
            int len = ByteStreams.toByteArray(response.getEntity().getContent()).length;
            if (len != 10) {
                throw new IllegalStateException("Unexpected content length: " + len);
            }
        }
    }

    public static class PlayWrapper {

        protected static int port;

        static {
            try {
                port = Ports.getAvailable();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            TestServer server;
            try {
                server = createNewerPlayServer();
            } catch (Exception e) {
                try {
                    server = createOlderPlayServer();
                } catch (Exception f) {
                    try {
                        server = createEvenOlderPlayServer();
                    } catch (Exception g) {
                        // throw original exception
                        throw new RuntimeException(e);
                    }
                }
            }
            server.start();
        }

        private static TestServer createNewerPlayServer() throws Exception {
            Class<?> environmentClass = Class.forName("play.Environment");

            Class<?> builderClass = Class.forName("play.inject.guice.GuiceApplicationBuilder");
            Method inMethod = builderClass.getMethod("in", environmentClass);
            Method buildMethod = builderClass.getMethod("build");

            Object env = environmentClass.getConstructor(File.class).newInstance(new File("."));
            Object builder = builderClass.newInstance();
            builder = inMethod.invoke(builder, env);
            Object app = buildMethod.invoke(builder);

            Class<?> applicationClass = Class.forName("play.Application");
            Constructor<TestServer> testServerConstructor =
                    TestServer.class.getConstructor(int.class, applicationClass);
            return testServerConstructor.newInstance(port, app);
        }

        private static TestServer createOlderPlayServer() throws Exception {
            Class<?> globalSettingsClass = Class.forName("play.GlobalSettings");
            Class<?> fakeApplicationClass = Class.forName("play.test.FakeApplication");
            Constructor<?> fakeApplicationConstructor = fakeApplicationClass.getConstructor(
                    File.class, ClassLoader.class, Map.class, List.class, globalSettingsClass);
            Object app = fakeApplicationConstructor.newInstance(new File("."),
                    PlayWrapper.class.getClassLoader(), ImmutableMap.of(), ImmutableList.of(),
                    null);
            Constructor<TestServer> testServerConstructor =
                    TestServer.class.getConstructor(int.class, fakeApplicationClass);
            return testServerConstructor.newInstance(port, app);
        }

        // play 2.0.x
        private static TestServer createEvenOlderPlayServer() throws Exception {
            Class<?> fakeApplicationClass = Class.forName("play.test.FakeApplication");
            Constructor<?> fakeApplicationConstructor = fakeApplicationClass.getConstructor(
                    File.class, ClassLoader.class, Map.class, List.class);
            Object app = fakeApplicationConstructor.newInstance(new File("."),
                    PlayWrapper.class.getClassLoader(), ImmutableMap.of(), ImmutableList.of());
            Constructor<TestServer> testServerConstructor =
                    TestServer.class.getConstructor(int.class, fakeApplicationClass);
            return testServerConstructor.newInstance(port, app);
        }
    }
}
