/**
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
package org.glowroot.instrumentation.cassandra;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.common.base.Stopwatch;

import org.glowroot.instrumentation.test.harness.util.Docker;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

class CassandraWrapper {

    private static final String CASSANDRA_VERSION;

    private static String containerName;

    static {
        if (useCassandra2x()) {
            // driver versions 2.x are incompatible with cassandra 3+
            CASSANDRA_VERSION = "2";
        } else {
            // latest
            CASSANDRA_VERSION = "latest";
        }
    }

    static int start() throws Exception {
        int port = Ports.getAvailable();
        containerName = Docker.start("cassandra:" + CASSANDRA_VERSION, "-p", port + ":9042");
        waitForCassandra(port);
        return port;
    }

    static void stop() throws Exception {
        Docker.stop(containerName);
    }

    private static void waitForCassandra(int port) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Exception lastException = null;
        while (stopwatch.elapsed(MINUTES) < 2) {
            Cluster cluster = Cluster.builder()
                    .addContactPointsWithPorts(
                            Arrays.asList(new InetSocketAddress("127.0.0.1", port)))
                    .build();
            try {
                cluster.connect();
                cluster.close();
                return;
            } catch (NoHostAvailableException e) {
                lastException = e;
                cluster.close();
                SECONDS.sleep(1);
            }
        }
        throw lastException;
    }

    private static boolean useCassandra2x() {
        try {
            Method method = Cluster.class.getMethod("getDriverVersion");
            String driverVersion = (String) method.invoke(null);
            return driverVersion.startsWith("2.");
        } catch (NoSuchMethodException e) {
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
