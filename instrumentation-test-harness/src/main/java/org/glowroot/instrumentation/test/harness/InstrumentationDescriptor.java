package org.glowroot.instrumentation.test.harness;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

@Descriptor(
            id = "instrumentation-test-harness",
            name = "Test Harness",
            advice = {
                    @Descriptor.Advice(
                                       className = "org.glowroot.instrumentation.test.harness.TransactionMarker",
                                       methodName = "transactionMarker",
                                       methodParameterTypes = {
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Test harness",
                                       transactionNameTemplate = "trace marker / {{this.class.simpleName}}",
                                       timerName = "mock trace marker"),
                    @Descriptor.Advice(
                                       className = "org.glowroot.instrumentation.test.harness.TestSpans",
                                       methodName = "createLocalSpan",
                                       methodParameterTypes = {
                                               ".."
                                       },
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "test local span",
                                       timerName = "test local span")
            })
public class InstrumentationDescriptor {}
