#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  curl -fL https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar -o "$JAR.tmp"
  mv "$JAR.tmp" "$JAR"
fi
JAVA_EXE=java
[ -z "${JAVA_HOME:-}" ] || JAVA_EXE="$JAVA_HOME/bin/java"
exec "$JAVA_EXE" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
