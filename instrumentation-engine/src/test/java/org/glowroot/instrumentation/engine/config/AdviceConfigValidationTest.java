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
package org.glowroot.instrumentation.engine.config;

import java.util.List;

import org.junit.Test;

import org.glowroot.instrumentation.api.Descriptor.CaptureKind;

import static org.assertj.core.api.Assertions.assertThat;

public class AdviceConfigValidationTest {

    private final AdviceConfig baseConfig =
            ImmutableAdviceConfig.builder()
                    .className("a")
                    .methodName("n")
                    .addMethodParameterTypes("java.lang.String")
                    .methodReturnType("")
                    .captureKind(CaptureKind.TIMER)
                    .timerName("t")
                    .spanMessageTemplate("")
                    .spanCaptureSelfNested(false)
                    .transactionType("")
                    .transactionNameTemplate("")
                    .transactionUserTemplate("")
                    .enabledProperty("")
                    .localSpanEnabledProperty("")
                    .build();

    @Test
    public void testValid() {
        // when
        List<String> validationErrors = baseConfig.validationErrors();
        // then
        assertThat(validationErrors).isEmpty();
    }

    @Test
    public void testInvalidClassNameAndMethodName() {
        // given
        AdviceConfig config = ImmutableAdviceConfig.builder()
                .from(baseConfig)
                .className("")
                .methodName("")
                .build();
        // when
        List<String> validationErrors = config.validationErrors();
        // then
        assertThat(validationErrors).containsExactly(
                "className and classAnnotation are both empty",
                "methodName and methodAnnotation are both empty");
    }

    @Test
    public void testInvalidEmptyTimerName() {
        // given
        AdviceConfig config = ImmutableAdviceConfig.builder()
                .from(baseConfig)
                .timerName("")
                .build();
        // when
        List<String> validationErrors = config.validationErrors();
        // then
        assertThat(validationErrors).containsExactly("timerName is empty");
    }

    @Test
    public void testInvalidCharactersInTimerName() {
        // given
        AdviceConfig config = ImmutableAdviceConfig.builder()
                .from(baseConfig)
                .timerName("a_b")
                .build();
        // when
        List<String> validationErrors = config.validationErrors();
        // then
        assertThat(validationErrors).containsExactly("timerName contains invalid characters: a_b");
    }

    @Test
    public void testValidEmptyTimerName() {
        // given
        AdviceConfig config = ImmutableAdviceConfig.builder()
                .from(baseConfig)
                .captureKind(CaptureKind.OTHER)
                .timerName("")
                .build();
        // when
        List<String> validationErrors = config.validationErrors();
        // then
        assertThat(validationErrors).isEmpty();
    }

    @Test
    public void testInvalidLocalSpan() {
        // given
        AdviceConfig config = ImmutableAdviceConfig.builder()
                .from(baseConfig)
                .captureKind(CaptureKind.LOCAL_SPAN)
                .build();
        // when
        List<String> validationErrors = config.validationErrors();
        // then
        assertThat(validationErrors).containsExactly("spanMessageTemplate is empty");
    }

    @Test
    public void testInvalidTransaction() {
        // given
        AdviceConfig config = ImmutableAdviceConfig.builder()
                .from(baseConfig)
                .captureKind(CaptureKind.TRANSACTION)
                .build();
        // when
        List<String> validationErrors = config.validationErrors();
        // then
        assertThat(validationErrors).containsExactly("transactionType is empty",
                "transactionNameTemplate is empty");
    }
}
