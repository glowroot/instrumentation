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
import org.glowroot.instrumentation.mongodb.MongoCursorInstrumentation.MongoCursorMixin;

public class MongoIterableInstrumentation {

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"com.mongodb.client.FindIterable", "com.mongodb.client.AggregateIterable"})
    public static class MongoIterableImpl implements MongoIterableMixin {

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
    public interface MongoIterableMixin {

        @Nullable
        QuerySpan glowroot$getQuerySpan();

        void glowroot$setQuerySpan(@Nullable QuerySpan querySpan);
    }

    @Advice.Pointcut(className = "com.mongodb.client.FindIterable",
                     methodName = "*",
                     methodParameterTypes = {".."},
                     methodReturnType = "com.mongodb.client.FindIterable")
    public static class MapAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable MongoIterableMixin newMongoIterable,
                @Bind.This MongoIterableMixin mongoIterable) {

            if (newMongoIterable != null) {
                newMongoIterable.glowroot$setQuerySpan(mongoIterable.glowroot$getQuerySpan());
            }
        }
    }

    @Advice.Pointcut(className = "com.mongodb.client.MongoIterable",
                     subTypeRestriction = "com.mongodb.client.FindIterable"
                             + "|com.mongodb.client.AggregateIterable",
                     methodName = "first",
                     methodParameterTypes = {},
                     nestingGroup = "mongodb")
    public static class FirstAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This MongoIterableMixin mongoIterable) {

            QuerySpan querySpan = mongoIterable.glowroot$getQuerySpan();
            return querySpan == null ? null : querySpan.extend();
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object document,
                @Bind.This MongoIterableMixin mongoIterable) {

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

    @Advice.Pointcut(className = "com.mongodb.client.MongoIterable",
                     subTypeRestriction = "com.mongodb.client.FindIterable"
                             + "|com.mongodb.client.AggregateIterable",
                     methodName = "iterator",
                     methodParameterTypes = {},
                     methodReturnType = "com.mongodb.client.MongoCursor",
                     nestingGroup = "mongodb")
    public static class IteratorAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable MongoCursorMixin mongoCursor,
                @Bind.This MongoIterableMixin mongoIterable) {

            if (mongoCursor != null) {
                mongoCursor.glowroot$setQuerySpan(mongoIterable.glowroot$getQuerySpan());
            }
        }
    }
}
