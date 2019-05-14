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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glowroot.instrumentation.api.QueryMessageSupplier;

public class BatchPreparedStatementMessageSupplier extends QueryMessageSupplier {

    private final List<BindParameterList> batchedParameters;
    private final int batchCount;

    public BatchPreparedStatementMessageSupplier(List<BindParameterList> batchedParameters,
            int batchCount) {
        this.batchedParameters = batchedParameters;
        this.batchCount = batchCount;
    }

    @Override
    public Map<String, Object> get() {
        if (batchedParameters.isEmpty()) {
            return Collections.<String, Object>singletonMap("batchCount", batchCount);
        } else {
            Map<String, Object> details = new HashMap<String, Object>(2);
            details.put("batchCount", batchCount);
            details.put("parameters", getParameters());
            return details;
        }
    }

    private List<List</*@Nullable*/ Object>> getParameters() {
        List<List</*@Nullable*/ Object>> parameters =
                new ArrayList<List</*@Nullable*/ Object>>(batchedParameters.size());
        for (BindParameterList oneParameters : batchedParameters) {
            parameters.add(oneParameters.toDetailList());
        }
        return parameters;
    }
}
