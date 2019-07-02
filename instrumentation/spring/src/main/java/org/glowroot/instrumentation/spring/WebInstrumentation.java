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

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.ThreadContext.ServletRequestInfo;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.util.FastThreadLocal;
import org.glowroot.instrumentation.api.util.FastThreadLocal.Holder;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.api.weaving.Shim;

public class WebInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("spring controller");

    private static final TimerName WEBSOCKET_TIMER_NAME =
            Agent.getTimerName("spring websocket controller");

    private static final BooleanProperty useAltTransactionNaming =
            Agent.getConfigService("spring").getBooleanProperty("useAltTransactionNaming");

    private static final ConcurrentMap<String, String> normalizedPatterns =
            new ConcurrentHashMap<String, String>();

    private static final FastThreadLocal</*@Nullable*/ URI> webSocketUri =
            new FastThreadLocal</*@Nullable*/ URI>();

    private static final FastThreadLocal</*@Nullable*/ String> webSocketTransactionName =
            new FastThreadLocal</*@Nullable*/ String>();

    private static final Getter<Object> GETTER = new NopGetter();

    private static final Object REQUEST = new Object();

    @Shim("org.springframework.web.servlet.mvc.method.RequestMappingInfo")
    public interface RequestMappingInfo {

        @Shim("org.springframework.web.servlet.mvc.condition.PatternsRequestCondition"
                + " getPatternsCondition()")
        @Nullable
        PatternsRequestCondition glowroot$getPatternsCondition();
    }

    @Shim("org.springframework.web.servlet.mvc.condition.PatternsRequestCondition")
    public interface PatternsRequestCondition {

        @Nullable
        Set<String> getPatterns();
    }

    @Shim("org.springframework.web.socket.WebSocketSession")
    public interface WebSocketSession {

        @Nullable
        URI getUri();
    }

    @Shim("org.springframework.messaging.handler.invocation.AbstractMethodMessageHandler")
    public interface AbstractMethodMessageHandler {

        @Shim("java.lang.String getDestination(org.springframework.messaging.Message)")
        @Nullable
        String glowroot$getDestination(Object message);
    }

    @Shim("org.springframework.messaging.simp.SimpMessageMappingInfo")
    public interface SimpMessageMappingInfo {

        @Shim("org.springframework.messaging.handler.DestinationPatternsMessageCondition"
                + " getDestinationConditions()")
        @Nullable
        DestinationPatternsMessageCondition glowroot$getDestinationConditions();
    }

    @Shim("org.springframework.messaging.handler.DestinationPatternsMessageCondition")
    public interface DestinationPatternsMessageCondition {

        @Nullable
        Set<String> getPatterns();
    }

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"org.springframework.messaging.support.ExecutorSubscribableChannel$SendTask",
            "org.springframework.messaging.support.ExecutorSubscribableChannel$1"})
    public static class WithWebSocketUriImpl implements WithWebSocketUriMixin {

        private transient @Nullable URI glowroot$webSocketUri;

        @Override
        public @Nullable URI glowroot$getWebSocketUri() {
            return glowroot$webSocketUri;
        }

        @Override
        public void glowroot$setWebSocketUri(@Nullable URI uri) {
            this.glowroot$webSocketUri = uri;
        }
    }

    // the field and method names are verbose since they will be mixed in to existing classes
    public interface WithWebSocketUriMixin {

        @Nullable
        URI glowroot$getWebSocketUri();

        void glowroot$setWebSocketUri(@Nullable URI uri);
    }

    @Advice.Pointcut(className = "org.springframework.web.servlet.handler.AbstractHandlerMethodMapping",
                     methodName = "handleMatch",
                     methodParameterTypes = {"java.lang.Object",
                             "java.lang.String", "javax.servlet.http.HttpServletRequest"})
    public static class HandlerMethodMappingAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) @Nullable Object mapping,
                ThreadContext context) {

            if (useAltTransactionNaming.value()) {
                return;
            }
            if (!(mapping instanceof RequestMappingInfo)) {
                return;
            }
            PatternsRequestCondition patternCondition =
                    ((RequestMappingInfo) mapping).glowroot$getPatternsCondition();
            if (patternCondition == null) {
                return;
            }
            Set<String> patterns = patternCondition.getPatterns();
            if (patterns == null || patterns.isEmpty()) {
                return;
            }
            String prefix = getServletPath(context.getServletRequestInfo());
            String pattern = patterns.iterator().next();
            if (pattern == null || pattern.isEmpty()) {
                context.setTransactionName(prefix, Priority.CORE_INSTRUMENTATION);
                return;
            }
            String normalizedPattern = normalizedPatterns.get(pattern);
            if (normalizedPattern == null) {
                normalizedPattern = pattern.replaceAll("\\{[^}]*\\}", "*");
                normalizedPatterns.put(pattern, normalizedPattern);
            }
            context.setTransactionName(prefix + normalizedPattern, Priority.CORE_INSTRUMENTATION);
        }
    }

    @Advice.Pointcut(className = "org.springframework.web.servlet.handler.AbstractUrlHandlerMapping",
                     methodName = "exposePathWithinMapping",
                     methodParameterTypes = {"java.lang.String",
                             "java.lang.String", "javax.servlet.http.HttpServletRequest"})
    public static class UrlHandlerMappingAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) @Nullable String bestMatchingPattern,
                ThreadContext context) {

            if (useAltTransactionNaming.value()) {
                return;
            }
            String prefix = getServletPath(context.getServletRequestInfo());
            if (bestMatchingPattern == null || bestMatchingPattern.isEmpty()) {
                context.setTransactionName(prefix, Priority.CORE_INSTRUMENTATION);
                return;
            }
            String normalizedPattern = normalizedPatterns.get(bestMatchingPattern);
            if (normalizedPattern == null) {
                normalizedPattern = bestMatchingPattern.replaceAll("\\{[^}]*\\}", "*");
                normalizedPatterns.put(bestMatchingPattern, normalizedPattern);
            }
            context.setTransactionName(prefix + normalizedPattern, Priority.CORE_INSTRUMENTATION);
        }
    }

    @Advice.Pointcut(
                     classAnnotation = "org.springframework.stereotype.Controller"
                             + "|org.springframework.web.bind.annotation.RestController",
                     methodAnnotation = "/org.springframework.web.bind.annotation"
                             + ".(Request|Delete|Get|Patch|Post|Put)Mapping/",
                     methodParameterTypes = {".."})
    public static class ControllerAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                @Bind.MethodMeta ControllerMethodMeta controllerMethodMeta,
                ThreadContext context) {

            if (useAltTransactionNaming.value()) {
                ServletRequestInfo servletRequestInfo = context.getServletRequestInfo();
                if (servletRequestInfo == null) {
                    context.setTransactionName(controllerMethodMeta.getAltTransactionName(),
                            Priority.CORE_INSTRUMENTATION);
                } else {
                    String httpMethod = servletRequestInfo.getMethod();
                    if (httpMethod == null || httpMethod.isEmpty()) {
                        context.setTransactionName(controllerMethodMeta.getAltTransactionName(),
                                Priority.CORE_INSTRUMENTATION);
                    } else {
                        context.setTransactionName(
                                httpMethod + " " + controllerMethodMeta.getAltTransactionName(),
                                Priority.CORE_INSTRUMENTATION);
                    }
                }
            }
            return context.startLocalSpan(MessageSupplier.create("spring controller: {}.{}()",
                    controllerMethodMeta.getControllerClassName(),
                    controllerMethodMeta.getMethodName()), TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.end();
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "org.springframework.web.socket.WebSocketHandler",
                     methodName = "handleMessage",
                     methodParameterTypes = {"org.springframework.web.socket.WebSocketSession",
                             ".."})
    public static class HandleMessageAdvice {

        @Advice.OnMethodBefore
        public static Holder</*@Nullable*/ URI> onBefore(
                @Bind.Argument(0) WebSocketSession session) {

            Holder</*@Nullable*/ URI> holder = webSocketUri.getHolder();
            holder.set(session.getUri());
            return holder;
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Holder</*@Nullable*/ URI> holder) {

            holder.set(null);
        }
    }

    @Advice.Pointcut(className = "org.springframework.messaging.support.ExecutorSubscribableChannel$*",
                     superTypeRestriction = "java.lang.Runnable",
                     methodName = "<init>",
                     methodParameterTypes = {".."})
    public static class SendTaskInitAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This WithWebSocketUriMixin withWebSocketUri) {

            withWebSocketUri.glowroot$setWebSocketUri(webSocketUri.get());
        }
    }

    @Advice.Pointcut(className = "org.springframework.messaging.support.ExecutorSubscribableChannel$*",
                     superTypeRestriction = "java.lang.Runnable",
                     methodName = "run",
                     methodParameterTypes = {})
    public static class SendTaskRunAdvice {

        @Advice.OnMethodBefore
        public static Holder</*@Nullable*/ URI> onBefore(
                @Bind.This WithWebSocketUriMixin withWebSocketUri) {

            Holder</*@Nullable*/ URI> holder = webSocketUri.getHolder();
            holder.set(withWebSocketUri.glowroot$getWebSocketUri());
            return holder;
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Holder</*@Nullable*/ URI> holder) {

            holder.set(null);
        }
    }

    @Advice.Pointcut(className = "org.springframework.messaging.simp.annotation.support"
            + ".SimpAnnotationMethodMessageHandler",
                     methodName = "handleMatch",
                     methodParameterTypes = {"java.lang.Object",
                             "org.springframework.messaging.handler.HandlerMethod",
                             "java.lang.String",
                             "org.springframework.messaging.Message"})
    public static class WebSocketMappingAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Holder</*@Nullable*/ String> onBefore(
                @Bind.Argument(0) @Nullable Object mapping,
                @Bind.Argument(2) @Nullable String lookupDestination,
                @Bind.Argument(3) @Nullable Object message,
                @Bind.This AbstractMethodMessageHandler messageHandler) {

            if (useAltTransactionNaming.value()) {
                return null;
            }
            if (!(mapping instanceof SimpMessageMappingInfo)) {
                return null;
            }
            DestinationPatternsMessageCondition patternCondition =
                    ((SimpMessageMappingInfo) mapping).glowroot$getDestinationConditions();
            if (patternCondition == null) {
                return null;
            }
            Set<String> patterns = patternCondition.getPatterns();
            if (patterns == null || patterns.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            URI uri = webSocketUri.get();
            if (uri != null) {
                sb.append(uri);
            }
            if (lookupDestination != null && message != null) {
                String destination = messageHandler.glowroot$getDestination(message);
                if (destination != null) {
                    sb.append(destination.substring(0,
                            destination.length() - lookupDestination.length()));
                }
            }
            String pattern = patterns.iterator().next();
            Holder</*@Nullable*/ String> holder = webSocketTransactionName.getHolder();
            if (pattern == null || pattern.isEmpty()) {
                holder.set(sb.toString());
                return holder;
            }
            String normalizedPattern = normalizedPatterns.get(pattern);
            if (normalizedPattern == null) {
                normalizedPattern = pattern.replaceAll("\\{[^}]*\\}", "*");
                normalizedPatterns.put(pattern, normalizedPattern);
            }
            sb.append(normalizedPattern);
            holder.set(sb.toString());
            return holder;
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Holder</*@Nullable*/ String> holder) {

            if (holder != null) {
                holder.set(null);
            }
        }
    }

    @Advice.Pointcut(classAnnotation = "org.springframework.stereotype.Controller",
                     methodAnnotation = "org.springframework.messaging.handler.annotation.MessageMapping",
                     methodParameterTypes = {".."})
    public static class MessageMappingAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                OptionalThreadContext context,
                @Bind.MethodMeta ControllerMethodMeta controllerMethodMeta) {

            String transactionName;
            if (useAltTransactionNaming.value()) {
                transactionName = controllerMethodMeta.getAltTransactionName();
            } else {
                transactionName = webSocketTransactionName.get();
                if (transactionName == null) {
                    transactionName = "<unknown>"; // ???
                }
            }
            return context.startIncomingSpan("Web", transactionName, GETTER, REQUEST,
                    MessageSupplier.create("spring websocket controller: {}.{}()",
                            controllerMethodMeta.getControllerClassName(),
                            controllerMethodMeta.getMethodName()),
                    WEBSOCKET_TIMER_NAME, AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.end();
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    private static String getServletPath(@Nullable ServletRequestInfo servletRequestInfo) {

        if (servletRequestInfo == null) {
            return "";
        }
        String httpMethod = servletRequestInfo.getMethod();
        StringBuilder sb = new StringBuilder();
        if (httpMethod != null && !httpMethod.isEmpty()) {
            sb.append(httpMethod);
            sb.append(' ');
        }
        sb.append(servletRequestInfo.getContextPath());
        if (servletRequestInfo.getPathInfo() != null) {
            // pathInfo is null when the servlet is mapped to "/" (not "/*") and therefore it is
            // replacing the default servlet and getServletPath() returns the full path
            sb.append(servletRequestInfo.getServletPath());
        }
        return sb.toString();
    }

    private static class NopGetter implements Getter<Object> {

        @Override
        public @Nullable String get(Object carrier, String key) {
            return null;
        }
    }
}
