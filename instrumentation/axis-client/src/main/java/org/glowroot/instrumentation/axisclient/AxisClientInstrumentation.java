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
package org.glowroot.instrumentation.axisclient;

import org.apache.axis.client.Call;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;

public class AxisClientInstrumentation {

    private static final TimerName TIMER_NAME = Agent.getTimerName("http client request");

    private static final Setter<Call> SETTER = new NopSetter();

    @Advice.Pointcut(className = "org.apache.axis.client.Call",
                     methodName = "invoke",
                     methodParameterTypes = {},
                     nestingGroup = "http-client")
    public static class ResourceAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(@Bind.This Call call, ThreadContext context) {

            String url = call.getTargetEndpointAddress();
            return context.startOutgoingSpan("HTTP", "POST " + url, SETTER, call,
                    MessageSupplier.create("http client request: POST {}", url), TIMER_NAME);
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

    private static class NopSetter implements Setter<Call> {

        @Override
        public void put(Call carrier, String key, String value) {
            // TODO
        }
    }
}
