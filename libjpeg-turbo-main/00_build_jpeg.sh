#!/bin/sh

# 初始化环境变量
source 00_config.sh

# 获取当前路径
NOW_DIR=$(cd $(dirname $0) && pwd)

# 源代码路径
MY_SOURCE_DIR=$NOW_DIR/source

# 编译产出路径
LIBS_DIR=$NOW_DIR/libs
PREFIX=${LIBS_DIR}/${AOSP_ABI}/

# 编译中间产物路径
BUILD_ROOT=build
BUILD_DIR=${BUILD_ROOT}/${AOSP_ABI}

mkdir -p ${BUILD_DIR} && rm -rf "${BUILD_DIR}"/*
mkdir -p ${PREFIX} && rm -rf "${PREFIX}"/*

cd "${BUILD_DIR}"

cmake -G"Unix Makefiles" \
  -DANDROID_ABI=${AOSP_ABI} \
  -DANDROID_ARM_MODE=${AOSP_ARM_MODE} \
  -DANDROID_PLATFORM=android-${AOSP_API} \
  -DANDROID_TOOLCHAIN=${TOOLCHAIN} \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_ASM_FLAGS="--target=${AOSP_TARGET}}" \
  -DCMAKE_TOOLCHAIN_FILE=${ANDROID_NDK_ROOT}/build/cmake/android.toolchain.cmake \
  -DCMAKE_INSTALL_PREFIX=${PREFIX} \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" \
  -DCMAKE_MODULE_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" \
  ${MY_SOURCE_DIR}

make clean
make
make install