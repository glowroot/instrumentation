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
package org.glowroot.instrumentation.servlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.util.ImmutableList;
import org.glowroot.instrumentation.api.util.ImmutableMap;
import org.glowroot.instrumentation.servlet.boot.Patterns;
import org.glowroot.instrumentation.servlet.boot.RequestHostAndPortDetail;
import org.glowroot.instrumentation.servlet.boot.RequestInvoker;
import org.glowroot.instrumentation.servlet.boot.ServletInstrumentationProperties;
import org.glowroot.instrumentation.servlet.boot.Strings;

// shallow copies are necessary because request may not be thread safe, which may affect ability
// to see detail from active traces
//
// shallow copies are also necessary because servlet container may clear out the objects after the
// request is complete (e.g. tomcat does this) in order to reuse them, in which case this detail
// would need to be captured synchronously at end of request anyways (although then it could be
// captured only if trace met threshold for storage...)
public class DetailCapture {

    private DetailCapture() {}

    static Map<String, Object> captureRequestParameters(
            Map</*@Nullable*/ String, ?> requestParameters) {
        List<Pattern> capturePatterns = ServletInstrumentationProperties.captureRequestParameters();
        Map<String, Object> map = new HashMap<String, Object>();
        for (Map.Entry</*@Nullable*/ String, ?> entry : requestParameters.entrySet()) {
            String name = entry.getKey();
            if (name == null) {
                continue;
            }
            // converted to lower case for case-insensitive matching (patterns are lower case)
            String keyLowerCase = name.toLowerCase(Locale.ENGLISH);
            if (!Patterns.matchesOneOf(keyLowerCase, capturePatterns)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String[]) {
                set(map, name, (String[]) value);
            }
        }
        return ImmutableMap.copyOf(map);
    }

    static Map<String, Object> captureRequestParameters(HttpServletRequest request) {
        Enumeration<? extends /*@Nullable*/ Object> e = request.getParameterNames();
        if (e == null) {
            return Collections.emptyMap();
        }
        List<Pattern> capturePatterns = ServletInstrumentationProperties.captureRequestParameters();
        List<Pattern> maskPatterns = ServletInstrumentationProperties.maskRequestParameters();
        Map<String, Object> map = new HashMap<String, Object>();
        while (e.hasMoreElements()) {
            Object nameObj = e.nextElement();
            if (nameObj == null) {
                continue;
            }
            if (!(nameObj instanceof String)) {
                continue;
            }
            String name = (String) nameObj;
            // converted to lower case for case-insensitive matching (patterns are lower case)
            String keyLowerCase = name.toLowerCase(Locale.ENGLISH);
            if (!Patterns.matchesOneOf(keyLowerCase, capturePatterns)) {
                continue;
            }
            if (Patterns.matchesOneOf(keyLowerCase, maskPatterns)) {
                map.put(name, "****");
                continue;
            }
            @Nullable
            String[] values = request.getParameterValues(name);
            if (values != null) {
                set(map, name, values);
            }
        }
        return ImmutableMap.copyOf(map);
    }

    static Map<String, Object> captureRequestHeaders(HttpServletRequest request) {
        List<Pattern> capturePatterns = ServletInstrumentationProperties.captureRequestHeaders();
        if (capturePatterns.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> requestHeaders = new HashMap<String, Object>();
        Enumeration</*@Nullable*/ String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return Collections.emptyMap();
        }
        for (Enumeration</*@Nullable*/ String> e = headerNames; e.hasMoreElements();) {
            String name = e.nextElement();
            if (name == null) {
                continue;
            }
            // converted to lower case for case-insensitive matching (patterns are lower case)
            String keyLowerCase = name.toLowerCase(Locale.ENGLISH);
            if (!Patterns.matchesOneOf(keyLowerCase, capturePatterns)) {
                continue;
            }
            Enumeration</*@Nullable*/ String> values = request.getHeaders(name);
            if (values != null) {
                captureRequestHeader(name, values, requestHeaders);
            }
        }
        return ImmutableMap.copyOf(requestHeaders);
    }

