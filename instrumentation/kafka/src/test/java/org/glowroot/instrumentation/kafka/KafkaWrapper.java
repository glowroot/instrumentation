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

    private static String containerName;

    static int start() throws Exception {
        // TODO find out why this only works with port 9092, or switch to another image (which may
        // involve running zookeeper separately, or using docker-compose)
        int port = 9092;
        containerName = Docker.start("spotify/kafka", "-p", port + ":9092", "--env",
                "ADVERTISED_HOST=127.0.0.1", "--env", "ADVERTISED_PORT=" + port);
        return port;
    }

    static void stop() throws Exception {
        Docker.stop(containerName);
    }
}
