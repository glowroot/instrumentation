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
package org.glowroot.instrumentation.engine.weaving;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class Reweaving {

    private static final Logger logger = LoggerFactory.getLogger(Reweaving.class);

    private Reweaving() {}

    public static void initialReweave(Set<PointcutClassName> pointcutClassNames,
            Class<?>[] initialLoadedClasses, Instrumentation instrumentation) {
        if (!instrumentation.isRetransformClassesSupported()) {
            return;
        }
        Set<Class<?>> classes = getExistingModifiableSubClasses(pointcutClassNames,
                initialLoadedClasses, instrumentation);
        for (Class<?> clazz : classes) {
            if (clazz.isInterface()) {
                continue;
            }
            try {
                instrumentation.retransformClasses(clazz);
            } catch (UnmodifiableClassException e) {
                // IBM J9 VM Java 6 throws UnmodifiableClassException even though call to
                // isModifiableClass() in getExistingModifiableSubClasses() returns true
                logger.debug(e.getMessage(), e);
            }
        }
    }

    public static Set<Class<?>> getExistingModifiableSubClasses(
            Set<PointcutClassName> pointcutClassNames, Class<?>[] classes,
            Instrumentation instrumentation) {
        List<Class<?>> matchingClasses = Lists.newArrayList();
        Multimap<Class<?>, Class<?>> subClasses = ArrayListMultimap.create();
        for (Class<?> clazz : classes) {
            if (!instrumentation.isModifiableClass(clazz)) {
                continue;
            }
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null) {
                subClasses.put(superclass, clazz);
            }
            for (Class<?> iface : clazz.getInterfaces()) {
                subClasses.put(iface, clazz);
            }
            for (PointcutClassName pointcutClassName : pointcutClassNames) {
                if (pointcutClassName.appliesTo(clazz.getName())) {
                    matchingClasses.add(clazz);
                    break;
                }
            }
        }
        Set<Class<?>> matchingSubClasses = Sets.newHashSet();
        for (Class<?> matchingClass : matchingClasses) {
            addToMatchingSubClasses(matchingClass, matchingSubClasses, subClasses);
        }
        return matchingSubClasses;
    }

    private static void addToMatchingSubClasses(Class<?> clazz, Set<Class<?>> matchingSubClasses,
            Multimap<Class<?>, Class<?>> subClasses) {
        matchingSubClasses.add(clazz);
        for (Class<?> subClass : subClasses.get(clazz)) {
            addToMatchingSubClasses(subClass, matchingSubClasses, subClasses);
        }
    }

    @Value.Immutable
    public abstract static class PointcutClassName {

        abstract @Nullable Pattern pattern();

        abstract @Nullable String nonPattern();

        abstract @Nullable PointcutClassName subTypeRestriction();

        abstract boolean doNotMatchSubClasses();

        public static PointcutClassName fromMaybePattern(String maybePattern,
                @Nullable PointcutClassName subTypeRestriction, boolean doNotMatchSubClasses) {
            Pattern pattern = MaybePatterns.buildPattern(maybePattern);
            if (pattern == null) {
                return fromNonPattern(maybePattern, subTypeRestriction, doNotMatchSubClasses);
            } else {
                return fromPattern(pattern, subTypeRestriction, doNotMatchSubClasses);
            }
        }

        public static PointcutClassName fromPattern(Pattern pattern,
                @Nullable PointcutClassName subTypeRestrictionPointcutClassName,
                boolean doNotMatchSubClasses) {
            return ImmutablePointcutClassName.builder()
                    .pattern(pattern)
                    .nonPattern(null)
                    .subTypeRestriction(subTypeRestrictionPointcutClassName)
                    .doNotMatchSubClasses(doNotMatchSubClasses)
                    .build();
        }

        public static PointcutClassName fromNonPattern(String nonPattern,
                @Nullable PointcutClassName subTypeRestrictionPointcutClassName,
                boolean doNotMatchSubClasses) {
            return ImmutablePointcutClassName.builder()
                    .pattern(null)
                    .nonPattern(nonPattern)
                    .subTypeRestriction(subTypeRestrictionPointcutClassName)
                    .doNotMatchSubClasses(doNotMatchSubClasses)
                    .build();
        }

        private boolean appliesTo(String className) {
            PointcutClassName subTypeRestriction = subTypeRestriction();
            if (subTypeRestriction != null && !subTypeRestriction.appliesTo(className)) {
                return false;
            }
            Pattern pattern = pattern();
            if (pattern != null) {
                return pattern.matcher(className).matches();
            } else {
                return checkNotNull(nonPattern()).equals(className);
            }
        }
    }
}
