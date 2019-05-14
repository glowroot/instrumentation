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
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionAttributeIT {

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
    public void testHasSessionAttribute() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("testattr"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan).get("testattr")).isEqualTo("val");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasSessionAttributeWithoutTrimmedAttributeName() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of(" testattr ", " other"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan).get("testattr")).isEqualTo("val");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasSessionAttributeUsingWildcard() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan).get("testattr")).isEqualTo("val");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasSessionAttributeUsingWildcardPlusOther() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*", "other", "::id"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan).get("testattr")).isEqualTo("val");
        assertThat(getSessionAttributes(incomingSpan).get("::id")).isEqualTo("123456789");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasSessionAttributeNotReadable() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.<String>of());
        // when
        IncomingSpan incomingSpan = container.executeForType(HasSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testSetSessionAttribute() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("testattr", "testother"));
        // when
        IncomingSpan incomingSpan = container.executeForType(SetSessionAttribute.class, "Web");
        // then
        assertThat(getInitialSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getInitialSessionAttributes(incomingSpan).get("testother")).isEqualTo("v");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("testattr")).isEqualTo("val");
    }

    @Test
    public void testSetSessionAttributeUsingWildcard() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));
        // when
        IncomingSpan incomingSpan = container.executeForType(SetSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("testattr")).isEqualTo("val");
    }

    @Test
    public void testSetSessionAttributeUsingWildcardAndOther() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*", "other"));
        // when
        IncomingSpan incomingSpan = container.executeForType(SetSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("testattr")).isEqualTo("val");
    }

    @Test
    public void testSetSessionAttributeNotReadable() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.<String>of());
        // when
        IncomingSpan incomingSpan = container.executeForType(SetSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testSetSessionAttributeNull() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));
        // when
        IncomingSpan incomingSpan = container.executeForType(SetSessionAttributeNull.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).containsValue("testattr")).isFalse();
    }

    @Test
    public void testHasNestedSessionAttributePath() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.two.three", "one.amap.x"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(HasNestedSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan).get("one.two.three")).isEqualTo("four");
        assertThat(getSessionAttributes(incomingSpan).get("one.amap.x")).isEqualTo("y");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testSetNestedSessionAttributePath() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.two.three", "one.amap.x"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetNestedSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("one.two.three"))
                .isEqualTo("four");
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("one.amap.x")).isEqualTo("y");
    }

    @Test
    public void testHasMissingSessionAttribute() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("missingtestattr"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasMissingNestedSessionAttributePath() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.missingtwo"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(HasNestedSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasNestedSessionAttributePath2() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.*", "one.two.*", "one.amap.*"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(HasNestedSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan)).hasSize(5);
        assertThat(getSessionAttributes(incomingSpan).get("one.two.three")).isEqualTo("four");
        assertThat(getSessionAttributes(incomingSpan).get("one.amap.x")).isEqualTo("y");
        assertThat(getSessionAttributes(incomingSpan).get("one.another")).isEqualTo("3");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testSetNestedSessionAttributePath2() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.*", "one.two.*", "one.amap.*"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetNestedSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).hasSize(5);
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("one.two.three"))
                .isEqualTo("four");
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("one.amap.x")).isEqualTo("y");
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("one.another")).isEqualTo("3");
    }

    @Test
    public void testSetNestedSessionAttributeToNull() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.*"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetNestedSessionAttributeToNull.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isEmpty();
    }

    @Test
    public void testSetSessionAttributeToNull() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.two"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(SetNestedSessionAttributeToNull.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isEmpty();
    }

    @Test
    public void testHasBadSessionAttribute() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasBadSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan)).hasSize(1);
        assertThat(getSessionAttributes(incomingSpan).get("one")).isEqualTo("");
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testSetBadSessionAttribute() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));
        // when
        IncomingSpan incomingSpan = container.executeForType(SetBadSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).hasSize(1);
        assertThat(getUpdatedSessionAttributes(incomingSpan).containsKey("one")).isTrue();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("one")).isEmpty();
    }

    @Test
    public void testHasMissingSessionAttribute2() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("missingtestattr.*"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasMissingNestedSessionAttributePath2() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("one.missingtwo.*"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(HasNestedSessionAttribute.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testGetBadAttributeNames() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("*"));
        // when
        IncomingSpan incomingSpan = container.executeForType(GetBadAttributeNames.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasHttpSession() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("::id"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasHttpSession.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNotNull();
        assertThat(getSessionAttributes(incomingSpan).get("::id")).isEqualTo("123456789");
        assertThat(getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testHasNoHttpSession() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("::id"));
        // when
        IncomingSpan incomingSpan = container.executeForType(HasNoHttpSession.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testCreateHttpSession() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("::id"));
        // when
        IncomingSpan incomingSpan = container.executeForType(CreateHttpSession.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("::id")).isEqualTo("123456789");
    }

    @Test
    public void testCreateHttpSessionTrue() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("::id"));
        // when
        IncomingSpan incomingSpan = container.executeForType(CreateHttpSessionTrue.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("::id")).isEqualTo("123456789");
    }

    @Test
    public void testCreateHttpSessionFalse() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("::id"));
        // when
        IncomingSpan incomingSpan = container.executeForType(CreateHttpSessionFalse.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan)).isNull();
    }

    @Test
    public void testChangeHttpSession() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("::id"));
        // when
        IncomingSpan incomingSpan = container.executeForType(ChangeHttpSession.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getInitialSessionAttributes(incomingSpan).get("::id")).isEqualTo("123456789");
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("::id")).isEqualTo("abcdef");
    }

    @Test
    public void testCreateAndChangeHttpSession() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "captureSessionAttributes",
                ImmutableList.of("::id"));
        // when
        IncomingSpan incomingSpan =
                container.executeForType(CreateAndChangeHttpSession.class, "Web");
        // then
        assertThat(getSessionAttributes(incomingSpan)).isNull();
        assertThat(getInitialSessionAttributes(incomingSpan)).isNull();
        assertThat(getUpdatedSessionAttributes(incomingSpan).get("::id")).isEqualTo("abcdef");
    }

    static Map<String, String> getSessionAttributes(IncomingSpan incomingSpan) {
        return getDetailMap(incomingSpan, "Session attributes");
    }

    static Map<String, String> getInitialSessionAttributes(IncomingSpan incomingSpan) {
        return getDetailMap(incomingSpan, "Session attributes (at beginning of this request)");
    }

    static Map<String, String> getUpdatedSessionAttributes(IncomingSpan incomingSpan) {
        return getDetailMap(incomingSpan, "Session attributes (updated during this request)");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getDetailMap(IncomingSpan incomingSpan, String name) {
        return (Map<String, String>) incomingSpan.detail().get(name);
    }

    @SuppressWarnings("serial")
    public static class HasSessionAttribute extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            MockHttpSession session = new MockHttpSession(request.getServletContext(), "123456789");
            ((MockHttpServletRequest) request).setSession(session);
            request.getSession().setAttribute("testattr", "val");
        }
    }

    @SuppressWarnings("serial")
    public static class SetSessionAttribute extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("testother", "v");
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("testattr", "val");
        }
    }

    @SuppressWarnings("serial")
    public static class SetSessionAttributeNull extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("testattr", "something");
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("testattr", null);
        }
    }

    @SuppressWarnings("serial")
    public static class HasNestedSessionAttribute extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("one", new NestedTwo("four", "3"));
        }
    }

    @SuppressWarnings("serial")
    public static class SetNestedSessionAttribute extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("one", new NestedTwo("four", "3"));
        }
    }

    @SuppressWarnings("serial")
    public static class SetNestedSessionAttributeToNull extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("one", null);
        }
    }

    @SuppressWarnings("serial")
    public static class HasBadSessionAttribute extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("one", new BadObject());
        }
    }

    @SuppressWarnings("serial")
    public static class SetBadSessionAttribute extends TestServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            request.getSession().setAttribute("one", new BadObject());
        }
    }

    @SuppressWarnings("serial")
    public static class GetBadAttributeNames extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            ((MockHttpServletRequest) request).setSession(new MockHttpSession() {
                @Override
                public Enumeration<String> getAttributeNames() {
                    return Collections.enumeration(Lists.newArrayList((String) null));
                }
            });
        }
    }

    @SuppressWarnings("serial")
    public static class HasHttpSession extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            MockHttpSession session = new MockHttpSession(request.getServletContext(), "123456789");
            ((MockHttpServletRequest) request).setSession(session);
        }
    }

    @SuppressWarnings("serial")
    public static class HasNoHttpSession extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {}
    }

    @SuppressWarnings("serial")
    public static class CreateHttpSession extends TestServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            MockHttpSession session = new MockHttpSession(request.getServletContext(), "123456789");
            ((MockHttpServletRequest) request).setSession(session);
            request.getSession();
        }
    }

    @SuppressWarnings("serial")
    public static class CreateHttpSessionTrue extends TestServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            MockHttpSession session = new MockHttpSession(request.getServletContext(), "123456789");
            ((MockHttpServletRequest) request).setSession(session);
            request.getSession(true);
            super.service(request, response);
        }
    }

    @SuppressWarnings("serial")
    public static class CreateHttpSessionFalse extends TestServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            request.getSession(false);
            super.service(request, response);
        }
    }

    @SuppressWarnings("serial")
    public static class ChangeHttpSession extends TestServlet {

        @Override
        protected void before(HttpServletRequest request, HttpServletResponse response) {
            MockHttpSession session = new MockHttpSession(request.getServletContext(), "123456789");
            ((MockHttpServletRequest) request).setSession(session);
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            request.getSession().invalidate();
            MockHttpSession session = new MockHttpSession(request.getServletContext(), "abcdef");
            ((MockHttpServletRequest) request).setSession(session);
            request.getSession();
            super.service(request, response);
        }
    }

    @SuppressWarnings("serial")
    public static class CreateAndChangeHttpSession extends TestServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            MockHttpSession session = new MockHttpSession(request.getServletContext(), "123456789");
            ((MockHttpServletRequest) request).setSession(session);
            request.getSession().invalidate();
            session = new MockHttpSession(request.getServletContext(), "abcdef");
            ((MockHttpServletRequest) request).setSession(session);
            request.getSession();
            super.service(request, response);
        }
    }

    public static class NestedTwo {

        private final NestedThree two;
        private final StringBuilder another;
        private final String iamnull = null;
        private final Map<String, String> amap = ImmutableMap.of("x", "y");

        public NestedTwo(String two, String another) {
            this.two = new NestedThree(two);
            this.another = new StringBuilder(another);
        }

        public NestedThree getTwo() {
            return two;
        }

        public StringBuilder getAnother() {
            return another;
        }

        public String getIamnull() {
            return iamnull;
        }

        public Map<String, String> getAmap() {
            return amap;
        }
    }

    public static class NestedThree {

        private final String three;

        public NestedThree(String three) {
            this.three = three;
        }

        public String getThree() {
            return three;
        }
    }

    public static class BadObject {

        @Override
        public String toString() {
            return null;
        }
    }
}
