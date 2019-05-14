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

import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;

public class Util {

    private Util() {}

    public static <C> Span startAsyncTransaction(OptionalThreadContext context,
            @Nullable String methodName, @Nullable String uri, Getter<C> getter, C carrier,
            TimerName timerName) {
        String path = getPath(uri);
        String message;
        if (methodName == null) {
            message = uri;
        } else {
            message = methodName + " " + uri;
        }
        Span span = context.startIncomingSpan("Web", path, getter, carrier,
                MessageSupplier.create(message), timerName,
                AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
        context.setTransactionAsync();
        return span;
    }

    private static String getPath(@Nullable String uri) {
        String path;
        if (uri == null) {
            path = "";
        } else {
            int index = uri.indexOf('?');
            if (index == -1) {
                path = uri;
            } else {
                path = uri.substring(0, index);
            }
        }
        return path;
    }
}
