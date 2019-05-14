/*
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
package org.glowroot.instrumentation.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.netty.boot.Util;

public class Netty4xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http request");

    private static final Getter<HttpRequestShim> GETTER = new GetterImpl();

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"io.netty.channel.Channel"})
    public abstract static class ChannelImpl implements ChannelMixin {

        private transient volatile @Nullable ThreadContext glowroot$threadContextToComplete;
        private transient volatile @Nullable AuxThreadContext glowroot$auxContext;

        @Override
        public @Nullable ThreadContext glowroot$getThreadContextToComplete() {
            return glowroot$threadContextToComplete;
        }

        @Override
        public void glowroot$setThreadContextToComplete(
                @Nullable ThreadContext threadContextToComplete) {
            glowroot$threadContextToComplete = threadContextToComplete;
        }

        @Override
        public @Nullable AuxThreadContext glowroot$getAuxContext() {
            return glowroot$auxContext;
        }

        @Override
        public void glowroot$setAuxContext(@Nullable AuxThreadContext auxContext) {
            glowroot$auxContext = auxContext;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface ChannelMixin {

        @Nullable
        ThreadContext glowroot$getThreadContextToComplete();

        void glowroot$setThreadContextToComplete(@Nullable ThreadContext completeAsyncTransaction);

        @Nullable
        AuxThreadContext glowroot$getAuxContext();

        void glowroot$setAuxContext(@Nullable AuxThreadContext auxThreadContext);
    }

    // need shims for netty-http-codec classes, since the pointcuts below are applied to
    // netty-transport classes, whether or not netty-codec-http is included on the classpath
    @Shim("io.netty.handler.codec.http.HttpRequest")
    public interface HttpRequestShim {

        @Shim("io.netty.handler.codec.http.HttpMethod getMethod()")
        @Nullable
        HttpMethodShim glowroot$getMethod();

        @Nullable
        String getUri();

        @Shim("io.netty.handler.codec.http.HttpHeaders headers()")
        @Nullable
        HttpHeadersShim glowroot$headers();
    }

    // need shims for netty-http-codec classes, since the pointcuts below are applied to
    // netty-transport classes, whether or not netty-codec-http is included on the classpath
    @Shim("io.netty.handler.codec.http.HttpMethod")
    public interface HttpMethodShim {

        @Nullable
        String name();
    }

    // need shims for netty-http-codec classes, since the pointcuts below are applied to
    // netty-transport classes, whether or not netty-codec-http is included on the classpath
    @Shim("io.netty.handler.codec.http.HttpHeaders")
    public interface HttpHeadersShim {

        @Nullable
        String get(String name);
    }

    // need shims for netty-http-codec classes, since the pointcuts below are applied to
    // netty-transport classes, whether or not netty-codec-http is included on the classpath
    @Shim("io.netty.handler.codec.http.LastHttpContent")
    public interface LastHttpContentShim {}

    @Advice.Pointcut(className = "io.netty.channel.ChannelHandlerContext",
                     methodName = "fireChannelRead",
                     methodParameterTypes = {"java.lang.Object"},
                     nestingGroup = "netty-inbound")
    public static class InboundAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                OptionalThreadContext context,
                @Bind.This ChannelHandlerContext channelHandlerContext,
                @Bind.Argument(0) @Nullable Object msg) {

            Channel channel = channelHandlerContext.channel();
            if (channel == null) {
                return null;
            }
            final ChannelMixin channelMixin = (ChannelMixin) channel;
            AuxThreadContext auxContext = channelMixin.glowroot$getAuxContext();
            if (auxContext != null) {
                return auxContext.start();
            }
            if (!(msg instanceof HttpRequestShim)) {
                return null;
            }
            HttpRequestShim request = (HttpRequestShim) msg;
            HttpMethodShim method = request.glowroot$getMethod();
            String methodName = method == null ? null : method.name();
            Span span = Util.startAsyncTransaction(context, methodName, request.getUri(), GETTER,
                    request, TIMER_NAME);
            channelMixin.glowroot$setThreadContextToComplete(context);
            // IMPORTANT the close future gets called if client disconnects, but does not get called
            // when transaction ends and Keep-Alive is used (so still need to capture write
            // LastHttpContent below)
            channel.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) {
                    endTransaction(channelMixin);
                }
            });
            if (!(msg instanceof LastHttpContentShim)) {
                channelMixin.glowroot$setAuxContext(context.createAuxThreadContext());
            }
            return span;
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    @Advice.Pointcut(className = "io.netty.channel.ChannelHandlerContext",
                     methodName = "fireChannelReadComplete",
                     methodParameterTypes = {},
                     nestingGroup = "netty-inbound")
    public static class InboundCompleteAdvice {

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(
                @Bind.This ChannelHandlerContext channelHandlerContext) {

            ChannelMixin channel = (ChannelMixin) channelHandlerContext.channel();
            if (channel == null) {
                return null;
            }
            AuxThreadContext auxContext = channel.glowroot$getAuxContext();
            if (auxContext == null) {
                return null;
            }
            return auxContext.start();
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    // IMPORTANT the close future gets called if client disconnects, but does not get called when
    // transaction ends and Keep-Alive is used (so still need to capture write LastHttpContent
    // below)
    @Advice.Pointcut(className = "io.netty.channel.ChannelOutboundHandler",
                     methodName = "write",
                     methodParameterTypes = {"io.netty.channel.ChannelHandlerContext",
                             "java.lang.Object", "io.netty.channel.ChannelPromise"})
    public static class OutboundAdvice {

        @Advice.OnMethodAfter
        public static void onAfter(
                @Bind.Argument(0) @Nullable ChannelHandlerContext channelHandlerContext,
                @Bind.Argument(1) @Nullable Object msg) {

            if (!(msg instanceof LastHttpContentShim)) {
                return;
            }
            if (channelHandlerContext == null) {
                return;
            }
            Channel channel = channelHandlerContext.channel();
            if (channel == null) {
                return;
            }
            endTransaction((ChannelMixin) channel);
        }
    }

    private static void endTransaction(ChannelMixin channelMixin) {

        ThreadContext context = channelMixin.glowroot$getThreadContextToComplete();
        if (context != null) {
            context.setTransactionAsyncComplete();
            channelMixin.glowroot$setThreadContextToComplete(null);
            channelMixin.glowroot$setAuxContext(null);
        }
    }

    private static class GetterImpl implements Getter<HttpRequestShim> {

        @Override
        public @Nullable String get(HttpRequestShim carrier, String key) {

            HttpHeadersShim headers = carrier.glowroot$headers();
            if (headers == null) {
                return null;
            }
            return headers.get(key);
        }
    }
}
