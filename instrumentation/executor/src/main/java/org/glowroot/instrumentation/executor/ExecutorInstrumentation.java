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
package org.glowroot.instrumentation.executor;

import java.util.Collection;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.ParameterHolder;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.Advice;
import org.glowroot.instrumentation.api.weaving.Bind;
import org.glowroot.instrumentation.api.weaving.Mixin;

public class ExecutorInstrumentation {

    private static final Logger logger = Logger.getLogger(ExecutorInstrumentation.class);

    private static final TimerName FUTURE_TIMER_NAME = Agent.getTimerName("wait on future");

    private static final String EXECUTOR_CLASSES = "java.util.concurrent.Executor"
            + "|java.util.concurrent.ExecutorService"
            + "|org.springframework.core.task.AsyncTaskExecutor"
            + "|org.springframework.core.task.AsyncListenableTaskExecutor";

    private static final AtomicBoolean isDoneExceptionLogged = new AtomicBoolean();

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin({"java.lang.Runnable", "java.util.concurrent.Callable",
            "java.util.concurrent.ForkJoinTask", "akka.jsr166y.ForkJoinTask",
            "scala.concurrent.forkjoin.ForkJoinTask"})
    public abstract static class RunnableEtcImpl implements RunnableEtcMixin {

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

    // TODO suppress various known thread pool threads (e.g. TimerThread)
    @Mixin({"org.apache.tomcat.util.net.JIoEndpoint$SocketProcessor",
            "org.apache.http.impl.nio.client.CloseableHttpAsyncClientBase$1",
            "java.util.TimerThread"})
    public static class SuppressedRunnableImpl implements SuppressedRunnableMixin {}

    // the method names are verbose since they will be mixed in to existing classes
    public interface RunnableEtcMixin {

        @Nullable
        AuxThreadContext glowroot$getAuxContext();

        void glowroot$setAuxContext(@Nullable AuxThreadContext auxContext);
    }

    public interface SuppressedRunnableMixin {}

