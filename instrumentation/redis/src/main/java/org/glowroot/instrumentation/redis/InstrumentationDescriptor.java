package org.glowroot.instrumentation.redis;

import org.glowroot.instrumentation.api.Descriptor;

@Descriptor(
            id = "redis",
            name = "Redis",
            classes = {
                    RedisInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
