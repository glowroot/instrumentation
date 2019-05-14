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

import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.internal.ReadableMessage;
import org.glowroot.instrumentation.test.harness.ImmutableOutgoingSpan;
import org.glowroot.instrumentation.test.harness.ThrowableInfo;
import org.glowroot.instrumentation.test.harness.agent.TimerImpl;

public class AsyncOutgoingSpanImpl extends BaseSpan
        implements AsyncSpan, org.glowroot.instrumentation.api.Timer, SpanImpl {

    private final String type;
    private final MessageSupplier messageSupplier;
    private final TimerImpl syncTimer;
    private final TimerImpl asyncTimer;

    public AsyncOutgoingSpanImpl(String type, MessageSupplier messageSupplier, TimerImpl syncTimer,
            TimerImpl asyncTimer, long startNanoTime) {
        super(startNanoTime);
        this.type = type;
        this.messageSupplier = messageSupplier;
        this.syncTimer = syncTimer;
        this.asyncTimer = asyncTimer;
    }

    @Override
    public void stopSyncTimer() {
        syncTimer.stop(System.nanoTime());
    }

    @Override
    public AsyncOutgoingSpanImpl extendSyncTimer() {
        syncTimer.setExtended();
        return this;
    }

    @Override
    public AsyncOutgoingSpanImpl extend() {
        syncTimer.setExtended();
        asyncTimer.setExtended();
        return this;
    }

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
        OutgoingSpanImpl.setDetail(builder, message.getDetail());
        if (exception != null) {
            builder.exception(ThrowableInfo.create(exception));
        }
        return builder.locationStackTraceMillis(locationStackTraceMillis)
                .build();
    }

    @Override
    protected void endInternal(long endNanoTime) {
        asyncTimer.stop(endNanoTime);
    }
}
