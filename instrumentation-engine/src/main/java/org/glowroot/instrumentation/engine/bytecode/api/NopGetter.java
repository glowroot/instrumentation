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
package org.glowroot.instrumentation.engine.bytecode.api;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.Getter;

// used by generated advice
public class NopGetter implements Getter<Object> {

    public static final Getter<Object> GETTER = new NopGetter();

    public static final Object CARRIER = new Object();

    @Override
    public @Nullable String get(Object carrier, String key) {
        return null;
    }
}
