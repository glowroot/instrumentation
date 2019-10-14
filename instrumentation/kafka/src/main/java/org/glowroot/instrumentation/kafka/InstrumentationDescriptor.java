package org.glowroot.instrumentation.kafka;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

@Descriptor(
            id = "kafka",
            name = "Kafka",
            advice = {
                    @Descriptor.Advice(
                                       className = "org.apache.kafka.clients.consumer.KafkaConsumer",
                                       methodName = "poll",
                                       methodParameterTypes = {
                                               "long"
                                       },
                                       nestingGroup = "kafka-poll",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "kafka poll => {{_.count}}",
                                       timerName = "kafka poll")
            },
            classes = {
                    ProducerInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
