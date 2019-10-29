package org.glowroot.instrumentation.quartz;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

@Descriptor(
            id = "quartz",
            name = "Quartz",
            advice = {
                    @Descriptor.Advice(
                                       className = "org.quartz.Job",
                                       methodName = "execute",
                                       methodParameterTypes = {
                                               "org.quartz.JobExecutionContext"
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "Quartz job: {{0.jobDetail.name}}",
                                       timerName = "quartz job")
            })
public class InstrumentationDescriptor {}
