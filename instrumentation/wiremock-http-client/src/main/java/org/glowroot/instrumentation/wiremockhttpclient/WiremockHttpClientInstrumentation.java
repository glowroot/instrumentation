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
package org.glowroot.instrumentation.wiremockhttpclient;

import java.net.URI;

import wiremock.org.apache.http.HttpHost;
import wiremock.org.apache.http.HttpRequest;
import wiremock.org.apache.http.RequestLine;
import wiremock.org.apache.http.client.methods.HttpUriRequest;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

//see nearly identical copy of this in ApacheHttpClient4xInstrumentation
public class WiremockHttpClientInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<HttpUriRequest> HTTP_URI_REQUEST_SETTER =
            new HttpUriRequestSetter();

    private static final Setter<HttpRequest> HTTP_REQUEST_SETTER = new HttpRequestSetter();

    @Advice.Pointcut(className = "wiremock.org.apache.http.client.HttpClient",
                     methodName = "execute",
                     methodParameterTypes = {
                             "wiremock.org.apache.http.client.methods.HttpUriRequest",
                             ".."},
                     nestingGroup = "http-client")
    public static class ExecuteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.Argument(0) @Nullable HttpUriRequest request,
                ThreadContext context) {

            if (request == null) {
                return null;
            }
            String method = request.getMethod();
            if (method == null) {
                method = "";
            } else {
                method += " ";
            }
            URI uriObj = request.getURI();
            String uri;
            if (uriObj == null) {
                uri = "";
            } else {
                uri = uriObj.toString();
            }
            return context.startOutgoingSpan("HTTP", method + stripQueryString(uri),
                    HTTP_URI_REQUEST_SETTER, request,
                    MessageSupplier.create("http client request: {}{}", method, uri), TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
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

    @Advice.Pointcut(className = "wiremock.org.apache.http.client.HttpClient",
                     methodName = "execute",
                     methodParameterTypes = {"wiremock.org.apache.http.HttpHost",
                             "wiremock.org.apache.http.HttpRequest", ".."},
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
            if (requestLine == null) {
                return null;
            }
            String method = requestLine.getMethod();
            if (method == null) {
                method = "";
            } else {
                method += " ";
            }
            String uri = requestLine.getUri();
            if (uri == null) {
                uri = "";
            }
            return context.startOutgoingSpan("HTTP", method + stripQueryString(uri),
                    HTTP_REQUEST_SETTER, request,
                    MessageSupplier.create("http client request: {}{}{}", method,
                            hostObj == null ? "" : hostObj.toURI(), uri),
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    private static String stripQueryString(String uri) {
        int index = uri.indexOf('?');
        return index == -1 ? uri : uri.substring(0, index);
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
