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
package org.glowroot.instrumentation.netty.boot;

import java.util.LinkedHashMap;
import java.util.Map;

import org.glowroot.instrumentation.api.Message;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.checker.Nullable;

class NettyMessageSupplier extends MessageSupplier {

    private final @Nullable String requestMethod;
    private final boolean ssl;
    private final @Nullable String host;
    private final @Nullable String uri;

    NettyMessageSupplier(@Nullable String requestMethod, boolean ssl, @Nullable String host,
            @Nullable String uri) {
        this.requestMethod = requestMethod;
        this.ssl = ssl;
        this.host = host;
        this.uri = uri;
    }

    @Override
    public Message get() {
        String message;
        if (requestMethod == null && uri == null) {
            message = "";
        } else if (requestMethod == null) {
            message = uri;
        } else {
            message = requestMethod + " " + uri;
        }
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        if (requestMethod != null) {
            detail.put("Request http method", requestMethod);
        }
        if (uri != null) {
            detail.put("Request uri", uri);
        }
        if (host != null) {
            detail.put("Request server hostname", host);
        }
        detail.put("Request scheme", ssl ? "https" : "http");
        return Message.create(message, detail);
    }
}
