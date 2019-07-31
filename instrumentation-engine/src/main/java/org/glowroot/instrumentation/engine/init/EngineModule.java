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
package org.glowroot.instrumentation.engine.init;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.api.internal.InstrumentationServiceHolder;
import org.glowroot.instrumentation.engine.bytecode.api.BytecodeServiceHolder;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.instrumentation.engine.config.AdviceConfig;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptor;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptors;
import org.glowroot.instrumentation.engine.impl.InstrumentationServiceImpl;
import org.glowroot.instrumentation.engine.impl.InstrumentationServiceImpl.ConfigServiceFactory;
import org.glowroot.instrumentation.engine.impl.SimpleConfigServiceFactory;
import org.glowroot.instrumentation.engine.impl.TimerNameCache;
import org.glowroot.instrumentation.engine.init.PreCheckLoadedClasses.PreCheckClassFileTransformer;
import org.glowroot.instrumentation.engine.spi.AgentSPI;
import org.glowroot.instrumentation.engine.util.JavaVersion;
import org.glowroot.instrumentation.engine.util.LazyPlatformMBeanServer;
import org.glowroot.instrumentation.engine.weaving.AdviceCache;
import org.glowroot.instrumentation.engine.weaving.AnalyzedWorld;
import org.glowroot.instrumentation.engine.weaving.BytecodeServiceImpl;
import org.glowroot.instrumentation.engine.weaving.BytecodeServiceImpl.OnEnteringMain;
import org.glowroot.instrumentation.engine.weaving.Java9;
import org.glowroot.instrumentation.engine.weaving.PointcutClassFileTransformer;
import org.glowroot.instrumentation.engine.weaving.PreloadSomeSuperTypesCache;
import org.glowroot.instrumentation.engine.weaving.Weaver;
import org.glowroot.instrumentation.engine.weaving.WeavingClassFileTransformer;

import static com.google.common.base.Preconditions.checkNotNull;

public class EngineModule {

    private static final Logger logger = LoggerFactory.getLogger(EngineModule.class);

    // log startup messages using logger name "org.glowroot.instrumentation"
    private static final Logger startupLogger =
            LoggerFactory.getLogger("org.glowroot.instrumentation");

    private final AdviceCache adviceCache;
    private final PreloadSomeSuperTypesCache preloadSomeSuperTypesCache;
    private final AnalyzedWorld analyzedWorld;
    private final Weaver weaver;
    private final BytecodeServiceImpl bytecodeService;

    private volatile @MonotonicNonNull LazyPlatformMBeanServer lazyPlatformMBeanServer;

    public static EngineModule createWithSomeDefaults(@Nullable Instrumentation instrumentation,
            File tmpDir, ThreadContextThreadLocal threadContextThreadLocal, AgentSPI agentSPI,
            @Nullable File agentJarFile) throws Exception {
        List<InstrumentationDescriptor> instrumentationDescriptors =
                InstrumentationDescriptors.read();
        return createWithSomeDefaults(instrumentation, tmpDir, threadContextThreadLocal,
                instrumentationDescriptors,
                new SimpleConfigServiceFactory(instrumentationDescriptors), agentSPI, true,
                ImmutableList.<String>of(), ImmutableList.<String>of(), agentJarFile);
    }

    public static EngineModule createWithSomeDefaults(@Nullable Instrumentation instrumentation,
            File tmpDir, ThreadContextThreadLocal threadContextThreadLocal,
            List<InstrumentationDescriptor> instrumentationDescriptors,
            ConfigServiceFactory configServiceFactory, AgentSPI agentSPI,
            boolean useInstrumentationAnnotations, List<String> doNotWeavePrefixes,
            List<String> tryToLoadInBootstrapClassLoader, @Nullable File agentJarFile)
            throws Exception {
        return new EngineModule(instrumentation, tmpDir, Ticker.systemTicker(),
                instrumentationDescriptors, Collections.<AdviceConfig>emptyList(),
                threadContextThreadLocal, new TimerNameCache(), configServiceFactory, agentSPI,
                useInstrumentationAnnotations, doNotWeavePrefixes, tryToLoadInBootstrapClassLoader,
                null, new Class<?>[0], agentJarFile);
    }

