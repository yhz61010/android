#!/bin/bash

FFMPEG_FOLDER=ffmpeg-5.1
NDK_PATH=~/Library/Android/sdk/ndk/21.1.6352462
# linux-x86_64
HOST_TAG=darwin-x86_64
MIN_SDK_VER=21

# ==================================

TOOLCHAINS=${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_TAG}
SYSROOT=${TOOLCHAINS}/sysroot

# ==================================

rm -rf prebuilt
rm -rf ../libs
rm -rf ../obj

ndk-build clean
