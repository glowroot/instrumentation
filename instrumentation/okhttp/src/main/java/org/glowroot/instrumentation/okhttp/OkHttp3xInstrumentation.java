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
package org.glowroot.instrumentation.okhttp;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.ParameterHolder;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.okhttp.boot.HttpRequestMessageSupplier;
import org.glowroot.instrumentation.okhttp.boot.Util;

public class OkHttp3xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<Map<String, String>> SETTER = new SetterImpl();

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("okhttp3.Request")
    public static class RequestMixinImpl implements RequestMixin {

        private transient volatile @Nullable Map<String, String> glowroot$extraHeaders;

        @Override
        public @Nullable Map<String, String> glowroot$getExtraHeaders() {
            return glowroot$extraHeaders;
        }

        @Override
        public void glowroot$setExtraHeaders(@Nullable Map<String, String> extraHeaders) {
            this.glowroot$extraHeaders = extraHeaders;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface RequestMixin {

        @Nullable
        Map<String, String> glowroot$getExtraHeaders();

        void glowroot$setExtraHeaders(@Nullable Map<String, String> extraHeaders);
    }

    @Advice.Pointcut(className = "okhttp3.OkHttpClient",
                     methodName = "newCall",
                     methodParameterTypes = {"okhttp3.Request"})
    public static class NewCallAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) ParameterHolder<Request> requestHolder) {

            Request request = requestHolder.get();
            if (request != null) {
                // need to replace possibly shared request with a non-shared request instance so
                // that headers can be stored during execute below
                requestHolder.set(request.newBuilder().build());
            }
        }
    }

    @Advice.Pointcut(className = "okhttp3.Call",
                     methodName = "execute",
                     methodParameterTypes = {},
                     nestingGroup = "http-client")
    public static class ExecuteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.This Call call,
                ThreadContext context) {

            Request request = call.request();
            if (request == null) {
                return null;
            }
            String url;
            HttpUrl httpUrl = request.url();
            if (httpUrl == null) {
                url = "";
            } else {
                url = httpUrl.toString();
            }
            // can't use normal cast here because Request is final and compiler doesn't know we've
            // mixed in the interface at runtime
            RequestMixin requestMixin = RequestMixin.class.cast(request);
            Map<String, String> extraHeaders = new HashMap<String, String>(4);
            requestMixin.glowroot$setExtraHeaders(extraHeaders);
            return Util.startOutgoingSpan(context, request.method(), url, SETTER, extraHeaders,
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return Response response,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                HttpRequestMessageSupplier supplier =
                        (HttpRequestMessageSupplier) span.getMessageSupplier();
                if (supplier != null) {
                    supplier.setStatusCode(response.code());
                }
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(className = "okhttp3.Call",
                     methodName = "enqueue",
                     methodParameterTypes = {"okhttp3.Callback"},
                     nestingGroup = "http-client")
    public static class EnqueueAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.This Call call,
                @Bind.Argument(0) ParameterHolder<Callback> callback,
                ThreadContext context) {

            Request request = call.request();
            if (request == null) {
                return null;
            }
            if (callback == null) {
                return null;
            }
            String url;
            HttpUrl httpUrl = request.url();
            if (httpUrl == null) {
                url = "";
            } else {
                url = httpUrl.toString();
            }
            // can't use normal cast here because Request is final and compiler doesn't know we've
            // mixed in the interface at runtime
            RequestMixin requestMixin = RequestMixin.class.cast(request);
            Map<String, String> extraHeaders = new HashMap<String, String>(4);
            requestMixin.glowroot$setExtraHeaders(extraHeaders);
            AsyncSpan span = Util.startAsyncOutgoingSpan(context, request.method(), url,
                    SETTER, extraHeaders, TIMER_NAME);
            callback.set(createWrapper(callback, span, context));
            return span;
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable AsyncSpan span) {

            if (span != null) {
                span.stopSyncTimer();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable AsyncSpan span) {

            if (span != null) {
                span.stopSyncTimer();
                span.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(
                     className = "okhttp3.internal.http.RealInterceptorChain"
                             + "|okhttp3.RealCall$ApplicationInterceptorChain",
                     methodName = "proceed",
                     methodParameterTypes = {"okhttp3.Request"})
    public static class PropagateAdvice {

        // cannot rely on ThreadContext here because one thread can pass multiple enqueued Calls to
        // the executor (and conversely some threads may then pass none to the executor)
        // see okhttp3.Dispatcher.enqueue(AsyncCall), also see brave.okhttp3.TracingCallFactory
        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.This Interceptor.Chain chain,
                @Bind.Argument(0) ParameterHolder<Request> requestHolder) {

            // can't use normal cast here because Request is final and compiler doesn't know we've
            // mixed in the interface at runtime
            RequestMixin requestMixin = RequestMixin.class.cast(chain.request());
            Map<String, String> extraHeaders = requestMixin.glowroot$getExtraHeaders();
            if (extraHeaders == null) {
                return;
            }
            Request request = requestHolder.get();
            if (request == null) {
                return;
            }
            Request.Builder builder = request.newBuilder();
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
            requestHolder.set(builder.build());
            requestMixin.glowroot$setExtraHeaders(null);
        }
    }

    private static Callback createWrapper(ParameterHolder<Callback> callback, AsyncSpan span,
            ThreadContext context) {

        Callback delegate = callback.get();
        if (delegate == null) {
            return new OkHttp3xCallbackWrapperForNullDelegate(span);
        } else {
            return new OkHttp3xCallbackWrapper(delegate, span, context.createAuxThreadContext());
        }
    }

    private static class SetterImpl implements Setter<Map<String, String>> {

        @Override
        public void put(Map<String, String> carrier, String key, String value) {
            carrier.put(key, value);
        }
    }
}
