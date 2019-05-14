/*
 * Copyright 2012-2019 the original author or authors.
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

import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice.OnMethodReturn;

/**
 * See {@link ThreadContext} for how to create and use {@code Span} instances.
 */
public interface Span {

    /**
     * End the entry.
     */
    void end();

    /**
     * End the entry and capture a stack trace if its total time exceeds the specified
     * {@code threshold}.
     * 
     * This is a no-op for async spans (those created by
     * {@link ThreadContext#startAsyncOutgoingSpan(String, String, Setter, Object, MessageSupplier, TimerName)}
     * and
     * {@link ThreadContext#startAsyncQuerySpan(String, String, String, QueryMessageSupplier, TimerName)}).
     * This is because async spans are used when their end is performed by a different thread, and
     * so a stack trace at that time does not point to the code which executed triggered the span
     * creation.
     */
    void endWithLocationStackTrace(long thresholdNanos);

    /**
     * End the entry and mark the span as an error with the specified throwable.
     * 
     * The error message text is captured from {@code Throwable#getMessage()}.
     * 
     * If this is the root entry, then the error flag on the transaction is set.
     */
    void endWithError(Throwable t);

    /**
     * Example of query and subsequent iterating over results which goes back to database and pulls
     * more results.
     * 
     * Important note for async spans (those created by
     * {@link ThreadContext#startAsyncOutgoingSpan(String, String, Setter, Object, MessageSupplier, TimerName)}
     * and
     * {@link ThreadContext#startAsyncQuerySpan(String, String, String, QueryMessageSupplier, TimerName)}):
     * this method should not be used by a thread other than the one that created the async trace
     * entry.
     */
    Timer extend();

    /**
     * Returns the message supplier that was supplied when the {@code Span} was created.
     * 
     * This can be useful (for example) to retrieve the message supplier in @{@link OnMethodReturn}
     * so that the return value can be added to the message produced by the {@code MessageSupplier}.
     * 
     * Under some error conditions this can return {@code null}.
     * 
     * Returns either a {@link MessageSupplier} or {@link QueryMessageSupplier}.
     */
    @Nullable
    Object getMessageSupplier();

    // this is needed for ApplicationInsights, at least until end of 2019
    // if supported by the given instrumentation, it will be called on incoming spans right after
    // they are created via startIncomingSpan()
    @Deprecated
    <R> void propagateToResponse(R response, Setter<R> setter);

    // this is needed for ApplicationInsights, at least until end of 2019
    // if supported by the given instrumentation, it will be called on outgoing spans right before
    // they are ended via one of the end*() methods
    @Deprecated
    <R> void extractFromResponse(R response, Getter<R> getter);
}
