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
import java.util.concurrent.TimeUnit;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.AsyncQuerySpan;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextPlus;
import org.glowroot.instrumentation.engine.impl.TimerNameImpl;
import org.glowroot.instrumentation.engine.util.TwoPartCompletion;
import org.glowroot.instrumentation.test.harness.agent.spans.AsyncOutgoingSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.AsyncQuerySpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.IncomingSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.LocalSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.LoggerSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.OutgoingSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.ParentSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.QuerySpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.SpanImpl;

import static com.google.common.base.Preconditions.checkNotNull;

public class ThreadContextImpl implements ThreadContextPlus {

    private final IncomingSpanImpl incomingSpan;

    private final Deque<TimerImpl> currTimerStack;
    private final Deque<ParentSpanImpl> currParentSpanStack;

    private int currentNestingGroupId;
    private int currentSuppressionKeyId;

    private final @Nullable TwoPartCompletion auxThreadAsyncCompletion;

    public ThreadContextImpl(IncomingSpanImpl incomingSpan, Deque<TimerImpl> currTimerStack,
            Deque<ParentSpanImpl> currParentSpanStack, int rootNestingGroupId,
            int rootSuppressionKeyId, @Nullable TwoPartCompletion auxThreadAsyncCompletion) {
        this.incomingSpan = incomingSpan;
        this.currTimerStack = currTimerStack;
        this.currParentSpanStack = currParentSpanStack;
        currentNestingGroupId = rootNestingGroupId;
        currentSuppressionKeyId = rootSuppressionKeyId;
        this.auxThreadAsyncCompletion = auxThreadAsyncCompletion;
    }

    @Override
    public boolean isInTransaction() {
        return true;
    }

    @Override
    public <C> Span startIncomingSpan(String transactionType, String transactionName,
            Getter<C> getter, C carrier, MessageSupplier messageSupplier, TimerName timerName,
            AlreadyInTransactionBehavior alreadyInTransactionBehavior) {
        if (alreadyInTransactionBehavior == AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN) {
            return startLocalSpan(messageSupplier, timerName);
        } else if (alreadyInTransactionBehavior == AlreadyInTransactionBehavior.CAPTURE_NEW_TRANSACTION) {
            throw new IllegalStateException("CAPTURE_NEW_TRANSACTION is not supported yet");
        } else {
            throw new IllegalStateException("Unexpected enum: " + alreadyInTransactionBehavior);
        }
    }

    @Override
    public Span startLocalSpan(MessageSupplier messageSupplier, TimerName timerName) {

        long startNanoTime = System.nanoTime();
        TimerImpl timer =
                TimerImpl.create((TimerNameImpl) timerName, startNanoTime, currTimerStack);
        LocalSpanImpl localSpan =
                new LocalSpanImpl(System.nanoTime(), messageSupplier, timer, currParentSpanStack);
        addChildSpan(localSpan);
        return localSpan;
    }

    @Override
    public QuerySpan startQuerySpan(String type, String dest, String text,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName) {

        long startNanoTime = System.nanoTime();
        TimerImpl timer =
                TimerImpl.create((TimerNameImpl) timerName, startNanoTime, currTimerStack);
        QuerySpanImpl querySpan =
                new QuerySpanImpl(startNanoTime, type, dest, text, queryMessageSupplier, timer);
        addChildSpan(querySpan);
        return querySpan;
    }

    @Override
    public QuerySpan startQuerySpan(String type, String dest, String text, long queryExecutionCount,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName) {

        // TODO pass along queryExecutionCount
        return startQuerySpan(type, dest, text, queryMessageSupplier, timerName);
    }

    @Override
    public AsyncQuerySpan startAsyncQuerySpan(String type, String dest, String text,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName) {

        long startNanoTime = System.nanoTime();
        TimerImpl syncTimer =
                TimerImpl.create((TimerNameImpl) timerName, startNanoTime, currTimerStack);
        TimerImpl asyncTimer = TimerImpl.createAsync((TimerNameImpl) timerName, startNanoTime);
        incomingSpan.addAsyncTimer(asyncTimer);
        AsyncQuerySpanImpl asyncQuerySpan = new AsyncQuerySpanImpl(type, dest, text,
                queryMessageSupplier, syncTimer, asyncTimer, System.nanoTime());
        addChildSpan(asyncQuerySpan);
        return asyncQuerySpan;
    }

    @Override
    public <C> Span startOutgoingSpan(String type, String text, Setter<C> setter, C carrier,
            MessageSupplier messageSupplier, TimerName timerName) {

        // TODO revisit the point of text
        setter.put(carrier, "X-Test-Harness", "Yes");
        long startNanoTime = System.nanoTime();
        TimerImpl timer =
                TimerImpl.create((TimerNameImpl) timerName, startNanoTime, currTimerStack);
        OutgoingSpanImpl outgoingSpan =
                new OutgoingSpanImpl(System.nanoTime(), type, messageSupplier, timer);
        addChildSpan(outgoingSpan);
        return outgoingSpan;
    }

