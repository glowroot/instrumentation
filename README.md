## Requirements

* Runtime supports Java 6+

## Building

The usual:

    mvn clean install

Building requires Java 8+ and Maven 3.3.1+.

## Contributing

This project uses [Immutables](https://immutables.github.io) annotation processing to eliminate maintenance on lots of boilerplate code. If you are using Eclipse, this requires installing the [m2e-apt](https://github.com/jbosstools/m2e-apt) plugin and changing Window > Preferences > Maven > Annotation Processing to "Automatically configure JDT APT".

## Integration tests

Integration tests are run during Maven's standard `integration-test` lifecycle phase.

The [test harness](instrumentation-test-harness) makes it easy to run sample application code and then validate the trace captured by the instrumentation.  The test harness is able to run tests both using a custom weaving class loader (which is very convenient for running and debugging inside your favorite IDE), and by spawning a JVM with the -javaagent flag (which more correctly simulates real world conditions).

## Code quality

[SonarQube](http://www.sonarqube.org) is used to check Java coding conventions, code coverage, duplicate code, package cycles and much more. See analysis at [https://sonarcloud.io](https://sonarcloud.io/dashboard?id=org.glowroot.instrumentation%3Ainstrumentation-parent).

[Checker Framework](https://checkerframework.org/) is used to eliminate fear of *null* with its rigorous [Nullness Checker](https://checkerframework.org/manual/#nullness-checker). It is run as part of every Travis CI build (see the job with TARGET=checker) and any violation fails the build.

## License

Source code is licensed under the Apache License, Version 2.0.
