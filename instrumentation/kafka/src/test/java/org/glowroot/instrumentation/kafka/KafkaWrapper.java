/**
 * Copyright 2018-2019 the original author or authors.
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
package org.glowroot.instrumentation.kafka;

import org.glowroot.instrumentation.test.harness.util.Docker;

class KafkaWrapper {

    private static String networkName;
    private static String zookeeperContainerName;
    private static String kafkaContainerName;

    static int start() throws Exception {
        networkName = Docker.createNetwork();
        zookeeperContainerName = Docker.start("confluentinc/cp-zookeeper", "--net=" + networkName,
                "-p", "2181", "--env", "ZOOKEEPER_CLIENT_PORT=2181");
        // need to give zookeeper a few seconds head start to avoid sporadic failures
        Thread.sleep(5000);
        int kafkaPort = 9092;
        kafkaContainerName = Docker.start("confluentinc/cp-kafka", "--net=" + networkName,
                "-p", kafkaPort + ":9092",
                "--env", "KAFKA_ZOOKEEPER_CONNECT=" + zookeeperContainerName + ":2181",
                "--env", "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:" + kafkaPort,
                "--env", "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1");
        return kafkaPort;
    }

    static void stop() throws Exception {
        Docker.stop(kafkaContainerName);
        Docker.stop(zookeeperContainerName);
        Docker.removeNetwork(networkName);
    }
}
