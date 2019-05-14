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

import com.google.common.base.Throwables;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static com.google.common.base.Preconditions.checkNotNull;

// global state used instead of passing these to various classes (e.g. ThreadContextImpl) in order
// to reduce memory footprint
public class Global {

    private static final ThreadContextThreadLocal threadContextThreadLocal =
            new ThreadContextThreadLocal();

    private static volatile @Nullable TraceReporter traceReporter;

    private Global() {}

    public static ThreadContextThreadLocal getThreadContextThreadLocal() {
        return threadContextThreadLocal;
    }

    public static ThreadContextThreadLocal.Holder getThreadContextHolder() {
        return threadContextThreadLocal.getHolder();
    }

    public static void report(IncomingSpan incomingSpan) {
        try {
            checkNotNull(traceReporter).send(incomingSpan);
        } catch (Throwable t) {
            Throwables.propagateIfPossible(t, RuntimeException.class);
            throw new RuntimeException(t);
        }
    }

    public static void setTraceReporter(TraceReporter traceReporter) {
        Global.traceReporter = traceReporter;
    }
}
