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

import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.internal.InstrumentationService;
import org.glowroot.instrumentation.api.internal.InstrumentationServiceHolder;

public class Agent {

    private static final InstrumentationService service = InstrumentationServiceHolder.get();

    private Agent() {}

    public static TimerName getTimerName(String name) {
        return service.getTimerName(name);
    }

    /**
     * Returns the {@code ConfigService} instance for the specified {@code instrumentationId}.
     * 
     * The return value can (and should) be cached by the instrumentation for the life of the jvm to
     * avoid looking it up every time it is needed (which is often).
     */
    public static ConfigService getConfigService(String instrumentationId) {
        return service.getConfigService(instrumentationId);
    }

    public static @Nullable ThreadContext getThreadContext() {
        return service.getThreadContext();
    }
}
