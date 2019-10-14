package org.glowroot.instrumentation.jaxws;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "jaxws",
            name = "JAX-WS",
            properties = {
                    @Descriptor.Property(
                                         name = "useAltTransactionNaming",
                                         type = PropertyType.BOOLEAN,
                                         label = "Alternate transaction naming",
                                         checkboxLabel = "Use alternate transaction naming",
                                         description = "Set transaction name to the service's className#methodName instead of using the service's URL mapping")
            },
            classes = {
                    JaxwsInstrumentation.class
            })
public class InstrumentationDescriptor {}
