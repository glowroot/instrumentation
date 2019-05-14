/*
 * Copyright 2019 the original author or authors.
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

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.checker.Nullable;

class HttpResponseGetter implements Getter<HttpResponse> {

    static final HttpResponseGetter INSTANCE = new HttpResponseGetter();

    @Override
    public @Nullable String get(HttpResponse response, String key) {
        Header header = response.getFirstHeader(key);
        return header == null ? null : header.getValue();
    }
}
