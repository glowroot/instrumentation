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
package org.glowroot.instrumentation.engine.config;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.DefaultValue;

import static com.google.common.base.Charsets.ISO_8859_1;
import static com.google.common.base.Preconditions.checkNotNull;

public class InstrumentationDescriptors {

    private InstrumentationDescriptors() {}

    public static List<InstrumentationDescriptor> read()
            throws IOException, ClassNotFoundException {
        List<URL> resources = getResources("META-INF/instrumentation.list");
        List<InstrumentationDescriptor> descriptors = Lists.newArrayList();
        for (URL resource : resources) {
            List<String> jsonFiles = Resources.readLines(resource, ISO_8859_1);
            for (String jsonFile : jsonFiles) {
                if (jsonFile.isEmpty()) {
                    continue;
                }
                Class<?> clazz = Class.forName(jsonFile, false,
                        InstrumentationDescriptors.class.getClassLoader());
                Descriptor descriptor = checkNotNull(clazz.getAnnotation(Descriptor.class));
                descriptors.add(read(descriptor));
            }
        }
        return checkNotNull(descriptors);
    }

    private static InstrumentationDescriptor read(Descriptor descriptor) {
        ImmutableInstrumentationDescriptor.Builder builder =
                ImmutableInstrumentationDescriptor.builder()
                        .id(descriptor.id())
                        .name(descriptor.name());
        for (Descriptor.Property property : descriptor.properties()) {
            ImmutablePropertyDescriptor.Builder propertyBuilder =
                    ImmutablePropertyDescriptor.builder()
                            .name(property.name())
                            .type(property.type());
            DefaultValue[] defaultValues = property.defaultValue();
            if (defaultValues.length > 0) {
                DefaultValue defaultValue = defaultValues[0];
                propertyBuilder
                        .defaultValue(new org.glowroot.instrumentation.engine.config.DefaultValue(
                                getValue(property, defaultValue)));
            }
            builder.addProperties(propertyBuilder
                    .label(property.label())
                    .checkboxLabel(property.checkboxLabel())
                    .description(property.description())
                    .build());
        }
        for (Descriptor.Advice advice : descriptor.advice()) {
            builder.addAdviceConfigs(ImmutableAdviceConfig.builder()
                    .className(advice.className())
                    .classAnnotation(advice.classAnnotation())
                    .subTypeRestriction(advice.subTypeRestriction())
                    .superTypeRestriction(advice.superTypeRestriction())
                    .methodName(advice.methodName())
                    .methodAnnotation(advice.methodAnnotation())
                    .addMethodParameterTypes(advice.methodParameterTypes())
                    .methodReturnType(advice.methodReturnType())
                    .addMethodModifiers(advice.methodModifiers())
                    .nestingGroup(advice.nestingGroup())
                    .order(advice.order())
                    .captureKind(advice.captureKind())
                    .transactionType(advice.transactionType())
                    .transactionNameTemplate(advice.transactionNameTemplate())
                    .transactionUserTemplate(advice.transactionUserTemplate())
                    .transactionSlowThresholdMillis(
                            toNullableInteger(advice.transactionSlowThresholdMillis()))
                    .alreadyInTransactionBehavior(advice.alreadyInTransactionBehavior())
                    .spanMessageTemplate(advice.spanMessageTemplate())
                    .spanStackThresholdMillis(toNullableInteger(advice.spanStackThresholdMillis()))
                    .spanCaptureSelfNested(advice.spanCaptureSelfNested())
                    .timerName(advice.timerName())
                    .enabledProperty(advice.enabledProperty())
                    .localSpanEnabledProperty(advice.localSpanEnabledProperty())
                    .build());
        }
        for (Class<?> clazz : descriptor.classes()) {
            builder.addClasses(clazz.getName());
        }
        return builder.collocate(descriptor.collocate())
                .build();
    }

    private static List<URL> getResources(String resourceName) throws IOException {
        ClassLoader loader = InstrumentationDescriptors.class.getClassLoader();
        if (loader == null) {
            return ImmutableList
                    .copyOf(Iterators.forEnumeration(ClassLoader.getSystemResources(resourceName)));
        } else {
            return ImmutableList
                    .copyOf(Iterators.forEnumeration(loader.getResources(resourceName)));
        }
    }

    private static Object getValue(Descriptor.Property property, DefaultValue defaultValue) {
        switch (property.type()) {
            case STRING:
                return defaultValue.stringValue();
            case BOOLEAN:
                return defaultValue.booleanValue();
            case DOUBLE:
                return defaultValue.doubleValue();
            case LIST:
                return Arrays.asList(defaultValue.listValue());
            default:
                throw new IllegalStateException("Unexpected property type: " + property.type());
        }
    }

    private static @Nullable Integer toNullableInteger(int value) {
        return value == -1 ? null : value;
    }
}
