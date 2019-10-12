/*
 * Copyright 2017-2019 the original author or authors.
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
package org.glowroot.instrumentation.servlet;

import java.lang.management.ManagementFactory;

import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class ContainerStartup {

    private static final Logger logger = Logger.getLogger(ContainerStartup.class);

    private static final Getter<Object> GETTER = new NopGetter();

    private static final Object REQUEST = new Object();

    private ContainerStartup() {}

    static Span onBeforeCommon(@Nullable String path, TimerName timerName,
            OptionalThreadContext context) {
        initPlatformMBeanServer();
        String transactionName;
        if (path == null || path.isEmpty()) {
            // root context path is empty "", but makes more sense to display "/"
            transactionName = "Servlet context: /";
        } else {
            transactionName = "Servlet context: " + path;
        }
        Span span = context.startIncomingSpan("Startup", transactionName, GETTER, REQUEST,
                MessageSupplier.create(transactionName), timerName,
                AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
        context.setTransactionSlowThreshold(0, MILLISECONDS, Priority.CORE_INSTRUMENTATION);
        return span;
    }

    static void initPlatformMBeanServer() {
        // make sure the platform mbean server gets created so that it can then be retrieved by
        // LazyPlatformMBeanServer which may be waiting for it to be created (the current
        // thread context class loader should have access to the platform mbean server that is set
        // via the javax.management.builder.initial system property)
        try {
            ManagementFactory.getPlatformMBeanServer();
        } catch (Throwable t) {
            logger.error("could not create platform mbean server: {}", t.getMessage(), t);
        }
    }

    private static class NopGetter implements Getter<Object> {

        @Override
        public @Nullable String get(Object carrier, String key) {
            return null;
        }
    }
}
