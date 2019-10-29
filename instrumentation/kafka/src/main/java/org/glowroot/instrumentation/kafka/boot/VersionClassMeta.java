package org.glowroot.instrumentation.kafka.boot;

import org.glowroot.instrumentation.api.ClassInfo;
import org.glowroot.instrumentation.api.util.Reflection;

public class VersionClassMeta {

    // headers are supported starting with kafka client 0.11.0.0
    private final boolean producerSupportsHeaders;
    private final boolean consumerSupportsHeaders;

    public VersionClassMeta(ClassInfo classInfo) {
        Class<?> producerRecordClass = Reflection.getClass(
                "org.apache.kafka.clients.producer.ProducerRecord", classInfo.getLoader());
        producerSupportsHeaders = Reflection.getMethod(producerRecordClass, "headers") != null;

        Class<?> consumerRecordClass = Reflection.getClass(
                "org.apache.kafka.clients.consumer.ConsumerRecord", classInfo.getLoader());
        consumerSupportsHeaders = Reflection.getMethod(consumerRecordClass, "headers") != null;
    }

    public boolean producerSupportsHeaders() {
        return producerSupportsHeaders;
    }

    public boolean consumerSupportsHeaders() {
        return consumerSupportsHeaders;
    }
}
