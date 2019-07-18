/**
 * Copyright 2016-2018 the original author or authors.
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
package org.glowroot.instrumentation.test.matrix;

import java.io.IOException;

import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA6;
import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA7;
import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA8;

public class Logback {

    private static final String MODULE_PATH = "instrumentation/logback";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        runOld("0.9", "1.2");
        run("0.9.26", "1.6.1");
    }

    static void runAll() throws Exception {
        runOld("0.9", "1.2");
        for (int i = 1; i <= 5; i++) {
            runOld("0.9." + i, "1.3.0");
        }
        runOld("0.9.6", "1.4.0");
        runOld("0.9.7", "1.4.0");
        runOld("0.9.8", "1.4.3");
        runOld("0.9.9", "1.5.0");
        // logback 0.9.10 doesn't work period in multi-threaded environments due to
        // https://github.com/qos-ch/logback/commit/d699a4afd4cad728ab2aa57b04ef357e15d8c8cf
        runOld("0.9.11", "1.5.5");
        for (int i = 12; i <= 15; i++) {
            runOld("0.9." + i, "1.5.6");
        }
        runOld("0.9.16", "1.5.8");
        runOld("0.9.17", "1.5.8");
        runOld("0.9.18", "1.5.10");
        runOld("0.9.19", "1.5.11");
        runOld("0.9.20", "1.5.11");
        for (int i = 21; i <= 25; i++) {
            run("0.9." + i, "1.6.0");
        }

        for (int i = 26; i <= 29; i++) {
            run("0.9." + i, "1.6.1");
        }

        run("0.9.30", "1.6.2");
        for (int i = 0; i <= 13; i++) {
            run("1.0." + i, "1.7.19");
        }
        for (int i = 0; i <= 8; i++) {
            run("1.1." + i, "1.7.21");
        }
        for (int i = 9; i <= 10; i++) {
            run("1.1." + i, "1.7.22");
        }
        for (int i = 0; i <= 1; i++) {
            run("1.2." + i, "1.7.22");
        }
        for (int i = 2; i <= 3; i++) {
            run("1.2." + i, "1.7.25");
        }
    }

    private static void updateLibVersion(String property, String version) throws IOException {
        Util.updateLibVersion(MODULE_PATH, property, version);
    }

    private static void runOld(String logbackVersion, String slf4jVersion) throws Exception {
        updateLibVersion("logback.version", logbackVersion);
        updateLibVersion("slf4j.version", slf4jVersion);
        Util.runTests(MODULE_PATH, "logback-old", JAVA8, JAVA7, JAVA6);
    }

    private static void run(String logbackVersion, String slf4jVersion) throws Exception {
        updateLibVersion("logback.version", logbackVersion);
        updateLibVersion("slf4j.version", slf4jVersion);
        Util.runTests(MODULE_PATH, JAVA8, JAVA7, JAVA6);
    }
}
