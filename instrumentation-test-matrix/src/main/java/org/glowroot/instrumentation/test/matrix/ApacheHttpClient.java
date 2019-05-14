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

public class ApacheHttpClient {

    private static final String MODULE_PATH = "instrumentation/apache-http-client";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].contentEquals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run3x("3.0");
        run4x("4.0", "apache-httpclient-pre-4.2");
        run4x("4.2");
    }

    static void runAll() throws Exception {
        run3x("3.0");
        run3x("3.0.1");
        run3x("3.1");
        run4x("4.0", "apache-httpclient-pre-4.2");
        for (int i = 1; i <= 3; i++) {
            run4x("4.0." + i, "apache-httpclient-pre-4.2");
        }
        run4x("4.1", "apache-httpclient-pre-4.2");
        for (int i = 1; i <= 3; i++) {
            run4x("4.1." + i, "apache-httpclient-pre-4.2");
        }
        run4x("4.2");
        for (int i = 1; i <= 6; i++) {
            run4x("4.2." + i);
        }
        run4x("4.3");
        for (int i = 1; i <= 6; i++) {
            run4x("4.3." + i);
        }
        run4x("4.4");
        run4x("4.4.1");
        run4x("4.5");
        run4x("4.5.1");
        run4x("4.5.2");
        run4x("4.5.3");
        run4x("4.5.4");
        run4x("4.5.5");
        run4x("4.5.6");
    }

    private static void run3x(String version, String... profiles) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "apachehttpclient3x.version", version);
        Util.runTest(MODULE_PATH, "ApacheHttpClient3xIT", profiles, JAVA8, JAVA7, JAVA6);
    }

    private static void run4x(String version, String... profiles) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "apachehttpclient4x.version", version);
        Util.runTest(MODULE_PATH, "ApacheHttpClient4xIT", profiles, JAVA8, JAVA7, JAVA6);
    }
}
