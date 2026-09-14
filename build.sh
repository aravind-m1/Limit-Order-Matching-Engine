#!/usr/bin/env bash
# Compiles Limit-Order-Matching-Engine with plain javac (no build tool required).
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
find src/main/java -name "*.java" > /tmp/mf-main-sources.txt
find src/test/java -name "*.java" > /tmp/mf-test-sources.txt
echo "Compiling main sources..."
javac -d out -encoding UTF-8 @/tmp/mf-main-sources.txt
echo "Compiling test sources..."
javac -d out -cp out -encoding UTF-8 @/tmp/mf-test-sources.txt
echo "Build complete. Classes in ./out"
