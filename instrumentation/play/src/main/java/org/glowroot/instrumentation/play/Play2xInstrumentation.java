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
package org.glowroot.instrumentation.play;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.ThreadContext.Priority;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Shim;

public class Play2xInstrumentation {

    private static final TimerName RENDER_TIMER_NAME = Agent.getTimerName("play render");

    private static final BooleanProperty useAltTransactionNaming =
            Agent.getConfigService("play").getBooleanProperty("useAltTransactionNaming");

    // "play.core.routing.TaggingInvoker" is for play 2.4.x and later
    // "play.core.Router$Routes$TaggingInvoker" is for play 2.3.x
    @Shim({"play.core.routing.TaggingInvoker", "play.core.Router$Routes$TaggingInvoker"})
    public interface TaggingInvoker {

        @Shim("scala.collection.immutable.Map cachedHandlerTags()")
        @Nullable
        ScalaMap glowroot$cachedHandlerTags();
    }

    @Shim("scala.collection.immutable.Map")
    public interface ScalaMap {

        @Shim("scala.Option get(java.lang.Object)")
        @Nullable
        ScalaOption glowroot$get(Object key);
    }

    @Shim("scala.Option")
    public interface ScalaOption {

        boolean isDefined();

        Object get();
    }

    @Advice.Pointcut(className = "play.core.routing.TaggingInvoker|play.core.Router$Routes$TaggingInvoker",
                     methodName = "call",
                     methodParameterTypes = {"scala.Function0"})
    public static class HandlerInvokerAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.This TaggingInvoker taggingInvoker,
                ThreadContext context) {

            ScalaMap tags = taggingInvoker.glowroot$cachedHandlerTags();
            if (tags == null) {
                return;
            }
            if (useAltTransactionNaming.value()) {
                ScalaOption controllerOption = tags.glowroot$get("ROUTE_CONTROLLER");
                ScalaOption methodOption = tags.glowroot$get("ROUTE_ACTION_METHOD");
                if (controllerOption != null && controllerOption.isDefined() && methodOption != null
                        && methodOption.isDefined()) {
                    String controller = toNonNullString(controllerOption.get());
                    String transactionName =
                            getAltTransactionName(controller, toNonNullString(methodOption.get()));
                    context.setTransactionName(transactionName, Priority.CORE_INSTRUMENTATION);
                }
            } else {
                ScalaOption option = tags.glowroot$get("ROUTE_PATTERN");
                if (option != null && option.isDefined()) {
                    String route = toNonNullString(option.get());
                    route = Routes.simplifiedRoute(route);
                    context.setTransactionName(route, Priority.CORE_INSTRUMENTATION);
                }
            }
        }
    }

    @Advice.Pointcut(className = "views.html.*",
                     methodName = "apply",
                     methodParameterTypes = {".."},
                     nestingGroup = "play-render")
    public static class RenderAdvice {

        @Advice.OnMethodBefore
        public static Span onBefore(@Bind.This Object view, ThreadContext context) {

            String viewName = view.getClass().getSimpleName();
            // strip off trailing $
            viewName = viewName.substring(0, viewName.length() - 1);
            return context.startLocalSpan(MessageSupplier.create("play render: {}", viewName),
                    RENDER_TIMER_NAME);
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

    // ========== play 2.0.x - 2.2.x ==========

    @Shim("play.core.Router$HandlerDef")
    public interface HandlerDef {

        @Nullable
        String controller();

        @Nullable
        String method();
    }

    @Advice.Pointcut(className = "play.core.Router$HandlerInvoker",
                     methodName = "call",
                     methodParameterTypes = {"scala.Function0", "play.core.Router$HandlerDef"})
    public static class OldHandlerInvokerAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(1) HandlerDef handlerDef,
                @Bind.ClassMeta PlayInvoker invoker,
                ThreadContext context) {

            String controller = handlerDef.controller();
            String method = handlerDef.method();
            // path() method doesn't exist in play 2.0.x so need to use reflection instead of shim
            String path = invoker.path(handlerDef);
            if (useAltTransactionNaming.value() || path == null) {
                if (controller != null && method != null) {
                    context.setTransactionName(getAltTransactionName(controller, method),
                            Priority.CORE_INSTRUMENTATION);
                }
            } else {
                path = Routes.simplifiedRoute(path);
                context.setTransactionName(path, Priority.CORE_INSTRUMENTATION);
            }
        }
    }

    private static String getAltTransactionName(String controller, String methodName) {
        int index = controller.lastIndexOf('.');
        if (index == -1) {
            return controller + "#" + methodName;
        } else {
            return controller.substring(index + 1) + "#" + methodName;
        }
    }

    private static String toNonNullString(Object obj) {
        String str = obj.toString();
        return str == null ? "" : str;
    }
}
