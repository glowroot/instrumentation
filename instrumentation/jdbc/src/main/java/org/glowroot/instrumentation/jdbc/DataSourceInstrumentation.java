/*
 * Copyright 2011-2019 the original author or authors.
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
package org.glowroot.instrumentation.jdbc;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.Message;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.MonotonicNonNull;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.jdbc.boot.JdbcInstrumentationProperties;

// DataSource.getConnection() can be interesting in case the data source is improperly sized and is
// slow while expanding
public class DataSourceInstrumentation {

    private static final Logger logger = Logger.getLogger(DataSourceInstrumentation.class);

    private static final TimerName TIMER_NAME = Agent.getTimerName("jdbc get connection");

    private static final ConfigService configService = Agent.getConfigService("jdbc");

    private static final BooleanProperty captureGetConnection =
            configService.getBooleanProperty("captureGetConnection");
    private static final BooleanProperty captureConnectionLifecycleTraceEntries =
            configService.getBooleanProperty("captureConnectionLifecycleTraceEntries");
    private static final BooleanProperty captureTransactionLifecycleTraceEntries =
            configService.getBooleanProperty("captureTransactionLifecycleTraceEntries");

    private static final AtomicBoolean getAutoCommitExceptionLogged = new AtomicBoolean();

    @Advice.Pointcut(className = "javax.sql.DataSource",
                     methodName = "getConnection",
                     methodParameterTypes = {".."},
                     nestingGroup = "jdbc")
    public static class GetConnectionAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureGetConnection.value() || captureConnectionLifecycleTraceEntries.value();
        }

        @Advice.OnMethodBefore
        public static Object onBefore(ThreadContext context) {

            if (captureConnectionLifecycleTraceEntries.value()) {
                return context.startLocalSpan(new GetConnectionMessageSupplier(), TIMER_NAME);
            } else {
                return context.startTimer(TIMER_NAME);
            }
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Connection connection,
                @Bind.Enter Object spanOrTimer) {

            if (spanOrTimer instanceof Span) {
                onReturnSpan(connection, (Span) spanOrTimer);
            } else {
                ((Timer) spanOrTimer).stop();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Thrown Throwable t, @Bind.Enter Object spanOrTimer) {

            if (spanOrTimer instanceof Span) {
                ((Span) spanOrTimer).endWithError(t);
            } else {
                ((Timer) spanOrTimer).stop();
            }
        }
    }

    // split out to separate method so it doesn't affect inlining budget of common case
    private static void onReturnSpan(@Nullable Connection connection, Span span) {

        if (captureTransactionLifecycleTraceEntries.value() && connection != null) {
            GetConnectionMessageSupplier messageSupplier =
                    (GetConnectionMessageSupplier) span.getMessageSupplier();
            if (messageSupplier != null) {
                // messageSupplier can be null, e.g. if a span limit imposed by the agent was
                // exceeded
                String autoCommit;
                try {
                    autoCommit = Boolean.toString(connection.getAutoCommit());
                } catch (Exception e) {
                    if (getAutoCommitExceptionLogged.getAndSet(true)) {
                        logger.debug(e.getMessage(), e);
                    } else {
                        logger.warn(e.getMessage(), e);
                    }
                    // using toString() instead of getMessage() in order to capture exception
                    // class name
                    autoCommit = "<error occurred: " + e.toString() + ">";
                }
                messageSupplier.setAutoCommit(autoCommit);
            }
        }
        span.endWithLocationStackTrace(JdbcInstrumentationProperties.stackTraceThresholdNanos());
    }

    private static class GetConnectionMessageSupplier extends MessageSupplier {

        private volatile @MonotonicNonNull String autoCommit;

        @Override
        public Message get() {
            if (autoCommit == null) {
                return Message.create("jdbc get connection");
            } else {
                return Message.create("jdbc get connection (autocommit: {})", autoCommit);
            }
        }

        private void setAutoCommit(String autoCommit) {
            this.autoCommit = autoCommit;
        }
    }
}
