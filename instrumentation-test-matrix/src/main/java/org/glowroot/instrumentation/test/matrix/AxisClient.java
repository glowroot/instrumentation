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

public class AxisClient {

    private static final String MODULE_PATH = "instrumentation/axis-client";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].contentEquals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run("1.0");
    }

    static void runAll() throws Exception {
        run("1.0");
        run("1.1");
        run("1.2");
        run("1.2.1");
        run("1.3");
        run("1.4");
    }

    private static void run(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "axis.version", version);
        Util.runTests(MODULE_PATH, JAVA8, JAVA7, JAVA6);
    }
}
