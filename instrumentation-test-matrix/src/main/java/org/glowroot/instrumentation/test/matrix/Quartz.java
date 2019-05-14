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

import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA6;
import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA7;
import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA8;

public class Quartz {

    private static final String MODULE_PATH = "instrumentation/quartz";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].contentEquals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        runOld("1.7.2");
        run("2.0.0");
    }

    static void runAll() throws Exception {
        runOld("1.7.2");
        runOld("1.7.3");
        for (int i = 0; i <= 6; i++) {
            runOld("1.8." + i);
        }
        run("2.0.0");
        run("2.0.1");
        run("2.0.2");
        for (int i = 0; i <= 7; i++) {
            run("2.1." + i);
        }
        run("2.2.0");
        run("2.2.1");
        run("2.2.2");
        run("2.2.3");
        runJava7("2.3.0");
    }

    private static void runOld(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "quartz.version", version);
        Util.runTests(MODULE_PATH, "quartz-old", JAVA8, JAVA7, JAVA6);
    }

    private static void run(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "quartz.version", version);
        Util.runTests(MODULE_PATH, JAVA8, JAVA7, JAVA6);
    }

    private static void runJava7(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "quartz.version", version);
        Util.runTests(MODULE_PATH, JAVA8, JAVA7);
    }
}
