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

public class Log4j2xLevel {

    // constants from org.apache.logging.log4j.spi.StandardLevel
    public static final int FATAL = 100;
    public static final int ERROR = 200;
    public static final int WARN = 300;
    public static final int INFO = 400;
    public static final int DEBUG = 500;
    public static final int TRACE = 600;

}
