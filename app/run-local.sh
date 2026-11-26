#!/usr/bin/env bash
# Run Smart Grievance on this computer with the embedded H2 database (local profile, D130).
# No MySQL needed. Data, uploads and logs are kept in ./local-data next to this script.
#
#   ./run-local.sh            start the built JAR (builds it first if it is missing)
#   ./run-local.sh --build    clean build with all tests (./mvnw clean verify), then start
#   ./run-local.sh --reset    delete ./local-data (fresh demo data on the next start), then start
#
# Open http://localhost:8080 — demo logins are in README.md (admin@example.com / Admin@1234, mahesh@example.com / Demo@1234 …).
set -euo pipefail
cd "$(dirname "$0")"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$JAVA" -version 2>&1 | grep -q 'version "25' || { echo "JDK 25 is required (java -version)"; exit 1; }
JAR="target/smart-grievance.jar"
for arg in "$@"; do
  case "$arg" in
    --build) ./mvnw -B clean verify ;;
    --reset) rm -rf local-data && echo "local-data removed: demo data is loaded again on start" ;;
    *) echo "Unknown option $arg (use --build or --reset)"; exit 2 ;;
  esac
done
[ -f "$JAR" ] || ./mvnw -B clean package -DskipTests
export LOCAL_DATA_DIR="${LOCAL_DATA_DIR:-./local-data}"
echo "Starting on http://localhost:${SERVER_PORT:-8080} (data in $LOCAL_DATA_DIR, Ctrl+C to stop)"
exec "$JAVA" -Xms128m -Xmx512m -XX:+ExitOnOutOfMemoryError -jar "$JAR" --spring.profiles.active=local
