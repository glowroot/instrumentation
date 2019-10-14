package org.glowroot.instrumentation.struts;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "struts",
            name = "Struts",
            classes = {
                    Struts1xInstrumentation.class,
                    Struts2xInstrumentation.class
            })
public class InstrumentationDescriptor {}
