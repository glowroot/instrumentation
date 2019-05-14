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
package org.glowroot.instrumentation.jaxrs;

import java.util.List;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.ThreadContext.ServletRequestInfo;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class JaxrsInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("jaxrs resource");

    private static final BooleanProperty useAltTransactionNaming =
            Agent.getConfigService("jaxrs").getBooleanProperty("useAltTransactionNaming");

    @Advice.Pointcut(
                     methodAnnotation = "javax.ws.rs.Path|javax.ws.rs.DELETE|javax.ws.rs.GET"
                             + "|javax.ws.rs.HEAD|javax.ws.rs.OPTIONS|javax.ws.rs.POST|javax.ws.rs.PUT",
                     methodParameterTypes = {".."},
                     nestingGroup = "jaxrs")
    public static class ResourceAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.MethodMeta ResourceMethodMeta resourceMethodMeta,
                ThreadContext context) {

            if (resourceMethodMeta.hasHttpMethodAnnotation()) {
                if (useAltTransactionNaming.value()) {
                    context.setTransactionName(resourceMethodMeta.getAltTransactionName(),
                            Priority.CORE_INSTRUMENTATION);
                } else {
                    ServletRequestInfo servletRequestInfo = context.getServletRequestInfo();
                    if (servletRequestInfo != null) {
                        List<String> jaxRsParts = servletRequestInfo.getJaxRsParts();
                        String path = resourceMethodMeta.getPath();
                        if (!jaxRsParts.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (String jaxRsPart : jaxRsParts) {
                                sb.append(jaxRsPart);
                            }
                            sb.append(path);
                            path = sb.toString();
                        }
                        String transactionName = getTransactionName(path, servletRequestInfo);
                        context.setTransactionName(transactionName, Priority.CORE_INSTRUMENTATION);
                    }
                }
                return context.startLocalSpan(MessageSupplier.create("jaxrs resource: {}.{}()",
                        resourceMethodMeta.getResourceClassName(),
                        resourceMethodMeta.getMethodName()), TIMER_NAME);
            } else {
                if (!useAltTransactionNaming.value()) {
                    ServletRequestInfo servletRequestInfo = context.getServletRequestInfo();
                    if (servletRequestInfo != null) {
                        servletRequestInfo.addJaxRsPart(resourceMethodMeta.getPath());
                    }
                }
                return null;
            }
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    private static String getTransactionName(String path,
            @Nullable ServletRequestInfo servletRequestInfo) {
        if (servletRequestInfo == null) {
            return path;
        }
        String method = servletRequestInfo.getMethod();
        String servletPath = getServletPath(servletRequestInfo);
        if (method.isEmpty()) {
            return servletPath + path;
        } else {
            return method + " " + servletPath + path;
        }
    }

    private static String getServletPath(ServletRequestInfo servletRequestInfo) {
        if (servletRequestInfo.getPathInfo() == null) {
            // pathInfo is null when the servlet is mapped to "/" (not "/*") and therefore it is
            // replacing the default servlet and getServletPath() returns the full path
            return servletRequestInfo.getContextPath();
        } else {
            return servletRequestInfo.getContextPath() + servletRequestInfo.getServletPath();
        }
    }
}
