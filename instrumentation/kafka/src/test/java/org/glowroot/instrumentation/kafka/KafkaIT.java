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
package org.glowroot.instrumentation.kafka;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.google.common.base.Stopwatch;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleOutgoingSpanMessage;

public class KafkaIT {

    private static int kafkaPort;

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        kafkaPort = KafkaWrapper.start();
        container = Containers.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
        KafkaWrapper.stop();
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
    }

    @Test
    public void shouldSend() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(SendRecord.class, kafkaPort);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan).isEqualTo("kafka send: demo");
    }

    @Test
    public void shouldPoll() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(PollRecord.class, kafkaPort);

        // then
        List<Span> spans = incomingSpan.childSpans();

        for (int i = 0; i < spans.size() - 1; i++) {
            assertThat(spans.get(0).message()).isEqualTo("kafka poll => 0");
        }

        assertThat(spans.get(spans.size() - 1).message()).isEqualTo("kafka poll => 1");
    }

    public static class SendRecord implements AppUnderTest, TransactionMarker {

        private Producer<Long, String> producer;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            producer = createProducer((Integer) args[0]);
            transactionMarker();
            producer.close();
        }

        @Override
        public void transactionMarker() throws Exception {
            ProducerRecord<Long, String> record =
                    new ProducerRecord<Long, String>("demo", "message");
            producer.send(record).get();
        }

        private static Producer<Long, String> createProducer(int kafkaPort) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:" + kafkaPort);
            props.put(ProducerConfig.CLIENT_ID_CONFIG, "client1");
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    StringSerializer.class.getName());
            return new KafkaProducer<Long, String>(props);
        }
    }

    public static class PollRecord implements AppUnderTest, TransactionMarker {

        private Consumer<Long, String> consumer;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            Integer kafkaPort = (Integer) args[0];
            Producer<Long, String> producer = SendRecord.createProducer(kafkaPort);
            ProducerRecord<Long, String> record =
                    new ProducerRecord<Long, String>("demo", "message");
            producer.send(record).get();
            producer.close();

            consumer = createConsumer(kafkaPort);
            transactionMarker();
            consumer.close();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void transactionMarker() throws Exception {
            ConsumerRecords<Long, String> consumerRecords = consumer.poll(100);
            Stopwatch stopwatch = Stopwatch.createStarted();
            while (consumerRecords.count() == 0 && stopwatch.elapsed(SECONDS) < 5) {
                consumerRecords = consumer.poll(100);
            }
            if (consumerRecords.count() == 0) {
                throw new IllegalStateException("Record not found");
            }
            consumer.commitAsync();
        }

        private static Consumer<Long, String> createConsumer(int kafkaPort) {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:" + kafkaPort);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "consumerGroup1");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    LongDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                    StringDeserializer.class.getName());
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            Consumer<Long, String> consumer = new KafkaConsumer<Long, String>(props);
            consumer.subscribe(Collections.singletonList("demo"));
            return consumer;
        }
    }
}
