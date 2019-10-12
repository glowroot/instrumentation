/*
 * Copyright 2016-2019 the original author or authors.
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
package org.glowroot.instrumentation.engine.impl;

import java.util.concurrent.TimeUnit;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.api.AsyncQuerySpan;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextPlus;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.instrumentation.engine.spi.AgentSPI;

import static com.google.common.base.Preconditions.checkNotNull;

public class OptionalThreadContextImpl implements ThreadContextPlus {

    private static final Logger logger = LoggerFactory.getLogger(OptionalThreadContextImpl.class);

    private int rootNestingGroupId;
    private int rootSuppressionKeyId;

    private @MonotonicNonNull ThreadContextPlus threadContext;

    private final AgentSPI agentSPI;
    private final ThreadContextThreadLocal.Holder threadContextHolder;

    public OptionalThreadContextImpl(AgentSPI agentSPI,
            ThreadContextThreadLocal.Holder threadContextHolder, int rootNestingGroupId,
            int rootSuppressionKeyId) {
        this.agentSPI = agentSPI;
        this.threadContextHolder = threadContextHolder;
        this.rootNestingGroupId = rootNestingGroupId;
        this.rootSuppressionKeyId = rootSuppressionKeyId;
    }

    @Override
    public boolean isInTransaction() {
        return threadContext != null;
    }

    @Override
    public <C> Span startIncomingSpan(String operationType, String transactionName,
            Getter<C> getter, C carrier, MessageSupplier messageSupplier, TimerName timerName,
            AlreadyInTransactionBehavior alreadyInTransactionBehavior) {
        if (operationType == null) {
            logger.error("startIncomingSpan(): argument 'transactionType' must be non-null");
            return NopTransactionService.LOCAL_SPAN;
        }
        if (transactionName == null) {
            logger.error("startIncomingSpan(): argument 'transactionName' must be non-null");
            return NopTransactionService.LOCAL_SPAN;
        }
        if (messageSupplier == null) {
            logger.error("startIncomingSpan(): argument 'messageSupplier' must be non-null");
            return NopTransactionService.LOCAL_SPAN;
        }
        if (timerName == null) {
            logger.error("startIncomingSpan(): argument 'timerName' must be non-null");
            return NopTransactionService.LOCAL_SPAN;
        }
        if (threadContext == null) {
            Span span = agentSPI.startIncomingSpan(operationType, transactionName, getter, carrier,
                    messageSupplier, timerName, threadContextHolder, rootNestingGroupId,
                    rootSuppressionKeyId);
            if (span == null) {
                return NopTransactionService.LOCAL_SPAN;
            } else {
                threadContext = checkNotNull(threadContextHolder.get());
                return span;
            }
        } else {
            return threadContext.startIncomingSpan(operationType, transactionName, getter, carrier,
                    messageSupplier, timerName, alreadyInTransactionBehavior);
        }
    }

    @Override
    public Span startLocalSpan(MessageSupplier messageSupplier, TimerName timerName) {
        if (threadContext == null) {
            return NopTransactionService.LOCAL_SPAN;
        }
        return threadContext.startLocalSpan(messageSupplier, timerName);
    }

    @Override
    public QuerySpan startQuerySpan(String type, String dest, String text,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName) {
        if (threadContext == null) {
            return NopTransactionService.QUERY_SPAN;
        }
        return threadContext.startQuerySpan(type, dest, text, queryMessageSupplier, timerName);
    }

    @Override
    public QuerySpan startQuerySpan(String type, String dest, String text, long queryExecutionCount,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName) {
        if (threadContext == null) {
            return NopTransactionService.QUERY_SPAN;
        }
        return threadContext.startQuerySpan(type, dest, text, queryExecutionCount,
                queryMessageSupplier, timerName);
    }

    @Override
    public AsyncQuerySpan startAsyncQuerySpan(String type, String dest, String text,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName) {
        if (threadContext == null) {
            return NopTransactionService.ASYNC_QUERY_SPAN;
        }
        return threadContext.startAsyncQuerySpan(type, dest, text, queryMessageSupplier, timerName);
    }

    @Override
    public <C> Span startOutgoingSpan(String serviceCallType, String serviceCallText,
            Setter<C> setter, C carrier, MessageSupplier messageSupplier, TimerName timerName) {
        if (threadContext == null) {
            return NopTransactionService.LOCAL_SPAN;
        }
        return threadContext.startOutgoingSpan(serviceCallType, serviceCallText, setter, carrier,
                messageSupplier, timerName);
    }

    @Override
    public <C> AsyncSpan startAsyncOutgoingSpan(String serviceCallType, String serviceCallText,
            Setter<C> setter, C carrier, MessageSupplier messageSupplier, TimerName timerName) {
        if (threadContext == null) {
            return NopTransactionService.ASYNC_SPAN;
        }
        return threadContext.startAsyncOutgoingSpan(serviceCallType, serviceCallText, setter,
                carrier, messageSupplier, timerName);
    }

    @Override
    public void captureLoggerSpan(MessageSupplier messageSupplier, @Nullable Throwable throwable) {
        if (threadContext != null) {
            threadContext.captureLoggerSpan(messageSupplier, throwable);
        }
    }

    @Override
    public Timer startTimer(TimerName timerName) {
        if (threadContext == null) {
            return NopTransactionService.TIMER;
        }
        return threadContext.startTimer(timerName);
    }

    @Override
    public AuxThreadContext createAuxThreadContext() {
        if (threadContext == null) {
            return NopTransactionService.AUX_THREAD_CONTEXT;
        }
        return threadContext.createAuxThreadContext();
    }

    @Override
    public void setTransactionAsync() {
        if (threadContext != null) {
            threadContext.setTransactionAsync();
        }
    }

    @Override
    public void setTransactionAsyncComplete() {
        if (threadContext != null) {
            threadContext.setTransactionAsyncComplete();
        }
    }

    @Override
    public void setTransactionType(@Nullable String transactionType, int priority) {
        if (threadContext != null) {
            threadContext.setTransactionType(transactionType, priority);
        }
    }

    @Override
    public void setTransactionName(@Nullable String transactionName, int priority) {
        if (threadContext != null) {
            threadContext.setTransactionName(transactionName, priority);
        }
    }

    @Override
    public void setTransactionUser(@Nullable String user, int priority) {
        if (threadContext != null) {
            threadContext.setTransactionUser(user, priority);
        }
    }

    @Override
    public void addTransactionAttribute(String name, @Nullable String value) {
        if (threadContext != null) {
            threadContext.addTransactionAttribute(name, value);
        }
    }

    @Override
    public void setTransactionSlowThreshold(long threshold, TimeUnit unit, int priority) {
        if (threadContext != null) {
            threadContext.setTransactionSlowThreshold(threshold, unit, priority);
        }
    }

    @Override
    public void setTransactionError(Throwable t) {
        if (threadContext != null) {
            threadContext.setTransactionError(t);
        }
    }

    @Override
    public void setTransactionError(@Nullable String message) {
        if (threadContext != null) {
            threadContext.setTransactionError(message);
        }
    }

    @Override
    public void setTransactionError(@Nullable String message, @Nullable Throwable t) {
        if (threadContext != null) {
            threadContext.setTransactionError(message, t);
        }
    }

    @Override
    public void trackResourceAcquired(Object resource, boolean withLocationStackTrace) {
        if (threadContext != null) {
            threadContext.trackResourceAcquired(resource, withLocationStackTrace);
        }
    }

    @Override
    public void trackResourceReleased(Object resource) {
        if (threadContext != null) {
            threadContext.trackResourceReleased(resource);
        }
    }

    @Override
    public @Nullable ServletRequestInfo getServletRequestInfo() {
        if (threadContext != null) {
            return threadContext.getServletRequestInfo();
        }
        return null;
    }

    @Override
    public void setServletRequestInfo(ServletRequestInfo servletRequestInfo) {
        if (threadContext != null) {
            threadContext.setServletRequestInfo(servletRequestInfo);
        }
    }

    @Override
    public int getCurrentNestingGroupId() {
        if (threadContext == null) {
            return 0;
        } else {
            return threadContext.getCurrentNestingGroupId();
        }
    }

    @Override
    public void setCurrentNestingGroupId(int nestingGroupId) {
        if (threadContext == null) {
            rootNestingGroupId = nestingGroupId;
        } else {
            threadContext.setCurrentNestingGroupId(nestingGroupId);
        }
    }

    @Override
    public int getCurrentSuppressionKeyId() {
        if (threadContext == null) {
            return 0;
        } else {
            return threadContext.getCurrentSuppressionKeyId();
        }
    }

    @Override
    public void setCurrentSuppressionKeyId(int suppressionKeyId) {
        if (threadContext == null) {
            rootSuppressionKeyId = suppressionKeyId;
        } else {
            threadContext.setCurrentSuppressionKeyId(suppressionKeyId);
        }
    }
}
