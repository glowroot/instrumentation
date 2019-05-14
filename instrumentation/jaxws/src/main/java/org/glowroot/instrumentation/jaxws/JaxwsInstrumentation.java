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
package org.glowroot.instrumentation.jaxws;

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

public class JaxwsInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("jaxws service");

    private static final BooleanProperty useAltTransactionNaming =
            Agent.getConfigService("jaxws").getBooleanProperty("useAltTransactionNaming");

    @Advice.Pointcut(classAnnotation = "javax.jws.WebService",
                     methodAnnotation = "javax.jws.WebMethod",
                     methodParameterTypes = {".."})
    public static class ResourceAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                @Bind.MethodMeta ServiceMethodMeta serviceMethodMeta,
                ThreadContext context) {

            if (useAltTransactionNaming.value()) {
                context.setTransactionName(serviceMethodMeta.getAltTransactionName(),
                        Priority.CORE_INSTRUMENTATION);
            } else {
                String transactionName = getTransactionName(context.getServletRequestInfo(),
                        serviceMethodMeta.getMethodName());
                context.setTransactionName(transactionName, Priority.CORE_INSTRUMENTATION);
            }
            return context.startLocalSpan(MessageSupplier.create("jaxws service: {}.{}()",
                    serviceMethodMeta.getServiceClassName(), serviceMethodMeta.getMethodName()),
                    TIMER_NAME);
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

    private static String getTransactionName(@Nullable ServletRequestInfo servletRequestInfo,
            String methodName) {

        if (servletRequestInfo == null) {
            return '#' + methodName;
        }
        String method = servletRequestInfo.getMethod();
        String uri = servletRequestInfo.getUri();
        if (method.isEmpty()) {
            return uri + '#' + methodName;
        } else {
            return method + ' ' + uri + '#' + methodName;
        }
    }
}
