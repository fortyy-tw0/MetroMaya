#!/usr/bin/env bash
# Compiles everything and runs the plain-Java booking-rule checks.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out && mkdir out
javac -d out $(find src/main/java src/test/java -name '*.java')
cp -r src/main/resources/. out/
java -cp out appointment.SmokeTest
