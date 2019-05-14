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

import io.netty.handler.codec.http.HttpMethod;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyContext;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.server.HttpServer;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.TestSpans;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class WebFluxIT {

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
    public void shouldCapture() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(HittingWebFlux.class);

        // then
        assertThat(incomingSpan.message()).isEqualTo("GET /webflux/abc");

        assertSingleLocalSpanMessage(incomingSpan).matches("test local span");
    }

    public static class HittingWebFlux implements AppUnderTest {
        @Override
        public void executeApp(Serializable... args) throws Exception {
            int port = Ports.getAvailable();
            NettyContext nettyContext = HttpServer.create("localhost", port)
                    .newHandler(new ReactorHttpHandlerAdapter(new MyHttpHandler()))
                    .block();

            WebClient client = WebClient.create("http://localhost:" + port);
            client.get()
                    .uri("/webflux/abc")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            nettyContext.dispose();
        }
    }

    private static class MyHttpHandler implements HttpHandler {
        @Override
        public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
            TestSpans.createLocalSpan();
            HttpClient httpClient = HttpClient.create();
            return httpClient.request(HttpMethod.GET, "http://example.org", req -> {
                return req.send();
            }).doOnError(t -> {
                t.printStackTrace();
            }).doOnNext(res -> {
                response.writeWith(Mono.just(response.bufferFactory().wrap("xyzo".getBytes())));
            }).then();
        }
    }
}
