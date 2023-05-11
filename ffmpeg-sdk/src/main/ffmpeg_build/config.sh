#!/bin/bash

FFMPEG_FOLDER=ffmpeg-6.0
NDK_PATH=~/Library/Android/sdk/ndk/25.1.8937393
# linux-x86_64
HOST_TAG=darwin-x86_64
MIN_SDK_VER=23

# ==================================

TOOLCHAINS=${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_TAG}
SYSROOT=${TOOLCHAINS}/sysroot

# ==================================

rm -rf prebuilt
rm -rf ../../libs
rm -rf ../../obj

