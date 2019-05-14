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

import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA7;
import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA8;

public class OkHttp {

    private static final String MODULE_PATH = "instrumentation/okhttp";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].contentEquals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run2x("2.2.0");
        run3x("3.0.1");
    }

    static void runAll() throws Exception {
        run2x("2.2.0");
        run2x("2.3.0");
        run2x("2.4.0");
        run2x("2.5.0");
        run2x("2.6.0");
        for (int i = 0; i <= 5; i++) {
            run2x("2.7." + i);
        }
        run3x("3.0.1");
        run3x("3.1.0");
        run3x("3.1.1");
        run3x("3.1.2");
        run3x("3.2.0");
        run3x("3.3.0");
        run3x("3.3.1");
        run3x("3.4.0");
        run3x("3.4.1");
        run3x("3.4.2");
        run3x("3.5.0");
        run3x("3.6.0");
        run3x("3.7.0");
        run3x("3.8.0");
        run3x("3.8.1");
        run3x("3.9.0");
        run3x("3.9.1");
        run3x("3.10.0");
        run3x("3.11.0");
        run3x("3.12.0");
        run3x("3.12.1");
    }

    private static void run2x(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "okhttp2x.version", version);
        Util.runTest(MODULE_PATH, "OkHttp2xIT", JAVA8, JAVA7);
    }

    private static void run3x(String version) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "okhttp3x.version", version);
        Util.runTest(MODULE_PATH, "OkHttp3xIT", JAVA8, JAVA7);
    }
}
