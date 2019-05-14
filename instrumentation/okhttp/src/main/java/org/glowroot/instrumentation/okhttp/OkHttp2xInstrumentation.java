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

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

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
import org.glowroot.instrumentation.okhttp.boot.CallInvoker;
import org.glowroot.instrumentation.okhttp.boot.HttpRequestMessageSupplier;
import org.glowroot.instrumentation.okhttp.boot.Util;

public class OkHttp2xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<Map<String, String>> SETTER = new SetterImpl();

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("com.squareup.okhttp.Request")
    public static class RequestMixinImpl implements RequestMixin {

        private transient volatile @Nullable Map<String, String> glowroot$extraHeaders;

        @Override
        public @Nullable Map<String, String> glowroot$getExtraHeaders() {
            return glowroot$extraHeaders;
        }

        @Override
        public void glowroot$setExtraHeaders(Map<String, String> extraHeaders) {
            this.glowroot$extraHeaders = extraHeaders;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface RequestMixin {

        @Nullable
        Map<String, String> glowroot$getExtraHeaders();

        void glowroot$setExtraHeaders(Map<String, String> extraHeaders);
    }

    @Advice.Pointcut(className = "com.squareup.okhttp.OkHttpClient",
                     methodName = "newCall",
                     methodParameterTypes = {"com.squareup.okhttp.Request"})
    public static class NewCallAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.Argument(0) ParameterHolder<Request> requestHolder) {

            Request request = requestHolder.get();
            if (request != null) {
                // need to replace possibly shared request with a non-shared request instance so
                // that headers can be stored during execute below
                requestHolder.set(request.newBuilder().build());
            }
        }
    }

    @Advice.Pointcut(className = "com.squareup.okhttp.Call",
                     methodName = "execute",
                     methodParameterTypes = {},
                     nestingGroup = "http-client")
    public static class ExecuteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.This Object call,
                @Bind.ClassMeta CallInvoker callInvoker,
                ThreadContext context) {

            Request originalRequest = (Request) callInvoker.getOriginalRequest(call);
            if (originalRequest == null) {
                return null;
            }
            // can't use normal cast here because Request is final and compiler doesn't know we've
            // mixed in the interface at runtime
            RequestMixin requestMixin = RequestMixin.class.cast(originalRequest);
            Map<String, String> extraHeaders = new HashMap<String, String>(4);
            requestMixin.glowroot$setExtraHeaders(extraHeaders);
            return Util.startOutgoingSpan(context, originalRequest.method(),
                    originalRequest.urlString(), SETTER, extraHeaders, TIMER_NAME);
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

    @Advice.Pointcut(className = "com.squareup.okhttp.Call",
                     methodName = "enqueue",
                     methodParameterTypes = {"com.squareup.okhttp.Callback"},
                     nestingGroup = "http-client")
    public static class EnqueueAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.This Object call,
                @Bind.Argument(0) ParameterHolder<Callback> callback,
                @Bind.ClassMeta CallInvoker callInvoker,
                ThreadContext context) {

            Request originalRequest = (Request) callInvoker.getOriginalRequest(call);
            if (originalRequest == null) {
                return null;
            }
            // can't use normal cast here because Request is final and compiler doesn't know we've
            // mixed in the interface at runtime
            RequestMixin requestMixin = RequestMixin.class.cast(originalRequest);
            Map<String, String> extraHeaders = new HashMap<String, String>(4);
            requestMixin.glowroot$setExtraHeaders(extraHeaders);
            AsyncSpan span = Util.startAsyncOutgoingSpan(context, originalRequest.method(),
                    originalRequest.urlString(), SETTER, extraHeaders, TIMER_NAME);
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

    @Advice.Pointcut(className = "com.squareup.okhttp.Call",
                     methodName = "getResponse",
                     methodParameterTypes = {"com.squareup.okhttp.Request", "boolean"})
    public static class PropagateAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.Argument(0) ParameterHolder<Request> requestHolder) {

            Request request = requestHolder.get();
            if (request == null) {
                return;
            }
            RequestMixin requestMixin = RequestMixin.class.cast(request);
            Map<String, String> extraHeaders = requestMixin.glowroot$getExtraHeaders();
            if (extraHeaders == null) {
                return;
            }
            Request.Builder builder = request.newBuilder();
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
            requestHolder.set(builder.build());
        }
    }

    private static Callback createWrapper(ParameterHolder<Callback> callback, AsyncSpan span,
            ThreadContext context) {

        Callback delegate = callback.get();
        if (delegate == null) {
            return new OkHttp2xCallbackWrapperForNullDelegate(span);
        } else {
            return new OkHttp2xCallbackWrapper(delegate, span, context.createAuxThreadContext());
        }
    }

    private static class SetterImpl implements Setter<Map<String, String>> {

        @Override
        public void put(Map<String, String> carrier, String key, String value) {
            carrier.put(key, value);
        }
    }
}
