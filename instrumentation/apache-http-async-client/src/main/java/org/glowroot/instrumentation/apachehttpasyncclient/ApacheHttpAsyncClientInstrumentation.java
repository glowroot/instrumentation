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
package org.glowroot.instrumentation.apachehttpasyncclient;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;

import org.glowroot.instrumentation.apachehttpasyncclient.boot.Util;
import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.ParameterHolder;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class ApacheHttpAsyncClientInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<HttpUriRequest> URI_REQUEST_SETTER = new HttpUriRequestSetter();

    private static final Setter<HttpRequest> REQUEST_SETTER = new HttpRequestSetter();

    @Advice.Pointcut(className = "org.apache.http.nio.client.HttpAsyncClient",
                     methodName = "execute",
                     methodParameterTypes = {"org.apache.http.client.methods.HttpUriRequest",
                             "org.apache.http.concurrent.FutureCallback"},
                     nestingGroup = "http-client")
    public static class ExecuteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.Argument(0) @Nullable HttpUriRequest request,
                @Bind.Argument(1) ParameterHolder<FutureCallback<HttpResponse>> callback,
                ThreadContext context) {

            return onBeforeCommon(request, callback, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable AsyncSpan span) {

            onReturnCommon(span);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable AsyncSpan span) {

            onThrowCommon(t, span);
        }
    }

    @Advice.Pointcut(className = "org.apache.http.nio.client.HttpAsyncClient",
                     methodName = "execute",
                     methodParameterTypes = {"org.apache.http.client.methods.HttpUriRequest",
                             "org.apache.http.protocol.HttpContext",
                             "org.apache.http.concurrent.FutureCallback"},
                     nestingGroup = "http-client")
    public static class ExecuteAdvice2 {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.Argument(0) @Nullable HttpUriRequest request,
                @Bind.Argument(2) ParameterHolder<FutureCallback<HttpResponse>> callback,
                ThreadContext context) {

            return onBeforeCommon(request, callback, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable AsyncSpan span) {

            onReturnCommon(span);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable AsyncSpan span) {

            onThrowCommon(t, span);
        }
    }

    @Advice.Pointcut(className = "org.apache.http.nio.client.HttpAsyncClient",
                     methodName = "execute",
                     methodParameterTypes = {"org.apache.http.HttpHost",
                             "org.apache.http.HttpRequest",
                             "org.apache.http.concurrent.FutureCallback"},
                     nestingGroup = "http-client")
    public static class ExecuteWithHostAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.Argument(0) @Nullable HttpHost hostObj,
                @Bind.Argument(1) @Nullable HttpRequest request,
                @Bind.Argument(2) ParameterHolder<FutureCallback<HttpResponse>> callback,
                ThreadContext context) {

            return onBeforeCommon(hostObj, request, callback, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable AsyncSpan span) {

            onReturnCommon(span);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable AsyncSpan span) {

            onThrowCommon(t, span);
        }
    }

    @Advice.Pointcut(className = "org.apache.http.nio.client.HttpAsyncClient",
                     methodName = "execute",
                     methodParameterTypes = {"org.apache.http.HttpHost",
                             "org.apache.http.HttpRequest",
                             "org.apache.http.protocol.HttpContext",
                             "org.apache.http.concurrent.FutureCallback"},
                     nestingGroup = "http-client")
    public static class ExecuteWithHostAdvice2 {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.Argument(0) @Nullable HttpHost hostObj,
                @Bind.Argument(1) @Nullable HttpRequest request,
                @Bind.Argument(3) ParameterHolder<FutureCallback<HttpResponse>> callback,
                ThreadContext context) {

            return onBeforeCommon(hostObj, request, callback, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable AsyncSpan span) {

            onReturnCommon(span);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable AsyncSpan span) {

            onThrowCommon(t, span);
        }
    }

    @Advice.Pointcut(className = "org.apache.http.nio.client.HttpAsyncClient",
                     methodName = "execute",
                     methodParameterTypes = {
                             "org.apache.http.nio.protocol.HttpAsyncRequestProducer",
                             "org.apache.http.nio.protocol.HttpAsyncResponseConsumer",
                             "org.apache.http.concurrent.FutureCallback"},
                     nestingGroup = "http-client")
    public static class ExecuteWithProducerConsumerAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(2) ParameterHolder<FutureCallback<HttpResponse>> callback,
                ThreadContext context) {

            onBeforeOther(callback, context);
        }
    }

    @Advice.Pointcut(className = "org.apache.http.nio.client.HttpAsyncClient",
                     methodName = "execute",
                     methodParameterTypes = {
                             "org.apache.http.nio.protocol.HttpAsyncRequestProducer",
                             "org.apache.http.nio.protocol.HttpAsyncResponseConsumer",
                             "org.apache.http.protocol.HttpContext",
                             "org.apache.http.concurrent.FutureCallback"},
                     nestingGroup = "http-client")
    public static class ExecuteWithProducerConsumerAdvice2 {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(3) ParameterHolder<FutureCallback<HttpResponse>> callback,
                ThreadContext context) {

            onBeforeOther(callback, context);
        }
    }

    private static @Nullable AsyncSpan onBeforeCommon(@Nullable HttpUriRequest request,
            ParameterHolder<FutureCallback<HttpResponse>> callback, ThreadContext context) {

        if (request == null) {
            return null;
        }
        AsyncSpan span = Util.startAsyncOutgoingSpan(context, request.getMethod(), null,
                request.getURI().toString(), URI_REQUEST_SETTER, request, TIMER_NAME);
        callback.set(createWrapper(callback, span, context));
        return span;
    }

    private static @Nullable AsyncSpan onBeforeCommon(@Nullable HttpHost hostObj,
            @Nullable HttpRequest request, ParameterHolder<FutureCallback<HttpResponse>> callback,
            ThreadContext context) {

        if (request == null) {
            return null;
        }
        RequestLine requestLine = request.getRequestLine();
        if (requestLine == null) {
            return null;
        }
        AsyncSpan span = Util.startAsyncOutgoingSpan(context, requestLine.getMethod(),
                hostObj == null ? null : hostObj.toURI(), requestLine.getUri(),
                REQUEST_SETTER, request, TIMER_NAME);
        callback.set(createWrapper(callback, span, context));
        return span;
    }

    private static void onBeforeOther(ParameterHolder<FutureCallback<HttpResponse>> callback,
            ThreadContext context) {

        FutureCallback<HttpResponse> delegate = callback.get();
        if (delegate != null) {
            callback.set(new FutureCallbackWithoutEntryWrapper<HttpResponse>(delegate,
                    context.createAuxThreadContext()));
        }
    }

    private static void onReturnCommon(@Nullable AsyncSpan span) {

        if (span != null) {
            span.stopSyncTimer();
        }
    }

    private static void onThrowCommon(Throwable t, @Nullable AsyncSpan span) {

        if (span != null) {
            span.stopSyncTimer();
            span.endWithError(t);
        }
    }

    private static FutureCallback<HttpResponse> createWrapper(
            ParameterHolder<FutureCallback<HttpResponse>> callback, AsyncSpan span,
            ThreadContext context) {

        FutureCallback<HttpResponse> delegate = callback.get();
        if (delegate == null) {
            return new FutureCallbackWrapperForNullDelegate(span);
        } else {
            return new FutureCallbackWrapper(delegate, span, context.createAuxThreadContext());
        }
    }

    private static class HttpUriRequestSetter implements Setter<HttpUriRequest> {

        @Override
        public void put(HttpUriRequest carrier, String key, String value) {

            carrier.setHeader(key, value);
        }
    }

    private static class HttpRequestSetter implements Setter<HttpRequest> {

        @Override
        public void put(HttpRequest carrier, String key, String value) {

            carrier.setHeader(key, value);
        }
    }
}
