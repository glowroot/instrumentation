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
package org.glowroot.instrumentation.test.harness.agent.spans;

import java.util.Map;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.internal.ReadableMessage;
import org.glowroot.instrumentation.test.harness.ImmutableLoggerSpan;
import org.glowroot.instrumentation.test.harness.ThrowableInfo;

public class LoggerSpanImpl implements SpanImpl {

    private final MessageSupplier messageSupplier;
    private final @Nullable Throwable throwable;

    public LoggerSpanImpl(MessageSupplier messageSupplier, @Nullable Throwable throwable) {
        this.messageSupplier = messageSupplier;
        this.throwable = throwable;
    }

    @Override
    public ImmutableLoggerSpan toImmutable() {
        ReadableMessage message = (ReadableMessage) messageSupplier.get();
        ImmutableLoggerSpan.Builder builder = ImmutableLoggerSpan.builder()
                .totalNanos(0)
                .message(message.getText());
        setDetail(builder, message.getDetail());
        if (throwable != null) {
            builder.throwable(ThrowableInfo.create(throwable));
        }
        return builder.build();
    }

    // this suppression is needed for checker framework because the immutables method signature
    // takes Map<String, ? extends Object> instead of/ Map<String, ? extends /*@Nullable*/ Object>
    @SuppressWarnings("argument.type.incompatible")
    private static void setDetail(ImmutableLoggerSpan.Builder builder, Map<String, ?> detail) {
        builder.detail(detail);
    }
}
