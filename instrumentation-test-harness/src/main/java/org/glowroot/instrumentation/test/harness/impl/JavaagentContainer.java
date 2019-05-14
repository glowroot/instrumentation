/*
 * Copyright 2011-2019 the original author or authors.
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
package org.glowroot.instrumentation.test.harness.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.agent.Premain;
import org.glowroot.instrumentation.test.harness.util.ConsoleOutputPipe;
import org.glowroot.instrumentation.test.harness.util.TempDirs;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class JavaagentContainer implements Container {

    private static final boolean XDEBUG = Boolean.getBoolean("test.harness.xdebug");

    private static final Logger logger = LoggerFactory.getLogger(JavaagentContainer.class);

    private final HeartbeatListener heartbeatListener;
    private final ExecutorService heartbeatListenerExecutor;
    private final File tmpDir;
    private final TraceCollector traceCollector;
    private final JavaagentClient javaagentClient;
    private final ExecutorService consolePipeExecutor;
    private final Future<?> consolePipeFuture;
    private final Process process;
    private final ConsoleOutputPipe consoleOutputPipe;
    private final Thread shutdownHook;

    public static JavaagentContainer create() throws Exception {
        return new JavaagentContainer(ImmutableList.<String>of());
    }

    public static JavaagentContainer createWithExtraJvmArgs(List<String> extraJvmArgs)
            throws Exception {
        return new JavaagentContainer(extraJvmArgs);
    }

    private JavaagentContainer(List<String> extraJvmArgs) throws Exception {

        // need to start heartbeat socket listener before spawning process
        heartbeatListener = new HeartbeatListener();
        heartbeatListenerExecutor = Executors.newSingleThreadExecutor();
        heartbeatListenerExecutor.execute(heartbeatListener);

        traceCollector = new TraceCollector();
        traceCollector.start();
        tmpDir = TempDirs.createTempDir("harness-dir");
        List<String> command = buildCommand(heartbeatListener.serverSocket.getLocalPort(),
                traceCollector.getPort(), tmpDir, extraJvmArgs);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        consolePipeExecutor = Executors.newSingleThreadExecutor();
        InputStream in = process.getInputStream();
        // process.getInputStream() only returns null if ProcessBuilder.redirectOutput() is used
        // to redirect output to a file
        checkNotNull(in);
        consoleOutputPipe = new ConsoleOutputPipe(in, System.out);
        consolePipeFuture = consolePipeExecutor.submit(consoleOutputPipe);
        this.process = process;

        Stopwatch stopwatch = Stopwatch.createStarted();
        javaagentClient = connectToJavaagent(heartbeatListener.getJavaagentServerPort(), stopwatch);
        javaagentClient.resetInstrumentationProperties();
        shutdownHook = new ShutdownHookThread(javaagentClient);
        // unfortunately, ctrl-c during maven test will kill the maven process, but won't kill the
        // forked surefire jvm where the tests are being run
        // (http://jira.codehaus.org/browse/SUREFIRE-413), and so this hook won't get triggered by
        // ctrl-c while running tests under maven
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private static JavaagentClient connectToJavaagent(int javaagentServerPort, Stopwatch stopwatch)
            throws Exception {
        // this can take a while on slow travis ci build machines
        Exception lastException = null;
        while (stopwatch.elapsed(SECONDS) < 30) {
            try {
                return new JavaagentClient(javaagentServerPort);
            } catch (Exception e) {
                logger.debug(e.getMessage(), e);
                lastException = e;
            }
            MILLISECONDS.sleep(10);
        }
        throw checkNotNull(lastException);
    }

    @Override
    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            boolean propertyValue) throws Exception {
        javaagentClient.setInstrumentationProperty(instrumentationId, propertyName, propertyValue);
    }

    @Override
    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            Double propertyValue) throws Exception {
        javaagentClient.setInstrumentationProperty(instrumentationId, propertyName, propertyValue);
    }

    @Override
    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            String propertyValue) throws Exception {
        javaagentClient.setInstrumentationProperty(instrumentationId, propertyName, propertyValue);
    }

    @Override
    public void setInstrumentationProperty(String instrumentationId, String propertyName,
            List<String> propertyValue) throws Exception {
        // converting to ArrayList since guava ImmutableList may be shaded in javaagent and so would
        // error on de-serialization
        javaagentClient.setInstrumentationProperty(instrumentationId, propertyName,
                new ArrayList<String>(propertyValue));
    }

    @Override
    public IncomingSpan execute(Class<? extends AppUnderTest> appClass, Serializable... args)
            throws Exception {
        return executeInternal(appClass, null, null, args);
    }

    @Override
    public IncomingSpan executeForType(Class<? extends AppUnderTest> appClass,
            String transactionType, Serializable... args) throws Exception {
        return executeInternal(appClass, transactionType, null, args);
    }

    @Override
    public IncomingSpan executeForTypeAndName(Class<? extends AppUnderTest> appClass,
            String transactionType, String transactionName, Serializable... args) throws Exception {
        return executeInternal(appClass, transactionType, transactionName, args);
    }

    @Override
    public void executeNoExpectedTrace(Class<? extends AppUnderTest> appClass, Serializable... args)
            throws Exception {
        executeInternal(appClass, args);
        // give a short time to see if trace gets collected
        MILLISECONDS.sleep(10);
        if (traceCollector != null && traceCollector.hasIncomingSpan()) {
            throw new IllegalStateException("Trace was collected when none was expected");
        }
    }

    @Override
    public void resetAfterEachTest() throws Exception {
        javaagentClient.resetInstrumentationProperties();
    }

    @Override
    public void close() throws Exception {
        heartbeatListener.closed = true;
        javaagentClient.kill();
        traceCollector.close();
        process.waitFor();
        consolePipeFuture.get();
        consolePipeExecutor.shutdown();
        if (!consolePipeExecutor.awaitTermination(10, SECONDS)) {
            throw new IllegalStateException("Could not terminate executor");
        }
        heartbeatListenerExecutor.shutdown();
        if (!heartbeatListenerExecutor.awaitTermination(10, SECONDS)) {
            throw new IllegalStateException("Could not terminate executor");
        }
        heartbeatListener.serverSocket.close();
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        TempDirs.deleteRecursively(tmpDir);
    }

    private IncomingSpan executeInternal(Class<? extends AppUnderTest> appClass,
            @Nullable String transactionType, @Nullable String transactionName,
            Serializable... args) throws Exception {
        checkNotNull(traceCollector);
        executeInternal(appClass, args);
        // extra long wait time is needed for StackOverflowOOMIT on slow travis ci machines since it
        // can sometimes take a long time for that large trace to be serialized and transferred
        IncomingSpan incomingSpan =
                traceCollector.getCompletedIncomingSpan(transactionType, transactionName, 20,
                        SECONDS);
        traceCollector.clearIncomingSpans();
        return incomingSpan;
    }

    private void executeInternal(Class<? extends AppUnderTest> appUnderTestClass,
            Serializable[] args) throws Exception {
        javaagentClient.executeApp(appUnderTestClass.getName(), args);
    }

    private static List<String> buildCommand(int heartbeatPort, int collectorPort, File tmpDir,
            List<String> extraJvmArgs) throws Exception {
        List<String> command = Lists.newArrayList();
        String javaExecutable = StandardSystemProperty.JAVA_HOME.value() + File.separator + "bin"
                + File.separator + "java";
        command.add(javaExecutable);
        boolean hasXmx = false;
        for (String extraJvmArg : extraJvmArgs) {
            command.add(extraJvmArg);
            if (extraJvmArg.startsWith("-Xmx")) {
                hasXmx = true;
            }
        }
        // it is important for jacoco javaagent to be prior to the test harness javaagent so that
        // jacoco will use original class bytes to form its class id at runtime which will then
        // match up with the class id at analysis time
        command.addAll(getJacocoArgsFromCurrentJvm());
        String classpath = Strings.nullToEmpty(StandardSystemProperty.JAVA_CLASS_PATH.value());
        List<String> bootPaths = Lists.newArrayList();
        List<String> paths = Lists.newArrayList();
        List<String> maybeBootPaths = Lists.newArrayList();
        File delegatingJavaagentJarFile = null;
        File javaagentJarFile = null;
        for (String path : Splitter.on(File.pathSeparatorChar).split(classpath)) {
            File file = new File(path);
            String name = file.getName();
            String targetClasses = File.separator + "target" + File.separator + "classes";
            if (name.matches("delegating-javaagent-[0-9.]+(-SNAPSHOT)?.jar")) {
                delegatingJavaagentJarFile = file;
            } else if (name.matches("instrumentation-test-harness-[0-9.]+(-SNAPSHOT)?.jar")) {
                javaagentJarFile = file;
            } else if (name.matches("instrumentation-api-[0-9.]+(-SNAPSHOT)?.jar")
                    || name.matches("instrumentation-engine-[0-9.]+(-SNAPSHOT)?.jar")) {
                // these are instrumentation-test-harness transitive dependencies
                maybeBootPaths.add(path);
            } else if (file.getAbsolutePath()
                    .endsWith(File.separator + "instrumentation-api" + targetClasses)
                    || file.getAbsolutePath().endsWith(File.separator + "engine" + targetClasses)) {
                // these are instrumentation-test-harness transitive dependencies
                maybeBootPaths.add(path);
            } else if (name.matches("asm-.*\\.jar")
                    || name.matches("guava-.*\\.jar")
                    || name.matches("gson-.*\\.jar")
                    || name.matches("logback-.*\\.jar")
                    // javax.servlet-api is needed because logback-classic has
                    // META-INF/services/javax.servlet.ServletContainerInitializer
                    || name.matches("javax.servlet-api-.*\\.jar")
                    || name.matches("slf4j-api-.*\\.jar")
                    || name.matches("value-.*\\.jar")
                    // this is needed for now to support reusable ExecuteHttpBase
                    || name.matches("nanohttpd-.*\\.jar")
                    || name.matches("error_prone_annotations-.*\\.jar")
                    || name.matches("jsr305-.*\\.jar")) {
                // these are instrumentation-test-harness transitive dependencies
                maybeBootPaths.add(path);
            } else if (name.endsWith(".jar") && file.getAbsolutePath()
                    .endsWith(File.separator + "target" + File.separator + name)) {
                // this is the instrumentation under test
                bootPaths.add(path);
            } else if (name.matches("instrumentation-[a-z0-9-]+-[0-9.]+(-SNAPSHOT)?.jar")) {
                // this another (core) instrumentation that it depends on, e.g. the executor
                // instrumentation
                bootPaths.add(path);
            } else if (file.getAbsolutePath().endsWith(targetClasses)) {
                // this is the instrumentation under test
                bootPaths.add(path);
            } else if (file.getAbsolutePath()
                    .endsWith(File.separator + "target" + File.separator + "test-classes")) {
                // this is the instrumentation test classes
                paths.add(path);
            } else {
                // these are instrumentation test dependencies
                paths.add(path);
            }
        }
        if (javaagentJarFile == null) {
            bootPaths.addAll(maybeBootPaths);
        } else {
            boolean shaded = false;
            JarInputStream jarIn = new JarInputStream(new FileInputStream(javaagentJarFile));
            try {
                JarEntry jarEntry;
                while ((jarEntry = jarIn.getNextJarEntry()) != null) {
                    if (jarEntry.getName()
                            .startsWith("org/glowroot/instrumentation/test/harness/shaded/")) {
                        shaded = true;
                        break;
                    }
                }
            } finally {
                jarIn.close();
            }
            if (shaded) {
                paths.addAll(maybeBootPaths);
            } else {
                bootPaths.addAll(maybeBootPaths);
            }
        }
        command.add("-Xbootclasspath/a:" + Joiner.on(File.pathSeparatorChar).join(bootPaths));
        command.add("-classpath");
        command.add(Joiner.on(File.pathSeparatorChar).join(paths));
        if (XDEBUG) {
            // the -agentlib arg needs to come before the -javaagent arg
            command.add("-Xdebug");
            command.add("-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y");
        }
        if (javaagentJarFile == null) {
            javaagentJarFile = checkNotNull(delegatingJavaagentJarFile);
        }
        command.add("-javaagent:" + javaagentJarFile + "=" + Premain.class.getName());
        command.add("-Dtest.harness.tmpDir=" + tmpDir.getAbsolutePath());
        command.add("-Dtest.harness.collectorPort=" + collectorPort);
        // this is used inside low-entropy docker containers
        String sourceOfRandomness = System.getProperty("java.security.egd");
        if (sourceOfRandomness != null) {
            command.add("-Djava.security.egd=" + sourceOfRandomness);
        }
        if (!hasXmx) {
            command.add("-Xmx" + Runtime.getRuntime().maxMemory());
        }
        // leave as much memory as possible to old gen
        command.add("-XX:NewRatio=20");
        command.add(JavaagentMain.class.getName());
        command.add(Integer.toString(heartbeatPort));
        return command;
    }

    private static List<String> getJacocoArgsFromCurrentJvm() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMXBean.getInputArguments();
        List<String> jacocoArgs = Lists.newArrayList();
        for (String argument : arguments) {
            if (argument.startsWith("-javaagent:") && argument.contains("jacoco")) {
                jacocoArgs.add(argument + ",includes=org.glowroot.instrumentation.*");
                break;
            }
        }
        return jacocoArgs;
    }

    private static class HeartbeatListener implements Runnable {

        private final ServerSocket serverSocket;

        private final AtomicInteger javaagentServerPort = new AtomicInteger();

        private volatile boolean closed;

        private HeartbeatListener() throws IOException {
            serverSocket = new ServerSocket(0);
        }

        @Override
        public void run() {
            try {
                Socket socket = serverSocket.accept();
                ObjectInputStream socketIn = new ObjectInputStream(socket.getInputStream());
                synchronized (javaagentServerPort) {
                    javaagentServerPort.set(socketIn.readInt());
                    javaagentServerPort.notifyAll();
                }
                ByteStreams.exhaust(socketIn);
            } catch (IOException e) {
                if (!closed) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

        private int getJavaagentServerPort() throws InterruptedException {
            synchronized (javaagentServerPort) {
                while (javaagentServerPort.get() == 0) {
                    javaagentServerPort.wait();
                }
            }
            return javaagentServerPort.get();
        }
    }

    private static class ShutdownHookThread extends Thread {

        private final JavaagentClient javaagentClient;

        private ShutdownHookThread(JavaagentClient javaagentClient) {
            this.javaagentClient = javaagentClient;
        }

        @Override
        public void run() {
            try {
                javaagentClient.kill();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
