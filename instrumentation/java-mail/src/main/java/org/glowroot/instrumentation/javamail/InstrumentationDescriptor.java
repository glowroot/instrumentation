package org.glowroot.instrumentation.javamail;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

@Descriptor(
            id = "mail",
            name = "Mail",
            advice = {
                    @Descriptor.Advice(
                                       className = "javax.mail.Service",
                                       methodName = "connect",
                                       methodParameterTypes = {
                                               ".."
                                       },
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "mail connect {{this.uRLName}}",
                                       timerName = "mail"),
                    @Descriptor.Advice(
                                       className = "javax.mail.Transport",
                                       methodName = "sendMessage",
                                       methodParameterTypes = {
                                               ".."
                                       },
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "mail send message",
                                       timerName = "mail")
            })
public class InstrumentationDescriptor {}
