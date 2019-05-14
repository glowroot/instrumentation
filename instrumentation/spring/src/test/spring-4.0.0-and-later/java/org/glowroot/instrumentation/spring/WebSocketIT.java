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
package org.glowroot.instrumentation.spring;

import java.io.Serializable;
import java.net.URI;
import java.nio.ByteBuffer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.glowroot.instrumentation.spring.InvokeSpringControllerInTomcat.RunnableWithPort;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;

public class WebSocketIT {

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
    public void shouldCaptureTransactionNameHittingWebSocket() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForTypeAndName(HittingWebSocket.class, "Web",
                "/websocket/app/abc", "", "abc");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/websocket/app/abc");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathHittingWebSocket() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForTypeAndName(HittingWebSocket.class, "Web",
                "/zzz/websocket/app/abc", "/zzz", "abc");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz/websocket/app/abc");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureTransactionNameHittingWebSocketWithPropertyController()
            throws Exception {
        // property based mapping is not supported until Spring 4.2.x
        String springVersion = WebSocketMessageBrokerConfigurer.class.getPackage()
                .getImplementationVersion();
        assumeFalse(springVersion.startsWith("4.0.") || springVersion.startsWith("4.1."));

        // when
        IncomingSpan incomingSpan = container.executeForTypeAndName(HittingWebSocket.class, "Web",
                "/websocket/app/xyz", "", "xyz");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/websocket/app/xyz");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    @Test
    public void shouldCaptureTransactionNameWithContextPathHittingWebSocketWithPropertyController()
            throws Exception {
        // property based mapping is not supported until Spring 4.2.x
        String springVersion = WebSocketMessageBrokerConfigurer.class.getPackage()
                .getImplementationVersion();
        assumeFalse(springVersion.startsWith("4.0.") || springVersion.startsWith("4.1."));

        // when
        IncomingSpan incomingSpan = container.executeForTypeAndName(HittingWebSocket.class, "Web",
                "/zzz/websocket/app/xyz", "/zzz", "xyz");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("/zzz/websocket/app/xyz");
        assertThat(incomingSpan.childSpans()).isEmpty();
    }

    public static class HittingWebSocket extends InvokeSpringControllerInTomcat {

        @Override
        public void executeApp(Serializable... args) throws Exception {
            String contextPath = (String) args[0];
            String path = (String) args[1];
            executeApp("webapp1", contextPath, new WebSocketTest(contextPath, path));
        }
    }

    private static class WebSocketTest implements RunnableWithPort {

        private final String contextPath;
        private final String path;

        private WebSocketTest(String contextPath, String path) {
            this.contextPath = contextPath;
            this.path = path;
        }

        @Override
        public void run(int port) throws Exception {
            WebSocketClient client = new MyWebSocketClient(
                    new URI("ws://localhost:" + port + contextPath + "/websocket"), path);
            client.connect();
            synchronized (client) {
                client.wait(30000);
            }
        }
    }

    @Controller
    public static class TestWebSocketController {

        @MessageMapping("/abc")
        @SendTo("/destination/abc")
        public String echo(String message) {
            return message + " world";
        }
    }

    @Controller
    public static class TestWebSocketWithPropertyController {

        @MessageMapping("${xyz.path:/xyz}")
        @SendTo("/destination/xyz")
        public String abc(String message) {
            return message + " world";
        }
    }

    private static class MyWebSocketClient extends WebSocketClient {

        private final String path;

        public MyWebSocketClient(URI serverUri, String path) {
            super(serverUri);
            this.path = path;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            send("CONNECT\n\n\0");
            send("SUBSCRIBE\n"
                    + "destination:/destination/" + path + "\n"
                    + "id: 0\n\n\0");
            send("SEND\n"
                    + "destination:/app/" + path + "\n"
                    + "content-type:text/plain;charset=UTF-8\n"
                    + "content-length:5\n\n"
                    + "hello\0");
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {}

        @Override
        public void onMessage(String message) {
            synchronized (this) {
                this.notifyAll();
            }
        }

        @Override
        public void onMessage(ByteBuffer message) {}

        @Override
        public void onError(Exception e) {
            e.printStackTrace();
        }
    }
}
