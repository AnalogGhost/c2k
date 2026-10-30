#!/bin/bash
set -e
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_CACHE_DIR="${GRADLE_DOCKER_CACHE:-$HOME/.gradle-docker}"

# Preserve any existing local.properties (holds real signing credentials on dev
# machines); CI checkouts won't have one, so just leave it absent afterward.
# The backup lives in a fresh temp directory and is restored by a trap, so a failed build
# (set -e) can't leave the stub in place, and a second run can't back up the stub over it.
BACKUP_DIR="$(mktemp -d)"
restore_local_properties() {
  if [ -f "$BACKUP_DIR/local.properties" ]; then
    cp "$BACKUP_DIR/local.properties" "$PROJECT_DIR/local.properties"
  else
    rm -f "$PROJECT_DIR/local.properties"
  fi
  rm -rf "$BACKUP_DIR"
}
if [ -f "$PROJECT_DIR/local.properties" ]; then
  cp "$PROJECT_DIR/local.properties" "$BACKUP_DIR/local.properties"
fi
trap restore_local_properties EXIT
printf 'sdk.dir=/opt/android-sdk\n' > "$PROJECT_DIR/local.properties"

docker run --rm \
  -v "$PROJECT_DIR:/workspace:z" \
  -v "$GRADLE_CACHE_DIR:/root/.gradle:z" \
  -e JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  -e HOST_UID="$(id -u)" \
  -e HOST_GID="$(id -g)" \
  -w /workspace \
  registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie \
  bash -c "apt-get install -y -q openjdk-21-jdk-headless 2>/dev/null && ./gradlew --no-daemon -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64 --no-build-cache clean :app:assembleFossRelease; status=\$?; chown -R \$HOST_UID:\$HOST_GID /workspace /root/.gradle; exit \$status"

ls -la "$PROJECT_DIR/app/build/outputs/apk/foss/release/"
