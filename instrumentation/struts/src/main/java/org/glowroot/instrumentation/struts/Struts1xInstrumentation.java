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
package org.glowroot.instrumentation.struts;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.ThreadContext.ServletRequestInfo;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class Struts1xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("struts action");

    @Advice.Pointcut(className = "org.apache.struts.action.Action",
                     methodName = "execute",
                     methodParameterTypes = {"org.apache.struts.action.ActionMapping",
                             "org.apache.struts.action.ActionForm", ".."},
                     nestingGroup = "struts")
    public static class ActionAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(@Bind.This Object action, ThreadContext context) {

            Class<?> actionClass = action.getClass();
            String httpMethod = null;
            ServletRequestInfo servletRequestInfo = context.getServletRequestInfo();
            if (servletRequestInfo != null) {
                httpMethod = servletRequestInfo.getMethod();
            }
            context.setTransactionName(getTransactionName(httpMethod, actionClass),
                    Priority.CORE_INSTRUMENTATION);
            return context.startLocalSpan(
                    MessageSupplier.create("struts action: {}.execute()", actionClass.getName()),
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

    private static String getTransactionName(@Nullable String httpMethod, Class<?> actionClass) {
        StringBuilder sb = new StringBuilder();
        if (httpMethod != null && !httpMethod.isEmpty()) {
            sb.append(httpMethod);
            sb.append(' ');
        }
        sb.append(actionClass.getSimpleName());
        sb.append("#execute");
        return sb.toString();
    }
}
