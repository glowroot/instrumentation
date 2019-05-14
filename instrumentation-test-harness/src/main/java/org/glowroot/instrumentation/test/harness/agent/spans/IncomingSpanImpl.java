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
import java.util.Map;
import java.util.Queue;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.ThreadContext.ServletRequestInfo;
import org.glowroot.instrumentation.api.internal.ReadableMessage;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.instrumentation.engine.util.TwoPartCompletion;
import org.glowroot.instrumentation.test.harness.ImmutableIncomingSpan;
import org.glowroot.instrumentation.test.harness.ImmutableIncomingSpanError;
import org.glowroot.instrumentation.test.harness.IncomingSpan.IncomingSpanError;
import org.glowroot.instrumentation.test.harness.ThrowableInfo;
import org.glowroot.instrumentation.test.harness.agent.Global;
import org.glowroot.instrumentation.test.harness.agent.TimerImpl;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class IncomingSpanImpl
        implements org.glowroot.instrumentation.api.Span, ParentSpanImpl {

    private final MessageSupplier messageSupplier;
    private final ThreadContextThreadLocal.Holder threadContextHolder;

    private final long startNanoTime;

    private final TimerImpl mainThreadTimer;
    private final Deque<ParentSpanImpl> currParentSpanStack;

    private final Queue<TimerImpl> auxThreadTimers = Queues.newConcurrentLinkedQueue();

    private final Queue<TimerImpl> asyncTimers = Queues.newConcurrentLinkedQueue();

    private final Queue<SpanImpl> childSpans = Queues.newConcurrentLinkedQueue();

    private volatile @Nullable ServletRequestInfo servletRequestInfo;

    private volatile String transactionType;
    private volatile int transactionTypePriority = Integer.MIN_VALUE;
    private volatile String transactionName;
    private volatile int transactionNamePriority = Integer.MIN_VALUE;
    private volatile @MonotonicNonNull String user;
    private volatile int userPriority = Integer.MIN_VALUE;

    private volatile long syncTotalNanos = -1;
    private volatile long totalNanos = -1;

    private volatile @MonotonicNonNull IncomingSpanError error;
    private volatile @MonotonicNonNull Long locationStackTraceMillis;

    private volatile @MonotonicNonNull TwoPartCompletion asyncCompletion;

    private final Map<Object, Boolean> resourcesAcquired = Maps.newConcurrentMap();

    public IncomingSpanImpl(String transactionType, String transactionName,
            MessageSupplier messageSupplier, ThreadContextThreadLocal.Holder threadContextHolder,
            TimerImpl mainThreadTimer, Deque<ParentSpanImpl> currParentSpanStack,
            long startNanoTime) {
        this.transactionType = transactionType;
        this.transactionName = transactionName;
        this.messageSupplier = messageSupplier;
        this.threadContextHolder = threadContextHolder;
        this.mainThreadTimer = mainThreadTimer;
        this.currParentSpanStack = currParentSpanStack;
        this.startNanoTime = startNanoTime;
    }

    @Override
    public void end() {
        endInternal();
    }

    @Override
    public void endWithLocationStackTrace(long thresholdNanos) {
        locationStackTraceMillis = NANOSECONDS.toMillis(thresholdNanos);
        endInternal();
    }

    @Override
    public void endWithError(Throwable t) {
        if (error == null) {
            error = ImmutableIncomingSpanError.builder()
                    .exception(ThrowableInfo.create(t))
                    .build();
        }
        endInternal();
    }

    @Override
    public org.glowroot.instrumentation.api.Timer extend() {
        throw new UnsupportedOperationException("extend() shouldn't be called on incoming span");
    }

    @Override
    public Object getMessageSupplier() {
        return messageSupplier;
    }

    @Override
    @Deprecated
    public <R> void propagateToResponse(R response, Setter<R> setter) {}

    @Override
    @Deprecated
    public <R> void extractFromResponse(R response, Getter<R> getter) {}

    @Override
    public void addChildSpan(SpanImpl childSpan) {
        childSpans.add(childSpan);
    }

    @Override
    public ImmutableIncomingSpan toImmutable() {
        ImmutableIncomingSpan.Builder builder = ImmutableIncomingSpan.builder()
                .totalNanos(totalNanos)
                .transactionType(transactionType)
                .transactionName(transactionName)
                .user(Strings.nullToEmpty(user))
                .mainThreadTimer(mainThreadTimer.toImmutable());
        for (TimerImpl auxThreadTimer : auxThreadTimers) {
            builder.addAuxThreadTimers(auxThreadTimer.toImmutable());
        }
        for (TimerImpl asyncTimer : asyncTimers) {
            builder.addAsyncTimers(asyncTimer.toImmutable());
        }
        ReadableMessage message = (ReadableMessage) messageSupplier.get();
        builder.message(message.getText());
        setDetail(builder, message.getDetail());
        builder.error(error)
                .locationStackTraceMillis(locationStackTraceMillis);
        for (SpanImpl childSpan : childSpans) {
            builder.addChildSpans(childSpan.toImmutable());
        }
        if (resourcesAcquired.isEmpty()) {
            builder.resourceLeakDetected(false);
            builder.resourceLeakDetectedWithLocation(false);
        } else {
            builder.resourceLeakDetected(true);
            boolean withLocation = false;
            for (boolean value : resourcesAcquired.values()) {
                if (value) {
                    withLocation = true;
                }
            }
            builder.resourceLeakDetectedWithLocation(withLocation);
        }
        return builder.build();
    }

    public @Nullable ServletRequestInfo getServletRequestInfo() {
        return servletRequestInfo;
    }

    public void setServletRequestInfo(@Nullable ServletRequestInfo servletRequestInfo) {
        this.servletRequestInfo = servletRequestInfo;
    }

    public void setAsync() {
        asyncCompletion = new TwoPartCompletion();
    }

    public void setAsyncComplete() {
        checkNotNull(asyncCompletion);
        if (asyncCompletion.completePart1()) {
            totalNanos = System.nanoTime() - startNanoTime;
            Global.report(toImmutable());
        }
    }

    public void setTransactionType(@Nullable String transactionType, int priority) {
        if (priority > transactionTypePriority && !Strings.isNullOrEmpty(transactionType)) {
            this.transactionType = transactionType;
            transactionTypePriority = priority;
        }
    }

    public void setTransactionName(@Nullable String transactionName, int priority) {
        if (priority > transactionNamePriority && !Strings.isNullOrEmpty(transactionName)) {
            this.transactionName = transactionName;
            transactionNamePriority = priority;
        }
    }

    public void setUser(@Nullable String user, int priority) {
        if (priority > userPriority && !Strings.isNullOrEmpty(user)) {
            this.user = user;
            userPriority = priority;
        }
    }

    public void setError(Throwable t) {
        if (error == null) {
            error = ImmutableIncomingSpanError.builder()
                    .exception(ThrowableInfo.create(t))
                    .build();
        }
    }

    public void setError(@Nullable String message) {
        if (error == null) {
            error = ImmutableIncomingSpanError.builder()
                    .message(message)
                    .build();
        }
    }

    public void setError(@Nullable String message, @Nullable Throwable t) {
        if (error == null) {
            if (t == null) {
                error = ImmutableIncomingSpanError.builder()
                        .message(message)
                        .build();
            } else {
                error = ImmutableIncomingSpanError.builder()
                        .message(message)
                        .exception(ThrowableInfo.create(t))
                        .build();
            }
        }
    }

    public void addAuxThreadTimer(TimerImpl auxThreadTimer) {
        auxThreadTimers.add(auxThreadTimer);
    }

    public void addAsyncTimer(TimerImpl asyncTimer) {
        asyncTimers.add(asyncTimer);
    }

    public void trackResourceAcquired(Object resource, boolean withLocationStackTrace) {
        resourcesAcquired.put(resource, withLocationStackTrace);
    }

    public void trackResourceReleased(Object resource) {
        resourcesAcquired.remove(resource);
    }

    private void endInternal() {
        endInternalPart1();
        endInternalPart2();
    }

    private long endInternalPart1() {
        if (syncTotalNanos == -1) {
            threadContextHolder.set(null);

            long nanoTime = System.nanoTime();
            mainThreadTimer.stop(nanoTime);

            if (currParentSpanStack.pop() != this) {
                throw new IllegalStateException(
                        "Unexpected value at the top of current parent span stack");
            }

            syncTotalNanos = nanoTime - startNanoTime;
        }
        return syncTotalNanos;
    }

    private void endInternalPart2() {
        if (asyncCompletion == null || asyncCompletion.completePart2()) {
            totalNanos = syncTotalNanos;
            Global.report(toImmutable());
        }
    }

    // this suppression is needed for checker framework because the immutables method signature
    // takes Map<String, ? extends Object> instead of/ Map<String, ? extends /*@Nullable*/ Object>
    @SuppressWarnings("argument.type.incompatible")
    private static void setDetail(ImmutableIncomingSpan.Builder builder, Map<String, ?> detail) {
        builder.detail(detail);
    }
}
