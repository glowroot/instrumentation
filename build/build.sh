#!/bin/bash -e

# see https://github.com/travis-ci/travis-ci/issues/8408
_JAVA_OPTIONS=

# java.security.egd is needed for low-entropy docker containers
# /dev/./urandom (as opposed to simply /dev/urandom) is needed prior to Java 8
# (see https://docs.oracle.com/javase/8/docs/technotes/guides/security/enhancements-8.html)
#
# NewRatio is to leave as much memory as possible to old gen
surefire_jvm_args="-Xmx256m -XX:NewRatio=20 -Djava.security.egd=file:/dev/./urandom"
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
if [[ $java_version == 1.6* || $java_version == 1.7* ]]
then
  # MaxPermSize bump is needed for running grails instrumentation tests
  surefire_jvm_args="$surefire_jvm_args -XX:MaxPermSize=128m"
fi


case "$1" in

      "test") if [[ "$USE_LOCAL_TEST_HARNESS" == "true" ]]
              then
                mvn clean install -DargLine="$surefire_jvm_args" \
                                  -Dtest.harness.skipShading \
                                  -Dtest.harness=local \
                                  -B
              else
                mvn clean install -DargLine="$surefire_jvm_args" \
                                  -B
              fi
              echo running jdbc tests with test.jdbcConnectionType=H2 ...
              mvn clean verify -pl :instrumentation-jdbc \
                               -DargLine="$surefire_jvm_args" \
                               -Dtest.harness=${TEST_HARNESS:-javaagent} \
                               -Dtest.jdbcConnectionType=H2 \
                               -B
              echo running jdbc tests with test.jdbcConnectionType=COMMONS_DBCP_WRAPPED ...
              mvn clean verify -pl :instrumentation-jdbc \
                               -DargLine="$surefire_jvm_args" \
                               -Dtest.harness=${TEST_HARNESS:-javaagent} \
                               -Dtest.jdbcConnectionType=COMMONS_DBCP_WRAPPED \
                               -B
              echo running jdbc tests with test.jdbcConnectionType=TOMCAT_JDBC_POOL_WRAPPED ...
              mvn clean verify -pl :instrumentation-jdbc \
                               -DargLine="$surefire_jvm_args" \
                               -Dtest.harness=${TEST_HARNESS:-javaagent} \
                               -Dtest.jdbcConnectionType=TOMCAT_JDBC_POOL_WRAPPED \
                               -B
              echo running jdbc tests with test.jdbcConnectionType=HIKARI_CP_WRAPPED ...
              mvn clean verify -pl :instrumentation-jdbc \
                               -DargLine="$surefire_jvm_args" \
                               -Dtest.harness=${TEST_HARNESS:-javaagent} \
                               -Dtest.jdbcConnectionType=HIKARI_CP_WRAPPED \
                               -B
              if [[ "$TEST_HARNESS" != "local" ]]
              then
                echo running jdbc tests with test.jdbcConnectionType=GLASSFISH_JDBC_POOL_WRAPPED ...
                mvn clean verify -pl :instrumentation-jdbc \
                                 -DargLine="$surefire_jvm_args" \
                                 -Dtest.harness=${TEST_HARNESS:-javaagent} \
                                 -Dtest.jdbcConnectionType=GLASSFISH_JDBC_POOL_WRAPPED \
                                 -B
              fi
              ;;

     "deploy") # only deploy snapshot versions (release versions need pgp signature)
               version=`mvn help:evaluate -Dexpression=project.version | grep -v '\['`
               if [[ "$TRAVIS_REPO_SLUG" == "glowroot/instrumentation" && "$TRAVIS_BRANCH" == "master" && "$TRAVIS_PULL_REQUEST" == "false" && "$version" == *-SNAPSHOT ]]
               then
                 mvn clean deploy -Pjavadoc \
                                  -DargLine="$surefire_jvm_args" \
                                  -Dtest.harness=javaagent \
                                  -Dbuild.commit=$TRAVIS_COMMIT \
                                  --settings build/settings.xml \
                                  -B
               else
                 mvn clean install -Pjavadoc \
                                   -DargLine="$surefire_jvm_args" \
                                   -B
               fi
               ;;

    "checker") set +e
               git diff --exit-code > /dev/null
               if [ $? -ne 0 ]
               then
                 echo you have unstaged changes!
                 exit 1
               fi
               set -e

               echo "MAVEN_OPTS=\"-Xmx1g -XX:NewRatio=20\"" > ~/.mavenrc

               find -name *.java -print0 | xargs -0 sed -i 's|/\*@UnderInitialization\*/|@org.checkerframework.checker.initialization.qual.UnderInitialization|g'
               find -name *.java -print0 | xargs -0 sed -i 's|/\*@Initialized\*/|@org.checkerframework.checker.initialization.qual.Initialized|g'
               find -name *.java -print0 | xargs -0 sed -i 's|/\*@Untainted\*/|@org.checkerframework.checker.tainting.qual.Untainted|g'
               find -name *.java -print0 | xargs -0 sed -i 's|/\*@\([A-Za-z]*\)\*/|@org.checkerframework.checker.nullness.qual.\1|g'
               find instrumentation-api -name *.java -print0 | xargs -0 sed -i 's|^import org.glowroot.instrumentation.api.checker.|import org.checkerframework.checker.nullness.qual.|g'
               find instrumentation -name *.java -print0 | xargs -0 sed -i 's|^import org.glowroot.instrumentation.api.checker.|import org.checkerframework.checker.nullness.qual.|g'

               mvn clean compile -Dbuild.checker \
                                 -Dbuild.checker.stubs=$PWD/build/checker-stubs \
                                 -B \
                                 | sed 's/\[ERROR\] .*[\/]\([^\/.]*\.java\):\[\([0-9]*\),\([0-9]*\)\]/[ERROR] (\1:\2) [column \3]/'
               # preserve exit status from mvn (needed because of pipe to sed)
               mvn_status=${PIPESTATUS[0]}
               git checkout -- .
               exit $mvn_status
               ;;

      "sonar") if [[ $SONAR_LOGIN ]]
               then
                 # need to skip shading when running jacoco, otherwise the bytecode changes done to
                 # the classes during shading will modify the jacoco class id and the sonar reports
                 # won't report usage of those bytecode modified classes
                 #
                 # code coverage for @Pointcut classes are only captured when run with the javaagent
                 # test harness since in that case jacoco javaagent goes first (see JavaagentMain)
                 # and uses the original bytecode to construct the class ids, whereas when run with
                 # the local test harness, the jacoco javaagent uses the bytecode that is woven
                 # by IsolatedWeavingClassLoader to construct the class ids
                 #
                 # jacoco destFile needs absolute path, otherwise it is relative to each submodule
                 mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install \
                                 -P play-2.x,play-2.4.x \
                                 -Djacoco.inclBootstrapClasses=true \
                                 -Djacoco.inclNoLocationClasses=true \
                                 -Djacoco.destFile=$PWD/jacoco-combined.exec \
                                 -Djacoco.propertyName=jacocoArgLine \
                                 -DargLine="$surefire_jvm_args \${jacocoArgLine}" \
                                 -Dtest.harness.skipShading \
                                 -B
                 mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install \
                                 -pl :instrumentation-play \
                                 -P play-1.x \
                                 -Djacoco.inclBootstrapClasses=true \
                                 -Djacoco.inclNoLocationClasses=true \
                                 -Djacoco.destFile=$PWD/jacoco-combined.exec \
                                 -Djacoco.propertyName=jacocoArgLine \
                                 -DargLine="$surefire_jvm_args \${jacocoArgLine}" \
                                 -B
                 # the sonar.login system property is set in the pom.xml using the
                 # environment variable SONAR_LOGIN (instead of setting the system
                 # property on the command line which which would make it visible to ps)
                 mvn org.jacoco:jacoco-maven-plugin:report sonar:sonar \
                                 -pl !:instrumentation-test-matrix \
                                 -Djacoco.dataFile=$PWD/jacoco-combined.exec \
                                 -Dsonar.host.url=https://sonarcloud.io \
                                 -Dsonar.organization=glowroot \
                                 -B
               else
                 echo SONAR_LOGIN token missing
               fi
               ;;

esac
