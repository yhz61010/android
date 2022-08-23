#!/bin/bash

# Delete existed so files in [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
rm -f ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/*.so
rm -f ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/*.so

# NDK build
ndk-build clean
ndk-build

# Copy new so files to [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
cp ../libs/armeabi-v7a/*.so ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/arm64-v8a/*.so   ../../../../adpcm-ima-qt-codec-h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
