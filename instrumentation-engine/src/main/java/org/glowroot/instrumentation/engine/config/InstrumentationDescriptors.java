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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.engine.config.DefaultValue.PropertyValueTypeAdapter;

import static com.google.common.base.Charsets.ISO_8859_1;
import static com.google.common.base.Preconditions.checkNotNull;

public class InstrumentationDescriptors {

    private static final Gson gson;

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapterFactory(new GsonAdaptersInstrumentationDescriptor());
        gsonBuilder.registerTypeAdapterFactory(new GsonAdaptersPropertyDescriptor());
        gsonBuilder.registerTypeAdapterFactory(new GsonAdaptersAdviceConfig());
        gsonBuilder.registerTypeAdapter(DefaultValue.class, new PropertyValueTypeAdapter());
        gsonBuilder.registerTypeAdapterFactory(new EnumTypeAdapterFactory());
        gson = gsonBuilder.create();
    }

    private InstrumentationDescriptors() {}

    public static List<InstrumentationDescriptor> read() throws IOException {
        List<URL> resources = getResources("META-INF/instrumentation.list");
        List<InstrumentationDescriptor> descriptors = Lists.newArrayList();
        for (URL resource : resources) {
            List<String> jsonFiles = Resources.readLines(resource, ISO_8859_1);
            for (String jsonFile : jsonFiles) {
                if (jsonFile.isEmpty()) {
                    continue;
                }
                URL url = InstrumentationDescriptors.class.getResource("/META-INF/" + jsonFile);
                if (url == null) {
                    throw new IllegalStateException("No such resource: /META-INF/" + jsonFile);
                }
                descriptors.add(read(url));
            }
        }
        return checkNotNull(descriptors);
    }

    public static InstrumentationDescriptor read(URL url) throws IOException {
        String json = Resources.toString(url, ISO_8859_1);
        return checkNotNull(gson.fromJson(json, ImmutableInstrumentationDescriptor.class));
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

    private static class EnumTypeAdapterFactory implements TypeAdapterFactory {

        @Override
        public <T> /*@Nullable*/ TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            @SuppressWarnings("unchecked")
            Class<T> rawType = (Class<T>) type.getRawType();
            if (!rawType.isEnum()) {
                return null;
            }
            final Map<String, T> enumConstants = Maps.newHashMap();
            for (T enumConstant : rawType.getEnumConstants()) {
                enumConstants.put(((Enum<?>) enumConstant).name().replace('_', '-')
                        .toLowerCase(Locale.ENGLISH), enumConstant);
            }
            return new TypeAdapter<T>() {

                @Override
                public @Nullable T read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    } else {
                        return enumConstants.get(in.nextString());
                    }
                }

                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    throw new UnsupportedOperationException("This should not be needed");
                }
            };
        }
    }
}
