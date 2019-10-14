package org.glowroot.instrumentation.grails;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "grails",
            name = "Grails",
            classes = {
                    GrailsInstrumentation.class
            })
public class InstrumentationDescriptor {}
