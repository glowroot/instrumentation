package org.glowroot.instrumentation.axisclient;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "axis-client",
            name = "Axis Client",
            classes = {
                    AxisClientInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
