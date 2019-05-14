/*
 * Copyright 2016-2019 the original author or authors.
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

import org.glowroot.instrumentation.api.AsyncQuerySpan;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.cassandra.ResultSetInstrumentation.ResultSetMixin;

public class ResultSetFutureInstrumentation {

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("com.datastax.driver.core.ResultSetFuture")
    public static class ResultSetFutureImpl implements ResultSetFutureMixin {

        private transient volatile boolean glowroot$completed;
        private transient volatile @Nullable Throwable glowroot$exception;
        private transient volatile @Nullable AsyncQuerySpan glowroot$asyncQuerySpan;

        @Override
        public void glowroot$setCompleted() {

            glowroot$completed = true;
        }

        @Override
        public boolean glowroot$isCompleted() {

            return glowroot$completed;
        }

        @Override
        public void glowroot$setException(Throwable exception) {

            glowroot$exception = exception;
        }

        @Override
        public @Nullable Throwable glowroot$getException() {

            return glowroot$exception;
        }

        @Override
        public @Nullable AsyncQuerySpan glowroot$getAsyncQuerySpan() {

            return glowroot$asyncQuerySpan;
        }

        @Override
        public void glowroot$setAsyncQuerySpan(@Nullable AsyncQuerySpan asyncQuerySpan) {

            glowroot$asyncQuerySpan = asyncQuerySpan;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface ResultSetFutureMixin {

        void glowroot$setCompleted();

        boolean glowroot$isCompleted();

        void glowroot$setException(Throwable t);

        @Nullable
        Throwable glowroot$getException();

        @Nullable
        AsyncQuerySpan glowroot$getAsyncQuerySpan();

        void glowroot$setAsyncQuerySpan(@Nullable AsyncQuerySpan asyncQuerySpan);
    }

    @Advice.Pointcut(className = "java.util.concurrent.Future",
                     subTypeRestriction = "com.datastax.driver.core.ResultSetFuture",
                     methodName = "get",
                     methodParameterTypes = {".."},
                     suppressionKey = "wait-on-future")
    public static class FutureGetAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(
                @Bind.This ResultSetFutureMixin resultSetFuture) {

            return onBeforeCommon(resultSetFuture);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable ResultSetMixin resultSet,
                @Bind.This ResultSetFutureMixin resultSetFuture) {

            onReturnCommon(resultSet, resultSetFuture);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            onAfterCommon(timer);
        }
    }

    // waiting on async result
    @Advice.Pointcut(className = "com.datastax.driver.core.ResultSetFuture",
                     methodName = "getUninterruptibly",
                     methodParameterTypes = {".."})
    public static class FutureGetUninterruptiblyAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(
                @Bind.This ResultSetFutureMixin resultSetFuture) {

            return onBeforeCommon(resultSetFuture);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable ResultSetMixin resultSet,
                @Bind.This ResultSetFutureMixin resultSetFuture) {

            onReturnCommon(resultSet, resultSetFuture);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            onAfterCommon(timer);
        }
    }

    @Advice.Pointcut(className = "com.google.common.util.concurrent.AbstractFuture",
                     subTypeRestriction = "com.datastax.driver.core.DefaultResultSetFuture",
                     methodName = "setException",
                     methodParameterTypes = {"java.lang.Throwable"})
    public static class FutureSetExceptionAdvice {

        // using @Advice.OnMethodBefore instead of @Advice.OnMethodExit to ensure that the async
        // span
        // is ended prior to an overall transaction that may be waiting on this future has a chance
        // to end
        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.This ResultSetFutureMixin resultSetFuture,
                @Bind.Argument(0) @Nullable Throwable t) {

            if (t == null) {
                return;
            }
            // to prevent race condition, setting completed/exception status before getting async
            // query entry, and the converse is done when setting async query entry
            // ok if end() happens to get called twice
            resultSetFuture.glowroot$setCompleted();
            resultSetFuture.glowroot$setException(t);
            AsyncQuerySpan asyncQuerySpan = resultSetFuture.glowroot$getAsyncQuerySpan();
            if (asyncQuerySpan != null) {
                asyncQuerySpan.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(className = "com.google.common.util.concurrent.AbstractFuture",
                     subTypeRestriction = "com.datastax.driver.core.DefaultResultSetFuture",
                     methodName = "set",
                     methodParameterTypes = {"java.lang.Object"})
    public static class FutureSetAdvice {

        // using @Advice.OnMethodBefore instead of @Advice.OnMethodExit to ensure that the async
        // span
        // is ended prior to an overall transaction that may be waiting on this future has a chance
        // to end
        @Advice.OnMethodBefore
        public static void onBefore(@Bind.This ResultSetFutureMixin resultSetFuture) {

            // to prevent race condition, setting completed status before getting async query entry,
            // and the converse is done when setting async query entry
            // ok if end() happens to get called twice
            resultSetFuture.glowroot$setCompleted();
            AsyncQuerySpan asyncQuerySpan = resultSetFuture.glowroot$getAsyncQuerySpan();
            if (asyncQuerySpan != null) {
                asyncQuerySpan.end();
            }
        }
    }

    private static @Nullable Timer onBeforeCommon(ResultSetFutureMixin resultSetFuture) {

        AsyncQuerySpan asyncQuerySpan = resultSetFuture.glowroot$getAsyncQuerySpan();
        if (asyncQuerySpan == null) {
            return null;
        } else {
            return asyncQuerySpan.extendSyncTimer();
        }
    }

    private static void onReturnCommon(@Nullable ResultSetMixin resultSet,
            ResultSetFutureMixin resultSetFuture) {

        if (resultSet == null) {
            return;
        }
        // pass query entry to the result set so it can be used when iterating the result set
        AsyncQuerySpan asyncQuerySpan = resultSetFuture.glowroot$getAsyncQuerySpan();
        resultSet.glowroot$setQuerySpan(asyncQuerySpan);
    }

    private static void onAfterCommon(@Nullable Timer timer) {

        if (timer != null) {
            timer.stop();
        }
    }
}
