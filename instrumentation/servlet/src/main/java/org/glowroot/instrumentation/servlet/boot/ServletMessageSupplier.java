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
package org.glowroot.instrumentation.servlet.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.glowroot.instrumentation.api.Message;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.ThreadContext.ServletRequestInfo;
import org.glowroot.instrumentation.api.checker.MonotonicNonNull;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.checker.RequiresNonNull;

// this class is thread-safe (unlike other MessageSuppliers) since it gets passed around to
// auxiliary thread contexts for handling async servlets
public class ServletMessageSupplier extends MessageSupplier implements ServletRequestInfo {

    private static final String MASK_TEXT = "****";

    private final String requestMethod;
    private final String requestContextPath;
    private final String requestServletPath;
    private final @Nullable String requestPathInfo;
    private final String requestUri;
    private final @Nullable String requestQueryString;

    private volatile @MonotonicNonNull Map<String, Object> requestParameters;

    private final Map<String, Object> requestHeaders;

    private final Map<String, String> requestCookies;

    private final @Nullable RequestHostAndPortDetail requestHostAndPortDetail;

    private volatile int responseCode;

    private final ResponseHeaderComponent responseHeaderComponent = new ResponseHeaderComponent();

    // session attributes may not be thread safe, so they must be converted to Strings
    // within the request processing thread, which can then be safely read by the trace storage
    // thread (and live viewing thread also)
    // the initial value map contains the session attributes as they were present at the beginning
    // of the request
    private final Map<String, String> sessionAttributeInitialValueMap;

    private volatile @MonotonicNonNull ConcurrentMap<String, String> sessionAttributeUpdatedValueMap;

    private @Nullable List<String> jaxRsParts;

    public ServletMessageSupplier(String requestMethod, String requestContextPath,
            String requestServletPath, @Nullable String requestPathInfo, String requestUri,
            @Nullable String requestQueryString, Map<String, Object> requestHeaders,
            Map<String, String> requestCookies,
            @Nullable RequestHostAndPortDetail requestHostAndPortDetail,
            Map<String, String> sessionAttributeMap) {
        this.requestMethod = requestMethod;
        this.requestContextPath = requestContextPath;
        this.requestServletPath = requestServletPath;
        this.requestPathInfo = requestPathInfo;
        this.requestUri = requestUri;
        this.requestQueryString = requestQueryString;
        this.requestHeaders = requestHeaders;
        this.requestCookies = requestCookies;
        this.requestHostAndPortDetail = requestHostAndPortDetail;
        this.sessionAttributeInitialValueMap = sessionAttributeMap;
    }

