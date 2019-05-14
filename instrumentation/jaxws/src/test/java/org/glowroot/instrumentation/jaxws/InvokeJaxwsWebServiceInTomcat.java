/*
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
package org.glowroot.instrumentation.jaxws;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.apache.catalina.Context;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Tomcat;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.util.Ports;

public class InvokeJaxwsWebServiceInTomcat implements AppUnderTest {

    private static final Logger logger =
            LoggerFactory.getLogger(InvokeJaxwsWebServiceInTomcat.class);

    @Override
    public void executeApp(Serializable... args) throws Exception {
        String webapp = (String) args[0];
        String contextPath = (String) args[1];
        String path = (String) args[2];
        executeApp(webapp, contextPath, path);
    }

    private void executeApp(String webapp, String contextPath, String url) throws Exception {
        int port = Ports.getAvailable();
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir("target/tomcat");
        tomcat.setPort(port);
        Context context = tomcat.addWebapp(contextPath,
                new File("src/test/resources/" + webapp).getAbsolutePath());

        WebappLoader webappLoader =
                new WebappLoader(InvokeJaxwsWebServiceInTomcat.class.getClassLoader());
        context.setLoader(webappLoader);

        tomcat.start();

        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ForBothHelloAndRootService.class);
        factory.setAddress("http://localhost:" + port + contextPath + url);
        ForBothHelloAndRootService client = (ForBothHelloAndRootService) factory.create();
        client.echo("abc");

        checkForRequestThreads(webappLoader);
        tomcat.stop();
        tomcat.destroy();
    }

    // log stack traces for any currently running request threads in order to troubleshoot sporadic
    // test failures due to ERROR logged by org.apache.catalina.loader.WebappClassLoaderBase
    // "The web application [] is still processing a request that has yet to finish. This is very
    // likely to create a memory leak..."
    private void checkForRequestThreads(WebappLoader webappLoader) throws Exception {
        Method getThreadsMethod = WebappClassLoaderBase.class.getDeclaredMethod("getThreads");
        getThreadsMethod.setAccessible(true);
        Method isRequestThreadMethod =
                WebappClassLoaderBase.class.getDeclaredMethod("isRequestThread", Thread.class);
        isRequestThreadMethod.setAccessible(true);
        ClassLoader webappClassLoader = webappLoader.getClassLoader();
        Thread[] threads = (Thread[]) getThreadsMethod.invoke(webappClassLoader);
        for (Thread thread : threads) {
            if (thread == null) {
                continue;
            }
            if ((boolean) isRequestThreadMethod.invoke(webappClassLoader, thread)) {
                StringBuilder sb = new StringBuilder();
                for (StackTraceElement element : thread.getStackTrace()) {
                    sb.append(element);
                    sb.append('\n');
                }
                logger.error("tomcat request thread \"{}\" is still active:\n{}", thread.getName(),
                        sb);
            }
        }
    }

    @WebService
    public interface ForBothHelloAndRootService {

        String echo(@WebParam(name = "param") String msg);
    }
}