    public EngineModule(@Nullable Instrumentation instrumentation, File tmpDir, Ticker ticker,
            List<InstrumentationDescriptor> instrumentationDescriptors,
            List<AdviceConfig> reweavableAdviceConfigs,
            ThreadContextThreadLocal threadContextThreadLocal, TimerNameCache timerNameCache,
            ConfigServiceFactory configServiceFactory, AgentSPI agentSPI,
            boolean useInstrumentationAnnotations, List<String> doNotWeavePrefixes,
            List<String> tryToLoadInBootstrapClassLoader,
            @Nullable PreCheckClassFileTransformer preCheckClassFileTransformer,
            Class<?>[] allPreCheckLoadedClasses, @Nullable File agentJarFile) throws Exception {

        try {
            if (instrumentation != null) {
                instrumentation.addTransformer(new ManagementFactoryHackClassFileTransformer());
                // need to load ThreadMXBean before it's possible to start any transactions since
                // starting transactions depends on ThreadMXBean and so can lead to problems
                // (e.g. see FileInstrumentationPresentAtStartupIT)
                ManagementFactory.getThreadMXBean();
                // don't remove transformer in case the class is retransformed later
                if (JavaVersion.isGreaterThanOrEqualToJava9()) {
                    Object baseModule = Java9.getModule(ClassLoader.class);
                    Java9.grantAccessToEngine(instrumentation, baseModule);
                    Java9.grantAccess(instrumentation,
                            "org.glowroot.instrumentation.engine.weaving.ClassLoaders",
                            "java.lang.ClassLoader", false);
                    instrumentation.addTransformer(new Java9HackClassFileTransformer());
                    Class.forName(
                            "org.glowroot.instrumentation.engine.weaving.WeavingClassFileTransformer");
                    // don't remove transformer in case the class is retransformed later
                }
                if (JavaVersion.isJ9Jvm() && JavaVersion.isJava6()) {
                    instrumentation.addTransformer(new IbmJ9Java6HackClassFileTransformer());
                    Class.forName("com.google.protobuf.UnsafeUtil");
                    // don't remove transformer in case the class is retransformed later
                }
            }

            ClassFileTransformer pointcutClassFileTransformer = null;
            if (instrumentation != null) {
                for (InstrumentationDescriptor descriptor : instrumentationDescriptors) {
                    File jarFile = descriptor.jarFile();
                    if (jarFile != null) {
                        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jarFile));
                    }
                }
                pointcutClassFileTransformer = new PointcutClassFileTransformer();
                instrumentation.addTransformer(pointcutClassFileTransformer);
            }
            adviceCache = new AdviceCache(instrumentationDescriptors, reweavableAdviceConfigs,
                    instrumentation, doNotWeavePrefixes, tmpDir);
            if (pointcutClassFileTransformer != null) {
                checkNotNull(instrumentation).removeTransformer(pointcutClassFileTransformer);
            }
            preloadSomeSuperTypesCache = new PreloadSomeSuperTypesCache(
                    new File(tmpDir, "preload-some-super-types-cache"), 50000);
            analyzedWorld = new AnalyzedWorld(adviceCache.getAdvisorsSupplier(),
                    adviceCache.getShimTypes(), adviceCache.getMixinTypes(),
                    useInstrumentationAnnotations, preloadSomeSuperTypesCache);

            weaver = new Weaver(adviceCache.getAdvisorsSupplier(), adviceCache.getShimTypes(),
                    adviceCache.getMixinTypes(), analyzedWorld, ticker);

            // need to initialize instrumentation-api and BytecodeService before enabling
            // instrumentation

            InstrumentationServiceHolder
                    .set(new InstrumentationServiceImpl(timerNameCache, configServiceFactory));

            bytecodeService = new BytecodeServiceImpl(threadContextThreadLocal, agentSPI,
                    tryToLoadInBootstrapClassLoader, preloadSomeSuperTypesCache);
            BytecodeServiceHolder.set(bytecodeService);

