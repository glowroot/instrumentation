/*
 * Copyright 2012-2019 the original author or authors.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.util.ImmutableMap;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// this is public so it can be called from other instrumentation
public class JdbcInstrumentationProperties {

    private static final ConfigService configService = Agent.getConfigService("jdbc");

    // visibility should be provided by memoryBarrier in
    // org.glowroot.instrumentation.api.config.ConfigService
    private static long stackTraceThresholdNanos;
    private static long explainPlanThresholdNanos;

    private static volatile Map<String, Set<Integer>> displayBinaryParameterAsHex =
            Collections.emptyMap();

    static {
        configService.registerConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                stackTraceThresholdNanos = getThresholdNanos("stackTraceThresholdMillis");
                explainPlanThresholdNanos = getThresholdNanos("explainPlanThresholdMillis");
            }
        });
    }

    private JdbcInstrumentationProperties() {}

    // this can always be called multiple times with the same sql if want to display multiple
    // parameters in the same sql as hex
    //
    // this is public so it can be called from other instrumentation
    public static void setDisplayBinaryParameterAsHex(String sql, int parameterIndex) {
        Map<String, Set<Integer>> mutableMap =
                new HashMap<String, Set<Integer>>(displayBinaryParameterAsHex);
        Set<Integer> set = mutableMap.get(sql);
        if (set == null) {
            set = new HashSet<Integer>();
        } else {
            // convert to mutable set
            set = new HashSet<Integer>(set);
        }
        set.add(parameterIndex);
        mutableMap.put(sql, set);
        displayBinaryParameterAsHex = ImmutableMap.copyOf(mutableMap);
    }

    public static long stackTraceThresholdNanos() {
        return stackTraceThresholdNanos;
    }

    public static long explainPlanThresholdNanos() {
        return explainPlanThresholdNanos;
    }

    public static boolean displayBinaryParameterAsHex(String sql, int parameterIndex) {
        Set<Integer> set = displayBinaryParameterAsHex.get(sql);
        return set != null && set.contains(parameterIndex);
    }

    private static long getThresholdNanos(String name) {
        Double value = configService.getDoubleProperty(name).value();
        return value == null ? Long.MAX_VALUE : MILLISECONDS.toNanos(value.intValue());
    }
}
