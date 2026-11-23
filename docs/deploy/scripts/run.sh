#!/usr/bin/env bash
# Start Smart Grievance from the built JAR (doc 14 §7.3). Linux / macOS.
#   ./run.sh            dev profile (local MySQL grievance_dev, demo data)
#   ./run.sh prod       prod profile; export DB_URL, DB_USERNAME, DB_PASSWORD, ADMIN_PASSWORD first (§5.6)
# Run it from the app/ folder (logs/ and uploads/ are created there).
set -euo pipefail
PROFILE="${1:-dev}"
JAR="target/smart-grievance.jar"
[ -f "$JAR" ] || { echo "No $JAR - run ./mvnw clean verify first"; exit 1; }
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$JAVA" -version 2>&1 | grep -q 'version "25' || { echo "JDK 25 is required (java -version)"; exit 1; }
# 4 GB laptop: 512 MB heap is enough; -Duser.timezone is NOT needed (the app uses its own Clock, D18)
exec "$JAVA" -Xms128m -Xmx512m -XX:+ExitOnOutOfMemoryError \
  -jar "$JAR" --spring.profiles.active="$PROFILE"
