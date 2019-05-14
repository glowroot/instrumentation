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
package org.glowroot.instrumentation.play;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class Play1xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http request");

    @Advice.Pointcut(className = "play.mvc.ActionInvoker",
                     methodName = "invoke",
                     methodParameterTypes = {"play.mvc.Http$Request", "play.mvc.Http$Response"})
    public static class ActionInvokerAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(OptionalThreadContext context) {

            return context.startLocalSpan(MessageSupplier.create("play action invoker"),
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Enter Span span,
                @Bind.Argument(0) Object request,
                @Bind.ClassMeta PlayInvoker invoker,
                ThreadContext context) {

            String action = invoker.getAction(request);
            if (action != null) {
                int index = action.lastIndexOf('.');
                if (index != -1) {
                    action = action.substring(0, index) + '#' + action.substring(index + 1);
                }
                context.setTransactionName(action, Priority.CORE_INSTRUMENTATION);
            }
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
