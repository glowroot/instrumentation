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
import org.glowroot.instrumentation.test.harness.ImmutableOutgoingSpan;
import org.glowroot.instrumentation.test.harness.ThrowableInfo;
import org.glowroot.instrumentation.test.harness.agent.TimerImpl;

public class OutgoingSpanImpl extends BaseSpan implements
        org.glowroot.instrumentation.api.Timer, SpanImpl {

    private final String type;
    private final MessageSupplier messageSupplier;
    private final TimerImpl timer;

    private volatile @Nullable TimerImpl extendedTimer;

    public OutgoingSpanImpl(long startNanoTime, String type, MessageSupplier messageSupplier,
            TimerImpl timer) {
        super(startNanoTime);
        this.type = type;
        this.messageSupplier = messageSupplier;
        this.timer = timer;
    }

    @Override
    public OutgoingSpanImpl extend() {
        timer.setExtended();
        return this;
    }

    // this is called via the return value from extend() above
    @Override
    public void stop() {}

    @Override
    public Object getMessageSupplier() {
        return messageSupplier;
    }

    @Override
    public ImmutableOutgoingSpan toImmutable() {
        ReadableMessage message = (ReadableMessage) messageSupplier.get();
        ImmutableOutgoingSpan.Builder builder = ImmutableOutgoingSpan.builder()
                .type(type)
                .dest("") // TODO
                .totalNanos(totalNanos)
                .message(message.getText());
        setDetail(builder, message.getDetail());
        if (exception != null) {
            builder.exception(ThrowableInfo.create(exception));
        }
        return builder.locationStackTraceMillis(locationStackTraceMillis)
                .build();
    }

    @Override
    protected void endInternal(long endNanoTime) {
        timer.stop(endNanoTime);
    }

    // this suppression is needed for checker framework because the immutables method signature
    // takes Map<String, ? extends Object> instead of/ Map<String, ? extends /*@Nullable*/ Object>
    @SuppressWarnings("argument.type.incompatible")
    static void setDetail(ImmutableOutgoingSpan.Builder builder, Map<String, ?> detail) {
        builder.detail(detail);
    }
}
