#!/bin/bash
set -e

./gradlew --no-daemon :app:bundlePlayRelease

echo ""
echo "AAB output:"
ls -la /home/ghost/Projects/c2k/app/build/outputs/bundle/playRelease/
