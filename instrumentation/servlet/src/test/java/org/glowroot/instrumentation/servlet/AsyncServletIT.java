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
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.ning.http.client.AsyncHttpClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class AsyncServletIT {

    private static final String INSTRUMENTATION_ID = "servlet";

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        // async servlet test relies on executor instrumentation, which only works under javaagent
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
    public void testAsyncServlet() throws Exception {
        testAsyncServlet("", InvokeAsync.class);
    }

    @Test
    public void testAsyncServletWithContextPath() throws Exception {
        testAsyncServlet("/zzz", InvokeAsyncWithContextPath.class);
    }

    @Test
    public void testAsyncServlet2() throws Exception {
        testAsyncServlet2("", InvokeAsync2.class);
    }

    @Test
    public void testAsyncServlet2WithContextPath() throws Exception {
        testAsyncServlet2("/zzz", InvokeAsync2WithContextPath.class);
    }

    @Test
    public void testAsyncServletWithDispatch() throws Exception {
        testAsyncServletWithDispatch("", InvokeAsyncWithDispatch.class);
    }

    @Test
    public void testAsyncServletWithDispatchWithContextPath() throws Exception {
        testAsyncServletWithDispatch("/zzz", InvokeAsyncWithDispatchWithContextPath.class);
    }

    private void testAsyncServlet(String contextPath,
            Class<? extends AppUnderTest> appUnderTestClass) throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));

        // when
        IncomingSpan incomingSpan = container.executeForType(appUnderTestClass, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo(contextPath + "/async");
        assertThat(incomingSpan.transactionName()).isEqualTo(contextPath + "/async");

        // check session attributes set across async boundary
        assertThat(SessionAttributeIT.getSessionAttributes(incomingSpan)).isNull();
        assertThat(SessionAttributeIT.getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(SessionAttributeIT.getUpdatedSessionAttributes(incomingSpan).get("sync"))
                .isEqualTo("a");
        assertThat(SessionAttributeIT.getUpdatedSessionAttributes(incomingSpan).get("async"))
                .isEqualTo("b");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    private void testAsyncServlet2(String contextPath,
            Class<? extends AppUnderTest> appUnderTestClass) throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));

        // when
        IncomingSpan incomingSpan = container.executeForType(appUnderTestClass, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo(contextPath + "/async2");
        assertThat(incomingSpan.transactionName()).isEqualTo(contextPath + "/async2");

        // check session attributes set across async boundary
        assertThat(SessionAttributeIT.getSessionAttributes(incomingSpan)).isNull();
        assertThat(SessionAttributeIT.getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(SessionAttributeIT.getUpdatedSessionAttributes(incomingSpan).get("sync"))
                .isEqualTo("a");
        assertThat(SessionAttributeIT.getUpdatedSessionAttributes(incomingSpan).get("async"))
                .isEqualTo("b");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    private void testAsyncServletWithDispatch(String contextPath,
            Class<? extends AppUnderTest> appUnderTestClass) throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));

        // when
        IncomingSpan incomingSpan = container.executeForType(appUnderTestClass, "Web");

        // then
        assertThat(incomingSpan.message()).isEqualTo(contextPath + "/async3");
        assertThat(incomingSpan.transactionName()).isEqualTo(contextPath + "/async3");

        // and check session attributes set across async and dispatch boundary
        assertThat(SessionAttributeIT.getSessionAttributes(incomingSpan)).isNull();
        assertThat(SessionAttributeIT.getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(SessionAttributeIT.getUpdatedSessionAttributes(incomingSpan).get("sync"))
                .isEqualTo("a");
        assertThat(SessionAttributeIT.getUpdatedSessionAttributes(incomingSpan).get("async"))
                .isEqualTo("b");
        assertThat(
                SessionAttributeIT.getUpdatedSessionAttributes(incomingSpan).get("async-dispatch"))
                        .isEqualTo("c");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    public static class InvokeAsync extends InvokeAsyncBase {

        public InvokeAsync() {
            super("");
        }
    }

    public static class InvokeAsyncWithContextPath extends InvokeAsyncBase {

        public InvokeAsyncWithContextPath() {
            super("/zzz");
        }
    }

    private static class InvokeAsyncBase extends InvokeServletInTomcat {

        private InvokeAsyncBase(String contextPath) {
            super(contextPath);
        }

        @Override
        protected void doTest(int port) throws Exception {
            AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
            int statusCode =
                    asyncHttpClient.prepareGet("http://localhost:" + port + contextPath + "/async")
                            .execute().get().getStatusCode();
            asyncHttpClient.close();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class InvokeAsync2 extends InvokeAsync2Base {

        public InvokeAsync2() {
            super("");
        }
    }

    public static class InvokeAsync2WithContextPath extends InvokeAsync2Base {

        public InvokeAsync2WithContextPath() {
            super("/zzz");
        }
    }

    private static class InvokeAsync2Base extends InvokeServletInTomcat {

        private InvokeAsync2Base(String contextPath) {
            super(contextPath);
        }

        @Override
        protected void doTest(int port) throws Exception {
            AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
            int statusCode =
                    asyncHttpClient.prepareGet("http://localhost:" + port + contextPath + "/async2")
                            .execute().get().getStatusCode();
            asyncHttpClient.close();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    public static class InvokeAsyncWithDispatch extends InvokeAsyncWithDispatchBase {

        public InvokeAsyncWithDispatch() {
            super("");
        }
    }

    public static class InvokeAsyncWithDispatchWithContextPath extends InvokeAsyncWithDispatchBase {

        public InvokeAsyncWithDispatchWithContextPath() {
            super("/zzz");
        }
    }

    private static class InvokeAsyncWithDispatchBase extends InvokeServletInTomcat {

        private InvokeAsyncWithDispatchBase(String contextPath) {
            super(contextPath);
        }

        @Override
        protected void doTest(int port) throws Exception {
            AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
            int statusCode =
                    asyncHttpClient.prepareGet("http://localhost:" + port + contextPath + "/async3")
                            .execute().get().getStatusCode();
            asyncHttpClient.close();
            if (statusCode != 200) {
                throw new IllegalStateException("Unexpected status code: " + statusCode);
            }
        }
    }

    @WebServlet(value = "/async", asyncSupported = true, loadOnStartup = 0)
    @SuppressWarnings("serial")
    public static class AsyncServlet extends HttpServlet {

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public void destroy() {
            executor.shutdown();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("sync", "a");
            TestSpans.createLocalSpan();
            final AsyncContext asyncContext = request.startAsync();
            asyncContext.start(new Runnable() {
                @Override
                public void run() {
                    try {
                        MILLISECONDS.sleep(200);
                        ((HttpServletRequest) asyncContext.getRequest()).getSession()
                                .setAttribute("async", "b");
                        TestSpans.createLocalSpan();
                        asyncContext.getResponse().getWriter().println("async response");
                        asyncContext.complete();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @WebServlet(value = "/async2", asyncSupported = true, loadOnStartup = 0)
    @SuppressWarnings("serial")
    public static class AsyncServlet2 extends HttpServlet {

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public void destroy() {
            executor.shutdown();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("sync", "a");
            TestSpans.createLocalSpan();
            final AsyncContext asyncContext = request.startAsync(request, response);
            asyncContext.start(new Runnable() {
                @Override
                public void run() {
                    try {
                        MILLISECONDS.sleep(200);
                        ((HttpServletRequest) asyncContext.getRequest()).getSession()
                                .setAttribute("async", "b");
                        TestSpans.createLocalSpan();
                        asyncContext.getResponse().getWriter().println("async response");
                        asyncContext.complete();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @WebServlet(value = "/async3", asyncSupported = true, loadOnStartup = 0)
    @SuppressWarnings("serial")
    public static class AsyncServletWithDispatch extends HttpServlet {

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public void destroy() {
            executor.shutdown();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("sync", "a");
            TestSpans.createLocalSpan();
            final AsyncContext asyncContext = request.startAsync();
            asyncContext.start(new Runnable() {
                @Override
                public void run() {
                    try {
                        MILLISECONDS.sleep(200);
                        ((HttpServletRequest) asyncContext.getRequest()).getSession()
                                .setAttribute("async", "b");
                        TestSpans.createLocalSpan();
                        asyncContext.dispatch("/async-forward");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @WebServlet(value = "/async-forward", loadOnStartup = 0)
    @SuppressWarnings("serial")
    public static class SimpleServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            request.getSession().setAttribute("async-dispatch", "c");
            TestSpans.createLocalSpan();
            response.getWriter().println("the response");
        }
    }
}
