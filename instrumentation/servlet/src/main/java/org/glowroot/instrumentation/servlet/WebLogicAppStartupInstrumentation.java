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
package org.glowroot.instrumentation.servlet;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Shim;

public class WebLogicAppStartupInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("startup");

    @Shim("weblogic.servlet.internal.WebAppServletContext")
    public interface WebAppServletContextShim {

        @Nullable
        String getContextPath();
    }

    @Advice.Pointcut(className = "weblogic.servlet.internal.WebAppServletContext",
                     methodName = "start",
                     methodParameterTypes = {},
                     nestingGroup = "servlet-startup")
    public static class StartAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                OptionalThreadContext context,
                @Bind.This WebAppServletContextShim webAppServletContext) {

            String path = webAppServletContext.getContextPath();
            return ContainerStartup.onBeforeCommon(context, path, TIMER_NAME);
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
