/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.instrumentation.engine.weaving;

import java.util.List;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.api.Descriptor.CaptureKind;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.engine.config.AdviceConfig;
import org.glowroot.instrumentation.engine.config.ImmutableAdviceConfig;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.objectweb.asm.Opcodes.ASM7;

class InstrumentationSeekerClassVisitor extends ClassVisitor {

    private static final Logger logger =
            LoggerFactory.getLogger(InstrumentationSeekerClassVisitor.class);

    private final List<AdviceConfig> configs = Lists.newArrayList();

    private @MonotonicNonNull String owner;

    InstrumentationSeekerClassVisitor() {
        super(ASM7);
    }

    @Override
    public void visit(int version, int access, String name, @Nullable String signature,
            @Nullable String superName, String /*@Nullable*/ [] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.owner = name;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            @Nullable String signature, String /*@Nullable*/ [] exceptions) {
        return new InstrumentationAnnotationMethodVisitor(name, descriptor);
    }

    List<AdviceConfig> getConfigs() {
        return configs;
    }

    private class InstrumentationAnnotationMethodVisitor extends MethodVisitor {

        private final String methodName;
        private final String descriptor;

        private @MonotonicNonNull TransactionAnnotationVisitor transactionAnnotationVisitor;
        private @MonotonicNonNull LocalSpanAnnotationVisitor localSpanAnnotationVisitor;
        private @MonotonicNonNull TimerAnnotationVisitor timerAnnotationVisitor;

        private InstrumentationAnnotationMethodVisitor(String methodName, String descriptor) {
            super(ASM7);
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        @Override
        public @Nullable AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(
                    "Lorg/glowroot/instrumentation/annotation/api/Instrumentation$Transaction;")) {
                transactionAnnotationVisitor = new TransactionAnnotationVisitor();
                return transactionAnnotationVisitor;
            } else if (descriptor.equals(
                    "Lorg/glowroot/instrumentation/annotation/api/Instrumentation$LocalSpan;")) {
                localSpanAnnotationVisitor = new LocalSpanAnnotationVisitor();
                return localSpanAnnotationVisitor;
            } else if (descriptor.equals(
                    "Lorg/glowroot/instrumentation/annotation/api/Instrumentation$Timer;")) {
                timerAnnotationVisitor = new TimerAnnotationVisitor();
                return timerAnnotationVisitor;
            }
            return null;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            processTransaction();
            processLocalSpan();
            processTimer();
        }

