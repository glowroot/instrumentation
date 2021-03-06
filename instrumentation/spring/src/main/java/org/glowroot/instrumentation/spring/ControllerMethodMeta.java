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
package org.glowroot.instrumentation.spring;

import org.glowroot.instrumentation.api.MethodInfo;

public class ControllerMethodMeta {

    private final String controllerClassName;
    private final String methodName;

    private final String altTransactionName;

    public ControllerMethodMeta(MethodInfo methodInfo) {
        controllerClassName = methodInfo.getDeclaringClassName();
        methodName = methodInfo.getName();
        altTransactionName = getSimpleName(controllerClassName) + "#" + methodName;
    }

    String getControllerClassName() {
        return controllerClassName;
    }

    String getMethodName() {
        return methodName;
    }

    String getAltTransactionName() {
        return altTransactionName;
    }

    private static String getSimpleName(String className) {
        return substringAfterLast(substringAfterLast(className, '.'), '$');
    }

    private static String substringAfterLast(String str, char c) {
        int index = str.lastIndexOf(c);
        if (index == -1) {
            return str;
        } else {
            return str.substring(index + 1);
        }
    }
}
