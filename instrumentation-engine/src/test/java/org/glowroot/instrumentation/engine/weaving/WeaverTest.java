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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.glowroot.instrumentation.api.weaving.Advice.Pointcut;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.api.weaving.OptionalReturn;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.engine.weaving.ClassLoaders.LazyDefinedClass;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.AnotherAnnotationBasedAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.AnotherAnnotationBasedAdviceButWrong;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BasicAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BasicAnnotationBasedAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BasicHighOrderAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BasicMiscAllConstructorAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BasicMiscConstructorAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BasicWithInnerClassAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BasicWithInnerClassArgAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindAutoboxedReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindClassMetaAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindMethodMetaAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindMethodMetaArrayAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindMethodMetaReturnArrayAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindMethodNameAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindMutableParameterAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindMutableParameterWithMoreFramesAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindOptionalPrimitiveReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindOptionalReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindOptionalVoidReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindParameterAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindParameterArrayAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindPrimitiveBooleanTravelerAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindPrimitiveReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindPrimitiveTravelerAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindReceiverAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindThrowableAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindTravelerAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BrokenAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.ChangeReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.CircularClassDependencyAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.ClassNamePatternAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.ComplexSuperTypeRestrictionAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.FinalMethodAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.GenericMiscAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.HackedConstructorBytecodeAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.HackedConstructorBytecodeJumpingAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.HasString;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.HasStringClassMixin;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.HasStringInterfaceMixin;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.HasStringMultipleMixin;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.InterfaceAppearsTwiceInHierarchyAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MatchingPublicNonStaticAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MethodParametersBadDotDotAdvice1;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MethodParametersDotDotAdvice1;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MethodParametersDotDotAdvice2;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MethodParametersDotDotAdvice3;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MethodReturnCharSequenceAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MethodReturnStringAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MethodReturnVoidAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MoreNotPerfectBytecodeAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MultipleMethodsAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.NonMatchingMethodReturnAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.NonMatchingMethodReturnAdvice2;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.NonMatchingStaticAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.NotPerfectBytecodeAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.PrimitiveAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.PrimitiveWithAutoboxAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.PrimitiveWithWildcardAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.Shimmy;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.StaticAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.SubTypeRestrictionAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.SubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.SubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.SuperBasicAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.SuperTypeRestrictionAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.SuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestBytecodeWithStackFramesAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestBytecodeWithStackFramesAdvice2;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestBytecodeWithStackFramesAdvice3;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestBytecodeWithStackFramesAdvice4;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestBytecodeWithStackFramesAdvice5;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestBytecodeWithStackFramesAdvice6;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestClassMeta;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestMethodMeta;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.TestTroublesomeBytecodeAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.ThrowInOnBeforeAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.ThrowableToStringAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.WildMethodAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentationThreadLocals.IntegerThreadLocal;
import org.glowroot.instrumentation.engine.weaving.other.ArrayMisc;
import org.glowroot.instrumentation.engine.weaving.targets.AbstractMisc.ExtendsAbstractMisc;
import org.glowroot.instrumentation.engine.weaving.targets.AbstractNotMisc.ExtendsAbstractNotMisc;
import org.glowroot.instrumentation.engine.weaving.targets.AbstractNotMiscWithFinal.ExtendsAbstractNotMiscWithFinal;
import org.glowroot.instrumentation.engine.weaving.targets.AccessibilityMisc;
import org.glowroot.instrumentation.engine.weaving.targets.BasicMisc;
import org.glowroot.instrumentation.engine.weaving.targets.BytecodeWithStackFramesMisc;
import org.glowroot.instrumentation.engine.weaving.targets.DuplicateStackFramesMisc;
import org.glowroot.instrumentation.engine.weaving.targets.ExtendsPackagePrivateMisc;
import org.glowroot.instrumentation.engine.weaving.targets.GenericAbstractMiscImpl;
import org.glowroot.instrumentation.engine.weaving.targets.GenericMisc;
import org.glowroot.instrumentation.engine.weaving.targets.GenericMiscImpl;
import org.glowroot.instrumentation.engine.weaving.targets.InnerTryCatchMisc;
import org.glowroot.instrumentation.engine.weaving.targets.Misc;
import org.glowroot.instrumentation.engine.weaving.targets.Misc2;
import org.glowroot.instrumentation.engine.weaving.targets.NativeMisc;
import org.glowroot.instrumentation.engine.weaving.targets.NestingMisc;
import org.glowroot.instrumentation.engine.weaving.targets.OnlyThrowingMisc;
import org.glowroot.instrumentation.engine.weaving.targets.PrimitiveMisc;
import org.glowroot.instrumentation.engine.weaving.targets.ShimmedMisc;
import org.glowroot.instrumentation.engine.weaving.targets.StaticMisc;
import org.glowroot.instrumentation.engine.weaving.targets.StaticSubbedMisc;
import org.glowroot.instrumentation.engine.weaving.targets.SubBasicMisc;
import org.glowroot.instrumentation.engine.weaving.targets.SubException;
import org.glowroot.instrumentation.engine.weaving.targets.SubMisc;
import org.glowroot.instrumentation.engine.weaving.targets.SuperBasic;
import org.glowroot.instrumentation.engine.weaving.targets.SuperBasicMisc;
import org.glowroot.instrumentation.engine.weaving.targets.ThrowMutatedParamMisc;
import org.glowroot.instrumentation.engine.weaving.targets.ThrowingMisc;

import static org.assertj.core.api.Assertions.assertThat;

public class WeaverTest {

    @Before
    public void before() {
        SomeInstrumentationThreadLocals.resetThreadLocals();
    }

    // ===================== @Advice.IsEnabled =====================

