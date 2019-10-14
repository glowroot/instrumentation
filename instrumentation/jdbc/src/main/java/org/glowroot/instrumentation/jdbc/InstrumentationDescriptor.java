package org.glowroot.instrumentation.jdbc;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "jdbc",
            name = "JDBC",
            properties = {
                    @Descriptor.Property(
                                         name = "captureBindParametersIncludes",
                                         type = PropertyType.LIST,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          listValue = {
                                                                                  ".*"})
                                         },
                                         label = "Capture bind parameters for these queries",
                                         description = "List of regular expressions. If a query matches one or more of these regular expressions and also does not match any of the regular expressions in the \"do not capture\" list below, then its jdbc bind parameters will be captured when it is executed as a PreparedStatement."),
                    @Descriptor.Property(
                                         name = "captureBindParametersExcludes",
                                         type = PropertyType.LIST,
                                         label = "Do not capture bind parameters for these queries",
                                         description = "List of regular expressions. If a query matches one or more of these regular expressions then its jdbc bind parameters will NOT be captured when it is executed as a PreparedStatement, even if the query matches one or more of the regular expressions in the \"capture\" list above."),
                    @Descriptor.Property(
                                         name = "captureResultSetNavigate",
                                         type = PropertyType.BOOLEAN,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          booleanValue = true)
                                         },
                                         label = "ResultSet navigation",
                                         checkboxLabel = "Capture timings for ResultSet navigation",
                                         description = "Capture timings for executions of ResultSet.next()/previous()/relative()/etc to read the next record in a result set. This defaults to true since the timing for this timer is a good indication of jdbc fetch size issues and database latency, but it can be disabled if it is common to return millions of records and the overhead of System.nanoTime() becomes relevant."),
                    @Descriptor.Property(
                                         name = "captureResultSetGet",
                                         type = PropertyType.BOOLEAN,
                                         label = "ResultSet values",
                                         checkboxLabel = "Capture timings for ResultSet value retrieval",
                                         description = "Capture timings for executions of ResultSet.get*() to read the individual column values out of a result set record. This defaults to false since the number of calls can be excessive and generally ResultSet.next() captures more interesting info with less overhead."),
                    @Descriptor.Property(
                                         name = "captureConnectionPoolLeaks",
                                         type = PropertyType.BOOLEAN,
                                         label = "Connection pool leaks",
                                         checkboxLabel = "Capture connection pool leaks",
                                         description = "Mark transactions as errors if they fail to return a connection back to a connection pool. This feature is experimental (currently generates false positives in some environments)."),
                    @Descriptor.Property(
                                         name = "captureConnectionPoolLeakDetails",
                                         type = PropertyType.BOOLEAN,
                                         label = "Connection pool leak details",
                                         checkboxLabel = "Capture stack trace location of connection pool leaks",
                                         description = "Capture the stack trace location for any connection borrowed from a connection pool that is not returned back to the pool. This defaults to false since it requires capturing a stack trace every time a connection is borrowed, which has non-negligible overhead."),
                    @Descriptor.Property(
                                         name = "captureGetConnection",
                                         type = PropertyType.BOOLEAN,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          booleanValue = true)
                                         },
                                         label = "Get connection",
                                         checkboxLabel = "Capture timings for DataSource.getConnection()",
                                         description = "Capture timings for executions of DataSource.getConnection()."),
                    @Descriptor.Property(
                                         name = "captureConnectionClose",
                                         type = PropertyType.BOOLEAN,
                                         label = "Connection close",
                                         checkboxLabel = "Capture timings for Connection.close()",
                                         description = "Capture timings for executions of Connection.close()."),
                    @Descriptor.Property(
                                         name = "capturePreparedStatementCreation",
                                         type = PropertyType.BOOLEAN,
                                         label = "Prepared statement creation",
                                         checkboxLabel = "Capture timings for Connection.prepare*()",
                                         description = "Capture timings for executions of Connection.prepareStatement() and Connection.prepareCall()."),
                    @Descriptor.Property(
                                         name = "captureStatementClose",
                                         type = PropertyType.BOOLEAN,
                                         label = "Statement close",
                                         checkboxLabel = "Capture timings for Statement.close()",
                                         description = "Capture timings for executions of Statement.close()."),
                    @Descriptor.Property(
                                         name = "captureTransactionLifecycleTraceEntries",
                                         type = PropertyType.BOOLEAN,
                                         label = "Transaction lifecycle",
                                         checkboxLabel = "Capture trace entries for Connection.setAutoCommit()",
                                         description = "Capture trace entries for executions of Connection.setAutoCommit()"),
                    @Descriptor.Property(
                                         name = "captureConnectionLifecycleTraceEntries",
                                         type = PropertyType.BOOLEAN,
                                         label = "Connection lifecycle",
                                         checkboxLabel = "Capture trace entries for Connection opening and closing",
                                         description = "Capture trace entries for executions of DataSource.getConnection() and Connection.close()"),
                    @Descriptor.Property(
                                         name = "stackTraceThresholdMillis",
                                         type = PropertyType.DOUBLE,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          doubleValue = 1000.0)
                                         },
                                         label = "Stack trace threshold (millis)",
                                         description = "Any jdbc call that exceeds this threshold will have a stack trace captured and attached to it. An empty value will not collect any stack traces, a zero value will collect a stack trace for every jdbc call."),
                    @Descriptor.Property(
                                         name = "explainPlanThresholdMillis",
                                         type = PropertyType.DOUBLE,
                                         label = "Explain plan threshold (millis)",
                                         description = "Any statement execution (not including prepared statements) that exceeds this threshold will have an explain plan captured and attached to it. An empty value will not collect any explain plans, a zero value will collect an explain plan for every jdbc statement execution.")
            },
            classes = {
                    StatementInstrumentation.class,
                    ResultSetInstrumentation.class,
                    ConnectionInstrumentation.class,
                    DataSourceInstrumentation.class,
                    ObjectPoolInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
