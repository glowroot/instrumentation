/*
 * Copyright 2014-2019 the original author or authors.
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
package org.glowroot.instrumentation.log4j;

import org.apache.log4j.Category;
import org.apache.log4j.Priority;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.log4j.boot.LogMessageSupplier;
import org.glowroot.instrumentation.log4j.boot.LoggerInstrumentationProperties;

public class Log4j1xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("logging");

    // constants from org.apache.log4j.Priority
    private static final int ERROR_INT = 40000;
    private static final int WARN_INT = 30000;

    @Advice.Pointcut(className = "org.apache.log4j.Category",
                     methodName = "forcedLog",
                     methodParameterTypes = {"java.lang.String", "org.apache.log4j.Priority",
                             "java.lang.Object", "java.lang.Throwable"},
                     nestingGroup = "logging")
    public static class ForcedLogAdvice {

        @Advice.OnMethodBefore
        public static Timer onBefore(
                @Bind.This Category logger,
                @Bind.Argument(1) @Nullable Priority level,
                @Bind.Argument(2) @Nullable Object message,
                @Bind.Argument(3) @Nullable Throwable t,
                ThreadContext context) {

            String messageText = String.valueOf(message);
            int lvl = level == null ? 0 : level.toInt();
            if (LoggerInstrumentationProperties.markTraceAsError(lvl >= ERROR_INT, lvl >= WARN_INT,
                    t != null)) {
                context.setTransactionError(messageText, t);
            }
            context.captureLoggerSpan(new LogMessageSupplier(messageText,
                    level == null ? null : level.toString(), logger.getName()), t);
            return context.startTimer(TIMER_NAME);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {

            timer.stop();
        }
    }
}
