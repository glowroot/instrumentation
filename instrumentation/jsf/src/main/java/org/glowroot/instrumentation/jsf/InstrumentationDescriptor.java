package org.glowroot.instrumentation.jsf;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

@Descriptor(
            id = "jsf",
            name = "JSF",
            advice = {
                    @Descriptor.Advice(
                                       className = "com.sun.faces.lifecycle.ApplyRequestValuesPhase",
                                       methodName = "execute",
                                       methodParameterTypes = {
                                               "javax.faces.context.FacesContext"
                                       },
                                       nestingGroup = "jsf",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "jsf apply request: {{0.viewRoot.viewId}}",
                                       timerName = "jsf apply request"),
                    @Descriptor.Advice(
                                       className = "com.sun.faces.application.ActionListenerImpl",
                                       methodName = "processAction",
                                       methodParameterTypes = {
                                               "javax.faces.event.ActionEvent"
                                       },
                                       nestingGroup = "jsf",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "jsf invoke: {{0.component.action.expressionString}}",
                                       timerName = "jsf invoke"),
                    @Descriptor.Advice(
                                       className = "com.sun.faces.lifecycle.RenderResponsePhase",
                                       methodName = "execute",
                                       methodParameterTypes = {
                                               "javax.faces.context.FacesContext"
                                       },
                                       nestingGroup = "jsf",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "jsf render: {{0.viewRoot.viewId}}",
                                       timerName = "jsf render")
            })
public class InstrumentationDescriptor {}
