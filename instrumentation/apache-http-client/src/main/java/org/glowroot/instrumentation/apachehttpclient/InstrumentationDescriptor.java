package org.glowroot.instrumentation.apachehttpclient;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "apache-http-client",
            name = "Apache HttpClient",
            classes = {
                    ApacheHttpClient4xInstrumentation.class,
                    ApacheHttpClient3xInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
