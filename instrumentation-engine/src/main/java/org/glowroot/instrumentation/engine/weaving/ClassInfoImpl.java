/*
 * Copyright 2018-2019 the original author or authors.
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
package org.glowroot.instrumentation.engine.weaving;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.ClassInfo;

public class ClassInfoImpl implements ClassInfo {

    private final String name;
    private final @Nullable ClassLoader loader;

    public ClassInfoImpl(String name, @Nullable ClassLoader loader) {
        this.name = name;
        this.loader = loader;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable ClassLoader getLoader() {
        return loader;
    }
}
