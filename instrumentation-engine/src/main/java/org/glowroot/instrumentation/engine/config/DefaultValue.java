/*
 * Copyright 2014-2019 the original author or authors.
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
import java.util.List;

import com.google.common.collect.Lists;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.checkerframework.checker.nullness.qual.Nullable;

public class DefaultValue {

    // can be boolean, @Nullable Double, @NonNull String or List
    private final @Nullable Object value;

    DefaultValue(@Nullable Object value) {
        this.value = value;
    }

    public @Nullable Object value() {
        return value;
    }

    public enum PropertyType {
        STRING, BOOLEAN, DOUBLE, LIST
    }

    static class PropertyValueTypeAdapter extends TypeAdapter<DefaultValue> {

        @Override
        public DefaultValue read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            switch (token) {
                case BOOLEAN:
                    return new DefaultValue(in.nextBoolean());
                case NUMBER:
                    return new DefaultValue(in.nextDouble());
                case STRING:
                    return new DefaultValue(in.nextString());
                case BEGIN_ARRAY:
                    List<String> list = Lists.newArrayList();
                    in.beginArray();
                    while (in.peek() != JsonToken.END_ARRAY) {
                        list.add(in.nextString());
                    }
                    in.endArray();
                    return new DefaultValue(list);
                default:
                    throw new AssertionError("Unexpected json type: " + token);
            }
        }

        @Override
        public void write(JsonWriter out, DefaultValue value) {
            throw new UnsupportedOperationException("This should not be needed");
        }
    }
}
