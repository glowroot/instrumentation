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
package org.glowroot.instrumentation.test.harness.agent;

import java.util.Deque;

import com.google.common.collect.Queues;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextPlus;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.instrumentation.engine.impl.ImmutableTimerNameImpl;
import org.glowroot.instrumentation.engine.impl.NopTransactionService;
import org.glowroot.instrumentation.engine.impl.TimerNameImpl;
import org.glowroot.instrumentation.engine.util.TwoPartCompletion;
import org.glowroot.instrumentation.test.harness.agent.spans.IncomingSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.ParentSpanImpl;

public class AuxThreadContextImpl implements AuxThreadContext {

    private static final TimerNameImpl AUX_THREAD_ROOT_TIMER_NAME = ImmutableTimerNameImpl.builder()
            .name("auxiliary thread")
            .extended(false)
            .build();

    private final IncomingSpanImpl incomingSpan;
    private final ParentSpanImpl parentSpan;

    public AuxThreadContextImpl(IncomingSpanImpl incomingSpan, ParentSpanImpl parentSpan) {
        this.incomingSpan = incomingSpan;
        this.parentSpan = parentSpan;
    }

    @Override
    public Span start() {
        return start(false);
    }

    @Override
    public Span startAndMarkAsyncTransactionComplete() {
        return start(true);
    }

    private Span start(boolean completeAsyncTransaction) {
        ThreadContextThreadLocal.Holder threadContextHolder = Global.getThreadContextHolder();
        ThreadContextPlus threadContext = threadContextHolder.get();
        if (threadContext != null) {
            if (completeAsyncTransaction) {
                threadContext.setTransactionAsyncComplete();
            }
            return NopTransactionService.LOCAL_SPAN;
        }

        long startNanoTime = System.nanoTime();

        Deque<TimerImpl> currTimerStack = Queues.newArrayDeque();
        TimerImpl auxThreadTimer =
                TimerImpl.create(AUX_THREAD_ROOT_TIMER_NAME, startNanoTime, currTimerStack);
        incomingSpan.addAuxThreadTimer(auxThreadTimer);

        Deque<ParentSpanImpl> currParentSpanStack = Queues.newArrayDeque();
        currParentSpanStack.push(parentSpan);

        TwoPartCompletion auxThreadAsyncCompletion = new TwoPartCompletion();

        threadContext = new ThreadContextImpl(incomingSpan, currTimerStack, currParentSpanStack, 0,
                0, auxThreadAsyncCompletion);
        threadContextHolder.set(threadContext);
        if (completeAsyncTransaction) {
            threadContext.setTransactionAsyncComplete();
        }
        return new AuxThreadSpanImpl(threadContextHolder, auxThreadTimer, auxThreadAsyncCompletion,
                incomingSpan);
    }

    private static class AuxThreadSpanImpl implements Span {

        private final ThreadContextThreadLocal.Holder threadContextHolder;
        private final TimerImpl auxThreadTimer;

        private final TwoPartCompletion auxThreadAsyncCompletion;
        private final IncomingSpanImpl incomingSpan;

        private AuxThreadSpanImpl(ThreadContextThreadLocal.Holder threadContextHolder,
                TimerImpl auxThreadTimer, TwoPartCompletion auxThreadAsyncCompletion,
                IncomingSpanImpl incomingSpan) {
            this.threadContextHolder = threadContextHolder;
            this.auxThreadTimer = auxThreadTimer;
            this.auxThreadAsyncCompletion = auxThreadAsyncCompletion;
            this.incomingSpan = incomingSpan;
        }

        @Override
        public void end() {
            endInternal();
        }

        @Override
        public void endWithLocationStackTrace(long thresholdNanos) {
            endInternal();
        }

        @Override
        public void endWithError(Throwable t) {
            endInternal();
        }

        @Override
        public Timer extend() {
            throw new UnsupportedOperationException(
                    "extend() shouldn't be called on auxiliary thread span");
        }

        @Override
        public @Nullable Object getMessageSupplier() {
            return null;
        }

        @Override
        @Deprecated
        public <R> void propagateToResponse(R response, Setter<R> setter) {}

        @Override
        @Deprecated
        public <R> void extractFromResponse(R response, Getter<R> getter) {}

        private long endInternal() {
            threadContextHolder.set(null);
            auxThreadTimer.stop();
            if (auxThreadAsyncCompletion.completePart2()) {
                incomingSpan.setAsyncComplete();
            }
            return auxThreadTimer.getTotalNanos();
        }
    }
}
