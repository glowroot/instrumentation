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

public class Struts {

    private static final String MODULE_PATH = "instrumentation/struts";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run1x("1.3.5");
        run2x("2.1.8");
        run2xJava7("2.5");
    }

    static void runAll() throws Exception {
        run1x("1.3.5");
        run1x("1.3.8");
        run1x("1.3.9");
        run1x("1.3.10");

        run2x("2.1.8");
        run2x("2.1.8.1");
        run2x("2.2.1");
        run2x("2.2.1.1");
        run2x("2.2.3");
        run2x("2.2.3.1");
        run2x("2.3.1");
        run2x("2.3.1.1");
        run2x("2.3.1.2");
        run2x("2.3.3");
        run2x("2.3.4");
        run2x("2.3.4.1");
        run2x("2.3.7");
        run2x("2.3.8");
        run2x("2.3.12");
        run2x("2.3.14");
        run2x("2.3.14.1");
        run2x("2.3.14.2");
        run2x("2.3.14.3");
        run2x("2.3.15");
        run2x("2.3.15.1");
        run2x("2.3.15.2");
        run2x("2.3.15.3");
        run2x("2.3.16");
        run2x("2.3.16.1");
        run2x("2.3.16.2");
        run2x("2.3.16.3");
        run2x("2.3.20");
        run2x("2.3.20.1");
        run2x("2.3.20.3");
        run2x("2.3.24");
        run2x("2.3.24.1");
        run2x("2.3.24.3");
        run2x("2.3.28");
        run2x("2.3.28.1");
        run2x("2.3.29");
        run2x("2.3.30");
        run2x("2.3.31");
        run2x("2.3.32");
        run2x("2.3.33");
        run2x("2.3.34");
        run2x("2.3.35");
        run2x("2.3.36");
        run2x("2.3.37");
        run2xJava7("2.5");
        run2xJava7("2.5.1");
        run2xJava7("2.5.2");
        run2xJava7("2.5.5");
        run2xJava7("2.5.8");
        run2xJava7("2.5.10");
        run2xJava7("2.5.10.1");
        run2xJava7("2.5.12");
        run2xJava7("2.5.13");
        run2xJava7("2.5.14");
        run2xJava7("2.5.14.1");
        run2xJava7("2.5.16");
        run2xJava7("2.5.17");
        run2xJava7("2.5.18");
    }

    private static void run1x(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "struts1x.version", version);
        Util.runTest(MODULE_PATH, "Struts1xIT", JAVA8, JAVA7, JAVA6);
    }

    private static void run2x(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "struts2x.version", version);
        Util.runTest(MODULE_PATH, "Struts2xIT", JAVA8, JAVA7, JAVA6);
    }

    private static void run2xJava7(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "struts2x.version", version);
        Util.runTest(MODULE_PATH, "Struts2xIT", JAVA8, JAVA7);
    }
}
