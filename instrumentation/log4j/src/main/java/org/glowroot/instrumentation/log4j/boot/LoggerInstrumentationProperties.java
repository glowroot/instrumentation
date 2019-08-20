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

import java.util.Locale;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;

public class LoggerInstrumentationProperties {

    private static final Logger logger = Logger.getLogger(LoggerInstrumentationProperties.class);

    private static final ConfigService configService = Agent.getConfigService("log4j");

    private static int log4j1xThreshold;
    private static int log4j2xThreshold;

    private static final BooleanProperty traceErrorOnWarningWithThrowable =
            configService.getBooleanProperty("traceErrorOnWarningWithThrowable");
    private static final BooleanProperty traceErrorOnWarningWithoutThrowable =
            configService.getBooleanProperty("traceErrorOnWarningWithoutThrowable");
    private static final BooleanProperty traceErrorOnErrorWithThrowable =
            configService.getBooleanProperty("traceErrorOnErrorWithThrowable");
    private static final BooleanProperty traceErrorOnErrorWithoutThrowable =
            configService.getBooleanProperty("traceErrorOnErrorWithoutThrowable");

    static {
        configService.registerConfigListener(new ConfigListenerImpl());
    }

    private LoggerInstrumentationProperties() {}

    public static boolean captureLog4j1xLevel(int level) {
        return level >= log4j1xThreshold;
    }

    public static boolean captureLog4j2xLevel(int level) {
        return level <= log4j2xThreshold;
    }

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

    private static class ConfigListenerImpl implements ConfigListener {

        @Override
        public void onChange() {
            String thresholdStr = configService.getStringProperty("threshold").value()
                    .toLowerCase(Locale.ENGLISH);
            if (thresholdStr.equals("")) {
                log4j1xThreshold = Integer.MIN_VALUE;
                log4j2xThreshold = Integer.MAX_VALUE;
            } else if (thresholdStr.equals("fatal")) {
                log4j1xThreshold = Log4j1xLevel.FATAL;
                log4j2xThreshold = Log4j2xLevel.FATAL;
            } else if (thresholdStr.equals("error")) {
                log4j1xThreshold = Log4j1xLevel.ERROR;
                log4j2xThreshold = Log4j2xLevel.ERROR;
            } else if (thresholdStr.equals("warn")) {
                log4j1xThreshold = Log4j1xLevel.WARN;
                log4j2xThreshold = Log4j2xLevel.WARN;
            } else if (thresholdStr.equals("info")) {
                log4j1xThreshold = Log4j1xLevel.INFO;
                log4j2xThreshold = Log4j2xLevel.INFO;
            } else if (thresholdStr.equals("debug")) {
                log4j1xThreshold = Log4j1xLevel.DEBUG;
                log4j2xThreshold = Log4j2xLevel.DEBUG;
            } else if (thresholdStr.equals("trace")) {
                log4j1xThreshold = Log4j1xLevel.DEBUG;
                log4j2xThreshold = Log4j2xLevel.TRACE;
            } else {
                logger.warn("unexpected configuration for threshold: {}", thresholdStr);
                log4j1xThreshold = Integer.MIN_VALUE;
                log4j2xThreshold = Integer.MAX_VALUE;
            }
        }
    }
}
