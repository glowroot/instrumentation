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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.engine.init.PreCheckLoadedClasses.PreCheckClassFileTransformer;
import org.glowroot.instrumentation.engine.util.JavaVersion;

public class MainEntryPointUtil {

    private MainEntryPointUtil() {}

    public static Logger initLogging(String name, @Nullable Instrumentation instrumentation) {
        if (JavaVersion.isJava6() && "IBM J9 VM".equals(System.getProperty("java.vm.name"))
                && instrumentation != null) {
            instrumentation.addTransformer(new IbmJ9Java6HackClassFileTransformer2());
            // don't remove transformer in case the class is retransformed later
        }

        ClassLoader priorLoader = Thread.currentThread().getContextClassLoader();
        // setting the context class loader to only load from bootstrap class loader (by specifying
        // null parent class loader), otherwise logback will pick up and use a SAX parser on the
        // system classpath because SAXParserFactory.newInstance() checks the thread context class
        // loader for resource named META-INF/services/javax.xml.parsers.SAXParserFactory
        Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
            // overriding getResourceAsStream() is needed for JDK 6 since it still manages to
            // fallback and find the resource on the system class path otherwise
            @Override
            public @Nullable InputStream getResourceAsStream(String name) {
                if (name.equals("META-INF/services/javax.xml.parsers.SAXParserFactory")) {
                    return new ByteArrayInputStream(new byte[0]);
                }
                return null;
            }
        });
        try {
            Logger logger = LoggerFactory.getLogger(name);
            PreCheckClassFileTransformer.initLogger();
            return logger;
        } finally {
            Thread.currentThread().setContextClassLoader(priorLoader);
        }
    }
}
