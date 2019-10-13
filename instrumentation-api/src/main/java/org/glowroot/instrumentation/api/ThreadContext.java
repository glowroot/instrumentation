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
package org.glowroot.instrumentation.api;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.glowroot.instrumentation.api.checker.Nullable;

public interface ThreadContext {

    /**
     * Creates and starts a local span with the given {@code messageSupplier}. A timer for the
     * specified timer name is also started.
     * 
     * If there is no current transaction then this method does nothing, and returns a no-op
     * instance of {@link Span}.
     */
    Span startLocalSpan(MessageSupplier messageSupplier, TimerName timerName);

    /**
     * {@link QuerySpan} is a specialized type of (outgoing) {@link Span} that is aggregated by its
     * query text.
     */
    QuerySpan startQuerySpan(String type, String dest, String text,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName);

    QuerySpan startQuerySpan(String type, String dest, String text, long queryExecutionCount,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName);

    AsyncQuerySpan startAsyncQuerySpan(String type, String dest, String text,
            QueryMessageSupplier queryMessageSupplier, TimerName timerName);

    <C> Span startOutgoingSpan(String type, String text, Setter<C> setter, C carrier,
            MessageSupplier messageSupplier, TimerName timerName);

    <C> AsyncSpan startAsyncOutgoingSpan(String type, String text, Setter<C> setter, C carrier,
            MessageSupplier messageSupplier, TimerName timerName);

    /**
     * Starts a timer for the specified timer name. If a timer is already running for the specified
     * timer name, it will keep an internal counter of the number of starts, and it will only end
     * the timer after the corresponding number of ends.
     * 
     * If there is no current transaction then this method does nothing, and returns a no-op
     * instance of {@link Timer}.
     */
    Timer startTimer(TimerName timerName);

    /**
     * TODO
     */
    AuxThreadContext createAuxThreadContext();

    /**
     * TODO
     */
    void setTransactionAsync();

    /**
     * TODO
     */
    void setTransactionAsyncComplete();

    /**
     * Set the transaction type that is used for aggregation.
     * 
     * Calling this method with a non-null non-empty value overrides the transaction type set in
     * {@link OptionalThreadContext#startIncomingSpan(String, String, Getter, Object, MessageSupplier, TimerName, org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior)}.
     * 
     * If this method is called multiple times within a single transaction, the highest priority
     * non-null non-empty value wins, and priority ties go to the first caller.
     * 
     * See {@link Priority} for common priority values.
     * 
     * If there is no current transaction then this method does nothing.
     */
    void setTransactionType(@Nullable String transactionType, int priority);

    /**
     * Set the transaction name that is used for aggregation.
     *
     * Calling this method with a non-null non-empty value overrides the transaction name set in
     * {@link OptionalThreadContext#startIncomingSpan(String, String, Getter, Object, MessageSupplier, TimerName, org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior)}.
     * 
     * If this method is called multiple times within a single transaction, the highest priority
     * non-null non-empty value wins, and priority ties go to the first caller.
     * 
     * See {@link Priority} for common priority values.
     * 
     * If there is no current transaction then this method does nothing.
     */
    void setTransactionName(@Nullable String transactionName, int priority);

    /**
     * Sets the user attribute on the transaction. This attribute is shared across all
     * instrumentation, and is generally set by the instrumentation that initiated the trace, but
     * can be set by other instrumentation if needed.
     * 
     * If this method is called multiple times within a single transaction, the highest priority
     * non-null non-empty value wins, and priority ties go to the first caller.
     * 
     * See {@link Priority} for common priority values.
     * 
     * If there is no current transaction then this method does nothing.
     */
    void setTransactionUser(@Nullable String user, int priority);

    /**
     * Adds an attribute on the current transaction with the specified {@code name} and
     * {@code value}. A transaction's attributes are displayed when viewing a trace on the trace
     * explorer page.
     * 
     * Subsequent calls to this method with the same {@code name} on the same transaction will add
     * an additional attribute if there is not already an attribute with the same {@code name} and
     * {@code value}.
     * 
     * If there is no current transaction then this method does nothing.
     * 
     * {@code null} values are normalized to the empty string.
     */
    void addTransactionAttribute(String name, @Nullable String value);

