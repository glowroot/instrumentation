/*
 * Copyright 2015-2019 the original author or authors.
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
package org.glowroot.instrumentation.jdbc.boot;

import java.util.Collections;
import java.util.Map;

import org.glowroot.instrumentation.api.QueryMessageSupplier;

public class BatchPreparedStatementMessageSupplier2 extends QueryMessageSupplier {

    private final int batchCount;

    public BatchPreparedStatementMessageSupplier2(int batchCount) {
        this.batchCount = batchCount;
    }

    @Override
    public Map<String, Integer> get() {
        return Collections.singletonMap("batchCount", batchCount);
    }
}
