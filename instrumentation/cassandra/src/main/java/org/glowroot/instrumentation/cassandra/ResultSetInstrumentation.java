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

import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;

public class ResultSetInstrumentation {

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("com.datastax.driver.core.ResultSet")
    public static class ResultSetImpl implements ResultSetMixin {

        // this may be async or non-async query entry
        //
        // needs to be volatile, since ResultSets are thread safe, and therefore app/framework does
        // *not* need to provide visibility when used across threads and so this cannot piggyback
        // (unlike with jdbc ResultSets)
        private transient volatile @Nullable QuerySpan glowroot$querySpan;

        @Override
        public @Nullable QuerySpan glowroot$getQuerySpan() {
            return glowroot$querySpan;
        }

        @Override
        public void glowroot$setQuerySpan(@Nullable QuerySpan querySpan) {
            glowroot$querySpan = querySpan;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface ResultSetMixin {

        @Nullable
        QuerySpan glowroot$getQuerySpan();

        void glowroot$setQuerySpan(@Nullable QuerySpan querySpan);
    }

    @Advice.Pointcut(className = "com.datastax.driver.core.ResultSet",
                     methodName = "one",
                     methodParameterTypes = {})
    public static class OneAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This ResultSetMixin resultSet) {

            QuerySpan querySpan = resultSet.glowroot$getQuerySpan();
            return querySpan == null ? null : querySpan.extend();
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object row,
                @Bind.This ResultSetMixin resultSet) {

            QuerySpan querySpan = resultSet.glowroot$getQuerySpan();
            if (querySpan == null) {
                return;
            }
            if (row != null) {
                querySpan.incrementCurrRow();
            } else {
                querySpan.rowNavigationAttempted();
            }
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    @Advice.Pointcut(className = "java.lang.Iterable",
                     subTypeRestriction = "com.datastax.driver.core.ResultSet",
                     methodName = "iterator",
                     methodParameterTypes = {})
    public static class IteratorAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This ResultSetMixin resultSet) {

            QuerySpan querySpan = resultSet.glowroot$getQuerySpan();
            if (querySpan == null) {
                // tracing must be disabled (e.g. exceeded a span limit imposed by the agent)
                return;
            }
            querySpan.rowNavigationAttempted();
        }
    }

    @Advice.Pointcut(
                     className = "com.datastax.driver.core.PagingIterable"
                             + "|com.datastax.driver.core.ResultSet",
                     methodName = "isExhausted",
                     subTypeRestriction = "com.datastax.driver.core.ResultSet",
                     methodParameterTypes = {})
    public static class IsExhaustedAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This ResultSetMixin resultSet) {

            QuerySpan querySpan = resultSet.glowroot$getQuerySpan();
            return querySpan == null ? null : querySpan.extend();
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This ResultSetMixin resultSet) {

            QuerySpan querySpan = resultSet.glowroot$getQuerySpan();
            if (querySpan == null) {
                // tracing must be disabled (e.g. exceeded a span limit imposed by the agent)
                return;
            }
            querySpan.rowNavigationAttempted();
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }
}
