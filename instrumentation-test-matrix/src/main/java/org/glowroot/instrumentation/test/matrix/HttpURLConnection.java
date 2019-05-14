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

public class HttpURLConnection {

    private static final String MODULE_PATH = "instrumentation/http-url-connection";

    public static void main(String[] args) throws Exception {
        run();
    }

    static void run() throws Exception {
        Util.log("HttpURLConnection");
        Util.runTests(MODULE_PATH, JAVA8, JAVA7, JAVA6);
    }
}
