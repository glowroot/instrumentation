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
package org.glowroot.instrumentation.test.harness.agent;

import java.util.List;
import java.util.Map;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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

import static com.google.common.base.Preconditions.checkNotNull;

public class MutableConfigServiceFactory implements ConfigServiceFactory {

    private final Map<String, InstrumentationDescriptor> instrumentationDescriptors;

    private final LoadingCache<String, MutableConfigService> configServices =
            CacheBuilder.newBuilder().build(new CacheLoader<String, MutableConfigService>() {
                @Override
                public MutableConfigService load(String instrumentationId) {
                    InstrumentationDescriptor descriptor =
                            instrumentationDescriptors.get(instrumentationId);
                    if (descriptor == null) {
                        throw new IllegalStateException(
                                "Instrumentation id not found: " + instrumentationId);
                    }
                    return new MutableConfigService(descriptor.properties());
                }
            });

    public MutableConfigServiceFactory(List<InstrumentationDescriptor> descriptors) {
        instrumentationDescriptors = Maps.newHashMap();
        for (InstrumentationDescriptor descriptor : descriptors) {
            instrumentationDescriptors.put(descriptor.id(), descriptor);
        }
    }

    @Override
    public ConfigService create(String instrumentationId) {
        return configServices.getUnchecked(instrumentationId);
    }

    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            boolean propertyValue) {
        configServices.getUnchecked(instrumentationId).setProperty(propertyName, propertyValue);
    }

    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            @Nullable Double propertyValue) {
        configServices.getUnchecked(instrumentationId).setProperty(propertyName, propertyValue);
    }

    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            String propertyValue) {
        configServices.getUnchecked(instrumentationId).setProperty(propertyName, propertyValue);
    }

    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            List<String> propertyValue) {
        configServices.getUnchecked(instrumentationId).setProperty(propertyName, propertyValue);
    }

    public void resetConfig() {
        for (MutableConfigService configService : configServices.asMap().values()) {
            configService.resetConfig();
        }
    }

    private static class MutableConfigService implements ConfigService {

        private final Map<String, StringPropertyImpl> stringProperties;
        private final Map<String, BooleanPropertyImpl> booleanProperties;
        private final Map<String, DoublePropertyImpl> doubleProperties;
        private final Map<String, ListPropertyImpl> listProperties;

        private final List<PropertyDescriptor> propertyDescriptors;

        private final List<ConfigListener> listeners = Lists.newArrayList();

        private MutableConfigService(List<PropertyDescriptor> propertyDescriptors) {
            Map<String, StringPropertyImpl> stringProperties = Maps.newHashMap();
            Map<String, BooleanPropertyImpl> booleanProperties = Maps.newHashMap();
            Map<String, DoublePropertyImpl> doubleProperties = Maps.newHashMap();
            Map<String, ListPropertyImpl> listProperties = Maps.newHashMap();
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                String name = propertyDescriptor.name();
                Object value = propertyDescriptor.getValidatedNonNullDefaultValue().value();
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
                        if (value == null || value instanceof Double) {
                            doubleProperties.put(name, new DoublePropertyImpl((Double) value));
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
            this.propertyDescriptors = propertyDescriptors;
        }

        public void resetConfig() {
            // properties have already been validated during construction
            for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
                String name = propertyDescriptor.name();
                Object value = propertyDescriptor.getValidatedNonNullDefaultValue().value();
                switch (propertyDescriptor.type()) {
                    case STRING:
                        checkNotNull(value);
                        checkNotNull(stringProperties.get(name)).value = (String) value;
                        break;
                    case BOOLEAN:
                        checkNotNull(value);
                        checkNotNull(booleanProperties.get(name)).value = (Boolean) value;
                        break;
                    case DOUBLE:
                        checkNotNull(doubleProperties.get(name)).value = (Double) value;
                        break;
                    case LIST:
                        checkNotNull(value);
                        List<String> stringList = Lists.newArrayList();
                        for (Object val : (List<?>) value) {
                            stringList.add((String) checkNotNull(val));
                        }
                        checkNotNull(listProperties.get(name)).value = stringList;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected property descriptor type: "
                                + propertyDescriptor.type());
                }
            }
            callConfigListeners();
        }

        @Override
        public void registerConfigListener(ConfigListener listener) {
            listeners.add(listener);
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

        private void setProperty(String propertyName, boolean propertyValue) {
            BooleanPropertyImpl booleanProperty = booleanProperties.get(propertyName);
            if (booleanProperty == null) {
                throw new IllegalStateException(
                        "Unexpected boolean property name: " + propertyName);
            }
            booleanProperty.value = propertyValue;
            callConfigListeners();
        }

        public void setProperty(String propertyName, @Nullable Double propertyValue) {
            DoublePropertyImpl doubleProperty = doubleProperties.get(propertyName);
            if (doubleProperty == null) {
                throw new IllegalStateException("Unexpected double property name: " + propertyName);
            }
            doubleProperty.value = propertyValue;
            callConfigListeners();
        }

        public void setProperty(String propertyName, String propertyValue) {
            StringPropertyImpl stringProperty = stringProperties.get(propertyName);
            if (stringProperty == null) {
                throw new IllegalStateException("Unexpected string property name: " + propertyName);
            }
            stringProperty.value = propertyValue;
            callConfigListeners();
        }

        public void setProperty(String propertyName, List<String> propertyValue) {
            ListPropertyImpl listProperty = listProperties.get(propertyName);
            if (listProperty == null) {
                throw new IllegalStateException("Unexpected list property name: " + propertyName);
            }
            listProperty.value = propertyValue;
            callConfigListeners();
        }

        private void callConfigListeners() {
            for (ConfigListener listener : listeners) {
                listener.onChange();
            }
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

        private volatile String value;

        private StringPropertyImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    private static class BooleanPropertyImpl implements BooleanProperty {

        private volatile boolean value;

        private BooleanPropertyImpl(boolean value) {
            this.value = value;
        }

        @Override
        public boolean value() {
            return value;
        }
    }

    private static class DoublePropertyImpl implements DoubleProperty {

        private volatile @Nullable Double value;

        private DoublePropertyImpl(@Nullable Double value) {
            this.value = value;
        }

        @Override
        public @Nullable Double value() {
            return value;
        }
    }

    private static class ListPropertyImpl implements ListProperty {

        private volatile List<String> value;

        private ListPropertyImpl(List<String> value) {
            this.value = value;
        }

        @Override
        public List<String> value() {
            return value;
        }
    }
}
