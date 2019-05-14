/*
 * Copyright 2015-2019 the original author or authors.
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
package org.glowroot.instrumentation.asynchttpclient;

import java.util.concurrent.Future;

import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Request;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.asynchttpclient.boot.AsyncHttpClientRequestInvoker;
import org.glowroot.instrumentation.asynchttpclient.boot.DirectExecutor;

public class AsyncHttpClient1xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<Request> SETTER = new SetterImpl();

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("com.ning.http.client.ListenableFuture")
    public abstract static class ListenableFutureImpl implements ListenableFutureMixin {

        // volatile not needed, only accessed by the main thread
        private transient @Nullable AsyncSpan glowroot$span;

        // volatile not needed, only accessed by the main thread
        private transient boolean glowroot$ignoreGet;

        @Override
        public @Nullable AsyncSpan glowroot$getSpan() {
            return glowroot$span;
        }

        @Override
        public void glowroot$setSpan(@Nullable AsyncSpan span) {
            glowroot$span = span;
        }

        @Override
        public boolean glowroot$getIgnoreGet() {
            return glowroot$ignoreGet;
        }

        @Override
        public void glowroot$setIgnoreGet(boolean ignoreGet) {
            glowroot$ignoreGet = ignoreGet;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    // NOTE this interface cannot extend ListenableFuture since ListenableFuture extends this
    // interface after the mixin takes place
    public interface ListenableFutureMixin {

        @Nullable
        AsyncSpan glowroot$getSpan();

        void glowroot$setSpan(@Nullable AsyncSpan span);

        boolean glowroot$getIgnoreGet();

        void glowroot$setIgnoreGet(boolean value);
    }

    @Advice.Pointcut(className = "com.ning.http.client.AsyncHttpClient",
                     methodName = "executeRequest",
                     methodParameterTypes = {"com.ning.http.client.Request", ".."},
                     methodReturnType = "com.ning.http.client.ListenableFuture",
                     nestingGroup = "http-client")
    public static class OldExecuteRequestAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.Argument(0) @Nullable Request request,
                @Bind.ClassMeta AsyncHttpClientRequestInvoker requestInvoker,
                ThreadContext context) {

            // need to start the span @Advice.OnMethodBefore in case it is executed in a "same
            // thread
            // executor" in which case will be over in @Advice.OnMethodExit
            if (request == null) {
                return null;
            }
            String method = request.getMethod();
            if (method == null) {
                method = "";
            } else {
                method += " ";
            }
            String url = requestInvoker.getUrl(request);
            return context.startAsyncOutgoingSpan("HTTP", method + stripQueryString(url), SETTER,
                    request, MessageSupplier.create("http client request: {}{}", method, url),
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static <T extends ListenableFutureMixin & ListenableFuture<?>> void onReturn(
                @Bind.Return @Nullable T future,
                @Bind.Enter @Nullable AsyncSpan span) {

            if (span == null) {
                return;
            }
            span.stopSyncTimer();
            if (future == null) {
                span.end();
                return;
            }
            future.glowroot$setSpan(span);
            future.addListener(new ExecuteRequestListener<T>(span, future),
                    DirectExecutor.INSTANCE);
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

    @Advice.Pointcut(className = "java.util.concurrent.Future",
                     subTypeRestriction = "com.ning.http.client.ListenableFuture",
                     methodName = "get",
                     methodParameterTypes = {".."},
                     suppressionKey = "wait-on-future")
    public static class FutureGetAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This ListenableFutureMixin future) {

            return !future.glowroot$getIgnoreGet();
        }

        @Advice.OnMethodBefore
        public static @Nullable Timer onBefore(@Bind.This ListenableFutureMixin future) {

            AsyncSpan span = future.glowroot$getSpan();
            if (span == null) {
                return null;
            } else {
                return span.extendSyncTimer();
            }
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter @Nullable Timer syncTimer) {

            if (syncTimer != null) {
                syncTimer.stop();
            }
        }
    }

    private static class ExecuteRequestListener<T extends ListenableFutureMixin & Future<?>>
            implements Runnable {

        private final AsyncSpan span;
        private final T future;

        private ExecuteRequestListener(AsyncSpan span, T future) {
            this.span = span;
            this.future = future;
        }

        @Override
        public void run() {
            Throwable t = getException();
            if (t == null) {
                span.end();
            } else {
                span.endWithError(t);
            }
        }

        // this is hacky way to find out if future ended with exception or not
        private @Nullable Throwable getException() {
            future.glowroot$setIgnoreGet(true);
            try {
                future.get();
            } catch (Throwable t) {
                return t;
            } finally {
                future.glowroot$setIgnoreGet(false);
            }
            return null;
        }
    }

    private static String stripQueryString(String uri) {
        int index = uri.indexOf('?');
        return index == -1 ? uri : uri.substring(0, index);
    }

    private static class SetterImpl implements Setter<Request> {

        @Override
        public void put(Request carrier, String key, String value) {
            // cannot use add(String, String) method since that method did not exist prior to 1.8.12
            carrier.getHeaders().add(key, new String[] {value});
        }
    }
}
