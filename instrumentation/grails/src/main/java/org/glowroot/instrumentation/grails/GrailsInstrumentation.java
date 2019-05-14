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
package org.glowroot.instrumentation.grails;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Shim;

public class GrailsInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("grails controller");

    @Shim("grails.core.GrailsControllerClass")
    public interface GrailsControllerClass {

        String getDefaultAction();

        String getName();

        String getFullName();
    }

    @Advice.Pointcut(className = "grails.core.GrailsControllerClass",
                     methodName = "invoke",
                     methodParameterTypes = {"java.lang.Object", "java.lang.String"})
    public static class ControllerAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                @Bind.This GrailsControllerClass grailsController,
                @Bind.Argument(1) String action,
                ThreadContext context) {

            String actionName = action == null ? grailsController.getDefaultAction() : action;
            context.setTransactionName(grailsController.getName() + "#" + actionName,
                    Priority.CORE_INSTRUMENTATION);
            return context.startLocalSpan(MessageSupplier.create("grails controller: {}.{}()",
                    grailsController.getFullName(), actionName), TIMER_NAME);
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
