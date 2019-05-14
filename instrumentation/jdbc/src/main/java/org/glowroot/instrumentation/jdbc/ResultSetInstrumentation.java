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

import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.checker.NonNull;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.jdbc.StatementInstrumentation.HasStatementMirrorMixin;
import org.glowroot.instrumentation.jdbc.boot.StatementMirror;

public class ResultSetInstrumentation {

    private static final Logger logger = Logger.getLogger(ResultSetInstrumentation.class);

    private static final ConfigService configService = Agent.getConfigService("jdbc");

    private static final BooleanProperty captureResultSetNavigate =
            configService.getBooleanProperty("captureResultSetNavigate");

    private static final BooleanProperty captureResultSetGet =
            configService.getBooleanProperty("captureResultSetGet");

    private static final AtomicBoolean getRowExceptionLogged = new AtomicBoolean();

    @Advice.Pointcut(className = "java.sql.ResultSet",
                     methodName = "next",
                     methodParameterTypes = {},
                     nestingGroup = "jdbc")
    public static class NextAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin resultSet) {

            return captureResultSetNavigate.value() && isEnabledCommon(resultSet);
        }

        @Advice.OnMethodBefore
        public static Timer onBefore(@Bind.This HasStatementMirrorMixin resultSet) {

            return onBeforeCommon(resultSet);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return boolean currentRowValid,
                @Bind.This HasStatementMirrorMixin resultSet) {

            StatementMirror mirror = resultSet.glowroot$getStatementMirror();
            if (mirror == null) {
                // this shouldn't happen since just checked above in isEnabled(), unless some
                // bizarre concurrent mis-usage of ResultSet
                return;
            }
            QuerySpan lastQuerySpan = mirror.getLastQuerySpan();
            if (lastQuerySpan == null) {
                // tracing must be disabled (e.g. exceeded a span limit imposed by the agent)
                return;
            }
            if (currentRowValid) {
                // ResultSet.getRow() is sometimes not super duper fast due to ResultSet
                // wrapping and other checks, so this optimizes the common case
                lastQuerySpan.incrementCurrRow();
            } else {
                lastQuerySpan.rowNavigationAttempted();
            }
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {
            timer.stop();
        }
    }

    @Advice.Pointcut(className = "java.sql.ResultSet",
                     methodName = "previous|relative|absolute|first|last",
                     methodParameterTypes = "..",
                     nestingGroup = "jdbc")
    public static class NavigateAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin resultSet) {

            return captureResultSetNavigate.value() && isEnabledCommon(resultSet);
        }

        @Advice.OnMethodBefore
        public static Timer onBefore(@Bind.This HasStatementMirrorMixin resultSet) {

            return onBeforeCommon(resultSet);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This HasStatementMirrorMixin resultSet) {

            StatementMirror mirror = resultSet.glowroot$getStatementMirror();
            if (mirror == null) {
                // this shouldn't happen since just checked above in isEnabled(), unless some
                // bizarre concurrent mis-usage of ResultSet
                return;
            }
            QuerySpan lastQuerySpan = mirror.getLastQuerySpan();
            if (lastQuerySpan == null) {
                // tracing must be disabled (e.g. exceeded a span limit imposed by the agent)
                return;
            }
            try {
                lastQuerySpan.setCurrRow(((ResultSet) resultSet).getRow());
            } catch (Exception e) {
                if (getRowExceptionLogged.getAndSet(true)) {
                    logger.debug(e.getMessage(), e);
                } else {
                    logger.warn(e.getMessage(), e);
                }
            }
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {

            timer.stop();
        }
    }

    @Advice.Pointcut(className = "java.sql.ResultSet",
                     methodName = "get*",
                     methodParameterTypes = {"int", ".."},
                     nestingGroup = "jdbc")
    public static class ValueAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin resultSet) {

            return captureResultSetGet.value() && isEnabledCommon(resultSet);
        }

        @Advice.OnMethodBefore
        public static Timer onBefore(@Bind.This HasStatementMirrorMixin resultSet) {

            return onBeforeCommon(resultSet);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {

            timer.stop();
        }
    }

    @Advice.Pointcut(className = "java.sql.ResultSet",
                     methodName = "get*",
                     methodParameterTypes = {"java.lang.String", ".."},
                     nestingGroup = "jdbc")
    public static class ValueAdvice2 {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This HasStatementMirrorMixin resultSet) {

            return captureResultSetGet.value() && isEnabledCommon(resultSet);
        }

        @Advice.OnMethodBefore
        public static Timer onBefore(@Bind.This HasStatementMirrorMixin resultSet) {

            return onBeforeCommon(resultSet);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {
            timer.stop();
        }
    }

    private static boolean isEnabledCommon(HasStatementMirrorMixin resultSet) {

        StatementMirror mirror = resultSet.glowroot$getStatementMirror();
        return mirror != null && mirror.getLastQuerySpan() != null;
    }

    private static Timer onBeforeCommon(HasStatementMirrorMixin resultSet) {

        @SuppressWarnings("nullness") // just checked above in isEnabledCommon()
        @NonNull
        StatementMirror mirror = resultSet.glowroot$getStatementMirror();

        @SuppressWarnings("nullness") // just checked above in isEnabledCommon()
        @NonNull
        QuerySpan lastQuerySpan = mirror.getLastQuerySpan();
        return lastQuerySpan.extend();
    }
}
