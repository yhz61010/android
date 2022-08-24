#!/bin/bash

# Clean `include` folder in `yuv-sdk` project.
rm -f src/main/cpp/include

cd ..
# Clean `libyuv` build folders
rm -rf libyuv/libs libyuv/obj

cd libyuv/jni
# Compile `libyuv`
ndk-build

# Copy `include` folder from `libyuv/jni` to `yuv-sdk/src/main/cpp`.
cp -R include/ ../../yuv-sdk/src/main/cpp/include

# Copy `so` files from `libyuv/libs` to `yuv-sdk/libs`.
cp ../libs/armeabi-v7a ../../yuv-sdk/libs/armeabi-v7a
cp ../libs/arm64-v8a   ../../yuv-sdk/libs/arm64-v8a
