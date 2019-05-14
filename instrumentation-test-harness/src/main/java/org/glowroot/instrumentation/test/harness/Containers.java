/*
 * Copyright 2011-2019 the original author or authors.
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
package org.glowroot.instrumentation.test.harness;

import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;
import org.glowroot.instrumentation.test.harness.impl.LocalContainer;

public class Containers {

    private static final String TEST_HARNESS_PROPERTY_NAME = "test.harness";

    private static final Harness harness;

    static {
        String value = System.getProperty(TEST_HARNESS_PROPERTY_NAME);
        if (value == null) {
            // this default is provided primarily for running tests from IDE
            harness = Harness.LOCAL;
        } else if (value.equals("javaagent")) {
            harness = Harness.JAVAAGENT;
        } else if (value.equals("local")) {
            harness = Harness.LOCAL;
        } else {
            throw new IllegalStateException(
                    "Unexpected " + TEST_HARNESS_PROPERTY_NAME + " value: " + value);
        }
    }

    private Containers() {}

    public static Container create() throws Exception {
        switch (harness) {
            case JAVAAGENT:
                // this is the most realistic way to run tests
                return JavaagentContainer.create();
            case LOCAL:
                // this is the easiest way to run/debug tests inside of IDE
                return LocalContainer.create();
            default:
                throw new IllegalStateException("Unexpected harness enum value: " + harness);
        }
    }

    public static boolean useJavaagent() {
        return harness == Harness.JAVAAGENT;
    }

    private enum Harness {
        JAVAAGENT, LOCAL;
    }
}
