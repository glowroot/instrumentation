/*
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
package org.glowroot.instrumentation.api;

import java.util.Collections;
import java.util.Map;

/**
 * A (lazy) supplier of {@link QueryMessage} instances. Needs to be thread safe since transaction
 * thread creates it, but trace storage (and live viewing) is done in a separate thread.
 */
public abstract class QueryMessageSupplier {

    private static final QueryMessageSupplier EMPTY = new QueryMessageSupplier() {
        @Override
        public Map<String, ?> get() {
            return Collections.emptyMap();
        }
    };

    /**
     * Returns the {@code QueryMessage} for a {@link QuerySpan}.
     * 
     * The {@code QueryMessage} does not need to be thread safe if it is instantiated by the
     * implementation of this method.
     */
    public abstract Map<String, ?> get();

    protected QueryMessageSupplier() {}

    /**
     * Creates a {@code QueryMessageSupplier} for the specified {@code prefix} and {@code suffix}.
     */
    public static QueryMessageSupplier create() {
        return EMPTY;
    }

    /**
     * Creates a {@code QueryMessageSupplier} for the specified {@code prefix}.
     */
    public static QueryMessageSupplier create(final Map<String, ?> detail) {
        return new QueryMessageSupplier() {
            @Override
            public Map<String, ?> get() {
                return detail;
            }
        };
    }
}
