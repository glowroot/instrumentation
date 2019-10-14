package org.glowroot.instrumentation.okhttp;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "okhttp",
            name = "OkHttp",
            classes = {
                    OkHttp2xInstrumentation.class,
                    OkHttp3xInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
