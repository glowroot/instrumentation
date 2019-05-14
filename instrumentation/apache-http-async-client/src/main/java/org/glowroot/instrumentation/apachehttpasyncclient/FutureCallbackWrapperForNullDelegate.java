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

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;

import org.glowroot.instrumentation.apachehttpasyncclient.boot.HttpRequestMessageSupplier;
import org.glowroot.instrumentation.api.AsyncSpan;

class FutureCallbackWrapperForNullDelegate implements FutureCallback<HttpResponse> {

    private final AsyncSpan span;

    FutureCallbackWrapperForNullDelegate(AsyncSpan span) {
        this.span = span;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void completed(HttpResponse result) {
        HttpRequestMessageSupplier messageSupplier =
                (HttpRequestMessageSupplier) span.getMessageSupplier();
        StatusLine statusLine = result.getStatusLine();
        if (messageSupplier != null && statusLine != null) {
            messageSupplier.setStatusCode(statusLine.getStatusCode());
        }
        span.extractFromResponse(result, HttpResponseGetter.INSTANCE);
        span.end();
    }

    @Override
    public void failed(Exception exception) {
        span.endWithError(exception);
    }

    @Override
    public void cancelled() {
        span.end();
    }
}
