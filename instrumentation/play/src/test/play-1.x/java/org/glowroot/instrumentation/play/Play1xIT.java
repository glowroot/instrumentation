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
import java.util.Iterator;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Play;
import play.server.Server;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static org.assertj.core.api.Assertions.assertThat;

public class Play1xIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // javaagent is required for Executor.execute() weaving (tests run in play dev mode which
        // uses netty)
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
    public void shouldCaptureIndexRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetIndex.class);

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("Application#index");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("play action invoker");

        Iterator<Span> j = localSpan.childSpans().iterator();

        localSpan = (LocalSpan) j.next();
        assertThat(localSpan.message()).isEqualTo("play render: Application/index.html");

        assertThat(j.hasNext()).isFalse();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureApplicationIndexRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetApplicationIndex.class);

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("Application#index");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("play action invoker");

        Iterator<Span> j = localSpan.childSpans().iterator();

        localSpan = (LocalSpan) j.next();
        assertThat(localSpan.message()).isEqualTo("play render: Application/index.html");

        assertThat(j.hasNext()).isFalse();

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void shouldCaptureApplicationCalculateRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(GetApplicationCalculate.class);

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("Application#calculate");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("play action invoker");

        Iterator<Span> j = localSpan.childSpans().iterator();

        localSpan = (LocalSpan) j.next();
        assertThat(localSpan.message()).isEqualTo("play render: Application/calculate.html");

        assertThat(j.hasNext()).isFalse();

        assertThat(i.hasNext()).isFalse();
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

    public static class GetApplicationIndex implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet =
                    new HttpGet("http://localhost:" + PlayWrapper.port + "/application/index");
            int statusCode = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class GetApplicationCalculate implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet =
                    new HttpGet("http://localhost:" + PlayWrapper.port + "/application/calculate");
            int statusCode = httpClient.execute(httpGet).getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
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
            Play.init(new File("target/test-classes/application"), "test");
            Play.configuration.setProperty("http.port", Integer.toString(port));
            try {
                Constructor<Server> constructor = Server.class.getConstructor(String[].class);
                constructor.newInstance(new Object[] {new String[0]});
            } catch (Exception e) {
                try {
                    // play 1.1
                    Server.class.newInstance();
                } catch (Exception f) {
                    f.printStackTrace();
                    // re-throw original exception
                    throw new IllegalStateException(e);
                }
            }
            Play.start();
        }
    }
}
