package org.glowroot.instrumentation.wiremockhttpclient;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "wiremock-http-client",
            name = "Wiremock HttpClient",
            classes = {
                    WiremockHttpClientInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
