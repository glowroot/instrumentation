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

import java.util.Map;

import com.google.common.collect.Maps;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.test.harness.ImmutableOutgoingSpan;
import org.glowroot.instrumentation.test.harness.ThrowableInfo;
import org.glowroot.instrumentation.test.harness.agent.TimerImpl;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class QuerySpanImpl extends BaseSpan
        implements QuerySpan, org.glowroot.instrumentation.api.Timer, SpanImpl {

    private final String type;
    private final String dest;
    private final String text;
    private final QueryMessageSupplier queryMessageSupplier;
    private final TimerImpl timer;

    private volatile long currRow = -1;
    private volatile long maxRow;

    private volatile @Nullable TimerImpl extendedTimer;

    public QuerySpanImpl(long startNanoTime, String type, String dest,
            String text, QueryMessageSupplier queryMessageSupplier, TimerImpl timer) {
        super(startNanoTime);
        this.type = type;
        this.dest = dest;
        this.text = text;
        this.queryMessageSupplier = queryMessageSupplier;
        this.timer = timer;
    }

    @Override
    public long partOneEnd() {
        return endInternal();
    }

    @Override
    public long partOneEndWithLocationStackTrace(long thresholdNanos) {
        locationStackTraceMillis = NANOSECONDS.toMillis(thresholdNanos);
        return endInternal();
    }

    @Override
    public void partTwoEnd() {}

    @Override
    public QuerySpanImpl extend() {
        timer.setExtended();
        return this;
    }

    // this is called via the return value from extend() above
    @Override
    public void stop() {}

    @Override
    public Object getMessageSupplier() {
        return queryMessageSupplier;
    }

    @Override
    public void rowNavigationAttempted() {
        if (currRow == -1) {
            currRow = 0;
        }
    }

    @Override
    public void incrementCurrRow() {
        if (currRow == -1) {
            currRow = 1;
            maxRow = 1;
        } else if (currRow == maxRow) {
            currRow++;
            maxRow = currRow;
        } else {
            currRow++;
        }
    }

    @Override
    public void setCurrRow(long row) {
        if (row > maxRow) {
            maxRow = row;
        }
        currRow = row;
    }

    @Override
    public ImmutableOutgoingSpan toImmutable() {
        Map<String, /*@Nullable*/ Object> detail = Maps.newHashMap(queryMessageSupplier.get());
        if (currRow != -1) {
            detail.put("rows", maxRow);
        }
        ImmutableOutgoingSpan.Builder builder = ImmutableOutgoingSpan.builder()
                .type(type)
                .dest(dest)
                .totalNanos(totalNanos)
                .message(text);
        OutgoingSpanImpl.setDetail(builder, detail);
        if (exception != null) {
            builder.exception(ThrowableInfo.create(exception));
        }
        return builder.locationStackTraceMillis(locationStackTraceMillis)
                .build();
    }

    @Override
    protected void endInternal(long endNanoTime) {
        timer.stop(endNanoTime);
    }
}
