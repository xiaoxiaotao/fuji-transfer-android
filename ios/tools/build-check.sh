#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
platform="${1:-iOS}"
output="ios/build"
if [[ "$platform" == "iOS Simulator" ]]; then output="ios/build-simulator"; fi
mkdir -p "$output"
if xcodebuild -project ios/FujiGallery.xcodeproj -scheme FujiGallery -configuration Debug -destination "generic/platform=$platform" -derivedDataPath "$output" CODE_SIGNING_ALLOWED=NO build > "$output/xcodebuild.log" 2>&1; then
  tail -8 "$output/xcodebuild.log"
else
  while IFS= read -r line; do
    line="${line//%/%25}"
    printf '::error::%s\n' "$line"
  done < <(awk '/: error:|fatal error:|BUILD FAILED/ {print}' "$output/xcodebuild.log" | head -25)
  tail -30 "$output/xcodebuild.log"
  exit 1
fi