    @Override
    public <C> AsyncSpan startAsyncOutgoingSpan(String type, String text, Setter<C> setter,
            C carrier, MessageSupplier messageSupplier, TimerName timerName) {

        // TODO revisit the point of text
        setter.put(carrier, "X-Test-Harness", "Yes");
        long startNanoTime = System.nanoTime();
        TimerImpl syncTimer =
                TimerImpl.create((TimerNameImpl) timerName, startNanoTime, currTimerStack);
        TimerImpl asyncTimer = TimerImpl.createAsync((TimerNameImpl) timerName, startNanoTime);
        incomingSpan.addAsyncTimer(asyncTimer);
        AsyncOutgoingSpanImpl asyncOutgoingSpan = new AsyncOutgoingSpanImpl(type, messageSupplier,
                syncTimer, asyncTimer, startNanoTime);
        addChildSpan(asyncOutgoingSpan);
        return asyncOutgoingSpan;
    }

    @Override
    public void captureLoggerSpan(MessageSupplier messageSupplier, @Nullable Throwable throwable) {
        addChildSpan(new LoggerSpanImpl(messageSupplier, throwable));
    }

    @Override
    public TimerImpl startTimer(TimerName timerName) {
        return TimerImpl.create((TimerNameImpl) timerName, System.nanoTime(), currTimerStack);
    }

    @Override
    public AuxThreadContext createAuxThreadContext() {
        return new AuxThreadContextImpl(incomingSpan, getCurrParentSpan());
    }

    @Override
    public void setTransactionAsync() {
        incomingSpan.setAsync();
    }

    @Override
    public void setTransactionAsyncComplete() {
        // this is so that if setTransactionAsyncComplete is called from within an auxiliary thread
        // span, that the transaction won't be completed until that (active) auxiliary thread span
        // is completed (see AuxThreadSpanImpl.endInternal())
        if (auxThreadAsyncCompletion == null || auxThreadAsyncCompletion.completePart1()) {
            incomingSpan.setAsyncComplete();
        }
    }

    @Override
    public void setTransactionType(@Nullable String transactionType, int priority) {
        incomingSpan.setTransactionType(transactionType, priority);
    }

    @Override
    public void setTransactionName(@Nullable String transactionName, int priority) {
        incomingSpan.setTransactionName(transactionName, priority);
    }

    @Override
    public void setTransactionUser(@Nullable String user, int priority) {
        incomingSpan.setUser(user, priority);
    }

    @Override
    public void addTransactionAttribute(String name, @Nullable String value) {}

    @Override
    public void setTransactionSlowThreshold(long threshold, TimeUnit unit, int priority) {}

    @Override
    public void setTransactionError(Throwable t) {
        incomingSpan.setError(t);
    }

    @Override
    public void setTransactionError(@Nullable String message) {
        incomingSpan.setError(message);
    }

    @Override
    public void setTransactionError(@Nullable String message, @Nullable Throwable t) {
        incomingSpan.setError(message, t);
    }

    @Override
    public void trackResourceAcquired(Object resource, boolean withLocationStackTrace) {
        incomingSpan.trackResourceAcquired(resource, withLocationStackTrace);
    }

    @Override
    public void trackResourceReleased(Object resource) {
        incomingSpan.trackResourceReleased(resource);
    }

    @Override
    public @Nullable ServletRequestInfo getServletRequestInfo() {
        return incomingSpan.getServletRequestInfo();
    }

    @Override
    public void setServletRequestInfo(@Nullable ServletRequestInfo servletRequestInfo) {
        incomingSpan.setServletRequestInfo(servletRequestInfo);
    }

    @Override
    public int getCurrentNestingGroupId() {
        return currentNestingGroupId;
    }

    @Override
    public void setCurrentNestingGroupId(int nestingGroupId) {
        this.currentNestingGroupId = nestingGroupId;
    }

    @Override
    public int getCurrentSuppressionKeyId() {
        return currentSuppressionKeyId;
    }

    @Override
    public void setCurrentSuppressionKeyId(int suppressionKeyId) {
        this.currentSuppressionKeyId = suppressionKeyId;
    }

    private void addChildSpan(SpanImpl span) {
        getCurrParentSpan().addChildSpan(span);
        if (span instanceof ParentSpanImpl) {
            currParentSpanStack.push((ParentSpanImpl) span);
        }
    }

    private ParentSpanImpl getCurrParentSpan() {
        return checkNotNull(currParentSpanStack.peek());
    }
}
