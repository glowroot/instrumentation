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
package org.glowroot.instrumentation.spring;

import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ControllerIT {

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
    public void shouldCaptureTransactionNameWithNormalServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "", "/hello/echo/5");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("" + "/hello/echo/*");

        validateSpans(incomingSpan.childSpans(), TestController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndNormalServletMapping()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "/zzz", "/hello/echo/5");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz" + "/hello/echo/*");

        validateSpans(incomingSpan.childSpans(), TestController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingHittingRoot() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("" + "/");

        validateSpans(incomingSpan.childSpans(), RootController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndNormalServletMappingHittingRoot()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "/zzz", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz" + "/");

        validateSpans(incomingSpan.childSpans(), RootController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp2", "", "/spring/hello/echo/5");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("" + "/spring/hello/echo/*");

        validateSpans(incomingSpan.childSpans(), TestController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndNestedServletMapping()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp2", "/zzz", "/spring/hello/echo/5");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz" + "/spring/hello/echo/*");

        validateSpans(incomingSpan.childSpans(), TestController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMappingHittingRoot() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp2", "", "/spring/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("" + "/spring/");

        validateSpans(incomingSpan.childSpans(), RootController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndNestedServletMappingHittingRoot()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp2", "/zzz", "/spring/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz" + "/spring/");

        validateSpans(incomingSpan.childSpans(), RootController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp3", "", "/hello/echo/5");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("" + "/hello/echo/*");

        validateSpans(incomingSpan.childSpans(), TestController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndLessNormalServletMapping()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp3", "/zzz", "/hello/echo/5");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz" + "/hello/echo/*");

        validateSpans(incomingSpan.childSpans(), TestController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMappingHittingRoot()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp3", "", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("" + "/");

        validateSpans(incomingSpan.childSpans(), RootController.class, "echo");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndLessNormalServletMappingHittingRoot()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp3", "/zzz", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz" + "/");

        validateSpans(incomingSpan.childSpans(), RootController.class, "echo");
    }

    @Test
    public void shouldCaptureAltTransactionName() throws Exception {
        // given
        container.setInstrumentationProperty("spring", "useAltTransactionNaming", true);

        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "", "/hello/echo/5");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("TestController#echo");

        validateSpans(incomingSpan.childSpans(), TestController.class, "echo");
    }

    private void validateSpans(List<Span> spans, Class<?> clazz, String methodName) {
        Iterator<Span> i = spans.iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message())
                .isEqualTo("spring controller: " + clazz.getName() + "." + methodName + "()");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @Controller
    @RequestMapping("hello")
    public static class TestController {

        @RequestMapping("echo/{id}")
        public @ResponseBody String echo() {
            return "";
        }
    }

    @Controller
    public static class RootController {

        @RequestMapping("")
        public @ResponseBody String echo() {
            return "";
        }
    }
}
