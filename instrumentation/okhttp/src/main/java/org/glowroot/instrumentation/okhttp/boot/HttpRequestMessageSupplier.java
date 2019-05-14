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
package org.glowroot.instrumentation.okhttp.boot;

import java.util.HashMap;
import java.util.Map;

import org.glowroot.instrumentation.api.Message;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.checker.Nullable;

public class HttpRequestMessageSupplier extends MessageSupplier {

    private static final String PREFIX = "http client request: ";

    private final @Nullable String httpMethod;
    private final @Nullable String uri;
    private int statusCode = -1;

    HttpRequestMessageSupplier(@Nullable String httpMethod, @Nullable String uri) {
        this.httpMethod = httpMethod;
        this.uri = uri;
    }

    @Override
    public Message get() {
        int messageLength = PREFIX.length();
        if (httpMethod != null) {
            messageLength += httpMethod.length() + 1;
        }
        if (uri != null) {
            messageLength += uri.length();
        }
        StringBuilder message = new StringBuilder(messageLength);
        message.append(PREFIX);
        Map<String, Object> detail = new HashMap<String, Object>();
        if (httpMethod != null) {
            detail.put("Method", httpMethod);
            message.append(httpMethod);
            message.append(' ');
        }
        if (uri != null) {
            detail.put("URI", uri);
            message.append(uri);
        }
        if (statusCode >= 0) {
            detail.put("Result", statusCode);
        }
        return Message.create(message.toString(), detail);
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
}
