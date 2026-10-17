#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
VARIANT=${1:?Usage: scripts/build.sh before|after [Debug|Release]}
BUILD_TYPE=${2:-Debug}
python3 "$ROOT/scripts/set-variant.py" "$VARIANT"
cd "$ROOT/android"
./gradlew :react-native-screens:clean ":app:assemble${BUILD_TYPE}" -PreactNativeArchitectures=arm64-v8a --console=plain
