#!/bin/bash

echo "
=======================================
============== Attention ==============
=======================================
Before running this script,
please make sure you have already download [libyuv] and modified [Android.mk] file.
You can do that following the instructions in [README.md]
"

# Clean `include` and `libs` folder in `yuv-sdk` project.
echo "Clean [include] and [libs] folder in [yuv-sdk] project."
rm -rf src/main/cpp/include
rm -rf libs/

cd ..
# Clean `libyuv` build folders
echo "Clean [libyuv] build folders"
rm -rf libyuv/libs libyuv/obj

cd libyuv/jni
# Compile `libyuv`
echo "Compile [libyuv]"
ndk-build

# Copy `include` folder from `libyuv/jni` to `yuv-sdk/src/main/cpp`.
echo "Copy [include] folder"
cp -R include/ ../../yuv-sdk/src/main/cpp/include

# Copy `so` files from `libyuv/libs` to `yuv-sdk/libs`.
echo "Copy [libs] folder"
mkdir -p ../../yuv-sdk/libs/armeabi-v7a
mkdir -p ../../yuv-sdk/libs/arm64-v8a
cp -R ../libs/armeabi-v7a/ ../../yuv-sdk/libs/armeabi-v7a/
cp -R ../libs/arm64-v8a/   ../../yuv-sdk/libs/arm64-v8a/

echo "All done."
