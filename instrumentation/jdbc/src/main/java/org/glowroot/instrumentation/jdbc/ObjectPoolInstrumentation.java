/*
 * Copyright 2019 the original author or authors.
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
package org.glowroot.instrumentation.jdbc;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class ObjectPoolInstrumentation {

    private static final ConfigService configService = Agent.getConfigService("jdbc");

    private static final BooleanProperty captureConnectionPoolLeakDetails =
            configService.getBooleanProperty("captureConnectionPoolLeakDetails");

    @Advice.Pointcut(
                     className = "org.apache.commons.pool.impl.GenericObjectPool"
                             + "|org.apache.commons.pool2.impl.GenericObjectPool",
                     methodName = "borrowObject",
                     methodParameterTypes = {".."})
    public static class DbcpBorrowAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceAcquired(resource, captureConnectionPoolLeakDetails.value());
            }
        }
    }

    @Advice.Pointcut(
                     className = "org.apache.commons.pool.impl.GenericObjectPool"
                             + "|org.apache.commons.pool2.impl.GenericObjectPool",
                     methodName = "returnObject|invalidateObject",
                     methodParameterTypes = {"java.lang.Object"})
    public static class DbcpReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Argument(0) @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceReleased(resource);
            }
        }
    }

    @Advice.Pointcut(className = "org.apache.tomcat.jdbc.pool.ConnectionPool",
                     methodName = "borrowConnection",
                     methodParameterTypes = {".."})
    public static class TomcatBorrowAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceAcquired(resource, captureConnectionPoolLeakDetails.value());
            }
        }
    }

    @Advice.Pointcut(className = "org.apache.tomcat.jdbc.pool.ConnectionPool",
                     methodName = "returnConnection",
                     methodParameterTypes = {"org.apache.tomcat.jdbc.pool.PooledConnection"})
    public static class TomcatReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Argument(0) @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceReleased(resource);
            }
        }
    }

    @Advice.Pointcut(className = "com.sun.gjc.spi.ManagedConnectionImpl",
                     methodName = "getConnection",
                     methodParameterTypes = {"javax.security.auth.Subject",
                             "javax.resource.spi.ConnectionRequestInfo"})
    public static class GlassfishBorrowAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceAcquired(resource, captureConnectionPoolLeakDetails.value());
            }
        }
    }

    @Advice.Pointcut(className = "com.sun.gjc.spi.ManagedConnectionImpl",
                     methodName = "connectionClosed",
                     methodParameterTypes = {"java.lang.Exception",
                             "com.sun.gjc.spi.base.ConnectionHolder"})
    public static class GlassfishReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Argument(1) Object connectionHolder,
                ThreadContext context) {

            if (connectionHolder != null) {
                context.trackResourceReleased(connectionHolder);
            }
        }
    }

    @Advice.Pointcut(className = "com.zaxxer.hikari.pool.BaseHikariPool",
                     methodName = "getConnection",
                     methodParameterTypes = {".."},
                     nestingGroup = "jdbc-hikari-leak-detection")
    public static class HikariBorrowAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceAcquired(resource, captureConnectionPoolLeakDetails.value());
            }
        }
    }

    @Advice.Pointcut(className = "com.zaxxer.hikari.HikariPool|com.zaxxer.hikari.pool.HikariPool",
                     methodName = "getConnection",
                     methodParameterTypes = {})
    public static class OldHikariBorrowAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceAcquired(resource, captureConnectionPoolLeakDetails.value());
            }
        }
    }

    @Advice.Pointcut(
                     className = "com.zaxxer.hikari.proxy.ConnectionProxy"
                             + "|com.zaxxer.hikari.pool.ProxyConnection",
                     methodName = "close",
                     methodParameterTypes = {})
    public static class HikariReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This Object connectionProxy,
                ThreadContext context) {

            context.trackResourceReleased(connectionProxy);
        }
    }

    @Advice.Pointcut(className = "bitronix.tm.resource.jdbc.PoolingDataSource",
                     methodName = "getConnection",
                     methodParameterTypes = {})
    public static class BitronixBorrowAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Return @Nullable Object resource,
                ThreadContext context) {

            if (resource != null) {
                context.trackResourceAcquired(resource, captureConnectionPoolLeakDetails.value());
            }
        }
    }

    @Advice.Pointcut(className = "bitronix.tm.resource.jdbc.proxy.ConnectionJavaProxy",
                     methodName = "close",
                     methodParameterTypes = {})
    public static class BitronixReturnAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.This Object connectionProxy,
                ThreadContext context) {

            context.trackResourceReleased(connectionProxy);
        }
    }
}
