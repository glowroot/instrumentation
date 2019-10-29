package org.glowroot.instrumentation.camel;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

@Descriptor(
            id = "camel",
            name = "Camel",
            advice = {
                    @Descriptor.Advice(
                                       className = "org.apache.camel.processor.Pipeline",
                                       methodName = "process",
                                       methodParameterTypes = {
                                               "org.apache.camel.Exchange",
                                               ".."
                                       },
                                       nestingGroup = "camel-process",
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "Camel pipeline",
                                       spanMessageTemplate = "camel pipeline => {{_}}",
                                       timerName = "camel pipeline")
            })
public class InstrumentationDescriptor {}
