/*
 * Copyright 2019 the original author or authors.
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

import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestCookieIT {

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
    public void testRequestCookies() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureRequestCookies",
                ImmutableList.of("One", "Two"));

        // when
        IncomingSpan incomingSpan = container.executeForType(SetRequestCookies.class, "Web");

        // then
        Map<String, Object> requestCookies =
                ResponseHeaderIT.getDetailMap(incomingSpan, "Request cookies");
        assertThat(requestCookies.get("One")).isEqualTo("111");
        assertThat(requestCookies.get("Two")).isEqualTo("222");
    }

    @Test
    public void testRequestCookiesLowercase() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureRequestCookies",
                ImmutableList.of("One", "Two"));

        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetRequestCookiesLowercase.class, "Web");

        // then
        Map<String, Object> requestCookies =
                ResponseHeaderIT.getDetailMap(incomingSpan, "Request cookies");
        // cookie names are case sensitive
        assertThat(requestCookies).isNull();
    }

    @SuppressWarnings("serial")
    public static class SetRequestCookies extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            Cookie[] cookies = new Cookie[2];
            cookies[0] = new Cookie("One", "111");
            cookies[1] = new Cookie("Two", "222");
            ((MockHttpServletRequest) request).setCookies(cookies);
        }
    }

    @SuppressWarnings("serial")
    public static class SetRequestCookiesLowercase extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            Cookie[] cookies = new Cookie[2];
            cookies[0] = new Cookie("one", "111");
            cookies[1] = new Cookie("two", "222");
            ((MockHttpServletRequest) request).setCookies(cookies);
        }
    }
}
