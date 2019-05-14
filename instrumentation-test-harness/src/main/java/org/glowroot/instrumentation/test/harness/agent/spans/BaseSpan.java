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

import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.Setter;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public abstract class BaseSpan implements org.glowroot.instrumentation.api.Span {

    protected final long startNanoTime;

    protected volatile @Nullable Throwable exception;
    protected volatile @Nullable Long locationStackTraceMillis;

    protected volatile long totalNanos = -1;

    public BaseSpan(long startNanoTime) {
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
        exception = t;
        endInternal();
    }

    @Override
    @Deprecated
    public <R> void propagateToResponse(R response, Setter<R> setter) {}

    @Override
    @Deprecated
    public <R> void extractFromResponse(R response, Getter<R> getter) {}

    protected final long endInternal() {
        long endNanoTime = System.nanoTime();
        totalNanos = endNanoTime - startNanoTime;
        endInternal(endNanoTime);
        return totalNanos;
    }

    protected abstract void endInternal(long endNanoTime);
}
