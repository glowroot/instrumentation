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

public class Cassandra {

    private static final String MODULE_PATH = "instrumentation/cassandra";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run("2.0.4", "guava-19.0");
        run("3.2.0");
    }

    static void runAll() throws Exception {
        for (int i = 4; i <= 12; i++) {
            run("2.0." + i, "guava-19.0");
        }
        run("2.0.9.1", "guava-19.0");
        run("2.0.9.2", "guava-19.0");
        run("2.0.12.1", "guava-19.0");
        run("2.0.12.2", "guava-19.0");
        run("2.0.12.3", "guava-19.0");
        for (int i = 0; i <= 10; i++) {
            run("2.1." + i, "guava-19.0");
        }
        run("2.1.7.1", "guava-19.0");
        run("2.1.10.1", "guava-19.0");
        run("2.1.10.2", "guava-19.0");
        run("2.1.10.3", "guava-19.0");
        for (int i = 0; i <= 8; i++) {
            run("3.0." + i, "guava-19.0");
        }
        for (int i = 0; i <= 4; i++) {
            run("3.1." + i, "guava-19.0");
        }
        run("3.2.0");
        run("3.3.0");
        run("3.3.1");
        run("3.3.2");
        run("3.4.0");
        run("3.5.0");
        run("3.5.1");
        run("3.6.0");
    }

    private static void run(String version, String... profiles) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "datastax.driver.version", version);
        Util.runTests(MODULE_PATH, profiles, JAVA8, JAVA7, JAVA6);
    }
}
