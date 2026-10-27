#!/usr/bin/env bash
# Compiles Java 8 bytecode and writes dist/masking-<version>.jar.
# Set JAVA_HOME to choose a consistent JDK toolchain.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${VERSION:-1.0.0}"
JAR="dist/masking-${VERSION}.jar"

BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/masking-build.XXXXXXXX")"
mkdir -p "$BUILD_DIR/classes" dist
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR_TOOL="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
if [[ "$("$JAVAC" -version 2>&1)" == javac\ 1.8.* ]]; then
  TARGET_OPTS=(-source 8 -target 8)
else
  TARGET_OPTS=(--release 8)
fi

echo "== compile"
MAIN_SOURCES=()
while IFS= read -r source; do MAIN_SOURCES+=("$source"); done < <(find src/main/java -name '*.java')
"$JAVAC" "${TARGET_OPTS[@]}" -encoding UTF-8 -Xlint:all -d "$BUILD_DIR/classes" "${MAIN_SOURCES[@]}"

echo "== jar"
printf 'Automatic-Module-Name: com.infyss.projects\nImplementation-Version: %s\n' "$VERSION" > "$BUILD_DIR/manifest.txt"
"$JAR_TOOL" cfm "$JAR" "$BUILD_DIR/manifest.txt" -C "$BUILD_DIR/classes" .

echo "built $JAR"