        private void processTransaction() {
            if (transactionAnnotationVisitor == null) {
                return;
            }
            checkNotNull(owner);
            String transactionType = transactionAnnotationVisitor.transactionType;
            if (transactionType == null) {
                logger.error("@Instrumentation.Transaction had no transactionType attribute: {}",
                        ClassNames.fromInternalName(owner));
                return;
            }
            String transactionNameTemplate = transactionAnnotationVisitor.transactionNameTemplate;
            if (transactionNameTemplate == null) {
                logger.error(
                        "@Instrumentation.Transaction had no transactionNameTemplate attribute: {}",
                        ClassNames.fromInternalName(owner));
                return;
            }
            String traceHeadline = transactionAnnotationVisitor.traceHeadline;
            if (traceHeadline == null) {
                logger.error("@Instrumentation.Transaction had no traceHeadline attribute: {}",
                        ClassNames.fromInternalName(owner));
                return;
            }
            String timerName = transactionAnnotationVisitor.timerName;
            if (timerName == null) {
                logger.error("@Instrumentation.Transaction had no timerName attribute: {}",
                        ClassNames.fromInternalName(owner));
                return;
            }
            AlreadyInTransactionBehavior alreadyInTransactionBehavior = MoreObjects.firstNonNull(
                    transactionAnnotationVisitor.alreadyInTransactionBehavior,
                    AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
            configs.add(startBuilder()
                    .captureKind(CaptureKind.TRANSACTION)
                    .transactionType(transactionType)
                    .transactionNameTemplate(transactionNameTemplate)
                    .spanMessageTemplate(traceHeadline)
                    .timerName(timerName)
                    .alreadyInTransactionBehavior(alreadyInTransactionBehavior)
                    .build());
        }

        private void processLocalSpan() {
            if (localSpanAnnotationVisitor == null) {
                return;
            }
            checkNotNull(owner);
            String messageTemplate = localSpanAnnotationVisitor.messageTemplate;
            if (messageTemplate == null) {
                logger.error("@Instrumentation.LocalSpan had no messageTemplate attribute: {}",
                        ClassNames.fromInternalName(owner));
                return;
            }
            String timerName = localSpanAnnotationVisitor.timerName;
            if (timerName == null) {
                logger.error("@Instrumentation.LocalSpan had no timerName attribute: {}",
                        ClassNames.fromInternalName(owner));
                return;
            }
            configs.add(startBuilder()
                    .captureKind(CaptureKind.LOCAL_SPAN)
                    .spanMessageTemplate(messageTemplate)
                    .timerName(timerName)
                    .build());
        }

        private void processTimer() {
            if (timerAnnotationVisitor == null) {
                return;
            }
            checkNotNull(owner);
            String timerName = timerAnnotationVisitor.timerName;
            if (timerName == null) {
                logger.error("@Instrumentation.Timer had no value attribute: {}",
                        ClassNames.fromInternalName(owner));
                return;
            }
            configs.add(startBuilder()
                    .captureKind(CaptureKind.TIMER)
                    .timerName(timerName)
                    .build());
        }

        @RequiresNonNull("owner")
        private ImmutableAdviceConfig.Builder startBuilder() {
            Type type = Type.getObjectType(owner);
            Type[] argumentTypes = Type.getArgumentTypes(descriptor);
            ImmutableAdviceConfig.Builder builder = ImmutableAdviceConfig.builder()
                    .className(type.getClassName())
                    .methodName(methodName);
            for (Type argumentType : argumentTypes) {
                builder.addMethodParameterTypes(argumentType.getClassName());
            }
            return builder;
        }
    }

    private static class TransactionAnnotationVisitor extends AnnotationVisitor {

        private @Nullable String transactionType;
        private @Nullable String transactionNameTemplate;
        private @Nullable String traceHeadline;
        private @Nullable String timerName;
        private @Nullable AlreadyInTransactionBehavior alreadyInTransactionBehavior;

        private TransactionAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public void visit(@Nullable String name, Object value) {
            if (name == null) {
                return;
            }
            if (name.equals("transactionType")) {
                transactionType = (String) value;
            } else if (name.equals("transactionName")) {
                transactionNameTemplate = (String) value;
            } else if (name.equals("traceHeadline")) {
                traceHeadline = (String) value;
            } else if (name.equals("timer")) {
                timerName = (String) value;
            }
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if (name.equals("alreadyInTransactionBehavior")) {
                alreadyInTransactionBehavior = toProto(value);
            }
        }

        private static AlreadyInTransactionBehavior toProto(String value) {
            if (value.equals("CAPTURE_LOCAL_SPAN")) {
                return AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN;
            } else if (value.equals("CAPTURE_NEW_TRANSACTION")) {
                return AlreadyInTransactionBehavior.CAPTURE_NEW_TRANSACTION;
            } else if (value.equals("DO_NOTHING")) {
                return AlreadyInTransactionBehavior.DO_NOTHING;
            } else {
                throw new IllegalStateException(
                        "Unexpected AlreadyInTransactionBehavior: " + value);
            }
        }
    }

    private static class LocalSpanAnnotationVisitor extends AnnotationVisitor {

        private @Nullable String messageTemplate;
        private @Nullable String timerName;

        private LocalSpanAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public void visit(@Nullable String name, Object value) {
            if (name == null) {
                return;
            }
            if (name.equals("message")) {
                messageTemplate = (String) value;
            } else if (name.equals("timer")) {
                timerName = (String) value;
            }
        }
    }

    private static class TimerAnnotationVisitor extends AnnotationVisitor {

        private @Nullable String timerName;

        private TimerAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public void visit(@Nullable String name, Object value) {
            if (name == null) {
                return;
            }
            if (name.equals("value")) {
                timerName = (String) value;
            }
        }
    }
}
