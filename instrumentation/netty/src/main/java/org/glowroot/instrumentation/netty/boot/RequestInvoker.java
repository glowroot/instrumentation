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
package org.glowroot.instrumentation.netty.boot;

import java.lang.reflect.Method;

import org.glowroot.instrumentation.api.ClassInfo;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.util.Reflection;
import org.glowroot.instrumentation.netty.Netty3xInstrumentation.HttpRequestShim;

public class RequestInvoker implements Getter<HttpRequestShim> {

    private static final Logger logger = Logger.getLogger(RequestInvoker.class);

    private final @Nullable Method getHeaderMethod;
    private final @Nullable Method headersMethod;
    private final @Nullable Method headersGetMethod;

    public RequestInvoker(ClassInfo classInfo) {
        Class<?> httpRequestClass = Reflection.getClassWithWarnIfNotFound(
                "org.jboss.netty.handler.codec.http.HttpRequest", classInfo.getLoader());
        Class<?> httpHeadersClass = Reflection
                .getClass("org.jboss.netty.handler.codec.http.HttpHeaders", classInfo.getLoader());
        headersMethod = Reflection.getMethod(httpRequestClass, "headers");
        headersGetMethod = Reflection.getMethod(httpHeadersClass, "get", String.class);
        if (headersMethod != null && headersGetMethod == null) {
            logger.warn("HttpHeaders.get() not found");
        }
        // this is needed for Netty prior to 3.10.0
        getHeaderMethod = Reflection.getMethod(httpRequestClass, "getHeader", String.class);

        if (getHeaderMethod == null && headersMethod == null) {
            logger.warn("both HttpRequest.headers() and HttpRequest.getHeader() not found");
        }
    }

    @Nullable
    public String getHeader(Object request, String name) {
        if (getHeaderMethod != null) {
            return Reflection.invoke(getHeaderMethod, request, name);
        } else if (headersMethod != null) {
            Object headers = Reflection.invoke(headersMethod, request);
            if (headers == null) {
                return null;
            } else {
                return Reflection.invoke(headersGetMethod, headers, name);
            }
        } else {
            return null;
        }
    }

    @Override
    @Nullable
    public String get(HttpRequestShim carrier, String key) {
        return getHeader(carrier, key);
    }
}
