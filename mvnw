#!/bin/sh
set -euf

BASE_DIR=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_PROPERTIES="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -r "$WRAPPER_PROPERTIES" ]; then
  echo "Missing $WRAPPER_PROPERTIES" >&2
  exit 1
fi

wrapperUrl=$(grep -E '^wrapperUrl=' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)

if [ ! -r "$WRAPPER_JAR" ]; then
  mkdir -p "$WRAPPER_DIR"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$wrapperUrl" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$wrapperUrl" -O "$WRAPPER_JAR"
  else
    echo "Neither curl nor wget is available to download Maven Wrapper." >&2
    exit 1
  fi
fi

exec java -Dmaven.multiModuleProjectDirectory="$BASE_DIR" -cp "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
