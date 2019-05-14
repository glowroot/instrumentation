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
package org.glowroot.instrumentation.engine.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.api.TimerName;

// used to ensure one instance per name so that pointer equality can be used instead of String
// equality
//
// also used to ensure @Advice.Pointcut timer name matches the timer name passed to TransactionRegistry
public class TimerNameCache {

    private static final Logger logger = LoggerFactory.getLogger(TimerNameCache.class);

    // weak values is to support dynamically named timers
    private final LoadingCache<String, TimerNameImpl> names = CacheBuilder.newBuilder()
            .weakValues()
            .build(new CacheLoader<String, TimerNameImpl>() {
                @Override
                public TimerNameImpl load(String name) {
                    return ImmutableTimerNameImpl.of(name, false);
                }
            });

    private final TimerName unknownTimerName = names.getUnchecked("unknown");

    public TimerName getTimerName(String name) {
        if (name == null) {
            logger.error("getTimerName(): argument 'name' must be non-null");
            return unknownTimerName;
        }
        return names.getUnchecked(name);
    }
}
