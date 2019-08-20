/*
 * Copyright 2014-2019 the original author or authors.
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
package org.glowroot.instrumentation.log4j;

import java.io.Serializable;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LoggerSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TransactionMarker;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4j1xIT {

    private static final String INSTRUMENTATION_ID = "log4j";

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
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
        assertThat(loggerSpan.message()).isEqualTo("def");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLogWithThreshold() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID, "threshold", "error");

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLog.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name", ShouldLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
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
        assertThat(loggerSpan.message()).isEqualTo("def_");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("456");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
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
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh_");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("678");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
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
        assertThat(loggerSpan.message()).isEqualTo("def_");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg_");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh_");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithNullThrowable.class.getName());

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
        assertThat(loggerSpan.message()).isEqualTo("def__");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriority.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg__");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriority.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh__");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
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
        assertThat(loggerSpan.message()).isEqualTo("def___");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("456_");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
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
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh___");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("678_");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
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
        assertThat(loggerSpan.message()).isEqualTo("def___null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg___null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh___null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLogWithPriorityAndNullThrowable.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLocalizedLog() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ShouldLocalizedLog.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg____");
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.error().exception().type()).isEqualTo(IllegalStateException.class);
        assertThat(incomingSpan.error().exception().message()).isEqualTo("567__");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("456__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("567__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLog.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("678__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLog.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLocalizedLogWithNullThrowable() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan = container.execute(ShouldLocalizedLogWithNullThrowable.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg____null");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def____null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg____null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh____null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithNullThrowable.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLocalizedLogWithParameters() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ShouldLocalizedLogWithParameters.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg____");
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.error().exception().type()).isEqualTo(IllegalStateException.class);
        assertThat(incomingSpan.error().exception().message()).isEqualTo("567__");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("456__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("567__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("678__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParameters.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLocalizedLogWithEmptyParameters() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(ShouldLocalizedLogWithEmptyParameters.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg____");
        assertThat(incomingSpan.error().exception()).isNotNull();
        assertThat(incomingSpan.error().exception().type()).isEqualTo(IllegalStateException.class);
        assertThat(incomingSpan.error().exception().message()).isEqualTo("567__");

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("456__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithEmptyParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("567__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithEmptyParameters.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh____");
        assertThat(loggerSpan.throwable()).isNotNull();
        assertThat(loggerSpan.throwable().type()).isEqualTo(IllegalStateException.class);
        assertThat(loggerSpan.throwable().message()).isEqualTo("678__");
        assertThat(loggerSpan.throwable().stackTrace()[0].getMethodName())
                .isEqualTo("transactionMarker");
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithEmptyParameters.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    @Test
    public void testLocalizedLogWithParametersAndNullThrowable() throws Exception {
        // given
        container.setInstrumentationProperty(INSTRUMENTATION_ID,
                "traceErrorOnErrorWithoutThrowable", true);

        // when
        IncomingSpan incomingSpan =
                container.execute(ShouldLocalizedLogWithParametersAndNullThrowable.class);

        // then
        assertThat(incomingSpan.error()).isNotNull();
        assertThat(incomingSpan.error().message()).isEqualTo("efg____null");
        assertThat(incomingSpan.error().exception()).isNull();

        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LoggerSpan loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("def____null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "WARN");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParametersAndNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("efg____null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "ERROR");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParametersAndNullThrowable.class.getName());

        loggerSpan = (LoggerSpan) i.next();
        assertThat(loggerSpan.message()).isEqualTo("fgh____null");
        assertThat(loggerSpan.throwable()).isNull();
        assertThat(loggerSpan.detail()).hasSize(2);
        assertThat(loggerSpan.detail()).containsEntry("Level", "FATAL");
        assertThat(loggerSpan.detail()).containsEntry("Logger name",
                ShouldLocalizedLogWithParametersAndNullThrowable.class.getName());

        assertThat(i.hasNext()).isFalse();
    }

    public static class ShouldLog implements AppUnderTest, TransactionMarker {

        private static final Logger logger = Logger.getLogger(ShouldLog.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.debug("bcd");
            logger.info("cde");
            logger.warn("def");
            logger.error("efg");
            logger.fatal("fgh");
        }
    }

    public static class ShouldLogWithThrowable implements AppUnderTest, TransactionMarker {

        private static final Logger logger = Logger.getLogger(ShouldLogWithThrowable.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.debug("bcd_", new IllegalStateException("234"));
            logger.info("cde_", new IllegalStateException("345"));
            logger.warn("def_", new IllegalStateException("456"));
            logger.error("efg_", new IllegalStateException("567"));
            logger.fatal("fgh_", new IllegalStateException("678"));
        }
    }

    public static class ShouldLogWithNullThrowable implements AppUnderTest, TransactionMarker {

        private static final Logger logger = Logger.getLogger(ShouldLogWithNullThrowable.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.debug("bcd_", null);
            logger.info("cde_", null);
            logger.warn("def_", null);
            logger.error("efg_", null);
            logger.fatal("fgh_", null);
        }
    }

    public static class ShouldLogWithPriority implements AppUnderTest, TransactionMarker {

        private static final Logger logger = Logger.getLogger(ShouldLogWithPriority.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            try {
                logger.log(null, "abc__");
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.log(Level.DEBUG, "bcd__");
            logger.log(Level.INFO, "cde__");
            logger.log(Level.WARN, "def__");
            logger.log(Level.ERROR, "efg__");
            logger.log(Level.FATAL, "fgh__");
        }
    }

    public static class ShouldLogWithPriorityAndThrowable
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithPriorityAndThrowable.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            try {
                logger.log(null, "abc___", new IllegalStateException("123_"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.log(Level.DEBUG, "bcd___", new IllegalStateException("234_"));
            logger.log(Level.INFO, "cde___", new IllegalStateException("345_"));
            logger.log(Level.WARN, "def___", new IllegalStateException("456_"));
            logger.log(Level.ERROR, "efg___", new IllegalStateException("567_"));
            logger.log(Level.FATAL, "fgh___", new IllegalStateException("678_"));
        }
    }

    public static class ShouldLogWithPriorityAndNullThrowable
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLogWithPriorityAndNullThrowable.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.log(Level.DEBUG, "bcd___null", null);
            logger.log(Level.INFO, "cde___null", null);
            logger.log(Level.WARN, "def___null", null);
            logger.log(Level.ERROR, "efg___null", null);
            logger.log(Level.FATAL, "fgh___null", null);
        }
    }

    public static class ShouldLocalizedLog implements AppUnderTest, TransactionMarker {

        private static final Logger logger = Logger.getLogger(ShouldLocalizedLog.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            try {
                logger.l7dlog(null, "abc____", new IllegalStateException("123__"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.l7dlog(Level.DEBUG, "bcd____", new IllegalStateException("234__"));
            logger.l7dlog(Level.INFO, "cde____", new IllegalStateException("345__"));
            logger.l7dlog(Level.WARN, "def____", new IllegalStateException("456__"));
            logger.l7dlog(Level.ERROR, "efg____", new IllegalStateException("567__"));
            logger.l7dlog(Level.FATAL, "fgh____", new IllegalStateException("678__"));
        }
    }

    public static class ShouldLocalizedLogWithNullThrowable
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithNullThrowable.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.l7dlog(Level.DEBUG, "bcd____null", null);
            logger.l7dlog(Level.INFO, "cde____null", null);
            logger.l7dlog(Level.WARN, "def____null", null);
            logger.l7dlog(Level.ERROR, "efg____null", null);
            logger.l7dlog(Level.FATAL, "fgh____null", null);
        }
    }

    public static class ShouldLocalizedLogWithParameters
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithParameters.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            try {
                logger.l7dlog(null, "abc____", new Object[] {"a", "b", "c"},
                        new IllegalStateException("123__"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.l7dlog(Level.DEBUG, "bcd____", new Object[] {"b", "c", "d"},
                    new IllegalStateException("234__"));
            logger.l7dlog(Level.INFO, "cde____", new Object[] {"c", "d", "e"},
                    new IllegalStateException("345__"));
            logger.l7dlog(Level.WARN, "def____", new Object[] {"d", "e", "f"},
                    new IllegalStateException("456__"));
            logger.l7dlog(Level.ERROR, "efg____", new Object[] {"e", "f", "g"},
                    new IllegalStateException("567__"));
            logger.l7dlog(Level.FATAL, "fgh____", new Object[] {"f", "g", "h"},
                    new IllegalStateException("678__"));
        }
    }

    public static class ShouldLocalizedLogWithEmptyParameters
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithEmptyParameters.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            try {
                logger.l7dlog(null, "abc____", new Object[] {"a", "b", "c"},
                        new IllegalStateException("123__"));
            } catch (NullPointerException e) {
                // re-throw if it does not originate from log4j
                if (!e.getStackTrace()[0].getClassName().startsWith("org.apache.log4j.")) {
                    throw e;
                }
            }
            logger.l7dlog(Level.DEBUG, "bcd____", new Object[] {},
                    new IllegalStateException("234__"));
            logger.l7dlog(Level.INFO, "cde____", new Object[] {},
                    new IllegalStateException("345__"));
            logger.l7dlog(Level.WARN, "def____", new Object[] {},
                    new IllegalStateException("456__"));
            logger.l7dlog(Level.ERROR, "efg____", new Object[] {},
                    new IllegalStateException("567__"));
            logger.l7dlog(Level.FATAL, "fgh____", new Object[] {},
                    new IllegalStateException("678__"));
        }
    }

    public static class ShouldLocalizedLogWithParametersAndNullThrowable
            implements AppUnderTest, TransactionMarker {

        private static final Logger logger =
                Logger.getLogger(ShouldLocalizedLogWithParametersAndNullThrowable.class);

        @Override
        public void executeApp(Serializable... args) {
            transactionMarker();
        }

        @Override
        public void transactionMarker() {
            logger.l7dlog(Level.DEBUG, "bcd____null", new Object[] {"b_", "c_", "d_"}, null);
            logger.l7dlog(Level.INFO, "cde____null", new Object[] {"c_", "d_", "e_"}, null);
            logger.l7dlog(Level.WARN, "def____null", new Object[] {"d_", "e_", "f_"}, null);
            logger.l7dlog(Level.ERROR, "efg____null", new Object[] {"e_", "f_", "g_"}, null);
            logger.l7dlog(Level.FATAL, "fgh____null", new Object[] {"f_", "g_", "h_"}, null);
        }
    }
}
