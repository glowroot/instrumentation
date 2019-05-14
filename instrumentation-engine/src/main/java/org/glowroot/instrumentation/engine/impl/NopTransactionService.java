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
package org.glowroot.instrumentation.engine.impl;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.AsyncQuerySpan;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.Timer;

public class NopTransactionService {

    public static final Timer TIMER = NopTimer.INSTANCE;
    public static final Span LOCAL_SPAN = NopAsyncQuerySpan.INSTANCE;
    public static final QuerySpan QUERY_SPAN = NopAsyncQuerySpan.INSTANCE;
    public static final AsyncSpan ASYNC_SPAN = NopAsyncQuerySpan.INSTANCE;
    public static final AsyncQuerySpan ASYNC_QUERY_SPAN = NopAsyncQuerySpan.INSTANCE;
    public static final AuxThreadContext AUX_THREAD_CONTEXT = NopAuxThreadContext.INSTANCE;

    private NopTransactionService() {}

    private static class NopAsyncQuerySpan implements AsyncQuerySpan {

        private static final NopAsyncQuerySpan INSTANCE = new NopAsyncQuerySpan();

        private NopAsyncQuerySpan() {}

        @Override
        public void end() {}

        @Override
        public void endWithLocationStackTrace(long thresholdNanos) {}

        @Override
        public void endWithError(Throwable t) {}

        @Override
        public long partOneEnd() {
            return 0;
        }

        @Override
        public long partOneEndWithLocationStackTrace(long thresholdNanos) {
            return 0;
        }

        @Override
        public void partTwoEnd() {}

        @Override
        public @Nullable MessageSupplier getMessageSupplier() {
            return null;
        }

        @Override
        public Timer extend() {
            return NopTimer.INSTANCE;
        }

        @Override
        public void rowNavigationAttempted() {}

        @Override
        public void incrementCurrRow() {}

        @Override
        public void setCurrRow(long row) {}

        @Override
        public void stopSyncTimer() {}

        @Override
        public Timer extendSyncTimer() {
            return NopTimer.INSTANCE;
        }

        @Override
        @Deprecated
        public <R> void propagateToResponse(R response, Setter<R> setter) {}

        @Override
        @Deprecated
        public <R> void extractFromResponse(R response, Getter<R> getter) {}
    }

    private static class NopAuxThreadContext implements AuxThreadContext {

        private static final NopAuxThreadContext INSTANCE = new NopAuxThreadContext();

        private NopAuxThreadContext() {}

        @Override
        public Span start() {
            return NopTransactionService.LOCAL_SPAN;
        }

        @Override
        public Span startAndMarkAsyncTransactionComplete() {
            return NopTransactionService.LOCAL_SPAN;
        }
    }

    private static class NopTimer implements Timer {

        private static final NopTimer INSTANCE = new NopTimer();

        private NopTimer() {}

        @Override
        public void stop() {}
    }
}
