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
package org.glowroot.instrumentation.axisclient;

import java.io.Serializable;
import java.net.URL;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleOutgoingSpanMessage;

public class AxisClientIT {

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
    public void shouldCaptureAxisCall() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ExecuteSoapRequest.class);

        // then
        assertSingleOutgoingSpanMessage(incomingSpan).matches("http client request:"
                + " POST http://localhost:[0-9]+/cxf/helloWorld");
    }

    public static class ExecuteSoapRequest implements AppUnderTest, TransactionMarker {

        private int port;

        @Override
        public void executeApp(Serializable... args) throws Exception {
            port = Ports.getAvailable();
            Endpoint.publish("http://localhost:" + port + "/cxf/helloWorld", new HelloWorldImpl());
            JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
            factory.setServiceClass(HelloWorld.class);
            factory.setAddress("http://localhost:" + port + "/cxf/helloWorld");

            transactionMarker();
        }

        @Override
        public void transactionMarker() throws Exception {
            String endpoint = "http://localhost:" + port + "/cxf/helloWorld";

            Service service = new Service();
            Call call = (Call) service.createCall();

            call.setTargetEndpointAddress(new URL(endpoint));
            call.setPortTypeName(new QName("http://axisclient.instrumentation.glowroot.org/",
                    "HelloWorld"));
            call.setOperationName(
                    new QName("http://axisclient.instrumentation.glowroot.org/", "hello"));

            call.invoke(new Object[0]);
        }
    }

    @WebService
    @SOAPBinding(style = SOAPBinding.Style.RPC)
    public interface HelloWorld {

        @WebMethod
        String hello();
    }

    @WebService(endpointInterface = "org.glowroot.instrumentation.axisclient.AxisClientIT"
            + "$HelloWorld")
    public static class HelloWorldImpl implements HelloWorld {

        @Override
        public String hello() {
            return "Hello!";
        }
    }
}
