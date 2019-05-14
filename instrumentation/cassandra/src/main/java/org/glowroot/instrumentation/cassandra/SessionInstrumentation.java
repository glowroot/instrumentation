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
package org.glowroot.instrumentation.cassandra;

import java.util.ArrayList;
import java.util.Collection;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AsyncQuerySpan;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.cassandra.ResultSetFutureInstrumentation.ResultSetFutureMixin;
import org.glowroot.instrumentation.cassandra.ResultSetInstrumentation.ResultSetMixin;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class SessionInstrumentation {

    private static final TimerName QUERY_TIMER_NAME = Agent.getTimerName("cassandra query");

    private static final TimerName PREPARE_TIMER_NAME = Agent.getTimerName("cql prepare");

    private static final String QUERY_TYPE = "Cassandra";

    private static final ConfigService configService = Agent.getConfigService("cassandra");

    // visibility should be provided by memoryBarrier in
    // org.glowroot.instrumentation.api.config.ConfigService
    private static long stackTraceThresholdNanos;

    static {
        configService.registerConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                Double value = configService.getDoubleProperty("stackTraceThresholdMillis").value();
                stackTraceThresholdNanos =
                        value == null ? Long.MAX_VALUE : MILLISECONDS.toNanos(value.intValue());
            }
        });
    }

    @Shim("com.datastax.driver.core.Statement")
    public interface Statement {}

    @Shim("com.datastax.driver.core.RegularStatement")
    public interface RegularStatement extends Statement {

        @Nullable
        String getQueryString();
    }

    @Shim("com.datastax.driver.core.BoundStatement")
    public interface BoundStatement extends Statement {

        @Shim("com.datastax.driver.core.PreparedStatement preparedStatement()")
        @Nullable
        PreparedStatement glowroot$preparedStatement();
    }

    @Shim("com.datastax.driver.core.BatchStatement")
    public interface BatchStatement extends Statement {

        @Nullable
        Collection<Statement> getStatements();
    }

    @Shim("com.datastax.driver.core.PreparedStatement")
    public interface PreparedStatement {

        @Nullable
        String getQueryString();
    }

    @Advice.Pointcut(className = "com.datastax.driver.core.Session",
                     methodName = "execute",
                     methodParameterTypes = {"com.datastax.driver.core.Statement"},
                     nestingGroup = "cassandra",
                     suppressionKey = "wait-on-future")
    public static class ExecuteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable QuerySpan onBefore(
                @Bind.Argument(0) @Nullable Object arg,
                ThreadContext context) {

            QuerySpanInfo querySpanInfo = getQuerySpanInfo(arg);
            if (querySpanInfo == null) {
                return null;
            }
            // TODO capture dest
            return context.startQuerySpan(QUERY_TYPE, "", querySpanInfo.queryText,
                    querySpanInfo.queryMessageSupplier, QUERY_TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable ResultSetMixin resultSet,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
                if (resultSet != null) {
                    resultSet.glowroot$setQuerySpan(querySpan);
                }
                querySpan.endWithLocationStackTrace(stackTraceThresholdNanos);
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

    @Advice.Pointcut(className = "com.datastax.driver.core.Session",
                     methodName = "prepare",
                     methodParameterTypes = {"*"},
                     suppressionKey = "wait-on-future")
    public static class PrepareAdvice {

        @Advice.OnMethodBefore
        public static Timer onBefore(ThreadContext context) {

            return context.startTimer(PREPARE_TIMER_NAME);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {

            timer.stop();
        }
    }

    @Advice.Pointcut(className = "com.datastax.driver.core.Session",
                     methodName = "executeAsync",
                     methodParameterTypes = {"com.datastax.driver.core.Statement"},
                     nestingGroup = "cassandra")
    public static class ExecuteAsyncAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncQuerySpan onBefore(
                ThreadContext context,
                @Bind.Argument(0) @Nullable Object arg) {

            QuerySpanInfo querySpanInfo = getQuerySpanInfo(arg);
            if (querySpanInfo == null) {
                return null;
            }
            // TODO capture dest
            return context.startAsyncQuerySpan(QUERY_TYPE, "", querySpanInfo.queryText,
                    querySpanInfo.queryMessageSupplier, QUERY_TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable ResultSetFutureMixin future,
                @Bind.Enter @Nullable AsyncQuerySpan asyncQuerySpan) {

            if (asyncQuerySpan == null) {
                return;
            }
            asyncQuerySpan.stopSyncTimer();
            if (future == null) {
                asyncQuerySpan.end();
                return;
            }
            // to prevent race condition, setting async query entry before getting completed status,
            // and the converse is done when getting async query entry
            // ok if end() happens to get called twice
            future.glowroot$setAsyncQuerySpan(asyncQuerySpan);
            if (future.glowroot$isCompleted()) {
                // ResultSetFuture completed really fast, prior to @Advice.OnMethodExit
                Throwable exception = future.glowroot$getException();
                if (exception == null) {
                    asyncQuerySpan.end();
                } else {
                    asyncQuerySpan.endWithError(exception);
                }
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable AsyncQuerySpan asyncQuerySpan) {

            if (asyncQuerySpan != null) {
                asyncQuerySpan.stopSyncTimer();
                asyncQuerySpan.endWithError(t);
            }
        }
    }

    private static @Nullable QuerySpanInfo getQuerySpanInfo(@Nullable Object arg) {
        if (arg == null) {
            // seems nothing sensible to do here other than ignore
            return null;
        }
        String queryText;
        if (arg instanceof String) {
            queryText = (String) arg;
        } else if (arg instanceof RegularStatement) {
            queryText = nullToEmpty(((RegularStatement) arg).getQueryString());
        } else if (arg instanceof BoundStatement) {
            PreparedStatement preparedStatement =
                    ((BoundStatement) arg).glowroot$preparedStatement();
            queryText = preparedStatement == null ? ""
                    : nullToEmpty(preparedStatement.getQueryString());
        } else if (arg instanceof BatchStatement) {
            Collection<Statement> statements = ((BatchStatement) arg).getStatements();
            if (statements == null) {
                statements = new ArrayList<Statement>();
            }
            queryText = concatenate(statements);
        } else {
            return null;
        }
        return new QuerySpanInfo(queryText, QueryMessageSupplier.create());
    }

    private static String concatenate(Collection<Statement> statements) {
        if (statements.isEmpty()) {
            return "[empty batch]";
        }
        StringBuilder sb = new StringBuilder("[batch] ");
        String currQuery = null;
        int currCount = 0;
        boolean first = true;
        for (Statement statement : statements) {
            String query = getQuery(statement);
            if (currQuery == null) {
                currQuery = query;
                currCount = 1;
            } else if (!query.equals(currQuery)) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                if (currCount == 1) {
                    sb.append(currQuery);
                } else {
                    sb.append(currCount + " x " + currQuery);
                }
                currQuery = query;
                currCount = 1;
            } else {
                currCount++;
            }
        }
        if (currQuery != null) {
            if (!first) {
                sb.append(", ");
            }
            if (currCount == 1) {
                sb.append(currQuery);
            } else {
                sb.append(currCount + " x " + currQuery);
            }
        }
        return sb.toString();
    }

    private static String getQuery(Statement statement) {
        if (statement instanceof RegularStatement) {
            String qs = ((RegularStatement) statement).getQueryString();
            return nullToEmpty(qs);
        } else if (statement instanceof BoundStatement) {
            PreparedStatement preparedStatement =
                    ((BoundStatement) statement).glowroot$preparedStatement();
            String qs = preparedStatement == null ? "" : preparedStatement.getQueryString();
            return nullToEmpty(qs);
        } else if (statement instanceof BatchStatement) {
            return "[nested batch statement]";
        } else {
            return "[unexpected statement type: " + statement.getClass().getName() + "]";
        }
    }

    private static String nullToEmpty(@Nullable String string) {
        return string == null ? "" : string;
    }

    private static class QuerySpanInfo {

        private final String queryText;
        private final QueryMessageSupplier queryMessageSupplier;

        private QuerySpanInfo(String queryText, QueryMessageSupplier messageSupplier) {
            this.queryText = queryText;
            this.queryMessageSupplier = messageSupplier;
        }
    }
}
