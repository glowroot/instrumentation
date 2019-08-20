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
package org.glowroot.instrumentation.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.logback.boot.LogMessageSupplier;
import org.glowroot.instrumentation.logback.boot.LogbackLevel;
import org.glowroot.instrumentation.logback.boot.LoggerInstrumentationProperties;
import org.glowroot.instrumentation.logback.boot.LoggingEventInvoker;

public class LogbackInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("logging");

    @Advice.Pointcut(className = "ch.qos.logback.classic.Logger",
                     methodName = "callAppenders",
                     methodParameterTypes = {"ch.qos.logback.classic.spi.ILoggingEvent"},
                     nestingGroup = "logging")
    public static class CallAppendersAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) @Nullable ILoggingEvent loggingEvent) {
            if (loggingEvent == null) {
                return false;
            }
            Level level = loggingEvent.getLevel();
            return level != null && LoggerInstrumentationProperties.captureLevel(level.toInt());
        }

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(
                @Bind.Argument(0) ILoggingEvent loggingEvent,
                ThreadContext context) {

            String formattedMessage = nullToEmpty(loggingEvent.getFormattedMessage());
            Level level = loggingEvent.getLevel();
            int lvl = level == null ? 0 : level.toInt();
            Object throwableProxy = loggingEvent.getThrowableProxy();
            Throwable t = null;
            if (throwableProxy instanceof ThrowableProxy) {
                // there is only one other subclass of ch.qos.logback.classic.spi.IThrowableProxy
                // and it is only used for logging exceptions over the wire
                t = ((ThrowableProxy) throwableProxy).getThrowable();
            }
            if (LoggerInstrumentationProperties.markTraceAsError(lvl >= LogbackLevel.ERROR,
                    lvl >= LogbackLevel.WARN, t != null)) {
                context.setTransactionError(formattedMessage, t);
            }
            String levelStr = level == null ? null : level.toString();
            context.captureLoggerSpan(new LogMessageSupplier(formattedMessage, levelStr,
                    loggingEvent.getLoggerName()), t);
            return context.startTimer(TIMER_NAME);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    // this is for logback prior to 0.9.16
    @Advice.Pointcut(className = "ch.qos.logback.classic.Logger",
                     methodName = "callAppenders",
                     methodParameterTypes = {"ch.qos.logback.classic.spi.LoggingEvent"},
                     nestingGroup = "logging")
    public static class CallAppenders0xAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) @Nullable LoggingEvent loggingEvent) {
            if (loggingEvent == null) {
                return false;
            }
            Level level = loggingEvent.getLevel();
            return level != null && LoggerInstrumentationProperties.captureLevel(level.toInt());
        }

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(
                @Bind.This Object logger,
                @Bind.Argument(0) LoggingEvent loggingEvent,
                @Bind.ClassMeta LoggingEventInvoker invoker,
                ThreadContext context) {

            String formattedMessage = invoker.getFormattedMessage(loggingEvent);
            Level level = loggingEvent.getLevel();
            int lvl = level == null ? 0 : level.toInt();
            Throwable t = invoker.getThrowable(loggingEvent);
            if (LoggerInstrumentationProperties.markTraceAsError(lvl >= LogbackLevel.ERROR,
                    lvl >= LogbackLevel.WARN, t != null)) {
                context.setTransactionError(formattedMessage, t);
            }
            String levelStr = level == null ? null : level.toString();
            context.captureLoggerSpan(new LogMessageSupplier(formattedMessage, levelStr,
                    invoker.getLoggerName(logger)), t);
            return context.startTimer(TIMER_NAME);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    private static String nullToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }
}
