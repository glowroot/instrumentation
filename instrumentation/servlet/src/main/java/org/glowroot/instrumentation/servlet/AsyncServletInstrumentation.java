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
package org.glowroot.instrumentation.servlet;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;

import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class AsyncServletInstrumentation {

    static final String AUX_CONTEXT_REQUEST_ATTRIBUTE = "glowroot$auxContext";

    @Advice.Pointcut(className = "javax.servlet.ServletRequest",
                     methodName = "startAsync",
                     methodParameterTypes = {".."})
    public static class StartAsyncAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return AsyncContext asyncContext,
                ThreadContext context) {

            context.setTransactionAsync();
            asyncContext.addListener(new AsyncListenerImpl(context));
        }
    }

    // IMPORTANT complete is not called if client disconnects, but it's still useful to capture for
    // normal requests since it is called from an auxiliary thread, and by calling
    // setTransactinAsyncComplete() first from the auxiliary thread, the transaction will wait to
    // complete until the auxiliary thread completes, and won't leave behind "this auxiliary thread
    // was still running when the transaction ended" span (if the agent captures those)
    @Advice.Pointcut(className = "javax.servlet.AsyncContext",
                     methodName = "complete",
                     methodParameterTypes = {})
    public static class CompleteAdvice {

        // using @Advice.OnMethodBefore instead of @Advice.OnMethodExit since it is during
        // complete()
        // that AsyncEvent is fired, and want the setTransactionAsyncComplete() in this (auxiliary)
        // thread to win
        @Advice.OnMethodBefore
        public static void onBefore(ThreadContext context) {

            context.setTransactionAsyncComplete();
        }
    }

    @Advice.Pointcut(className = "javax.servlet.AsyncContext",
                     methodName = "dispatch",
                     methodParameterTypes = {".."},
                     nestingGroup = "servlet-dispatch")
    public static class DispatchAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.This AsyncContext asyncContext,
                ThreadContext context) {

            ServletRequest request = asyncContext.getRequest();
            if (request == null) {
                return;
            }
            request.setAttribute(AUX_CONTEXT_REQUEST_ATTRIBUTE, context.createAuxThreadContext());
        }
    }

    private static class AsyncListenerImpl implements AsyncListener {

        private final ThreadContext context;

        private AsyncListenerImpl(ThreadContext context) {
            this.context = context;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            context.setTransactionAsyncComplete();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            Throwable throwable = event.getThrowable();
            if (throwable != null) {
                context.setTransactionError(throwable);
            }
            context.setTransactionAsyncComplete();
        }

        @Override
        public void onError(AsyncEvent event) {
            Throwable throwable = event.getThrowable();
            if (throwable != null) {
                context.setTransactionError(throwable);
            }
            context.setTransactionAsyncComplete();
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            AsyncContext asyncContext = event.getAsyncContext();
            if (asyncContext != null) {
                asyncContext.addListener(this);
            }
        }
    }
}
