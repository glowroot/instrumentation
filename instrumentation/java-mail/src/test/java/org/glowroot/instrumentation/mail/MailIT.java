/**
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
package org.glowroot.instrumentation.mail;

import java.io.Serializable;
import java.util.Iterator;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;

public class MailIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.create();
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
    public void shouldSendMessage() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteSend.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).startsWith("mail connect smtp://");

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("mail send message");

        assertThat(i.hasNext()).isFalse();
    }

    public static class ExecuteSend extends DoMail {

        @Override
        public void transactionMarker() {
            GreenMailUtil.sendTextEmailTest("to@localhost.com", "from@localhost.com",
                    "some subject", "some body");
        }
    }

    private abstract static class DoMail implements AppUnderTest, TransactionMarker {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            ServerSetup serverSetup = ServerSetupTest.SMTP;
            serverSetup.setServerStartupTimeout(5000);
            GreenMail greenMail = new GreenMail(serverSetup); // uses test ports by default
            greenMail.start();
            try {
                transactionMarker();
            } finally {
                greenMail.stop();
            }
        }
    }
}
