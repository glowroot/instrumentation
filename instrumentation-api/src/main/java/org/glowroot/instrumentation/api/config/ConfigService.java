/*
 * Copyright 2011-2019 the original author or authors.
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
package org.glowroot.instrumentation.api.config;

public interface ConfigService {

    /**
     * Registers a listener that will receive a callback when the instrumentation's property values
     * are changed.
     */
    void registerConfigListener(ConfigListener listener);

    /**
     * Returns the {@code String} instrumentation property value with the specified {@code name}.
     * {@code null} is never returned. If there is no {@code String} instrumentation property with
     * the specified {@code name} then the empty string {@code ""} is returned.
     * 
     * Properties are scoped per instrumentation. They are defined in the instrumentation's
     * META-INF/instrumentation.*.json file, and can be modified (assuming they are not marked as
     * hidden) on the configuration page under the instrumentation's configuration section.
     */
    StringProperty getStringProperty(String name);

    /**
     * Returns the {@code boolean} instrumentation property value with the specified {@code name}.
     * If there is no {@code boolean} instrumentation property with the specified {@code name} then
     * {@code false} is returned.
     * 
     * Properties are scoped per instrumentation. They are defined in the instrumentation's
     * META-INF/instrumentation.*.json file, and can be modified (assuming they are not marked as
     * hidden) on the configuration page under the instrumentation's configuration section.
     */
    BooleanProperty getBooleanProperty(String name);

    /**
     * Returns the {@code Double} instrumentation property value with the specified {@code name}. If
     * there is no {@code Double} instrumentation property with the specified {@code name} then
     * {@code null} is returned.
     * 
     * Properties are scoped per instrumentation. They are defined in the instrumentation's
     * META-INF/instrumentation.*.json file, and can be modified (assuming they are not marked as
     * hidden) on the configuration page under the instrumentation's configuration section.
     */
    DoubleProperty getDoubleProperty(String name);

    /**
     * Returns the {@code List} instrumentation property value with the specified {@code name}.
     * {@code null} is never returned. If there is no {@code String} instrumentation property with
     * the specified {@code name} then the empty list is returned.
     * 
     * Properties are scoped per instrumentation. They are defined in the instrumentation's
     * META-INF/instrumentation.*.json file, and can be modified (assuming they are not marked as
     * hidden) on the configuration page under the instrumentation's configuration section.
     */
    ListProperty getListProperty(String name);
}
