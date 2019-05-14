/*
 * Copyright 2015-2019 the original author or authors.
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

// this covers Tomcat, TomEE, Glassfish, JBoss EAP
public class CatalinaAppStartupInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("startup");

    @Shim("org.apache.catalina.core.StandardContext")
    public interface StandardContextShim {

        @Nullable
        String getPath();
    }

    // startInternal is needed for Tomcat 7+ which moved the start() method up into a new super
    // class, org.apache.catalina.util.LifecycleBase, but this new start() method delegates to
    // abstract method startInternal() which does all of the real work
    @Advice.Pointcut(className = "org.apache.catalina.core.StandardContext",
                     methodName = "start|startInternal",
                     methodParameterTypes = {},
                     nestingGroup = "servlet-startup")
    public static class StartAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                OptionalThreadContext context,
                @Bind.This StandardContextShim standardContext) {

            String path = standardContext.getPath();
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
