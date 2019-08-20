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
package org.glowroot.instrumentation.log4j.boot;

public class Log4j1xLevel {

    // constants from org.apache.log4j.Priority
    public static final int FATAL = 50000;
    public static final int ERROR = 40000;
    public static final int WARN = 30000;
    public static final int INFO = 20000;
    public static final int DEBUG = 10000;

}