    @Advice.Pointcut(className = EXECUTOR_CLASSES,
                     methodName = "execute|submit|submitListenable",
                     methodParameterTypes = {"java.lang.Runnable", ".."},
                     nestingGroup = "executor-execute")
    public static class ExecuteRunnableAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) ParameterHolder<Runnable> runnableHolder,
                ThreadContext context) {

            onBeforeWithRunnableHolder(runnableHolder, context);
        }
    }

    @Advice.Pointcut(className = EXECUTOR_CLASSES,
                     methodName = "execute|submit|submitListenable",
                     methodParameterTypes = {"java.util.concurrent.Callable", ".."},
                     nestingGroup = "executor-execute")
    public static class ExecuteCallableAdvice {

        @Advice.OnMethodBefore
        public static <T> void onBefore(
                @Bind.Argument(0) ParameterHolder<Callable<T>> callableHolder,
                ThreadContext context) {

            onBeforeWithCallableHolder(callableHolder, context);
        }
    }

    @Advice.Pointcut(className = "java.util.concurrent.ForkJoinPool",
                     methodName = "execute|submit|invoke",
                     methodParameterTypes = {"java.util.concurrent.ForkJoinTask", ".."},
                     nestingGroup = "executor-execute")
    public static class ForkJoinPoolAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) Object forkJoinTask) {

            // this class may have been loaded before class file transformer was added to jvm
            return forkJoinTask instanceof RunnableEtcMixin;
        }

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) Object forkJoinTask,
                ThreadContext context) {

            // cast is safe because of isEnabled() check above
            onBeforeCommon((RunnableEtcMixin) forkJoinTask, context);
        }
    }

    @Advice.Pointcut(className = "akka.jsr166y.ForkJoinPool",
                     methodName = "execute|submit|invoke",
                     methodParameterTypes = {"akka.jsr166y.ForkJoinTask", ".."},
                     nestingGroup = "executor-execute")
    public static class AkkaJsr166yForkJoinPoolAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) Object forkJoinTask) {

            // this class may have been loaded before class file transformer was added to jvm
            return forkJoinTask instanceof RunnableEtcMixin;
        }

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) Object forkJoinTask,
                ThreadContext context) {

            // cast is safe because of isEnabled() check above
            onBeforeCommon((RunnableEtcMixin) forkJoinTask, context);
        }
    }

    @Advice.Pointcut(className = "scala.concurrent.forkjoin.ForkJoinPool",
                     methodName = "execute|submit|invoke",
                     methodParameterTypes = {"scala.concurrent.forkjoin.ForkJoinTask", ".."},
                     nestingGroup = "executor-execute")
    public static class ScalaForkJoinPoolAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) Object forkJoinTask) {

            // this class may have been loaded before class file transformer was added to jvm
            return forkJoinTask instanceof RunnableEtcMixin;
        }

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) Object forkJoinTask,
                ThreadContext context) {

            // cast is safe because of isEnabled() check above
            onBeforeCommon((RunnableEtcMixin) forkJoinTask, context);
        }
    }

    @Advice.Pointcut(className = "java.lang.Thread",
                     methodName = "<init>",
                     methodParameterTypes = {},
                     nestingGroup = "executor-execute")
    public static class ThreadInitAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This Thread thread, ThreadContext context) {

            onThreadInitCommon(thread, context);
        }
    }

    @Advice.Pointcut(className = "java.lang.Thread",
                     methodName = "<init>",
                     methodParameterTypes = {"java.lang.String"},
                     nestingGroup = "executor-execute")
    public static class ThreadInitWithStringAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This Thread thread, ThreadContext context) {

            onThreadInitCommon(thread, context);
        }
    }

    @Advice.Pointcut(className = "java.lang.Thread",
                     methodName = "<init>",
                     methodParameterTypes = {"java.lang.ThreadGroup", "java.lang.String"},
                     nestingGroup = "executor-execute")
    public static class ThreadInitWithStringAndThreadGroupAdvice {

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.This Thread thread, ThreadContext context) {

            onThreadInitCommon(thread, context);
        }
    }

    @Advice.Pointcut(className = "java.lang.Thread",
                     methodName = "<init>",
                     methodParameterTypes = {"java.lang.Runnable", ".."},
                     nestingGroup = "executor-execute")
    public static class ThreadInitWithRunnableAdvice {

        // cannot use @Advice.This in @Advice.OnMethodBefore of a constructor (at least not in
        // OpenJ9, and for good reason since receiver is not initialized before call to super)
        @Advice.OnMethodBefore
        public static boolean onBefore(
                @Bind.Argument(0) ParameterHolder<Runnable> runnableHolder,
                ThreadContext context) {

            return onThreadInitCommon(runnableHolder, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Enter boolean alreadyHandled,
                @Bind.This Thread thread,
                ThreadContext context) {

            if (!alreadyHandled && thread instanceof RunnableEtcMixin) {
                onBeforeCommon((RunnableEtcMixin) thread, context);
            }
        }
    }

    @Advice.Pointcut(className = "java.lang.Thread",
                     methodName = "<init>",
                     methodParameterTypes = {"java.lang.ThreadGroup", "java.lang.Runnable", ".."},
                     nestingGroup = "executor-execute")
    public static class ThreadInitWithThreadGroupAdvice {

        // cannot use @Advice.This in @Advice.OnMethodBefore of a constructor (at least not in
        // OpenJ9, and for good reason since receiver is not initialized before call to super)
        @Advice.OnMethodBefore
        public static boolean onBefore(
                @Bind.Argument(1) ParameterHolder<Runnable> runnableHolder,
                ThreadContext context) {

            return onThreadInitCommon(runnableHolder, context);
        }

        @Advice.OnMethodReturn
        public static void onReturn(
                @Bind.Enter boolean alreadyHandled,
                @Bind.This Thread thread,
                ThreadContext context) {

            if (!alreadyHandled && thread instanceof RunnableEtcMixin) {
                onBeforeCommon((RunnableEtcMixin) thread, context);
            }
        }
    }

    @Advice.Pointcut(className = "com.google.common.util.concurrent.ListenableFuture",
                     methodName = "addListener",
                     methodParameterTypes = {"java.lang.Runnable", "java.util.concurrent.Executor"},
                     nestingGroup = "executor-add-listener")
    public static class AddListenerAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) ParameterHolder<Runnable> runnableHolder,
                ThreadContext context) {

            onBeforeWithRunnableHolder(runnableHolder, context);
        }
    }

    @Advice.Pointcut(
                     className = "java.util.concurrent.ExecutorService|java.util.concurrent.ForkJoinPool"
                             + "|akka.jsr166y.ForkJoinPool|scala.concurrent.forkjoin.ForkJoinPool",
                     methodName = "invokeAll|invokeAny",
                     methodParameterTypes = {"java.util.Collection", ".."},
                     nestingGroup = "executor-execute")
    public static class InvokeAnyAllAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) Collection<?> callables,
                ThreadContext context) {

            if (callables == null) {
                return;
            }
            for (Object callable : callables) {
                // this class may have been loaded before class file transformer was added to jvm
                if (callable instanceof RunnableEtcMixin) {
                    RunnableEtcMixin callableMixin = (RunnableEtcMixin) callable;
                    AuxThreadContext auxContext = context.createAuxThreadContext();
                    callableMixin.glowroot$setAuxContext(auxContext);
                }
            }
        }
    }

    @Advice.Pointcut(className = "java.util.concurrent.ScheduledExecutorService",
                     methodName = "schedule",
                     methodParameterTypes = {"java.lang.Runnable", ".."},
                     nestingGroup = "executor-execute")
    public static class ScheduleRunnableAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) ParameterHolder<Runnable> runnableHolder,
                ThreadContext context) {

            onBeforeWithRunnableHolder(runnableHolder, context);
        }
    }

    @Advice.Pointcut(className = "java.util.concurrent.ScheduledExecutorService",
                     methodName = "schedule",
                     methodParameterTypes = {"java.util.concurrent.Callable", ".."},
                     nestingGroup = "executor-execute")
    public static class ScheduleCallableAdvice {

        @Advice.OnMethodBefore
        public static <T> void onBefore(
                @Bind.Argument(0) ParameterHolder<Callable<T>> callableHolder,
                ThreadContext context) {

            onBeforeWithCallableHolder(callableHolder, context);
        }
    }

    @Advice.Pointcut(className = "akka.actor.Scheduler",
                     methodName = "scheduleOnce",
                     methodParameterTypes = {"scala.concurrent.duration.FiniteDuration",
                             "java.lang.Runnable", ".."},
                     nestingGroup = "executor-execute")
    public static class ScheduleOnceAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(1) ParameterHolder<Runnable> runnableHolder,
                ThreadContext context) {

            onBeforeWithRunnableHolder(runnableHolder, context);
        }
    }

    @Advice.Pointcut(className = "java.util.Timer",
                     methodName = "schedule",
                     methodParameterTypes = {"java.util.TimerTask", ".."},
                     nestingGroup = "executor-execute")
    public static class TimerScheduleAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.Argument(0) Object runnableEtc) {

            // this class may have been loaded before class file transformer was added to jvm
            return runnableEtc instanceof RunnableEtcMixin;
        }

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) TimerTask timerTask,
                ThreadContext context) {

            // cast is safe because of isEnabled() check above
            onBeforeCommon((RunnableEtcMixin) timerTask, context);
        }
    }

    // this method uses submit() and returns Future, but none of the callers use/wait on the Future
    @Advice.Pointcut(className = "net.sf.ehcache.store.disk.DiskStorageFactory",
                     methodName = "schedule",
                     methodParameterTypes = {"java.util.concurrent.Callable"},
                     nestingGroup = "executor-execute")
    public static class EhcacheDiskStorageScheduleAdvice {}

    // these methods use execute() to start long running threads that should not be tied to the
    // current transaction
    @Advice.Pointcut(
                     className = "org.eclipse.jetty.io.SelectorManager"
                             + "|org.eclipse.jetty.server.AbstractConnector"
                             + "|wiremock.org.eclipse.jetty.io.SelectorManager"
                             + "|wiremock.org.eclipse.jetty.server.AbstractConnector",
                     methodName = "doStart",
                     methodParameterTypes = {},
                     nestingGroup = "executor-execute")
    public static class JettyDoStartAdvice {}

    @Advice.Pointcut(className = "javax.servlet.AsyncContext",
                     methodName = "start",
                     methodParameterTypes = {"java.lang.Runnable"})
    public static class StartAdvice {

        @Advice.OnMethodBefore
        public static void onBefore(
                @Bind.Argument(0) ParameterHolder<Runnable> runnableHolder,
                ThreadContext context) {

            onBeforeWithRunnableHolder(runnableHolder, context);
        }
    }

    @Advice.Pointcut(className = "java.util.concurrent.Future",
                     methodName = "get",
                     methodParameterTypes = {".."},
                     suppressibleUsingKey = "wait-on-future")
    public static class FutureGetAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(
                @Bind.This Future<?> future,
                @Bind.ClassMeta FutureClassMeta futureClassMeta) {

            if (futureClassMeta.isNonStandardFuture()) {
                // this is to handle known non-standard Future implementations
                return false;
            }
            // don't capture if already done, primarily this is to avoid caching pattern where
            // a future is used to store the value to ensure only-once initialization
            try {
                return !future.isDone();
            } catch (Exception e) {
                if (isDoneExceptionLogged.getAndSet(true)) {
                    logger.debug(e.getMessage(), e);
                } else {
                    logger.info("encountered a non-standard java.util.concurrent.Future"
                            + " implementation, please report this stack trace to"
                            + " https://github.com/glowroot/instrumentation:", e);
                }
                return false;
            }
        }

        @Advice.OnMethodBefore
        public static Timer onBefore(ThreadContext context) {

            return context.startTimer(FUTURE_TIMER_NAME);
        }

        @Advice.OnMethodAfter
        public static void onAfter(@Bind.Enter Timer timer) {

            timer.stop();
        }
    }

    // the nesting group only starts applying once auxiliary thread context is started (it does not
    // apply to OptionalThreadContext that miss)
    @Advice.Pointcut(className = "java.lang.Runnable",
                     methodName = "run",
                     methodParameterTypes = {},
                     nestingGroup = "executor-run")
    public static class RunnableAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This Runnable runnable) {

            if (!(runnable instanceof RunnableEtcMixin)) {
                // this class was loaded before class file transformer was added to jvm
                return false;
            }
            RunnableEtcMixin runnableMixin = (RunnableEtcMixin) runnable;
            return runnableMixin.glowroot$getAuxContext() != null;
        }

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(@Bind.This Runnable runnable) {

            RunnableEtcMixin runnableMixin = (RunnableEtcMixin) runnable;
            AuxThreadContext auxContext = runnableMixin.glowroot$getAuxContext();
            if (auxContext == null) {
                // this is unlikely (since checked in @Advice.IsEnabled) but possible under
                // concurrency
                return null;
            }
            runnableMixin.glowroot$setAuxContext(null);
            return auxContext.start();
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    // the nesting group only starts applying once auxiliary thread context is started (it does not
    // apply to OptionalThreadContext that miss)
    @Advice.Pointcut(className = "java.util.concurrent.Callable",
                     methodName = "call",
                     methodParameterTypes = {},
                     nestingGroup = "executor-run")
    public static class CallableAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This Callable<?> callable) {

            if (!(callable instanceof RunnableEtcMixin)) {
                // this class was loaded before class file transformer was added to jvm
                return false;
            }
            RunnableEtcMixin callableMixin = (RunnableEtcMixin) callable;
            return callableMixin.glowroot$getAuxContext() != null;
        }

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(@Bind.This Callable<?> callable) {

            RunnableEtcMixin callableMixin = (RunnableEtcMixin) callable;
            AuxThreadContext auxContext = callableMixin.glowroot$getAuxContext();
            if (auxContext == null) {
                // this is unlikely (since checked in @Advice.IsEnabled) but possible under
                // concurrency
                return null;
            }
            callableMixin.glowroot$setAuxContext(null);
            return auxContext.start();
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    // need to clear out the "executor-execute" nesting group, see
    // ExecutorWithLambdasIT.shouldCaptureNestedExecute()
    @Advice.Pointcut(className = "org.glowroot.instrumentation.executor.RunnableWrapper",
                     methodName = "run",
                     methodParameterTypes = {},
                     nestingGroup = "executor-run")
    public static class RunnableWrapperAdvice {}

    // need to clear out the "executor-execute" nesting group, see
    // ExecutorWithLambdasIT.shouldCaptureNestedSubmit()
    @Advice.Pointcut(className = "org.glowroot.instrumentation.executor.CallableWrapper",
                     methodName = "call",
                     methodParameterTypes = {},
                     nestingGroup = "executor-run")
    public static class CallableWrapperAdvice {}

    // the nesting group only starts applying once auxiliary thread context is started (it does not
    // apply to OptionalThreadContext that miss)
    @Advice.Pointcut(
                     className = "java.util.concurrent.ForkJoinTask|akka.jsr166y.ForkJoinTask"
                             + "|scala.concurrent.forkjoin.ForkJoinTask",
                     methodName = "exec",
                     methodParameterTypes = {},
                     nestingGroup = "executor-run")
    public static class ExecAdvice {

        @Advice.IsEnabled
        public static boolean isEnabled(@Bind.This Object task) {

            if (!(task instanceof RunnableEtcMixin)) {
                // this class was loaded before class file transformer was added to jvm
                return false;
            }
            RunnableEtcMixin taskMixin = (RunnableEtcMixin) task;
            return taskMixin.glowroot$getAuxContext() != null;
        }

        @Advice.OnMethodBefore
        public static @Nullable Span onBefore(@Bind.This Object task) {

            RunnableEtcMixin taskMixin = (RunnableEtcMixin) task;
            AuxThreadContext auxContext = taskMixin.glowroot$getAuxContext();
            if (auxContext == null) {
                // this is unlikely (since checked in @Advice.IsEnabled) but possible under
                // concurrency
                return null;
            }
            taskMixin.glowroot$setAuxContext(null);
            return auxContext.start();
        }

        @Advice.OnMethodReturn
        public static void onReturn(@Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.end();
            }
        }

        @Advice.OnMethodThrow
        public static void onThrow(@Bind.Thrown Throwable t,
                @Bind.Enter @Nullable Span span) {

            if (span != null) {
                span.endWithError(t);
            }
        }
    }

    private static void onBeforeWithRunnableHolder(ParameterHolder<Runnable> runnableHolder,
            ThreadContext context) {
        Runnable runnable = runnableHolder.get();
        if (runnable instanceof SuppressedRunnableMixin) {
            return;
        } else if (runnable instanceof RunnableEtcMixin) {
            onBeforeCommon((RunnableEtcMixin) runnable, context);
        } else if (runnable != null && runnable.getClass().getName().contains("$$Lambda$")) {
            wrapRunnable(runnableHolder, context);
        }
    }

    private static <T> void onBeforeWithCallableHolder(ParameterHolder<Callable<T>> callableHolder,
            ThreadContext context) {
        Callable<T> callable = callableHolder.get();
        if (callable instanceof RunnableEtcMixin) {
            onBeforeCommon((RunnableEtcMixin) callable, context);
        } else if (callable != null && callable.getClass().getName().contains("$$Lambda$")) {
            wrapCallable(callableHolder, context);
        }
    }

    private static void onThreadInitCommon(Thread thread, ThreadContext context) {
        if (thread instanceof RunnableEtcMixin && !(thread instanceof SuppressedRunnableMixin)) {
            onBeforeCommon((RunnableEtcMixin) thread, context);
        }
    }

    private static boolean onThreadInitCommon(ParameterHolder<Runnable> runnableHolder,
            ThreadContext context) {
        Runnable runnable = runnableHolder.get();
        if (!(runnable instanceof SuppressedRunnableMixin)) {
            if (runnable instanceof RunnableEtcMixin) {
                onBeforeCommon((RunnableEtcMixin) runnable, context);
                return true;
            } else if (runnable != null && runnable.getClass().getName().contains("$$Lambda$")) {
                wrapRunnable(runnableHolder, context);
                return true;
            }
        }
        return false;
    }

    private static void onBeforeCommon(RunnableEtcMixin runnableEtc, ThreadContext context) {
        RunnableEtcMixin runnableMixin = runnableEtc;
        AuxThreadContext auxContext = context.createAuxThreadContext();
        runnableMixin.glowroot$setAuxContext(auxContext);
    }

    private static void wrapRunnable(ParameterHolder<Runnable> runnableHolder,
            ThreadContext context) {
        Runnable runnable = runnableHolder.get();
        if (runnable != null) {
            runnableHolder.set(new RunnableWrapper(runnable, context.createAuxThreadContext()));
        }
    }

    private static <T> void wrapCallable(ParameterHolder<Callable<T>> callableHolder,
            ThreadContext context) {
        Callable<T> callable = callableHolder.get();
        if (callable != null) {
            callableHolder.set(new CallableWrapper<T>(callable, context.createAuxThreadContext()));
        }
    }

    // ========== debug ==========

    // KEEP THIS CODE IT IS VERY USEFUL

    // private static final ThreadLocal<?> inAuxDebugLogging;
    //
    // static {
    // try {
    // Class<?> clazz = Class.forName("org.glowroot.agent.impl.AuxThreadContextImpl");
    // Field field = clazz.getDeclaredField("inAuxDebugLogging");
    // field.setAccessible(true);
    // inAuxDebugLogging = (ThreadLocal<?>) field.get(null);
    // } catch (Exception e) {
    // throw new IllegalStateException(e);
    // }
    // }
    //
    // @Advice.Pointcut(className = "/(?!org.glowroot).*/", methodName = "<init>",
    // methodParameterTypes = {".."})
    // public static class RunnableInitAdvice {
    //
    // @Advice.OnMethodAfter
    // public static void onAfter(OptionalThreadContext context, @Advice.This Object obj) {
    // if (obj instanceof Runnable && inAuxDebugLogging.get() == null) {
    // new Exception(
    // "Init " + Thread.currentThread().getName() + " " + obj.getClass().getName()
    // + ":" + obj.hashCode() + " " + context.getClass().getName())
    // .printStackTrace();
    // }
    // }
    // }
    //
    // @Advice.Pointcut(className = "java.lang.Runnable", methodName = "run", methodParameterTypes =
    // {},
    // order = 1)
    // public static class RunnableRunAdvice {
    //
    // @Advice.OnMethodBefore
    // public static void onBefore(OptionalThreadContext context, @Advice.This Runnable obj)
    // {
    // new Exception("Run " + Thread.currentThread().getName() + " " + obj.getClass().getName()
    // + ":" + obj.hashCode() + " " + context.getClass().getName()).printStackTrace();
    // }
    // }
}
