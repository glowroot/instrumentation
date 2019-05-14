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
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Shim;

public class Struts2xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("struts action");

    @Shim("com.opensymphony.xwork2.ActionProxy")
    public interface ActionProxy {

        Object getAction();

        String getMethod();
    }

    @Advice.Pointcut(className = "com.opensymphony.xwork2.ActionProxy",
                     methodName = "execute",
                     methodParameterTypes = {},
                     nestingGroup = "struts")
    public static class ActionProxyAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                @Bind.This ActionProxy actionProxy,
                ThreadContext context) {

            Class<?> actionClass = actionProxy.getAction().getClass();
            String actionMethod = actionProxy.getMethod();
            String methodName = actionMethod != null ? actionMethod : "execute";
            context.setTransactionName(actionClass.getSimpleName() + "#" + methodName,
                    Priority.CORE_INSTRUMENTATION);
            return context.startLocalSpan(MessageSupplier.create("struts action: {}.{}()",
                    actionClass.getName(), methodName), TIMER_NAME);
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
}
