/*
 * Copyright 2019 the original author or authors.
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
package org.glowroot.instrumentation.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.weaving.Advice.MethodModifier;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
public @interface Descriptor {

    String id();

    String name();

    Property[] properties() default {};

    Advice[] advice() default {};

    Class<?>[] classes() default {};

    boolean collocate() default false;

    @Target(ANNOTATION_TYPE)
    @interface Property {

        String name();

        PropertyType type();

        DefaultValue[] defaultValue() default {}; // zero or one elements allowed in this array

        String label();

        String checkboxLabel() default ""; // for boolean properties

        String description() default "";
    }

    @Target(ANNOTATION_TYPE)
    @interface Advice {

        String className() default "";

        String classAnnotation() default "";

        String subTypeRestriction() default "";

        String superTypeRestriction() default "";

        String methodName() default "";

        String methodAnnotation() default "";

        // empty methodParameterTypes means match no-arg methods only
        String[] methodParameterTypes();

        String methodReturnType() default "";

        MethodModifier[] methodModifiers() default {};

        String nestingGroup() default "";

        int order() default 0;

        CaptureKind captureKind();

        String transactionType() default "";

        String transactionNameTemplate() default "";

        String transactionUserTemplate() default "";

        int transactionSlowThresholdMillis() default -1;

        // only applicable for capture kind "transaction"
        AlreadyInTransactionBehavior alreadyInTransactionBehavior() default AlreadyInTransactionBehavior.CAPTURE_LOCAL_SPAN;

        String spanMessageTemplate() default "";

        int spanStackThresholdMillis() default -1;

        boolean spanCaptureSelfNested() default false;

        String timerName() default "";

        String enabledProperty() default "";

        String localSpanEnabledProperty() default "";
    }

    @Target(ANNOTATION_TYPE)
    @interface DefaultValue {

        String stringValue() default "";

        boolean booleanValue() default false;

        boolean nonNullDoubleValue() default false;

        double doubleValue() default 0;

        String[] listValue() default {};
    }

    enum PropertyType {
        STRING, BOOLEAN, DOUBLE, LIST
    }

    enum CaptureKind {
        TRANSACTION, LOCAL_SPAN, TIMER, OTHER
    }
}
