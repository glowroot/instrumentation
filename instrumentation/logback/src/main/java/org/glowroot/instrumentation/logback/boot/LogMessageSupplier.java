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
package org.glowroot.instrumentation.logback.boot;

import java.util.HashMap;
import java.util.Map;

import org.glowroot.instrumentation.api.Message;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.checker.Nullable;

public class LogMessageSupplier extends MessageSupplier {

    private final String messageText;
    private final @Nullable String level;
    private final String loggerName;

    public LogMessageSupplier(String messageText, @Nullable String level, String loggerName) {
        this.messageText = messageText;
        this.level = level;
        this.loggerName = loggerName;
    }

    @Override
    public Message get() {
        Map<String, Object> detail = new HashMap<String, Object>(2);
        if (level != null) {
            detail.put("Level", level);
        }
        detail.put("Logger name", loggerName);
        return Message.create(messageText, detail);
    }
}
