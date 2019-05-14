/**
 * Copyright 2016-2019 the original author or authors.
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
package org.glowroot.instrumentation.test.matrix;

public class RunAll {

    public static void main(String[] args) throws Exception {
        ApacheHttpAsyncClient.runAll();
        ApacheHttpClient.runAll();
        AsyncHttpClient.runAll();
        AxisClient.runAll();
        Camel.runAll();
        Cassandra.runAll();
        Elasticsearch.runAll();
        Executor.run();
        Grails.runAll();
        Hibernate.runAll();
        HttpURLConnection.run();
        JavaUtilLogging.run();
        JAXRS.runAll();
        JDBC.runAll();
        JMS.run();
        JSF.runAll();
        JSP.run();
        Kafka.runAll();
        Log4j.runAll();
        Logback.runAll();
        MongoDB.runAll();
        Netty.runAll();
        OkHttp.runAll();
        Play.runAll();
        Quartz.runAll();
        Redis.runAll();
        Servlet.run();
        Spring.runAll();
        Struts.runAll();
    }
}
