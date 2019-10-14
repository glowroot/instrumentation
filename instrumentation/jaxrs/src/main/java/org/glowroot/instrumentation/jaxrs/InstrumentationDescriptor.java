package org.glowroot.instrumentation.jaxrs;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "jaxrs",
            name = "JAX-RS",
            properties = {
                    @Descriptor.Property(
                                         name = "useAltTransactionNaming",
                                         type = PropertyType.BOOLEAN,
                                         label = "Alternate transaction naming",
                                         checkboxLabel = "Use alternate transaction naming",
                                         description = "Set transaction name to the resource's className#methodName instead of using the resource's URL mapping")
            },
            classes = {
                    JaxrsInstrumentation.class
            })
public class InstrumentationDescriptor {}
