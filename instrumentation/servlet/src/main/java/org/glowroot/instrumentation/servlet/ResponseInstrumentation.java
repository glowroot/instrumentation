/*
 * Copyright 2014-2019 the original author or authors.
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

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.servlet.boot.Patterns;
import org.glowroot.instrumentation.servlet.boot.ResponseInvoker;
import org.glowroot.instrumentation.servlet.boot.ServletInstrumentationProperties;
import org.glowroot.instrumentation.servlet.boot.ServletMessageSupplier;

public class ResponseInstrumentation {

    @Advice.Pointcut(className = "javax.servlet.ServletResponse",
                     methodName = "setContentLength",
                     methodParameterTypes = {"int"},
                     nestingGroup = "servlet-inner-call")
    public static class SetContentLengthAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) int value,
                ThreadContext context) {

            if (!ServletInstrumentationProperties.captureContentLengthResponseHeader()) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseIntHeader("Content-Length", value);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.ServletResponse",
                     methodName = "setContentLengthLong",
                     methodParameterTypes = {"long"},
                     nestingGroup = "servlet-inner-call")
    public static class SetContentLengthLongAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) long value,
                ThreadContext context) {

            if (!ServletInstrumentationProperties.captureContentLengthResponseHeader()) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseLongHeader("Content-Length", value);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.ServletResponse",
                     methodName = "setContentType",
                     methodParameterTypes = {"java.lang.String"},
                     nestingGroup = "servlet-inner-call")
    public static class SetContentTypeAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.This Object response,
                @Bind.Argument(0) @Nullable String value,
                @Bind.ClassMeta ResponseInvoker responseInvoker,
                ThreadContext context) {

            if (value == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            if (!ServletInstrumentationProperties.captureContentTypeResponseHeader()) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                if (responseInvoker.hasGetContentTypeMethod()) {
                    String contentType = responseInvoker.getContentType(response);
                    messageSupplier.setResponseHeader("Content-Type", contentType);
                } else {
                    messageSupplier.setResponseHeader("Content-Type", value);
                }
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.ServletResponse",
                     methodName = "setCharacterEncoding",
                     methodParameterTypes = {"java.lang.String"},
                     nestingGroup = "servlet-inner-call")
    public static class SetCharacterEncodingAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.This Object response,
                @Bind.ClassMeta ResponseInvoker responseInvoker,
                ThreadContext context) {

            if (!ServletInstrumentationProperties.captureContentTypeResponseHeader()) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null && responseInvoker.hasGetContentTypeMethod()) {
                String contentType = responseInvoker.getContentType(response);
                messageSupplier.setResponseHeader("Content-Type", contentType);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.ServletResponse",
                     methodName = "setLocale",
                     methodParameterTypes = {"java.util.Locale"},
                     nestingGroup = "servlet-inner-call")
    public static class SetLocaleAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.This Object response,
                @Bind.Argument(0) @Nullable Locale locale,
                @Bind.ClassMeta ResponseInvoker responseInvoker,
                ThreadContext context) {

            if (locale == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            boolean captureContentLanguage =
                    ServletInstrumentationProperties.captureContentLanguageResponseHeader();
            boolean captureContentType =
                    ServletInstrumentationProperties.captureContentTypeResponseHeader();
            if (!captureContentLanguage && !captureContentType) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                if (captureContentLanguage) {
                    messageSupplier.setResponseHeader("Content-Language", locale.toString());
                }
                if (captureContentType && responseInvoker.hasGetContentTypeMethod()) {
                    String contentType = responseInvoker.getContentType(response);
                    messageSupplier.setResponseHeader("Content-Type", contentType);
                }
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "setHeader",
                     methodParameterTypes = {"java.lang.String", "java.lang.String"},
                     nestingGroup = "servlet-inner-call")
    public static class SetHeaderAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) @Nullable String name,
                @Bind.Argument(1) @Nullable String value,
                ThreadContext context) {

            if (name == null || value == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            if (!captureResponseHeader(name)) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseHeader(name, value);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "setDateHeader",
                     methodParameterTypes = {"java.lang.String", "long"},
                     nestingGroup = "servlet-inner-call")
    public static class SetDateHeaderAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) @Nullable String name,
                @Bind.Argument(1) long value,
                ThreadContext context) {

            if (name == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            if (!captureResponseHeader(name)) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseDateHeader(name, value);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "setIntHeader",
                     methodParameterTypes = {"java.lang.String", "int"},
                     nestingGroup = "servlet-inner-call")
    public static class SetIntHeaderAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) @Nullable String name,
                @Bind.Argument(1) int value,
                ThreadContext context) {

            if (name == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            if (!captureResponseHeader(name)) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseIntHeader(name, value);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "addHeader",
                     methodParameterTypes = {"java.lang.String", "java.lang.String"},
                     nestingGroup = "servlet-inner-call")
    public static class AddHeaderAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) @Nullable String name,
                @Bind.Argument(1) @Nullable String value,
                ThreadContext context) {

            if (name == null || value == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            if (!captureResponseHeader(name)) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.addResponseHeader(name, value);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "addDateHeader",
                     methodParameterTypes = {"java.lang.String", "long"},
                     nestingGroup = "servlet-inner-call")
    public static class AddDateHeaderAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) @Nullable String name,
                @Bind.Argument(1) long value,
                ThreadContext context) {

            if (name == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            if (!captureResponseHeader(name)) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.addResponseDateHeader(name, value);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "addIntHeader",
                     methodParameterTypes = {"java.lang.String", "int"},
                     nestingGroup = "servlet-inner-call")
    public static class AddIntHeaderAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            // good to short-cut advice if no response headers need to be captured
            return ServletInstrumentationProperties.captureResponseHeadersNonEmpty();
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) @Nullable String name,
                @Bind.Argument(1) int value,
                ThreadContext context) {

            if (name == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            if (!captureResponseHeader(name)) {
                return;
            }
            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.addResponseIntHeader(name, value);
            }
        }
    }

    private static boolean captureResponseHeader(String name) {

        List<Pattern> capturePatterns = ServletInstrumentationProperties.captureResponseHeaders();
        // converted to lower case for case-insensitive matching (patterns are lower case)
        String keyLowerCase = name.toLowerCase(Locale.ENGLISH);
        return Patterns.matchesOneOf(keyLowerCase, capturePatterns);
    }
}
