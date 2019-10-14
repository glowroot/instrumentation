package org.glowroot.instrumentation.netty;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "netty",
            name = "Netty",
            classes = {
                    Netty3xInstrumentation.class,
                    Netty4xInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
