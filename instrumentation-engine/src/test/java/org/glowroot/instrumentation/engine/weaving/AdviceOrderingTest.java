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

import com.google.common.collect.Ordering;
import org.junit.Test;
import org.objectweb.asm.Type;

import org.glowroot.instrumentation.api.weaving.Advice.Pointcut;

import static org.assertj.core.api.Assertions.assertThat;

public class AdviceOrderingTest {

    private final Pointcut pointcutPriority1 =
            OnlyForTheOrder1.class.getAnnotation(Pointcut.class);

    private final Pointcut pointcutPriority2 =
            OnlyForTheOrder2.class.getAnnotation(Pointcut.class);

    private final Advice advicePriority1 = ImmutableAdvice.builder()
            .pointcut(pointcutPriority1)
            .adviceType(Type.getType(AdviceOrderingTest.class))
            .reweavable(false)
            .hasBindThreadContext(false)
            .hasBindOptionalThreadContext(false)
            .build();

    private final Advice advicePriority2 = ImmutableAdvice.builder()
            .pointcut(pointcutPriority2)
            .adviceType(Type.getType(AdviceOrderingTest.class))
            .reweavable(false)
            .hasBindThreadContext(false)
            .hasBindOptionalThreadContext(false)
            .build();

    @Test
    public void shouldCompare() {
        Ordering<Advice> ordering = Advice.ordering;
        assertThat(ordering.compare(advicePriority1, advicePriority2)).isNegative();
        assertThat(ordering.compare(advicePriority2, advicePriority1)).isPositive();
    }

    @Pointcut(className = "dummy", methodName = "dummy", methodParameterTypes = {}, order = 1)
    private static class OnlyForTheOrder1 {}

    @Pointcut(className = "dummy", methodName = "dummy", methodParameterTypes = {}, order = 2)
    private static class OnlyForTheOrder2 {}
}
