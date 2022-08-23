#!/bin/bash

# 初始化环境变量
source config.sh

# ==================================

TOOLCHAINS=${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_TAG}
SYSROOT=${TOOLCHAINS}/sysroot

function build_one
{
if [ $ARCH == "arm" ]
then
    CROSS_PREFIX=$TOOLCHAINS/bin/arm-linux-androideabi-
elif [ $ARCH == "aarch64" ]
then
    CROSS_PREFIX=$TOOLCHAINS/bin/aarch64-linux-android-
elif [ $ARCH == "x86_32" ]
then
    CROSS_PREFIX=$TOOLCHAINS/bin/i686-linux-android-
else
    CROSS_PREFIX=$TOOLCHAINS/bin/x86_64-linux-android-
fi

# https://blog.csdn.net/yu_yuan_1314/article/details/81267442
pushd $FFMPEG_FOLDER
./configure \
    --prefix=$PREFIX \
    --extra-cflags="$OPTIMIZE_CFLAGS" \
    --cross-prefix=$CROSS_PREFIX \
    --sysroot=$SYSROOT \
    --enable-cross-compile \
    --target-os=android \
    --arch=$ARCH \
    --cc=${CC} \
    --cxx=${CC}++ \
    --ld=${CC} \
    --ar=${TOOLCHAINS}/bin/llvm-ar \
    --as=${CC} \
    --nm=${TOOLCHAINS}/bin/llvm-nm \
    --ranlib=${TOOLCHAINS}/bin/llvm-ranlib \
    --strip=${TOOLCHAINS}/bin/llvm-strip \
    --disable-everything \
    --disable-programs \
    --disable-x86asm \
    --disable-inline-asm \
    --disable-swresample \
    --disable-swscale \
    --disable-avfilter \
    --disable-avdevice \
    --disable-avformat \
    --disable-static \
    --enable-decoder=adpcm_ima_qt \
    --enable-encoder=adpcm_ima_qt \
    --enable-shared \
    --enable-small \
    --enable-pic

make clean
make -j$(nproc)
make install
popd
}

#armeabi-v7a
ARCH=arm
OPTIMIZE_CFLAGS="-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -march=armv7-a -mthumb -Wformat -Werror=format-security   -Oz -DNDEBUG  -fPIC --target=armv7-none-linux-androideabi$MIN_SDK_VER --gcc-toolchain=$TOOLCHAINS"
PREFIX=`pwd`/prebuilt/armeabi-v7a
export CC=$TOOLCHAINS/bin/armv7a-linux-androideabi$MIN_SDK_VER-clang
export CXX=$TOOLCHAINS/bin/armv7a-linux-androideabi$MIN_SDK_VER-clang++
build_one

#arm64-v8a
ARCH=aarch64
OPTIMIZE_CFLAGS="-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -O2 -DNDEBUG  -fPIC --target=aarch64-none-linux-android$MIN_SDK_VER --gcc-toolchain=$TOOLCHAINS"
PREFIX=`pwd`/prebuilt/arm64-v8a
export CC=$TOOLCHAINS/bin/aarch64-linux-android$MIN_SDK_VER-clang
export CXX=$TOOLCHAINS/bin/aarch64-linux-android$MIN_SDK_VER-clang++
build_one

##x86_32
#ARCH=x86_32
#OPTIMIZE_CFLAGS="-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -O2 -DNDEBUG  -fPIC --target=i686-none-linux-android$MIN_SDK_VER --gcc-toolchain=$TOOLCHAINS"
#PREFIX=`pwd`/prebuilt/x86
#export CC=$TOOLCHAINS/bin/i686-linux-android$MIN_SDK_VER-clang
#export CXX=$TOOLCHAINS/bin/i686-linux-android$MIN_SDK_VER-clang++
#build_one
#
##x86_64
#ARCH=x86_64
#OPTIMIZE_CFLAGS="-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -O2 -DNDEBUG  -fPIC --target=x86_64-none-linux-android$MIN_SDK_VER --gcc-toolchain=$TOOLCHAINS"
#PREFIX=`pwd`/prebuilt/x86_64
#export CC=$TOOLCHAINS/bin/x86_64-linux-android$MIN_SDK_VER-clang
#export CXX=$TOOLCHAINS/bin/x86_64-linux-android$MIN_SDK_VER-clang++
#build_one

sh ndk-build-and-copy-to-module_ffmpeg_adpcm_ima_qt_codec.sh
