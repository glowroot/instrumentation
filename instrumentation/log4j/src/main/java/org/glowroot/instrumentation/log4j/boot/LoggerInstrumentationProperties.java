/*
 * Copyright 2014-2019 the original author or authors.
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
package org.glowroot.instrumentation.log4j.boot;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigService;

public class LoggerInstrumentationProperties {

    private static final ConfigService configService = Agent.getConfigService("log4j");

    private static final BooleanProperty traceErrorOnWarningWithThrowable =
            configService.getBooleanProperty("traceErrorOnWarningWithThrowable");
    private static final BooleanProperty traceErrorOnWarningWithoutThrowable =
            configService.getBooleanProperty("traceErrorOnWarningWithoutThrowable");
    private static final BooleanProperty traceErrorOnErrorWithThrowable =
            configService.getBooleanProperty("traceErrorOnErrorWithThrowable");
    private static final BooleanProperty traceErrorOnErrorWithoutThrowable =
            configService.getBooleanProperty("traceErrorOnErrorWithoutThrowable");

    private LoggerInstrumentationProperties() {}

    public static boolean markTraceAsError(boolean isErrorOrHigher, boolean isWarnOrHigher,
            boolean throwable) {
        if (isErrorOrHigher) {
            return throwable ? traceErrorOnErrorWithThrowable.value()
                    : traceErrorOnErrorWithoutThrowable.value();
        }
        if (isWarnOrHigher) {
            return throwable ? traceErrorOnWarningWithThrowable.value()
                    : traceErrorOnWarningWithoutThrowable.value();
        }
        return false;
    }
}
