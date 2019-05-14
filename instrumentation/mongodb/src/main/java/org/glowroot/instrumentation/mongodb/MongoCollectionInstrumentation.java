/*
 * Copyright 2018-2019 the original author or authors.
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

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.mongodb.MongoIterableInstrumentation.MongoIterableMixin;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// modern driver API (available since 3.0.0)
public class MongoCollectionInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("mongodb query");

    private static final String QUERY_TYPE = "MongoDB";

    private static final ConfigService configService = Agent.getConfigService("mongodb");

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

    @Shim("com.mongodb.client.MongoCollection")
    public interface MongoCollection {

        @Shim("com.mongodb.MongoNamespace getNamespace()")
        @Nullable
        Object getNamespace();
    }

    // TODO add MongoCollection.watch()
    @Advice.Pointcut(className = "com.mongodb.client.MongoCollection",
                     methodName = "count*|distinct|findOneAnd*|mapReduce|bulkWrite|insert*|delete*"
                             + "|replace|update*|drop*|create*|list*|rename*",
                     methodParameterTypes = {".."},
                     nestingGroup = "mongodb")
    public static class MongoCollectionAdvice {

        @Advice.OnMethodBefore
        public static @Nullable QuerySpan onBefore(
                @Bind.This MongoCollection collection,
                @Bind.MethodName String methodName,
                ThreadContext context) {

            Object namespace = collection.getNamespace();
            if (namespace == null) {
                return null;
            }
            String queryText = methodName + " " + namespace.toString();
            // TODO capture dest
            return context.startQuerySpan(QUERY_TYPE, "", queryText, QueryMessageSupplier.create(),
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
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

    @Advice.Pointcut(className = "com.mongodb.client.MongoCollection",
                     methodName = "find|aggregate",
                     methodParameterTypes = {".."},
                     nestingGroup = "mongodb")
    public static class MongoFindAdvice {

        @Advice.OnMethodBefore
        public static @Nullable QuerySpan onBefore(
                @Bind.This MongoCollection collection,
                @Bind.MethodName String methodName,
                ThreadContext context) {

            Object namespace = collection.getNamespace();
            if (namespace == null) {
                return null;
            }
            String queryText = methodName + " " + namespace.toString();
            // TODO capture dest
            return context.startQuerySpan(QUERY_TYPE, "", queryText, QueryMessageSupplier.create(),
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return MongoIterableMixin mongoIterable,
                @Bind.Enter @Nullable QuerySpan querySpan) {

            if (querySpan != null) {
                if (mongoIterable != null) {
                    mongoIterable.glowroot$setQuerySpan(querySpan);
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
}
