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

public class Log4j {

    private static final String MODULE_PATH = "instrumentation/log4j";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run1x("1.2.4");
        run2x("2.0");
        run2xJava7("2.4");
    }

    static void runAll() throws Exception {
        for (int i = 4; i <= 17; i++) {
            if (i == 10) {
                // there is no 1.2.10 in maven central
                continue;
            }
            run1x("1.2." + i);
        }
        run2x("2.0");
        run2x("2.0.1");
        run2x("2.0.2");
        run2x("2.1");
        run2x("2.2");
        run2x("2.3");
        run2xJava7("2.4");
        run2xJava7("2.4.1");
        run2xJava7("2.5");
        // tests fail with log4j 2.6 due to https://github.com/apache/logging-log4j2/pull/31
        run2xJava7("2.6.1");
        run2xJava7("2.6.2");
        run2xJava7("2.7");
        run2xJava7("2.8");
        run2xJava7("2.8.1");
        run2xJava7("2.8.2");
        run2xJava7("2.9.0");
        run2xJava7("2.9.1");
        run2xJava7("2.10.0");
        run2xJava7("2.11.0");
        run2xJava7("2.11.1");
    }

    private static void run1x(String version) throws Exception {
        updateLibVersion("log4j1x.version", version);
        Util.runTest(MODULE_PATH, "Log4j1xIT", JAVA8, JAVA7, JAVA6);
    }

    private static void run2x(String version) throws Exception {
        updateLibVersion("log4j2x.version", version);
        Util.runTest(MODULE_PATH, "Log4j2xIT,Log4j2xMarkerIT", JAVA8, JAVA7, JAVA6);
    }

    private static void run2xJava7(String version) throws Exception {
        updateLibVersion("log4j2x.version", version);
        Util.runTest(MODULE_PATH, "Log4j2xIT,Log4j2xMarkerIT", JAVA8, JAVA7);
    }

    private static void updateLibVersion(String property, String version) throws IOException {
        Util.updateLibVersion(MODULE_PATH, property, version);
    }
}
