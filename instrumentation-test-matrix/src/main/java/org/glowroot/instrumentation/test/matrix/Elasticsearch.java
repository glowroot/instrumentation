/**
 * Copyright 2017-2019 the original author or authors.
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

import static org.glowroot.instrumentation.test.matrix.JavaVersion.JAVA8;

public class Elasticsearch {

    private static final String MODULE_PATH = "instrumentation/elasticsearch";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].contentEquals("short")) {
            runShort();
        } else {
            runAll();
        }
    }

    static void runShort() throws Exception {
        run("5.0.0", "2.6.2");
    }

    static void runAll() throws Exception {
        // for log4j version, see optional dependency of org.elasticsearch:elasticsearch
        run("5.0.0", "2.6.2");
        run("5.0.1", "2.6.2");
        run("5.0.2", "2.6.2");
        // there is no 5.1.0 in maven central
        run("5.1.1", "2.7");
        run("5.1.2", "2.7");
        run("5.2.0", "2.7");
        run("5.2.1", "2.7");
        run("5.2.2", "2.7");
        run("5.3.0", "2.7");
        run("5.3.1", "2.7");
        run("5.3.2", "2.7");
        run("5.3.3", "2.7");
        run("5.4.0", "2.8.2");
        run("5.4.1", "2.8.2");
        run("5.4.2", "2.8.2");
        run("5.4.3", "2.8.2");
        run("5.5.0", "2.8.2");
        run("5.5.1", "2.8.2");
        run("5.5.2", "2.8.2");
        run("5.5.3", "2.8.2");
        run("5.6.0", "2.9.0");
        run("5.6.1", "2.9.0");
        run("5.6.2", "2.9.1");
        run("5.6.3", "2.9.1");
        run("5.6.4", "2.9.1");
        run("5.6.5", "2.9.1");
        run("5.6.6", "2.9.1");
        run("5.6.7", "2.9.1");
        run("5.6.8", "2.9.1");
        run("5.6.9", "2.9.1");
        run("5.6.10", "2.9.1");
        run("5.6.11", "2.11.1");
        run("5.6.12", "2.11.1");
        run("5.6.13", "2.11.1");
        run("5.6.14", "2.11.1");
        run("6.0.0", "2.9.1");
        run("6.0.1", "2.9.1");
        run("6.1.0", "2.9.1");
        run("6.1.1", "2.9.1");
        run("6.1.2", "2.9.1");
        run("6.1.3", "2.9.1");
        run("6.1.4", "2.9.1");
        run("6.2.0", "2.9.1");
        run("6.2.1", "2.9.1");
        run("6.2.2", "2.9.1");
        run("6.2.3", "2.9.1");
        run("6.2.4", "2.9.1");
        run("6.3.0", "2.9.1");
        run("6.3.1", "2.9.1");
        run("6.3.2", "2.9.1");
        run("6.4.0", "2.11.1");
        run("6.4.1", "2.11.1");
        run("6.4.2", "2.11.1");
        run("6.4.3", "2.11.1");
        run("6.5.0", "2.11.1");
        run("6.5.1", "2.11.1");
        run("6.5.2", "2.11.1");
        run("6.5.3", "2.11.1");
        run("6.5.4", "2.11.1");
    }

    private static void run(String version, String log4jVersion) throws Exception {
        Util.updateLibVersion(MODULE_PATH, "elasticsearch.version", version);
        Util.updateLibVersion(MODULE_PATH, "log4j.version", log4jVersion);
        Util.runTests(MODULE_PATH, JAVA8);
    }
}
