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
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.util.jar.JarFile;

// this class should have minimal dependencies since it will live in the system class loader while
// the rest of glowroot will live in the bootstrap class loader
public class Premain {

    private Premain() {}

    public static void premain(@SuppressWarnings("unused") String agentArgs,
            Instrumentation instrumentation) throws Exception {
        String tmpDirPath = System.getProperty("test.harness.tmpDir");
        if (tmpDirPath == null) {
            throw new IllegalStateException("Missing test.harness.tmpDir");
        }
        File agentJarFile = getAgentJarFile();
        if (agentJarFile != null) {
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(agentJarFile));
        }
        Class<?> mainEntryPointClass =
                Class.forName("org.glowroot.instrumentation.test.harness.agent.MainEntryPoint",
                        true,
                        Premain.class.getClassLoader());
        Method premainMethod =
                mainEntryPointClass.getMethod("premain", Instrumentation.class, File.class);
        premainMethod.invoke(null, instrumentation, new File(tmpDirPath));
    }

    // suppress warnings is used instead of annotating this method with @Nullable
    // just to avoid dependencies on other classes (in this case the @Nullable annotation)
    @SuppressWarnings("return.type.incompatible")
    static File getAgentJarFile() throws Exception {
        CodeSource codeSource = Premain.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        File codeSourceFile = new File(codeSource.getLocation().toURI());
        if (codeSourceFile.getName().endsWith(".jar")) {
            return codeSourceFile;
        }
        return null;
    }
}
