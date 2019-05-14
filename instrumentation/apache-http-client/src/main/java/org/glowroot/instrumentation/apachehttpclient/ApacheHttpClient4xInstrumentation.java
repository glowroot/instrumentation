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
package org.glowroot.instrumentation.apachehttpclient;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;

import org.glowroot.instrumentation.apachehttpclient.boot.HttpRequestMessageSupplier;
import org.glowroot.instrumentation.apachehttpclient.boot.Util;
import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

// see nearly identical copy of this in WiremockApacheHttpClientInstrumentation
public class ApacheHttpClient4xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<HttpUriRequest> URI_REQUEST_SETTER = new HttpUriRequestSetter();

    private static final Setter<HttpRequest> REQUEST_SETTER = new HttpRequestSetter();

    private static final Getter<HttpResponse> GETTER = new HttpResponseGetter();

    @Advice.Pointcut(className = "org.apache.http.client.HttpClient",
                     methodName = "execute",
                     methodParameterTypes = {"org.apache.http.client.methods.HttpUriRequest", ".."},
                     nestingGroup = "http-client")
    public static class ExecuteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.Argument(0) @Nullable HttpUriRequest request,
                ThreadContext context) {

            if (request == null) {
                return null;
            }
            return Util.startOutgoingSpan(context, request.getMethod(), null,
                    request.getURI().toString(), URI_REQUEST_SETTER, request, TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable HttpResponse response,
                @Bind.Enter @Nullable Span span) {

            onReturnCommon(response, span);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            onThrowCommon(t, span);
        }
    }

    @Advice.Pointcut(className = "org.apache.http.client.HttpClient",
                     methodName = "execute",
                     methodParameterTypes = {"org.apache.http.HttpHost",
                             "org.apache.http.HttpRequest",
                             ".."},
                     nestingGroup = "http-client")
    public static class ExecuteWithHostAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.Argument(0) @Nullable HttpHost hostObj,
                @Bind.Argument(1) @Nullable HttpRequest request,
                ThreadContext context) {

            if (request == null) {
                return null;
            }
            RequestLine requestLine = request.getRequestLine();
            return Util.startOutgoingSpan(context, requestLine.getMethod(),
                    hostObj == null ? null : hostObj.toURI(), requestLine.getUri(),
                    REQUEST_SETTER, request, TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable HttpResponse response,
                @Bind.Enter @Nullable Span span) {

            onReturnCommon(response, span);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            onThrowCommon(t, span);
        }
    }

    @SuppressWarnings("deprecation")
    private static void onReturnCommon(@Nullable HttpResponse response, @Nullable Span span) {
        if (span != null) {
            if (response != null) {
                StatusLine statusLine = response.getStatusLine();
                HttpRequestMessageSupplier supplier =
                        (HttpRequestMessageSupplier) span.getMessageSupplier();
                if (statusLine != null && supplier != null) {
                    supplier.setStatusCode(statusLine.getStatusCode());
                }
                span.extractFromResponse(response, GETTER);
            }
            span.end();
        }
    }

    private static void onThrowCommon(Throwable t, @Nullable Span span) {
        if (span != null) {
            span.endWithError(t);
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

    private static class HttpResponseGetter implements Getter<HttpResponse> {

        @Override
        public @Nullable String get(HttpResponse carrier, String key) {
            Header header = carrier.getFirstHeader(key);
            if (header == null) {
                return null;
            } else {
                return header.getValue();
            }
        }
    }
}
