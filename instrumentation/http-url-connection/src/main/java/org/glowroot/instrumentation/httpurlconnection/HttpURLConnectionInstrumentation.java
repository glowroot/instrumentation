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
package org.glowroot.instrumentation.httpurlconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.httpurlconnection.boot.HttpRequestMessageSupplier;

public class HttpURLConnectionInstrumentation {

    private static final Logger logger = Logger.getLogger(HttpURLConnectionInstrumentation.class);

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client");

    private static final Setter<HttpURLConnection> SETTER = new SetterImpl();

    private static final AtomicBoolean inputStreamIssueLogged = new AtomicBoolean();
    private static final AtomicBoolean outputStreamIssueAlreadyLogged = new AtomicBoolean();

    private static final AtomicBoolean addRequestPropertyIssueLogged = new AtomicBoolean();

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"java.net.HttpURLConnection",
            "sun.net.www.protocol.http.HttpURLConnection$HttpInputStream",
            "sun.net.www.protocol.http.HttpURLConnection$StreamingOutputStream",
            "sun.net.www.http.PosterOutputStream",
            "weblogic.net.http.KeepAliveStream",
            "weblogic.utils.io.UnsyncByteArrayOutputStream"})
    public static class HasSpanImpl implements HasSpanMixin {

        private transient @Nullable Span glowroot$span;

        @Override
        public @Nullable Span glowroot$getSpan() {
            return glowroot$span;
        }

        @Override
        public void glowroot$setSpan(@Nullable Span span) {
            glowroot$span = span;
        }

        @Override
        public boolean glowroot$hasSpan() {
            return glowroot$span != null;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface HasSpanMixin {

        @Nullable
        Span glowroot$getSpan();

        void glowroot$setSpan(@Nullable Span span);

        boolean glowroot$hasSpan();
    }

    private static class SpanOrTimer {

        private final @Nullable Span span;
        private final @Nullable Timer timer;

        private SpanOrTimer(Span span) {
            this.span = span;
            timer = null;
        }

        private SpanOrTimer(Timer timer) {
            this.timer = timer;
            span = null;
        }

        private void onReturn() {
            if (span != null) {
                span.end();
            } else if (timer != null) {
                timer.stop();
            }
        }

        private void onThrow(Throwable t) {
            if (span != null) {
                span.endWithError(t);
            } else if (timer != null) {
                timer.stop();
            }
        }
    }

    @Advice.Pointcut(className = "java.net.URLConnection",
                     subTypeRestriction = "java.net.HttpURLConnection",
                     methodName = "connect",
                     methodParameterTypes = {},
                     nestingGroup = "http-client")
    public static class ConnectAdvice {

        @Advice.OnMethodBefore
        public static @Nullable SpanOrTimer onBefore(
                @Bind.This HttpURLConnection httpURLConnection,
                ThreadContext context) {

            return onBeforeCommon(httpURLConnection, false, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onReturnCommon(spanOrTimer);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onThrowCommon(spanOrTimer, t);
        }
    }

    @Advice.Pointcut(className = "java.net.URLConnection",
                     subTypeRestriction = "java.net.HttpURLConnection",
                     methodName = "getInputStream",
                     methodParameterTypes = {},
                     nestingGroup = "http-client")
    public static class GetInputStreamAdvice {

        @Advice.OnMethodBefore
        public static @Nullable SpanOrTimer onBefore(
                @Bind.This HttpURLConnection httpURLConnection,
                ThreadContext context) {

            return onBeforeCommon(httpURLConnection, false, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable InputStream returnValue,
                @Bind.This HttpURLConnection httpURLConnection,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            if (httpURLConnection instanceof HasSpanMixin) {
                if (returnValue instanceof HasSpanMixin) {
                    Span span = ((HasSpanMixin) httpURLConnection).glowroot$getSpan();
                    ((HasSpanMixin) returnValue).glowroot$setSpan(span);
                } else if (returnValue != null && !inputStreamIssueLogged.getAndSet(true)) {
                    logger.info("found non-instrumented http url connection input stream, please"
                            + " report to https://github.com/glowroot/instrumentation: {}",
                            returnValue.getClass().getName());
                }
            }

            onReturnCaptureResponseCode(httpURLConnection);
            onReturnCommon(spanOrTimer);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onThrowCommon(spanOrTimer, t);
        }
    }

    @Advice.Pointcut(className = "java.net.URLConnection",
                     subTypeRestriction = "java.net.HttpURLConnection",
                     methodName = "getOutputStream",
                     methodParameterTypes = {},
                     nestingGroup = "http-client")
    public static class GetOutputStreamAdvice {

        @Advice.OnMethodBefore
        public static @Nullable SpanOrTimer onBefore(
                @Bind.This HttpURLConnection httpURLConnection,
                ThreadContext context) {

            return onBeforeCommon(httpURLConnection, true, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable OutputStream returnValue,
                @Bind.This HttpURLConnection httpURLConnection,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            if (httpURLConnection instanceof HasSpanMixin) {
                if (returnValue instanceof HasSpanMixin) {
                    Span span = ((HasSpanMixin) httpURLConnection).glowroot$getSpan();
                    ((HasSpanMixin) returnValue).glowroot$setSpan(span);
                } else if (returnValue != null && !outputStreamIssueAlreadyLogged.getAndSet(true)) {
                    logger.info("found non-instrumented http url connection output stream, please"
                            + " report to https://github.com/glowroot/instrumentation: {}",
                            returnValue.getClass().getName());
                }
            }
            onReturnCommon(spanOrTimer);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onThrowCommon(spanOrTimer, t);
        }
    }

    @Advice.Pointcut(className = "java.net.HttpURLConnection",
                     methodName = "getResponseCode|getResponseMessage",
                     methodParameterTypes = {},
                     nestingGroup = "http-client")
    public static class GetResponseAdvice {

        @Advice.OnMethodBefore
        public static @Nullable SpanOrTimer onBefore(
                @Bind.This HttpURLConnection httpURLConnection,
                ThreadContext context) {

            return onBeforeCommon(httpURLConnection, false, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HttpURLConnection httpURLConnection,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onReturnCaptureResponseCode(httpURLConnection);
            onReturnCommon(spanOrTimer);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onThrowCommon(spanOrTimer, t);
        }
    }

    @Advice.Pointcut(className = "java.net.URLConnection",
                     subTypeRestriction = "java.net.HttpURLConnection",
                     methodName = "getHeaderField*|getContent*|getDate|getExpiration"
                             + "|getLastModified",
                     methodParameterTypes = {".."},
                     nestingGroup = "http-client")
    public static class GetHeaderFieldAdvice {

        @Advice.OnMethodBefore
        public static @Nullable SpanOrTimer onBefore(
                @Bind.This HttpURLConnection httpURLConnection,
                ThreadContext context) {

            return onBeforeCommon(httpURLConnection, false, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This HttpURLConnection httpURLConnection,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onReturnCaptureResponseCode(httpURLConnection);
            onReturnCommon(spanOrTimer);
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable SpanOrTimer spanOrTimer) {

            onThrowCommon(spanOrTimer, t);
        }
    }

    @Advice.Pointcut(className = "java.io.InputStream",
                     subTypeRestriction = "sun.net.www.protocol.http.HttpURLConnection$HttpInputStream"
                             + "|weblogic.net.http.KeepAliveStream",
                     methodName = "*",
                     methodParameterTypes = {".."})
    public static class HttpInputStreamAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This InputStream inputStream) {

            if (!(inputStream instanceof HasSpanMixin)) {
                return null;
            }
            Span span = ((HasSpanMixin) inputStream).glowroot$getSpan();
            if (span == null) {
                return null;
            }
            return span.extend();
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    @Advice.Pointcut(className = "java.io.OutputStream",
                     subTypeRestriction = "sun.net.www.protocol.http.HttpURLConnection$StreamingOutputStream"
                             + "|sun.net.www.http.PosterOutputStream"
                             + "|weblogic.utils.io.UnsyncByteArrayOutputStream",
                     methodName = "*",
                     methodParameterTypes = {".."})
    public static class StreamingOutputStreamAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This OutputStream outputStream) {

            if (!(outputStream instanceof HasSpanMixin)) {
                return null;
            }
            Span span = ((HasSpanMixin) outputStream).glowroot$getSpan();
            if (span == null) {
                return null;
            }
            return span.extend();
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer timer) {

            if (timer != null) {
                timer.stop();
            }
        }
    }

    private static @Nullable SpanOrTimer onBeforeCommon(HttpURLConnection httpURLConnection,
            boolean overrideGetWithPost, ThreadContext context) {

        if (!(httpURLConnection instanceof HasSpanMixin)) {
            return null;
        }
        HasSpanMixin hasSpanMixin = (HasSpanMixin) httpURLConnection;
        Span span = hasSpanMixin.glowroot$getSpan();
        if (span != null) {
            return new SpanOrTimer(span.extend());
        }
        String method = httpURLConnection.getRequestMethod();
        if (method == null) {
            method = "";
        } else if (overrideGetWithPost && method.equals("GET")) {
            // this is to match behavior in
            // sun.net.www.protocol.http.HttpURLConnection.getOutputStream0()
            method = "POST";
        }
        URL urlObj = httpURLConnection.getURL();
        String url;
        if (urlObj == null) {
            url = "";
        } else {
            url = urlObj.toString();
        }
        span = startOutgoingSpan(context, method, url, SETTER, httpURLConnection, TIMER_NAME);
        hasSpanMixin.glowroot$setSpan(span);
        return new SpanOrTimer(span);
    }

    private static void onReturnCommon(@Nullable SpanOrTimer spanOrTimer) {
        if (spanOrTimer != null) {
            spanOrTimer.onReturn();
        }
    }

    private static void onReturnCaptureResponseCode(HttpURLConnection httpURLConnection) {
        if (!(httpURLConnection instanceof HasSpanMixin)) {
            return;
        }
        Span span = ((HasSpanMixin) httpURLConnection).glowroot$getSpan();
        if (span == null) {
            return;
        }
        HttpRequestMessageSupplier messageSupplier =
                (HttpRequestMessageSupplier) span.getMessageSupplier();
        if (messageSupplier == null) {
            return;
        }
        try {
            messageSupplier.setStatusCode(httpURLConnection.getResponseCode());
        } catch (IOException e) {
            logger.debug(e.getMessage(), e);
        }
    }

    private static void onThrowCommon(@Nullable SpanOrTimer spanOrTimer, Throwable t) {
        if (spanOrTimer != null) {
            spanOrTimer.onThrow(t);
        }
    }

    public static <C> Span startOutgoingSpan(ThreadContext context, @Nullable String httpMethod,
            @Nullable String uri, Setter<C> setter, C carrier,
            TimerName timerName) {

        int maxLength = 0;
        if (httpMethod != null) {
            maxLength += httpMethod.length();
        }
        if (uri != null) {
            maxLength += uri.length() + 1;
        }

        StringBuilder sb = new StringBuilder(maxLength);
        if (httpMethod != null) {
            sb.append(httpMethod);
        }
        if (uri != null) {
            if (sb.length() != 0) {
                sb.append(' ');
            }
            sb.append(stripQueryString(uri));
        }

        HttpRequestMessageSupplier messageSupplier =
                new HttpRequestMessageSupplier(httpMethod, uri);
        return context.startOutgoingSpan("HTTP", sb.toString(), setter, carrier, messageSupplier,
                timerName);
    }

    private static String stripQueryString(String uri) {
        int index = uri.indexOf('?');
        return index == -1 ? uri : uri.substring(0, index);
    }

    private static class SetterImpl implements Setter<HttpURLConnection> {

        @Override
        public void put(HttpURLConnection carrier, String key, String value) {
            try {
                carrier.addRequestProperty(key, value);
            } catch (RuntimeException e) {
                // just in case it throws IllegalStateException: Already connected
                if (!addRequestPropertyIssueLogged.getAndSet(true)) {
                    logger.error("could not add http request header '{}': {}", key, e.toString());
                }
            }
        }
    }
}
