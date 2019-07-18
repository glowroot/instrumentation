/**
 * Copyright 2018 the original author or authors.
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

public class MongoDB {

    private static final String MODULE_PATH = "instrumentation/mongodb";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        runPriorTo37("0.11");
        run("3.7.0");
    }

    static void runAll() throws Exception {
        runPriorTo37("0.11");
        runPriorTo37("1.0");
        runPriorTo37("1.1");
        runPriorTo37("1.2");
        runPriorTo37("1.2.1");
        runPriorTo37("1.3");
        runPriorTo37("1.4");
        runPriorTo37("2.0");
        runPriorTo37("2.1");
        runPriorTo37("2.2");
        runPriorTo37("2.3");
        runPriorTo37("2.4");
        runPriorTo37("2.5");
        runPriorTo37("2.5.1");
        runPriorTo37("2.5.2");
        runPriorTo37("2.5.3");
        runPriorTo37("2.6");
        runPriorTo37("2.6.1");
        runPriorTo37("2.6.2");
        runPriorTo37("2.6.3");
        runPriorTo37("2.6.5");
        runPriorTo37("2.7.0");
        runPriorTo37("2.7.1");
        runPriorTo37("2.7.2");
        runPriorTo37("2.7.3");
        runPriorTo37("2.8.0");
        runPriorTo37("2.9.0");
        runPriorTo37("2.9.1");
        runPriorTo37("2.9.2");
        runPriorTo37("2.9.3");
        runPriorTo37("2.10.0");
        runPriorTo37("2.10.1");
        runPriorTo37("2.11.0");
        runPriorTo37("2.11.1");
        runPriorTo37("2.11.2");
        runPriorTo37("2.11.3");
        runPriorTo37("2.11.4");
        runPriorTo37("2.12.0");
        runPriorTo37("2.12.1");
        runPriorTo37("2.12.2");
        runPriorTo37("2.12.3");
        runPriorTo37("2.12.4");
        runPriorTo37("2.12.5");
        runPriorTo37("2.13.0");
        runPriorTo37("2.13.1");
        runPriorTo37("2.13.2");
        runPriorTo37("2.13.3");
        runPriorTo37("2.14.0");
        runPriorTo37("2.14.1");
        runPriorTo37("2.14.2");
        runPriorTo37("2.14.3");

        runPriorTo37("3.0.0");
        runPriorTo37("3.0.1");
        runPriorTo37("3.0.2");
        runPriorTo37("3.0.3");
        runPriorTo37("3.0.4");
        runPriorTo37("3.1.0");
        runPriorTo37("3.1.1");
        runPriorTo37("3.2.0");
        runPriorTo37("3.2.1");
        runPriorTo37("3.2.2");
        runPriorTo37("3.3.0");
        runPriorTo37("3.4.0");
        runPriorTo37("3.4.1");
        runPriorTo37("3.4.2");
        runPriorTo37("3.4.3");
        runPriorTo37("3.5.0");
        runPriorTo37("3.6.0");
        runPriorTo37("3.6.1");
        runPriorTo37("3.6.2");
        runPriorTo37("3.6.3");
        runPriorTo37("3.6.4");
        run("3.7.0");
        run("3.7.1");
        run("3.8.0");
        run("3.8.1");
        run("3.8.2");
    }

    private static void runPriorTo37(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "mongodb.driver.version", version);
        Util.runTests(MODULE_PATH, "mongodb-prior-to-3.7.0", JAVA8, JAVA7, JAVA6);
    }

    private static void run(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "mongodb.driver.version", version);
        Util.runTests(MODULE_PATH, "mongodb-3.7.0-and-later", JAVA8, JAVA7, JAVA6);
    }
}
