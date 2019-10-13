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
package org.glowroot.instrumentation.engine.spi;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal;

public interface AgentSPI {

    // if this returns non-null Span, it must put the newly created thread context into the
    // threadContextHolder that is passed in, and it must clear the threadContextHolder at the end
    // of the synchronous part of the span capture
    @Nullable
    <C> Span startIncomingSpan(String transactionType, String transactionName,
            Getter<C> getter, C carrier, MessageSupplier messageSupplier, TimerName timerName,
            ThreadContextThreadLocal.Holder threadContextHolder, int rootNestingGroupId,
            int rootSuppressionKeyId);

    void captureLoggerSpan(MessageSupplier messageSupplier, @Nullable Throwable throwable);
}
