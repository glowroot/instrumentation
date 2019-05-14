/*
 * Copyright 2012-2019 the original author or authors.
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
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.weaving.Advice.IsEnabled;
import org.glowroot.instrumentation.api.weaving.Advice.OnMethodAfter;
import org.glowroot.instrumentation.api.weaving.Advice.OnMethodBefore;
import org.glowroot.instrumentation.api.weaving.Advice.OnMethodReturn;
import org.glowroot.instrumentation.api.weaving.Advice.OnMethodThrow;
import org.glowroot.instrumentation.api.weaving.Advice.Pointcut;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.engine.weaving.Advice.AdviceParameter;
import org.glowroot.instrumentation.engine.weaving.Advice.ParameterKind;
import org.glowroot.instrumentation.engine.weaving.ClassLoaders.LazyDefinedClass;
import org.glowroot.instrumentation.engine.weaving.InstrumentationDetail.BindAnnotation;
import org.glowroot.instrumentation.engine.weaving.InstrumentationDetail.PointcutClass;
import org.glowroot.instrumentation.engine.weaving.InstrumentationDetail.PointcutMethod;

import static com.google.common.base.Preconditions.checkNotNull;

class AdviceBuilder {

    private static final Type IsEnabledType = Type.getType(IsEnabled.class);
    private static final Type OnBeforeType = Type.getType(OnMethodBefore.class);
    private static final Type OnReturnType = Type.getType(OnMethodReturn.class);
    private static final Type OnThrowType = Type.getType(OnMethodThrow.class);
    private static final Type OnAfterType = Type.getType(OnMethodAfter.class);

    private static final Type ThreadContextType = Type.getType(ThreadContext.class);
    private static final Type OptionalThreadContextType = Type.getType(OptionalThreadContext.class);

    private static final Type BindReceiverType = Type.getType(Bind.This.class);
    private static final Type BindParameterType = Type.getType(Bind.Argument.class);
    private static final Type BindParameterArrayType = Type.getType(Bind.AllArguments.class);
    private static final Type BindMethodNameType = Type.getType(Bind.MethodName.class);
    private static final Type BindReturnType = Type.getType(Bind.Return.class);
    private static final Type BindOptionalReturnType = Type.getType(Bind.OptionalReturn.class);
    private static final Type BindThrowableType = Type.getType(Bind.Thrown.class);
    private static final Type BindTravelerType = Type.getType(Bind.Enter.class);
    private static final Type BindClassMetaType = Type.getType(Bind.ClassMeta.class);
    private static final Type BindMethodMetaType = Type.getType(Bind.MethodMeta.class);

    private static final Type StringType = Type.getType(String.class);
    private static final Type ThrowableType = Type.getType(Throwable.class);

    private static final ImmutableList<Type> isEnabledBindAnnotationTypes =
            ImmutableList.of(BindReceiverType, BindParameterType, BindParameterArrayType,
                    BindMethodNameType, BindClassMetaType, BindMethodMetaType);
    private static final ImmutableList<Type> onBeforeBindAnnotationTypes =
            ImmutableList.of(BindReceiverType, BindParameterType, BindParameterArrayType,
                    BindMethodNameType, BindClassMetaType, BindMethodMetaType);
    private static final ImmutableList<Type> onReturnBindAnnotationTypes =
            ImmutableList.of(BindReceiverType, BindParameterType, BindParameterArrayType,
                    BindMethodNameType, BindReturnType, BindOptionalReturnType, BindTravelerType,
                    BindClassMetaType, BindMethodMetaType);
    private static final ImmutableList<Type> onThrowBindAnnotationTypes =
            ImmutableList.of(BindReceiverType, BindParameterType, BindParameterArrayType,
                    BindMethodNameType, BindThrowableType, BindTravelerType, BindClassMetaType,
                    BindMethodMetaType);
    private static final ImmutableList<Type> onAfterBindAnnotationTypes =
            ImmutableList.of(BindReceiverType, BindParameterType, BindParameterArrayType,
                    BindMethodNameType, BindReturnType, BindThrowableType, BindTravelerType,
                    BindClassMetaType, BindMethodMetaType);

    private static final ImmutableMap<Type, ParameterKind> parameterKindMap =
            new ImmutableMap.Builder<Type, ParameterKind>()
                    .put(BindReceiverType, ParameterKind.RECEIVER)
                    .put(BindParameterType, ParameterKind.METHOD_ARG)
                    .put(BindParameterArrayType, ParameterKind.METHOD_ARG_ARRAY)
                    .put(BindMethodNameType, ParameterKind.METHOD_NAME)
                    .put(BindReturnType, ParameterKind.RETURN)
                    .put(BindOptionalReturnType, ParameterKind.OPTIONAL_RETURN)
                    .put(BindThrowableType, ParameterKind.THROWABLE)
                    .put(BindTravelerType, ParameterKind.TRAVELER)
                    .put(BindClassMetaType, ParameterKind.CLASS_META)
                    .put(BindMethodMetaType, ParameterKind.METHOD_META)
                    .build();

    private final ImmutableAdvice.Builder builder = ImmutableAdvice.builder();

    private final @Nullable PointcutClass adviceClass;
    private final @Nullable LazyDefinedClass lazyAdviceClass;

    private boolean hasIsEnabledAdvice;
    private boolean hasOnBeforeAdvice;
    private boolean hasOnReturnAdvice;
    private boolean hasOnThrowAdvice;
    private boolean hasOnAfterAdvice;

    AdviceBuilder(PointcutClass adviceClass) {
        this.adviceClass = adviceClass;
        this.lazyAdviceClass = null;
        builder.reweavable(false);
    }

    AdviceBuilder(LazyDefinedClass lazyAdviceClass, boolean reweavable) {
        this.adviceClass = null;
        this.lazyAdviceClass = lazyAdviceClass;
        builder.reweavable(reweavable);
    }

    Advice build() throws Exception {
        PointcutClass adviceClass = this.adviceClass;
        if (adviceClass == null) {
            // safe check, if adviceClass is null then lazyAdviceClass is non-null
            checkNotNull(lazyAdviceClass);
            adviceClass = InstrumentationDetailBuilder.buildAdviceClass(lazyAdviceClass.bytes());
        }
        Pointcut pointcut = adviceClass.pointcut();
        checkNotNull(pointcut, "Class has no @Advice.Pointcut annotation");
        builder.pointcut(pointcut);
        builder.adviceType(adviceClass.type());
        builder.pointcutClassNamePattern(MaybePatterns.buildPattern(pointcut.className()));
        builder.pointcutClassAnnotationPattern(
                MaybePatterns.buildPattern(pointcut.classAnnotation()));
        builder.pointcutSubTypeRestrictionPattern(
                MaybePatterns.buildPattern(pointcut.subTypeRestriction()));
        builder.pointcutSuperTypeRestrictionPattern(
                MaybePatterns.buildPattern(pointcut.superTypeRestriction()));
        builder.pointcutMethodNamePattern(MaybePatterns.buildPattern(pointcut.methodName()));
        builder.pointcutMethodAnnotationPattern(
                MaybePatterns.buildPattern(pointcut.methodAnnotation()));
        builder.pointcutMethodParameterTypes(buildPatterns(pointcut.methodParameterTypes()));

        // hasBindThreadContext will be overridden below if needed
        builder.hasBindThreadContext(false);
        // hasBindOptionalThreadContext will be overridden below if needed
        builder.hasBindOptionalThreadContext(false);
        for (PointcutMethod adviceMethod : adviceClass.methods()) {
            if (adviceMethod.annotationTypes().contains(IsEnabledType)) {
                initIsEnabledAdvice(adviceClass, adviceMethod);
            } else if (adviceMethod.annotationTypes().contains(OnBeforeType)) {
                initOnBeforeAdvice(adviceClass, adviceMethod);
            } else if (adviceMethod.annotationTypes().contains(OnReturnType)) {
                initOnReturnAdvice(adviceClass, adviceMethod);
            } else if (adviceMethod.annotationTypes().contains(OnThrowType)) {
                initOnThrowAdvice(adviceClass, adviceMethod);
            } else if (adviceMethod.annotationTypes().contains(OnAfterType)) {
                initOnAfterAdvice(adviceClass, adviceMethod);
            }
        }
        if (adviceClass.collocateInClassLoader()) {
            InstrumentationClassRenamer classRenamer = new InstrumentationClassRenamer(adviceClass);
            builder.nonBootstrapLoaderAdviceClass(
                    classRenamer.buildNonBootstrapLoaderAdviceClass());
            builder.nonBootstrapLoaderAdvice(
                    classRenamer.buildNonBootstrapLoaderAdvice(builder.build()));
        }
        Advice advice = builder.build();
        if (pointcut.methodName().equals("<init>") && advice.onBeforeAdvice() != null
                && advice.hasBindOptionalThreadContext()) {
            // this is because of the way @Advice.OnMethodBefore advice is handled on constructors,
            // see WeavingMethodVisitory.invokeOnBefore()
            throw new IllegalStateException("@BindOptionalThreadContext is not allowed in a"
                    + " @Advice.Pointcut with methodName \"<init>\" that has an @Advice.OnMethodBefore"
                    + " method");
        }
        if (pointcut.methodName().equals("<init>") && advice.isEnabledAdvice() != null) {
            for (AdviceParameter parameter : advice.isEnabledParameters()) {
                if (parameter.kind() == ParameterKind.RECEIVER) {
                    // @Advice.IsEnabled is called before the super constructor is called, so "this"
                    // is not
                    // available yet
                    throw new IllegalStateException(
                            "@Advice.This is not allowed on @Advice.IsEnabled for"
                                    + " a @Advice.Pointcut with methodName \"<init>\"");
                }
            }
        }
        return advice;
    }

    private void initIsEnabledAdvice(PointcutClass adviceClass, PointcutMethod adviceMethod)
            throws AdviceConstructionException {
        checkState(!hasIsEnabledAdvice, "@Advice.Pointcut '" + adviceClass.type().getClassName()
                + "' has more than one @Advice.IsEnabled method");
        Method asmMethod = adviceMethod.toAsmMethod();
        checkState(asmMethod.getReturnType().getSort() == Type.BOOLEAN,
                "@Advice.IsEnabled method must return boolean");
        builder.isEnabledAdvice(asmMethod);
        List<AdviceParameter> parameters = getAdviceParameters(adviceMethod.bindAnnotations(),
                asmMethod.getArgumentTypes(), isEnabledBindAnnotationTypes, IsEnabledType);
        for (int i = 1; i < parameters.size(); i++) {
            checkState(parameters.get(i).kind() != ParameterKind.OPTIONAL_THREAD_CONTEXT,
                    "OptionalThreadContext must be the first argument to @Advice.IsEnabled");
        }
        builder.addAllIsEnabledParameters(parameters);
        hasIsEnabledAdvice = true;
    }

    private void initOnBeforeAdvice(PointcutClass adviceClass, PointcutMethod adviceMethod)
            throws AdviceConstructionException {
        checkState(!hasOnBeforeAdvice, "@Advice.Pointcut '" + adviceClass.type().getClassName()
                + "' has more than one @Advice.OnMethodBefore method");
        Method asmMethod = adviceMethod.toAsmMethod();
        builder.onBeforeAdvice(asmMethod);
        List<AdviceParameter> parameters = getAdviceParameters(adviceMethod.bindAnnotations(),
                asmMethod.getArgumentTypes(), onBeforeBindAnnotationTypes, OnBeforeType);
        for (int i = 1; i < parameters.size(); i++) {
            checkState(parameters.get(i).kind() != ParameterKind.OPTIONAL_THREAD_CONTEXT,
                    "OptionalThreadContext must be the first argument to @Advice.OnMethodBefore");
        }
        builder.addAllOnBeforeParameters(parameters);
        if (asmMethod.getReturnType().getSort() != Type.VOID) {
            builder.travelerType(asmMethod.getReturnType());
        }
        checkForBindThreadContext(parameters);
        checkForBindOptionalThreadContext(parameters);
        hasOnBeforeAdvice = true;
    }

    private void initOnReturnAdvice(PointcutClass adviceClass, PointcutMethod adviceMethod)
            throws AdviceConstructionException {
        checkState(!hasOnReturnAdvice, "@Advice.Pointcut '" + adviceClass.type().getClassName()
                + "' has more than one @Advice.OnMethodReturn method");
        Method asmMethod = adviceMethod.toAsmMethod();
        List<AdviceParameter> parameters = getAdviceParameters(adviceMethod.bindAnnotations(),
                asmMethod.getArgumentTypes(), onReturnBindAnnotationTypes, OnReturnType);
        for (int i = 1; i < parameters.size(); i++) {
            checkState(parameters.get(i).kind() != ParameterKind.RETURN,
                    "@Advice.Return must be the first argument to @Advice.OnMethodReturn");
            checkState(parameters.get(i).kind() != ParameterKind.OPTIONAL_RETURN,
                    "@Advice.OptionalReturn must be the first argument to @Advice.OnMethodReturn");
        }
        boolean isReturnArg =
                !parameters.isEmpty() && (parameters.get(0).kind() == ParameterKind.RETURN
                        || parameters.get(0).kind() == ParameterKind.OPTIONAL_RETURN);
        int startIndex = isReturnArg ? 2 : 1;
        for (int i = startIndex; i < parameters.size(); i++) {
            checkState(parameters.get(i).kind() != ParameterKind.OPTIONAL_THREAD_CONTEXT,
                    "OptionalThreadContext must be the first argument to @Advice.OnMethodReturn"
                            + " (or second argument if @Advice.Return is present)");
        }
        builder.onReturnAdvice(asmMethod);
        builder.addAllOnReturnParameters(parameters);
        checkForBindThreadContext(parameters);
        checkForBindOptionalThreadContext(parameters);
        hasOnReturnAdvice = true;
    }

    private void initOnThrowAdvice(PointcutClass adviceClass, PointcutMethod adviceMethod)
            throws AdviceConstructionException {
        checkState(!hasOnThrowAdvice, "@Advice.Pointcut '" + adviceClass.type().getClassName()
                + "' has more than one @Advice.OnMethodThrow method");
        Method asmMethod = adviceMethod.toAsmMethod();
        List<AdviceParameter> parameters = getAdviceParameters(adviceMethod.bindAnnotations(),
                asmMethod.getArgumentTypes(), onThrowBindAnnotationTypes, OnThrowType);
        for (int i = 1; i < parameters.size(); i++) {
            checkState(parameters.get(i).kind() != ParameterKind.THROWABLE,
                    "@Advice.Thrown must be the first argument to @Advice.OnMethodThrow");
        }
        boolean isThrownArg =
                !parameters.isEmpty() && parameters.get(0).kind() == ParameterKind.THROWABLE;
        int startIndex = isThrownArg ? 2 : 1;
        for (int i = startIndex; i < parameters.size(); i++) {
            checkState(parameters.get(i).kind() != ParameterKind.OPTIONAL_THREAD_CONTEXT,
                    "OptionalThreadContext must be the first argument to @Advice.OnMethodThrow"
                            + " (or second argument if @Advice.Thrown is present)");
        }
        checkState(asmMethod.getReturnType().getSort() == Type.VOID,
                "@Advice.OnMethodThrow method must return void (for now)");
        builder.onThrowAdvice(asmMethod);
        builder.addAllOnThrowParameters(parameters);
        checkForBindThreadContext(parameters);
        checkForBindOptionalThreadContext(parameters);
        hasOnThrowAdvice = true;
    }

    private void initOnAfterAdvice(PointcutClass adviceClass, PointcutMethod adviceMethod)
            throws AdviceConstructionException {
        checkState(!hasOnAfterAdvice, "@Advice.Pointcut '" + adviceClass.type().getClassName()
                + "' has more than one @Advice.OnMethodAfter method");
        Method asmMethod = adviceMethod.toAsmMethod();
        checkState(asmMethod.getReturnType().getSort() == Type.VOID,
                "@Advice.OnMethodAfter method must return void");
        builder.onAfterAdvice(asmMethod);
        List<AdviceParameter> parameters = getAdviceParameters(adviceMethod.bindAnnotations(),
                asmMethod.getArgumentTypes(), onAfterBindAnnotationTypes, OnAfterType);
        builder.addAllOnAfterParameters(parameters);
        checkForBindThreadContext(parameters);
        checkForBindOptionalThreadContext(parameters);
        hasOnAfterAdvice = true;
    }

    private void checkForBindThreadContext(List<AdviceParameter> parameters) {
        for (AdviceParameter parameter : parameters) {
            if (parameter.kind() == ParameterKind.THREAD_CONTEXT) {
                builder.hasBindThreadContext(true);
                return;
            }
        }
    }

    private void checkForBindOptionalThreadContext(List<AdviceParameter> parameters) {
        for (AdviceParameter parameter : parameters) {
            if (parameter.kind() == ParameterKind.OPTIONAL_THREAD_CONTEXT) {
                builder.hasBindOptionalThreadContext(true);
                break;
            }
        }
    }

    private static void checkState(boolean condition, String message)
            throws AdviceConstructionException {
        if (!condition) {
            throw new AdviceConstructionException(message);
        }
    }

    private static List<Object> buildPatterns(String[] maybePatterns) {
        List<Object> patterns = Lists.newArrayList();
        for (String maybePattern : maybePatterns) {
            Pattern pattern = MaybePatterns.buildPattern(maybePattern);
            if (pattern == null) {
                patterns.add(maybePattern);
            } else {
                patterns.add(pattern);
            }
        }
        return patterns;
    }

    private static List<AdviceParameter> getAdviceParameters(
            Map<Integer, BindAnnotation> parameterAnnotations, Type[] parameterTypes,
            List<Type> validBindAnnotationTypes, Type adviceAnnotationType)
            throws AdviceConstructionException {

        List<AdviceParameter> parameters = Lists.newArrayList();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i].equals(ThreadContextType)) {
                parameters.add(ImmutableAdviceParameter.builder()
                        .kind(ParameterKind.THREAD_CONTEXT)
                        .type(ThreadContextType)
                        .argIndex(-1)
                        .build());
                continue;
            }
            if (parameterTypes[i].equals(OptionalThreadContextType)) {
                parameters.add(ImmutableAdviceParameter.builder()
                        .kind(ParameterKind.OPTIONAL_THREAD_CONTEXT)
                        .type(OptionalThreadContextType)
                        .argIndex(-1)
                        .build());
                continue;
            }
            BindAnnotation bindAnnotation = parameterAnnotations.get(i);
            if (bindAnnotation == null) {
                // no bind annotations found, provide a good error message
                List<String> validBindAnnotationNames = Lists.newArrayList();
                for (Type annotationType : validBindAnnotationTypes) {
                    validBindAnnotationNames.add("@" + annotationType.getClassName());
                }
                throw new AdviceConstructionException("All parameters to @"
                        + adviceAnnotationType.getClassName() + " must be annotated with one"
                        + " of " + Joiner.on(", ").join(validBindAnnotationNames));
            }
            Type bindAnnotationType = bindAnnotation.type();
            checkState(validBindAnnotationTypes.contains(bindAnnotationType), "Annotation '"
                    + bindAnnotationType.getClassName() + "' found in an invalid location");
            parameters.add(getAdviceParameter(bindAnnotationType, parameterTypes[i],
                    bindAnnotation.argIndex()));
        }
        return parameters;
    }

    private static AdviceParameter getAdviceParameter(Type validBindAnnotationType,
            Type parameterType, int argIndex) throws AdviceConstructionException {
        checkState(
                !validBindAnnotationType.equals(BindMethodNameType)
                        || parameterType.equals(StringType),
                "@Advice.MethodName parameter type must be java.lang.String");
        checkState(
                !validBindAnnotationType.equals(BindThrowableType)
                        || parameterType.equals(ThrowableType),
                "@Advice.MethodName parameter type must be java.lang.Throwable");
        ParameterKind parameterKind = parameterKindMap.get(validBindAnnotationType);
        // parameterKind should never be null since all bind annotations have a mapping in
        // parameterKindMap
        checkNotNull(parameterKind, "Annotation not found in parameterKindMap: "
                + validBindAnnotationType.getClassName());
        return ImmutableAdviceParameter.builder()
                .kind(parameterKind)
                .type(parameterType)
                .argIndex(argIndex)
                .build();
    }

    @SuppressWarnings("serial")
    private static class AdviceConstructionException extends Exception {

        private AdviceConstructionException(@Nullable String message) {
            super(message);
        }
    }
}
