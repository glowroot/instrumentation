/*
 * Copyright 2016-2019 the original author or authors.
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
package org.glowroot.instrumentation.jaxrs;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.glowroot.instrumentation.test.harness.util.HarnessAssertions.assertSingleLocalSpanMessage;

public class ResourceIT {

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
    public void shouldCaptureTransactionNameWithSimpleServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "", "/simple");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /simple");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$SimpleResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithSimpleServletMappingWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "/zzz", "/simple");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/simple");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$SimpleResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "", "/hello/1");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /hello/*");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$HelloResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "/zzz", "/hello/1");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/hello/*");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$HelloResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingHittingRoot() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNormalServletMappingHittingRootWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "/zzz", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp2", "", "/rest/hello/1");

        // then
        assertThat(incomingSpan.transactionName())
                .isEqualTo("GET /rest/hello/*");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$HelloResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMappingWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp2", "/zzz", "/rest/hello/1");

        // then
        assertThat(incomingSpan.transactionName())
                .isEqualTo("GET /zzz/rest/hello/*");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$HelloResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMappingHittingRoot() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp2", "", "/rest/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /rest/");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithNestedServletMappingHittingRootWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp2", "/zzz", "/rest/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/rest/");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMapping() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp3", "", "/hello/1");

        // then
        if (incomingSpan.transactionName().equals("GET /")) {
            // Jersey (2.5 and above) doesn't like this "less than normal" servlet mapping, and ends
            // up mapping everything to RootResource
            assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                    + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
        } else {
            assertThat(incomingSpan.transactionName()).isEqualTo("GET /hello/*");

            assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                    + " org.glowroot.instrumentation.jaxrs.ResourceIT$HelloResource.echo()");
        }
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMappingWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp3", "/zzz", "/hello/1");

        // then
        if (incomingSpan.transactionName().equals("GET /zzz/")) {
            // Jersey (2.5 and above) doesn't like this "less than normal" servlet mapping, and ends
            // up mapping everything to RootResource
            assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                    + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
        } else {
            assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/hello/*");

            assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                    + " org.glowroot.instrumentation.jaxrs.ResourceIT$HelloResource.echo()");
        }
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMappingHittingRoot()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp3", "", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
    }

    @Test
    public void shouldCaptureTransactionNameWithLessNormalServletMappingHittingRootWithContextPath()
            throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp3", "/zzz", "/");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /zzz/");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$RootResource.echo()");
    }

    @Test
    public void shouldCaptureAltTransactionName() throws Exception {
        // given
        container.setInstrumentationProperty("jaxrs", "useAltTransactionNaming", true);

        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "", "/hello/1");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("HelloResource#echo");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$HelloResource.echo()");
    }

    @Test
    public void shouldCaptureWhenInterfaceAnnotated() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "", "/another/1");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /another/*");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$AnotherResourceImpl.echo()");
    }

    @Test
    public void shouldCaptureSubResource() throws Exception {
        // when
        IncomingSpan incomingSpan = container.executeForType(InvokeJaxrsResourceInTomcat.class,
                "Web", "webapp1", "", "/parent/child/grandchild/1");

        // then
        assertThat(incomingSpan.transactionName()).isEqualTo("GET /parent/child/grandchild/*");

        assertSingleLocalSpanMessage(incomingSpan).isEqualTo("jaxrs resource:"
                + " org.glowroot.instrumentation.jaxrs.ResourceIT$GrandchildResourceImpl"
                + ".echo()");
    }

    @Path("simple")
    public static class SimpleResource {

        @GET
        public Response echo() {
            return Response.status(200).entity("hi").build();
        }
    }

    @Path("hello")
    public static class HelloResource {

        @GET
        @Path("{param}")
        public Response echo(@PathParam("param") String msg) {
            return Response.status(200).entity(msg).build();
        }
    }

    @Path("/")
    public static class RootResource {

        @GET
        public Response echo() {
            return Response.status(200).build();
        }
    }

    @Path("another")
    public static class AnotherResourceImpl implements AnotherResource {

        private final AnotherResource delegate = new DelegateAnotherResource();

        @Override
        public Response echo(String msg) {
            return delegate.echo(msg);
        }
    }

    @Path("parent")
    public static class ParentResourceImpl implements ParentResource {

        private final ParentResource delegate = new DelegateParentResource();

        @Override
        public ChildResource getChildResource() {
            return delegate.getChildResource();
        }
    }

    public static class DelegateAnotherResource implements AnotherResource {

        @Override
        public Response echo(String msg) {
            return Response.status(200).entity(msg).build();
        }
    }

    public static class DelegateParentResource implements ParentResource {

        @Override
        public ChildResource getChildResource() {
            return new ChildResourceImpl();
        }
    }

    public static class ChildResourceImpl implements ChildResource {

        @Override
        public GrandchildResource getGrandchildResource() {
            return new GrandchildResourceImpl();
        }
    }

    public static class GrandchildResourceImpl implements GrandchildResource {

        @Override
        public Response echo(String msg) {
            return Response.status(200).entity(msg).build();
        }
    }

    public interface AnotherResource {

        @GET
        @Path("{param}")
        Response echo(@PathParam("param") String msg);
    }

    public interface ParentResource {

        @Path("child")
        ChildResource getChildResource();
    }

    public interface ChildResource {

        @Path("grandchild")
        GrandchildResource getGrandchildResource();
    }

    public interface GrandchildResource {

        @GET
        @Path("{param}")
        Response echo(@PathParam("param") String msg);
    }
}
