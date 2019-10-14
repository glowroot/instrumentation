package org.glowroot.instrumentation.asynchttpclient;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "async-http-client",
            name = "AsyncHttpClient",
            classes = {
                    AsyncHttpClient1xInstrumentation.class,
                    AsyncHttpClient2xInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
