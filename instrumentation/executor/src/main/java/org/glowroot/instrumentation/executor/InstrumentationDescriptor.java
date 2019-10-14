package org.glowroot.instrumentation.executor;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "executor",
            name = "Executor",
            classes = {
                    ExecutorInstrumentation.class
            })
public class InstrumentationDescriptor {}
