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
package org.glowroot.instrumentation.kafka;

import java.io.UnsupportedEncodingException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.kafka.boot.VersionClassMeta;

public class ConsumerInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("kafka consumer");

    private static final Getter<ConsumerRecord<?, ?>> GETTER = new ConsumerRecordGetter();
    private static final Getter<ConsumerRecord<?, ?>> NOP_GETTER = new NopGetter();

    @Advice.Pointcut(className = "org.springframework.kafka.listener.GenericMessageListener",
                     subTypeRestriction = "org.springframework.kafka.listener.MessageListener",
                     methodName = "onMessage",
                     methodParameterTypes = {
                             ".."
                     },
                     nestingGroup = "kafka-consumer")
    public static class ConsumeAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.Argument(0) @Nullable Object recordObj,
                @Bind.ClassMeta VersionClassMeta versionClassMeta,
                OptionalThreadContext context) {

            if (!(recordObj instanceof ConsumerRecord)) {
                return null;
            }
            ConsumerRecord<?, ?> record = (ConsumerRecord<?, ?>) recordObj;
            String topic = record.topic();
            if (topic == null) {
                topic = "";
            }
            Getter<ConsumerRecord<?, ?>> getter =
                    versionClassMeta.consumerSupportsHeaders() ? GETTER : NOP_GETTER;
            return context.startIncomingSpan("Background", "Kafka consumer: " + topic, getter,
                    record, MessageSupplier.create("kafka consumer: {}", topic), TIMER_NAME,
                    AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    private static class ConsumerRecordGetter implements Getter<ConsumerRecord<?, ?>> {

        @Override
        public @Nullable String get(ConsumerRecord<?, ?> carrier, String key) {
            Header header = carrier.headers().lastHeader(key);
            if (header == null) {
                return null;
            } else {
                try {
                    return new String(header.value(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // TODO log once
                    return null;
                }
            }
        }
    }

    private static class NopGetter implements Getter<ConsumerRecord<?, ?>> {

        @Override
        public @Nullable String get(ConsumerRecord<?, ?> carrier, String key) {
            return null;
        }
    }
}
