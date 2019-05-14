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

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.NonNull;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.netty.boot.Util;

public class Netty3xInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http request");

    private static final Getter<HttpRequestShim> CARRIER = new GetterImpl();

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"org.jboss.netty.channel.Channel"})
    public abstract static class ChannelImpl implements ChannelMixin {

        private transient volatile boolean glowroot$completeAsyncTransaction;

        @Override
        public boolean glowroot$getCompleteAsyncTransaction() {
            return glowroot$completeAsyncTransaction;
        }

        @Override
        public void glowroot$setCompleteAsyncTransaction(boolean completeAsyncTransaction) {
            glowroot$completeAsyncTransaction = completeAsyncTransaction;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface ChannelMixin {

        boolean glowroot$getCompleteAsyncTransaction();

        void glowroot$setCompleteAsyncTransaction(boolean completeAsyncTransaction);
    }

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"org.jboss.netty.channel.ChannelFutureListener"})
    public abstract static class ListenerImpl implements ListenerMixin {

        private transient volatile @Nullable AuxThreadContext glowroot$auxContext;

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
    public interface ListenerMixin {

        @Nullable
        AuxThreadContext glowroot$getAuxContext();

        void glowroot$setAuxContext(@Nullable AuxThreadContext auxContext);
    }

    @Shim("org.jboss.netty.channel.ChannelHandlerContext")
    public interface ChannelHandlerContextShim {

        @Shim("org.jboss.netty.channel.Channel getChannel()")
        @Nullable
        ChannelMixin glowroot$getChannel();
    }

    @Shim("org.jboss.netty.handler.codec.http.HttpRequest")
    public interface HttpRequestShim {

        @Shim("org.jboss.netty.handler.codec.http.HttpMethod getMethod()")
        @Nullable
        HttpMethodShim glowroot$getMethod();

        @Nullable
        String getUri();

        @Shim("org.jboss.netty.handler.codec.http.HttpHeaders headers()")
        @Nullable
        HttpHeadersShim glowroot$headers();
    }

    @Shim("org.jboss.netty.handler.codec.http.HttpMethod")
    public interface HttpMethodShim {

        @Nullable
        String getName();
    }

    @Shim("org.jboss.netty.handler.codec.http.HttpHeaders")
    public interface HttpHeadersShim {

        @Nullable
        String get(String name);
    }

    @Shim("org.jboss.netty.channel.MessageEvent")
    public interface MessageEventShim {

        @Nullable
        Object getMessage();
    }

    @Shim("org.jboss.netty.handler.codec.http.HttpMessage")
    public interface HttpMessageShim {

        boolean isChunked();
    }

    @Shim("org.jboss.netty.handler.codec.http.HttpChunk")
    public interface HttpChunkShim {

        boolean isLast();
    }

    @Advice.Pointcut(className = "org.jboss.netty.channel.ChannelHandlerContext",
                     methodName = "sendUpstream",
                     methodParameterTypes = {"org.jboss.netty.channel.ChannelEvent"},
                     nestingGroup = "netty-inbound")
    public static class InboundAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(
                @Bind.This ChannelHandlerContextShim channelHandlerContext,
                @Bind.Argument(0) @Nullable Object channelEvent) {

            return channelHandlerContext.glowroot$getChannel() != null && channelEvent != null
                    && channelEvent instanceof MessageEventShim
                    && ((MessageEventShim) channelEvent).getMessage() instanceof HttpRequestShim;
        }

        @Advice.OnMethodBefore
        public static Span onBefore(
                OptionalThreadContext context,
                @Bind.This ChannelHandlerContextShim channelHandlerContext,
                // not null, just checked above in isEnabled()
                @Bind.Argument(0) Object channelEvent) {

            @SuppressWarnings("nullness") // just checked above in isEnabled()
            @NonNull
            ChannelMixin channel = channelHandlerContext.glowroot$getChannel();

            // just checked valid cast above in isEnabled()
            @SuppressWarnings("nullness") // just checked above in isEnabled()
            @NonNull
            Object msg = ((MessageEventShim) channelEvent).getMessage();

            // just checked valid cast above in isEnabled()
            HttpRequestShim request = (HttpRequestShim) msg;
            HttpMethodShim method = request.glowroot$getMethod();
            String methodName = method == null ? null : method.getName();
            channel.glowroot$setCompleteAsyncTransaction(true);
            return Util.startAsyncTransaction(context, methodName, request.getUri(), CARRIER,
                    request, TIMER_NAME);
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.end();
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    @Shim("com.typesafe.netty.http.pipelining.OrderedDownstreamChannelEvent")
    public interface OrderedDownstreamChannelEventShim {

        boolean isLast();
    }

    @Advice.Pointcut(className = "org.jboss.netty.channel.ChannelDownstreamHandler",
                     methodName = "handleDownstream",
                     methodParameterTypes = {"org.jboss.netty.channel.ChannelHandlerContext",
                             "org.jboss.netty.channel.ChannelEvent"})
    public static class OutboundAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(
                @Bind.Argument(0) @Nullable ChannelHandlerContextShim channelHandlerContext) {

            if (channelHandlerContext == null) {
                return false;
            }
            ChannelMixin channel = channelHandlerContext.glowroot$getChannel();
            return channel != null && channel.glowroot$getCompleteAsyncTransaction();
        }

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) @Nullable ChannelHandlerContextShim channelHandlerContext,
                @Bind.Argument(1) @Nullable Object channelEvent,
                ThreadContext context) {

            if (channelHandlerContext == null) {
                return;
            }
            if (channelEvent instanceof OrderedDownstreamChannelEventShim) {
                // play 2.2.x and later implements its own chunked transfer, not using netty's
                // MessageEvent/HttpMessage/HttpChunk
                if (((OrderedDownstreamChannelEventShim) channelEvent).isLast()) {
                    completeAsyncTransaction(channelHandlerContext, context);
                }
                return;
            }
            if (!(channelEvent instanceof MessageEventShim)) {
                return;
            }
            Object messageEvent = ((MessageEventShim) channelEvent).getMessage();
            if (messageEvent instanceof HttpMessageShim) {
                if (!((HttpMessageShim) messageEvent).isChunked()) {
                    completeAsyncTransaction(channelHandlerContext, context);
                }
                return;
            }
            if (messageEvent instanceof HttpChunkShim && ((HttpChunkShim) messageEvent).isLast()) {
                completeAsyncTransaction(channelHandlerContext, context);
            }
        }
    }

    @Advice.Pointcut(className = "org.jboss.netty.channel.ChannelFuture",
                     methodName = "addListener",
                     methodParameterTypes = {"org.jboss.netty.channel.ChannelFutureListener"})
    public static class AddListenerAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) ListenerMixin listener,
                ThreadContext context) {

            AuxThreadContext auxContext = context.createAuxThreadContext();
            listener.glowroot$setAuxContext(auxContext);
        }
    }

    @Advice.Pointcut(className = "org.jboss.netty.channel.ChannelFutureListener",
                     methodName = "operationComplete",
                     methodParameterTypes = {"org.jboss.netty.channel.ChannelFuture"})
    public static class OperationCompleteAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This ListenerMixin listener) {

            return listener.glowroot$getAuxContext() != null;
        }

        @Advice.OnMethodBefore
        public static Span onBefore(@Bind.This ListenerMixin listener) {

            @SuppressWarnings("nullness") // just checked above in isEnabled()
            @NonNull
            AuxThreadContext auxContext = listener.glowroot$getAuxContext();
            listener.glowroot$setAuxContext(null);
            return auxContext.start();
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter Span span) {

            span.end();
        }

        @Advice.OnMethodThrow
        public static void onThrow(
                @Bind.Thrown Throwable t,
                @Bind.Enter Span span) {

            span.endWithError(t);
        }
    }

    @Advice.Pointcut(className = "org.jboss.netty.channel.Channel",
                     methodName = "close",
                     methodParameterTypes = {})
    public static class CloseAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(ThreadContext context) {

            context.setTransactionAsyncComplete();
        }
    }

    private static void completeAsyncTransaction(ChannelHandlerContextShim channelHandlerContext,
            ThreadContext context) {

        context.setTransactionAsyncComplete();
        ChannelMixin channel = channelHandlerContext.glowroot$getChannel();
        if (channel != null) {
            channel.glowroot$setCompleteAsyncTransaction(false);
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
