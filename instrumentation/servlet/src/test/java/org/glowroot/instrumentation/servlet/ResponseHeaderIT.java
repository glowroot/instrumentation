/*
 * Copyright 2011-2019 the original author or authors.
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
package org.glowroot.instrumentation.servlet;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import org.glowroot.instrumentation.servlet.TestServlet.PatchedMockHttpServletResponse;
import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponseHeaderIT {

    private static final String INSTRUMENTATION_ID = "servlet";

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
    public void testStandardResponseHeaders() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.of("Content-Type", " Content-Length", " Content-Language"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeaders.class, "Web");

        // then
        Map<String, Object> responseHeaders = getResponseHeaders(incomingSpan);
        assertThat(responseHeaders.get("Content-Type")).isEqualTo("text/plain;charset=UTF-8");
        assertThat(responseHeaders.get("Content-Length")).isEqualTo("1");
        assertThat(responseHeaders.get("Content-Language")).isEqualTo("en");
        assertThat(responseHeaders.get("Extra")).isNull();
    }

    @Test
    public void testStandardResponseHeadersUsingSetHeader() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.of("Content-Type", " Content-Length", " Content-Language"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeadersUsingSetHeader.class, "Web");

        // then
        Map<String, Object> responseHeaders = getResponseHeaders(incomingSpan);
        assertThat(responseHeaders.get("Content-Type")).isEqualTo("text/plain;charset=UTF-8");
        assertThat(responseHeaders.get("Content-Length")).isEqualTo("1");
        assertThat(responseHeaders.get("Content-Language")).isEqualTo("en");
        assertThat(responseHeaders.get("Extra")).isNull();
    }

    @Test
    public void testStandardResponseHeadersUsingAddHeader() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.of("Content-Type", " Content-Length", " Content-Language"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeadersUsingAddHeader.class, "Web");

        // then
        Map<String, Object> responseHeaders = getResponseHeaders(incomingSpan);
        assertThat(responseHeaders.get("Content-Type")).isEqualTo("text/plain;charset=UTF-8");
        assertThat(responseHeaders.get("Content-Length")).isEqualTo("1");
        assertThat(responseHeaders.get("Content-Language")).isEqualTo("en");
        assertThat(responseHeaders.get("Extra")).isNull();
    }

    @Test
    public void testStandardResponseHeadersLowercase() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.of("Content-Type", " Content-Length"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeadersLowercase.class, "Web");

        // then
        Map<String, Object> responseHeaders = getResponseHeaders(incomingSpan);
        assertThat(responseHeaders.get("content-type")).isEqualTo("text/plain;charset=UTF-8");
        assertThat(responseHeaders.get("content-length")).isEqualTo("1");
        assertThat(responseHeaders.get("extra")).isNull();
    }

    @Test
    public void testWithoutAnyHeaderCapture() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.<String>of());
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeaders.class, "Web");
        // then
        assertThat(getResponseHeaders(incomingSpan)).isNull();
    }

    @Test
    public void testWithoutAnyInterestingHeaderCapture() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.of("ABC"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeaders.class, "Web");
        // then
        assertThat(getResponseHeaders(incomingSpan)).isNull();
    }

    @Test
    public void testWithoutAnyHeaderCaptureUsingSetHeader() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.<String>of());
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeadersUsingSetHeader.class, "Web");
        // then
        assertThat(getResponseHeaders(incomingSpan)).isNull();
    }

    @Test
    public void testWithoutAnyHeaderCaptureUsingAddHeader() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.<String>of());
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetStandardResponseHeadersUsingAddHeader.class, "Web");
        // then
        assertThat(getResponseHeaders(incomingSpan)).isNull();
    }

    @Test
    public void testLotsOfResponseHeaders() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.of("One", "Two", "Date-One", "Date-Two", "Int-One", "Int-Two",
                        "X-One"));

        // when
        IncomingSpan incomingSpan = container.executeForType(SetLotsOfResponseHeaders.class, "Web");

        // then
        Map<String, Object> responseHeaders = getResponseHeaders(incomingSpan);
        @SuppressWarnings("unchecked")
        List<String> one = (List<String>) responseHeaders.get("One");
        @SuppressWarnings("unchecked")
        List<String> dOne = (List<String>) responseHeaders.get("Date-One");
        @SuppressWarnings("unchecked")
        List<String> iOne = (List<String>) responseHeaders.get("Int-One");
        @SuppressWarnings("unchecked")
        List<String> xOne = (List<String>) responseHeaders.get("X-One");
        assertThat(one).containsExactly("ab", "xy");
        assertThat(responseHeaders.get("Two")).isEqualTo("1");
        assertThat(responseHeaders.get("Three")).isNull();
        assertThat(dOne).containsExactly("Fri, 28 Feb 2014 02:06:52 GMT",
                "Fri, 28 Feb 2014 02:06:53 GMT");
        assertThat(responseHeaders.get("Date-Two")).isEqualTo("Fri, 28 Feb 2014 02:06:54 GMT");
        assertThat(responseHeaders.get("Date-Three")).isNull();
        assertThat(iOne).containsExactly("2", "3");
        assertThat(responseHeaders.get("Int-Two")).isEqualTo("4");
        assertThat(responseHeaders.get("Int-Three")).isNull();
        assertThat(xOne).containsExactly("xy", "Fri, 28 Feb 2014 02:06:56 GMT", "6");
    }

    @Test
    public void testOutsideServlet() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureResponseHeaders",
                ImmutableList.of("Content-Type", " Content-Length", " Content-Language"));
        // when
        container.executeNoExpectedTrace(SetStandardResponseHeadersOutsideServlet.class);
        // then
        // basically just testing that it should not generate any errors
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getDetailMap(IncomingSpan incomingSpan, String name) {
        return (Map<String, Object>) incomingSpan.detail().get(name);
    }

    static Map<String, Object> getResponseHeaders(IncomingSpan incomingSpan) {
        return getDetailMap(incomingSpan, "Response headers");
    }

    @SuppressWarnings("serial")
    public static class SetStandardResponseHeaders extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setContentLength(1);
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.setLocale(Locale.ENGLISH);
        }
    }

    @SuppressWarnings("serial")
    public static class SetStandardResponseHeadersUsingSetHeader extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("Content-Type", "text/plain;charset=UTF-8");
            response.setHeader("Content-Length", "1");
            response.setHeader("Extra", "abc");
            response.setLocale(Locale.ENGLISH);
        }
    }

    @SuppressWarnings("serial")
    public static class SetStandardResponseHeadersUsingAddHeader extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.addHeader("Content-Type", "text/plain;charset=UTF-8");
            response.addHeader("Content-Length", "1");
            response.addHeader("Extra", "abc");
            response.setLocale(Locale.ENGLISH);
        }
    }

    @SuppressWarnings("serial")
    public static class SetStandardResponseHeadersLowercase extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("content-type", "text/plain;charset=UTF-8");
            response.setHeader("content-length", "1");
            response.setHeader("extra", "abc");
        }
    }

    @SuppressWarnings("serial")
    public static class SetLotsOfResponseHeaders extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("One", "abc");
            response.setHeader("one", "ab");
            response.addHeader("one", "xy");
            response.setHeader("Two", "1");
            response.setHeader("Three", "xyz");

            response.setDateHeader("Date-One", 1393553211832L);
            response.setDateHeader("Date-one", 1393553212832L);
            response.addDateHeader("Date-one", 1393553213832L);
            response.setDateHeader("Date-Two", 1393553214832L);
            response.setDateHeader("Date-Thr", 1393553215832L);
            response.addDateHeader("Date-Four", 1393553215832L);

            response.setIntHeader("Int-One", 1);
            response.setIntHeader("Int-one", 2);
            response.addIntHeader("Int-one", 3);
            response.setIntHeader("Int-Two", 4);
            response.setIntHeader("Int-Thr", 5);
            response.addIntHeader("Int-Four", 6);

            response.addHeader("X-One", "xy");
            response.addDateHeader("X-one", 1393553216832L);
            response.addIntHeader("X-one", 6);
        }
    }

    public static class SetStandardResponseHeadersOutsideServlet implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            MockHttpServletResponse response = new PatchedMockHttpServletResponse();
            response.setContentLength(1);
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.setLocale(Locale.ENGLISH);
        }
    }
}
