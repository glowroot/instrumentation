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
package org.glowroot.instrumentation.test.harness.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.engine.config.AdviceConfig;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptor;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptors;
import org.glowroot.instrumentation.engine.impl.TimerNameCache;
import org.glowroot.instrumentation.engine.init.EngineModule;
import org.glowroot.instrumentation.engine.init.MainEntryPointUtil;
import org.glowroot.instrumentation.engine.init.PreCheckLoadedClasses.PreCheckClassFileTransformer;
import org.glowroot.instrumentation.engine.weaving.IsolatedWeavingClassLoader;

import static com.google.common.base.Preconditions.checkNotNull;

public class MainEntryPoint {

    private static @MonotonicNonNull MutableConfigServiceFactory configServiceFactory;

    private MainEntryPoint() {}

    public static void premain(Instrumentation instrumentation, File tmpDir) throws Exception {
        // DO NOT USE ANY GUAVA CLASSES before initLogging() because they trigger loading of jul
        // (and thus, under shading, org.glowroot.instrumentation.engine.jul.Logger and thus
        // org.slf4j)
        MainEntryPointUtil.initLogging("org.glowroot.instrumentation.test.harness",
                instrumentation);
        int collectorPort = checkNotNull(Integer.getInteger("test.harness.collectorPort"));
        start(instrumentation, tmpDir, collectorPort);
    }

    public static void start(@Nullable Instrumentation instrumentation, File tmpDir,
            int collectorPort) throws Exception {

        AgentImpl agent = new AgentImpl();

        List<InstrumentationDescriptor> instrumentationDescriptors =
                InstrumentationDescriptors.read();
        configServiceFactory = new MutableConfigServiceFactory(instrumentationDescriptors);

        EngineModule engineModule = new EngineModule(instrumentation, tmpDir, Ticker.systemTicker(),
                instrumentationDescriptors, Collections.<AdviceConfig>emptyList(),
                Global.getThreadContextThreadLocal(), new TimerNameCache(), configServiceFactory,
                agent, ImmutableList.<String>of(), ImmutableList.<String>of(),
                new PreCheckClassFileTransformer(), new Class<?>[0], null);

        if (instrumentation == null) {
            // running in LocalContainer
            IsolatedWeavingClassLoader isolatedWeavingClassLoader =
                    (IsolatedWeavingClassLoader) Thread.currentThread().getContextClassLoader();
            checkNotNull(isolatedWeavingClassLoader);
            isolatedWeavingClassLoader.setWeaver(engineModule.getWeaver());
        }

        Global.setTraceReporter(new TraceReporter(collectorPort));
    }

    public static void resetInstrumentationProperties() {
        checkNotNull(configServiceFactory).resetConfig();
    }

    public static void setInstrumentationProperty(String instrumentationId, String propertyName,
            boolean propertyValue) {
        checkNotNull(configServiceFactory).setInstrumentationProperty(instrumentationId,
                propertyName, propertyValue);
    }

    public static void setInstrumentationProperty(String instrumentationId, String propertyName,
            @Nullable Double propertyValue) {
        checkNotNull(configServiceFactory).setInstrumentationProperty(instrumentationId,
                propertyName, propertyValue);
    }

    public static void setInstrumentationProperty(String instrumentationId, String propertyName,
            String propertyValue) {
        checkNotNull(configServiceFactory).setInstrumentationProperty(instrumentationId,
                propertyName, propertyValue);
    }

    public static void setInstrumentationProperty(String instrumentationId, String propertyName,
            List<String> propertyValue) {
        checkNotNull(configServiceFactory).setInstrumentationProperty(instrumentationId,
                propertyName, propertyValue);
    }
}
