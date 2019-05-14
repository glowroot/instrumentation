/*
 * Copyright 2017-2019 the original author or authors.
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
package org.glowroot.instrumentation.jaxws;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class WebServiceIT {

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
    public void shouldCaptureTransactionNameWithNormalServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp1", "", "/hello");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("POST /hello#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$HelloService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp1", "/zzz", "/hello");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("POST /zzz/hello#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$HelloService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingHittingRoot() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp1", "", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("POST /#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingHittingRootWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp1", "/zzz", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("POST /zzz/#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp2", "", "/service/hello");

        // then
        assertThat(incomingSpan.transactionName())
                .isEqualTo("POST /service/hello#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$HelloService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMappingWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp2", "/zzz", "/service/hello");

        // then
        assertThat(incomingSpan.transactionName())
                .isEqualTo("POST /zzz/service/hello#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$HelloService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMappingHittingRoot() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp2", "", "/service/");

        // then
        assertThat(incomingSpan.transactionName())
                .isEqualTo("POST /service/#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMappingHittingRootWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp2", "/zzz", "/service/");

        // then
        assertThat(incomingSpan.transactionName())
                .isEqualTo("POST /zzz/service/#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp3", "", "/hello");

        // then
        if (!incomingSpan.transactionName().equals("POST /hello#echo")) {
            // Jersey (2.5 and above) doesn't like this "less than normal" servlet mapping, and ends
            // up mapping everything to RootService
            assertThat(incomingSpan.transactionName()).isEqualTo("POST #echo");
        }

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMappingWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp3", "/zzz", "/hello");

        // then
        if (!incomingSpan.transactionName().equals("POST /zzz/hello#echo")) {
            // Jersey (2.5 and above) doesn't like this "less than normal" servlet mapping, and ends
            // up mapping everything to RootService
            assertThat(incomingSpan.transactionName()).isEqualTo("POST /zzz#echo");
        }

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMappingHittingRoot()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp3", "", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("POST /#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMappingHittingRootWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp3", "/zzz", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("POST /zzz/#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$RootService.echo()");
    }

    @Test
    public void shouldCaptureAltTransactionName() throws Exception {
        // given
        container.setInstrumentationProperty("jaxws", "useAltTransactionNaming", true);

        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxwsWebServiceInTomcat.class,
                "Web", "webapp1", "", "/hello");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("HelloService#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxws service:"
                + " org.glowroot.instrumentation.jaxws.WebServiceIT$HelloService.echo()");
    }

    @WebService
    public static class HelloService {

        @WebMethod
        public String echo(@WebParam(name = "param") String msg) {
            return msg;
        }
    }

    @WebService
    public static class RootService {

        @WebMethod
        public String echo(@WebParam(name = "param") String msg) {
            return msg;
        }
    }
}