    @Test
    public void shouldExecuteEnabledAdvice() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteEnabledAdviceOnThrow() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BasicAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteEnabledAdviceOnOnlyThrow() throws Exception {
        // given
        Misc test = newWovenObject(OnlyThrowingMisc.class, Misc.class, BasicAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotExecuteDisabledAdvice() throws Exception {
        // given
        BasicAdvice.disable();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotExecuteDisabledAdviceOnThrow() throws Exception {
        // given
        BasicAdvice.disable();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BasicAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    // ===================== @Advice.This =====================

    @Test
    public void shouldBindReceiver() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindReceiverAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.isEnabledReceiver.get()).isEqualTo(test);
        assertThat(SomeInstrumentationThreadLocals.onBeforeReceiver.get()).isEqualTo(test);
        assertThat(SomeInstrumentationThreadLocals.onReturnReceiver.get()).isEqualTo(test);
        assertThat(SomeInstrumentationThreadLocals.onThrowReceiver.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterReceiver.get()).isEqualTo(test);
    }

    @Test
    public void shouldBindReceiverOnThrow() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindReceiverAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeInstrumentationThreadLocals.isEnabledReceiver.get()).isEqualTo(test);
        assertThat(SomeInstrumentationThreadLocals.onBeforeReceiver.get()).isEqualTo(test);
        assertThat(SomeInstrumentationThreadLocals.onReturnReceiver.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onThrowReceiver.get()).isEqualTo(test);
        assertThat(SomeInstrumentationThreadLocals.onAfterReceiver.get()).isEqualTo(test);
    }

    // ===================== @Advice.Argument =====================

