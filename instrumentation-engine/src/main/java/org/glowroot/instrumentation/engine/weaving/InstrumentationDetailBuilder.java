/*
 * Copyright 2018-2019 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.api.weaving.Advice.MethodModifier;
import org.glowroot.instrumentation.api.weaving.Advice.Pointcut;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptor;
import org.glowroot.instrumentation.engine.util.OnlyUsedByTests;
import org.glowroot.instrumentation.engine.weaving.InstrumentationDetail.PointcutClass;
import org.glowroot.instrumentation.engine.weaving.InstrumentationDetail.PointcutMethod;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.objectweb.asm.Opcodes.ASM7;

class InstrumentationDetailBuilder {

    private static final Logger logger =
            LoggerFactory.getLogger(InstrumentationDetailBuilder.class);

    private InstrumentationDescriptor instrumentationDescriptor;

    InstrumentationDetailBuilder(InstrumentationDescriptor instrumentationDescriptor) {
        this.instrumentationDescriptor = instrumentationDescriptor;
    }

    InstrumentationDetail build() throws IOException, ClassNotFoundException {
        ImmutableInstrumentationDetail.Builder builder = ImmutableInstrumentationDetail.builder();
        for (String clazz : instrumentationDescriptor.classes()) {
            String internalName = ClassNames.toInternalName(clazz);
            byte[] bytes = getBytes(internalName, instrumentationDescriptor.jarFile());
            InstrumentationClassVisitor cv = new InstrumentationClassVisitor(internalName);
            new ClassReader(bytes).accept(cv, ClassReader.SKIP_CODE);
            for (String innerClassName : cv.innerClassNames) {
                bytes = getBytes(innerClassName, instrumentationDescriptor.jarFile());
                MemberClassVisitor mcv = new MemberClassVisitor();
                new ClassReader(bytes).accept(mcv, ClassReader.SKIP_CODE);
                if (mcv.pointcutAnnotationVisitor != null) {
                    builder.addPointcutClasses(
                            mcv.buildPointcutClass(bytes, instrumentationDescriptor.collocate(),
                                    instrumentationDescriptor.jarFile()));
                } else if (mcv.mixinAnnotationVisitor != null) {
                    builder.addMixinTypes(
                            mcv.buildMixinType(instrumentationDescriptor.collocate(), bytes));
                } else if (mcv.shim) {
                    builder.addShimTypes(
                            mcv.buildShimType(instrumentationDescriptor.collocate()));
                }
            }
        }
        return builder.build();
    }

    static PointcutClass buildAdviceClass(byte[] bytes) {
        MemberClassVisitor acv = new MemberClassVisitor();
        new ClassReader(bytes).accept(acv, ClassReader.SKIP_CODE);
        return acv.buildPointcutClass(bytes, false, null);
    }

    static byte[] getBytes(String className, @Nullable File jarFile) throws IOException {
        String resourceName = "/" + className + ".class";
        URL url = InstrumentationDetailBuilder.class.getResource(resourceName);
        if (url != null) {
            return Resources.toByteArray(url);
        }
        if (jarFile != null) {
            url = new URL("jar:" + jarFile.toURI() + "!" + resourceName);
            return Resources.toByteArray(url);
        }
        throw new IOException("Class not found: " + className);
    }

    @OnlyUsedByTests
    static PointcutClass buildAdviceClass(Class<?> clazz) throws IOException {
        return buildAdviceClassLookAtSuperClass(ClassNames.toInternalName(clazz.getName()));
    }

    @OnlyUsedByTests
    static MixinType buildMixinType(Class<?> clazz) throws IOException {
        URL url = checkNotNull(InstrumentationDetailBuilder.class
                .getResource("/" + ClassNames.toInternalName(clazz.getName()) + ".class"));
        byte[] bytes = Resources.asByteSource(url).read();
        MemberClassVisitor mcv = new MemberClassVisitor();
        new ClassReader(bytes).accept(mcv, ClassReader.SKIP_CODE);
        return mcv.buildMixinType(false, bytes);
    }

    private static PointcutClass buildAdviceClassLookAtSuperClass(String internalName)
            throws IOException {
        URL url = checkNotNull(
                InstrumentationDetailBuilder.class.getResource("/" + internalName + ".class"));
        byte[] bytes = Resources.asByteSource(url).read();
        MemberClassVisitor mcv = new MemberClassVisitor();
        new ClassReader(bytes).accept(mcv, ClassReader.SKIP_CODE);
        ImmutablePointcutClass pointcutClass = mcv.buildPointcutClass(bytes, false, null);
        String superName = checkNotNull(mcv.superName);
        if (!"java/lang/Object".equals(superName)) {
            pointcutClass = ImmutablePointcutClass.builder()
                    .from(pointcutClass)
                    .addAllMethods(buildAdviceClassLookAtSuperClass(superName).methods())
                    .build();
        }
        return pointcutClass;
    }

    private static class InstrumentationClassVisitor extends ClassVisitor {

        private final String internalName;
        private final List<String> innerClassNames = Lists.newArrayList();

        private InstrumentationClassVisitor(String internalName) {
            super(ASM7);
            this.internalName = internalName;
        }

        @Override
        public void visitInnerClass(String name, @Nullable String outerName,
                @Nullable String innerName, int access) {
            if (internalName.equals(outerName)) {
                innerClassNames.add(name);
            }
        }
    }

    private static class MemberClassVisitor extends ClassVisitor {

        private @MonotonicNonNull String name;
        private @Nullable String superName;
        private String /*@Nullable*/ [] interfaces;
        private @Nullable PointcutAnnotationVisitor pointcutAnnotationVisitor;
        private @Nullable MixinAnnotationVisitor mixinAnnotationVisitor;
        private List<PointcutMethodVisitor> pointcutMethodVisitors = Lists.newArrayList();
        private List<MixinMethodVisitor> mixinMethodVisitors = Lists.newArrayList();
        private boolean shim;

        private MemberClassVisitor() {
            super(ASM7);
        }

        @Override
        public void visit(int version, int access, String name, @Nullable String signature,
                @Nullable String superName, String /*@Nullable*/ [] interfaces) {
            this.name = name;
            this.superName = superName;
            this.interfaces = interfaces;
        }

        @Override
        public @Nullable AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor
                    .equals("Lorg/glowroot/instrumentation/api/weaving/Advice$Pointcut;")) {
                pointcutAnnotationVisitor = new PointcutAnnotationVisitor();
                return pointcutAnnotationVisitor;
            }
            if (descriptor.equals("Lorg/glowroot/instrumentation/api/weaving/Mixin;")) {
                mixinAnnotationVisitor = new MixinAnnotationVisitor();
                return mixinAnnotationVisitor;
            }
            if (descriptor.equals("Lorg/glowroot/instrumentation/api/weaving/Shim;")) {
                shim = true;
            }
            return null;
        }

        @Override
        public @Nullable MethodVisitor visitMethod(int access, String name, String descriptor,
                @Nullable String signature, String /*@Nullable*/ [] exceptions) {
            if (pointcutAnnotationVisitor != null) {
                PointcutMethodVisitor methodVisitor = new PointcutMethodVisitor(name, descriptor);
                pointcutMethodVisitors.add(methodVisitor);
                return methodVisitor;
            }
            if (mixinAnnotationVisitor != null) {
                MixinMethodVisitor methodVisitor = new MixinMethodVisitor(name, descriptor);
                mixinMethodVisitors.add(methodVisitor);
                return methodVisitor;
            }
            return null;
        }

        private ImmutablePointcutClass buildPointcutClass(byte[] bytes,
                boolean collocateInClassLoader, @Nullable File jarFile) {
            ImmutablePointcutClass.Builder builder = ImmutablePointcutClass.builder()
                    .type(Type.getObjectType(checkNotNull(name)));
            for (PointcutMethodVisitor methodVisitor : pointcutMethodVisitors) {
                builder.addMethods(methodVisitor.build());
            }
            return builder.pointcut(checkNotNull(pointcutAnnotationVisitor).build())
                    .bytes(bytes)
                    .collocateInClassLoader(collocateInClassLoader)
                    .jarFile(jarFile)
                    .build();
        }

        private MixinType buildMixinType(boolean collocateInClassLoader, byte[] bytes) {
            String initMethodName = null;
            for (MixinMethodVisitor methodVisitor : mixinMethodVisitors) {
                if (methodVisitor.init) {
                    if (initMethodName != null) {
                        throw new IllegalStateException("@Mixin has more than one @MixinInit");
                    }
                    initMethodName = methodVisitor.name;
                }
            }
            ImmutableMixinType.Builder builder = ImmutableMixinType.builder();
            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (collocateInClassLoader
                            && !iface.endsWith(InstrumentationClassRenamer.MIXIN_SUFFIX)) {
                        // see InstrumentationClassRenamer.hack() for reason why consistent Mixin
                        // suffix is important
                        logger.warn("mixin interface name should end with \"Mixin\": {}", iface);
                    }
                    builder.addInterfaces(Type.getObjectType(iface));
                }
            }
            builder.addAllTargets(checkNotNull(mixinAnnotationVisitor).build());
            builder.initMethodName(initMethodName);
            builder.implementationBytes(bytes);
            return builder.build();
        }

        private ShimType buildShimType(boolean collocateInClassLoader)
                throws ClassNotFoundException {
            checkNotNull(name);
            if (collocateInClassLoader && !name.endsWith(InstrumentationClassRenamer.SHIM_SUFFIX)) {
                // see InstrumentationClassRenamer.hack() for reason why consistent Shim suffix is
                // important
                logger.warn("shim interface name should end with \"Shim\": {}", name);
            }
            Class<?> clazz = Class.forName(ClassNames.fromInternalName(name), false,
                    MemberClassVisitor.class.getClassLoader());
            return ShimType.create(clazz);
        }
    }

    private static class PointcutAnnotationVisitor extends AnnotationVisitor {

        private String className = "";
        private String classAnnotation = "";
        private String subTypeRestriction = "";
        private String superTypeRestriction = "";
        private String methodName = "";
        private String methodAnnotation = "";
        private List<String> methodParameterTypes = Lists.newArrayList();
        private String methodReturnType = "";
        private List<MethodModifier> methodModifiers = Lists.newArrayList();
        private String nestingGroup = "";
        private int order;
        private String suppressibleUsingKey = "";
        private String suppressionKey = "";

        private PointcutAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public void visit(@Nullable String name, Object value) {
            if ("className".equals(name)) {
                className = (String) value;
            } else if ("classAnnotation".equals(name)) {
                classAnnotation = (String) value;
            } else if ("subTypeRestriction".equals(name)) {
                subTypeRestriction = (String) value;
            } else if ("superTypeRestriction".equals(name)) {
                superTypeRestriction = (String) value;
            } else if ("methodName".equals(name)) {
                methodName = (String) value;
            } else if ("methodAnnotation".equals(name)) {
                methodAnnotation = (String) value;
            } else if ("methodReturnType".equals(name)) {
                methodReturnType = (String) value;
            } else if ("nestingGroup".equals(name)) {
                nestingGroup = (String) value;
            } else if ("order".equals(name)) {
                order = (Integer) value;
            } else if ("suppressibleUsingKey".equals(name)) {
                suppressibleUsingKey = (String) value;
            } else if ("suppressionKey".equals(name)) {
                suppressionKey = (String) value;
            } else {
                throw new IllegalStateException(
                        "Unexpected @Advice.Pointcut attribute name: " + name);
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("methodParameterTypes".equals(name)) {
                return new StringArrayAnnotationVisitor(methodParameterTypes);
            } else if ("methodModifiers".equals(name)) {
                return new MethodModifierArrayAnnotationVisitor(methodModifiers);
            } else {
                throw new IllegalStateException(
                        "Unexpected @Advice.Pointcut attribute name: " + name);
            }
        }

        private Pointcut build() {
            return new Pointcut() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return Pointcut.class;
                }
                @Override
                public String className() {
                    return className;
                }
                @Override
                public String classAnnotation() {
                    return classAnnotation;
                }
                @Override
                public String subTypeRestriction() {
                    return subTypeRestriction;
                }
                @Override
                public String superTypeRestriction() {
                    return superTypeRestriction;
                }
                @Override
                public String methodName() {
                    return methodName;
                }
                @Override
                public String methodAnnotation() {
                    return methodAnnotation;
                }
                @Override
                public String[] methodParameterTypes() {
                    return Iterables.toArray(methodParameterTypes, String.class);
                }
                @Override
                public String methodReturnType() {
                    return methodReturnType;
                }
                @Override
                public MethodModifier[] methodModifiers() {
                    return Iterables.toArray(methodModifiers, MethodModifier.class);
                }
                @Override
                public String nestingGroup() {
                    return nestingGroup;
                }
                @Override
                public int order() {
                    return order;
                }
                @Override
                public String suppressibleUsingKey() {
                    return suppressibleUsingKey;
                }
                @Override
                public String suppressionKey() {
                    return suppressionKey;
                }
            };
        }
    }

    private static class PointcutMethodVisitor extends MethodVisitor {

        private final String name;
        private final String descriptor;
        private final List<Type> annotationTypes = Lists.newArrayList();
        private final Map<Integer, ImmutableBindAnnotation.Builder> bindAnnotations =
                Maps.newHashMap();

        private PointcutMethodVisitor(String name, String descriptor) {
            super(ASM7);
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public @Nullable AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            annotationTypes.add(Type.getType(descriptor));
            return null;
        }

        @Override
        public @Nullable AnnotationVisitor visitParameterAnnotation(int parameter,
                String descriptor, boolean visible) {

            if (!descriptor.startsWith("Lorg/glowroot/instrumentation/api/weaving/Bind$")) {
                return null;
            }
            if (bindAnnotations.containsKey(parameter)) {
                throw new IllegalStateException(
                        "More than one bind annotation found on a method parameter: " + name
                                + ", parameter: " + parameter);
            }
            ImmutableBindAnnotation.Builder bindAnnotation = ImmutableBindAnnotation.builder()
                    .type(Type.getType(descriptor));
            bindAnnotations.put(parameter, bindAnnotation);
            if (descriptor
                    .equals("Lorg/glowroot/instrumentation/api/weaving/Bind$Argument;")) {
                return new BindAnnotationVisitor(bindAnnotation);
            } else {
                bindAnnotation.argIndex(-1);
                return null;
            }
        }

        private PointcutMethod build() {
            ImmutablePointcutMethod.Builder builder = ImmutablePointcutMethod.builder()
                    .name(name)
                    .descriptor(descriptor)
                    .addAllAnnotationTypes(annotationTypes);
            for (Map.Entry<Integer, ImmutableBindAnnotation.Builder> entry : bindAnnotations
                    .entrySet()) {
                builder.putBindAnnotations(entry.getKey(), entry.getValue().build());
            }
            return builder.build();
        }
    }

    private static class BindAnnotationVisitor extends AnnotationVisitor {

        private final ImmutableBindAnnotation.Builder bindAnnotation;

        private BindAnnotationVisitor(ImmutableBindAnnotation.Builder bindAnnotation) {
            super(ASM7);
            this.bindAnnotation = bindAnnotation;
        }

        @Override
        public void visit(@Nullable String name, Object value) {
            if ("value".equals(name)) {
                bindAnnotation.argIndex((Integer) value);
            } else {
                throw new IllegalStateException(
                        "Unexpected @Bind.Argument attribute name: " + name);
            }
        }
    }

    private static class MixinAnnotationVisitor extends AnnotationVisitor {

        private MixinAnnotationVisitor() {
            super(ASM7);
        }

        private List<String> values = Lists.newArrayList();

        @Override
        public void visit(@Nullable String name, Object value) {
            throw new IllegalStateException("Unexpected @Mixin attribute name: " + name);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("value".equals(name)) {
                return new StringArrayAnnotationVisitor(values);
            } else {
                throw new IllegalStateException("Unexpected @Mixin attribute name: " + name);
            }
        }

        private List<String> build() {
            return values;
        }
    }

    private static class MixinMethodVisitor extends MethodVisitor {

        private final String name;
        private final String descriptor;

        private boolean init;

        private MixinMethodVisitor(String name, String descriptor) {
            super(ASM7);
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public @Nullable AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals("Lorg/glowroot/instrumentation/api/weaving/MixinInit;")) {
                if (Type.getArgumentTypes(this.descriptor).length > 0) {
                    throw new IllegalStateException("@MixinInit method cannot have any parameters");
                }
                if (!Type.getReturnType(this.descriptor).equals(Type.VOID_TYPE)) {
                    throw new IllegalStateException("@MixinInit method must return void");
                }
                init = true;
            }
            return null;
        }
    }

    private static class StringArrayAnnotationVisitor extends AnnotationVisitor {

        private final List<String> list;

        private StringArrayAnnotationVisitor(List<String> list) {
            super(ASM7);
            this.list = list;
        }

        @Override
        public void visit(@Nullable String name, Object value) {
            list.add((String) value);
        }
    }

    private static class MethodModifierArrayAnnotationVisitor extends AnnotationVisitor {

        private final List<MethodModifier> list;

        private MethodModifierArrayAnnotationVisitor(List<MethodModifier> list) {
            super(ASM7);
            this.list = list;
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            list.add(MethodModifier.valueOf(value));
        }
    }
}
