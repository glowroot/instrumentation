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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class RestControllerIT {

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
    public void shouldCaptureTransactionNameWithNormalServletMappingHittingRest() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "", "/rest");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /rest");

        validateSpans(incomingSpan.childSpans(), TestRestController.class, "rest");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingHittingAbc() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "", "/abc");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /abc");

        validateSpans(incomingSpan.childSpans(), TestRestWithPropertyController.class, "abc");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndNormalServletMappingHittingRest()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "/zzz", "/rest");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/rest");

        validateSpans(incomingSpan.childSpans(), TestRestController.class, "rest");
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathAndNormalServletMappingHittingAbc()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeSpringControllerInTomcat.class,
                "Web", "webapp1", "/zzz", "/abc");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/abc");

        validateSpans(incomingSpan.childSpans(), TestRestWithPropertyController.class, "abc");
    }

    private void validateSpans(List<Span> spans, Class<?> clazz, String methodName) {
        Iterator<Span> i = spans.iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message())
                .isEqualTo("spring controller: " + clazz.getName() + "." + methodName + "()");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    @RestController
    public static class TestRestController {
        @RequestMapping("rest")
        public String rest() {
            return "";
        }
    }

    @RestController
    public static class TestRestWithPropertyController {
        @RequestMapping("${abc.path:abc}")
        public String abc() {
            return "";
        }
    }
}
