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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.NonNull;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.jdbc.boot.BatchPreparedStatementMessageSupplier;
import org.glowroot.instrumentation.jdbc.boot.BatchPreparedStatementMessageSupplier2;
import org.glowroot.instrumentation.jdbc.boot.JdbcInstrumentationProperties;
import org.glowroot.instrumentation.jdbc.boot.PreparedStatementMessageSupplier;
import org.glowroot.instrumentation.jdbc.boot.PreparedStatementMirror;
import org.glowroot.instrumentation.jdbc.boot.PreparedStatementMirror.ByteArrayParameterValue;
import org.glowroot.instrumentation.jdbc.boot.PreparedStatementMirror.StreamingParameterValue;
import org.glowroot.instrumentation.jdbc.boot.StatementMessageSupplier;
import org.glowroot.instrumentation.jdbc.boot.StatementMirror;

// many of the pointcuts are not restricted to configService.isEnabled() because StatementMirrors
// must be tracked for their entire life
public class StatementInstrumentation {

    private static final Logger logger = Logger.getLogger(StatementInstrumentation.class);

    private static final TimerName QUERY_TIMER_NAME = Agent.getTimerName("jdbc query");

    private static final TimerName STATEMENT_CLOSE_TIMER_NAME =
            Agent.getTimerName("jdbc statement close");

    private static final String QUERY_TYPE = "SQL";

    private static final QueryMessageSupplier BATCH_STATEMENT_MESSAGE_SUPPLIER =
            QueryMessageSupplier.create(Collections.singletonMap("batchStatement", true));

    private static final ConfigService configService = Agent.getConfigService("jdbc");

    private static final BooleanProperty captureStatementClose =
            configService.getBooleanProperty("captureStatementClose");

    private static boolean captureBindParameters;

    private static final AtomicBoolean explainPlanExceptionLogged = new AtomicBoolean();

    private static final AtomicBoolean explainPlanMultipleRowsLogged = new AtomicBoolean();

