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

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;

import org.glowroot.instrumentation.apachehttpclient.boot.HttpRequestMessageSupplier;
import org.glowroot.instrumentation.apachehttpclient.boot.Util;
import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class ApacheHttpClient3xInstrumentation {

    private static final Logger logger = Logger.getLogger(ApacheHttpClient3xInstrumentation.class);

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<HttpMethod> SETTER = new SetterImpl();

    private static final Getter<HttpMethod> GETTER = new GetterImpl();

    @Advice.Pointcut(className = "org.apache.commons.httpclient.HttpClient",
                     methodName = "executeMethod",
                     methodParameterTypes = {"org.apache.commons.httpclient.HostConfiguration",
                             "org.apache.commons.httpclient.HttpMethod",
                             "org.apache.commons.httpclient.HttpState"},
                     nestingGroup = "http-client")
    public static class ExecuteMethodAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.Argument(1) @Nullable HttpMethod requestResponse,
                ThreadContext context) {

            if (requestResponse == null) {
                return null;
            }
            URI uriObj;
            try {
                uriObj = requestResponse.getURI();
            } catch (URIException e) {
                logger.debug(e.getMessage(), e);
                uriObj = null;
            }
            String uri = uriObj == null ? null : uriObj.toString();
            return Util.startOutgoingSpan(context, requestResponse.getName(), null, uri, SETTER,
                    requestResponse, TIMER_NAME);
        }

        @Advice.OnMethodReturn
        @SuppressWarnings("deprecation")
        public static void onReturn(
                @Bind.Return int statusCode,
                @Bind.Enter @Nullable Span span,
                @Bind.Argument(1) HttpMethod requestResponse) {

            if (span != null) {
                HttpRequestMessageSupplier supplier =
                        (HttpRequestMessageSupplier) span.getMessageSupplier();
                if (supplier != null) {
                    supplier.setStatusCode(statusCode);
                }
                span.extractFromResponse(requestResponse, GETTER);
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    private static class SetterImpl implements Setter<HttpMethod> {

        @Override
        public void put(HttpMethod carrier, String key, String value) {
            carrier.setRequestHeader(key, value);
        }
    }

    private static class GetterImpl implements Getter<HttpMethod> {

        @Override
        public @Nullable String get(HttpMethod carrier, String key) {
            Header header = carrier.getResponseHeader(key);
            if (header == null) {
                return null;
            } else {
                return header.getValue();
            }
        }
    }
}
