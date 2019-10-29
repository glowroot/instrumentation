package org.glowroot.instrumentation.jms;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;

@Descriptor(
            id = "jms",
            name = "JMS",
            advice = {
                    @Descriptor.Advice(
                                       className = "javax.jms.MessageListener",
                                       methodName = "onMessage",
                                       methodParameterTypes = {
                                               "javax.jms.Message"
                                       },
                                       nestingGroup = "jms",
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "JMS Message: {{this.class.simpleName}}",
                                       timerName = "jms message"),
                    @Descriptor.Advice(
                                       className = "javax.jms.MessageProducer",
                                       methodName = "send",
                                       methodParameterTypes = {
                                               "javax.jms.Message",
                                               ".."
                                       },
                                       nestingGroup = "jms",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "jms send message: {{this.destination}}",
                                       timerName = "jms send message"),
                    @Descriptor.Advice(
                                       className = "javax.jms.MessageProducer",
                                       methodName = "send",
                                       methodParameterTypes = {
                                               "javax.jms.Destination",
                                               "javax.jms.Message",
                                               ".."
                                       },
                                       nestingGroup = "jms",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "jms send message: {{1.destination}}",
                                       timerName = "jms send message")
            })
public class InstrumentationDescriptor {}
