/**
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
package org.glowroot.instrumentation.redis;

import redis.clients.jedis.Connection;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class RedisInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("redis");

    private static final Setter<Object> SETTER = new NopSetter();

    private static final Object CARRIER = new Object();

    @Advice.Pointcut(className = "redis.clients.jedis.Connection",
                     methodName = "sendCommand",
                     methodParameterTypes = {"*", ".."},
                     nestingGroup = "redis")
    public static class SendCommandAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(
                @Bind.This Connection connection,
                @Bind.Argument(0) Object command,
                ThreadContext context) {

            String cmd = nullToEmpty(command.toString());
            return context.startOutgoingSpan("Redis", cmd, SETTER, CARRIER,
                    MessageSupplier.create("redis {}:{} {}", connection.getHost(),
                            Integer.toString(connection.getPort()), cmd),
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.end();
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "redis.clients.jedis.Connection",
                     methodName = "connect",
                     methodParameterTypes = {},
                     nestingGroup = "redis")
    public static class ConnectAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(@Bind.This Connection connection, ThreadContext context) {

            return context.startOutgoingSpan("Redis", "CONNECT", SETTER, CARRIER,
                    MessageSupplier.create("redis {}:{} CONNECT", connection.getHost(),
                            Integer.toString(connection.getPort())),
                    TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.end();
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    private static String nullToEmpty(@Nullable String string) {
        return string == null ? "" : string;
    }

    private static class NopSetter implements Setter<Object> {

        @Override
        public void put(Object carrier, String key, String value) {}
    }
}
