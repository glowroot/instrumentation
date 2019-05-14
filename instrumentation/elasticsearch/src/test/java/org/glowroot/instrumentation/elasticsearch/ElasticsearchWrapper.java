/**
 * Copyright 2017-2019 the original author or authors.
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
package org.glowroot.instrumentation.elasticsearch;

import java.net.InetSocketAddress;

import com.google.common.base.Stopwatch;
import org.elasticsearch.client.transport.TransportClient;

import org.glowroot.instrumentation.test.harness.util.Docker;
import org.glowroot.instrumentation.test.harness.util.Ports;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

class ElasticsearchWrapper {

    private static final String ELASTICSEARCH_VERSION;

    private static String containerName;

    static {
        String elasticsearchClientVersion =
                TransportClient.class.getPackage().getImplementationVersion();
        if (elasticsearchClientVersion.startsWith("5.")) {
            // elasticsearch image doesn't have convenience tags (e.g. 5)
            ELASTICSEARCH_VERSION = "5.6.16";
        } else if (elasticsearchClientVersion.startsWith("6.")) {
            // elasticsearch image doesn't have convenience tags (e.g. 6)
            ELASTICSEARCH_VERSION = "6.7.1";
        } else {
            // elasticsearch image doesn't have convenience tags (e.g. 7)
            ELASTICSEARCH_VERSION = "7.0.1";
        }
    }

    static int start() throws Exception {
        int port = Ports.getAvailable();
        if (ELASTICSEARCH_VERSION.startsWith("5.")) {
            containerName = Docker.start(
                    "docker.elastic.co/elasticsearch/elasticsearch:" + ELASTICSEARCH_VERSION, "-e",
                    "xpack.security.enabled=false", "-p", port + ":9300");
        } else {
            containerName = Docker.start(
                    "docker.elastic.co/elasticsearch/elasticsearch:" + ELASTICSEARCH_VERSION, "-p",
                    port + ":9300");
        }
        waitForElasticsearch(port);
        return port;
    }

    static void stop() throws Exception {
        Docker.stop(containerName);
    }

    private static void waitForElasticsearch(int port) throws Exception {
        TransportClient client = Util.client(new InetSocketAddress("127.0.0.1", port));
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (client.connectedNodes().isEmpty() && stopwatch.elapsed(MINUTES) < 2) {
            SECONDS.sleep(1);
        }
        if (client.connectedNodes().isEmpty()) {
            throw new IllegalStateException("Could not connect to Elasticsearch");
        }
        client.close();
    }
}
