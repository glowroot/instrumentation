package org.glowroot.instrumentation.spring;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "spring",
            name = "Spring",
            properties = {
                    @Descriptor.Property(
                                         name = "useAltTransactionNaming",
                                         type = PropertyType.BOOLEAN,
                                         label = "Alternate transaction naming",
                                         checkboxLabel = "Use alternate transaction naming",
                                         description = "Set transaction name to the controller's className#methodName instead of using the controller's URL mapping")
            },
            advice = {
                    @Descriptor.Advice(
                                       className = "org.springframework.batch.core.Step",
                                       methodName = "execute",
                                       methodParameterTypes = {
                                               "org.springframework.batch.core.StepExecution"
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "Spring Batch: {{this.name}}",
                                       timerName = "spring batch"),
                    @Descriptor.Advice(
                                       className = "org.springframework.jms.listener.SessionAwareMessageListener",
                                       methodName = "onMessage",
                                       methodParameterTypes = {
                                               "javax.jms.Message",
                                               "javax.jms.Session"
                                       },
                                       nestingGroup = "jms",
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "JMS Message: {{this.class.simpleName}}",
                                       timerName = "jms message"),
                    @Descriptor.Advice(
                                       classAnnotation = "org.springframework.stereotype.Component|org.springframework.stereotype.Controller|org.springframework.stereotype.Repository|org.springframework.stereotype.Service|org.springframework.web.bind.annotation.RestController",
                                       methodAnnotation = "org.springframework.scheduling.annotation.Scheduled",
                                       methodParameterTypes = {
                                               ".."
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "Spring scheduled: {{this.class.simpleName}}#{{methodName}}",
                                       spanMessageTemplate = "Spring scheduled: {{this.class.name}}.{{methodName}}()",
                                       timerName = "spring scheduled"),
                    @Descriptor.Advice(
                                       classAnnotation = "org.springframework.stereotype.Component|org.springframework.stereotype.Service",
                                       methodAnnotation = "org.springframework.amqp.rabbit.annotation.RabbitListener",
                                       methodParameterTypes = {
                                               ".."
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Background",
                                       transactionNameTemplate = "Spring amqp: {{this.class.simpleName}}#{{methodName}}",
                                       timerName = "spring amqp")
            },
            classes = {
                    WebInstrumentation.class
            })
public class InstrumentationDescriptor {}
