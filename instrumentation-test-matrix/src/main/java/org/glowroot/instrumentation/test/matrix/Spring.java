/**
 * Copyright 2016-2019 the original author or authors.
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

public class Spring {

    private static final String MODULE_PATH = "instrumentation/spring";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].contentEquals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run("3.0.0.RELEASE");
        run("4.0.0.RELEASE");
        runJava8("5.0.0.RELEASE", "0.7.0.RELEASE");
    }

    static void runAll() throws Exception {
        for (int i = 0; i <= 7; i++) {
            run("3.0." + i + ".RELEASE");
        }
        for (int i = 0; i <= 4; i++) {
            run("3.1." + i + ".RELEASE");
        }
        for (int i = 0; i <= 8; i++) {
            // 3.2.0 through 3.2.8 fail under Java 8 due to https://jira.spring.io/browse/SPR-11719
            runNotJava8("3.2." + i + ".RELEASE");
        }
        for (int i = 9; i <= 18; i++) {
            run("3.2." + i + ".RELEASE");
        }
        run("4.0.0.RELEASE");
        for (int i = 1; i <= 9; i++) {
            run("4.0." + i + ".RELEASE");
        }
        for (int i = 0; i <= 9; i++) {
            run("4.1." + i + ".RELEASE");
        }
        for (int i = 0; i <= 9; i++) {
            run("4.2." + i + ".RELEASE");
        }
        for (int i = 0; i <= 22; i++) {
            run("4.3." + i + ".RELEASE");
        }
        runJava8("5.0.0.RELEASE", "0.7.0.RELEASE");
        runJava8("5.0.1.RELEASE", "0.7.1.RELEASE");
        runJava8("5.0.2.RELEASE", "0.7.1.RELEASE");
        runJava8("5.0.3.RELEASE", "0.7.3.RELEASE");
        runJava8("5.0.4.RELEASE", "0.7.4.RELEASE");
        runJava8("5.0.5.RELEASE", "0.7.6.RELEASE");
        runJava8("5.0.6.RELEASE", "0.7.7.RELEASE");
        runJava8("5.0.7.RELEASE", "0.7.8.RELEASE");
        runJava8("5.0.8.RELEASE", "0.7.8.RELEASE");
        runJava8("5.0.9.RELEASE", "0.7.9.RELEASE");
        runJava8("5.0.10.RELEASE", "0.7.9.RELEASE");
        runJava8("5.0.11.RELEASE", "0.7.12.RELEASE");
        runJava8("5.0.12.RELEASE", "0.7.14.RELEASE");

        runJava8("5.1.0.RELEASE", "0.8.0.RELEASE");
        runJava8("5.1.1.RELEASE", "0.8.1.RELEASE");
        runJava8("5.1.2.RELEASE", "0.8.2.RELEASE");
        runJava8("5.1.3.RELEASE", "0.8.3.RELEASE");
        runJava8("5.1.4.RELEASE", "0.8.4.RELEASE");
    }

    private static void run(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "spring.version", version);
        Util.runTests(MODULE_PATH, getProfiles(version), JAVA8, JAVA7, JAVA6);
    }

    private static void runNotJava8(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "spring.version", version);
        Util.runTests(MODULE_PATH, getProfiles(version), JAVA7, JAVA6);
    }

    private static void runJava8(String version, String reactorVersion)
            throws Exception {
        Util.updateLibVersion(MODULE_PATH, "spring.version", version);
        Util.updateLibVersion(MODULE_PATH, "reactor.version", reactorVersion);
        Util.runTests(MODULE_PATH, getProfiles(version), JAVA8);
    }

    private static String[] getProfiles(String version) {
        if (version.startsWith("3.0.") || version.startsWith("3.1.")) {
            return new String[0];
        } else if (version.startsWith("3.2.")) {
            return new String[] {"spring-3.2.0-and-later"};
        } else if (version.equals("4.0.0.RELEASE")) {
            return new String[] {"spring-3.2.0-and-later", "spring-4.0.0-and-later",
                    "spring-4.0.0"};
        } else if (version.startsWith("4.")) {
            return new String[] {"spring-3.2.0-and-later", "spring-4.0.0-and-later",
                    "spring-4.0.1-and-later"};
        } else if (version.startsWith("5.0.")) {
            return new String[] {"spring-3.2.0-and-later", "spring-4.0.0-and-later",
                    "spring-4.0.1-and-later", "spring-5.0.x"};
        } else if (version.startsWith("5.1.")) {
            return new String[] {"spring-3.2.0-and-later", "spring-4.0.0-and-later",
                    "spring-4.0.1-and-later", "spring-5.1.x"};
        } else {
            throw new IllegalStateException("Unexpected spring version: " + version);
        }
    }
}
