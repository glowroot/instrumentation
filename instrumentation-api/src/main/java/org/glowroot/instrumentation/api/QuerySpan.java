/*
 * Copyright 2015-2019 the original author or authors.
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

/**
 * A {@link Span} that also captures query data for aggregation.
 */
public interface QuerySpan extends Span {

    /**
     * This can be used if the instrumentation wants to stop the timer on the span and check the
     * duration and gather more info if the duration exceeds some threshold before the underlying
     * agent flushes the data, which could heppen in part 2.
     * 
     * @return duration in nanoseconds
     */
    long partOneEnd();

    /**
     * See {@link #partOneEnd()}.
     * 
     * @return duration in nanoseconds
     */
    long partOneEndWithLocationStackTrace(long thresholdNanos);

    /**
     * See {@link #partOneEnd()}.
     */
    void partTwoEnd();

    void rowNavigationAttempted();

    /**
     * Call after successfully getting next row.
     */
    void incrementCurrRow();

    /**
     * Row numbers start at 1 (not 0).
     * 
     * @param row
     */
    void setCurrRow(long row);
}
