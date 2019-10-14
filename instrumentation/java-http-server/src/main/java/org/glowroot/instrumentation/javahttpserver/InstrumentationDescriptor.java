package org.glowroot.instrumentation.javahttpserver;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "java-http-server",
            name = "Java HTTP Server",
            properties = {
                    @Descriptor.Property(
                                         name = "captureRequestHeaders",
                                         type = PropertyType.LIST,
                                         label = "Capture request headers",
                                         description = "List of request headers to capture in the root trace entry. The wildcard '*' is supported anywhere in the header name."),
                    @Descriptor.Property(
                                         name = "maskRequestHeaders",
                                         type = PropertyType.LIST,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          listValue = {
                                                                                  "Authorization"})
                                         },
                                         label = "Mask request headers",
                                         description = "List of sensitive request headers to mask, e.g. credentials. The wildcard '*' is supported anywhere in the header name."),
                    @Descriptor.Property(
                                         name = "captureRequestRemoteAddr",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request remote address",
                                         checkboxLabel = "Capture request remote address using HttpExchange.getRemoteAddress().getAddress().getHostAddress()"),
                    @Descriptor.Property(
                                         name = "captureRequestRemoteHost",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request remote host",
                                         checkboxLabel = "Capture request remote host using HttpExchange.getRemoteAddress().getHostName()"),
                    @Descriptor.Property(
                                         name = "captureResponseHeaders",
                                         type = PropertyType.LIST,
                                         label = "Capture response headers",
                                         description = "List of response headers to capture in the root trace entry. The wildcard '*' is supported anywhere in the header name."),
                    @Descriptor.Property(
                                         name = "traceErrorOn4xxResponseCode",
                                         type = PropertyType.BOOLEAN,
                                         label = "Error on 4xx",
                                         checkboxLabel = "Mark trace as error on 4xx response code",
                                         description = "Mark the trace as an error when a 4xx response code is returned.")
            },
            classes = {
                    JavaHttpServerInstrumentation.class
            })
public class InstrumentationDescriptor {}