            bytecodeService.addOnEnteringMain(new OnEnteringMain() {
                @Override
                public void run(@Nullable String mainClass) throws Exception {
                    weaver.setNoLongerNeedToWeaveMainMethods();
                    lazyPlatformMBeanServer = LazyPlatformMBeanServer.create(mainClass);
                    bytecodeService.setOnExitingGetPlatformMBeanServer(new Runnable() {
                        @Override
                        public void run() {
                            // TODO report checker framework issue that occurs without checkNotNull
                            checkNotNull(lazyPlatformMBeanServer);
                            lazyPlatformMBeanServer.setPlatformMBeanServerAvailable();
                        }
                    });
                }
            });
        } catch (Throwable t) {
            BytecodeServiceHolder.setEngineFailedToStart();
            Throwables.propagateIfPossible(t, Exception.class);
            throw new Exception(t);
        }

        if (instrumentation != null) {
            PreInitializeWeavingClasses.preInitializeClasses();
            List<String> internalNames = Lists.newArrayList();
            for (String doNotWeavePrefix : doNotWeavePrefixes) {
                internalNames.add(doNotWeavePrefix.replace('.', '/'));
            }
            WeavingClassFileTransformer transformer =
                    new WeavingClassFileTransformer(weaver, instrumentation, internalNames);
            boolean retransformClassesSupported = instrumentation.isRetransformClassesSupported();
            if (retransformClassesSupported) {
                instrumentation.addTransformer(transformer, true);
            } else {
                instrumentation.addTransformer(transformer);
            }
            Class<?>[] allLoadedClasses = instrumentation.getAllLoadedClasses();
            adviceCache.initialReweave(allLoadedClasses);
            if (preCheckClassFileTransformer == null) {
                logAnyImportantClassLoadedPriorToWeavingInit(allLoadedClasses, agentJarFile, false);
            } else {
                logPreCheckInfo(allPreCheckLoadedClasses, agentJarFile,
                        preCheckClassFileTransformer);
                instrumentation.removeTransformer(preCheckClassFileTransformer);
            }
            if (retransformClassesSupported) {
                instrumentation.retransformClasses(ClassLoader.class);
            }
            // need to initialize some classes while still single threaded in order to prevent
            // possible deadlock later on
            try {
                Class.forName("sun.net.www.protocol.ftp.Handler");
                Class.forName("sun.net.www.protocol.ftp.FtpURLConnection");
            } catch (ClassNotFoundException e) {
                logger.debug(e.getMessage(), e);
            }
        }

        initInstrumentation(instrumentationDescriptors);
    }

    public void addOnEnteringMain(OnEnteringMain onEnteringMain) {
        bytecodeService.addOnEnteringMain(onEnteringMain);
    }

    public AdviceCache getAdviceCache() {
        return adviceCache;
    }

    public PreloadSomeSuperTypesCache getPreloadSomeSuperTypesCache() {
        return preloadSomeSuperTypesCache;
    }

    public AnalyzedWorld getAnalyzedWorld() {
        return analyzedWorld;
    }

    public Weaver getWeaver() {
        return weaver;
    }

    public LazyPlatformMBeanServer getLazyPlatformMBeanServer() {
        if (lazyPlatformMBeanServer == null) {
            throw new IllegalStateException("onEnteringMain() was never called");
        }
        return lazyPlatformMBeanServer;
    }

    private static void logPreCheckInfo(Class<?>[] allPreCheckLoadedClasses,
            @Nullable File agentJarFile,
            PreCheckClassFileTransformer preCheckClassFileTransformer) {
        if (logAnyImportantClassLoadedPriorToWeavingInit(allPreCheckLoadedClasses,
                agentJarFile, true)) {
            List<String> classNames = Lists.newArrayList();
            for (Class<?> clazz : allPreCheckLoadedClasses) {
                String className = clazz.getName();
                if (!className.startsWith("[")) {
                    classNames.add(className);
                }
            }
            Collections.sort(classNames);
            startupLogger.warn("PRE-CHECK: full list of classes already loaded: {}",
                    Joiner.on(", ").join(classNames));
            for (Map.Entry<String, Exception> entry : preCheckClassFileTransformer
                    .getImportantClassLoadingPoints().entrySet()) {
                startupLogger.warn("PRE-CHECK: loading location of important class: {}",
                        entry.getKey(), entry.getValue());
            }
        } else {
            startupLogger.info("PRE-CHECK: successful");
        }
    }

    private static boolean logAnyImportantClassLoadedPriorToWeavingInit(Class<?>[] allLoadedClasses,
            @Nullable File agentJarFile, boolean preCheck) {
        List<String> loadedImportantClassNames = Lists.newArrayList();
        for (Class<?> loadedClass : allLoadedClasses) {
            String className = loadedClass.getName();
            if (PreCheckLoadedClasses.isImportantClass(className, loadedClass)) {
                loadedImportantClassNames.add(className);
            }
        }
        if (loadedImportantClassNames.isEmpty()) {
            return false;
        } else {
            logLoadedImportantClassWarning(loadedImportantClassNames, agentJarFile, preCheck);
            return true;
        }
    }

    // now init instrumentation to give them a chance to do something in their static initializer
    // e.g. append their package to jboss.modules.system.pkgs
    private static void initInstrumentation(
            List<InstrumentationDescriptor> instrumentationDescriptors) {
        for (InstrumentationDescriptor descriptor : instrumentationDescriptors) {
            for (String clazz : descriptor.classes()) {
                try {
                    Class.forName(clazz, true, EngineModule.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    // this would have already been logged as a warning during advice construction
                    logger.debug(e.getMessage(), e);
                }
            }
        }
    }

    private static void logLoadedImportantClassWarning(List<String> loadedImportantClassNames,
            @Nullable File agentJarFile, boolean preCheck) {
        if (preCheck) {
            // this is only logged with -Dglowroot.debug.preCheckLoadedClasses=true
            startupLogger.warn("PRE-CHECK: one or more important classes were loaded before"
                    + " agent initialization: {}", Joiner.on(", ").join(loadedImportantClassNames));
            return;
        }
        List<String> javaAgentArgsBeforeThisAgent = getJavaAgentArgsBeforeThisAgent(agentJarFile);
        if (!javaAgentArgsBeforeThisAgent.isEmpty()) {
            startupLogger.warn("one or more important classes were loaded before  instrumentation"
                    + " could be applied to them: {}. This likely occurred because one or more"
                    + " other javaagents ({}) are listed in the JVM args prior to this agent which"
                    + " which gives them a higher loading precedence.",
                    Joiner.on(", ").join(loadedImportantClassNames),
                    Joiner.on(" ").join(javaAgentArgsBeforeThisAgent));
            return;
        }
        List<String> nativeAgentArgs = getNativeAgentArgs();
        if (!nativeAgentArgs.isEmpty()) {
            startupLogger.warn("one or more important classes were loaded before instrumentation"
                    + " could be applied to them: {}. This likely occurred because one or more"
                    + " native agents ({}) are listed in the JVM args, and native agents have"
                    + " higher loading precedence than java agents.",
                    Joiner.on(", ").join(loadedImportantClassNames),
                    Joiner.on(" ").join(nativeAgentArgs));
            return;
        }
        startupLogger.warn("one or more important classes were loaded before instrumentation could"
                + " be applied to them: {}", Joiner.on(", ").join(loadedImportantClassNames));
    }

    private static List<String> getNativeAgentArgs() {
        List<String> nativeAgentArgs = Lists.newArrayList();
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (jvmArg.startsWith("-agentpath:") || jvmArg.startsWith("-agentlib:")) {
                nativeAgentArgs.add(jvmArg);
            }
        }
        return nativeAgentArgs;
    }

    private static List<String> getJavaAgentArgsBeforeThisAgent(@Nullable File agentJarFile) {
        if (agentJarFile == null) {
            return ImmutableList.of();
        }
        List<String> javaAgentArgsBeforeThisAgent = Lists.newArrayList();
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (jvmArg.startsWith("-javaagent:") && jvmArg.endsWith(agentJarFile.getName())) {
                break;
            }
            if (jvmArg.startsWith("-javaagent:") || isIbmJ9HealthcenterArg(jvmArg)) {
                javaAgentArgsBeforeThisAgent.add(jvmArg);
            }
        }
        return javaAgentArgsBeforeThisAgent;
    }

    private static boolean isIbmJ9HealthcenterArg(String jvmArg) {
        return JavaVersion.isJ9Jvm()
                && (jvmArg.equals("-Xhealthcenter") || jvmArg.startsWith("-Xhealthcenter:"));
    }
}
