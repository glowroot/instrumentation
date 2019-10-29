/*
 * Copyright 2018-2019 the original author or authors.
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
package org.glowroot.instrumentation.mongodb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MongoDbInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("mongodb query");

    private static final String QUERY_TYPE = "MongoDB";

    private static final ConfigService configService = Agent.getConfigService("mongodb");

    // visibility should be provided by memoryBarrier in
    // org.glowroot.instrumentation.api.config.ConfigService
    private static long stackTraceThresholdNanos;

    static {
        configService.registerConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                Double value = configService.getDoubleProperty("stackTraceThresholdMillis").value();
                stackTraceThresholdNanos =
                        value == null ? Long.MAX_VALUE : MILLISECONDS.toNanos(value.intValue());
            }
        });
    }

    @Advice.Pointcut(className = "com.mongodb.MongoClientOptions|com.mongodb.MongoClientSettings",
                     methodName = "getCommandListeners",
                     methodParameterTypes = {},
                     nestingGroup = "mongodb")
    public static class MongoCollectionAdvice {

        @Advice.OnMethodReturn
        public static @Nullable List<CommandListener> onReturn(
                @Bind.Return List<CommandListener> commandListeners) {

            List<CommandListener> newCommandListeners =
                    new ArrayList<CommandListener>(commandListeners);
            newCommandListeners.add(new InstrumentationCommandListener());

            return newCommandListeners;
        }
    }

    public static class InstrumentationCommandListener implements CommandListener {

        private static final BsonString MASK_VALUE = new BsonString("?");

        private final Map<Integer, QuerySpan> querySpans =
                new ConcurrentHashMap<Integer, QuerySpan>();

        @Override
        public void commandStarted(CommandStartedEvent event) {
            String commandName = event.getCommandName();
            BsonDocument command = event.getCommand();

            StringBuilder text = new StringBuilder();
            text.append(commandName);
            String collectionAttrName;
            if (commandName.equals("getMore")) {
                collectionAttrName = "collection";
            } else {
                collectionAttrName = commandName;
            }
            BsonValue collectionAttr = command.get(collectionAttrName);
            if (collectionAttr != null && collectionAttr.isString()) {
                text.append(' ');
                text.append(event.getDatabaseName());
                text.append('.');
                text.append(collectionAttr.asString().getValue());
            }

            if (commandName.equals("find")) {
                BsonValue filter = command.get("filter");
                if (filter != null && filter instanceof BsonDocument
                        && !((BsonDocument) filter).isEmpty()) {
                    text.append(' ');
                    BsonValue maskedFilter = maskFilter(filter);
                    text.append(maskedFilter.toString());
                }
            }

            ThreadContext context = Agent.getThreadContext();
            if (context != null) {
                QuerySpan querySpan = context.startQuerySpan(QUERY_TYPE, "", text.toString(),
                        QueryMessageSupplier.create(), TIMER_NAME);
                querySpans.put(event.getRequestId(), querySpan);
            }
        }

        @Override
        public void commandSucceeded(CommandSucceededEvent event) {
            QuerySpan querySpan = querySpans.remove(event.getRequestId());
            if (querySpan != null) {
                querySpan.endWithLocationStackTrace(stackTraceThresholdNanos);
            }
        }

        @Override
        public void commandFailed(CommandFailedEvent event) {
            QuerySpan querySpan = querySpans.remove(event.getRequestId());
            if (querySpan != null) {
                querySpan.endWithError(event.getThrowable());
            }
        }

        private static BsonValue maskFilter(BsonValue value) {
            if (value instanceof BsonDocument) {
                BsonDocument dest = new BsonDocument();
                for (Entry<String, BsonValue> entry : ((BsonDocument) value).entrySet()) {
                    dest.append(entry.getKey(), maskFilter(entry.getValue()));
                }
                return dest;
            } else if (value instanceof BsonArray) {
                BsonArray dest = new BsonArray();
                for (BsonValue val : (BsonArray) value) {
                    dest.add(maskFilter(val));
                }
                return dest;
            } else {
                return MASK_VALUE;
            }
        }
    }
}
