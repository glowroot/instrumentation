package org.glowroot.instrumentation.log4j;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "log4j",
            name = "Log4j",
            properties = {
                    @Descriptor.Property(
                                         name = "threshold",
                                         type = PropertyType.STRING,
                                         label = "Threshold",
                                         description = "Threshold to apply to logging capture. Must be one of: trace, debug, info, warn, error, fatal."),
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
                    Log4j1xInstrumentation.class,
                    Log4j2xInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
