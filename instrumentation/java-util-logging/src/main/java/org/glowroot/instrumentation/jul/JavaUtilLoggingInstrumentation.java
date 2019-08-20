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
package org.glowroot.instrumentation.jul;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Message;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class JavaUtilLoggingInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("logging");

    private static final Formatter formatter = new DummyFormatter();

    @Advice.Pointcut(className = "java.util.logging.Logger",
                     methodName = "log",
                     methodParameterTypes = {"java.util.logging.LogRecord"},
                     nestingGroup = "logging")
    public static class LogAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) @Nullable LogRecord record) {
            return record != null
                    && LoggerInstrumentationProperties.captureLevel(record.getLevel());
        }

        // cannot use java.util.logging.Logger in the signature of this method because that triggers
        // java.util.logging.Logger to be loaded before weaving is put in place (from inside
        // org.glowroot.instrumentation.engine.weaving.AdviceBuilder)
        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(
                @Bind.Argument(0) LogRecord record,
                @Bind.This Object logger,
                ThreadContext context) {

            Level level = record.getLevel();
            if (!((Logger) logger).isLoggable(level)) {
                // Logger.log(LogRecord) was called directly
                return null;
            }
            return onBeforeCommon(record, level, context);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    @Advice.Pointcut(className = "org.jboss.logmanager.LoggerNode",
                     methodName = "publish",
                     methodParameterTypes = {"org.jboss.logmanager.ExtLogRecord"},
                     nestingGroup = "logging")
    public static class JBossLogAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) @Nullable LogRecord record) {
            return record != null
                    && LoggerInstrumentationProperties.captureLevel(record.getLevel());
        }

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(
                @Bind.Argument(0) LogRecord record,
                ThreadContext context) {

            return onBeforeCommon(record, record.getLevel(), context);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    private static Timer onBeforeCommon(LogRecord record, Level level, ThreadContext context) {
        // cannot check Logger.getFilter().isLoggable(LogRecord) because the Filter object
        // could be stateful and might alter its state (e.g.
        // com.sun.mail.util.logging.DurationFilter)
        String formattedMessage = nullToEmpty(formatter.formatMessage(record));
        int lvl = level.intValue();
        Throwable t = record.getThrown();
        if (LoggerInstrumentationProperties.markTraceAsError(lvl >= Level.SEVERE.intValue(),
                lvl >= Level.WARNING.intValue(), t != null)) {
            context.setTransactionError(formattedMessage, t);
        }
        context.captureLoggerSpan(
                new LogMessageSupplier(formattedMessage, level, record.getLoggerName()), t);
        return context.startTimer(TIMER_NAME);
    }

    private static String nullToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    // this is just needed for calling formatMessage in abstract super class
    private static class DummyFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            throw new UnsupportedOperationException();
        }
    }

    private static class LogMessageSupplier extends MessageSupplier {

        private final String messageText;
        private final Level level;
        private final @Nullable String loggerName;

        public LogMessageSupplier(String messageText, Level level, @Nullable String loggerName) {
            this.messageText = messageText;
            this.level = level;
            this.loggerName = loggerName;
        }

        @Override
        public Message get() {
            Map<String, Object> detail = new HashMap<String, Object>(2);
            if (level != null) {
                detail.put("Level", level.getName());
            }
            if (loggerName != null) {
                detail.put("Logger name", loggerName);
            }
            return Message.create(messageText, detail);
        }
    }
}
