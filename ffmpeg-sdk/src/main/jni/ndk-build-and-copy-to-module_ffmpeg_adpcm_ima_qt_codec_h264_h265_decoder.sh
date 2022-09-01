#!/bin/bash

# Delete existed so files in [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
rm -rf   ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
rm -rf   ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
rm -rf   ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/x86/
rm -rf   ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/x86_64/

mkdir -p ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
mkdir -p ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
mkdir -p ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/x86/
mkdir -p ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/x86_64/

# NDK build
ndk-build clean
ndk-build

# Copy new so files to [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
cp ../libs/armeabi-v7a/*.so ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/arm64-v8a/*.so   ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/x86/*.so         ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/x86/
cp ../libs/x86_64/*.so      ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/x86_64/
