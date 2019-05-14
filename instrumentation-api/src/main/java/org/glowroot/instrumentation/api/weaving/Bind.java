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
package org.glowroot.instrumentation.api.weaving;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.glowroot.instrumentation.api.weaving.Advice.IsEnabled;
import org.glowroot.instrumentation.api.weaving.Advice.Pointcut;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

// the naming of the annotations in this class is based on ByteBuddy's annotations names
public class Bind {

    /**
     * Annotates a parameter to an {@literal @}{@link IsEnabled},
     * {@literal @}{@link Advice.OnMethodBefore}, {@literal @}{@link Advice.OnMethodReturn},
     * {@literal @}{@link Advice.OnMethodThrow} or {@literal @}{@link Advice.OnMethodAfter} method
     * in a {@literal @}{@link Pointcut} class.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface ClassMeta {}

    /**
     * Annotates a parameter to an {@literal @}{@link IsEnabled},
     * {@literal @}{@link Advice.OnMethodBefore}, {@literal @}{@link Advice.OnMethodReturn},
     * {@literal @}{@link Advice.OnMethodThrow} or {@literal @}{@link Advice.OnMethodAfter} method
     * in a {@literal @}{@link Pointcut} class.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface MethodMeta {}

    /**
     * Annotates a parameter to any {@literal @}{@link Pointcut} advice method
     * 
     * ({@literal @}{@link IsEnabled}, {@literal @}{@link Advice.OnMethodBefore},
     * {@literal @}{@link Advice.OnMethodReturn}, {@literal @}{@link Advice.OnMethodThrow},
     * {@literal @}{@link Advice.OnMethodAfter}).
     * <p>
     * When the advice method is called, the name of the method matched by the
     * 
     * {@literal @}{@link Pointcut} is bound to this parameter. This is useful when the
     * {@link Pointcut#methodName()} uses wildcards or is a regular expression.
     * <p>
     * Parameters annotated with {@literal @}{@link MethodName} must be of type {@link String}.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface MethodName {}

    /**
     * Annotates a parameter to an {@literal @}{@link Advice.OnMethodReturn} method in a
     * {@literal @}{@link Pointcut} class.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface OptionalReturn {}

    /**
     * Annotates parameters to any {@literal @}{@link Pointcut} advice method
     * 
     * ({@literal @}{@link IsEnabled}, {@literal @}{@link Advice.OnMethodBefore},
     * {@literal @}{@link Advice.OnMethodReturn}, {@literal @}{@link Advice.OnMethodThrow},
     * {@literal @}{@link Advice.OnMethodAfter}).
     * <p>
     * When the advice method is called, the parameters passed to the method matched by the
     * {@link Pointcut} are bound to these parameters.
     * <p>
     * There cannot be more {@literal @}{@link Argument} parameters than the number of arguments
     * passed to the {@link Pointcut} method. If {@code ".."} is used at the end of
     * {@link Pointcut#methodParameterTypes()} and the number of arguments is unknown, then
     * {@link Bind.AllArguments} can be used to bind all arguments to a single parameter of type
     * {@code Object[]}.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface Argument {
        int value();
    }

    /**
     * Annotates a parameter to any {@literal @}{@link Pointcut} advice method
     * 
     * ({@literal @}{@link IsEnabled}, {@literal @}{@link Advice.OnMethodBefore},
     * {@literal @}{@link Advice.OnMethodReturn}, {@literal @}{@link Advice.OnMethodThrow},
     * {@literal @}{@link Advice.OnMethodAfter}).
     * <p>
     * When the advice method is called, all of the parameters passed to the method matched by the
     * {@link Pointcut} are wrapped into a single {@link Object}[] and bound to this parameter.
     * <p>
     * Parameters annotated with {@literal @}{@link MethodName} must be of type {@link Object}[].
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface AllArguments {}

    /**
     * Annotates a parameter to an {@literal @}{@link IsEnabled},
     * {@literal @}{@link Advice.OnMethodBefore}, {@literal @}{@link Advice.OnMethodReturn},
     * {@literal @}{@link Advice.OnMethodThrow} or {@literal @}{@link Advice.OnMethodAfter} method
     * in a {@literal @}{@link Pointcut} class.
     */
    // for non-static methods, binds "this"
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface This {}

    /**
     * Annotates a parameter to an {@literal @}{@link Advice.OnMethodReturn} method in a
     * {@literal @}{@link Pointcut} class.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface Return {}

    /**
     * Annotates a parameter to an {@literal @}{@link Advice.OnMethodThrow} method in a
     * {@literal @}{@link Pointcut} class.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface Thrown {}

    /**
     * Annotates a parameter to an {@literal @}{@link Advice.OnMethodReturn},
     * {@literal @}{@link Advice.OnMethodThrow} or {@literal @}{@link Advice.OnMethodAfter} method
     * in a {@literal @}{@link Pointcut} class.
     */
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface Enter {}
}
