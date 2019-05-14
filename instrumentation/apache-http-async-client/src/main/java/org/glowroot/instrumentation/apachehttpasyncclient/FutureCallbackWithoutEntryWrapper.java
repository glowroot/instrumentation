/*
 * Copyright 2018-2019 the original author or authors.
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

import org.apache.http.concurrent.FutureCallback;

import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Span;

class FutureCallbackWithoutEntryWrapper<T> implements FutureCallback<T> {

    private final FutureCallback<T> delegate;
    private final AuxThreadContext auxContext;

    FutureCallbackWithoutEntryWrapper(FutureCallback<T> delegate,
            AuxThreadContext auxContext) {
        this.delegate = delegate;
        this.auxContext = auxContext;
    }

    @Override
    public void completed(T result) {
        Span span = auxContext.start();
        try {
            delegate.completed(result);
        } catch (Throwable t) {
            span.endWithError(t);
            throw rethrow(t);
        }
        span.end();
    }

    @Override
    public void failed(Exception exception) {
        Span span = auxContext.start();
        try {
            delegate.failed(exception);
        } catch (Throwable t) {
            span.endWithError(t);
            throw rethrow(t);
        }
        span.end();
    }

    @Override
    public void cancelled() {
        Span span = auxContext.start();
        try {
            delegate.cancelled();
        } catch (Throwable t) {
            span.endWithError(t);
            throw rethrow(t);
        }
        span.end();
    }

    private static RuntimeException rethrow(Throwable t) {
        FutureCallbackWithoutEntryWrapper.<RuntimeException>throwsUnchecked(t);
        throw new AssertionError();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwsUnchecked(Throwable t) throws T {
        throw (T) t;
    }
}
