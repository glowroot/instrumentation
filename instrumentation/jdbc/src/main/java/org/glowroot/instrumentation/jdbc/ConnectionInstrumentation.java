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
import java.sql.DatabaseMetaData;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.jdbc.StatementInstrumentation.HasStatementMirrorMixin;
import org.glowroot.instrumentation.jdbc.boot.JdbcInstrumentationProperties;
import org.glowroot.instrumentation.jdbc.boot.PreparedStatementMirror;
import org.glowroot.instrumentation.jdbc.boot.StatementMirror;

public class ConnectionInstrumentation {

    private static final Logger logger = Logger.getLogger(ConnectionInstrumentation.class);

    private static final TimerName PREPARE_TIMER_NAME = Agent.getTimerName("jdbc prepare");

    private static final TimerName COMMIT_TIMER_NAME = Agent.getTimerName("jdbc commit");

    private static final TimerName ROLLBACK_TIMER_NAME = Agent.getTimerName("jdbc rollback");

    private static final TimerName CONNECTION_CLOSE_TIMER_NAME =
            Agent.getTimerName("jdbc connection close");

    private static final TimerName AUTOCOMMIT_TIMER_NAME =
            Agent.getTimerName("jdbc set autocommit");

    private static final ConfigService configService = Agent.getConfigService("jdbc");

    private static final BooleanProperty capturePreparedStatementCreation =
            configService.getBooleanProperty("capturePreparedStatementCreation");
    private static final BooleanProperty captureConnectionClose =
            configService.getBooleanProperty("captureConnectionClose");
    private static final BooleanProperty captureConnectionLifecycleTraceEntries =
            configService.getBooleanProperty("captureConnectionLifecycleTraceEntries");
    private static final BooleanProperty captureTransactionLifecycleTraceEntries =
            configService.getBooleanProperty("captureTransactionLifecycleTraceEntries");

    private static final AtomicBoolean destExceptionLogged = new AtomicBoolean();

    // ===================== Mixin =====================

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("java.sql.Connection")
    public static class ConnectionImpl implements ConnectionMixin {

        // does not need to be volatile, app/framework must provide visibility of Connections if
        // used across threads and this can piggyback
        private transient @Nullable String glowroot$dest;

        @Override
        public @Nullable String glowroot$getDest() {
            return glowroot$dest;
        }

        @Override
        public void glowroot$setDest(@Nullable String dest) {
            glowroot$dest = dest;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface ConnectionMixin {

        @Nullable
        String glowroot$getDest();

        void glowroot$setDest(@Nullable String dest);
    }

    // ===================== Statement Preparation =====================

    // capture the sql used to create the PreparedStatement
    @Advice.Pointcut(className = "java.sql.Connection",
                     methodName = "prepare*",
                     methodParameterTypes = {"java.lang.String", ".."},
                     nestingGroup = "jdbc")
    public static class PrepareAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(ThreadContext context) {

            if (capturePreparedStatementCreation.value()) {
                return context.startTimer(PREPARE_TIMER_NAME);
            } else {
                return null;
            }
        }

        @Advice.OnMethodReturn
        public static <T extends Connection & ConnectionMixin> void onReturn(
                @Bind.Return @Nullable HasStatementMirrorMixin preparedStatement,
                @Bind.This T connection,
                @Bind.Argument(0) @Nullable String sql) {

            if (preparedStatement == null || sql == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            preparedStatement.glowroot$setStatementMirror(
                    new PreparedStatementMirror(getDest(connection), sql));
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.Connection",
                     methodName = "createStatement",
                     methodParameterTypes = {".."})
    public static class CreateStatementAdvice {

        @Advice.OnMethodReturn
        public static <T extends Connection & ConnectionMixin> void onReturn(
                @Bind.Return @Nullable HasStatementMirrorMixin statement,
                @Bind.This T connection) {

            if (statement == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            statement.glowroot$setStatementMirror(new StatementMirror(getDest(connection)));
        }
    }

    @Advice.Pointcut(className = "java.sql.Connection",
                     methodName = "commit",
                     methodParameterTypes = {},
                     nestingGroup = "jdbc")
    public static class CommitAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(ThreadContext context) {

            return context.startLocalSpan(MessageSupplier.create("jdbc commit"), COMMIT_TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.endWithLocationStackTrace(
                    JdbcInstrumentationProperties.stackTraceThresholdNanos());
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "java.sql.Connection",
                     methodName = "rollback",
                     methodParameterTypes = {},
                     nestingGroup = "jdbc")
    public static class RollbackAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(ThreadContext context) {

            return context.startLocalSpan(MessageSupplier.create("jdbc rollback"),
                    ROLLBACK_TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.endWithLocationStackTrace(
                    JdbcInstrumentationProperties.stackTraceThresholdNanos());
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "java.sql.Connection",
                     methodName = "close",
                     methodParameterTypes = {},
                     nestingGroup = "jdbc")
    public static class CloseAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureConnectionClose.value()
                    || captureConnectionLifecycleTraceEntries.value();
        }

        @Advice.OnMethodBefore
        public static Object onBefore(ThreadContext context) {

            if (captureConnectionLifecycleTraceEntries.value()) {
                return context.startLocalSpan(MessageSupplier.create("jdbc connection close"),
                        CONNECTION_CLOSE_TIMER_NAME);
            } else {
                return context.startTimer(CONNECTION_CLOSE_TIMER_NAME);
            }
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Object spanOrTimer) {

            if (spanOrTimer instanceof Span) {
                ((Span) spanOrTimer).endWithLocationStackTrace(
                        JdbcInstrumentationProperties.stackTraceThresholdNanos());
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

    @Advice.Pointcut(className = "java.sql.Connection",
                     methodName = "setAutoCommit",
                     methodParameterTypes = {"boolean"},
                     nestingGroup = "jdbc")
    public static class SetAutoCommitAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureTransactionLifecycleTraceEntries.value();
        }

        @Advice.OnMethodBefore
        public static Span onBefore(
                @Bind.Argument(0) boolean autoCommit,
                ThreadContext context) {

            return context.startLocalSpan(
                    MessageSupplier.create("jdbc set autocommit: {}",
                            Boolean.toString(autoCommit)),
                    AUTOCOMMIT_TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.endWithLocationStackTrace(
                    JdbcInstrumentationProperties.stackTraceThresholdNanos());
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    static String getDest(Connection connection) {
        ConnectionMixin mixin = (ConnectionMixin) connection;
        String dest = mixin.glowroot$getDest();
        if (dest == null) {
            dest = buildDest(connection);
            mixin.glowroot$setDest(dest);
        }
        return dest;
    }

    private static String buildDest(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            if (metaData == null) {
                return "jdbc:";
            }
            String url = metaData.getURL();
            if (url == null) {
                return "jdbc:";
            }
            return buildDest(url);
        } catch (Exception e) {
            // getMetaData and getURL can throw SQLException
            if (destExceptionLogged.getAndSet(true)) {
                logger.debug(e.getMessage(), e);
            } else {
                logger.warn(e.getMessage(), e);
            }
            return "jdbc:";
        }
    }

    private static String buildDest(String url) {
        int index = url.indexOf(';');
        if (index == -1) {
            // e.g. PostgreSQL jdbc url
            index = url.indexOf('?');
        }
        if (index == -1) {
            return url;
        }
        return url.substring(0, index);
    }
}
