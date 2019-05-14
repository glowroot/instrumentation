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
package org.glowroot.instrumentation.jsp;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class JspInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("jsp render");

    @Advice.Pointcut(className = "javax.servlet.jsp.HttpJspPage",
                     methodName = "_jspService",
                     methodParameterTypes = {"javax.servlet.http.HttpServletRequest",
                             "javax.servlet.http.HttpServletResponse"},
                     nestingGroup = "jsp")
    public static class HttpJspPageAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(@Bind.This Object httpJspPage, ThreadContext context) {

            // get filename from classname
            String filename = HttpJspPages.getFilename(httpJspPage.getClass());
            return context.startLocalSpan(MessageSupplier.create("jsp render: {}", filename),
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
}