    /**
     * Overrides the default slow trace threshold (Configuration &gt; General &gt; Slow trace
     * threshold) for the current transaction. This can be used to store particular traces at a
     * lower or higher threshold than the general threshold.
     * 
     * If this method is called multiple times within a single transaction, the highest priority
     * value wins, and priority ties go to the first caller.
     * 
     * See {@link Priority} for common priority values.
     * 
     * If there is no current transaction then this method does nothing.
     */
    void setTransactionSlowThreshold(long threshold, TimeUnit unit, int priority);

    /**
     * Marks the transaction as an error with the given message. Normally transactions are only
     * marked as an error if {@code endWithError} is called on the root entry. This method can be
     * used to mark the entire transaction as an error from a nested entry.
     * 
     * The error message text is captured from {@code Throwable#getMessage()}.
     * 
     * This should be used sparingly. Normally, entries should only mark themselves (using
     * {@code endWithError}), and let the root entry determine if the transaction as a whole should
     * be marked as an error.
     * 
     * E.g., this method is called from the logger instrumentation, to mark the entire transaction
     * as an error if an error is logged through one of the supported logger APIs.
     * 
     * If this method is called multiple times within a single transaction, only the first call has
     * any effect, and subsequent calls are ignored.
     * 
     * If there is no current transaction then this method does nothing.
     */
    void setTransactionError(Throwable t);

    /**
     * Marks the transaction as an error with the given message. Normally transactions are only
     * marked as an error if {@code endWithError} is called on the root entry. This method can be
     * used to mark the entire transaction as an error from a nested entry.
     * 
     * This should be used sparingly. Normally, entries should only mark themselves (using
     * {@code endWithError}), and let the root entry determine if the transaction as a whole should
     * be marked as an error.
     * 
     * E.g., this method is called from the logger instrumentation, to mark the entire transaction
     * as an error if an error is logged through one of the supported logger APIs.
     * 
     * If this method is called multiple times within a single transaction, only the first call has
     * any effect, and subsequent calls are ignored.
     * 
     * If there is no current transaction then this method does nothing.
     */
    void setTransactionError(@Nullable String message);

    /**
     * Marks the transaction as an error with the given message. Normally transactions are only
     * marked as an error if {@code endWithError} is called on the root entry. This method can be
     * used to mark the entire transaction as an error from a nested entry.
     * 
     * If {@code message} is empty or null, then the error message text is captured from
     * {@code Throwable#getMessage()}.
     * 
     * This should be used sparingly. Normally, entries should only mark themselves (using
     * {@code endWithError}), and let the root entry determine if the transaction as a whole should
     * be marked as an error.
     * 
     * E.g., this method is called from the logger instrumentation, to mark the entire transaction
     * as an error if an error is logged through one of the supported logger APIs.
     * 
     * If this method is called multiple times within a single transaction, only the first call has
     * any effect, and subsequent calls are ignored.
     * 
     * If there is no current transaction then this method does nothing.
     */
    void setTransactionError(@Nullable String message, @Nullable Throwable t);

    // this is for tracking down resource leaks
    void trackResourceAcquired(Object resource, boolean withLocationStackTrace);

    void trackResourceReleased(Object resource);

    @Nullable
    ServletRequestInfo getServletRequestInfo();

    /**
     * DO NOT USE.
     * 
     * This method should only ever be used by the servlet instrumentation.
     */
    void setServletRequestInfo(ServletRequestInfo servletRequestInfo);

    interface ServletRequestInfo {

        String getMethod();

        String getContextPath();

        String getServletPath();

        // getPathInfo() returns null when the servlet is mapped to "/" (not "/*") and therefore it
        // is replacing the default servlet and in this case getServletPath() returns the full path
        @Nullable
        String getPathInfo();

        String getUri();

        void addJaxRsPart(String part); // should only ever be used by the jaxrs instrumentation

        List<String> getJaxRsParts(); // should only ever be used by the jaxrs instrumentation
    }

    public final class Priority {

        public static final int CORE_INSTRUMENTATION = -100;
        public static final int USER_INSTRUMENTATION = 100;
        public static final int USER_API = 1000;
        public static final int USER_CONFIG = 10000;
        // this is used for very special circumstances, currently only
        // when setting transaction name from HTTP header
        // and for setting slow threshold (to zero) for Startup transactions
        // and for setting slow threshold for user-specific profiling
        public static final int CORE_MAX = 1000000;

        private Priority() {}
    }
}
