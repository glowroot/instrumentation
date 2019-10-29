package org.glowroot.instrumentation.ejb;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;

@Descriptor(
            id = "ejb",
            name = "EJB",
            advice = {
                    @Descriptor.Advice(
                                       classAnnotation = "javax.ejb.Singleton",
                                       methodAnnotation = "javax.ejb.Timeout|javax.ejb.Schedule|javax.ejb.Schedules",
                                       methodParameterTypes = {
                                               ".."
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "EJB timer: {{this.class.simpleName}}#{{methodName}}",
                                       spanMessageTemplate = "EJB timer: {{this.class.name}}.{{methodName}}()",
                                       timerName = "ejb timer")
            })
public class InstrumentationDescriptor {}
