/**
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
package org.glowroot.instrumentation.jms;

import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class JmsIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = JavaagentContainer.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
    }

    @Test
    public void shouldReceiveMessage() throws Exception {
        IncomingSpan incomingSpan = container.execute(ReceiveMessage.class);
        assertThat(incomingSpan.transactionType()).isEqualTo("Background");
        assertThat(incomingSpan.transactionName()).isEqualTo("JMS Message: TestMessageListener");
        assertThat(incomingSpan.message()).isEqualTo("JMS Message: TestMessageListener");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldSendMessage() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(SendMessage.class);

        // then
        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jms send message: queue://a queue");

        // TODO implement client span for send
    }

    public static class ReceiveMessage implements AppUnderTest {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            ConnectionFactory connectionFactory =
                    new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
            Connection connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("a queue");
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.setMessageListener(new TestMessageListener());
            MessageProducer producer = session.createProducer(queue);
            Message message = session.createMessage();
            producer.send(message);
            SECONDS.sleep(1);
            connection.close();
        }
    }

    public static class SendMessage implements AppUnderTest, TransactionMarker {

        private Connection connection;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            ConnectionFactory connectionFactory =
                    new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
            connection = connectionFactory.createConnection();
            connection.start();
            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("a queue");
            MessageProducer producer = session.createProducer(queue);
            Message message = session.createMessage();
            producer.send(message);
            connection.close();
        }
    }

    static class TestMessageListener implements MessageListener {

        @Override
        public void onMessage(Message message) {}
    }
}
