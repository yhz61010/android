#!/bin/bash

# NDK build
#ndk-build clean
#ndk-build

# Delete existed so files in [h264-hevc-decoder-sdk] module.
rm -f ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/*.so
rm -f ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/*.so

# Copy new so files to [adpcm-ima-qt-codec-h264-hevc-decoder-sdk] module.
cp ../libs/armeabi-v7a/libh264-hevc-decoder.so ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavcodec.so           ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libavutil.so            ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/
cp ../libs/armeabi-v7a/libc++_shared.so        ../../../../h264-hevc-decoder-sdk/src/main/libs/armeabi-v7a/

cp ../libs/arm64-v8a/libh264-hevc-decoder.so   ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavcodec.so             ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libavutil.so              ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/
cp ../libs/arm64-v8a/libc++_shared.so          ../../../../h264-hevc-decoder-sdk/src/main/libs/arm64-v8a/