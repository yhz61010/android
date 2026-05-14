#!/bin/bash

ANDROID_NDK_ROOT=~/Android/Sdk/ndk/29.0.14206865

echo "
=======================================
============== Attention ==============
=======================================
Before running this script,
please make sure you have already download [libyuv] and modified [Android.mk] file.
You can do that following the instructions in [README.md]
"

# Clean `include` and `libs` folder in `yuv` project.
echo "Clean [include] and [libs] folder in [yuv] project."
rm -rf src/main/cpp/include
rm -rf libs/

cd ..
# Clean `libyuv` build folders
echo "Clean [libyuv] build folders"
rm -rf libyuv/libs libyuv/obj

cd libyuv/jni
# Compile `libyuv`
echo "Compile [libyuv]"

"$ANDROID_NDK_ROOT"/ndk-build

# Copy `include` folder from `libyuv/jni` to `yuv/src/main/cpp`.
echo "Copy [include] folder to [yuv]"
cp -R include/ ../../yuv/src/main/cpp/include

# Copy `so` files from `libyuv/libs` to `yuv/libs`.
echo "Copy [libs] folder to [yuv]"
mkdir -p ../../yuv/libs/armeabi-v7a
mkdir -p ../../yuv/libs/arm64-v8a
mkdir -p ../../yuv/libs/x86
mkdir -p ../../yuv/libs/x86_64

cp -R ../libs/armeabi-v7a/ ../../yuv/libs/
cp -R ../libs/arm64-v8a/   ../../yuv/libs/
cp -R ../libs/x86/         ../../yuv/libs/
cp -R ../libs/x86_64/      ../../yuv/libs/

echo "All files copied. Prepare to assemble..."
cd ../../
pwd
./gradlew :yuv:assembleRelease

echo "All done."
