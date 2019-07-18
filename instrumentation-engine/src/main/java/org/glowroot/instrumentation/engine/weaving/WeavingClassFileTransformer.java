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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeavingClassFileTransformer implements ClassFileTransformer {

    private static final Logger logger = LoggerFactory.getLogger(WeavingClassFileTransformer.class);

    private final Weaver weaver;
    private final Instrumentation instrumentation;
    private final List<String> doNotWeavePrefixes;

    private final boolean weaveBootstrapClassLoader;

    // not using the much more convenient (and concurrent) guava CacheBuilder since it uses many
    // additional classes that must then be pre-initialized since this is called from inside
    // ClassFileTransformer.transform() (see PreInitializeClasses)
    private final Set<Object> redefinedModules = Collections
            .newSetFromMap(Collections.synchronizedMap(new WeakHashMap<Object, Boolean>()));

    // because of the crazy pre-initialization of javaagent classes (see
    // org.glowroot.instrumentation.engine.init.PreInitializeWeavingClasses), all inputs into this
    // class should be concrete, non-subclassed types so that the correct set of used classes can be
    // computed (see calculation in the test class
    // org.glowroot.instrumentation.engine.init.GlobalCollector, and hard-coded results in
    // org.glowroot.instrumentation.engine.init.PreInitializeWeavingClassesTest)
    // note: an exception is made for WeavingTimerService, see PreInitializeWeavingClassesTest for
    // explanation
    public WeavingClassFileTransformer(Weaver weaver, Instrumentation instrumentation,
            List<String> doNotWeavePrefixes) {
        this.weaver = weaver;
        this.instrumentation = instrumentation;
        this.doNotWeavePrefixes = doNotWeavePrefixes;
        // can only weave classes in bootstrap class loader if the engine is in bootstrap class
        // loader, otherwise woven bootstrap classes will generate NoClassDefFoundError since the
        // woven code will not be able to see engine classes (e.g. woven code will not be able to
        // see org.glowroot.instrumentation.api.Agent)
        weaveBootstrapClassLoader = isInBootstrapClassLoader();
    }

    // this method is called by the Java 9 transform method that passes in a Module
    // see Java9HackClassFileTransformer
    public byte /*@Nullable*/ [] transformJava9(Object module, @Nullable ClassLoader loader,
            @Nullable String className, @Nullable Class<?> classBeingRedefined,
            @Nullable ProtectionDomain protectionDomain, byte[] bytes) {
        if (redefinedModules.add(module)) {
            try {
                Java9.grantAccessToEngine(instrumentation, module);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return transform(loader, className, classBeingRedefined, protectionDomain, bytes);
    }

    // From the javadoc on ClassFileTransformer.transform():
    // "throwing an exception has the same effect as returning null"
    //
    // so all exceptions must be caught and logged here or they will be lost
    @Override
    public byte /*@Nullable*/ [] transform(@Nullable ClassLoader loader, @Nullable String className,
            @Nullable Class<?> classBeingRedefined, @Nullable ProtectionDomain protectionDomain,
            byte[] bytes) {
        // internal subclasses of MethodHandle are passed in with null className
        // (see integration test MethodHandleWeavingTest for more detail)
        // also, more importantly, Java 8 lambdas are passed in with null className, which need to
        // be woven by executor instrumentation
        String nonNullClassName = className == null ? "unnamed" : className;
        try {
            return transformInternal(loader, nonNullClassName, classBeingRedefined,
                    protectionDomain, bytes);
        } catch (Throwable t) {
            // see method-level comment
            logger.error("error weaving {}: {}", nonNullClassName, t.getMessage(), t);
            return null;
        }
    }

    private byte /*@Nullable*/ [] transformInternal(@Nullable ClassLoader loader, String className,
            @Nullable Class<?> classBeingRedefined, @Nullable ProtectionDomain protectionDomain,
            byte[] bytes) {
        if (ignoreClass(className, loader)) {
            return null;
        }
        if (loader == null && !weaveBootstrapClassLoader) {
            // can only weave classes in bootstrap class loader if the engine is in bootstrap class
            // loader, otherwise woven bootstrap classes will generate NoClassDefFoundError since
            // the woven code will not be able to see engine classes (e.g. woven code will not be
            // able to see org.glowroot.instrumentation.api.Agent)
            return null;
        }
        CodeSource codeSource = protectionDomain == null ? null : protectionDomain.getCodeSource();
        return weaver.weave(bytes, className, classBeingRedefined, codeSource, loader);
    }

    private boolean ignoreClass(String className, @Nullable ClassLoader loader) {
        if (isEngineClass(className)) {
            // don't weave engine classes, including shaded classes
            return true;
        }
        for (String doNotWeavePrefix : doNotWeavePrefixes) {
            if (className.startsWith(doNotWeavePrefix)) {
                return true;
            }
        }
        if (className.startsWith("sun/reflect/Generated")) {
            // optimization, no need to try to weave the many classes generated for reflection:
            // sun/reflect/GeneratedSerializationConstructorAccessor..
            // sun/reflect/GeneratedConstructorAccessor..
            // sun/reflect/GeneratedMethodAccessor..
            return true;
        }
        if (className.equals("load/C4") && loader != null && loader.getClass().getName()
                .equals("oracle.classloader.util.ClassLoadEnvironment$DependencyLoader")) {
            // special case to avoid weaving error when running OC4J
            return true;
        }
        return false;
    }

    private static boolean isEngineClass(String className) {
        return className.startsWith("org/glowroot/instrumentation/engine/")
                || className.startsWith("org/glowroot/instrumentation/api/");
    }

    private static boolean isInBootstrapClassLoader() {
        return WeavingClassFileTransformer.class.getClassLoader() == null;
    }
}