    @Test
    public void shouldBindParameters() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindParameterAdvice.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(SomeInstrumentationThreadLocals.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onReturnParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onThrowParams.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterParams.get()).isEqualTo(parameters);
    }

    @Test
    public void shouldBindParameterOnThrow() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindParameterAdvice.class);
        // when
        try {
            test.executeWithArgs("one", 2);
        } catch (Throwable t) {
        }
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(SomeInstrumentationThreadLocals.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onReturnParams.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onThrowParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onAfterParams.get()).isEqualTo(parameters);
    }

    // ===================== @Advice.AllArguments =====================

    @Test
    public void shouldBindParameterArray() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindParameterArrayAdvice.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(SomeInstrumentationThreadLocals.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onReturnParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onThrowParams.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterParams.get()).isEqualTo(parameters);
    }

    @Test
    public void shouldBindParameterArrayOnThrow() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindParameterArrayAdvice.class);
        // when
        try {
            test.executeWithArgs("one", 2);
        } catch (Throwable t) {
        }
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(SomeInstrumentationThreadLocals.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onReturnParams.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onThrowParams.get()).isEqualTo(parameters);
        assertThat(SomeInstrumentationThreadLocals.onAfterParams.get()).isEqualTo(parameters);
    }

    // ===================== @Advice.Enter =====================

    @Test
    public void shouldBindTraveler() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindTravelerAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onReturnTraveler.get()).isEqualTo("a traveler");
        assertThat(SomeInstrumentationThreadLocals.onThrowTraveler.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterTraveler.get()).isEqualTo("a traveler");
    }

    @Test
    public void shouldBindPrimitiveTraveler() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindPrimitiveTravelerAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onReturnTraveler.get()).isEqualTo(3);
        assertThat(SomeInstrumentationThreadLocals.onThrowTraveler.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterTraveler.get()).isEqualTo(3);
    }

    @Test
    public void shouldBindPrimitiveBooleanTraveler() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                BindPrimitiveBooleanTravelerAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onReturnTraveler.get()).isEqualTo(true);
        assertThat(SomeInstrumentationThreadLocals.onThrowTraveler.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterTraveler.get()).isEqualTo(true);
    }

    @Test
    public void shouldBindTravelerOnThrow() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindTravelerAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeInstrumentationThreadLocals.onReturnTraveler.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onThrowTraveler.get()).isEqualTo("a traveler");
        assertThat(SomeInstrumentationThreadLocals.onAfterTraveler.get()).isEqualTo("a traveler");
    }

    // ===================== @Advice.ClassMeta =====================

    @Test
    public void shouldBindClassMeta() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindClassMetaAdvice.class,
                TestClassMeta.class);
        // when
        test.execute1();
        // then
        // can't compare Class objects directly since they are in different class loaders due to
        // IsolatedWeavingClassLoader
        assertThat(SomeInstrumentationThreadLocals.isEnabledClassMeta.get().getClazzName())
                .isEqualTo(BasicMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onBeforeClassMeta.get().getClazzName())
                .isEqualTo(BasicMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onReturnClassMeta.get().getClazzName())
                .isEqualTo(BasicMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onThrowClassMeta.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterClassMeta.get().getClazzName())
                .isEqualTo(BasicMisc.class.getName());
    }

    @Test
    public void shouldBindClassMetaOnThrow() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindClassMetaAdvice.class,
                TestClassMeta.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        // can't compare Class objects directly since they are in different class loaders due to
        // IsolatedWeavingClassLoader
        assertThat(SomeInstrumentationThreadLocals.isEnabledClassMeta.get().getClazzName())
                .isEqualTo(ThrowingMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onBeforeClassMeta.get().getClazzName())
                .isEqualTo(ThrowingMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onReturnClassMeta.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onThrowClassMeta.get().getClazzName())
                .isEqualTo(ThrowingMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onAfterClassMeta.get().getClazzName())
                .isEqualTo(ThrowingMisc.class.getName());
    }

    // ===================== @Advice.MethodMeta =====================

    @Test
    public void shouldBindMethodMeta() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindMethodMetaAdvice.class,
                TestMethodMeta.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        // can't compare Class objects directly since they are in different class loaders due to
        // IsolatedWeavingClassLoader
        assertThat(
                SomeInstrumentationThreadLocals.isEnabledMethodMeta.get().getDeclaringClassName())
                        .isEqualTo(BasicMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get().getReturnTypeName())
                .isEqualTo(void.class.getName());
        assertThat(
                SomeInstrumentationThreadLocals.isEnabledMethodMeta.get().getParameterTypeNames())
                        .containsExactly(String.class.getName(), int.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onBeforeMethodMeta.get())
                .isEqualTo(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get());
        assertThat(SomeInstrumentationThreadLocals.onReturnMethodMeta.get())
                .isEqualTo(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get());
        assertThat(SomeInstrumentationThreadLocals.onThrowMethodMeta.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterMethodMeta.get())
                .isEqualTo(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get());
    }

    @Test
    public void shouldBindMethodMetaOnThrow() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindMethodMetaAdvice.class,
                TestMethodMeta.class);
        // when
        try {
            test.executeWithArgs("one", 2);
        } catch (Throwable t) {
        }
        // then
        // can't compare Class objects directly since they are in different class loaders due to
        // IsolatedWeavingClassLoader
        assertThat(
                SomeInstrumentationThreadLocals.isEnabledMethodMeta.get().getDeclaringClassName())
                        .isEqualTo(ThrowingMisc.class.getName());
        assertThat(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get().getReturnTypeName())
                .isEqualTo(void.class.getName());
        assertThat(
                SomeInstrumentationThreadLocals.isEnabledMethodMeta.get().getParameterTypeNames())
                        .containsExactly(String.class.getName(), int.class.getName());
        assertThat(SomeInstrumentationThreadLocals.onBeforeMethodMeta.get())
                .isEqualTo(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get());
        assertThat(SomeInstrumentationThreadLocals.onReturnMethodMeta.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onThrowMethodMeta.get())
                .isEqualTo(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get());
        assertThat(SomeInstrumentationThreadLocals.onAfterMethodMeta.get())
                .isEqualTo(SomeInstrumentationThreadLocals.isEnabledMethodMeta.get());
    }

    @Test
    public void shouldBindMethodMetaArrays() throws Exception {
        // given
        Misc test = newWovenObject(ArrayMisc.class, Misc.class, BindMethodMetaArrayAdvice.class,
                TestMethodMeta.class);
        // when
        test.execute1();
        // then
        // can't compare Class objects directly since they are in different class loaders due to
        // IsolatedWeavingClassLoader
        TestMethodMeta testMethodMeta = SomeInstrumentationThreadLocals.isEnabledMethodMeta.get();
        assertThat(testMethodMeta.getDeclaringClassName()).isEqualTo(ArrayMisc.class.getName());
        assertThat(testMethodMeta.getReturnTypeName()).isEqualTo(void.class.getName());
        Class<?> somethingPrivateClass =
                Class.forName(
                        "org.glowroot.instrumentation.engine.weaving.other.ArrayMisc$SomethingPrivate");
        Class<?> somethingPrivateArrayClass =
                Array.newInstance(somethingPrivateClass, 0).getClass();
        assertThat(testMethodMeta.getParameterTypeNames()).containsExactly(byte[].class.getName(),
                Object[][][].class.getName(), somethingPrivateArrayClass.getName());
        assertThat(SomeInstrumentationThreadLocals.onBeforeMethodMeta.get())
                .isEqualTo(testMethodMeta);
        assertThat(SomeInstrumentationThreadLocals.onReturnMethodMeta.get())
                .isEqualTo(testMethodMeta);
        assertThat(SomeInstrumentationThreadLocals.onThrowMethodMeta.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterMethodMeta.get())
                .isEqualTo(testMethodMeta);
    }

    @Test
    public void shouldBindMethodMetaReturnArray() throws Exception {
        // given
        Misc test = newWovenObject(ArrayMisc.class, Misc.class,
                BindMethodMetaReturnArrayAdvice.class, TestMethodMeta.class);
        // when
        test.execute1();
        // then
        // can't compare Class objects directly since they are in different class loaders due to
        // IsolatedWeavingClassLoader
        TestMethodMeta testMethodMeta = SomeInstrumentationThreadLocals.isEnabledMethodMeta.get();
        assertThat(testMethodMeta.getDeclaringClassName()).isEqualTo(ArrayMisc.class.getName());
        assertThat(testMethodMeta.getReturnTypeName()).isEqualTo(int[].class.getName());
        assertThat(testMethodMeta.getParameterTypeNames()).isEmpty();
        assertThat(SomeInstrumentationThreadLocals.onBeforeMethodMeta.get())
                .isEqualTo(testMethodMeta);
        assertThat(SomeInstrumentationThreadLocals.onReturnMethodMeta.get())
                .isEqualTo(testMethodMeta);
        assertThat(SomeInstrumentationThreadLocals.onThrowMethodMeta.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterMethodMeta.get())
                .isEqualTo(testMethodMeta);
    }

    // ===================== @Advice.Return =====================

    @Test
    public void shouldBindReturn() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindReturnAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.returnValue.get()).isEqualTo("xyz");
    }

    @Test
    public void shouldBindPrimitiveReturn() throws Exception {
        // given
        Misc test =
                newWovenObject(PrimitiveMisc.class, Misc.class, BindPrimitiveReturnAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.returnValue.get()).isEqualTo(4);
    }

    @Test
    public void shouldBindAutoboxedReturn() throws Exception {
        // given
        Misc test =
                newWovenObject(PrimitiveMisc.class, Misc.class, BindAutoboxedReturnAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.returnValue.get()).isEqualTo(4);
    }

    // ===================== @Advice.OptionalReturn =====================

    @Test
    public void shouldBindOptionalReturn() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindOptionalReturnAdvice.class,
                OptionalReturn.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.optionalReturnValue.get().isVoid()).isFalse();
        assertThat(SomeInstrumentationThreadLocals.optionalReturnValue.get().getValue())
                .isEqualTo("xyz");
    }

    @Test
    public void shouldBindOptionalVoidReturn() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindOptionalVoidReturnAdvice.class,
                OptionalReturn.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.optionalReturnValue.get().isVoid()).isTrue();
    }

    @Test
    public void shouldBindOptionalPrimitiveReturn() throws Exception {
        // given
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class,
                BindOptionalPrimitiveReturnAdvice.class, OptionalReturn.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.optionalReturnValue.get().isVoid()).isFalse();
        assertThat(SomeInstrumentationThreadLocals.optionalReturnValue.get().getValue())
                .isEqualTo(Integer.valueOf(4));
    }

    // ===================== @Advice.Thrown =====================

    @Test
    public void shouldBindThrowable() throws Exception {
        // given
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindThrowableAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeInstrumentationThreadLocals.throwable.get()).isNotNull();
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(1);
    }

    // ===================== @Advice.MethodName =====================

    @Test
    public void shouldBindMethodName() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindMethodNameAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.isEnabledMethodName.get()).isEqualTo("execute1");
        assertThat(SomeInstrumentationThreadLocals.onBeforeMethodName.get()).isEqualTo("execute1");
        assertThat(SomeInstrumentationThreadLocals.onReturnMethodName.get()).isEqualTo("execute1");
        assertThat(SomeInstrumentationThreadLocals.onThrowMethodName.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterMethodName.get()).isEqualTo("execute1");
    }

    // ===================== change return value =====================

    @Test
    public void shouldChangeReturnValue() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, ChangeReturnAdvice.class);
        // when
        CharSequence returnValue = test.executeWithReturn();
        // then
        assertThat(returnValue).isEqualTo("modified xyz:executeWithReturn");
    }

    // ===================== inheritance =====================

    @Test
    public void shouldNotWeaveIfDoesNotOverrideMatch() throws Exception {
        // given
        Misc2 test = newWovenObject(BasicMisc.class, Misc2.class, BasicAdvice.class);
        // when
        test.execute2();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    // ===================== methodParameters '..' =====================

    @Test
    public void shouldMatchMethodParametersDotDot1() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, MethodParametersDotDotAdvice1.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotMatchMethodParametersBadDotDot1() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, MethodParametersBadDotDotAdvice1.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldMatchMethodParametersDotDot2() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, MethodParametersDotDotAdvice2.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldMatchMethodParametersDotDot3() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, MethodParametersDotDotAdvice3.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    // ===================== @Advice.Pointcut.subTypeRestriction =====================

    @Test
    public void shouldExecuteSubTypeRestrictionAdvice() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, SubTypeRestrictionAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteSubTypeRestrictionAdvice2() throws Exception {
        // given
        Misc test = newWovenObject(SubBasicMisc.class, Misc.class, SubTypeRestrictionAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotExecuteSubTypeRestrictionAdvice() throws Exception {
        // given
        Misc test = newWovenObject(NestingMisc.class, Misc.class, SubTypeRestrictionAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldExecuteSubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice()
            throws Exception {
        // given
        SuperBasic test = newWovenObject(BasicMisc.class, SuperBasic.class,
                SubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteSubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice2()
            throws Exception {
        // given
        SuperBasic test = newWovenObject(SubBasicMisc.class, SuperBasic.class,
                SubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotExecuteSubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice()
            throws Exception {
        // given
        SuperBasic test = newWovenObject(SuperBasicMisc.class, SuperBasic.class,
                SubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldExecuteSubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice()
            throws Exception {
        // given
        Misc test = newWovenObject(SubBasicMisc.class, Misc.class,
                SubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteSubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice2()
            throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                SubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotExecuteSubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice()
            throws Exception {
        // given
        Misc test = newWovenObject(NestingMisc.class, Misc.class,
                SubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    // ===================== @Advice.Pointcut.superTypeRestriction =====================

    @Test
    public void shouldExecuteSuperTypeRestrictionAdvice() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, SuperTypeRestrictionAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteSuperTypeRestrictionAdvice2() throws Exception {
        // given
        Misc test =
                newWovenObject(SubBasicMisc.class, Misc.class, SuperTypeRestrictionAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotExecuteSuperTypeRestrictionAdvice() throws Exception {
        // given
        Misc test = newWovenObject(NestingMisc.class, Misc.class, SuperTypeRestrictionAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotExecuteSuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice()
            throws Exception {
        // given
        SuperBasic test = newWovenObject(BasicMisc.class, SuperBasic.class,
                SuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotExecuteSuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice2()
            throws Exception {
        // given
        SuperBasic test = newWovenObject(SubBasicMisc.class, SuperBasic.class,
                SuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldStillNotExecuteSuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice()
            throws Exception {
        // given
        SuperBasic test = newWovenObject(SuperBasicMisc.class, SuperBasic.class,
                SuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldExecuteComplexSuperTypeRestrictedPointcut() throws Exception {
        // given
        SubMisc test = newWovenObject(BasicMisc.class, SubMisc.class,
                ComplexSuperTypeRestrictionAdvice.class);
        // when
        test.sub();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    // ===================== annotation-based pointcuts =====================

    @Test
    public void shouldExecuteAnnotationBasedPointcut() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicAnnotationBasedAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteAnotherAnnotationBasedPointcut() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, AnotherAnnotationBasedAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteAnotherAnnotationButWrongBasedPointcut() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                AnotherAnnotationBasedAdviceButWrong.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    // ===================== throw in lower priority @Advice.OnMethodBefore =====================

    // motivation for this test: any dangerous code in @Advice.OnMethodBefore should occur before
    // calling transactionService.start..., since @Advice.OnMethodAfter will not be called if an
    // exception occurs however, if there are multiple pointcuts for one method, the dangerous code
    // in @Advice.OnMethodBefore of the lower priority pointcut occurs after the
    // @Advice.OnMethodBefore of the higher priority pointcut and so if the dangerous code in the
    // lower priority pointcut throws an exception, need to make sure the @Advice.OnMethodAfter of
    // the higher priority pointcut is still called

    @Test
    public void shouldStillCallOnAfterOfHigherPriorityPointcut() throws Exception {
        // given

        // SomeInstrumentationThreadLocals is passed as bridgeable so that the static thread locals
        // will be accessible for test verification
        IsolatedWeavingClassLoader isolatedWeavingClassLoader = new IsolatedWeavingClassLoader(
                Misc.class, SomeInstrumentationThreadLocals.class, IntegerThreadLocal.class);
        List<Advice> advisors = Lists.newArrayList();
        advisors.add(newAdvice(BasicAdvice.class));
        advisors.add(newAdvice(BindThrowableAdvice.class));
        advisors.add(newAdvice(ThrowInOnBeforeAdvice.class));
        advisors.add(newAdvice(BasicHighOrderAdvice.class));
        Supplier<List<Advice>> advisorsSupplier =
                Suppliers.<List<Advice>>ofInstance(ImmutableList.copyOf(advisors));
        AnalyzedWorld analyzedWorld = new AnalyzedWorld(advisorsSupplier,
                ImmutableList.<ShimType>of(), ImmutableList.<MixinType>of(), null);
        Weaver weaver = new Weaver(advisorsSupplier, ImmutableList.<ShimType>of(),
                ImmutableList.<MixinType>of(), analyzedWorld, Ticker.systemTicker());
        isolatedWeavingClassLoader.setWeaver(weaver);
        Misc test = isolatedWeavingClassLoader.newInstance(BasicMisc.class, Misc.class);
        // when
        RuntimeException exception = null;
        try {
            test.execute1();
        } catch (RuntimeException e) {
            exception = e;
        }
        // then
        assertThat(exception.getMessage()).isEqualTo("Abxy");
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(2);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.throwable.get().getMessage()).isEqualTo("Abxy");
    }

    // ===================== @Shim =====================

    @Test
    public void shouldShim() throws Exception {
        // given
        Misc test = newWovenObject(ShimmedMisc.class, Misc.class, Shimmy.class, Shimmy.class);
        // when
        ((Shimmy) test).shimmySetString("another value");
        // then
        assertThat(((Shimmy) test).shimmyGetString()).isEqualTo("another value");
    }

    // ===================== @Mixin =====================

    @Test
    public void shouldMixinToClass() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringClassMixin.class,
                HasString.class);
        // when
        ((HasString) test).setString("another value");
        // then
        assertThat(((HasString) test).getString()).isEqualTo("another value");
    }

    @Test
    public void shouldMixinToInterface() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringInterfaceMixin.class,
                HasString.class);
        // when
        ((HasString) test).setString("another value");
        // then
        assertThat(((HasString) test).getString()).isEqualTo("another value");
    }

    @Test
    public void shouldMixinOnlyOnce() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringMultipleMixin.class,
                HasString.class);
        // when
        ((HasString) test).setString("another value");
        // then
        assertThat(((HasString) test).getString()).isEqualTo("another value");
    }

    @Test
    public void shouldMixinAndCallInitExactlyOnce() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringClassMixin.class,
                HasString.class);
        // when
        // then
        assertThat(((HasString) test).getString()).isEqualTo("a string");
    }

    // ===================== static pointcuts =====================

    @Test
    public void shouldWeaveStaticMethod() throws Exception {
        // given
        Misc test = newWovenObject(StaticMisc.class, Misc.class, StaticAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotWeaveStaticSubbedMethod() throws Exception {
        // given
        Misc test = newWovenObject(StaticSubbedMisc.class, Misc.class, StaticAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    // ===================== primitive args =====================

    @Test
    public void shouldWeaveMethodWithPrimitiveArgs() throws Exception {
        // given
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class, PrimitiveAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    // ===================== wildcard args =====================

    @Test
    public void shouldWeaveMethodWithWildcardArgs() throws Exception {
        // given
        Misc test =
                newWovenObject(PrimitiveMisc.class, Misc.class, PrimitiveWithWildcardAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotBombWithWithWildcardArg() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, WildMethodAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    // ===================== type name pattern =====================

    @Test
    public void shouldWeaveTypeWithNamePattern() throws Exception {
        // given
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class, ClassNamePatternAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    // ===================== autobox args =====================

    @Test
    public void shouldWeaveMethodWithAutoboxArgs() throws Exception {
        // given
        Misc test =
                newWovenObject(PrimitiveMisc.class, Misc.class, PrimitiveWithAutoboxAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
    }

    // ===================== return type matching =====================

    @Test
    public void shouldMatchMethodReturningVoid() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MethodReturnVoidAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldMatchMethodReturningCharSequence() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, MethodReturnCharSequenceAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotMatchMethodReturningString() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MethodReturnStringAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotMatchMethodBasedOnReturnType() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, NonMatchingMethodReturnAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotMatchMethodBasedOnReturnType2() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, NonMatchingMethodReturnAdvice2.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
    }

    // ===================== bridge methods =====================

    @Test
    public void shouldHandleGenericOverride1() throws Exception {
        // given
        @SuppressWarnings("unchecked")
        GenericMisc<String> test =
                newWovenObject(GenericMiscImpl.class, GenericMisc.class, GenericMiscAdvice.class);
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        SomeInstrumentationThreadLocals.resetThreadLocals();
        // when
        test.execute1("");
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleGenericOverride2() throws Exception {
        // given
        @SuppressWarnings("unchecked")
        GenericMisc<String> test =
                newWovenObject(GenericMiscImpl.class, GenericMisc.class, GenericMiscAdvice.class);
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        SomeInstrumentationThreadLocals.resetThreadLocals();
        // when
        test.execute2("");
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleConfusingVisibilityBridge() throws Exception {
        // given
        @SuppressWarnings("unchecked")
        GenericMisc<String> test = newWovenObject(GenericAbstractMiscImpl.class, GenericMisc.class,
                GenericMiscAdvice.class);
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        SomeInstrumentationThreadLocals.resetThreadLocals();
        // when
        test.execute2(5);
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldHandleBridgeCallingSuper() throws Exception {
        // given
        @SuppressWarnings("unchecked")
        GenericMisc<String> test = newWovenObject(GenericAbstractMiscImpl.class, GenericMisc.class,
                GenericMiscAdvice.class);
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        SomeInstrumentationThreadLocals.resetThreadLocals();
        // when
        test.getObject(Long.class);
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    // ===================== constructor =====================

    @Test
    public void shouldHandleConstructorPointcut() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                enhanceConstructorAdviceClass(BasicMiscConstructorAdvice.class));
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        SomeInstrumentationThreadLocals.resetThreadLocals();
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldVerifyConstructorPointcutsAreNested() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                enhanceConstructorAdviceClass(BasicMiscAllConstructorAdvice.class));
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        SomeInstrumentationThreadLocals.resetThreadLocals();
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(2);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(2);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(2);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(2);
        assertThat(SomeInstrumentationThreadLocals.orderedEvents.get()).containsExactly("isEnabled",
                "onBefore", "isEnabled", "onBefore", "onReturn", "onAfter", "onReturn", "onAfter");
    }

    @Test
    public void shouldHandleInheritedMethodFulfillingAnInterface() throws Exception {
        // given
        Misc test = newWovenObject(ExtendsAbstractNotMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotCrashOnInheritedFinalMethodFulfillingAnInterface() throws Exception {
        // given
        Misc test = newWovenObject(ExtendsAbstractNotMiscWithFinal.class, Misc.class,
                BasicAdvice.class);
        // when
        test.execute1();
        // then
        // do not crash with java.lang.VerifyError
    }

    @Test
    public void shouldHandleInheritedMethod() throws Exception {
        // given
        SuperBasic test = newWovenObject(BasicMisc.class, SuperBasic.class, SuperBasicAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleInheritedPublicMethodFromPackagePrivateClass() throws Exception {
        // given
        Misc test = newWovenObject(ExtendsPackagePrivateMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleSubInheritedMethod() throws Exception {
        // given
        SuperBasic test = newWovenObject(BasicMisc.class, SuperBasic.class, SuperBasicAdvice.class);
        // when
        test.callSuperBasic();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleSubInheritedFromClassInBootstrapClassLoader() throws Exception {
        // given
        Exception test =
                newWovenObject(SubException.class, Exception.class, ThrowableToStringAdvice.class);
        // when
        test.toString();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleInnerClassArg() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicWithInnerClassArgAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleInnerClass() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.InnerMisc.class, Misc.class,
                BasicWithInnerClassAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandlePointcutWithMultipleMethods() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MultipleMethodsAdvice.class);
        // when
        test.execute1();
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(2);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(2);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(2);
    }

    @Test
    public void shouldNotTryToWeaveNativeMethods() throws Exception {
        // given
        // when
        newWovenObject(NativeMisc.class, Misc.class, BasicAdvice.class);
        // then should not bomb
    }

    @Test
    public void shouldNotTryToWeaveAbstractMethods() throws Exception {
        // given
        Misc test = newWovenObject(ExtendsAbstractMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then should not bomb
    }

    @Test
    public void shouldNotDisruptInnerTryCatch() throws Exception {
        // given
        Misc test = newWovenObject(InnerTryCatchMisc.class, Misc.class, BasicAdvice.class,
                HasString.class);
        // when
        test.execute1();
        // then
        assertThat(test.executeWithReturn()).isEqualTo("caught");
    }

    @Test
    public void shouldPayAttentionToStaticModifierMatching() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, NonMatchingStaticAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldPayAttentionToPublicAndNonStaticModifierMatching() throws Exception {
        // given
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, MatchingPublicNonStaticAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotBomb() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BrokenAdvice.class);
        // when
        test.executeWithArgs("one", 2);
        // then should not bomb
    }

    @Test
    public void shouldNotBomb2() throws Exception {
        // given
        Misc test = newWovenObject(AccessibilityMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then should not bomb
    }

    @Test
    // weaving an interface method that references a concrete class that implements that interface
    // is supported
    public void shouldHandleCircularDependency() throws Exception {
        // given
        // when
        newWovenObject(BasicMisc.class, Misc.class, CircularClassDependencyAdvice.class);
        // then should not bomb
    }

    @Test
    // weaving an interface method that appears twice in a given class hierarchy should only weave
    // the method once
    public void shouldHandleInterfaceThatAppearsTwiceInHierarchy() throws Exception {
        // given
        // when
        Misc test = newWovenObject(SubBasicMisc.class, Misc.class,
                InterfaceAppearsTwiceInHierarchyAdvice.class);
        test.execute1();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleFinalMethodAdvice() throws Exception {
        // given
        // when
        Misc test = newWovenObject(SubBasicMisc.class, Misc.class, FinalMethodAdvice.class);
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    // test weaving against jdk 1.7 bytecode with stack frames
    public void shouldWeaveBytecodeWithStackFrames() throws Exception {
        Assume.assumeFalse(StandardSystemProperty.JAVA_VERSION.value().startsWith("1.6"));
        Misc test = newWovenObject(BytecodeWithStackFramesMisc.class, Misc.class,
                TestBytecodeWithStackFramesAdvice.class);
        test.executeWithReturn();
    }

    @Test
    // test weaving against jdk 1.7 bytecode with stack frames
    public void shouldWeaveBytecodeWithStackFrames2() throws Exception {
        Assume.assumeFalse(StandardSystemProperty.JAVA_VERSION.value().startsWith("1.6"));
        Misc test = newWovenObject(BytecodeWithStackFramesMisc.class, Misc.class,
                TestBytecodeWithStackFramesAdvice2.class);
        test.executeWithReturn();
    }

    @Test
    // test weaving against jdk 1.7 bytecode with stack frames
    public void shouldWeaveBytecodeWithStackFrames3() throws Exception {
        Assume.assumeFalse(StandardSystemProperty.JAVA_VERSION.value().startsWith("1.6"));
        Misc test = newWovenObject(BytecodeWithStackFramesMisc.class, Misc.class,
                TestBytecodeWithStackFramesAdvice3.class);
        test.executeWithReturn();
    }

    @Test
    // test weaving against jdk 1.7 bytecode with stack frames
    public void shouldWeaveBytecodeWithStackFrames4() throws Exception {
        Assume.assumeFalse(StandardSystemProperty.JAVA_VERSION.value().startsWith("1.6"));
        Misc test = newWovenObject(BytecodeWithStackFramesMisc.class, Misc.class,
                TestBytecodeWithStackFramesAdvice4.class);
        test.executeWithReturn();
    }

    @Test
    // test weaving against jdk 1.7 bytecode with stack frames
    public void shouldWeaveBytecodeWithStackFrames5() throws Exception {
        Assume.assumeFalse(StandardSystemProperty.JAVA_VERSION.value().startsWith("1.6"));
        Misc test = newWovenObject(BytecodeWithStackFramesMisc.class, Misc.class,
                TestBytecodeWithStackFramesAdvice5.class);
        test.executeWithReturn();
    }

    @Test
    // test weaving against jdk 1.7 bytecode with stack frames
    public void shouldWeaveBytecodeWithStackFrames6() throws Exception {
        Assume.assumeFalse(StandardSystemProperty.JAVA_VERSION.value().startsWith("1.6"));
        Misc test = newWovenObject(BytecodeWithStackFramesMisc.class, Misc.class,
                TestBytecodeWithStackFramesAdvice6.class);
        test.executeWithReturn();
    }

    @Test
    // test weaving against jdk 1.7 bytecode with stack frames
    public void shouldNotBombWithDuplicateFrames() throws Exception {
        // TODO this test only proves something when -target 1.7 (which currently it never is during
        // travis build)
        assumeJdk7();
        newWovenObject(DuplicateStackFramesMisc.class, Misc.class, BasicAdvice.class);
    }

    @Test
    public void shouldNotBombWithTroublesomeBytecode() throws Exception {
        // this actually works with -target 1.6 as long as run using 1.7 jvm since it defines the
        // troublesome bytecode at runtime as jdk 1.7 bytecode
        assumeJdk7();
        Misc test = newWovenObject(TroublesomeBytecodeMisc.class, Misc.class,
                TestTroublesomeBytecodeAdvice.class);
        test.execute1();
    }

    // ===================== test not perfect bytecode =====================

    @Test
    public void shouldExecuteAdviceOnNotPerfectBytecode() throws Exception {
        // given
        LazyDefinedClass implClass = GenerateNotPerfectBytecode.generateNotPerfectBytecode();
        GenerateNotPerfectBytecode.Test test = newWovenObject(implClass,
                GenerateNotPerfectBytecode.Test.class, NotPerfectBytecodeAdvice.class);
        // when
        test.test1();
        test.test2();
        test.test3();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(3);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(3);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(3);
    }

    @Test
    public void shouldExecuteAdviceOnMoreNotPerfectBytecode() throws Exception {
        // given
        LazyDefinedClass implClass =
                GenerateMoreNotPerfectBytecode.generateMoreNotPerfectBytecode(false);
        GenerateMoreNotPerfectBytecode.Test test = newWovenObject(implClass,
                GenerateMoreNotPerfectBytecode.Test.class, MoreNotPerfectBytecodeAdvice.class);
        // when
        test.execute();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteAdviceOnMoreNotPerfectBytecodeVariant() throws Exception {
        // given
        LazyDefinedClass implClass =
                GenerateMoreNotPerfectBytecode.generateMoreNotPerfectBytecode(true);
        GenerateMoreNotPerfectBytecode.Test test = newWovenObject(implClass,
                GenerateMoreNotPerfectBytecode.Test.class, MoreNotPerfectBytecodeAdvice.class);
        // when
        test.execute();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteAdviceOnStillMoreNotPerfectBytecode() throws Exception {
        // given
        LazyDefinedClass implClass =
                GenerateStillMoreNotPerfectBytecode.generateStillMoreNotPerfectBytecode();
        GenerateStillMoreNotPerfectBytecode.Test test = newWovenObject(implClass,
                GenerateStillMoreNotPerfectBytecode.Test.class, MoreNotPerfectBytecodeAdvice.class);
        // when
        test.execute();
        // then
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteAdviceOnHackedConstructorBytecode() throws Exception {
        // given
        LazyDefinedClass implClass =
                GenerateHackedConstructorBytecode.generateHackedConstructorBytecode();
        newWovenObject(implClass, GenerateHackedConstructorBytecode.Test.class,
                enhanceConstructorAdviceClass(HackedConstructorBytecodeAdvice.class));
        // when
        // (advice is on constructor, so already captured above)
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteAdviceOnMoreHackedConstructorBytecode() throws Exception {
        // given
        LazyDefinedClass implClass =
                GenerateMoreHackedConstructorBytecode.generateMoreHackedConstructorBytecode();
        newWovenObject(implClass, GenerateMoreHackedConstructorBytecode.Test.class,
                enhanceConstructorAdviceClass(HackedConstructorBytecodeAdvice.class));
        // when
        // (advice is on constructor, so already captured above)
        // then
        assertThat(SomeInstrumentationThreadLocals.enabledCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteSingleJumpAdviceOnHackedConstructorBytecode() throws Exception {
        // given
        LazyDefinedClass implClass =
                GenerateHackedConstructorBytecode.generateHackedConstructorBytecode();

        // when
        Exception exception = null;
        try {
            newWovenObject(implClass, GenerateHackedConstructorBytecode.Test.class,
                    enhanceConstructorAdviceClass(HackedConstructorBytecodeJumpingAdvice.class));
        } catch (Exception e) {
            exception = e;
        }

        // then
        // this is just to confirm VerifyError is not encountered above due to bad class frames
        assertThat(exception.getCause().getCause().getMessage())
                .startsWith("Bytecode service retrieved ");
    }

    // ===================== test mutable parameter =====================

    @Test
    public void shouldMutateParameter() throws Exception {
        // given
        Misc test = newWovenObject(ThrowMutatedParamMisc.class, Misc.class,
                BindMutableParameterAdvice.class);
        // when
        String param = null;
        try {
            test.executeWithArgs("one", 2);
        } catch (RuntimeException e) {
            param = e.getMessage();
        }
        // then
        assertThat(param).isEqualTo("one and more / 3");
    }

    @Test
    public void shouldMutateParameterWithMoreFrames() throws Exception {
        // when
        newWovenObject(ThrowMutatedParamMisc.class, Misc.class,
                BindMutableParameterWithMoreFramesAdvice.class);

        // then
        // do not crash with java.lang.VerifyError
    }

    public static <S, T extends S> S newWovenObject(Class<T> implClass, Class<S> bridgeClass,
            Class<?> adviceOrShimOrMixinClass, Class<?>... extraBridgeClasses) throws Exception {
        // SomeInstrumentationThreadLocals is passed as bridgeable so that the static thread locals
        // will be accessible for test verification
        List<Class<?>> bridgeClasses = Lists.newArrayList();
        bridgeClasses.add(bridgeClass);
        bridgeClasses.add(SomeInstrumentationThreadLocals.class);
        bridgeClasses.add(IntegerThreadLocal.class);
        bridgeClasses.addAll(Arrays.asList(extraBridgeClasses));
        IsolatedWeavingClassLoader isolatedWeavingClassLoader =
                new IsolatedWeavingClassLoader(bridgeClasses.toArray(new Class<?>[0]));
        List<Advice> advisors = Lists.newArrayList();
        if (adviceOrShimOrMixinClass.isAnnotationPresent(Pointcut.class)) {
            advisors.add(newAdvice(adviceOrShimOrMixinClass));
        }
        List<MixinType> mixinTypes = Lists.newArrayList();
        Mixin mixin = adviceOrShimOrMixinClass.getAnnotation(Mixin.class);
        if (mixin != null) {
            mixinTypes.add(newMixin(adviceOrShimOrMixinClass));
        }
        List<ShimType> shimTypes = Lists.newArrayList();
        Shim shim = adviceOrShimOrMixinClass.getAnnotation(Shim.class);
        if (shim != null) {
            shimTypes.add(ShimType.create(shim, adviceOrShimOrMixinClass));
        }
        Supplier<List<Advice>> advisorsSupplier =
                Suppliers.<List<Advice>>ofInstance(ImmutableList.copyOf(advisors));
        AnalyzedWorld analyzedWorld =
                new AnalyzedWorld(advisorsSupplier, shimTypes, mixinTypes, null);
        Weaver weaver = new Weaver(advisorsSupplier, shimTypes, mixinTypes, analyzedWorld,
                Ticker.systemTicker());
        isolatedWeavingClassLoader.setWeaver(weaver);
        return isolatedWeavingClassLoader.newInstance(implClass, bridgeClass);
    }

    public static <S, T extends S> S newWovenObject(LazyDefinedClass toBeDefinedImplClass,
            Class<S> bridgeClass, Class<?> adviceOrShimOrMixinClass, Class<?>... extraBridgeClasses)
            throws Exception {
        // SomeInstrumentationThreadLocals is passed as bridgeable so that the static thread locals
        // will be accessible for test verification
        List<Class<?>> bridgeClasses = Lists.newArrayList();
        bridgeClasses.add(bridgeClass);
        bridgeClasses.add(SomeInstrumentationThreadLocals.class);
        bridgeClasses.add(IntegerThreadLocal.class);
        bridgeClasses.addAll(Arrays.asList(extraBridgeClasses));
        IsolatedWeavingClassLoader isolatedWeavingClassLoader =
                new IsolatedWeavingClassLoader(bridgeClasses.toArray(new Class<?>[0]));
        List<Advice> advisors = Lists.newArrayList();
        if (adviceOrShimOrMixinClass.isAnnotationPresent(Pointcut.class)) {
            advisors.add(newAdvice(adviceOrShimOrMixinClass));
        }
        List<MixinType> mixinTypes = Lists.newArrayList();
        Mixin mixin = adviceOrShimOrMixinClass.getAnnotation(Mixin.class);
        if (mixin != null) {
            mixinTypes.add(newMixin(adviceOrShimOrMixinClass));
        }
        List<ShimType> shimTypes = Lists.newArrayList();
        Shim shim = adviceOrShimOrMixinClass.getAnnotation(Shim.class);
        if (shim != null) {
            shimTypes.add(ShimType.create(shim, adviceOrShimOrMixinClass));
        }
        Supplier<List<Advice>> advisorsSupplier =
                Suppliers.<List<Advice>>ofInstance(ImmutableList.copyOf(advisors));
        AnalyzedWorld analyzedWorld =
                new AnalyzedWorld(advisorsSupplier, shimTypes, mixinTypes, null);
        Weaver weaver = new Weaver(advisorsSupplier, shimTypes, mixinTypes, analyzedWorld,
                Ticker.systemTicker());
        isolatedWeavingClassLoader.setWeaver(weaver);

        String className = toBeDefinedImplClass.getType().getClassName();
        isolatedWeavingClassLoader.addManualClass(className, toBeDefinedImplClass.getBytes());
        @SuppressWarnings("unchecked")
        Class<T> implClass = (Class<T>) Class.forName(className, false, isolatedWeavingClassLoader);
        return isolatedWeavingClassLoader.newInstance(implClass, bridgeClass);
    }

    private static Advice newAdvice(Class<?> clazz) throws Exception {
        return new AdviceBuilder(InstrumentationDetailBuilder.buildAdviceClass(clazz)).build();
    }

    private static MixinType newMixin(Class<?> clazz) throws Exception {
        return MixinType.create(InstrumentationDetailBuilder.buildMixinClass(clazz));
    }

    private static Class<?> enhanceConstructorAdviceClass(Class<?> adviceClass)
            throws ClassNotFoundException {
        IsolatedWeavingClassLoader isolatedWeavingClassLoader = new IsolatedWeavingClassLoader();
        return Class.forName(adviceClass.getName(), false, isolatedWeavingClassLoader);
    }

    private static void assumeJdk7() {
        Assume.assumeFalse(StandardSystemProperty.JAVA_VERSION.value().startsWith("1.6"));
    }
}
