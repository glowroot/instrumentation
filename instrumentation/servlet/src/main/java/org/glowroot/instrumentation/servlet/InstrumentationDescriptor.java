package org.glowroot.instrumentation.servlet;

import org.glowroot.instrumentation.api.Descriptor;
import org.glowroot.instrumentation.api.Descriptor.CaptureKind;
import org.glowroot.instrumentation.api.Descriptor.PropertyType;

@Descriptor(
            id = "servlet",
            name = "Servlet",
            properties = {
                    @Descriptor.Property(
                                         name = "sessionUserAttribute",
                                         type = PropertyType.STRING,
                                         label = "Session user attribute",
                                         description = "Session attribute to capture as the user of the trace. Traces can be filtered by user in the explorer. Nested paths are supported, e.g. something.user.username. The attribute value is converted into a String if necessary via toString(). The special attribute name '::id' can be used to refer to the http session id."),
                    @Descriptor.Property(
                                         name = "captureSessionAttributes",
                                         type = PropertyType.LIST,
                                         label = "Session attributes",
                                         description = "List of servlet session attributes to capture in the root trace entry. Nested paths are supported, e.g. mainObject.nestedObject.displayName. '*' at the end of a path is supported, e.g. mainObject.nestedObject.*, meaning capture all properties of mainObject.nestedObject (via reflection, looking at methods that begin with \"get[A-Z]\" or \"is[A-Z]\"). '*' by itself means capture all session attributes. Values are converted into Strings if necessary via toString(). The special attribute name '::id' can be used to refer to the http session id."),
                    @Descriptor.Property(
                                         name = "captureRequestParameters",
                                         type = PropertyType.LIST,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          listValue = {
                                                                                  "*"})
                                         },
                                         label = "Capture request parameters",
                                         description = "List of request parameters to capture in the root trace entry. The wildcard '*' is supported anywhere in the parameter name."),
                    @Descriptor.Property(
                                         name = "maskRequestParameters",
                                         type = PropertyType.LIST,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          listValue = {
                                                                                  "*password*"})
                                         },
                                         label = "Mask request parameters",
                                         description = "List of sensitive request parameters to mask, e.g. passwords. The wildcard '*' is supported anywhere in the parameter name."),
                    @Descriptor.Property(
                                         name = "captureRequestHeaders",
                                         type = PropertyType.LIST,
                                         defaultValue = {
                                                 @Descriptor.DefaultValue(
                                                                          listValue = {
                                                                                  "User-Agent"})
                                         },
                                         label = "Capture request headers",
                                         description = "List of request headers to capture in the root trace entry. The wildcard '*' is supported anywhere in the header name."),
                    @Descriptor.Property(
                                         name = "captureRequestCookies",
                                         type = PropertyType.LIST,
                                         label = "Capture request parameters",
                                         description = "List of request cookies to capture in the root trace entry. The wildcard '*' is supported anywhere in the cookie name."),
                    @Descriptor.Property(
                                         name = "captureResponseHeaders",
                                         type = PropertyType.LIST,
                                         label = "Capture response headers",
                                         description = "List of response headers to capture in the root trace entry. The wildcard '*' is supported anywhere in the header name."),
                    @Descriptor.Property(
                                         name = "traceErrorOn4xxResponseCode",
                                         type = PropertyType.BOOLEAN,
                                         label = "Error on 4xx",
                                         checkboxLabel = "Mark trace as error on 4xx response code",
                                         description = "Mark the trace as an error when a 4xx response code is returned."),
                    @Descriptor.Property(
                                         name = "captureRequestRemoteAddr",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request remote address",
                                         checkboxLabel = "Capture request remote address using ServletRequest.getRemoteAddr()"),
                    @Descriptor.Property(
                                         name = "captureRequestRemoteHostname",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request remote hostname",
                                         checkboxLabel = "Capture request remote hostname using ServletRequest.getRemoteHost()"),
                    @Descriptor.Property(
                                         name = "captureRequestRemotePort",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request remote port",
                                         checkboxLabel = "Capture request remote port using ServletRequest.getRemotePort()",
                                         description = "This only applies to Servlet 3.0+ containers."),
                    @Descriptor.Property(
                                         name = "captureRequestLocalAddr",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request local address",
                                         checkboxLabel = "Capture request local address using ServletRequest.getLocalAddr()",
                                         description = "This only applies to Servlet 3.0+ containers."),
                    @Descriptor.Property(
                                         name = "captureRequestLocalHostname",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request local hostname",
                                         checkboxLabel = "Capture request local hostname using ServletRequest.getLocalName()",
                                         description = "This only applies to Servlet 3.0+ containers."),
                    @Descriptor.Property(
                                         name = "captureRequestLocalPort",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request local port",
                                         checkboxLabel = "Capture request local port using ServletRequest.getLocalPort()",
                                         description = "This only applies to Servlet 3.0+ containers."),
                    @Descriptor.Property(
                                         name = "captureRequestServerHostname",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request server hostname",
                                         checkboxLabel = "Capture request server hostname using ServletRequest.getServerName()"),
                    @Descriptor.Property(
                                         name = "captureRequestServerPort",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request server port",
                                         checkboxLabel = "Capture request server port using ServletRequest.getServerPort()"),
                    @Descriptor.Property(
                                         name = "captureRequestScheme",
                                         type = PropertyType.BOOLEAN,
                                         label = "Capture request scheme",
                                         checkboxLabel = "Capture request scheme using ServletRequest.getScheme()")
            },
            advice = {
                    @Descriptor.Advice(
                                       className = "javax.servlet.ServletContextListener",
                                       methodName = "contextInitialized",
                                       methodParameterTypes = {
                                               "javax.servlet.ServletContextEvent"
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Startup",
                                       transactionNameTemplate = "Listener init: {{this.class.name}}",
                                       timerName = "listener init"),
                    @Descriptor.Advice(
                                       className = "javax.servlet.Servlet",
                                       methodName = "init",
                                       methodParameterTypes = {
                                               "javax.servlet.ServletConfig"
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Startup",
                                       transactionNameTemplate = "Servlet init: {{this.class.name}}",
                                       timerName = "servlet init"),
                    @Descriptor.Advice(
                                       className = "javax.servlet.Filter",
                                       methodName = "init",
                                       methodParameterTypes = {
                                               "javax.servlet.FilterConfig"
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Startup",
                                       transactionNameTemplate = "Filter init: {{this.class.name}}",
                                       timerName = "filter init"),
                    @Descriptor.Advice(
                                       className = "javax.servlet.ServletContainerInitializer",
                                       methodName = "onStartup",
                                       methodParameterTypes = {
                                               "java.util.Set",
                                               "javax.servlet.ServletContext"
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Startup",
                                       transactionNameTemplate = "Container initializer: {{this.class.name}}",
                                       timerName = "container initializer"),
                    @Descriptor.Advice(
                                       className = "org.wildfly.extension.undertow.deployment.UndertowDeploymentService",
                                       methodName = "startContext",
                                       methodParameterTypes = {
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Startup",
                                       transactionNameTemplate = "Servlet context: {{this.deploymentInfoInjectedValue.value.contextPath}}",
                                       timerName = "application startup"),
                    @Descriptor.Advice(
                                       className = "org.eclipse.jetty.webapp.WebAppContext",
                                       methodName = "doStart",
                                       methodParameterTypes = {
                                       },
                                       captureKind = CaptureKind.TRANSACTION,
                                       transactionType = "Startup",
                                       transactionNameTemplate = "Servlet context: {{this.contextPath}}",
                                       timerName = "application startup")
            },
            classes = {
                    ServletInstrumentation.class,
                    AsyncServletInstrumentation.class,
                    RequestInstrumentation.class,
                    ResponseInstrumentation.class,
                    RequestDispatcherInstrumentation.class,
                    SessionInstrumentation.class,
                    CatalinaAppStartupInstrumentation.class,
                    WebLogicAppStartupInstrumentation.class,
                    WebSphereAppStartupInstrumentation.class
            },
            collocate = true)
public class InstrumentationDescriptor {}
