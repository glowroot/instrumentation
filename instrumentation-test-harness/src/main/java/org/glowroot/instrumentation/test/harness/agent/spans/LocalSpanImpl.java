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

import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.internal.ReadableMessage;
import org.glowroot.instrumentation.test.harness.ImmutableLocalSpan;
import org.glowroot.instrumentation.test.harness.ThrowableInfo;
import org.glowroot.instrumentation.test.harness.agent.TimerImpl;

public class LocalSpanImpl extends BaseSpan
        implements org.glowroot.instrumentation.api.Timer, ParentSpanImpl {

    private final MessageSupplier messageSupplier;
    private final TimerImpl timer;
    private final Deque<ParentSpanImpl> currParentSpanStack;

    private final List<SpanImpl> childSpans = Lists.newArrayList();

    public LocalSpanImpl(long startNanoTime, MessageSupplier messageSupplier, TimerImpl timer,
            Deque<ParentSpanImpl> currParentSpanStack) {
        super(startNanoTime);
        this.messageSupplier = messageSupplier;
        this.timer = timer;
        this.currParentSpanStack = currParentSpanStack;
    }

    @Override
    public LocalSpanImpl extend() {
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
    public void addChildSpan(SpanImpl childSpan) {
        childSpans.add(childSpan);
    }

    @Override
    public ImmutableLocalSpan toImmutable() {
        ReadableMessage message = (ReadableMessage) messageSupplier.get();
        ImmutableLocalSpan.Builder builder = ImmutableLocalSpan.builder()
                .totalNanos(totalNanos)
                .message(message.getText());
        setDetail(builder, message.getDetail());
        if (exception != null) {
            builder.exception(ThrowableInfo.create(exception));
        }
        builder.locationStackTraceMillis(locationStackTraceMillis);
        for (SpanImpl childSpan : childSpans) {
            builder.addChildSpans(childSpan.toImmutable());
        }
        return builder.build();
    }

    @Override
    protected void endInternal(long endNanoTime) {
        timer.stop(endNanoTime);
        if (currParentSpanStack.pop() != this) {
            throw new IllegalStateException(
                    "Unexpected value at the top of current parent span stack");
        }
    }

    // this suppression is needed for checker framework because the immutables method signature
    // takes Map<String, ? extends Object> instead of/ Map<String, ? extends /*@Nullable*/ Object>
    @SuppressWarnings("argument.type.incompatible")
    private static void setDetail(ImmutableLocalSpan.Builder builder, Map<String, ?> detail) {
        builder.detail(detail);
    }
}
