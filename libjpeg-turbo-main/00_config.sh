#!/bin/bash

# CPU架构
if [ "$#" -lt 1 ]; then
	THE_ARCH=armv7
else
	THE_ARCH=$(tr [A-Z] [a-z] <<< "$1")
fi

# NDK路径和版本配置
ANDROID_NDK_VERSION=22
ANDROID_NDK_ROOT=~/Library/Android/sdk/ndk/22.1.7171670
if [ ${ANDROID_NDK_VERSION} -ge 17 ]
then
	TOOLCHAIN="clang"
else
	TOOLCHAIN="gcc"
fi

# 可支持的最低Android API版本号
AOSP_API=21

AOSP_ABI=""
AOSP_ARM_MODE=""
AOSP_TARGET=""

#根据不同架构配置环境变量
case "$THE_ARCH" in
  arm|armv5|armv6|armv7|armeabi)
	AOSP_ABI="armeabi"
	AOSP_ARM_MODE="arm"
	AOSP_TARGET="arm-linux-androideabi"${AOSP_API}
	;;
  armv7a|armeabi-v7a)
	AOSP_ABI="armeabi-v7a"
	AOSP_ARM_MODE="arm"
	AOSP_TARGET="arm-linux-androideabi"${AOSP_API}
	;;
  armv8|armv8a|aarch64|arm64|arm64-v8a)
	AOSP_ABI="arm64-v8a"
	AOSP_ARM_MODE="arm"
	AOSP_TARGET="aarch64-linux-android"${AOSP_API}
	;;
  x86)
	AOSP_ABI="x86"
	AOSP_ARM_MODE=""
	AOSP_TARGET=""
	;;
  x86_64|x64)
	AOSP_ABI="x86_64"
	AOSP_ARM_MODE=""
	AOSP_TARGET=""
	;;
  *)
	echo "ERROR: Unknown architecture $1"
	[ "$0" = "$BASH_SOURCE" ] && exit 1 || return 1
	;;
esac

echo "-------------------- Information --------------------"
echo "ANDROID_NDK_VERSION"=$ANDROID_NDK_VERSION
echo "ANDROID_NDK_ROOT"=$ANDROID_NDK_ROOT
echo "TOOLCHAIN"=$TOOLCHAIN
echo "AOSP_API"=$AOSP_API
echo "AOSP_ABI="$AOSP_ABI
echo "AOSP_ARM_MODE="$AOSP_ARM_MODE
echo "AOSP_TARGET="$AOSP_TARGET
