#!/bin/bash

# Delete existed so files in [h264-hevc-decoder-sdk] module.
rm -rf   ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
rm -rf   ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
rm -rf   ../../../../h264-hevc-decoder-sdk/src/main/libs/x86/
rm -rf   ../../../../h264-hevc-decoder-sdk/src/main/libs/x86_64/

mkdir -p ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
mkdir -p ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
mkdir -p ../../../../h264-hevc-decoder-sdk/src/main/libs/x86/
mkdir -p ../../../../h264-hevc-decoder-sdk/src/main/libs/x86_64/

# NDK build
ndk-build clean
ndk-build

# Copy new so files to [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
cp ../libs/armeabi-v7a/libh264-hevc-decoder.so ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavcodec.so           ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavutil.so            ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libswscale.so           ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libc++_shared.so        ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/

cp ../libs/arm64-v8a/libh264-hevc-decoder.so   ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavcodec.so             ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavutil.so              ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libswscale.so             ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libc++_shared.so          ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/

cp ../libs/x86/libh264-hevc-decoder.so   ../../../../h264-hevc-decoder-sdk/src/main/libs/x86/
cp ../libs/x86/libavcodec.so             ../../../../h264-hevc-decoder-sdk/src/main/libs/x86/
cp ../libs/x86/libavutil.so              ../../../../h264-hevc-decoder-sdk/src/main/libs/x86/
cp ../libs/x86/libswscale.so             ../../../../h264-hevc-decoder-sdk/src/main/libs/x86/
cp ../libs/x86/libc++_shared.so          ../../../../h264-hevc-decoder-sdk/src/main/libs/x86/

cp ../libs/x86_64/libh264-hevc-decoder.so   ../../../../h264-hevc-decoder-sdk/src/main/libs/x86_64/
cp ../libs/x86_64/libavcodec.so             ../../../../h264-hevc-decoder-sdk/src/main/libs/x86_64/
cp ../libs/x86_64/libavutil.so              ../../../../h264-hevc-decoder-sdk/src/main/libs/x86_64/
cp ../libs/x86_64/libswscale.so             ../../../../h264-hevc-decoder-sdk/src/main/libs/x86_64/
cp ../libs/x86_64/libc++_shared.so          ../../../../h264-hevc-decoder-sdk/src/main/libs/x86_64/
