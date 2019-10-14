package org.glowroot.instrumentation.jsp;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "jsp",
            name = "JSP",
            classes = {
                    JspInstrumentation.class
            })
public class InstrumentationDescriptor {}
