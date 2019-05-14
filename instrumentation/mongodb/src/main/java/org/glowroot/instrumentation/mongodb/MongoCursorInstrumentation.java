/*
 * Copyright 2019 the original author or authors.
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
package org.glowroot.instrumentation.mongodb;

import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;

public class MongoCursorInstrumentation {

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"com.mongodb.client.MongoCursor"})
    public static class MongoCursorImpl implements MongoCursorMixin {

        // does not need to be volatile, app/framework must provide visibility of MongoIterables if
        // used across threads and this can piggyback
        private transient @Nullable QuerySpan glowroot$querySpan;

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
    public interface MongoCursorMixin {

        @Nullable
        QuerySpan glowroot$getQuerySpan();

        void glowroot$setQuerySpan(@Nullable QuerySpan querySpan);
    }

    @Advice.Pointcut(className = "com.mongodb.client.MongoCursor",
                     methodName = "next|tryNext",
                     methodParameterTypes = {},
                     nestingGroup = "mongodb")
    public static class FirstAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This MongoCursorMixin mongoCursor) {

            QuerySpan querySpan = mongoCursor.glowroot$getQuerySpan();
            return querySpan == null ? null : querySpan.extend();
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object document,
                @Bind.This MongoCursorMixin mongoIterable) {

            QuerySpan querySpan = mongoIterable.glowroot$getQuerySpan();
            if (querySpan == null) {
                return;
            }
            if (document != null) {
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

    @Advice.Pointcut(className = "com.mongodb.client.MongoCursor",
                     methodName = "hasNext",
                     methodParameterTypes = {},
                     nestingGroup = "mongodb")
    public static class IsExhaustedAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This MongoCursorMixin mongoCursor) {

            QuerySpan querySpan = mongoCursor.glowroot$getQuerySpan();
            return querySpan == null ? null : querySpan.extend();
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This MongoCursorMixin mongoCursor) {

            QuerySpan querySpan = mongoCursor.glowroot$getQuerySpan();
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
