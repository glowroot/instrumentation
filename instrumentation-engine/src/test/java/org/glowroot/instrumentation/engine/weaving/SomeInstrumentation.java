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

import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.ClassInfo;
import org.glowroot.instrumentation.api.MethodInfo;
import org.glowroot.instrumentation.api.ParameterHolder;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Advice.MethodModifier;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.api.weaving.MixinInit;
import org.glowroot.instrumentation.api.weaving.OptionalReturn;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.engine.weaving.targets.Misc;

public class SomeInstrumentation {

    @Advice.Pointcut(
                     className = "org.glowroot.instrumentation.engine.weaving.targets.Misc"
                             + "|org.glowroot.instrumentation.engine.weaving.targets.DefaultMethodMisc"
                             + "|org.glowroot.instrumentation.engine.weaving.targets.DefaultMethodMisc2",
                     methodName = "execute1|execute2",
                     methodParameterTypes = {})
    public static class BasicAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.SuperBasicMisc",
                     methodName = "superBasic",
                     methodParameterTypes = {})
    public static class SuperBasicAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "java.lang.Throwable", methodName = "toString",
                     methodParameterTypes = {})
    public static class ThrowableToStringAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.GenericMisc",
                     methodName = "*",
                     methodParameterTypes = {"java.lang.Object|java.lang.Class"})
    public static class GenericMiscAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc",
                     methodName = "<init>",
                     methodParameterTypes = {})
    public static class BasicMiscConstructorAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc",
                     methodName = "<init>",
                     methodParameterTypes = {".."})
    public static class BasicMiscAllConstructorAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.orderedEvents.get().add("isEnabled");
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.orderedEvents.get().add("onBefore");
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.orderedEvents.get().add("onReturn");
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.orderedEvents.get().add("onThrow");
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.orderedEvents.get().add("onAfter");
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc",
                     methodName = "withInnerArg",
                     methodParameterTypes = {
                             "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc$Inner"})
    public static class BasicWithInnerClassArgAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc$InnerMisc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BasicWithInnerClassAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindReceiverAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This Misc receiver) {
            SomeInstrumentationThreadLocals.isEnabledReceiver.set(receiver);
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.This Misc receiver) {
            SomeInstrumentationThreadLocals.onBeforeReceiver.set(receiver);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This Misc receiver) {
            SomeInstrumentationThreadLocals.onReturnReceiver.set(receiver);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.This Misc receiver) {
            SomeInstrumentationThreadLocals.onThrowReceiver.set(receiver);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.This Misc receiver) {
            SomeInstrumentationThreadLocals.onAfterReceiver.set(receiver);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int"})
    public static class BindParameterAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) String one,
                @Bind.Argument(1) int two) {
            SomeInstrumentationThreadLocals.isEnabledParams.set(new Object[] {one, two});
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.Argument(0) String one,
                @Bind.Argument(1) int two) {
            SomeInstrumentationThreadLocals.onBeforeParams.set(new Object[] {one, two});
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Argument(0) String one,
                @Bind.Argument(1) int two) {
            SomeInstrumentationThreadLocals.onReturnParams.set(new Object[] {one, two});
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Argument(0) String one,
                @Bind.Argument(1) int two) {
            SomeInstrumentationThreadLocals.onThrowParams.set(new Object[] {one, two});
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Argument(0) String one,
                @Bind.Argument(1) int two) {
            SomeInstrumentationThreadLocals.onAfterParams.set(new Object[] {one, two});
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int"})
    public static class BindParameterArrayAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.AllArguments Object[] args) {
            SomeInstrumentationThreadLocals.isEnabledParams.set(args);
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.AllArguments Object[] args) {
            SomeInstrumentationThreadLocals.onBeforeParams.set(args);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.AllArguments Object[] args) {
            SomeInstrumentationThreadLocals.onReturnParams.set(args);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.AllArguments Object[] args) {
            SomeInstrumentationThreadLocals.onThrowParams.set(args);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.AllArguments Object[] args) {
            SomeInstrumentationThreadLocals.onAfterParams.set(args);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindTravelerAdvice {

        @Advice.OnMethodBefore
        public static String onBefore() {
            return "a traveler";
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter String traveler) {
            SomeInstrumentationThreadLocals.onReturnTraveler.set(traveler);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Enter String traveler) {
            SomeInstrumentationThreadLocals.onThrowTraveler.set(traveler);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter String traveler) {
            SomeInstrumentationThreadLocals.onAfterTraveler.set(traveler);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindPrimitiveTravelerAdvice {

        @Advice.OnMethodBefore
        public static int onBefore() {
            return 3;
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter int traveler) {
            SomeInstrumentationThreadLocals.onReturnTraveler.set(traveler);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Enter int traveler) {
            SomeInstrumentationThreadLocals.onThrowTraveler.set(traveler);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter int traveler) {
            SomeInstrumentationThreadLocals.onAfterTraveler.set(traveler);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindPrimitiveBooleanTravelerAdvice {

        @Advice.OnMethodBefore
        public static boolean onBefore() {
            return true;
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter boolean traveler) {
            SomeInstrumentationThreadLocals.onReturnTraveler.set(traveler);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Enter boolean traveler) {
            SomeInstrumentationThreadLocals.onThrowTraveler.set(traveler);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter boolean traveler) {
            SomeInstrumentationThreadLocals.onAfterTraveler.set(traveler);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindPrimitiveTravelerBadAdvice {

        @Advice.OnMethodBefore
        public static void onBefore() {}

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter int traveler) {
            SomeInstrumentationThreadLocals.onReturnTraveler.set(traveler);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Enter int traveler) {
            SomeInstrumentationThreadLocals.onThrowTraveler.set(traveler);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter int traveler) {
            SomeInstrumentationThreadLocals.onAfterTraveler.set(traveler);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindPrimitiveBooleanTravelerBadAdvice {

        @Advice.OnMethodBefore
        public static void onBefore() {}

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter boolean traveler) {
            SomeInstrumentationThreadLocals.onReturnTraveler.set(traveler);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Enter boolean traveler) {
            SomeInstrumentationThreadLocals.onThrowTraveler.set(traveler);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter boolean traveler) {
            SomeInstrumentationThreadLocals.onAfterTraveler.set(traveler);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindClassMetaAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.ClassMeta TestClassMeta meta) {
            SomeInstrumentationThreadLocals.isEnabledClassMeta.set(meta);
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.ClassMeta TestClassMeta meta) {
            SomeInstrumentationThreadLocals.onBeforeClassMeta.set(meta);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.ClassMeta TestClassMeta meta) {
            SomeInstrumentationThreadLocals.onReturnClassMeta.set(meta);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.ClassMeta TestClassMeta meta) {
            SomeInstrumentationThreadLocals.onThrowClassMeta.set(meta);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.ClassMeta TestClassMeta meta) {
            SomeInstrumentationThreadLocals.onAfterClassMeta.set(meta);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {".."})
    public static class BindMethodMetaAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.isEnabledMethodMeta.set(meta);
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onBeforeMethodMeta.set(meta);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onReturnMethodMeta.set(meta);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onThrowMethodMeta.set(meta);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onAfterMethodMeta.set(meta);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.other.ArrayMisc",
                     methodName = "executeArray",
                     methodParameterTypes = {".."})
    public static class BindMethodMetaArrayAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.isEnabledMethodMeta.set(meta);
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onBeforeMethodMeta.set(meta);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onReturnMethodMeta.set(meta);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onThrowMethodMeta.set(meta);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onAfterMethodMeta.set(meta);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.other.ArrayMisc",
                     methodName = "executeWithArrayReturn",
                     methodParameterTypes = {".."})
    public static class BindMethodMetaReturnArrayAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.isEnabledMethodMeta.set(meta);
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onBeforeMethodMeta.set(meta);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onReturnMethodMeta.set(meta);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onThrowMethodMeta.set(meta);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.MethodMeta TestMethodMeta meta) {
            SomeInstrumentationThreadLocals.onAfterMethodMeta.set(meta);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {})
    public static class BindReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Return String value) {
            SomeInstrumentationThreadLocals.returnValue.set(value);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.PrimitiveMisc",
                     methodName = "executeWithIntReturn",
                     methodParameterTypes = {})
    public static class BindPrimitiveReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Return int value) {
            SomeInstrumentationThreadLocals.returnValue.set(value);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.PrimitiveMisc",
                     methodName = "executeWithIntReturn",
                     methodParameterTypes = {})
    public static class BindAutoboxedReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Return Object value) {
            SomeInstrumentationThreadLocals.returnValue.set(value);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {})
    public static class BindOptionalReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.OptionalReturn OptionalReturn optionalReturn) {
            SomeInstrumentationThreadLocals.optionalReturnValue.set(optionalReturn);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindOptionalVoidReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.OptionalReturn OptionalReturn optionalReturn) {
            SomeInstrumentationThreadLocals.optionalReturnValue.set(optionalReturn);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.PrimitiveMisc",
                     methodName = "executeWithIntReturn",
                     methodParameterTypes = {})
    public static class BindOptionalPrimitiveReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.OptionalReturn OptionalReturn optionalReturn) {
            SomeInstrumentationThreadLocals.optionalReturnValue.set(optionalReturn);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindThrowableAdvice {

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Thrown Throwable t) {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
            SomeInstrumentationThreadLocals.throwable.set(t);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {},
                     order = 1)
    public static class ThrowInOnBeforeAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            throw new RuntimeException("Abxy");
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {},
                     order = 1000)
    public static class BasicHighOrderAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class BindMethodNameAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.MethodName String methodName) {
            SomeInstrumentationThreadLocals.isEnabledMethodName.set(methodName);
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.MethodName String methodName) {
            SomeInstrumentationThreadLocals.onBeforeMethodName.set(methodName);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.MethodName String methodName) {
            SomeInstrumentationThreadLocals.onReturnMethodName.set(methodName);
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.MethodName String methodName) {
            SomeInstrumentationThreadLocals.onThrowMethodName.set(methodName);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.MethodName String methodName) {
            SomeInstrumentationThreadLocals.onAfterMethodName.set(methodName);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {})
    public static class ChangeReturnAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            return true;
        }

        @Advice.OnMethodReturn
        public static String onReturn(@Bind.Return String value,
                @Bind.MethodName String methodName) {
            return "modified " + value + ":" + methodName;
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {".."})
    public static class MethodParametersDotDotAdvice1 {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"..", ".."})
    public static class MethodParametersBadDotDotAdvice1 {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", ".."})
    public static class MethodParametersDotDotAdvice2 {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int", ".."})
    public static class MethodParametersDotDotAdvice3 {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     subTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class SubTypeRestrictionAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.SuperBasicMisc",
                     subTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc",
                     methodName = "callSuperBasic",
                     methodParameterTypes = {})
    public static class SubTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     subTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.SubBasicMisc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class SubTypeRestrictionWhereMethodNotReImplementedInSubSubClassAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(classAnnotation = "org.glowroot.instrumentation.engine.weaving.SomeInstrumentation$SomeClass",
                     methodAnnotation = "org.glowroot.instrumentation.engine.weaving.SomeInstrumentation$SomeMethod",
                     methodParameterTypes = {".."})
    public static class BasicAnnotationBasedAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "*",
                     superTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.SuperBasicMisc",
                     methodAnnotation = "org.glowroot.instrumentation.engine.weaving.SomeInstrumentation$SomeMethod",
                     methodParameterTypes = {".."})
    public static class AnotherAnnotationBasedAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "*",
                     superTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.SuperBasicMiscButWrong",
                     methodAnnotation = "org.glowroot.instrumentation.engine.weaving.SomeInstrumentation$SomeMethod",
                     methodParameterTypes = {".."})
    public static class AnotherAnnotationBasedAdviceButWrong {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "*.BasicMisc",
                     superTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class SuperTypeRestrictionAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "*.BasicMisc",
                     superTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.SuperBasicMisc",
                     methodName = "callSuperBasic",
                     methodParameterTypes = {})
    public static class SuperTypeRestrictionWhereMethodNotReImplementedInSubClassAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Advice.Pointcut(className = "*SubMisc",
                     superTypeRestriction = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "sub",
                     methodParameterTypes = {})
    public static class ComplexSuperTypeRestrictionAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return SomeInstrumentationThreadLocals.enabled.get();
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }

        public static void enable() {
            SomeInstrumentationThreadLocals.enabled.set(true);
        }

        public static void disable() {
            SomeInstrumentationThreadLocals.enabled.set(false);
        }
    }

    @Shim("org.glowroot.instrumentation.engine.weaving.targets.ShimmedMisc")
    public interface Shimmy {

        @Shim("java.lang.String getString()")
        Object shimmyGetString();

        @Shim("void setString(java.lang.String)")
        void shimmySetString(String string);
    }

    public interface HasString {

        String getString();

        void setString(String string);
    }

    @Mixin("org.glowroot.instrumentation.engine.weaving.targets.BasicMisc")
    public static class HasStringClassMixin implements HasString {

        private transient String string;

        @MixinInit
        private void initHasString() {
            if (string == null) {
                string = "a string";
            } else {
                string = "init called twice";
            }
        }

        @Override
        public String getString() {
            return string;
        }

        @Override
        public void setString(String string) {
            this.string = string;
        }
    }

    @Mixin("org.glowroot.instrumentation.engine.weaving.targets.Misc")
    public static class HasStringInterfaceMixin implements HasString {

        private transient String string;

        @MixinInit
        private void initHasString() {
            string = "a string";
        }

        @Override
        public String getString() {
            return string;
        }

        @Override
        public void setString(String string) {
            this.string = string;
        }
    }

    @Mixin({"org.glowroot.instrumentation.engine.weaving.targets.Misc",
            "org.glowroot.instrumentation.engine.weaving.targets.Misc2"})
    public static class HasStringMultipleMixin implements HasString {

        private transient String string;

        @MixinInit
        private void initHasString() {
            string = "a string";
        }

        @Override
        public String getString() {
            return string;
        }

        @Override
        public void setString(String string) {
            this.string = string;
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute*",
                     methodParameterTypes = {".."})
    public static class InnerMethodAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute*",
                     methodParameterTypes = {".."})
    public static class MultipleMethodsAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.StaticMisc",
                     methodName = "executeStatic",
                     methodParameterTypes = {})
    public static class StaticAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {},
                     methodModifiers = MethodModifier.STATIC)
    public static class NonMatchingStaticAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {},
                     methodModifiers = {MethodModifier.PUBLIC, MethodModifier.NOT_STATIC})
    public static class MatchingPublicNonStaticAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Mis*",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class ClassNamePatternAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {},
                     methodReturnType = "void")
    public static class MethodReturnVoidAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {},
                     methodReturnType = "java.lang.CharSequence")
    public static class MethodReturnCharSequenceAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {},
                     methodReturnType = "java.lang.String")
    public static class MethodReturnStringAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {},
                     methodReturnType = "java.lang.String")
    public static class NonMatchingMethodReturnAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {},
                     methodReturnType = "java.lang.Number")
    public static class NonMatchingMethodReturnAdvice2 extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {},
                     methodReturnType = "java.lang.")
    public static class MethodReturnNarrowingAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "*",
                     methodParameterTypes = {".."})
    public static class WildMethodAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.PrimitiveMisc",
                     methodName = "executePrimitive",
                     methodParameterTypes = {"int", "double", "long", "byte[]"})
    public static class PrimitiveAdvice extends BasicAdvice {}

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.PrimitiveMisc",
                     methodName = "executePrimitive",
                     methodParameterTypes = {"int", "double", "*", ".."})
    public static class PrimitiveWithWildcardAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(
                @SuppressWarnings("unused") @Bind.Argument(0) int x) {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore(@SuppressWarnings("unused") @Bind.Argument(0) int x) {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.PrimitiveMisc",
                     methodName = "executePrimitive",
                     methodParameterTypes = {"int", "double", "*", ".."})
    public static class PrimitiveWithAutoboxAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(
                @SuppressWarnings("unused") @Bind.Argument(0) Object x) {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int"})
    public static class BrokenAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            return true;
        }

        @Advice.OnMethodBefore
        public static @Nullable Object onBefore() {
            return null;
        }

        @Advice.OnMethodAfter
        public static void onAfter(
                @SuppressWarnings("unused") @Bind.Enter Object traveler) {}
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int"})
    public static class VeryBadAdvice {

        @Advice.OnMethodBefore
        public static Object onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
            throw new IllegalStateException("Sorry");
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            // should not get called
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            // should not get called
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int"})
    public static class MoreVeryBadAdvice {

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
            throw new IllegalStateException("Sorry");
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            // should not get called
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            // should not get called
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    // same as MoreVeryBadAdvice, but testing weaving a method with a non-void return type
    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithReturn",
                     methodParameterTypes = {})
    public static class MoreVeryBadAdvice2 {

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
            throw new IllegalStateException("Sorry");
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            // should not get called
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            // should not get called
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc3",
                     methodName = "identity",
                     methodParameterTypes = {
                             "org.glowroot.instrumentation.engine.weaving.targets.BasicMisc"})
    public static class CircularClassDependencyAdvice {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "execute1",
                     methodParameterTypes = {})
    public static class InterfaceAppearsTwiceInHierarchyAdvice {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {".."})
    public static class FinalMethodAdvice {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }
    }

    // test weaving against JSR bytecode that ends up being inlined via JSRInlinerAdapter
    @Advice.Pointcut(className = "org.apache.jackrabbit.core.persistence.pool.BundleDbPersistenceManager",
                     methodName = "loadBundle",
                     methodParameterTypes = {"org.apache.jackrabbit.core.id.NodeId"})
    public static class TestJSRMethodAdvice {}

    // test weaving against 1.7 bytecode with stack frames
    @Advice.Pointcut(className = "org.xnio.Buffers", methodName = "*",
                     methodParameterTypes = {".."})
    public static class TestBytecodeWithStackFramesAdvice {}

    // test weaving against 1.7 bytecode with stack frames
    @Advice.Pointcut(className = "org.xnio.Buffers", methodName = "*",
                     methodParameterTypes = {".."})
    public static class TestBytecodeWithStackFramesAdvice2 {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            return true;
        }
    }

    // test weaving against 1.7 bytecode with stack frames
    @Advice.Pointcut(className = "org.xnio.Buffers", methodName = "*",
                     methodParameterTypes = {".."})
    public static class TestBytecodeWithStackFramesAdvice3 {

        @Advice.OnMethodBefore
        public static void onBefore() {}
    }

    // test weaving against 1.7 bytecode with stack frames
    @Advice.Pointcut(className = "org.xnio.Buffers", methodName = "*",
                     methodParameterTypes = {".."})
    public static class TestBytecodeWithStackFramesAdvice4 {

        @Advice.OnMethodReturn
        public static void onReturn() {}
    }

    // test weaving against 1.7 bytecode with stack frames
    @Advice.Pointcut(className = "org.xnio.Buffers", methodName = "*",
                     methodParameterTypes = {".."})
    public static class TestBytecodeWithStackFramesAdvice5 {

        @Advice.OnMethodThrow
        public static void onThrow() {}
    }

    // test weaving against 1.7 bytecode with stack frames
    @Advice.Pointcut(className = "org.xnio.Buffers", methodName = "*",
                     methodParameterTypes = {".."})
    public static class TestBytecodeWithStackFramesAdvice6 {

        @Advice.OnMethodAfter
        public static void onAfter() {}
    }

    @Advice.Pointcut(className = "TroublesomeBytecode", methodName = "*",
                     methodParameterTypes = {".."})
    public static class TestTroublesomeBytecodeAdvice {

        @Advice.OnMethodAfter
        public static void onAfter() {}
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.GenerateNotPerfectBytecode$Test",
                     methodName = "test*",
                     methodParameterTypes = {})
    public static class NotPerfectBytecodeAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(
                     className = "org.glowroot.instrumentation.engine.weaving.GenerateMoreNotPerfectBytecode$Test"
                             + "|org.glowroot.instrumentation.engine.weaving.GenerateStillMoreNotPerfectBytecode$Test",
                     methodName = "execute",
                     methodParameterTypes = {".."})
    public static class MoreNotPerfectBytecodeAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "HackedConstructorBytecode|MoreHackedConstructorBytecode",
                     methodName = "<init>",
                     methodParameterTypes = {})
    public static class HackedConstructorBytecodeAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "HackedConstructorBytecode",
                     methodName = "<init>",
                     methodParameterTypes = {},
                     nestingGroup = "xyz")
    public static class HackedConstructorBytecodeJumpingAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled() {
            SomeInstrumentationThreadLocals.enabledCount.increment();
            return true;
        }

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "java.lang.Iterable",
                     methodName = "iterator|spliterator",
                     methodParameterTypes = {".."})
    public static class IterableAdvice {

        @Advice.OnMethodBefore
        public static void onBefore() {
            SomeInstrumentationThreadLocals.onBeforeCount.increment();
        }

        @Advice.OnMethodReturn
        public static void onReturn() {
            SomeInstrumentationThreadLocals.onReturnCount.increment();
        }

        @Advice.OnMethodThrow
        public static void onThrow() {
            SomeInstrumentationThreadLocals.onThrowCount.increment();
        }

        @Advice.OnMethodAfter
        public static void onAfter() {
            SomeInstrumentationThreadLocals.onAfterCount.increment();
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int"})
    public static class BindMutableParameterAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.Argument(0) ParameterHolder<String> holder,
                @Bind.Argument(1) ParameterHolder<Integer> holder2) {
            holder.set(holder.get() + " and more");
            holder2.set(holder2.get() + 1);
        }
    }

    @Advice.Pointcut(className = "org.glowroot.instrumentation.engine.weaving.targets.Misc",
                     methodName = "executeWithArgs",
                     methodParameterTypes = {"java.lang.String", "int"},
                     nestingGroup = "xyz")
    public static class BindMutableParameterWithMoreFramesAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(@Bind.Argument(0) ParameterHolder<String> holder,
                @Bind.Argument(1) ParameterHolder<Integer> holder2) {
            holder.set(holder.get() + " and more");
            holder2.set(holder2.get() + 1);
        }
    }

    public static class TestClassMeta {

        private final ClassInfo classInfo;

        public TestClassMeta(ClassInfo classInfo) {
            this.classInfo = classInfo;
        }

        public String getClazzName() {
            return classInfo.getName();
        }
    }

    public static class TestMethodMeta {

        private final MethodInfo methodInfo;

        public TestMethodMeta(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
        }

        public String getDeclaringClassName() {
            return methodInfo.getDeclaringClassName();
        }

        public String getReturnTypeName() {
            return methodInfo.getReturnType().getName();
        }

        public List<String> getParameterTypeNames() {
            List<String> parameterTypeNames = Lists.newArrayList();
            for (Class<?> parameterType : methodInfo.getParameterTypes()) {
                parameterTypeNames.add(parameterType.getName());
            }
            return parameterTypeNames;
        }
    }

    public @interface SomeClass {}

    public @interface SomeMethod {}
}
