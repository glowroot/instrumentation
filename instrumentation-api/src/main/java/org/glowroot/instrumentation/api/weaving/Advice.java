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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class Advice {

    // pointcuts on constructors (methodName = "<init>") are unusual
    // because you cannot have a try catch handler than covers the call to the super constructor
    // see https://bugs.openjdk.java.net/browse/JDK-8172282
    // because of this, @Advice.IsEnabled is invoked prior to calling the super constructor
    // and @Advice.OnMethodBefore is invoked after calling the super constructor
    // NOTE @Advice.IsEnabled could be invoked after calling the super constructor, but this causes
    // stack frame
    // headaches if some other bytecode manipulation added local variable prior to the super
    // constructor
    // call (which is not possible via straight Java code, but is legal in bytecode,
    // see WeaverTest.shouldExecuteAdviceOnHackedConstructorBytecode())

    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface Pointcut {

        /**
         * Restrict the pointcut to methods that are declared in a classes (or interfaces) matching
         * the given name.
         * 
         * | and * can be used for limited regular expressions. Full regular expressions can be used
         * by starting and ending className with /
         */
        String className() default "";

        /**
         * Optionally (in addition to className or instead of className) restrict the pointcut to
         * methods that are declared in a classes (or interfaces) matching the given annotation.
         * 
         * | and * can be used for limited regular expressions. Full regular expressions can be used
         * by starting and ending classAnnotation with /
         */
        String classAnnotation() default "";

        /**
         * E.g. pointcut on className="java.util.concurrent.Future", methodName="get",
         * methodParameterTypes={}, but only for classes with
         * subTypeRestriction="com.ning.http.client.ListenableFuture"
         * 
         * | and * can be used for limited regular expressions. Full regular expressions can be used
         * by starting and ending subTypeRestriction with /
         */
        String subTypeRestriction() default "";

        /**
         * E.g. pointcut on className="com.yourcompany.*", methodName="run",
         * methodParameterTypes={}, but only for classes with
         * superTypeRestriction="java.lang.Runnable"
         * 
         * | and * can be used for limited regular expressions. Full regular expressions can be used
         * by starting and ending superTypeRestriction with /
         */
        String superTypeRestriction() default "";

        /**
         * | and * can be used for limited regular expressions. Full regular expressions can be used
         * by starting and ending methodName with /.
         */
        // use "<init>" to weave constructors
        // patterns never match constructors
        // static initializers ("<clinit>") are not supported
        String methodName() default "";

        // optionally (in addition to methodName or instead of methodName) restrict pointcut to
        // methods
        // with the given annotation
        String methodAnnotation() default "";

        // methodParameterTypes has no default since it's not obvious if default should be {} or
        // {".."}
        String[] methodParameterTypes();

        String methodReturnType() default "";

        MethodModifier[] methodModifiers() default {};

        String nestingGroup() default "";

        // order is used to order two pointcuts on the same method
        // it is used to nest pointcut inside another, e.g. creating a pointcut on
        // HttpServlet.service()
        // to override transaction type, in which case the pointcut's @Advice.OnMethodBefore needs
        // to
        // occur after the servlet instrumentation's @Advice.OnMethodBefore which starts the
        // transaction
        //
        // orders can be negative if an ordering before the default 0 is needed
        //
        // given a pointcut A with order 0 and a pointcut B with order 10:
        // * A's @Advice.OnMethodBefore will be called before B's @Advice.OnMethodBefore
        // * A's @Advice.OnMethodReturn will be called after B's @Advice.OnMethodReturn
        // * A's @Advice.OnMethodThrow will be called after B's @Advice.OnMethodThrow
        // * A's @Advice.OnMethodAfter will be called after B's @Advice.OnMethodAfter
        int order() default 0;

        // advanced feature:

        String suppressibleUsingKey() default "";

        // will suppress nested even if pointcut is not enabled, and even if suppressed pointcut is
        // on
        // same method but with lower order
        String suppressionKey() default "";
    }

    /**
     * Used for restricting a {@link Pointcut} to methods with or without particular modifiers.
     * 
     * <p>
     * {@link #PUBLIC} and {@link #NOT_STATIC} are useful for creating a pointcut that applies to
     * all public instance methods in a class.
     * 
     * <p>
     * {@link #STATIC} is useful for creating a pointcut that applies to
     * {@code public static void main(String[] args)} methods.
     * 
     * <p>
     * Additional modifiers can easily be supported if additional use cases arise.
     * 
     * @see Pointcut#methodModifiers()
     */
    public enum MethodModifier {

        PUBLIC, STATIC, NOT_STATIC;
    }

    /**
     * Annotates a method in a {@literal @}{@link Pointcut} class that should be run just before
     * each method execution picked out by the {@link Pointcut}. Only one method in a
     * 
     * {@literal @}{@link Pointcut} class may be annotated with {@literal @}{@code IsEnabled}.
     * <p>
     * An {@literal @}{@code IsEnabled} method can accept parameters annotated with any of the
     * following: {@link Bind.This}, {@link Bind.Argument}, {@link Bind.AllArguments} or
     * {@link Bind.MethodName}.
     * <p>
     * An {@literal @}{@code IsEnabled} method must return a {@code boolean}. If it returns
     * {@code false} then none of the {@literal @}{@link OnMethodBefore},
     * {@literal @}{@link OnMethodReturn}, {@literal @}{@link OnMethodThrow} or
     * {@literal @}{@link OnMethodAfter} methods are called for that execution of the method picked
     * out by the {@link Pointcut}.
     */
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface IsEnabled {}

    /**
     * Annotates a method in a {@literal @}{@link Pointcut} class that should be run just before
     * each method execution picked out by the {@link Pointcut}. Only one method in a
     * 
     * {@literal @}{@code Pointcut} class may be annotated with {@literal @}{@code OnMethodBefore}.
     * <p>
     * An {@literal @}{@code OnMethodBefore} method can accept parameters annotated with any of the
     * following: {@literal @}{@link Bind.This}, {@literal @}{@link Bind.Argument},
     * 
     * {@literal @}{@link Bind.AllArguments} or {@literal @}{@link Bind.MethodName}.
     * <p>
     * An {@literal @}{@code OnMethodBefore} method may return {@code void} or a non-{@code void}
     * type. If it returns a non-{@code void} type, the value returned by the
     * {@literal @}{@code OnMethodBefore} method is called the <em>traveler</em>, and is available
     * as input to subsequent
     * 
     * {@literal @}{@link OnMethodReturn}, {@literal @}{@link OnMethodThrow} and
     * {@literal @}{@link OnMethodAfter} methods by annotating a parameter on any of these methods
     * with {@literal @}{@link Bind.Enter}.
     */
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface OnMethodBefore {}

    /**
     * Annotates a method in a {@literal @}{@link Pointcut} class that should be run just after each
     * method (or constructor) execution picked out by the {@link Pointcut}, but only if the method
     * picked out by the {@link Pointcut} returns successfully (does not throw an
     * {@code Exception}). Only one method in a {@literal @}{@code Pointcut} class may be annotated
     * with
     * 
     * {@literal @}{@code OnMethodReturn}.
     * <p>
     * An {@literal @}{@code OnMethodReturn} method can accept parameters annotated with any of the
     * following: {@literal @}{@link Bind.This}, {@literal @}{@link Bind.Argument},
     * 
     * {@literal @}{@link Bind.AllArguments}, {@literal @}{@link Bind.MethodName},
     * 
     * {@literal @}{@link Bind.Enter} or {@literal @}{@link Bind.Return}.
     * 
     * {@literal @}{@link Bind.Enter} can only be used if there is a corresponding
     * 
     * {@literal @}{@link OnMethodBefore} method that returns a non-{@code void} type (the
     * <em>traveler</em>). {@literal @}{@link Bind.Return} can only be used if each method picked
     * out by the {@link Pointcut} returns a non-{@code void} type. If
     * {@literal @}{@link Bind.Return} is used, it must be the first parameter to the
     * {@literal @}{@code OnMethodReturn} method.
     * <p>
     * An {@literal @}{@code OnMethodReturn} method may return {@code void} or a non-{@code void}
     * type. If it returns a non-{@code void} type, the value returned by the
     * {@literal @}{@code OnMethodReturn} method is returned from the method execution picked out by
     * the {@link Pointcut} instead of that method's original return value. This can be used to wrap
     * the original return value by passing the original return value in to an
     * {@literal @}{@code OnMethodReturn} method (using
     * 
     * {@literal @}{@link Bind.Return}) and then returning the wrapped value.
     */
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface OnMethodReturn {}

    /**
     * Annotates a method in a {@literal @}{@link Pointcut} class that should be run just after each
     * method (or constructor) execution picked out by the {@link Pointcut}, but only if the method
     * picked out by the {@link Pointcut} throws an {@code Exception}. Only one method in a
     * 
     * {@literal @}{@code Pointcut} class may be annotated with {@code OnMethodThrow}.
     * <p>
     * An {@literal @}{@code OnMethodThrow} method can accept parameters annotated with any of the
     * following: {@literal @}{@link Bind.This}, {@literal @}{@link Bind.Argument},
     * 
     * {@literal @}{@link Bind.AllArguments}, {@literal @}{@link Bind.MethodName},
     * 
     * {@literal @}{@link Bind.Enter} or {@literal @}{@link Bind.Thrown}.
     * 
     * {@literal @}{@link Bind.Enter} can only be used if there is a corresponding
     * 
     * {@literal @}{@link OnMethodBefore} method that returns a non-{@code void} type (the
     * <em>traveler</em>). If {@literal @}{@link Bind.Thrown} is used, it must be the first
     * parameter to the
     * 
     * {@literal @}{@code OnMethodThrow} method.
     * <p>
     * An {@literal @}{@code OnMethodThrow} method must return {@code void}. It is not able to
     * suppress the original {@code Exception} or change the {@code Exception} that is thrown (at
     * least not currently).
     */
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface OnMethodThrow {}

    /**
     * Annotates a method in a {@literal @}{@link Pointcut} class that should be run just after each
     * method (or constructor) execution picked out by the {@link Pointcut}, whether the method
     * picked out by the {@link Pointcut} returns successfully or throws an {@code Exception}. Only
     * one method in a {@literal @}{@code Pointcut} class may be annotated with
     * {@literal @}{@code OnMethodAfter}.
     * <p>
     * An {@literal @}{@link OnMethodAfter} method is run after the
     * {@literal @}{@link OnMethodReturn} and {@literal @}{@link OnMethodThrow} methods, if either
     * of those are present.
     * <p>
     * An {@literal @}{@code OnMethodAfter} method can accept parameters annotated with any of the
     * following: {@literal @}{@link Bind.This}, {@literal @}{@link Bind.Argument},
     * 
     * {@literal @}{@link Bind.AllArguments}, {@literal @}{@link Bind.MethodName} or
     * 
     * {@literal @}{@link Bind.Enter}. {@literal @}{@link Bind.Enter} can only be used if there is a
     * corresponding {@literal @}{@link OnMethodBefore} method that returns a non-{@code void} type
     * (the <em>traveler</em>).
     * <p>
     * An {@literal @}{@code OnMethodAfter} method must return {@code void}.
     */
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface OnMethodAfter {}
}
