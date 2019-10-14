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
package org.glowroot.instrumentation.engine.config;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Value.Immutable
public abstract class PropertyDescriptor {

    private static final Logger logger = LoggerFactory.getLogger(PropertyDescriptor.class);

    public abstract String name();

    public abstract PropertyType type();

    public abstract @Nullable DefaultValue defaultValue();

    public abstract String label();

    @Value.Default
    public String checkboxLabel() {
        return "";
    }

    @Value.Default
    public String description() {
        return "";
    }

    public DefaultValue getValidatedNonNullDefaultValue() {
        DefaultValue defaultValue = defaultValue();
        if (defaultValue == null) {
            return getDefaultValue(type());
        }
        Object value = defaultValue.value();
        if (value == null) {
            // this actually shouldn't occur since jackson unmarshals null defaultValue as null
            // as opposed to new PropertyValue(null)
            return getDefaultValue(type());
        }
        if (isValidType(value, type())) {
            return new DefaultValue(value);
        } else {
            logger.warn("invalid default value for instrumentation property: {}", name());
            return getDefaultValue(type());
        }
    }

    public static DefaultValue getDefaultValue(PropertyType type) {
        switch (type) {
            case BOOLEAN:
                return new DefaultValue(false);
            case DOUBLE:
                return new DefaultValue(null);
            case STRING:
                return new DefaultValue("");
            case LIST:
                return new DefaultValue(ImmutableList.of());
            default:
                throw new AssertionError("Unexpected property type: " + type);
        }
    }

    public static boolean isValidType(Object value, PropertyType type) {
        switch (type) {
            case BOOLEAN:
                return value instanceof Boolean;
            case STRING:
                return value instanceof String;
            case DOUBLE:
                return value instanceof Double;
            case LIST:
                return value instanceof List;
            default:
                throw new AssertionError("Unexpected property type: " + type);
        }
    }
}
