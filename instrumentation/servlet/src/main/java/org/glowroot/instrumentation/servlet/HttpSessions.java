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
package org.glowroot.instrumentation.servlet;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.util.Beans;
import org.glowroot.instrumentation.api.util.ImmutableMap;
import org.glowroot.instrumentation.servlet.boot.ServletInstrumentationProperties;
import org.glowroot.instrumentation.servlet.boot.ServletInstrumentationProperties.SessionAttributePath;
import org.glowroot.instrumentation.servlet.boot.Strings;

class HttpSessions {

    private HttpSessions() {}

    static Map<String, String> getSessionAttributes(HttpSession session) {
        List<SessionAttributePath> attributePaths =
                ServletInstrumentationProperties.captureSessionAttributePaths();
        if (attributePaths.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> captureMap = new HashMap<String, String>();
        // dump only http session attributes in list
        for (SessionAttributePath attributePath : attributePaths) {
            if (attributePath.isAttributeNameWildcard()) {
                captureAllSessionAttributes(session, captureMap);
            } else if (attributePath.isWildcard()) {
                captureWildcardPath(session, captureMap, attributePath);
            } else {
                captureNonWildcardPath(session, captureMap, attributePath);
            }
        }
        return ImmutableMap.copyOf(captureMap);
    }

    static @Nullable Object getSessionAttribute(HttpSession session,
            SessionAttributePath attributePath) {
        if (attributePath.isSessionId()) {
            return session.getId();
        }
        Object attributeValue = session.getAttribute(attributePath.getAttributeName());
        return getSessionAttribute(attributeValue, attributePath);
    }

    static @Nullable Object getSessionAttribute(@Nullable Object attributeValue,
            SessionAttributePath attributePath) {
        List<String> nestedPath = attributePath.getNestedPath();
        if (nestedPath.isEmpty()) {
            return attributeValue;
        } else {
            try {
                return Beans.value(attributeValue, nestedPath);
            } catch (Exception e) {
                return "<could not access: " + e + ">";
            }
        }
    }

    private static void captureAllSessionAttributes(HttpSession session,
            Map<String, String> captureMap) {
        Enumeration<? extends /*@Nullable*/ Object> e = session.getAttributeNames();
        if (e == null) {
            return;
        }
        while (e.hasMoreElements()) {
            String attributeName = (String) e.nextElement();
            if (attributeName == null) {
                continue;
            }
            if (attributeName.equals(ServletInstrumentationProperties.HTTP_SESSION_ID_ATTR)) {
                captureMap.put(attributeName, Strings.nullToEmpty(session.getId()));
                continue;
            }
            Object value = session.getAttribute(attributeName);
            if (value == null) {
                // value shouldn't be null, but its (remotely) possible that a concurrent
                // request for the same session just removed the attribute
                continue;
            }
            captureMap.put(attributeName, Strings.nullToEmpty(value.toString()));
        }
    }

    private static void captureWildcardPath(HttpSession session, Map<String, String> captureMap,
            SessionAttributePath attributePath) {
        Object value = getSessionAttribute(session, attributePath);
        if (value instanceof Map<?, ?>) {
            String fullPath = attributePath.getFullPath();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                Object val = entry.getValue();
                captureMap.put(fullPath + "." + entry.getKey(),
                        val == null ? "" : Strings.nullToEmpty(val.toString()));
            }
        } else if (value != null) {
            String fullPath = attributePath.getFullPath();
            for (Map.Entry<String, String> entry : Beans.propertiesAsText(value).entrySet()) {
                captureMap.put(fullPath + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    private static void captureNonWildcardPath(HttpSession session, Map<String, String> captureMap,
            SessionAttributePath attributePath) {
        Object value = getSessionAttribute(session, attributePath);
        if (value != null) {
            captureMap.put(attributePath.getFullPath(), Strings.nullToEmpty(value.toString()));
        }
    }
}
