#!/bin/bash
set -euo pipefail

# This script builds ffmpeg-sdk module via Gradle/CMake and copies
# the generated .so files to the [adpcm-ima-qt-codec-h264-hevc-decoder] wrapper module.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../../../.."
TARGET_MODULE="$PROJECT_ROOT/adpcm-ima-qt-codec-h264-hevc-decoder"
BUILD_OUTPUT="$PROJECT_ROOT/ffmpeg-sdk/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib"

# Build ffmpeg-sdk via Gradle
cd "$PROJECT_ROOT" || exit 1
./gradlew :ffmpeg-sdk:assembleRelease --rerun-tasks

for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    for library in libadpcm-ima-qt-decoder.so libadpcm-ima-qt-encoder.so libh264-hevc-decoder.so libavcodec.so libavutil.so libswscale.so libc++_shared.so; do
        test -f "$BUILD_OUTPUT/$abi/$library" || {
            echo "Missing build output: $BUILD_OUTPUT/$abi/$library" >&2
            exit 1
        }
    done
done

# Delete existing .so files in target module
rm -rf "$TARGET_MODULE/src/main/libs/armeabi-v7a/"
rm -rf "$TARGET_MODULE/src/main/libs/arm64-v8a/"
rm -rf "$TARGET_MODULE/src/main/libs/x86/"
rm -rf "$TARGET_MODULE/src/main/libs/x86_64/"

# Copy new .so files to [adpcm-ima-qt-codec-h264-hevc-decoder] module
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    mkdir -p "$TARGET_MODULE/src/main/libs/$abi/"
    rsync -avh "$BUILD_OUTPUT/$abi/"*.so "$TARGET_MODULE/src/main/libs/$abi/"
done

echo "Done. .so files copied to adpcm-ima-qt-codec-h264-hevc-decoder module."
