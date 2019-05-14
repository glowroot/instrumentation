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

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.util.FastThreadLocal;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.servlet.boot.RequestHostAndPortDetail;
import org.glowroot.instrumentation.servlet.boot.RequestInvoker;
import org.glowroot.instrumentation.servlet.boot.ResponseInvoker;
import org.glowroot.instrumentation.servlet.boot.SendError;
import org.glowroot.instrumentation.servlet.boot.ServletInstrumentationProperties;
import org.glowroot.instrumentation.servlet.boot.ServletInstrumentationProperties.SessionAttributePath;
import org.glowroot.instrumentation.servlet.boot.ServletMessageSupplier;
import org.glowroot.instrumentation.servlet.boot.Strings;

// this instrumentation is careful not to rely on request or session objects being thread-safe
public class ServletInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http request");

    private static final Getter<HttpServletRequest> GETTER = new HttpServletRequestGetter();

    private static final Setter<HttpServletResponse> SETTER = new HttpServletResponseSetter();

    @Advice.Pointcut(className = "javax.servlet.Servlet",
                     methodName = "service",
                     methodParameterTypes = {"javax.servlet.ServletRequest",
                             "javax.servlet.ServletResponse"},
                     nestingGroup = "outer-servlet-or-filter")
    public static class ServiceAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                OptionalThreadContext context,
                @Bind.Argument(0) @Nullable ServletRequest req,
                @Bind.Argument(1) @Nullable ServletResponse res,
                @Bind.ClassMeta RequestInvoker requestInvoker) {

            return onBeforeCommon(req, res, null, requestInvoker, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(1) @Nullable ServletResponse res,
                @Bind.ClassMeta ResponseInvoker responseInvoker) {

            onReturnCommon(span, res, responseInvoker, context);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(1) @Nullable ServletResponse res) {

            onThrowCommon(t, span, res, context);
        }
    }

    @Advice.Pointcut(className = "javax.servlet.Filter",
                     methodName = "doFilter",
                     methodParameterTypes = {"javax.servlet.ServletRequest",
                             "javax.servlet.ServletResponse",
                             "javax.servlet.FilterChain"},
                     nestingGroup = "outer-servlet-or-filter")
    public static class DoFilterAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                OptionalThreadContext context,
                @Bind.Argument(0) @Nullable ServletRequest req,
                @Bind.Argument(1) @Nullable ServletResponse res,
                @Bind.ClassMeta RequestInvoker requestInvoker) {

            return onBeforeCommon(req, res, null, requestInvoker, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(1) @Nullable ServletResponse res,
                @Bind.ClassMeta ResponseInvoker responseInvoker) {

            onReturnCommon(span, res, responseInvoker, context);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(1) @Nullable ServletResponse res) {

            onThrowCommon(t, span, res, context);
        }
    }

    @Advice.Pointcut(
                     className = "org.eclipse.jetty.server.Handler"
                             + "|wiremock.org.eclipse.jetty.server.Handler",
                     subTypeRestriction = "/(?!org\\.eclipse\\.jetty.)"
                             + "(?!wiremock.org\\.eclipse\\.jetty.).*/",
                     methodName = "handle",
                     methodParameterTypes = {"java.lang.String",
                             "org.eclipse.jetty.server.Request|wiremock.org.eclipse.jetty.server.Request",
                             "javax.servlet.http.HttpServletRequest",
                             "javax.servlet.http.HttpServletResponse"},
                     nestingGroup = "outer-servlet-or-filter")
    public static class JettyHandlerAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                OptionalThreadContext context,
                @Bind.Argument(2) @Nullable ServletRequest req,
                @Bind.Argument(3) @Nullable ServletResponse res,
                @Bind.ClassMeta RequestInvoker requestInvoker) {

            return onBeforeCommon(req, res, null, requestInvoker, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(3) @Nullable ServletResponse res,
                @Bind.ClassMeta ResponseInvoker responseInvoker) {

            onReturnCommon(span, res, responseInvoker, context);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(3) @Nullable ServletResponse res) {

            onThrowCommon(t, span, res, context);
        }
    }

    // this pointcut makes sure to only set the transaction type to WireMock if WireMock is the
    // first servlet encountered
    @Advice.Pointcut(className = "javax.servlet.Servlet",
                     subTypeRestriction = "com.github.tomakehurst.wiremock.jetty9"
                             + ".JettyHandlerDispatchingServlet",
                     methodName = "service",
                     methodParameterTypes = {"javax.servlet.ServletRequest",
                             "javax.servlet.ServletResponse"},
                     nestingGroup = "outer-servlet-or-filter",
                     order = -1)
    public static class WireMockAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                OptionalThreadContext context,
                @Bind.Argument(0) @Nullable ServletRequest req,
                @Bind.Argument(1) @Nullable ServletResponse res,
                @Bind.ClassMeta RequestInvoker requestInvoker) {

            return onBeforeCommon(req, res, "WireMock", requestInvoker, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(1) @Nullable ServletResponse res,
                @Bind.ClassMeta ResponseInvoker responseInvoker) {

            onReturnCommon(span, res, responseInvoker, context);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                OptionalThreadContext context,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(1) @Nullable ServletResponse res) {

            onThrowCommon(t, span, res, context);
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "sendError",
                     methodParameterTypes = {"int", ".."},
                     nestingGroup = "servlet-inner-call")
    public static class SendErrorAdvice {

        // wait until after because sendError throws IllegalStateException if the response has
        // already been committed
        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Argument(0) int statusCode, ThreadContext context) {

            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseCode(statusCode);
            }
            if (captureAsError(statusCode)) {
                FastThreadLocal.Holder</*@Nullable*/ String> errorMessageHolder =
                        SendError.getErrorMessageHolder();
                if (errorMessageHolder.get() == null) {
                    errorMessageHolder.set("sendError, HTTP status code " + statusCode);
                }
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "sendRedirect",
                     methodParameterTypes = {"java.lang.String"},
                     nestingGroup = "servlet-inner-call")
    public static class SendRedirectAdvice {

        // wait until after because sendError throws IllegalStateException if the response has
        // already been committed
        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.This HttpServletResponse response,
                @Bind.Argument(0) @Nullable String location,
                @Bind.ClassMeta ResponseInvoker responseInvoker,
                ThreadContext context) {

            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseCode(302);
                if (responseInvoker.hasGetHeaderMethod()) {
                    // get the header as set by the container (e.g. after it converts relative
                    // to
                    // absolute path)
                    String header = responseInvoker.getHeader(response, "Location");
                    messageSupplier.addResponseHeader("Location", header);
                } else if (location != null) {
                    messageSupplier.addResponseHeader("Location", location);
                }
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletResponse",
                     methodName = "setStatus",
                     methodParameterTypes = {"int", ".."},
                     nestingGroup = "servlet-inner-call")
    public static class SetStatusAdvice {

        // wait until after because sendError throws IllegalStateException if the response has
        // already been committed
        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Argument(0) int statusCode, ThreadContext context) {

            ServletMessageSupplier messageSupplier =
                    (ServletMessageSupplier) context.getServletRequestInfo();
            if (messageSupplier != null) {
                messageSupplier.setResponseCode(statusCode);
            }
            if (captureAsError(statusCode)) {
                FastThreadLocal.Holder</*@Nullable*/ String> errorMessageHolder =
                        SendError.getErrorMessageHolder();
                if (errorMessageHolder.get() == null) {
                    errorMessageHolder.set("setStatus, HTTP status code " + statusCode);
                }
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletRequest",
                     methodName = "getUserPrincipal",
                     methodParameterTypes = {},
                     methodReturnType = "java.security.Principal",
                     nestingGroup = "servlet-inner-call")
    public static class GetUserPrincipalAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Principal principal,
                ThreadContext context) {

            if (principal != null) {
                context.setTransactionUser(principal.getName(), Priority.CORE_INSTRUMENTATION);
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.http.HttpServletRequest",
                     methodName = "getSession",
                     methodParameterTypes = {".."},
                     nestingGroup = "servlet-inner-call")
    public static class GetSessionAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable HttpSession session,
                ThreadContext context) {

            if (session == null) {
                return;
            }
            if (ServletInstrumentationProperties.sessionUserAttributeIsId()) {
                context.setTransactionUser(session.getId(), Priority.CORE_INSTRUMENTATION);
            }
            if (ServletInstrumentationProperties.captureSessionAttributeNamesContainsId()) {
                ServletMessageSupplier messageSupplier =
                        (ServletMessageSupplier) context.getServletRequestInfo();
                if (messageSupplier != null) {
                    messageSupplier.putSessionAttributeChangedValue(
                            ServletInstrumentationProperties.HTTP_SESSION_ID_ATTR,
                            session.getId());
                }
            }
        }
    }

    @Advice.Pointcut(className = "javax.servlet.Servlet",
                     methodName = "init",
                     methodParameterTypes = {"javax.servlet.ServletConfig"})
    public static class ServiceInitAdvice {

        @Advice.OnMethodBefore
        public static void onBefore() {

            ContainerStartup.initPlatformMBeanServer();
        }
    }

    @SuppressWarnings("deprecation")
    private static @Nullable Span onBeforeCommon(@Nullable ServletRequest req,
            @Nullable ServletResponse res, @Nullable String transactionTypeOverride,
            RequestInvoker requestInvoker, OptionalThreadContext context) {

        if (context.getServletRequestInfo() != null) {
            return null;
        }
        if (!(req instanceof HttpServletRequest) || !(res instanceof HttpServletResponse)) {
            // seems nothing sensible to do here other than ignore
            return null;
        }
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        AuxThreadContext auxContext = (AuxThreadContext) request
                .getAttribute(AsyncServletInstrumentation.AUX_CONTEXT_REQUEST_ATTRIBUTE);
        if (auxContext != null) {
            request.removeAttribute(AsyncServletInstrumentation.AUX_CONTEXT_REQUEST_ATTRIBUTE);
            return auxContext.startAndMarkAsyncTransactionComplete();
        }
        // request parameter map is collected in GetParameterAdvice
        // session info is collected here if the request already has a session
        ServletMessageSupplier messageSupplier;
        HttpSession session = request.getSession(false);
        String requestUri = Strings.nullToEmpty(request.getRequestURI());
        // don't convert null to empty, since null means no query string, while empty means
        // url ended with ? but nothing after that
        String requestQueryString = request.getQueryString();
        String requestMethod = Strings.nullToEmpty(request.getMethod());
        String requestContextPath = Strings.nullToEmpty(request.getContextPath());
        String requestServletPath = Strings.nullToEmpty(request.getServletPath());
        String requestPathInfo = request.getPathInfo();
        Map<String, Object> requestHeaders = DetailCapture.captureRequestHeaders(request);
        RequestHostAndPortDetail requestHostAndPortDetail =
                DetailCapture.captureRequestHostAndPortDetail(request, requestInvoker);
        Map<String, String> requestCookies = DetailCapture.captureRequestCookies(request);
        if (session == null) {
            messageSupplier = new ServletMessageSupplier(requestMethod, requestContextPath,
                    requestServletPath, requestPathInfo, requestUri, requestQueryString,
                    requestHeaders, requestCookies, requestHostAndPortDetail,
                    Collections.<String, String>emptyMap());
        } else {
            Map<String, String> sessionAttributes = HttpSessions.getSessionAttributes(session);
            messageSupplier = new ServletMessageSupplier(requestMethod, requestContextPath,
                    requestServletPath, requestPathInfo, requestUri, requestQueryString,
                    requestHeaders, requestCookies, requestHostAndPortDetail, sessionAttributes);
        }
        String user = null;
        if (session != null) {
            SessionAttributePath userAttributePath =
                    ServletInstrumentationProperties.userAttributePath();
            if (userAttributePath != null) {
                // capture user now, don't use a lazy supplier
                Object val = HttpSessions.getSessionAttribute(session, userAttributePath);
                user = val == null ? null : val.toString();
            }
        }
        String transactionType;
        boolean setWithCoreMaxPriority = false;
        String transactionTypeHeader = request.getHeader("X-Glowroot-Transaction-Type");
        if ("Synthetic".equals(transactionTypeHeader)) {
            // X-Glowroot-Transaction-Type header currently only accepts "Synthetic", in order to
            // prevent spamming of transaction types, which could cause some issues
            transactionType = transactionTypeHeader;
            setWithCoreMaxPriority = true;
        } else if (transactionTypeOverride != null) {
            transactionType = transactionTypeOverride;
        } else {
            transactionType = "Web";
        }
        Span span = context.startIncomingSpan(transactionType, requestUri, GETTER, request,
                messageSupplier, TIMER_NAME, AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
        span.propagateToResponse(response, SETTER);
        if (setWithCoreMaxPriority) {
            context.setTransactionType(transactionType, Priority.CORE_MAX);
        }
        context.setServletRequestInfo(messageSupplier);
        // X-Glowroot-Transaction-Name header is useful for automated tests which want to send a
        // more specific name for the transaction
        String transactionNameOverride = request.getHeader("X-Glowroot-Transaction-Name");
        if (transactionNameOverride != null) {
            context.setTransactionName(transactionNameOverride, Priority.CORE_MAX);
        }
        if (user != null) {
            context.setTransactionUser(user, Priority.CORE_INSTRUMENTATION);
        }
        return span;
    }

    private static void onReturnCommon(@Nullable Span span, @Nullable ServletResponse res,
            ResponseInvoker responseInvoker, OptionalThreadContext context) {

        if (span == null) {
            return;
        }
        if (!(res instanceof HttpServletResponse)) {
            // seems nothing sensible to do here other than ignore
            return;
        }
        ServletMessageSupplier messageSupplier =
                (ServletMessageSupplier) context.getServletRequestInfo();
        if (messageSupplier != null && responseInvoker.hasGetStatusMethod()) {
            messageSupplier.setResponseCode(responseInvoker.getStatus(res));
        }
        FastThreadLocal.Holder</*@Nullable*/ String> errorMessageHolder =
                SendError.getErrorMessageHolder();
        String errorMessage = errorMessageHolder.get();
        if (errorMessage != null) {
            context.setTransactionError(errorMessage);
            errorMessageHolder.set(null);
        }
        span.end();
    }

    private static void onThrowCommon(Throwable t, @Nullable Span span,
            @Nullable ServletResponse res, OptionalThreadContext context) {

        if (span == null) {
            return;
        }
        if (!(res instanceof HttpServletResponse)) {
            // seems nothing sensible to do here other than ignore
            return;
        }
        ServletMessageSupplier messageSupplier =
                (ServletMessageSupplier) context.getServletRequestInfo();
        if (messageSupplier != null) {
            // container will set this unless headers are already flushed
            messageSupplier.setResponseCode(500);
        }
        // ignoring potential sendError since this seems worse
        SendError.clearErrorMessage();
        span.endWithError(t);
    }

    private static boolean captureAsError(int statusCode) {

        return statusCode >= 500
                || ServletInstrumentationProperties.traceErrorOn4xxResponseCode()
                        && statusCode >= 400;
    }

    private static class HttpServletRequestGetter implements Getter<HttpServletRequest> {

        @Override
        public @Nullable String get(HttpServletRequest carrier, String key) {
            return carrier.getHeader(key);
        }
    }

    private static class HttpServletResponseSetter implements Setter<HttpServletResponse> {

        @Override
        public void put(HttpServletResponse carrier, String key, String value) {
            carrier.setHeader(key, value);
        }
    }
}
