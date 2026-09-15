#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
rm -rf "$ROOT/build"
mkdir -p "$ROOT/build/classes" "$ROOT/build/test-classes"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -d "$ROOT/build/classes"
jar --create --file "$ROOT/build/streamflix-desktop.jar" --main-class dev.streamflix.desktop.App -C "$ROOT/build/classes" .
find "$ROOT/src/test/java" -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -cp "$ROOT/build/classes" -d "$ROOT/build/test-classes"
java -cp "$ROOT/build/classes:$ROOT/build/test-classes" dev.streamflix.desktop.JsonTest
java -cp "$ROOT/build/classes:$ROOT/build/test-classes" dev.streamflix.desktop.ProviderFixtureTest
java -cp "$ROOT/build/classes:$ROOT/build/test-classes" dev.streamflix.desktop.ExtractorFixtureTest
echo "Built: $ROOT/build/streamflix-desktop.jar"
