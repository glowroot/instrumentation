/*
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
package org.glowroot.instrumentation.javahttpserver;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("restriction")
public class JavaHttpServerIT {

    private static final String INSTRUMENTATION_ID = "java-http-server";

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // tests only work with javaagent container because they need to weave bootstrap classes
        // that implement com.sun.net.httpserver.HttpExchange
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
    public void testHandler() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(ExecuteHandler.class, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo("/testhandler");
        assertThat(incomingSpan.transactionName()).isEqualTo("/testhandler");
        assertThat(getDetailValue(incomingSpan, "Request http method")).isEqualTo("GET");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testFilter() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(ExecuteFilter.class, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo("/testfilter");
        assertThat(incomingSpan.transactionName()).isEqualTo("/testfilter");
        assertThat(getDetailValue(incomingSpan, "Request http method")).isEqualTo("GET");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testCombination() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.executeForType(ExecuteFilterWithNestedHandler.class, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo("/testfilter");
        assertThat(incomingSpan.transactionName()).isEqualTo("/testfilter");
        assertThat(getDetailValue(incomingSpan, "Request http method")).isEqualTo("GET");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testNoQueryString() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(TestNoQueryString.class, "Web");
        // then
        assertThat(getDetailValue(incomingSpan, "Request query string")).isNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testEmptyQueryString() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(TestEmptyQueryString.class, "Web");
        // then
        assertThat(getDetailValue(incomingSpan, "Request query string")).isEqualTo("");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testNonEmptyQueryString() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(TestNonEmptyQueryString.class, "Web");
        // then
        assertThat(getDetailValue(incomingSpan, "Request query string")).isEqualTo("a=b&c=d");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testHandlerThrowsException() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(HandlerThrowsException.class, "Web");

        // then
        assertThat(incomingSpan.error().message()).isNull();
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testFilterThrowsException() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(FilterThrowsException.class, "Web");

        // then
        assertThat(incomingSpan.error().message()).isNull();
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testSend500Error() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(Send500Error.class, "Web");

        // then
        assertThat(incomingSpan.error().message())
                .isEqualTo("sendResponseHeaders, HTTP status code 500");
        assertThat(incomingSpan.error().exception()).isNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testSend400Error() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(Send400Error.class, "Web");

        // then
        assertThat(incomingSpan.error()).isNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testSend400ErrorWithCaptureOn() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "traceErrorOn4xxResponseCode",
                true);

        // when
        IncomingSpan incomingSpan = container.executeForType(Send400Error.class, "Web");

        // then
        assertThat(incomingSpan.error().message())
                .isEqualTo("sendResponseHeaders, HTTP status code 400");
        assertThat(incomingSpan.error().exception()).isNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    private static String getDetailValue(IncomingSpan incomingSpan, String name) {
        for (Map.Entry<String, ?> entry : incomingSpan.detail().entrySet()) {
            if (entry.getKey().equals(name)) {
                return (String) entry.getValue();
            }
        }
        return null;
    }

    public static class ExecuteHandler extends TestHandler {}

    public static class ExecuteFilter extends TestFilter {}

    public static class ExecuteFilterWithNestedHandler extends TestFilter {

        @Override
        public void doFilter(HttpExchange exchange, Chain chain)
                throws IOException {
            new TestFilter().doFilter(exchange, chain);
        }
    }

    public static class TestNoQueryString extends TestHandler {

        @Override
        protected void before(HttpExchange exchange) {
            ((MockHttpExchange) exchange).setQueryString(null);
        }
    }

    public static class TestEmptyQueryString extends TestHandler {

        @Override
        protected void before(HttpExchange exchange) {
            ((MockHttpExchange) exchange).setQueryString("");
        }
    }

    public static class TestNonEmptyQueryString extends TestHandler {

        @Override
        protected void before(HttpExchange exchange) {
            ((MockHttpExchange) exchange).setQueryString("a=b&c=d");
        }
    }

    public static class HandlerThrowsException extends TestHandler {

        private final RuntimeException exception = new RuntimeException("Something happened");

        @Override
        public void executeApp(Serializable... args) throws Exception {
            try {
                super.executeApp();
            } catch (RuntimeException e) {
                // only suppress expected exception
                if (e != exception) {
                    throw e;
                }
            }
        }

        @Override
        public void handle(HttpExchange exchange) {
            throw exception;
        }
    }

    public static class FilterThrowsException extends TestFilter {

        private final RuntimeException exception = new RuntimeException("Something happened");

        @Override
        public void executeApp(Serializable... args) throws Exception {
            try {
                super.executeApp();
            } catch (RuntimeException e) {
                // only suppress expected exception
                if (e != exception) {
                    throw e;
                }
            }
        }

        @Override
        public void doFilter(HttpExchange exchange, Chain chain) {
            throw exception;
        }
    }

    public static class Send500Error extends TestHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(500, 0);
        }
    }

    public static class Send400Error extends TestHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(400, 0);
        }
    }

    public static class NestedTwo {

        private final String two;

        public NestedTwo(String two) {
            this.two = two;
        }

        public String getTwo() {
            return two;
        }
    }
}