    @Override
    public Message get() {
        List<Pattern> maskPatterns = ServletInstrumentationProperties.maskRequestParameters();
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("Request http method", requestMethod);
        detail.put("Request uri", requestUri);
        String maskedRequestQueryString = maskRequestQueryString(requestQueryString, maskPatterns);
        if (maskedRequestQueryString != null) {
            // including empty query string since that means request ended with ?
            detail.put("Request query string", maskedRequestQueryString);
        }
        Map<String, Object> maskedRequestParameters =
                maskRequestParameters(requestParameters, maskPatterns);
        if (maskedRequestParameters != null && !maskedRequestParameters.isEmpty()) {
            detail.put("Request parameters", maskedRequestParameters);
        }
        if (!requestHeaders.isEmpty()) {
            detail.put("Request headers", requestHeaders);
        }
        if (!requestCookies.isEmpty()) {
            detail.put("Request cookies", requestCookies);
        }
        if (requestHostAndPortDetail != null) {
            if (requestHostAndPortDetail.remoteAddress != null) {
                detail.put("Request remote address", requestHostAndPortDetail.remoteAddress);
            }
            if (requestHostAndPortDetail.remoteHostname != null) {
                detail.put("Request remote hostname", requestHostAndPortDetail.remoteHostname);
            }
            if (requestHostAndPortDetail.remotePort != RequestHostAndPortDetail.UNSET) {
                detail.put("Request remote port", requestHostAndPortDetail.remotePort);
            }
            if (requestHostAndPortDetail.localAddress != null) {
                detail.put("Request local address", requestHostAndPortDetail.localAddress);
            }
            if (requestHostAndPortDetail.localHostname != null) {
                detail.put("Request local hostname", requestHostAndPortDetail.localHostname);
            }
            if (requestHostAndPortDetail.localPort != RequestHostAndPortDetail.UNSET) {
                detail.put("Request local port", requestHostAndPortDetail.localPort);
            }
            if (requestHostAndPortDetail.serverHostname != null) {
                detail.put("Request server hostname", requestHostAndPortDetail.serverHostname);
            }
            if (requestHostAndPortDetail.serverPort != RequestHostAndPortDetail.UNSET) {
                detail.put("Request server port", requestHostAndPortDetail.serverPort);
            }
            if (requestHostAndPortDetail.scheme != null) {
                detail.put("Request scheme", requestHostAndPortDetail.scheme);
            }
        }
        if (responseCode != 0) {
            detail.put("Response code", responseCode);
        }
        Map<String, Object> responseHeaderStrings = responseHeaderComponent.getMapOfStrings();
        if (!responseHeaderStrings.isEmpty()) {
            detail.put("Response headers", responseHeaderStrings);
        }
        addSessionAttributeDetail(detail);
        return Message.create(requestUri, detail);
    }

    @Override
    public String getMethod() {
        return requestMethod;
    }

    @Override
    public String getContextPath() {
        return requestContextPath;
    }

    @Override
    public String getServletPath() {
        return requestServletPath;
    }

    @Override
    public @Nullable String getPathInfo() {
        return requestPathInfo;
    }

    @Override
    public String getUri() {
        return requestUri;
    }

    @Override
    public void addJaxRsPart(String part) {
        if (jaxRsParts == null) {
            jaxRsParts = new ArrayList<String>();
        }
        jaxRsParts.add(part);
    }

    @Override
    public List<String> getJaxRsParts() {
        return jaxRsParts == null ? Collections.<String>emptyList() : jaxRsParts;
    }

    public boolean isRequestParametersCaptured() {
        return requestParameters != null;
    }

