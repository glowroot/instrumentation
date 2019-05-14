/*
 * Copyright 2013-2019 the original author or authors.
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
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Gson.TypeAdapters
@Value.Immutable
public abstract class AdviceConfig {

    private static final Logger logger = LoggerFactory.getLogger(AdviceConfig.class);

    @Value.Default
    public String className() {
        return "";
    }

    @Value.Default
    public String classAnnotation() {
        return "";
    }

    @Value.Default
    public String subTypeRestriction() {
        return "";
    }

    @Value.Default
    public String superTypeRestriction() {
        return "";
    }

    // pointcuts with methodDeclaringClassName are no longer supported in 0.9.16, but included here
    // to help with transitioning of old custom instrumentation config
    @Deprecated
    @Value.Default
    public String methodDeclaringClassName() {
        return "";
    }

    @Value.Default
    public String methodName() {
        return "";
    }

    @Value.Default
    public String methodAnnotation() {
        return "";
    }

    // empty methodParameterTypes means match no-arg methods only
    public abstract ImmutableList<String> methodParameterTypes();

    @Value.Default
    public String methodReturnType() {
        return "";
    }

    public abstract ImmutableList<MethodModifier> methodModifiers();

    @Value.Default
    public String nestingGroup() {
        return "";
    }

    @Value.Default
    public int order() {
        return 0;
    }

    public abstract CaptureKind captureKind();

    @Value.Default
    public String transactionType() {
        return "";
    }

    @Value.Default
    public String transactionNameTemplate() {
        return "";
    }

    @Value.Default
    public String transactionUserTemplate() {
        return "";
    }

    public abstract Map<String, String> transactionAttributeTemplates();

    // need to write zero since it is treated different from null
    public abstract @Nullable Integer transactionSlowThresholdMillis();

    public abstract @Nullable AlreadyInTransactionBehavior alreadyInTransactionBehavior();

    // corrected for data prior to 0.10.10
    @Gson.Ignore
    @Value.Derived
    public @Nullable AlreadyInTransactionBehavior alreadyInTransactionBehaviorCorrected() {
        if (captureKind() == CaptureKind.TRANSACTION) {
            return MoreObjects.firstNonNull(alreadyInTransactionBehavior(),
                    AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN);
        } else {
            return null;
        }
    }

    @Value.Default
    public String spanMessageTemplate() {
        return "";
    }

    // need to write zero since it is treated different from null
    public abstract @Nullable Integer spanStackThresholdMillis();

    @Value.Default
    public boolean spanCaptureSelfNested() {
        return false;
    }

    @Value.Default
    public String timerName() {
        return "";
    }

    // this is only for instrumentation authors (to be used in instrumentation.*.json)
    @Value.Default
    public String enabledProperty() {
        return "";
    }

    // this is only for instrumentation authors (to be used in instrumentation.*.json)
    @Value.Default
    public String localSpanEnabledProperty() {
        return "";
    }

    @Gson.Ignore
    @Value.Derived
    public boolean isTimerOrGreater() {
        return captureKind() == CaptureKind.TIMER || captureKind() == CaptureKind.LOCAL_SPAN
                || captureKind() == CaptureKind.TRANSACTION;
    }

    @Gson.Ignore
    @Value.Derived
    public boolean isLocalSpanOrGreater() {
        return captureKind() == CaptureKind.LOCAL_SPAN || captureKind() == CaptureKind.TRANSACTION;
    }

    @Gson.Ignore
    @Value.Derived
    public boolean isTransaction() {
        return captureKind() == CaptureKind.TRANSACTION;
    }

    @Gson.Ignore
    @Value.Derived
    public ImmutableList<String> validationErrors() {
        List<String> errors = Lists.newArrayList();
        if (className().isEmpty() && classAnnotation().isEmpty()) {
            errors.add("className and classAnnotation are both empty");
        }
        if (methodName().isEmpty() && methodAnnotation().isEmpty()) {
            errors.add("methodName and methodAnnotation are both empty");
        }
        if (isTimerOrGreater() && timerName().isEmpty()) {
            errors.add("timerName is empty");
        }
        if (captureKind() == CaptureKind.LOCAL_SPAN && spanMessageTemplate().isEmpty()) {
            errors.add("spanMessageTemplate is empty");
        }
        if (isTransaction() && transactionType().isEmpty()) {
            errors.add("transactionType is empty");
        }
        if (isTransaction() && transactionNameTemplate().isEmpty()) {
            errors.add("transactionNameTemplate is empty");
        }
        if (!timerName().matches("[a-zA-Z0-9 ]*")) {
            errors.add("timerName contains invalid characters: " + timerName());
        }
        if (!methodDeclaringClassName().isEmpty()) {
            errors.add("methodDeclaringClassName is no longer supported");
        }
        return ImmutableList.copyOf(errors);
    }

    public void logValidationErrorsIfAny() {
        List<String> errors = validationErrors();
        if (!errors.isEmpty()) {
            logger.error("invalid instrumentation config: {} - {}", Joiner.on(", ").join(errors),
                    this);
        }
    }

    public enum MethodModifier {
        PUBLIC, STATIC, NOT_STATIC;
    }

    public enum CaptureKind {
        TRANSACTION, LOCAL_SPAN, TIMER, OTHER
    }

    public enum AlreadyInTransactionBehavior {
        CAPTURE_LOCAL_SPAN, CAPTURE_NEW_TRANSACTION, DO_NOTHING
    }
}