    static {
        configService.registerConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                captureBindParameters = !configService
                        .getListProperty("captureBindParametersIncludes").value().isEmpty();
            }
        });
    }

    // ===================== Mixin =====================

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"java.sql.Statement", "java.sql.ResultSet"})
    public static class HasStatementMirrorImpl implements HasStatementMirrorMixin {

        // does not need to be volatile, app/framework must provide visibility of Statements and
        // ResultSets if used across threads and this can piggyback
        private transient @Nullable StatementMirror glowroot$statementMirror;

        @Override
        public @Nullable StatementMirror glowroot$getStatementMirror() {
            return glowroot$statementMirror;
        }

        @Override
        public void glowroot$setStatementMirror(@Nullable StatementMirror statementMirror) {
            glowroot$statementMirror = statementMirror;
        }

        @Override
        public boolean glowroot$hasStatementMirror() {
            return glowroot$statementMirror != null;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface HasStatementMirrorMixin {

        @Nullable
        StatementMirror glowroot$getStatementMirror();

        void glowroot$setStatementMirror(@Nullable StatementMirror statementMirror);

        boolean glowroot$hasStatementMirror();
    }

    // ================= Parameter Binding =================

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "setArray|setBigDecimal"
                             + "|setBoolean|setByte|setDate|setDouble|setFloat|setInt|setLong|setNString"
                             + "|setRef|setRowId|setShort|setString|setTime|setTimestamp|setURL",
                     methodParameterTypes = {"int", "*", ".."})
    public static class SetXAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureBindParameters;
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                @Bind.Argument(0) int parameterIndex,
                @Bind.Argument(1) @Nullable Object x) {

            PreparedStatementMirror mirror =
                    (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
            if (mirror != null) {
                mirror.setParameterValue(parameterIndex, x);
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "setAsciiStream|setBinaryStream|setBlob|setCharacterStream|setClob"
                             + "|setNCharacterStream|setNClob|setSQLXML|setUnicodeStream",
                     methodParameterTypes = {"int", "*", ".."})
    public static class SetStreamAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureBindParameters;
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                @Bind.Argument(0) int parameterIndex,
                @Bind.Argument(1) @Nullable Object x) {

            PreparedStatementMirror mirror =
                    (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
            if (mirror != null) {
                if (x == null) {
                    mirror.setParameterValue(parameterIndex, null);
                } else {
                    mirror.setParameterValue(parameterIndex,
                            new StreamingParameterValue(x.getClass()));
                }
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "setBytes",
                     methodParameterTypes = {"int", "byte[]"})
    public static class SetBytesAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureBindParameters;
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                @Bind.Argument(0) int parameterIndex,
                @Bind.Argument(1) byte /*@Nullable*/ [] x) {

            PreparedStatementMirror mirror =
                    (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
            if (mirror != null) {
                if (x == null) {
                    mirror.setParameterValue(parameterIndex, null);
                } else {
                    setBytes(mirror, parameterIndex, x);
                }
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "setObject",
                     methodParameterTypes = {"int", "java.lang.Object", ".."})
    public static class SetObjectAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureBindParameters;
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                @Bind.Argument(0) int parameterIndex,
                @Bind.Argument(1) @Nullable Object x) {

            PreparedStatementMirror mirror =
                    (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
            if (mirror != null) {
                if (x == null) {
                    mirror.setParameterValue(parameterIndex, null);
                } else if (x instanceof byte[]) {
                    setBytes(mirror, parameterIndex, (byte[]) x);
                } else {
                    mirror.setParameterValue(parameterIndex, x);
                }
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "setNull",
                     methodParameterTypes = {"int", "int", ".."})
    public static class SetNullAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureBindParameters;
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                @Bind.Argument(0) int parameterIndex) {

            PreparedStatementMirror mirror =
                    (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
            if (mirror != null) {
                mirror.setParameterValue(parameterIndex, null);
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "clearParameters",
                     methodParameterTypes = {})
    public static class ClearParametersAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {

            return captureBindParameters;
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This HasStatementMirrorMixin preparedStatement) {
            PreparedStatementMirror mirror =
                    (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
            if (mirror != null) {
                mirror.clearParameters();
            }
        }
    }

    // ================== Statement Batching ==================

    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "addBatch",
                     methodParameterTypes = {"java.lang.String"})
    public static class StatementAddBatchAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HasStatementMirrorMixin statement,
                @Bind.Argument(0) @Nullable String sql) {

            if (sql == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            StatementMirror mirror = statement.glowroot$getStatementMirror();
            if (mirror != null) {
                mirror.addBatch(sql);
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "addBatch",
                     methodParameterTypes = {})
    public static class PreparedStatementAddBatchAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This HasStatementMirrorMixin preparedStatement) {

            PreparedStatementMirror mirror =
                    (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
            if (mirror != null) {
                mirror.addBatch();
            }
        }
    }

    // Statement.clearBatch() can be used to re-initiate a prepared statement
    // that has been cached from a previous usage
    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "clearBatch",
                     methodParameterTypes = {})
    public static class ClearBatchAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This HasStatementMirrorMixin statement) {

            StatementMirror mirror = statement.glowroot$getStatementMirror();
            if (mirror != null) {
                mirror.clearBatch();
            }
        }
    }

    // =================== Statement Execution ===================

    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "execute",
                     methodParameterTypes = {"java.lang.String", ".."},
                     nestingGroup = "jdbc")
    public static class StatementExecuteAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin statement) {

            return statement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static @Nullable QuerySpan onBefore(
                @Bind.This HasStatementMirrorMixin statement,
                @Bind.Argument(0) @Nullable String sql,
                ThreadContext context) {

            return onBeforeStatement(statement, sql, new StatementMessageSupplier(), context);
        }

        @Advice.OnMethodReturn
        public static <T extends Statement & HasStatementMirrorMixin> void onReturn(
                @Bind.This T statement,
                @Bind.Argument(0) @Nullable String sql,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
                long totalNanos = querySpan.partOneEndWithLocationStackTrace(
                        JdbcInstrumentationProperties.stackTraceThresholdNanos());
                if (totalNanos >= JdbcInstrumentationProperties.explainPlanThresholdNanos()
                        && sql != null) {
                    captureExplainPlan(statement, sql, querySpan);
                }
                querySpan.partTwoEnd();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
                querySpan.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "executeQuery",
                     methodParameterTypes = {"java.lang.String"},
                     methodReturnType = "java.sql.ResultSet",
                     nestingGroup = "jdbc")
    public static class StatementExecuteQueryAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin statement) {

            return statement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static @Nullable QuerySpan onBefore(
                @Bind.This HasStatementMirrorMixin statement,
                @Bind.Argument(0) @Nullable String sql,
                ThreadContext context) {

            return onBeforeStatement(statement, sql, new StatementMessageSupplier(), context);
        }

        @Advice.OnMethodReturn
        public static <T extends Statement & HasStatementMirrorMixin> void onReturn(
                @Bind.Return @Nullable HasStatementMirrorMixin resultSet,
                @Bind.This T statement,
                @Bind.Argument(0) @Nullable String sql,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            // Statement can always be retrieved from ResultSet.getStatement(), and
            // StatementMirror from that, but ResultSet.getStatement() is sometimes not super
            // duper fast due to ResultSet wrapping and other checks, so StatementMirror is
            // stored directly in ResultSet as an optimization
            if (resultSet != null) {
                StatementMirror mirror = statement.glowroot$getStatementMirror();
                resultSet.glowroot$setStatementMirror(mirror);
            }
            if (querySpan != null) {
                long totalNanos = querySpan.partOneEndWithLocationStackTrace(
                        JdbcInstrumentationProperties.stackTraceThresholdNanos());
                if (totalNanos >= JdbcInstrumentationProperties.explainPlanThresholdNanos()
                        && sql != null) {
                    captureExplainPlan(statement, sql, querySpan);
                }
                querySpan.partTwoEnd();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
                querySpan.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "executeUpdate",
                     methodParameterTypes = {"java.lang.String", ".."},
                     methodReturnType = "int",
                     nestingGroup = "jdbc")
    public static class StatementExecuteUpdateAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin statement) {

            return statement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static @Nullable QuerySpan onBefore(
                @Bind.This HasStatementMirrorMixin statement,
                @Bind.Argument(0) @Nullable String sql,
                ThreadContext context) {

            return onBeforeStatement(statement, sql, QueryMessageSupplier.create(), context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return int rowCount,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
                querySpan.setCurrRow(rowCount);
                querySpan.endWithLocationStackTrace(
                        JdbcInstrumentationProperties.stackTraceThresholdNanos());
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
                querySpan.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "execute",
                     methodParameterTypes = {},
                     nestingGroup = "jdbc")
    public static class PreparedStatementExecuteAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin preparedStatement) {

            return preparedStatement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static QuerySpan onBefore(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                ThreadContext context) {

            return onBeforePreparedStatement(preparedStatement, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter QuerySpan querySpan) {

            querySpan.endWithLocationStackTrace(
                    JdbcInstrumentationProperties.stackTraceThresholdNanos());
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter QuerySpan querySpan) {

            querySpan.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "executeQuery",
                     methodParameterTypes = {},
                     methodReturnType = "java.sql.ResultSet",
                     nestingGroup = "jdbc")
    public static class PreparedStatementExecuteQueryAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin preparedStatement) {

            return preparedStatement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static QuerySpan onBefore(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                ThreadContext context) {

            return onBeforePreparedStatement(preparedStatement, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable HasStatementMirrorMixin resultSet,
                @Bind.This HasStatementMirrorMixin preparedStatement,
                @Bind.Enter QuerySpan querySpan) {

            // PreparedStatement can always be retrieved from ResultSet.getStatement(), and
            // StatementMirror from that, but ResultSet.getStatement() is sometimes not super
            // duper fast due to ResultSet wrapping and other checks, so StatementMirror is
            // stored directly in ResultSet as an optimization
            if (resultSet != null) {
                StatementMirror mirror = preparedStatement.glowroot$getStatementMirror();
                resultSet.glowroot$setStatementMirror(mirror);
            }
            querySpan.endWithLocationStackTrace(
                    JdbcInstrumentationProperties.stackTraceThresholdNanos());
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter QuerySpan querySpan) {

            querySpan.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "java.sql.PreparedStatement",
                     methodName = "executeUpdate",
                     methodParameterTypes = {},
                     methodReturnType = "int",
                     nestingGroup = "jdbc")
    public static class PreparedStatementExecuteUpdateAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin preparedStatement) {

            return preparedStatement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static QuerySpan onBefore(
                @Bind.This HasStatementMirrorMixin preparedStatement,
                ThreadContext context) {

            return onBeforePreparedStatement(preparedStatement, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return int rowCount,
                @Bind.Enter QuerySpan querySpan) {

            querySpan.setCurrRow(rowCount);
            querySpan.endWithLocationStackTrace(
                    JdbcInstrumentationProperties.stackTraceThresholdNanos());
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter QuerySpan querySpan) {

            querySpan.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "executeBatch",
                     methodParameterTypes = {},
                     nestingGroup = "jdbc")
    public static class StatementExecuteBatchAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin statement) {

            return statement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static QuerySpan onBefore(
                @Bind.This HasStatementMirrorMixin statement,
                ThreadContext context) {

            @SuppressWarnings("nullness") // just checked above in isEnabled()
            @NonNull
            StatementMirror mirror = statement.glowroot$getStatementMirror();
            if (statement instanceof PreparedStatement) {
                return onBeforeBatchPreparedStatement((PreparedStatementMirror) mirror, context);
            } else {
                return onBeforeBatchStatement(mirror, context);
            }
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return int[] rowCounts,
                @Bind.Enter QuerySpan querySpan) {

            int totalRowCount = 0;
            boolean count = false;
            for (int rowCount : rowCounts) {
                if (rowCount > 0) {
                    // ignore Statement.SUCCESS_NO_INFO (-2) and Statement.EXECUTE_FAILED (-3)
                    totalRowCount += rowCount;
                    count = true;
                }
            }
            if (count) {
                querySpan.setCurrRow(totalRowCount);
            }
            querySpan.endWithLocationStackTrace(
                    JdbcInstrumentationProperties.stackTraceThresholdNanos());
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter QuerySpan querySpan) {

            querySpan.endWithError(t);
        }
    }

    // ================== Additional ResultSet Tracking ==================

    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "getResultSet",
                     methodParameterTypes = {".."},
                     methodReturnType = "java.sql.ResultSet")
    public static class StatementGetResultSetAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin statement) {

            return statement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable HasStatementMirrorMixin resultSet,
                @Bind.This HasStatementMirrorMixin statement) {

            if (resultSet == null) {
                return;
            }
            StatementMirror mirror = statement.glowroot$getStatementMirror();
            resultSet.glowroot$setStatementMirror(mirror);
        }
    }

    // ================== Statement Closing ==================

    @Advice.Pointcut(className = "java.sql.Statement",
                     methodName = "close",
                     methodParameterTypes = {},
                     nestingGroup = "jdbc")
    public static class CloseAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin statement) {

            return statement.glowroot$hasStatementMirror();
        }

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(
                @Bind.This HasStatementMirrorMixin statement,
                ThreadContext context) {

            StatementMirror mirror = statement.glowroot$getStatementMirror();
            if (mirror != null) {
                // this should always be true since just checked hasStatementMirror() above
                mirror.clearLastQuerySpan();
            }
            if (captureStatementClose.value()) {
                return context.startTimer(STATEMENT_CLOSE_TIMER_NAME);
            } else {
                return null;
            }
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    private static @Nullable QuerySpan onBeforeStatement(HasStatementMirrorMixin statement,
            @Nullable String sql, QueryMessageSupplier messageSupplier, ThreadContext context) {

        if (sql == null) {
            // seems nothing sensible to do here other than ignore
            return null;
        }
        StatementMirror mirror = statement.glowroot$getStatementMirror();
        if (mirror == null) {
            // this shouldn't happen since just checked hasStatementMirror() above
            return null;
        }
        QuerySpan querySpan = context.startQuerySpan(QUERY_TYPE, mirror.getDest(), sql,
                messageSupplier, QUERY_TIMER_NAME);
        mirror.setLastQuerySpan(querySpan);
        return querySpan;
    }

    private static QuerySpan onBeforePreparedStatement(HasStatementMirrorMixin preparedStatement,
            ThreadContext context) {

        @SuppressWarnings("nullness") // just checked above in isEnabled()
        @NonNull
        PreparedStatementMirror mirror =
                (PreparedStatementMirror) preparedStatement.glowroot$getStatementMirror();
        QueryMessageSupplier messageSupplier;
        String queryText = mirror.getSql();
        if (captureBindParameters) {
            messageSupplier =
                    new PreparedStatementMessageSupplier(mirror.getParameters(), queryText);
        } else {
            messageSupplier = QueryMessageSupplier.create();
        }
        QuerySpan querySpan = context.startQuerySpan(QUERY_TYPE, mirror.getDest(), queryText,
                messageSupplier, QUERY_TIMER_NAME);
        mirror.setLastQuerySpan(querySpan);
        return querySpan;
    }

    private static QuerySpan onBeforeBatchStatement(StatementMirror mirror, ThreadContext context) {

        List<String> batchedSql = mirror.getBatchedSql();
        String concatenated;
        if (batchedSql.isEmpty()) {
            concatenated = "[empty batch statement]";
        } else if (batchedSql.size() == 1) {
            concatenated = batchedSql.get(0);
        } else {
            int len = 0;
            for (String sql : batchedSql) {
                len += sql.length();
            }
            int numSeparators = batchedSql.size() - 1;
            StringBuilder sb = new StringBuilder(len + 2 * numSeparators);
            boolean first = true;
            for (String sql : batchedSql) {
                if (!first) {
                    sb.append("; ");
                }
                sb.append(sql);
                first = false;
            }
            concatenated = sb.toString();
        }
        QuerySpan querySpan = context.startQuerySpan(QUERY_TYPE, mirror.getDest(), concatenated,
                BATCH_STATEMENT_MESSAGE_SUPPLIER, QUERY_TIMER_NAME);
        mirror.setLastQuerySpan(querySpan);
        mirror.clearBatch();
        return querySpan;
    }

    private static QuerySpan onBeforeBatchPreparedStatement(PreparedStatementMirror mirror,
            ThreadContext context) {

        QueryMessageSupplier messageSupplier;
        String queryText = mirror.getSql();
        int batchCount = mirror.getBatchCount();
        if (batchCount <= 0) {
            messageSupplier = new BatchPreparedStatementMessageSupplier2(0);
        } else if (captureBindParameters) {
            messageSupplier = new BatchPreparedStatementMessageSupplier(
                    mirror.getBatchedParameters(), batchCount);
        } else {
            messageSupplier = new BatchPreparedStatementMessageSupplier2(batchCount);
        }
        QuerySpan querySpan = context.startQuerySpan(QUERY_TYPE, mirror.getDest(), queryText,
                batchCount, messageSupplier, QUERY_TIMER_NAME);
        mirror.setLastQuerySpan(querySpan);
        mirror.clearBatch();
        return querySpan;
    }

    private static void setBytes(PreparedStatementMirror mirror, int parameterIndex, byte[] x) {

        boolean displayAsHex =
                JdbcInstrumentationProperties.displayBinaryParameterAsHex(mirror.getSql(),
                        parameterIndex);
        mirror.setParameterValue(parameterIndex, new ByteArrayParameterValue(x, displayAsHex));
    }

    private static <T extends Statement & HasStatementMirrorMixin> void captureExplainPlan(
            T statement, String sql, QuerySpan querySpan) {
        StatementMessageSupplier messageSupplier =
                (StatementMessageSupplier) querySpan.getMessageSupplier();
        if (messageSupplier == null) {
            return;
        }
        if (!startsWithCaseInsensitive(sql, "SELECT ")) {
            return;
        }
        try {
            Connection connection;
            try {
                connection = statement.getConnection();
            } catch (ClassCastException e) {
                // e.g. old tomcat jdbc connection pool proxies are busted (e.g. run in
                // StatementIT.testExplainPlan() in JavaagentContainer using
                // Connections.TOMCAT_JDBC_POOL_WRAPPED with tomcat.version 7.0.19)
                return;
            }
            String dest = ConnectionInstrumentation.getDest(connection);
            if (dest.startsWith("jdbc:mysql:")
                    || dest.startsWith("jdbc:postgresql:")
                    || dest.startsWith("jdbc:h2:")) {
                messageSupplier.setExplainPlan(captureExplainQuery(connection, "EXPLAIN " + sql));
            } else if (dest.startsWith("jdbc:oracle:")) {
                messageSupplier
                        .setExplainPlan(captureExplainQuery(connection, "EXPLAIN PLAN FOR " + sql));
            }
        } catch (Exception e) {
            if (explainPlanExceptionLogged.getAndSet(true)) {
                logger.debug(e.getMessage(), e);
            } else {
                logger.warn(e.getMessage(), e);
            }
        }
    }

    private static boolean startsWithCaseInsensitive(String str, String prefix) {
        return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    // suppress warnings is needed for checker framework @Tainted check
    @SuppressWarnings("argument.type.incompatible")
    private static Object captureExplainQuery(Connection connection, String query)
            throws SQLException {
        Statement statement = null;
        ResultSet rs = null;
        try {
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            if (!rs.next()) {
                return "[empty explain plan]";
            }
            Object explainPlan;
            int columns = rs.getMetaData().getColumnCount();
            if (columns == 1) {
                // e.g. PostgreSQL
                explainPlan = rs.getString(1);
                if (explainPlan == null) {
                    explainPlan = "[empty explain plan]";
                }
            } else {
                // e.g. MySQL
                Map<String, String> map = new HashMap<String, String>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); ++i) {
                    map.put(rs.getMetaData().getColumnName(i), rs.getString(i));
                }
                explainPlan = map;
            }
            if (rs.next() && !explainPlanMultipleRowsLogged.getAndSet(true)) {
                logger.info("explain plan unexpectedly returned more than one row, please report"
                        + " this to https://github.com/glowroot/instrumentation");
            }
            return explainPlan;
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    logger.debug(e.getMessage(), e);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    logger.debug(e.getMessage(), e);
                }
            }
        }
    }
}
