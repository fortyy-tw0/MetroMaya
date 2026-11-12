#!/usr/bin/env bash
# Compile and run the appointment system locally. Needs JDK 17 or newer.
# Usage: ./run.sh [port]      then open http://localhost:8080
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out && mkdir out
javac -d out $(find src/main/java -name '*.java')
cp -r src/main/resources/. out/

export PORT="${1:-${PORT:-8080}}"
exec java -cp out appointment.Main
