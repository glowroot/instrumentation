/*
 * Copyright 2015-2018 the original author or authors.
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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.test.harness.IncomingSpan;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class TraceCollector {

    private static final Logger logger = LoggerFactory.getLogger(TraceCollector.class);

    private final List<IncomingSpan> incomingSpans = Lists.newCopyOnWriteArrayList();

    private final ExecutorService executor;
    private final ServerSocket serverSocket;

    private volatile boolean closed;

    TraceCollector() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        serverSocket = new ServerSocket(0);
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    void start() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = serverSocket.accept();
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    while (!closed) {
                        incomingSpans.add((IncomingSpan) in.readObject());
                        out.writeObject("ok");
                    }
                } catch (Throwable t) {
                    if (!closed) {
                        logger.error(t.getMessage(), t);
                    }
                }
            }
        });
    }

    void close() throws IOException {
        closed = true;
        serverSocket.close();
        executor.shutdown();
    }

    IncomingSpan getCompletedIncomingSpan(@Nullable String transactionType,
            @Nullable String transactionName, int timeout, TimeUnit unit)
            throws InterruptedException {
        if (transactionName != null) {
            checkNotNull(transactionType);
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed(unit) < timeout) {
            for (IncomingSpan incomingSpan : incomingSpans) {
                if ((transactionType == null
                        || incomingSpan.transactionType().equals(transactionType))
                        && (transactionName == null
                                || incomingSpan.transactionName().equals(transactionName))) {
                    return incomingSpan;
                }
            }
            MILLISECONDS.sleep(10);
        }
        if (transactionName != null) {
            throw new IllegalStateException("No trace was collected for transaction type \""
                    + transactionType + "\" and transaction name \"" + transactionName + "\"");
        } else if (transactionType != null) {
            throw new IllegalStateException(
                    "No trace was collected for transaction type: " + transactionType);
        } else {
            throw new IllegalStateException("No trace was collected");
        }
    }

    boolean hasIncomingSpan() {
        return !incomingSpans.isEmpty();
    }

    void clearIncomingSpans() {
        incomingSpans.clear();
    }
}
