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

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AsyncQuerySpan;
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
import org.glowroot.instrumentation.elasticsearch.ActionFutureInstrumentation.ActionFutureMixin;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ActionRequestBuilderInstrumentation {

    private static final String QUERY_TYPE = "Elasticsearch";

    private static final TimerName TIMER_NAME = Agent.getTimerName("elasticsearch query");

    private static final ConfigService configService = Agent.getConfigService("elasticsearch");

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

    @Shim("org.elasticsearch.action.ActionRequestBuilder")
    public interface ActionRequestBuilder {

        @Shim("org.elasticsearch.action.ActionRequest request()")
        @Nullable
        ActionRequest glowroot$request();
    }

    @Shim("org.elasticsearch.action.search.SearchRequestBuilder")
    public interface SearchRequestBuilder {

        @Shim("org.elasticsearch.search.builder.SearchSourceBuilder sourceBuilder()")
        @Nullable
        Object glowroot$sourceBuilder();
    }

    @Shim("org.elasticsearch.action.ActionRequest")
    public interface ActionRequest {}

    @Shim("org.elasticsearch.action.index.IndexRequest")
    public interface IndexRequest extends ActionRequest {

        @Nullable
        String index();

        @Nullable
        String type();
    }

    @Shim("org.elasticsearch.action.get.GetRequest")
    public interface GetRequest extends ActionRequest {

        @Nullable
        String index();

        @Nullable
        String type();

        @Nullable
        String id();
    }

    @Shim("org.elasticsearch.action.update.UpdateRequest")
    public interface UpdateRequest extends ActionRequest {

        @Nullable
        String index();

        @Nullable
        String type();

        @Nullable
        String id();
    }

    @Shim("org.elasticsearch.action.delete.DeleteRequest")
    public interface DeleteRequest extends ActionRequest {

        @Nullable
        String index();

        @Nullable
        String type();

        @Nullable
        String id();
    }

    @Shim("org.elasticsearch.action.search.SearchRequest")
    public interface SearchRequest extends ActionRequest {

        @Nullable
        String /*@Nullable*/ [] indices();

        @Nullable
        String /*@Nullable*/ [] types();
    }

    @Shim("org.elasticsearch.common.bytes.BytesReference")
    public interface BytesReference {

        @Nullable
        String toUtf8();
    }

    @Advice.Pointcut(className = "org.elasticsearch.action.ActionRequestBuilder",
                     methodName = "get",
                     methodParameterTypes = {},
                     nestingGroup = "elasticsearch",
                     suppressionKey = "wait-on-future")
    public static class ExecuteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable QuerySpan onBefore(
                @Bind.This ActionRequestBuilder actionRequestBuilder,
                ThreadContext context) {

            // TODO capture dest
            return context.startQuerySpan(QUERY_TYPE, "", getQueryText(actionRequestBuilder),
                    getQueryMessageSupplier(actionRequestBuilder), TIMER_NAME);
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

    @Advice.Pointcut(className = "org.elasticsearch.action.ActionRequestBuilder",
                     methodName = "execute",
                     methodParameterTypes = {},
                     nestingGroup = "elasticsearch")
    public static class ExecuteAsyncAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncQuerySpan onBefore(
                @Bind.This ActionRequestBuilder actionRequestBuilder,
                ThreadContext context) {

            // TODO capture dest
            return context.startAsyncQuerySpan(QUERY_TYPE, "", getQueryText(actionRequestBuilder),
                    getQueryMessageSupplier(actionRequestBuilder), TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable ActionFutureMixin future,
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
                // ListenableActionFuture completed really fast, prior to @Advice.OnMethodExit
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

    private static String getQueryText(ActionRequestBuilder actionRequestBuilder) {
        ActionRequest actionRequest = actionRequestBuilder.glowroot$request();
        if (actionRequest instanceof IndexRequest) {
            IndexRequest request = (IndexRequest) actionRequest;
            return "PUT " + request.index() + '/' + request.type();
        } else if (actionRequest instanceof GetRequest) {
            GetRequest request = (GetRequest) actionRequest;
            return "GET " + request.index() + '/' + request.type();
        } else if (actionRequest instanceof UpdateRequest) {
            UpdateRequest request = (UpdateRequest) actionRequest;
            return "PUT " + request.index() + '/' + request.type();
        } else if (actionRequest instanceof DeleteRequest) {
            DeleteRequest request = (DeleteRequest) actionRequest;
            return "DELETE " + request.index() + '/' + request.type();
        } else if (actionRequest instanceof SearchRequest) {
            SearchRequest request = (SearchRequest) actionRequest;
            return getQueryText(request, (SearchRequestBuilder) actionRequestBuilder);
        } else if (actionRequest == null) {
            return "(action request was null)";
        } else {
            return actionRequest.getClass().getName();
        }
    }

    private static String getQueryText(SearchRequest request,
            SearchRequestBuilder actionRequestBuilder) {
        StringBuilder sb = new StringBuilder("SEARCH ");
        @Nullable
        String[] indices = request.indices();
        @Nullable
        String[] types = request.types();
        if (indices != null && indices.length > 0) {
            if (types != null && types.length > 0) {
                appendTo(sb, indices);
                sb.append('/');
                appendTo(sb, types);
            } else {
                appendTo(sb, indices);
            }
        } else {
            if (types != null && types.length > 0) {
                sb.append("_any/");
                appendTo(sb, types);
            } else {
                sb.append('/');
            }
        }
        Object sourceBuilder = actionRequestBuilder.glowroot$sourceBuilder();
        if (sourceBuilder != null) {
            sb.append(' ');
            sb.append(sourceBuilder);
        }
        return sb.toString();
    }

    private static QueryMessageSupplier getQueryMessageSupplier(
            ActionRequestBuilder actionRequestBuilder) {
        ActionRequest actionRequest = actionRequestBuilder.glowroot$request();
        if (actionRequest instanceof IndexRequest) {
            return QueryMessageSupplier.create();
        } else if (actionRequest instanceof GetRequest) {
            GetRequest request = (GetRequest) actionRequest;
            return new QueryMessageSupplierWithId(request.id());
        } else if (actionRequest instanceof UpdateRequest) {
            UpdateRequest request = (UpdateRequest) actionRequest;
            return new QueryMessageSupplierWithId(request.id());
        } else if (actionRequest instanceof DeleteRequest) {
            DeleteRequest request = (DeleteRequest) actionRequest;
            return new QueryMessageSupplierWithId(request.id());
        } else if (actionRequest instanceof SearchRequest) {
            return QueryMessageSupplier.create();
        } else {
            return QueryMessageSupplier.create();
        }
    }

    private static void appendTo(StringBuilder sb, @Nullable String[] values) {
        boolean first = true;
        for (String value : values) {
            if (!first) {
                sb.append(',');
            }
            sb.append(value);
            first = false;
        }
    }
}
