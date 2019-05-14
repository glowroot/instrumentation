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
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockServletConfig;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class StartupIT {

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
    public void testServletContextInitialized() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(TestServletContextListener.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan)
                .isEqualTo("Listener init: " + TestServletContextListener.class.getName());
    }

    @Test
    public void testServletInit() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(TestServletInit.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan)
                .isEqualTo("Servlet init: " + TestServletInit.class.getName());
    }

    @Test
    public void testFilterInit() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(TestFilterInit.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan)
                .isEqualTo("Filter init: " + TestFilterInit.class.getName());
    }

    @Test
    public void testContainerInitializer() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(TestServletContainerInitializer.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo(
                "Container initializer: " + TestServletContainerInitializer.class.getName());
    }

    public static class TestServletContextListener
            implements AppUnderTest, TransactionMarker, ServletContextListener {

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            contextInitialized(null);
        }

        @Override
        public void contextInitialized(ServletContextEvent sce) {}

        @Override
        public void contextDestroyed(ServletContextEvent sce) {}
    }

    @SuppressWarnings("serial")
    public static class TestServletInit extends HttpServlet
            implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws ServletException {
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws ServletException {
            init(new MockServletConfig());
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            // calling super to make sure it doesn't end up in an infinite loop (this happened once
            // before due to bug in weaver)
            super.init(config);
        }
    }

    public static class TestFilterInit implements AppUnderTest, TransactionMarker, Filter {

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            init(new MockFilterConfig());
        }

        @Override
        public void init(FilterConfig filterConfig) {}

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {}

        @Override
        public void destroy() {}
    }

    public static class TestServletContainerInitializer
            implements AppUnderTest, TransactionMarker, ServletContainerInitializer {

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            onStartup(null, null);
        }

        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx) {}
    }
}
