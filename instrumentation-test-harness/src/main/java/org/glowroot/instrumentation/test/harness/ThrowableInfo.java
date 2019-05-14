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
package org.glowroot.instrumentation.test.harness;

import java.io.Serializable;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkNotNull;

// this is needed because some Throwables have de-serialization problems
// (in particular old play 2.x versions)
@Value.Immutable
@SuppressWarnings("serial")
public abstract class ThrowableInfo implements Serializable {

    public abstract Class<?> type();

    public abstract @Nullable String message();

    public abstract StackTraceElement[] stackTrace();

    public abstract @Nullable ThrowableInfo cause();

    public static ThrowableInfo create(Throwable t) {
        ImmutableThrowableInfo.Builder builder = ImmutableThrowableInfo.builder()
                .type(t.getClass())
                .message(t.getMessage())
                .stackTrace(checkNotNull(t.getStackTrace()));
        Throwable cause = t.getCause();
        if (cause != null) {
            builder.cause(create(cause));
        }
        return builder.build();
    }
}