    public void setCaptureRequestParameters(Map<String, Object> requestParameters) {
        this.requestParameters = requestParameters;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public void setResponseHeader(String name, String value) {
        responseHeaderComponent.setHeader(name, value);
    }

    public void setResponseDateHeader(String name, long date) {
        responseHeaderComponent.setHeader(name, date);
    }

    public void setResponseIntHeader(String name, int value) {
        responseHeaderComponent.setHeader(name, value);
    }

    public void setResponseLongHeader(String name, long value) {
        responseHeaderComponent.setHeader(name, value);
    }

    public void addResponseHeader(String name, String value) {
        responseHeaderComponent.addHeader(name, value);
    }

    public void addResponseDateHeader(String name, long date) {
        responseHeaderComponent.addHeader(name, date);
    }

    public void addResponseIntHeader(String name, int value) {
        responseHeaderComponent.addHeader(name, value);
    }

    public void putSessionAttributeChangedValue(String attributeName,
            @Nullable String attributeValue) {
        if (sessionAttributeUpdatedValueMap == null) {
            sessionAttributeUpdatedValueMap = new ConcurrentHashMap<String, String>();
        }
        if (attributeValue == null) {
            // ConcurrentHashMap does not allow null values
            sessionAttributeUpdatedValueMap.remove(attributeName);
        } else {
            sessionAttributeUpdatedValueMap.put(attributeName, attributeValue);
        }
    }

    private void addSessionAttributeDetail(Map<String, Object> detail) {
        if (!sessionAttributeInitialValueMap.isEmpty()) {
            if (sessionAttributeUpdatedValueMap == null) {
                // session attributes were captured at the beginning of the request, and no session
                // attributes were updated mid-request
                detail.put("Session attributes", sessionAttributeInitialValueMap);
            } else {
                // some session attributes were updated mid-request
                addMidRequestSessionAttributeDetail(detail);
            }
        } else if (sessionAttributeUpdatedValueMap != null) {
            // no session attributes were available at the beginning of the request, and session
            // attributes were updated mid-request
            detail.put("Session attributes (updated during this request)",
                    sessionAttributeUpdatedValueMap);
        } else {
            // both initial and updated value maps are null so there is nothing to add to the
            // detail map
        }
    }

    @RequiresNonNull("sessionAttributeUpdatedValueMap")
    private void addMidRequestSessionAttributeDetail(Map<String, Object> detail) {
        Map<String, /*@Nullable*/ Object> sessionAttributeInitialValuePlusMap =
                new HashMap<String, /*@Nullable*/ Object>();
        sessionAttributeInitialValuePlusMap.putAll(sessionAttributeInitialValueMap);
        // add empty values into initial values for any updated attributes that are not
        // already present in initial values nested detail map
        for (Map.Entry<String, String> entry : sessionAttributeUpdatedValueMap.entrySet()) {
            if (!sessionAttributeInitialValueMap.containsKey(entry.getKey())) {
                sessionAttributeInitialValuePlusMap.put(entry.getKey(), null);
            }
        }
        detail.put("Session attributes (at beginning of this request)",
                sessionAttributeInitialValuePlusMap);
        detail.put("Session attributes (updated during this request)",
                sessionAttributeUpdatedValueMap);
    }

    static @Nullable String maskRequestQueryString(@Nullable String requestQueryString,
            List<Pattern> maskPatterns) {
        if (requestQueryString == null) {
            return null;
        }
        if (maskPatterns.isEmpty()) {
            return requestQueryString;
        }
        StringBuilder sb = new StringBuilder(requestQueryString.length());
        boolean existMaskedParameters = false;
        int keyStartIndex = 0;
        boolean inMaskedValue = false;
        for (int i = 0; i < requestQueryString.length(); i++) {
            char c = requestQueryString.charAt(i);
            switch (c) {
                case '&':
                    sb.append('&');
                    keyStartIndex = sb.length();
                    inMaskedValue = false;
                    break;
                case '=':
                    if (keyStartIndex == -1) {
                        // not in key
                        if (!inMaskedValue) {
                            sb.append(c);
                        }
                    } else {
                        String key = sb.substring(keyStartIndex, sb.length());
                        sb.append('=');
                        // converted to lower case for case-insensitive matching
                        // (patterns are lower case)
                        String keyLowerCase = key.toLowerCase(Locale.ENGLISH);
                        if (Patterns.matchesOneOf(keyLowerCase, maskPatterns)) {
                            inMaskedValue = true;
                            sb.append(MASK_TEXT);
                            existMaskedParameters = true;
                        }
                        keyStartIndex = -1;
                    }
                    break;
                default:
                    if (!inMaskedValue) {
                        sb.append(c);
                    }
            }
        }
        if (existMaskedParameters) {
            return sb.toString();
        } else {
            // save the expense of toString() in common case
            return requestQueryString;
        }
    }

    private static @Nullable Map<String, Object> maskRequestParameters(
            @Nullable Map<String, Object> requestParameters, List<Pattern> maskPatterns) {
        if (requestParameters == null) {
            return null;
        }
        if (maskPatterns.isEmpty()) {
            return requestParameters;
        }
        Map<String, Object> maskedRequestParameters = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : requestParameters.entrySet()) {
            String name = entry.getKey();
            // converted to lower case for case-insensitive matching (patterns are lower case)
            String keyLowerCase = name.toLowerCase(Locale.ENGLISH);
            if (Patterns.matchesOneOf(keyLowerCase, maskPatterns)) {
                maskedRequestParameters.put(name, MASK_TEXT);
            } else {
                maskedRequestParameters.put(name, entry.getValue());
            }
        }
        return maskedRequestParameters;
    }
}
