package org.glowroot.instrumentation.hibernate;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

@Descriptor(
            id = "hibernate",
            name = "Hibernate",
            advice = {
                    @Descriptor.Advice(
                                       className = "org.hibernate.Criteria",
                                       methodName = "list",
                                       methodParameterTypes = {
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate query"),
                    @Descriptor.Advice(
                                       className = "javax.persistence.TypedQuery",
                                       subTypeRestriction = "org.hibernate.*",
                                       methodName = "getResultList",
                                       methodParameterTypes = {
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate query"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Transaction",
                                       methodName = "commit",
                                       methodParameterTypes = {
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "hibernate commit",
                                       timerName = "hibernate commit"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Transaction",
                                       methodName = "rollback",
                                       methodParameterTypes = {
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "hibernate rollback",
                                       timerName = "hibernate rollback"),
                    @Descriptor.Advice(
                                       className = "javax.persistence.EntityTransaction",
                                       subTypeRestriction = "org.hibernate.Transaction",
                                       methodName = "commit",
                                       methodParameterTypes = {
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "hibernate commit",
                                       timerName = "hibernate commit"),
                    @Descriptor.Advice(
                                       className = "javax.persistence.EntityTransaction",
                                       subTypeRestriction = "org.hibernate.Transaction",
                                       methodName = "rollback",
                                       methodParameterTypes = {
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "hibernate rollback",
                                       timerName = "hibernate rollback"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "flush",
                                       methodParameterTypes = {
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.LOCAL_SPAN,
                                       spanMessageTemplate = "hibernate flush",
                                       timerName = "hibernate flush"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "saveOrUpdate",
                                       methodParameterTypes = {
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate saveOrUpdate"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "saveOrUpdate",
                                       methodParameterTypes = {
                                               "java.lang.String",
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate saveOrUpdate"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "save",
                                       methodParameterTypes = {
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate save"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "save",
                                       methodParameterTypes = {
                                               "java.lang.String",
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate save"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "merge",
                                       methodParameterTypes = {
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate merge"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "merge",
                                       methodParameterTypes = {
                                               "java.lang.String",
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate merge"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "update",
                                       methodParameterTypes = {
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate update"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "update",
                                       methodParameterTypes = {
                                               "java.lang.String",
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate update"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "persist",
                                       methodParameterTypes = {
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate persist"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "persist",
                                       methodParameterTypes = {
                                               "java.lang.String",
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate persist"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "delete",
                                       methodParameterTypes = {
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate delete"),
                    @Descriptor.Advice(
                                       className = "org.hibernate.Session",
                                       methodName = "delete",
                                       methodParameterTypes = {
                                               "java.lang.String",
                                               "java.lang.Object"
                                       },
                                       nestingGroup = "hibernate",
                                       captureKind = CaptureKind.TIMER,
                                       timerName = "hibernate delete")
            })
public class InstrumentationDescriptor {}
