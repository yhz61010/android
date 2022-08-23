#!/bin/bash

# Delete existed so files in [adpcm-ima-qt-codec-sdk] module.
rm -f ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/*.so
rm -f ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/*.so

# NDK build
ndk-build clean
ndk-build

# Copy new so files to [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
cp ../libs/armeabi-v7a/libadpcm-ima-qt-decoder.so ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libadpcm-ima-qt-encoder.so ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavcodec.so              ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavutil.so               ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libc++_shared.so           ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/

cp ../libs/arm64-v8a/libadpcm-ima-qt-decoder.so   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libadpcm-ima-qt-encoder.so   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavcodec.so                ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavutil.so                 ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libc++_shared.so             ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
