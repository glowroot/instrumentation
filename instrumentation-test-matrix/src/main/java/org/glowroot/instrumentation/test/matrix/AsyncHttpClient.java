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

import java.io.IOException;

import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA6;
import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA7;
import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA8;

public class AsyncHttpClient {

    private static final String MODULE_PATH = "instrumentation/async-http-client";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run1x("1.6.1");
        run1xJava7("1.9.0");
        run2x("2.0.0");
    }

    static void runAll() throws Exception {
        for (int i = 1; i <= 5; i++) {
            run1x("1.6." + i);
        }
        for (int i = 0; i <= 24; i++) {
            run1x("1.7." + i);
        }
        for (int i = 0; i <= 17; i++) {
            run1x("1.8." + i);
        }
        for (int i = 0; i <= 40; i++) {
            run1xJava7("1.9." + i);
        }

        run2x("2.0.0");
        run2x("2.0.1");
        run2x("2.0.2");
        run2x("2.0.3");
        run2x("2.0.4");
        run2x("2.0.5");
        run2x("2.0.6");
        run2x("2.0.7");
        run2x("2.0.8");
        run2x("2.0.9");
        run2x("2.0.10");
        run2x("2.0.11");
        run2x("2.0.12");
        run2x("2.0.13");
        run2x("2.0.14");
        run2x("2.0.15");
        run2x("2.0.16");
        run2x("2.0.17");
        run2x("2.0.18");
        run2x("2.0.19");
        run2x("2.0.20");
        run2x("2.0.21");
        run2x("2.0.22");
        run2x("2.0.23");
        run2x("2.0.24");
        run2x("2.0.25");
        run2x("2.0.26");
        run2x("2.0.27");
        run2x("2.0.28");
        run2x("2.0.29");
        run2x("2.0.30");
        run2x("2.0.31");
        run2x("2.0.32");
        run2x("2.0.33");
        run2x("2.0.34");
        run2x("2.0.35");
        run2x("2.0.36");
        run2x("2.0.37");
        run2x("2.0.38");
        run2x("2.0.39");

        run2x("2.1.0");
        run2x("2.1.1");
        run2x("2.1.2");

        run2x("2.2.0");
        run2x("2.2.1");

        run2x("2.3.0");

        run2x("2.4.0");
        run2x("2.4.1");
        run2x("2.4.2");
        run2x("2.4.3");
        run2x("2.4.4");
        run2x("2.4.5");
        run2x("2.4.6");
        run2x("2.4.7");
        run2x("2.4.8");
        run2x("2.4.9");

        run2x("2.5.0");
        run2x("2.5.1");
        run2x("2.5.2");
        run2x("2.5.3");
        run2x("2.5.4");
        run2x("2.6.0");
    }

    private static void run1x(String version) throws Exception {
        updateLibVersion("asynchttpclient1x.version", version);
        run("AsyncHttpClient1xIT", "async-http-client-1.x");
    }

    private static void run1xJava7(String version) throws Exception {
        updateLibVersion("asynchttpclient1x.version", version);
        runJava7("AsyncHttpClient1xIT", "async-http-client-1.x");
    }

    private static void run2x(String version) throws Exception {
        updateLibVersion("asynchttpclient2x.version", version);
        runJava8("AsyncHttpClient2xIT", "async-http-client-2.x");
    }

    private static void updateLibVersion(String property, String version) throws IOException {
        Util.updateLibVersion(MODULE_PATH, property, version);
    }

    private static void run(String test, String profile) throws Exception {
        Util.runTest(MODULE_PATH, test, profile, JAVA8, JAVA7, JAVA6);
    }

    private static void runJava7(String test, String profile) throws Exception {
        Util.runTest(MODULE_PATH, test, profile, JAVA8, JAVA7);
    }

    private static void runJava8(String test, String profile) throws Exception {
        Util.runTest(MODULE_PATH, test, profile, JAVA8);
    }
}
