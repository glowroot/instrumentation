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
package org.glowroot.instrumentation.elasticsearch;

import org.glowroot.instrumentation.api.AsyncQuerySpan;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;

public class ActionFutureInstrumentation {

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("org.elasticsearch.action.ActionFuture")
    public static class ActionFutureImpl implements ActionFutureMixin {

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
    public interface ActionFutureMixin {

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
                     subTypeRestriction = "org.elasticsearch.action.ActionFuture",
                     methodName = "get",
                     methodParameterTypes = {".."},
                     suppressionKey = "wait-on-future")
    public static class FutureGetAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This ActionFutureMixin actionFuture) {

            AsyncQuerySpan asyncQuerySpan = actionFuture.glowroot$getAsyncQuerySpan();
            if (asyncQuerySpan == null) {
                return null;
            } else {
                return asyncQuerySpan.extendSyncTimer();
            }
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    @Advice.Pointcut(className = "org.elasticsearch.common.util.concurrent.BaseFuture",
                     subTypeRestriction = "org.elasticsearch.action.ActionFuture",
                     methodName = "setException",
                     methodParameterTypes = {"java.lang.Throwable"})
    public static class FutureSetExceptionAdvice {

        // using @Advice.OnMethodBefore instead of @Advice.OnMethodExit to ensure that the async
        // span
        // is ended prior to an overall transaction that may be waiting on this future has a chance
        // to end
        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.This ActionFutureMixin actionFuture,
                @Bind.Argument(0) @Nullable Throwable t) {

            if (t == null) {
                return;
            }
            // to prevent race condition, setting completed/exception status before getting async
            // query entry, and the converse is done when setting async query entry
            // ok if end() happens to get called twice
            actionFuture.glowroot$setCompleted();
            actionFuture.glowroot$setException(t);
            AsyncQuerySpan asyncQuerySpan = actionFuture.glowroot$getAsyncQuerySpan();
            if (asyncQuerySpan != null) {
                asyncQuerySpan.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(className = "org.elasticsearch.common.util.concurrent.BaseFuture",
                     subTypeRestriction = "org.elasticsearch.action.ActionFuture",
                     methodName = "set",
                     methodParameterTypes = {"java.lang.Object"})
    public static class FutureSetAdvice {

        // using @Advice.OnMethodBefore instead of @Advice.OnMethodExit to ensure that the async
        // span
        // is ended prior to an overall transaction that may be waiting on this future has a chance
        // to end
        @Advice.OnMethodBefore
        public static void onBefore(@Bind.This ActionFutureMixin actionFuture) {

            // to prevent race condition, setting completed status before getting async query entry,
            // and the converse is done when setting async query entry
            // ok if end() happens to get called twice
            actionFuture.glowroot$setCompleted();
            AsyncQuerySpan asyncQuerySpan = actionFuture.glowroot$getAsyncQuerySpan();
            if (asyncQuerySpan != null) {
                asyncQuerySpan.end();
            }
        }
    }
}
