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
package org.glowroot.instrumentation.apachehttpclient.boot;

import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;

public class Util {

    private Util() {}

    public static <C> Span startOutgoingSpan(ThreadContext context, @Nullable String httpMethod,
            @Nullable String host, @Nullable String uri, Setter<C> setter, C carrier,
            TimerName timerName) {

        int maxLength = 0;
        if (httpMethod != null) {
            maxLength += httpMethod.length();
        }
        if (host != null) {
            maxLength += host.length() + 1;
        }
        if (uri != null) {
            maxLength += uri.length() + 1;
        }

        StringBuilder sb = new StringBuilder(maxLength);
        if (httpMethod != null) {
            sb.append(httpMethod);
        }
        if (host != null) {
            if (sb.length() != 0) {
                sb.append(' ');
            }
            sb.append(host);
        }
        if (uri != null) {
            if (sb.length() != 0) {
                sb.append(' ');
            }
            sb.append(stripQueryString(uri));
        }

        HttpRequestMessageSupplier messageSupplier =
                new HttpRequestMessageSupplier(httpMethod, host, uri);
        return context.startOutgoingSpan("HTTP", sb.toString(), setter, carrier, messageSupplier,
                timerName);
    }

    private static String stripQueryString(String uri) {
        int index = uri.indexOf('?');
        return index == -1 ? uri : uri.substring(0, index);
    }
}
