/*
 * Copyright 2016-2019 the original author or authors.
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
package org.glowroot.instrumentation.api;

/**
 * The detail map can contain only {@link String}, {@link Number}, {@link Boolean} and null values.
 * It can also contain nested lists of {@link String}, {@link Number}, {@link Boolean} and null
 * values (in particular, lists elements cannot other lists or maps). And it can contain any level
 * of nested maps whose keys are {@link String} and whose values are one of the above types
 * (including lists). The detail map cannot have null keys.
 * 
 * Lists are supported to simulate multimaps, e.g. for http request parameters and http headers,
 * both of which can have multiple values for the same key.
 * 
 * The detail map does not need to be thread safe as long as it is only instantiated in response to
 * either MessageSupplier.get() or Message.getDetail() which are called by the thread that needs the
 * map.
 */
public abstract class QueryMessage {

}
