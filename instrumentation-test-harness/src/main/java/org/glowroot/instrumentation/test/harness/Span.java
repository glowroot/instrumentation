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
package org.glowroot.instrumentation.test.harness;

import java.util.Map;

import org.checkerframework.checker.nullness.qual.Nullable;

public interface Span {

    long totalNanos();

    String message();

    @AllowNulls
    Map<String, /*@Nullable*/ Object> detail();

    @Nullable
    Long locationStackTraceMillis();

    // see http://immutables.github.io/immutable.html#nulls-in-collection
    @interface AllowNulls {}
}
