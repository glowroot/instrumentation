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
package org.glowroot.instrumentation.engine.impl;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NopTransactionServiceTest {

    @Test
    public void testNopLocalSpan() {
        NopTransactionService.LOCAL_SPAN.end();
        NopTransactionService.LOCAL_SPAN.endWithLocationStackTrace(0);
        NopTransactionService.LOCAL_SPAN.endWithError(new Throwable());
        assertThat(NopTransactionService.LOCAL_SPAN.getMessageSupplier()).isNull();
    }

    @Test
    public void testNopQuerySpan() {
        assertThat(NopTransactionService.QUERY_SPAN.extend())
                .isEqualTo(NopTransactionService.TIMER);
        NopTransactionService.QUERY_SPAN.rowNavigationAttempted();
        NopTransactionService.QUERY_SPAN.incrementCurrRow();
        NopTransactionService.QUERY_SPAN.setCurrRow(0);
    }

    @Test
    public void testNopAsyncOutgoingSpan() {
        NopTransactionService.ASYNC_SPAN.stopSyncTimer();
        assertThat(NopTransactionService.ASYNC_SPAN.extendSyncTimer())
                .isEqualTo(NopTransactionService.TIMER);
    }

    @Test
    public void testNopAsyncQuerySpan() {
        NopTransactionService.ASYNC_QUERY_SPAN.stopSyncTimer();
        assertThat(NopTransactionService.ASYNC_QUERY_SPAN.extendSyncTimer())
                .isEqualTo(NopTransactionService.TIMER);
    }

    @Test
    public void testNopAuxThreadContext() {
        assertThat(NopTransactionService.AUX_THREAD_CONTEXT.start())
                .isEqualTo(NopTransactionService.LOCAL_SPAN);
        assertThat(NopTransactionService.AUX_THREAD_CONTEXT.startAndMarkAsyncTransactionComplete())
                .isEqualTo(NopTransactionService.LOCAL_SPAN);
    }
}
