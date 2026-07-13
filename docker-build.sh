#!/bin/bash
set -e
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_CACHE_DIR="${GRADLE_DOCKER_CACHE:-$HOME/.gradle-docker}"

# Preserve any existing local.properties (holds real signing credentials on dev
# machines); CI checkouts won't have one, so just leave it absent afterward.
HAD_LOCAL_PROPERTIES=0
if [ -f "$PROJECT_DIR/local.properties" ]; then
  HAD_LOCAL_PROPERTIES=1
  cp "$PROJECT_DIR/local.properties" /tmp/local.properties.bak
fi
printf 'sdk.dir=/opt/android-sdk\n' > "$PROJECT_DIR/local.properties"

docker run --rm \
  -v "$PROJECT_DIR:/workspace:z" \
  -v "$GRADLE_CACHE_DIR:/root/.gradle:z" \
  -e JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  -w /workspace \
  registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie \
  bash -c "apt-get install -y -q openjdk-21-jdk-headless 2>/dev/null && ./gradlew --no-daemon -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64 --no-build-cache clean :app:assembleFossRelease"

# Restore (or remove) local.properties
if [ "$HAD_LOCAL_PROPERTIES" -eq 1 ]; then
  cp /tmp/local.properties.bak "$PROJECT_DIR/local.properties"
else
  rm -f "$PROJECT_DIR/local.properties"
fi
ls -la "$PROJECT_DIR/app/build/outputs/apk/foss/release/"
