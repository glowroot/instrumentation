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

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.ParameterHolder;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.kafka.boot.VersionClassMeta;

public class ProducerInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("kafka send");

    private static final Setter<ProducerRecord<?, ?>> SETTER = new ProducerRecordSetter();
    private static final Setter<ProducerRecord<?, ?>> NOP_SETTER = new NopSetter();

    @Advice.Pointcut(className = "org.apache.kafka.clients.producer.KafkaProducer",
                     methodName = "send",
                     methodParameterTypes = {"org.apache.kafka.clients.producer.ProducerRecord",
                             "org.apache.kafka.clients.producer.Callback"},
                     nestingGroup = "kafka-send")
    public static class SendAdvice {

        @Advice.OnMethodBefore
        public static @Nullable AsyncSpan onBefore(
                @Bind.Argument(0) @Nullable ProducerRecord<?, ?> record,
                @Bind.Argument(1) ParameterHolder<Callback> callbackHolder,
                @Bind.ClassMeta VersionClassMeta versionClassMeta,
                ThreadContext context) {

            if (record == null) {
                return null;
            }
            String topic = record.topic();
            if (topic == null) {
                topic = "";
            }
            Setter<ProducerRecord<?, ?>> setter =
                    versionClassMeta.producerSupportsHeaders() ? SETTER : NOP_SETTER;
            AsyncSpan span = context.startAsyncOutgoingSpan("Kafka", topic, setter, record,
                    MessageSupplier.create("kafka send: {}", topic), TIMER_NAME);
            Callback callback = callbackHolder.get();
            if (callback == null) {
                callbackHolder.set(new CallbackWrapperForNullDelegate(span));
            } else {
                callbackHolder.set(new CallbackWrapper(callback, span,
                        context.createAuxThreadContext()));
            }
            return span;
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable AsyncSpan span) {

            if (span != null) {
                span.stopSyncTimer();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable AsyncSpan span) {

            if (span != null) {
                span.stopSyncTimer();
                span.endWithError(t);
            }
        }
    }

    private static class ProducerRecordSetter implements Setter<ProducerRecord<?, ?>> {

        @Override
        public void put(ProducerRecord<?, ?> carrier, String key, String value) {
            try {
                carrier.headers().add(key, value.getBytes("UTF-8"));
            } catch (IllegalStateException e) {
                // TODO log once
            } catch (UnsupportedEncodingException e) {
                // TODO log once
            }
        }
    }

    private static class NopSetter implements Setter<ProducerRecord<?, ?>> {

        @Override
        public void put(ProducerRecord<?, ?> carrier, String key, String value) {}
    }
}
