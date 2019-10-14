package org.glowroot.instrumentation.mongodb;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "mongodb",
            name = "MongoDB",
            properties = {
                    @Descriptor.Property(
                                         name = "stackTraceThresholdMillis",
                                         type = PropertyType.DOUBLE,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          doubleValue = 1000.0)
                                         },
                                         label = "Stack trace threshold (millis)",
                                         description = "Any query that exceeds this threshold will have a stack trace captured and attached to it. An empty value will not collect any stack traces, a zero value will collect a stack trace for every query.")
            },
            classes = {
                    DBCollectionInstrumentation.class,
                    MongoCollectionInstrumentation.class,
                    MongoIterableInstrumentation.class,
                    MongoCursorInstrumentation.class
            })
public class InstrumentationDescriptor {}
