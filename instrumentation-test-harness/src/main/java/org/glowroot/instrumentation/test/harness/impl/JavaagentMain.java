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
package org.glowroot.instrumentation.test.harness.impl;

import com.google.common.reflect.Reflection;

import org.glowroot.instrumentation.test.harness.Container;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class JavaagentMain {

    public static void main(String[] args) throws Exception {

        // this is needed on Java 9+ now that sun.boot.class.path no longer exists, so that
        // instrumentation config auto complete can find this class in CustomInstrumentationConfigIT
        Reflection.initialize(Container.class);

        JavaagentServer javaagentServer = new JavaagentServer();
        javaagentServer.start();

        int port = Integer.parseInt(args[0]);
        SocketHeartbeat socketHeartbeat = new SocketHeartbeat(port);
        socketHeartbeat.sendJavaagentServerPort(javaagentServer.getPort());
        new Thread(socketHeartbeat).start();

        // non-daemon threads started above keep jvm alive after main returns
        MILLISECONDS.sleep(Long.MAX_VALUE);
    }
}
