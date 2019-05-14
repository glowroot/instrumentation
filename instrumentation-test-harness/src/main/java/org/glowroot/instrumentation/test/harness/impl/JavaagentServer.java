/*
 * Copyright 2015-2019 the original author or authors.
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.agent.MainEntryPoint;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class JavaagentServer {

    private static final Logger logger = LoggerFactory.getLogger(JavaagentServer.class);

    private final ServerSocket serverSocket;

    JavaagentServer() throws IOException {
        serverSocket = new ServerSocket(0);
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    void start() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = serverSocket.accept();
                    @SuppressWarnings("resource")
                    ObjectInputStream in = new ObjectInputStreamWithClassLoader(
                            socket.getInputStream(), ClassLoader.getSystemClassLoader());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    while (true) {
                        Command command = (Command) in.readObject();
                        switch (command) {
                            case PING:
                                out.writeObject("ok");
                                break;
                            case SET_INSTRUMENTATION_PROPERTY:
                                String instrumentationId = (String) in.readObject();
                                String propertyName = (String) in.readObject();
                                Object propertyValue = in.readObject();
                                setInstrumentationProperty(instrumentationId, propertyName,
                                        propertyValue);
                                out.writeObject("ok");
                                break;
                            case EXECUTE_APP:
                                String appClassName = (String) in.readObject();
                                Serializable[] args = (Serializable[]) in.readObject();
                                try {
                                    executeApp(appClassName, args);
                                    out.writeObject("ok");
                                } catch (Throwable t) {
                                    logger.error(t.getMessage(), t);
                                    out.writeObject(t);
                                }
                                break;
                            case RESET_INSTRUMENTATION_PROPERTIES:
                                MainEntryPoint.resetInstrumentationProperties();
                                out.writeObject("ok");
                                break;
                            case KILL:
                                kill();
                                out.writeObject("ok");
                                break;
                            default:
                                throw new IllegalStateException("Unexpected command: " + command);
                        }
                    }
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        });
    }

    private void setInstrumentationProperty(String instrumentationId, String propertyName,
            Object propertyValue) {
        if (propertyValue instanceof Boolean) {
            MainEntryPoint.setInstrumentationProperty(instrumentationId, propertyName,
                    (Boolean) propertyValue);
        } else if (propertyValue instanceof Double || propertyValue == null) {
            MainEntryPoint.setInstrumentationProperty(instrumentationId, propertyName,
                    (Double) propertyValue);
        } else if (propertyValue instanceof String) {
            MainEntryPoint.setInstrumentationProperty(instrumentationId, propertyName,
                    (String) propertyValue);
        } else if (propertyValue instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) propertyValue;
            MainEntryPoint.setInstrumentationProperty(instrumentationId, propertyName, list);
        }
    }

    private void executeApp(String appClassName, Serializable[] args) throws Exception {
        Class<?> appClass = Class.forName(appClassName, true, ClassLoader.getSystemClassLoader());
        AppUnderTest app = (AppUnderTest) appClass.getConstructor().newInstance();
        app.executeApp(args);
    }

    private void kill() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // wait a few millis for response to be returned successfully
                    MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                    logger.debug(e.getMessage(), e);
                    Thread.interrupted();
                }
                System.exit(0);
            }
        });
    }

    enum Command {
        PING, SET_INSTRUMENTATION_PROPERTY, EXECUTE_APP, RESET_INSTRUMENTATION_PROPERTIES, KILL
    }
}