    static Map<String, String> captureRequestCookies(HttpServletRequest request) {
        List<Pattern> capturePatterns = ServletInstrumentationProperties.captureRequestCookies();
        if (capturePatterns.isEmpty()) {
            return Collections.emptyMap();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Collections.emptyMap();
        }
        Map<String, String> requestCookies = new HashMap<String, String>();
        for (Cookie cookie : cookies) {
            String name = cookie.getName();
            if (name == null) {
                continue;
            }
            // cookie names are case sensitive
            if (!Patterns.matchesOneOf(name, capturePatterns)) {
                continue;
            }
            requestCookies.put(name, cookie.getValue());
        }
        return ImmutableMap.copyOf(requestCookies);
    }

    static @Nullable RequestHostAndPortDetail captureRequestHostAndPortDetail(
            HttpServletRequest request, RequestInvoker requestInvoker) {
        if (ServletInstrumentationProperties.captureSomeRequestHostAndPortDetail()) {
            RequestHostAndPortDetail requestHostAndPortDetail = new RequestHostAndPortDetail();
            if (ServletInstrumentationProperties.captureRequestRemoteAddress()) {
                requestHostAndPortDetail.remoteAddress = request.getRemoteAddr();
            }
            if (ServletInstrumentationProperties.captureRequestRemoteHostname()) {
                requestHostAndPortDetail.remoteHostname = request.getRemoteHost();
            }
            if (ServletInstrumentationProperties.captureRequestRemotePort()) {
                requestHostAndPortDetail.remotePort = requestInvoker.getRemotePort(request);
            }
            if (ServletInstrumentationProperties.captureRequestLocalAddress()) {
                requestHostAndPortDetail.localAddress = requestInvoker.getLocalAddr(request);
            }
            if (ServletInstrumentationProperties.captureRequestLocalHostname()) {
                requestHostAndPortDetail.localHostname = requestInvoker.getLocalName(request);
            }
            if (ServletInstrumentationProperties.captureRequestLocalPort()) {
                requestHostAndPortDetail.localPort = requestInvoker.getLocalPort(request);
            }
            if (ServletInstrumentationProperties.captureRequestServerHostname()) {
                requestHostAndPortDetail.serverHostname = request.getServerName();
            }
            if (ServletInstrumentationProperties.captureRequestServerPort()) {
                requestHostAndPortDetail.serverPort = request.getServerPort();
            }
            if (ServletInstrumentationProperties.captureRequestScheme()) {
                requestHostAndPortDetail.scheme = request.getScheme();
            }
            return requestHostAndPortDetail;
        } else {
            // optimized for common case
            return null;
        }
    }

    private static void set(Map<String, Object> map, String name, /*@Nullable*/ String[] values) {
        if (values.length == 1) {
            String value = values[0];
            if (value != null) {
                map.put(name, value);
            }
        } else {
            List</*@Nullable*/ String> list =
                    new ArrayList</*@Nullable*/ String>(values.length);
            Collections.addAll(list, values);
            map.put(name, list);
        }
    }

    private static void captureRequestHeader(String name, Enumeration</*@Nullable*/ String> values,
            Map<String, Object> requestHeaders) {
        if (!values.hasMoreElements()) {
            requestHeaders.put(name, "");
        } else {
            String value = values.nextElement();
            if (!values.hasMoreElements()) {
                requestHeaders.put(name, Strings.nullToEmpty(value));
            } else {
                List<String> list = new ArrayList<String>();
                list.add(Strings.nullToEmpty(value));
                while (values.hasMoreElements()) {
                    list.add(Strings.nullToEmpty(values.nextElement()));
                }
                requestHeaders.put(name, ImmutableList.copyOf(list));
            }
        }
    }
}
