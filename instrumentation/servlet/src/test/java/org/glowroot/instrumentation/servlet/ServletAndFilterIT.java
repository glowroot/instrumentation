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

import java.io.IOException;
import java.io.Serializable;

import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletAndFilterIT {

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
    public void testServlet() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(ExecuteServlet.class, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo("/testservlet");
        assertThat(incomingSpan.transactionName()).isEqualTo("/testservlet");
        assertThat(incomingSpan.detail().get("Request http method")).isEqualTo("GET");
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(200);
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testFilter() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(ExecuteFilter.class, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo("/testfilter");
        assertThat(incomingSpan.transactionName()).isEqualTo("/testfilter");
        assertThat(incomingSpan.detail().get("Request http method")).isEqualTo("GET");
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(200);
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testCombination() throws Exception {
        // when
        IncomingSpan incomingSpan =
                container.executeForType(ExecuteFilterWithNestedServlet.class, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo("/testfilter");
        assertThat(incomingSpan.transactionName()).isEqualTo("/testfilter");
        assertThat(incomingSpan.detail().get("Request http method")).isEqualTo("GET");
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(200);
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testNoQueryString() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(TestNoQueryString.class, "Web");
        // then
        assertThat(incomingSpan.detail().get("Request query string")).isNull();
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(200);
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testEmptyQueryString() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(TestEmptyQueryString.class, "Web");
        // then
        assertThat(incomingSpan.detail().get("Request query string")).isEqualTo("");
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(200);
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testNonEmptyQueryString() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(TestNonEmptyQueryString.class, "Web");
        // then
        assertThat(incomingSpan.detail().get("Request query string")).isEqualTo("a=b&c=d");
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(200);
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testServletThrowsException() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(ServletThrowsException.class, "Web");

        // then
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(500);
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testFilterThrowsException() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(FilterThrowsException.class, "Web");

        // then
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(500);
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testSendRedirect() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(SendRedirect.class, "Web");

        // then
        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(302);
        assertThat(ResponseHeaderIT.getResponseHeaders(incomingSpan).get("Location"))
                .isEqualTo("tohere");
    }

    @Test
    public void testSend500Error() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(Send500Error.class, "Web");

        // then
        assertThat(incomingSpan.error().message()).isEqualTo("sendError, HTTP status code 500");
        assertThat(incomingSpan.error().exception()).isNull();

        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(500);

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testSetStatus500Error() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(SetStatus500Error.class, "Web");

        // then
        assertThat(incomingSpan.error().message()).isEqualTo("setStatus, HTTP status code 500");
        assertThat(incomingSpan.error().exception()).isNull();

        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(500);

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testSend400Error() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(Send400Error.class, "Web");

        // then
        assertThat(incomingSpan.error()).isNull();
        assertThat(incomingSpan.childSpans()).isEmpty();

        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(400);
    }

    @Test
    public void testSetStatus400Error() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(SetStatus400Error.class, "Web");

        // then
        assertThat(incomingSpan.error()).isNull();
        assertThat(incomingSpan.childSpans()).isEmpty();

        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(400);
    }

    @Test
    public void testSend400ErrorWithCaptureOn() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "traceErrorOn4xxResponseCode",
                true);

        // when
        IncomingSpan incomingSpan = container.executeForType(Send400Error.class, "Web");

        // then
        assertThat(incomingSpan.error().message()).isEqualTo("sendError, HTTP status code 400");
        assertThat(incomingSpan.error().exception()).isNull();

        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(400);

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testSetStatus400ErrorWithCaptureOn() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "traceErrorOn4xxResponseCode",
                true);

        // when
        IncomingSpan incomingSpan = container.executeForType(SetStatus400Error.class, "Web");

        // then
        assertThat(incomingSpan.error().message()).isEqualTo("setStatus, HTTP status code 400");
        assertThat(incomingSpan.error().exception()).isNull();

        assertThat(incomingSpan.detail().get("Response code")).isEqualTo(400);

        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void testBizzareServletContainer() throws Exception {
        // when
        container.executeNoExpectedTrace(BizzareServletContainer.class);
        // then
    }

    @Test
    public void testBizzareThrowingServletContainer() throws Exception {
        // when
        container.executeNoExpectedTrace(BizzareThrowingServletContainer.class);
        // then
    }

    @SuppressWarnings("serial")
    public static class ExecuteServlet extends TestServlet {}

    public static class ExecuteFilter extends TestFilter {}

    public static class ExecuteFilterWithNestedServlet extends TestFilter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            new TestServlet().service(request, response);
        }
    }

    @SuppressWarnings("serial")
    public static class TestNoQueryString extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setQueryString(null);
        }
    }

    @SuppressWarnings("serial")
    public static class TestEmptyQueryString extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setQueryString("");
        }
    }

    @SuppressWarnings("serial")
    public static class TestNonEmptyQueryString extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setQueryString("a=b&c=d");
        }
    }

    @SuppressWarnings("serial")
    public static class InvalidateSession extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setSession(new MockHttpSession(null, "1234"));
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().invalidate();
        }
    }

    @SuppressWarnings("serial")
    public static class ServletThrowsException extends TestServlet {

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
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
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
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
            throw exception;
        }
    }

    @SuppressWarnings("serial")
    public static class SendRedirect extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.sendRedirect("tohere");
        }
    }

    @SuppressWarnings("serial")
    public static class Send500Error extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.sendError(500);
        }
    }

    @SuppressWarnings("serial")
    public static class SetStatus500Error extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.setStatus(500);
        }
    }

    @SuppressWarnings("serial")
    public static class Send400Error extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.sendError(400);
        }
    }

    @SuppressWarnings("serial")
    public static class SetStatus400Error extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.setStatus(400);
        }
    }

    public static class BizzareServletContainer implements AppUnderTest, Servlet {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            service(null, null);
        }

        @Override
        public void init(ServletConfig config) {}

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) {}

        @Override
        public String getServletInfo() {
            return null;
        }

        @Override
        public void destroy() {}
    }

    public static class BizzareThrowingServletContainer implements AppUnderTest, Servlet {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            try {
                service(null, null);
            } catch (RuntimeException e) {
            }
        }

        @Override
        public void init(ServletConfig config) {}

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) {
            throw new RuntimeException();
        }

        @Override
        public String getServletInfo() {
            return null;
        }

        @Override
        public void destroy() {}
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
