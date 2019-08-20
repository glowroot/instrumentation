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
package org.glowroot.instrumentation.jul;

import java.io.Serializable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LoggerSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;
import org.glowroot.instrumentation.test.harness.impl.JavaagentContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaUtilLoggingIT {

    private static final String INSTRUMENTATION_ID = "java-util-logging";

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = JavaagentContainer.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        // need null check in case assumption is false in setUp()
        if (container != null) {
            container.close();
        }
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
    }

    @Test
    public void testLog() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLog.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("cde");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithThreshold() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "threshold", "warning");

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLog.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithThrowable() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLogWithThrowable.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg_");
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.error().exception().type()).isEqualTo(IllegalStateException.class);
        assertThat(incomingSpan.error().exception().message()).isEqualTo("567");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("cde_");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("345");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def_");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("456");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg_");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("567");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithThrowable.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithNullThrowable() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLogWithNullThrowable.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg_");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("cde_");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def_");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg_");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithNullThrowable.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithLogRecord() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLogWithLogRecord.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg__");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("cde__");
        assertThat(loggerSpan.detail()).hasSize(1);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def__");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithLogRecord.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg__");
        assertThat(loggerSpan.detail()).hasSize(1);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithPriority() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLogWithPriority.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg__");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("cde__");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriority.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def__");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriority.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg__");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriority.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithPriorityAndThrowable() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ShouldLogWithPriorityAndThrowable.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg___");
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.error().exception().type()).isEqualTo(IllegalStateException.class);
        assertThat(incomingSpan.error().exception().message()).isEqualTo("567_");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("cde___");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("345_");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def___");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("456_");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg___");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("567_");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndThrowable.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithPriorityAndNullThrowable() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLogWithPriorityAndNullThrowable.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg___null");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("cde___null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def___null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg___null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndNullThrowable.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithParameters() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLogWithParameters.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("ghi_78_89");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg_56_67");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh_67_78");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("ghi_78_89");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithParameters.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLocalizedLogWithParameters() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLocalizedLogWithParameters.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("abc_78_89");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("abc_56_67");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "INFO");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("xyz_78_67");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARNING");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("abc_78_89");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "SEVERE");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParameters.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    public static class ShouldLog implements AppUnderTest, TransactionMarker {

        private static final Logger logger = Logger.getLogger(ShouldLog.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.finest("wxy");
            logger.finer("xyz");
            logger.fine("abc");
            logger.config("bcd");
            logger.info("cde");
            logger.warning("def");
            logger.severe("efg");
        }
    }

    public static class ShouldLogWithThrowable implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithThrowable.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.log(Level.FINEST, "wxy_", new IllegalStateException("678"));
            logger.log(Level.FINER, "xyz_", new IllegalStateException("789"));
            logger.log(Level.FINE, "abc_", new IllegalStateException("123"));
            logger.log(Level.CONFIG, "bcd_", new IllegalStateException("234"));
            logger.log(Level.INFO, "cde_", new IllegalStateException("345"));
            logger.log(Level.WARNING, "def_", new IllegalStateException("456"));
            logger.log(Level.SEVERE, "efg_", new IllegalStateException("567"));
        }
    }

    public static class ShouldLogWithNullThrowable implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithNullThrowable.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.log(Level.FINEST, "wxy_", (Throwable) null);
            logger.log(Level.FINER, "xyz_", (Throwable) null);
            logger.log(Level.FINE, "abc_", (Throwable) null);
            logger.log(Level.CONFIG, "bcd_", (Throwable) null);
            logger.log(Level.INFO, "cde_", (Throwable) null);
            logger.log(Level.WARNING, "def_", (Throwable) null);
            logger.log(Level.SEVERE, "efg_", (Throwable) null);
        }
    }

    public static class ShouldLogWithPriority implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithPriority.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            try {
                logger.log(null, "abc__");
            } catch (NullPointerException e) {
            }
            logger.log(Level.FINEST, "vwx__");
            logger.log(Level.FINER, "wxy__");
            logger.log(Level.FINE, "xyz__");
            logger.log(Level.CONFIG, "bcd__");
            logger.log(Level.INFO, "cde__");
            logger.log(Level.WARNING, "def__");
            logger.log(Level.SEVERE, "efg__");
        }
    }

    public static class ShouldLogWithPriorityAndThrowable
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithPriorityAndThrowable.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            try {
                logger.log(null, "abc___", new IllegalStateException("123_"));
            } catch (NullPointerException e) {
            }
            logger.log(Level.FINEST, "vwx___", new IllegalStateException("111_"));
            logger.log(Level.FINER, "wxy___", new IllegalStateException("222_"));
            logger.log(Level.FINE, "xwy___", new IllegalStateException("333_"));
            logger.log(Level.CONFIG, "bcd___", new IllegalStateException("234_"));
            logger.log(Level.INFO, "cde___", new IllegalStateException("345_"));
            logger.log(Level.WARNING, "def___", new IllegalStateException("456_"));
            logger.log(Level.SEVERE, "efg___", new IllegalStateException("567_"));
        }
    }

    public static class ShouldLogWithPriorityAndNullThrowable
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithPriorityAndNullThrowable.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.log(Level.FINEST, "vwx___null", (Throwable) null);
            logger.log(Level.FINER, "wxy___null", (Throwable) null);
            logger.log(Level.FINE, "xyz___null", (Throwable) null);
            logger.log(Level.CONFIG, "bcd___null", (Throwable) null);
            logger.log(Level.INFO, "cde___null", (Throwable) null);
            logger.log(Level.WARNING, "def___null", (Throwable) null);
            logger.log(Level.SEVERE, "efg___null", (Throwable) null);
        }
    }

    public static class ShouldLogWithParameters implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithParameters.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.log(Level.FINEST, "abc_{0}_{1}", new Object[] {12, 23});
            logger.log(Level.FINER, "bcd_{0}_{1}", new Object[] {23, 34});
            logger.log(Level.FINE, "cde_{0}_{1}", new Object[] {34, 45});
            logger.log(Level.CONFIG, "def_{0}_{1}", new Object[] {45, 56});
            logger.log(Level.INFO, "efg_{0}_{1}", new Object[] {56, 67});
            logger.log(Level.WARNING, "fgh_{0}_{1}", new Object[] {67, 78});
            logger.log(Level.SEVERE, "ghi_{0}_{1}", new Object[] {78, 89});
        }
    }

    public static class ShouldLocalizedLogWithParameters
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithParameters.class.getName(), "julmsgs");

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.log(Level.FINEST, "log.message.key1", new Object[] {12, 23});
            logger.log(Level.FINER, "log.message.key2", new Object[] {23, 34});
            logger.log(Level.FINE, "log.message.key1", new Object[] {34, 45});
            logger.log(Level.CONFIG, "log.message.key2", new Object[] {45, 56});
            logger.log(Level.INFO, "log.message.key1", new Object[] {56, 67});
            logger.log(Level.WARNING, "log.message.key2", new Object[] {67, 78});
            logger.log(Level.SEVERE, "log.message.key1", new Object[] {78, 89});
        }
    }

    public static class ShouldLogWithLogRecord implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithLogRecord.class.getName());

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.log(new LogRecord(Level.FINEST, "vwx__"));
            logger.log(new LogRecord(Level.FINER, "wxy__"));
            logger.log(new LogRecord(Level.FINE, "xyz__"));
            logger.log(new LogRecord(Level.CONFIG, "bcd__"));
            logger.log(new LogRecord(Level.INFO, "cde__"));
            LogRecord lr = new LogRecord(Level.WARNING, "def__");
            lr.setLoggerName(logger.getName()); // test logger name
            logger.log(lr);
            logger.log(new LogRecord(Level.SEVERE, "efg__"));
        }
    }
}
