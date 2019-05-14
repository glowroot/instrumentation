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

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.util.Collections;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

class Util {

    private static final Constructor<?> TRANSPORT_ADDRESS_CONSTRUCTOR;
    private static final Constructor<?> INET_SOCKET_TRANSPORT_ADDRESS_CONSTRUCTOR;

    static {
        TRANSPORT_ADDRESS_CONSTRUCTOR = getTransportAddressConstructor();
        INET_SOCKET_TRANSPORT_ADDRESS_CONSTRUCTOR = getInetSocketTransportAddressConstructor();
    }

    static TransportClient client(InetSocketAddress socketAddress) throws Exception {
        TransportClient client = new PreBuiltTransportClient(
                Settings.builder().put("client.transport.ignore_cluster_name", true).build(),
                Collections.emptyList());
        TransportAddress address;
        if (TRANSPORT_ADDRESS_CONSTRUCTOR == null) {
            address = (TransportAddress) INET_SOCKET_TRANSPORT_ADDRESS_CONSTRUCTOR
                    .newInstance(socketAddress);
        } else {
            address = (TransportAddress) TRANSPORT_ADDRESS_CONSTRUCTOR.newInstance(socketAddress);
        }
        client.addTransportAddress(address);
        return client;
    }

    // elasticsearch 6.x
    private static Constructor<?> getTransportAddressConstructor() {
        try {
            Class<?> clazz = Class.forName("org.elasticsearch.common.transport.TransportAddress");
            return clazz.getConstructor(InetSocketAddress.class);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        }
    }

    // elasticsearch 5.x
    private static Constructor<?> getInetSocketTransportAddressConstructor() {
        try {
            Class<?> clazz =
                    Class.forName("org.elasticsearch.common.transport.InetSocketTransportAddress");
            return clazz.getConstructor(InetSocketAddress.class);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        }
    }
}
