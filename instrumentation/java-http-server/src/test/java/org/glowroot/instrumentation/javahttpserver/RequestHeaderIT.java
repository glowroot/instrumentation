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

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.sun.net.httpserver.HttpExchange;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.assertj.core.api.Assertions.assertThat;

// The com.sun.net.httpserver.Headers class normalizes the header keys by
// converting to following form: first char upper case, rest lower case.
@SuppressWarnings("restriction")
public class RequestHeaderIT {

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
    public void testStandardRequestHeaders() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureRequestHeaders",
                ImmutableList.of("Content-Type", " Content-Length"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardRequestHeaders.class, "Web");

        // then
        Map<String, Object> requestHeaders =
                ResponseHeaderIT.getDetailMap(incomingSpan, "Request headers");
        assertThat(requestHeaders.get("Content-type")).isEqualTo("text/plain;charset=UTF-8");
        assertThat(requestHeaders.get("Content-length")).isEqualTo("1");
        assertThat(requestHeaders.get("Extra")).isNull();
    }

    @Test
    public void testStandardRequestHeadersLowercase() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureRequestHeaders",
                ImmutableList.of("Content-Type", " Content-Length"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardRequestHeadersLowercase.class, "Web");

        // then
        Map<String, Object> requestHeaders =
                ResponseHeaderIT.getDetailMap(incomingSpan, "Request headers");
        assertThat(requestHeaders.get("Content-type")).isEqualTo("text/plain;charset=UTF-8");
        assertThat(requestHeaders.get("Content-length")).isEqualTo("1");
        assertThat(requestHeaders.get("Extra")).isNull();
    }

    @Test
    public void testLotsOfRequestHeaders() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureRequestHeaders",
                ImmutableList.of("One", "two"));

        // when
        IncomingSpan incomingSpan = container.executeForType(SetOtherRequestHeaders.class, "Web");

        // then
        Map<String, Object> requestHeaders =
                ResponseHeaderIT.getDetailMap(incomingSpan, "Request headers");
        @SuppressWarnings("unchecked")
        List<String> one = (List<String>) requestHeaders.get("One");
        assertThat(one).containsExactly("ab", "xy");
        assertThat(requestHeaders.get("Two")).isEqualTo("1");
        assertThat(requestHeaders.get("Three")).isNull();
    }

    @Test
    public void testMaskRequestHeaders() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureRequestHeaders",
                ImmutableList.of("*"));
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "maskRequestHeaders",
                ImmutableList.of("content-Len*"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardRequestHeaders.class, "Web");

        // then
        Map<String, Object> requestHeaders =
                ResponseHeaderIT.getDetailMap(incomingSpan, "Request headers");
        assertThat(requestHeaders.get("Content-type")).isEqualTo("text/plain;charset=UTF-8");
        assertThat(requestHeaders.get("Content-length")).isEqualTo("****");
        assertThat(requestHeaders.get("Extra")).isEqualTo("abc");
    }

    public static class SetStandardRequestHeaders extends TestHandler {

        @Override
        protected void before(HttpExchange exchange) {
            exchange.getRequestHeaders().add("Content-Type", "text/plain;charset=UTF-8");
            exchange.getRequestHeaders().add("Content-Length", "1");
            exchange.getRequestHeaders().add("Extra", "abc");
        }
    }

    public static class SetStandardRequestHeadersLowercase extends TestHandler {

        @Override
        protected void before(HttpExchange exchange) {
            exchange.getRequestHeaders().add("content-type", "text/plain;charset=UTF-8");
            exchange.getRequestHeaders().add("content-length", "1");
            exchange.getRequestHeaders().add("extra", "abc");
        }
    }

    public static class SetOtherRequestHeaders extends TestHandler {

        @Override
        protected void before(HttpExchange exchange) {
            exchange.getRequestHeaders().add("One", "ab");
            exchange.getRequestHeaders().add("One", "xy");
            exchange.getRequestHeaders().add("Two", "1");
            exchange.getRequestHeaders().add("Three", "xyz");
        }
    }

}
