#!/bin/bash

# shellcheck disable=SC2034
FFMPEG_FOLDER=ffmpeg-6.0
NDK_PATH=/home/yhz61010/Android/Sdk/ndk/25.2.9519653
HOST_ARCH=$(uname -m)
# linux-x86_64
# darwin-x86_64
HOST_TAG=linux-${HOST_ARCH}
MIN_SDK_VER=23
PKG_CONFIG_PATH=$(which pkg-config | sed 's/pkg-config$//' )

# ==================================

TOOLCHAINS=${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_TAG}
SYSROOT=${TOOLCHAINS}/sysroot

# ==================================

echo "-> Current working directory=$(pwd)"
rm -rf prebuilt
rm -rf ../libs
rm -rf ../obj
