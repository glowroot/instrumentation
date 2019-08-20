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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.log4j.boot.Log4j2xLevel;
import org.glowroot.instrumentation.log4j.boot.LogMessageSupplier;
import org.glowroot.instrumentation.log4j.boot.LoggerInstrumentationProperties;

public class Log4j2xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("logging");

    @Advice.Pointcut(className = "org.apache.logging.log4j.spi.ExtendedLogger",
                     methodName = "logMessage",
                     methodParameterTypes = {"java.lang.String", "org.apache.logging.log4j.Level",
                             "org.apache.logging.log4j.Marker",
                             "org.apache.logging.log4j.message.Message",
                             "java.lang.Throwable"},
                     nestingGroup = "logging")
    public static class CallAppendersAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(1) @Nullable Level level) {
            return level != null
                    && LoggerInstrumentationProperties.captureLog4j2xLevel(level.intLevel());
        }

        @Advice.OnMethodBefore
        public static Timer onBefore(
                @Bind.This Logger logger,
                @Bind.Argument(1) @Nullable Level level,
                @Bind.Argument(3) @Nullable Message message,
                @Bind.Argument(4) @Nullable Throwable t,
                ThreadContext context) {

            String formattedMessage =
                    message == null ? "" : nullToEmpty(message.getFormattedMessage());
            int lvl = level == null ? 0 : level.intLevel();
            if (LoggerInstrumentationProperties.markTraceAsError(lvl <= Log4j2xLevel.ERROR,
                    lvl <= Log4j2xLevel.WARN, t != null)) {
                context.setTransactionError(formattedMessage, t);
            }
            context.captureLoggerSpan(new LogMessageSupplier(formattedMessage,
                    level == null ? null : level.toString(), logger.getName()), t);
            return context.startTimer(TIMER_NAME);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {

            timer.stop();
        }
    }

    private static String nullToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }
}
