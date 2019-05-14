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

import org.junit.Test;

import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindPrimitiveBooleanTravelerBadAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.BindPrimitiveTravelerBadAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MoreVeryBadAdvice;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.MoreVeryBadAdvice2;
import org.glowroot.instrumentation.engine.weaving.SomeInstrumentation.VeryBadAdvice;
import org.glowroot.instrumentation.engine.weaving.targets.BasicMisc;
import org.glowroot.instrumentation.engine.weaving.targets.Misc;

import static org.assertj.core.api.Assertions.assertThat;

public class WeaverErrorHandlingTest {

    @Test
    public void shouldHandleVoidPrimitiveTravelerGracefully() throws Exception {
        // given
        SomeInstrumentationThreadLocals.resetThreadLocals();
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, BindPrimitiveTravelerBadAdvice.class);

        // when
        test.execute1();

        // then
        assertThat(SomeInstrumentationThreadLocals.onReturnTraveler.get()).isEqualTo(0);
        assertThat(SomeInstrumentationThreadLocals.onThrowTraveler.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterTraveler.get()).isEqualTo(0);
    }

    @Test
    public void shouldHandleVoidPrimitiveBooleanTravelerGracefully() throws Exception {
        // given
        SomeInstrumentationThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                BindPrimitiveBooleanTravelerBadAdvice.class);

        // when
        test.execute1();

        // then
        assertThat(SomeInstrumentationThreadLocals.onReturnTraveler.get()).isEqualTo(false);
        assertThat(SomeInstrumentationThreadLocals.onThrowTraveler.get()).isNull();
        assertThat(SomeInstrumentationThreadLocals.onAfterTraveler.get()).isEqualTo(false);
    }

    @Test
    public void shouldNotCallOnThrowForOnBeforeException() throws Exception {
        // given
        SomeInstrumentationThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, VeryBadAdvice.class);

        // when
        IllegalStateException exception = null;
        try {
            test.executeWithArgs("one", 2);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Sorry");
            assertThat(SomeInstrumentationThreadLocals.onBeforeCount.get()).isEqualTo(1);
            assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
            assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
            exception = e;
        }

        // then
        assertThat(exception).isNotNull();
    }

    @Test
    public void shouldNotCallOnThrowForOnReturnException() throws Exception {
        // given
        SomeInstrumentationThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MoreVeryBadAdvice.class);

        // when
        IllegalStateException exception = null;
        try {
            test.executeWithArgs("one", 2);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Sorry");
            assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
            assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
            assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
            exception = e;
        }

        // then
        assertThat(exception).isNotNull();
    }

    // same as MoreVeryBadAdvice, but testing weaving a method with a non-void return type
    @Test
    public void shouldNotCallOnThrowForOnReturnException2() throws Exception {
        // given
        SomeInstrumentationThreadLocals.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MoreVeryBadAdvice2.class);

        // when
        IllegalStateException exception = null;
        try {
            test.executeWithReturn();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Sorry");
            assertThat(SomeInstrumentationThreadLocals.onReturnCount.get()).isEqualTo(1);
            assertThat(SomeInstrumentationThreadLocals.onThrowCount.get()).isEqualTo(0);
            assertThat(SomeInstrumentationThreadLocals.onAfterCount.get()).isEqualTo(0);
            exception = e;
        }

        // then
        assertThat(exception).isNotNull();
    }

    private static <S, T extends S> S newWovenObject(Class<T> implClass, Class<S> bridgeClass,
            Class<?> adviceClass) throws Exception {
        // adviceClass is passed as bridgeable so that the static threadlocals will be accessible
        // for test verification
        return WeaverTest.newWovenObject(implClass, bridgeClass, adviceClass);
    }
}
