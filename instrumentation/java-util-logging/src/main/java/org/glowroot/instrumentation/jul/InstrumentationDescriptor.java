package org.glowroot.instrumentation.jul;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "java-util-logging",
            name = "java.util.logging",
            properties = {
                    @Descriptor.Property(
                                         name = "threshold",
                                         type = PropertyType.STRING,
                                         label = "Threshold",
                                         description = "Threshold to apply to logging capture. Must be one of: finest, finer, fine, config, info, warning, severe."),
                    @Descriptor.Property(
                                         name = "traceErrorOnErrorWithThrowable",
                                         type = PropertyType.BOOLEAN,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          booleanValue = true)
                                         },
                                         label = "Error with throwable",
                                         checkboxLabel = "Mark trace as error when an error is logged with a throwable",
                                         description = "Mark the entire trace as an error any time an error message is logged with a throwable."),
                    @Descriptor.Property(
                                         name = "traceErrorOnErrorWithoutThrowable",
                                         type = PropertyType.BOOLEAN,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          booleanValue = false)
                                         },
                                         label = "Error without throwable",
                                         checkboxLabel = "Mark trace as error when an error is logged without a throwable",
                                         description = "Mark the entire trace as an error any time an error message is logged without a throwable."),
                    @Descriptor.Property(
                                         name = "traceErrorOnWarningWithThrowable",
                                         type = PropertyType.BOOLEAN,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          booleanValue = false)
                                         },
                                         label = "Warning with throwable",
                                         checkboxLabel = "Mark trace as error when a warning is logged with a throwable",
                                         description = "Mark the entire trace as an error any time a warning message is logged with a throwable."),
                    @Descriptor.Property(
                                         name = "traceErrorOnWarningWithoutThrowable",
                                         type = PropertyType.BOOLEAN,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          booleanValue = false)
                                         },
                                         label = "Warning without throwable",
                                         checkboxLabel = "Mark trace as error when a warning is logged without a throwable",
                                         description = "Mark the entire trace as an error any time a warning message is logged without a throwable.")
            },
            classes = {
                    JavaUtilLoggingInstrumentation.class
            })
public class InstrumentationDescriptor {}
