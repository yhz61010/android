#!/bin/bash

NDK_PATH=~/Library/Android/sdk/ndk/22.1.7171670
# linux-x86_64
HOST_TAG=darwin-x86_64
MIN_SDK_VER=21

# ==================================

TOOLCHAINS=${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_TAG}
SYSROOT=${TOOLCHAINS}/sysroot

function build_one
{
if [ $ARCH == "arm" ]
then
    CROSS_PREFIX=$TOOLCHAINS/bin/arm-linux-androideabi-
    HOST=arm-linux
elif [ $ARCH == "aarch64" ]
then
    CROSS_PREFIX=$TOOLCHAINS/bin/aarch64-linux-android-
    HOST=aarch64-linux
elif [ $ARCH == "i686" ]
then
    CROSS_PREFIX=$TOOLCHAINS/bin/i686-linux-android-
    HOST=i686-linux
else
    CROSS_PREFIX=$TOOLCHAINS/bin/x86_64-linux-android-
    HOST=x86_64-linux
fi

pushd libx264
./configure \
    --prefix=$PREFIX \
    --extra-cflags="$OPTIMIZE_CFLAGS" \
    --cross-prefix=$CROSS_PREFIX \
    --sysroot=$SYSROOT \
    --host=$HOST \
    --extra-ldflags="-nostdlib" \
    --enable-pic \
    --enable-shared \
    --enable-strip \
    --disable-cli \
    --disable-win32thread \
    --disable-avs \
    --disable-swscale \
    --disable-lavf \
    --disable-ffms \
    --disable-gpac \
    --disable-lsmash

make clean
#make -j4 install V=1
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

##x86
#ARCH=i686
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