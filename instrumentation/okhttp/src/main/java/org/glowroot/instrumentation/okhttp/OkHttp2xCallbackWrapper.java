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
package org.glowroot.instrumentation.okhttp;

import java.io.IOException;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.okhttp.boot.HttpRequestMessageSupplier;

class OkHttp2xCallbackWrapper implements Callback {

    private final Callback delegate;
    private final AsyncSpan span;
    private final AuxThreadContext auxContext;

    OkHttp2xCallbackWrapper(Callback delegate, AsyncSpan span, AuxThreadContext auxContext) {
        this.delegate = delegate;
        this.span = span;
        this.auxContext = auxContext;
    }

    @Override
    public void onFailure(Request request, IOException exception) {
        span.endWithError(exception);
        Span span = auxContext.start();
        try {
            delegate.onFailure(request, exception);
        } catch (Throwable t) {
            span.endWithError(t);
            throw rethrow(t);
        }
        span.end();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onResponse(Response response) throws IOException {
        HttpRequestMessageSupplier supplier =
                (HttpRequestMessageSupplier) span.getMessageSupplier();
        if (supplier != null) {
            supplier.setStatusCode(response.code());
        }
        span.extractFromResponse(response, OkHttp2xResponseGetter.INSTANCE);
        span.end();
        Span span = auxContext.start();
        try {
            delegate.onResponse(response);
        } catch (Throwable t) {
            span.endWithError(t);
            throw rethrow(t);
        }
        span.end();
    }

    private static RuntimeException rethrow(Throwable t) {
        OkHttp2xCallbackWrapper.<RuntimeException>throwsUnchecked(t);
        throw new AssertionError();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwsUnchecked(Throwable t) throws T {
        throw (T) t;
    }
}
