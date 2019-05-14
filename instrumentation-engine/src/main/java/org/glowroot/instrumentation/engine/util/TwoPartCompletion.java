/*
 * Copyright 2019 the original author or authors.
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
package org.glowroot.instrumentation.engine.util;

public class TwoPartCompletion {

    private volatile boolean part1;
    private volatile boolean part2;

    // returns true if this call results in the two-part completion
    // returns false if part1 was already complete, or if part2 is still not complete
    public boolean completePart1() {
        synchronized (this) {
            if (!part1 && part2) {
                part1 = true;
                return true;
            } else {
                part1 = true;
                return false;
            }
        }
    }

    // returns true if this call results in the two-part completion
    // returns false if part2 was already complete, or if part1 is still not complete
    public boolean completePart2() {
        synchronized (this) {
            if (part1 && !part2) {
                part2 = true;
                return true;
            } else {
                part2 = true;
                return false;
            }
        }
    }
}
