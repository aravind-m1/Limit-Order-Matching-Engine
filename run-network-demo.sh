#!/usr/bin/env bash
# Smoke-tests the NIO TCP gateway (port 9631) and metrics HTTP endpoint (port 9632).
set -e
cd "$(dirname "$0")"
./build.sh
java -cp out com.matchforge.NetworkDemo
