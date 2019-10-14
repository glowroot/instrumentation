package org.glowroot.instrumentation.httpurlconnection;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "http-url-connection",
            name = "HttpURLConnection",
            classes = {
                    HttpURLConnectionInstrumentation.class
            })
public class InstrumentationDescriptor {}
