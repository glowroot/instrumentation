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
import java.util.List;

import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.engine.impl.TimerNameImpl;
import org.glowroot.instrumentation.test.harness.ImmutableTimer;

public class TimerImpl implements org.glowroot.instrumentation.api.Timer {

    private final TimerNameImpl timerName;

    private final long startNanoTime;
    private final @Nullable Deque<TimerImpl> currTimerStack;

    private final List<TimerImpl> childTimers = Lists.newArrayList();

    private volatile long totalNanos = -1;

    private volatile boolean extended;

    public static TimerImpl create(TimerNameImpl timerName, long startNanoTime,
            Deque<TimerImpl> currTimerStack) {
        TimerImpl timer = new TimerImpl(timerName, startNanoTime, currTimerStack);
        TimerImpl currTimer = currTimerStack.peek();
        if (currTimer != null) {
            currTimer.addChildTimer(timer);
        }
        currTimerStack.push(timer);
        return timer;
    }

    public static TimerImpl createAsync(TimerNameImpl timerName, long startNanoTime) {
        return new TimerImpl(timerName, startNanoTime, null);
    }

    private TimerImpl(TimerNameImpl timerName, long startNanoTime,
            @Nullable Deque<TimerImpl> currTimerStack) {
        this.timerName = timerName;
        this.startNanoTime = startNanoTime;
        this.currTimerStack = currTimerStack;
    }

    public TimerNameImpl getTimerName() {
        return timerName;
    }

    public void addChildTimer(TimerImpl childTimer) {
        childTimers.add(childTimer);
    }

    @Override
    public void stop() {
        stop(System.nanoTime());
    }

    public void stop(long endNanoTime) {
        totalNanos = endNanoTime - startNanoTime;
        if (currTimerStack != null && currTimerStack.pop() != this) {
            throw new IllegalStateException(
                    "Unexpected value at the top of current parent span stack");
        }
    }

    long getTotalNanos() {
        return totalNanos;
    }

    public void setExtended() {
        extended = true;
    }

    public ImmutableTimer toImmutable() {
        ImmutableTimer.Builder builder = ImmutableTimer.builder()
                .name(timerName.name())
                .extended(extended)
                .totalNanos(totalNanos)
                .count(1);
        for (TimerImpl timer : childTimers) {
            builder.addChildTimers(timer.toImmutable());
        }
        return builder.build();
    }
}
