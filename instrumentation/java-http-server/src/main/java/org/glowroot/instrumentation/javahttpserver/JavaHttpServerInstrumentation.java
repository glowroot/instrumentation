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

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.util.FastThreadLocal;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Shim;

public class JavaHttpServerInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http request");

    private static final Getter<HttpExchange> GETTER = new GetterImpl();

    @Shim("com.sun.net.httpserver.HttpExchange")
    public interface HttpExchange {

        @Nullable
        URI getRequestURI();

        @Nullable
        String getRequestMethod();

        @Shim("com.sun.net.httpserver.Headers getRequestHeaders()")
        @Nullable
        Headers glowroot$getRequestHeaders();

        @Shim("com.sun.net.httpserver.Headers getResponseHeaders()")
        @Nullable
        Headers glowroot$getResponseHeaders();

        @Nullable
        InetSocketAddress getRemoteAddress();
    }

    @Shim("com.sun.net.httpserver.Headers")
    public interface Headers extends Map<String, List<String>> {

        @Nullable
        String getFirst(String key);
    }

    private static final FastThreadLocal</*@Nullable*/ String> sendError =
            new FastThreadLocal</*@Nullable*/ String>();

    @Advice.Pointcut(className = "com.sun.net.httpserver.HttpHandler",
                     methodName = "handle",
                     methodParameterTypes = {"com.sun.net.httpserver.HttpExchange"},
                     nestingGroup = "outer-handler-or-filter")
    public static class HandleAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.Argument(0) @Nullable HttpExchange exchange,
                OptionalThreadContext context) {

            return onBeforeCommon(exchange, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(0) @Nullable HttpExchange exchange,
                OptionalThreadContext context) {

            onReturnCommon(span, exchange, context);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(0) @Nullable HttpExchange exchange) {

            onThrowCommon(t, span, exchange);
        }
    }

    @Advice.Pointcut(className = "com.sun.net.httpserver.Filter",
                     methodName = "doFilter",
                     methodParameterTypes = {"com.sun.net.httpserver.HttpExchange",
                             "com.sun.net.httpserver.Filter$Chain"},
                     nestingGroup = "outer-handler-or-filter")
    public static class DoFilterAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.Argument(0) @Nullable HttpExchange exchange,
                OptionalThreadContext context) {

            return onBeforeCommon(exchange, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(0) @Nullable HttpExchange exchange,
                OptionalThreadContext context) {

            onReturnCommon(span, exchange, context);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(0) @Nullable HttpExchange exchange) {

            onThrowCommon(t, span, exchange);
        }
    }

    @Advice.Pointcut(className = "com.sun.net.httpserver.HttpExchange",
                     methodName = "sendResponseHeaders",
                     methodParameterTypes = {"int", "long"},
                     nestingGroup = "handler-inner-call")
    public static class SendResponseHeadersAdvice {

        // using @Advice.IsEnabled like this avoids ThreadContext lookup for common case
        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) Integer statusCode) {

            return statusCode >= 500
                    || JavaHttpServerInstrumentationProperties.traceErrorOn4xxResponseCode()
                            && statusCode >= 400;
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Argument(0) Integer statusCode) {

            FastThreadLocal.Holder</*@Nullable*/ String> errorMessageHolder = sendError.getHolder();
            if (errorMessageHolder.get() == null) {
                errorMessageHolder.set("sendResponseHeaders, HTTP status code " + statusCode);
            }
        }
    }

    @Advice.Pointcut(className = "com.sun.net.httpserver.HttpExchange",
                     methodName = "getPrincipal",
                     methodParameterTypes = {},
                     methodReturnType = "com.sun.net.httpserver.HttpPrincipal",
                     nestingGroup = "handler-inner-call")
    public static class GetPrincipalAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Principal principal,
                ThreadContext context) {

            if (principal != null) {
                context.setTransactionUser(principal.getName(), Priority.CORE_INSTRUMENTATION);
            }
        }
    }

    private static @Nullable Span onBeforeCommon(@Nullable HttpExchange exchange,
            OptionalThreadContext context) {

        if (exchange == null) {
            // seems nothing sensible to do here other than ignore
            return null;
        }
        String requestUri = getRequestURI(exchange.getRequestURI());
        String requestQueryString = getRequestQueryString(exchange.getRequestURI());
        String requestMethod = Strings.nullToEmpty(exchange.getRequestMethod());
        Map<String, Object> requestHeaders = DetailCapture.captureRequestHeaders(exchange);
        String requestRemoteAddr = DetailCapture.captureRequestRemoteAddr(exchange);
        String requestRemoteHost = DetailCapture.captureRequestRemoteHost(exchange);
        HttpHandlerMessageSupplier messageSupplier =
                new HttpHandlerMessageSupplier(requestMethod, requestUri, requestQueryString,
                        requestHeaders, requestRemoteAddr, requestRemoteHost);
        String transactionType = "Web";
        boolean setWithCoreMaxPriority = false;
        Headers headers = exchange.glowroot$getRequestHeaders();
        if (headers != null) {
            String transactionTypeHeader = headers.getFirst("X-Glowroot-Transaction-Type");
            if ("Synthetic".equals(transactionTypeHeader)) {
                // X-Glowroot-Transaction-Type header currently only accepts "Synthetic", in order
                // to prevent spamming of transaction types, which could cause some issues
                transactionType = transactionTypeHeader;
                setWithCoreMaxPriority = true;
            }
        }
        Span span = context.startIncomingSpan(transactionType, requestUri, GETTER, exchange,
                messageSupplier, TIMER_NAME, AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
        if (setWithCoreMaxPriority) {
            context.setTransactionType(transactionType, Priority.CORE_MAX);
        }
        // X-Glowroot-Transaction-Name header is useful for automated tests which want to send a
        // more specific name for the transaction
        if (headers != null) {
            String transactionNameOverride = headers.getFirst("X-Glowroot-Transaction-Name");
            if (transactionNameOverride != null) {
                context.setTransactionName(transactionNameOverride, Priority.CORE_MAX);
            }
        }
        return span;
    }

    private static void onReturnCommon(@Nullable Span span, @Nullable HttpExchange exchange,
            OptionalThreadContext context) {

        if (span == null) {
            return;
        }
        setResponseHeaders(exchange, span.getMessageSupplier());
        FastThreadLocal.Holder</*@Nullable*/ String> errorMessageHolder = sendError.getHolder();
        String errorMessage = errorMessageHolder.get();
        if (errorMessage != null) {
            context.setTransactionError(errorMessage);
            errorMessageHolder.set(null);
        }
        span.end();
    }

    private static void onThrowCommon(Throwable t, @Nullable Span span,
            @Nullable HttpExchange exchange) {
        if (span == null) {
            return;
        }
        // ignoring potential sendError since this seems worse
        sendError.set(null);
        setResponseHeaders(exchange, span.getMessageSupplier());
        span.endWithError(t);
    }

    private static String getRequestURI(@Nullable URI uri) {
        if (uri != null) {
            return Strings.nullToEmpty(uri.getPath());
        } else {
            return "";
        }
    }

    private static @Nullable String getRequestQueryString(@Nullable URI uri) {
        if (uri != null) {
            return uri.getQuery();
        } else {
            return null;
        }
    }

    private static void setResponseHeaders(@Nullable HttpExchange exchange,
            @Nullable Object messageSupplier) {
        if (exchange != null && messageSupplier instanceof HttpHandlerMessageSupplier) {
            ((HttpHandlerMessageSupplier) messageSupplier)
                    .setResponseHeaders(DetailCapture.captureResponseHeaders(exchange));
        }
    }

    private static class GetterImpl implements Getter<HttpExchange> {

        @Override
        public @Nullable String get(HttpExchange carrier, String key) {
            Headers headers = carrier.glowroot$getRequestHeaders();
            if (headers == null) {
                return null;
            }
            return headers.getFirst(key);
        }
    }
}
