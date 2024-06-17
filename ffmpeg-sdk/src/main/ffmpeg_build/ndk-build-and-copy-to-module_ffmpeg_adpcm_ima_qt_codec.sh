#!/bin/bash

# Delete existed so files in [adpcm-ima-qt-codec-sdk] module.
rm -rf   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
rm -rf   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
rm -rf   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86/
rm -rf   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86_64/

mkdir -p ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
mkdir -p ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
mkdir -p ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86/
mkdir -p ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86_64/

# NDK build
ndk-build clean
ndk-build

# Copy new so files to [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
cp ../libs/armeabi-v7a/libadpcm-ima-qt-decoder.so ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libadpcm-ima-qt-encoder.so ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavcodec.so              ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavutil.so               ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/
#cp ../libs/armeabi-v7a/libc++_shared.so           ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/armeabi-v7a/

cp ../libs/arm64-v8a/libadpcm-ima-qt-decoder.so   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libadpcm-ima-qt-encoder.so   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavcodec.so                ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavutil.so                 ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/
#cp ../libs/arm64-v8a/libc++_shared.so             ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/arm64-v8a/

cp ../libs/x86/libadpcm-ima-qt-decoder.so         ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86/
cp ../libs/x86/libadpcm-ima-qt-encoder.so         ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86/
cp ../libs/x86/libavcodec.so                      ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86/
cp ../libs/x86/libavutil.so                       ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86/
#cp ../libs/x86/libc++_shared.so                   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86/

cp ../libs/x86_64/libadpcm-ima-qt-decoder.so      ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86_64/
cp ../libs/x86_64/libadpcm-ima-qt-encoder.so      ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86_64/
cp ../libs/x86_64/libavcodec.so                   ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86_64/
cp ../libs/x86_64/libavutil.so                    ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86_64/
#cp ../libs/x86_64/libc++_shared.so                ../../../../adpcm-ima-qt-codec-sdk/src/main/libs/x86_64/
