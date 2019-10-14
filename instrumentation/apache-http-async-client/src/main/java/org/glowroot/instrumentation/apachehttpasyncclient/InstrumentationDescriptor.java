package org.glowroot.instrumentation.apachehttpasyncclient;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "apache-http-async-client",
            name = "Apache HttpAsyncClient",
            classes = {
                    ApacheHttpAsyncClientInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
