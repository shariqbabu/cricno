#!/bin/sh

# ----------------------------------------------------------------------------
# Gradle start-up script for POSIX (compact wrapper).
#
# NOTE: This project intentionally ships only the wrapper *properties*
# (gradle/wrapper/gradle-wrapper.properties). The wrapper jar is not part of
# the repository to keep the patchset light. To materialize it, run once:
#
#     gradle wrapper --gradle-version 8.7
#
# after that, ./gradlew works as usual. Android Studio picks up the
# distribution straight from the properties file, so building from the IDE
# needs nothing extra.
# ----------------------------------------------------------------------------

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -z "$JAVA_HOME" ] && [ -d "/usr/lib/jvm/java-17" ]; then
    JAVA_HOME=/usr/lib/jvm/java-17
fi

JAVACMD=${JAVA_HOME:+"$JAVA_HOME/bin/"}java

if [ ! -f "$CLASSPATH" ]; then
    echo "Gradle wrapper jar not found: $CLASSPATH" >&2
    echo "Run 'gradle wrapper --gradle-version 8.7' once, or open the project in Android Studio." >&2
    exit 1
fi

exec "$JAVACMD" \
    -Xmx2048m \
    -Xms256m \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
