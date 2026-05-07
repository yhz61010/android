#!/bin/bash

# This script builds ffmpeg-sdk module via Gradle/CMake and copies
# the generated .so files to the [h264-hevc-decoder] wrapper module.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../../../.."
TARGET_MODULE="$PROJECT_ROOT/h264-hevc-decoder"
BUILD_OUTPUT="$PROJECT_ROOT/ffmpeg-sdk/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib"

# Build ffmpeg-sdk via Gradle
cd "$PROJECT_ROOT" || exit 1
./gradlew :ffmpeg-sdk:assembleRelease || exit 1

# Delete existing .so files in target module
rm -rf "$TARGET_MODULE/src/main/libs/armeabi-v7a/"
rm -rf "$TARGET_MODULE/src/main/libs/arm64-v8a/"
rm -rf "$TARGET_MODULE/src/main/libs/x86/"
rm -rf "$TARGET_MODULE/src/main/libs/x86_64/"

# Copy new .so files to [h264-hevc-decoder] module
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    mkdir -p "$TARGET_MODULE/src/main/libs/$abi/"
    cp "$BUILD_OUTPUT/$abi/libh264-hevc-decoder.so" "$TARGET_MODULE/src/main/libs/$abi/"
    cp "$BUILD_OUTPUT/$abi/libavcodec.so"           "$TARGET_MODULE/src/main/libs/$abi/"
    cp "$BUILD_OUTPUT/$abi/libavutil.so"            "$TARGET_MODULE/src/main/libs/$abi/"
    cp "$BUILD_OUTPUT/$abi/libswscale.so"           "$TARGET_MODULE/src/main/libs/$abi/"
    cp "$BUILD_OUTPUT/$abi/libc++_shared.so"        "$TARGET_MODULE/src/main/libs/$abi/"
done

echo "Done. .so files copied to h264-hevc-decoder module."
