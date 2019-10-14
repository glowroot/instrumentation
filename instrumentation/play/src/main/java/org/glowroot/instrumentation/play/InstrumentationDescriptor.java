package org.glowroot.instrumentation.play;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "play",
            name = "Play",
            properties = {
                    @Descriptor.Property(
                                         name = "useAltTransactionNaming",
                                         type = PropertyType.BOOLEAN,
                                         label = "Alternate transaction naming",
                                         checkboxLabel = "Use alternate transaction naming",
                                         description = "Set transaction name to the controller's className#methodName instead of using the route's URL mapping")
            },
            advice = {
                    @Descriptor.Advice(
                                       className = "play.mvc.Controller",
                                       methodName = "renderTemplate",
                                       methodParameterTypes = {
                                               "java.lang.String",
                                               "java.util.Map"
                                       },
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "play render: {{0}}",
                                       timerName = "play render")
            },
            classes = {
                    Play1xInstrumentation.class,
                    Play2xInstrumentation.class
            })
public class InstrumentationDescriptor {}
