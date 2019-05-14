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
package org.glowroot.instrumentation.engine.impl;

import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.config.DoubleProperty;
import org.glowroot.instrumentation.api.config.ListProperty;
import org.glowroot.instrumentation.api.config.StringProperty;
import org.glowroot.instrumentation.engine.config.InstrumentationDescriptor;
import org.glowroot.instrumentation.engine.config.PropertyDescriptor;
import org.glowroot.instrumentation.engine.impl.InstrumentationServiceImpl.ConfigServiceFactory;

public class SimpleConfigServiceFactory implements ConfigServiceFactory {

    private final List<InstrumentationDescriptor> instrumentationDescriptors;
    private final Map<String, Map<String, /*@Nullable*/ Object>> overrides;

    public SimpleConfigServiceFactory(List<InstrumentationDescriptor> instrumentationDescriptors) {
        this(instrumentationDescriptors,
                ImmutableMap.<String, Map<String, /*@Nullable*/ Object>>of());
    }

    public SimpleConfigServiceFactory(List<InstrumentationDescriptor> instrumentationDescriptors,
            Map<String, Map<String, /*@Nullable*/ Object>> overrides) {
        this.instrumentationDescriptors = instrumentationDescriptors;
        this.overrides = overrides;
    }

    @Override
    public ConfigService create(String instrumentationId) {
        InstrumentationDescriptor descriptor =
                getDescriptor(instrumentationId, instrumentationDescriptors);
        Map<String, /*@Nullable*/ Object> overrides = this.overrides.get(instrumentationId);
        if (overrides == null) {
            overrides = ImmutableMap.of();
        }
        return new ConfigServiceImpl(descriptor.properties(), overrides);
    }

    private static InstrumentationDescriptor getDescriptor(String id,
            List<InstrumentationDescriptor> descriptors) {
        for (InstrumentationDescriptor descriptor : descriptors) {
            if (id.equals(descriptor.id())) {
                return descriptor;
            }
        }
        if (descriptors.isEmpty()) {
            throw new IllegalStateException("Unexpected instrumentation id: " + id
                    + " (there is no available instrumentation)");
        } else {
            List<String> ids = Lists.newArrayList();
            for (InstrumentationDescriptor descriptor : descriptors) {
                ids.add(descriptor.id());
            }
            throw new IllegalStateException("Unexpected instrumentation id: " + id
                    + " (available instrumentation ids are " + Joiner.on(", ").join(ids) + ")");
        }
    }

    private static class ConfigServiceImpl implements ConfigService {

        private final Map<String, StringProperty> stringProperties;
        private final Map<String, BooleanProperty> booleanProperties;
        private final Map<String, DoubleProperty> doubleProperties;
        private final Map<String, ListProperty> listProperties;

        private ConfigServiceImpl(List<PropertyDescriptor> propertyDescriptors,
                Map<String, /*@Nullable*/ Object> overrides) {
            Map<String, StringProperty> stringProperties = Maps.newHashMap();
            Map<String, BooleanProperty> booleanProperties = Maps.newHashMap();
            Map<String, DoubleProperty> doubleProperties = Maps.newHashMap();
            Map<String, ListProperty> listProperties = Maps.newHashMap();
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                String name = propertyDescriptor.name();
                Object value;
                // need to check contains since null is a valid value
                if (overrides.containsKey(name)) {
                    value = overrides.get(name);
                } else {
                    value = propertyDescriptor.getValidatedNonNullDefaultValue().value();
                }
                switch (propertyDescriptor.type()) {
                    case STRING:
                        if (value instanceof String) {
                            stringProperties.put(name, new StringPropertyImpl((String) value));
                        } else {
                            throw unexpectedValueType(name, "string", value);
                        }
                        break;
                    case BOOLEAN:
                        if (value instanceof Boolean) {
                            booleanProperties.put(name, new BooleanPropertyImpl((Boolean) value));
                        } else {
                            throw unexpectedValueType(name, "boolean", value);
                        }
                        break;
                    case DOUBLE:
                        if (value == null) {
                            doubleProperties.put(name, new DoublePropertyImpl(null));
                        } else if (value instanceof Number) {
                            doubleProperties.put(name,
                                    new DoublePropertyImpl(((Number) value).doubleValue()));
                        } else {
                            throw unexpectedValueType(name, "number", value);
                        }
                        break;
                    case LIST:
                        if (value instanceof List) {
                            List<String> stringList = Lists.newArrayList();
                            for (Object val : (List<?>) value) {
                                if (val instanceof String) {
                                    stringList.add((String) val);
                                } else {
                                    throw unexpectedValueTypeForList(name, val);
                                }
                            }
                            listProperties.put(name, new ListPropertyImpl(stringList));
                        } else {
                            throw unexpectedValueType(name, "list", value);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unexpected property descriptor type: "
                                + propertyDescriptor.type());
                }
            }

            this.stringProperties = ImmutableMap.copyOf(stringProperties);
            this.booleanProperties = ImmutableMap.copyOf(booleanProperties);
            this.doubleProperties = ImmutableMap.copyOf(doubleProperties);
            this.listProperties = ImmutableMap.copyOf(listProperties);
        }

        @Override
        public void registerConfigListener(ConfigListener listener) {
            listener.onChange();
        }

        @Override
        public StringProperty getStringProperty(String name) {
            StringProperty stringProperty = stringProperties.get(name);
            if (stringProperty == null) {
                throw new IllegalStateException("No such string property: " + name);
            }
            return stringProperty;
        }

        @Override
        public BooleanProperty getBooleanProperty(String name) {
            BooleanProperty booleanProperty = booleanProperties.get(name);
            if (booleanProperty == null) {
                throw new IllegalStateException("No such boolean property: " + name);
            }
            return booleanProperty;
        }

        @Override
        public DoubleProperty getDoubleProperty(String name) {
            DoubleProperty doubleProperty = doubleProperties.get(name);
            if (doubleProperty == null) {
                throw new IllegalStateException("No such double property: " + name);
            }
            return doubleProperty;
        }

        @Override
        public ListProperty getListProperty(String name) {
            ListProperty listProperty = listProperties.get(name);
            if (listProperty == null) {
                throw new IllegalStateException("No such list property: " + name);
            }
            return listProperty;
        }

        private static RuntimeException unexpectedValueType(String propertyName,
                String propertyType, @Nullable Object value) {
            String found = value == null ? "null" : value.getClass().getSimpleName();
            return new IllegalStateException("Unexpected value for " + propertyType + " property "
                    + propertyName + ": " + found);
        }

        private static RuntimeException unexpectedValueTypeForList(String propertyName,
                @Nullable Object value) {
            String found = value == null ? "null" : value.getClass().getSimpleName();
            return new IllegalStateException(
                    "Unexpected value for element of list property " + propertyName + ": " + found);
        }
    }

    private static class StringPropertyImpl implements StringProperty {

        private final String value;

        private StringPropertyImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    private static class BooleanPropertyImpl implements BooleanProperty {

        private final boolean value;

        private BooleanPropertyImpl(boolean value) {
            this.value = value;
        }

        @Override
        public boolean value() {
            return value;
        }
    }

    private static class DoublePropertyImpl implements DoubleProperty {

        private final @Nullable Double value;

        private DoublePropertyImpl(@Nullable Double value) {
            this.value = value;
        }

        @Override
        public @Nullable Double value() {
            return value;
        }
    }

    private static class ListPropertyImpl implements ListProperty {

        private final List<String> value;

        private ListPropertyImpl(List<String> value) {
            this.value = value;
        }

        @Override
        public List<String> value() {
            return value;
        }
    }
}
