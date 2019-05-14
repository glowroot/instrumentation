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

import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.instrumentation.engine.impl.TimerNameImpl;
import org.glowroot.instrumentation.engine.spi.AgentSPI;
import org.glowroot.instrumentation.test.harness.agent.spans.IncomingSpanImpl;
import org.glowroot.instrumentation.test.harness.agent.spans.ParentSpanImpl;

class AgentImpl implements AgentSPI {

    AgentImpl() {}

    // in addition to returning Span, this method needs to put the newly created ThreadContextPlus
    // into the threadContextHolder that is passed in
    @Override
    public <C> Span startIncomingSpan(String transactionType, String transactionName,
            Getter<C> getter, C carrier, MessageSupplier messageSupplier, TimerName timerName,
            ThreadContextThreadLocal.Holder threadContextHolder, int rootNestingGroupId,
            int rootSuppressionKeyId) {

        long startNanoTime = System.nanoTime();

        Deque<TimerImpl> currTimerStack = Queues.newArrayDeque();
        Deque<ParentSpanImpl> currParentSpanStack = Queues.newArrayDeque();

        TimerImpl mainThreadTimer =
                TimerImpl.create((TimerNameImpl) timerName, startNanoTime, currTimerStack);
        IncomingSpanImpl incomingSpan =
                new IncomingSpanImpl(transactionType, transactionName, messageSupplier,
                        threadContextHolder, mainThreadTimer, currParentSpanStack, startNanoTime);
        currParentSpanStack.push(incomingSpan);
        ThreadContextImpl threadContext =
                new ThreadContextImpl(threadContextHolder, incomingSpan, currTimerStack,
                        currParentSpanStack, rootNestingGroupId, rootSuppressionKeyId, null);
        threadContextHolder.set(threadContext);

        return incomingSpan;
    }
}
