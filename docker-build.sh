#!/bin/bash
set -e
# Backup and replace local.properties on the host before Docker runs
cp /home/ghost/Projects/c2k/local.properties /tmp/local.properties.bak
printf 'sdk.dir=/opt/android-sdk\n' > /home/ghost/Projects/c2k/local.properties

docker run --rm \
  -v /home/ghost/Projects/c2k:/workspace:z \
  -v /home/ghost/.gradle-docker:/root/.gradle:z \
  -e JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  -w /workspace \
  registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie \
  bash -c "apt-get install -y -q openjdk-21-jdk-headless 2>/dev/null && ./gradlew --no-daemon -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64 --no-build-cache clean :app:assembleFossRelease"

# Always restore local.properties
cp /tmp/local.properties.bak /home/ghost/Projects/c2k/local.properties
ls -la /home/ghost/Projects/c2k/app/build/outputs/apk/foss/release/
