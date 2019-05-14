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
package org.glowroot.instrumentation.servlet;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;

public class RequestDispatcherInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("servlet dispatch");

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("javax.servlet.RequestDispatcher")
    public abstract static class RequestDispatcherImpl implements RequestDispatcherMixin {

        private transient @Nullable String glowroot$path;

        @Override
        public @Nullable String glowroot$getPath() {
            return glowroot$path;
        }

        @Override
        public void glowroot$setPath(@Nullable String path) {
            glowroot$path = path;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface RequestDispatcherMixin {

        @Nullable
        String glowroot$getPath();

        void glowroot$setPath(@Nullable String path);
    }

    @Advice.Pointcut(className = "javax.servlet.ServletRequest|javax.servlet.ServletContext",
                     methodName = "getRequestDispatcher|getNamedDispatcher",
                     methodParameterTypes = {"java.lang.String"},
                     nestingGroup = "servlet-inner-call")
    public static class GetParameterAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable RequestDispatcherMixin requestDispatcher,
                @Bind.Argument(0) @Nullable String path) {

            if (requestDispatcher == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            requestDispatcher.glowroot$setPath(path);
        }
    }

    @Advice.Pointcut(className = "javax.servlet.RequestDispatcher",
                     methodName = "forward|include",
                     methodParameterTypes = {"javax.servlet.ServletRequest",
                             "javax.servlet.ServletResponse"},
                     nestingGroup = "servlet-dispatch")
    public static class DispatchAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                @Bind.This RequestDispatcherMixin requestDispatcher,
                ThreadContext context) {

            return context.startLocalSpan(MessageSupplier.create("servlet dispatch: {}",
                    requestDispatcher.glowroot$getPath()), TIMER_NAME);
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
