# Table of Contents
1. Play raw H.264
2. Convert video to raw stream
3. How to compile `libyuv`
4. How to compile `yuv`

**Attention:**
The base `libyuv.so` does not include the `JPEG` related library.

## Play raw H.264
```shell
# Show available pixel formats.
$ ffmpeg -pix_fmts
# Play YUV file.
$ ffplay -pix_fmt nv21 -f rawvideo -video_size 1920x1080 camera.yuv
$ ffplay -pix_fmt yuv420p -f rawvideo -video_size 1920x1080 camera.yuv
```

## Convert video to raw stream
### Convert H.264 video to raw stream
```shell
$ ffmpeg -i tears_400_x264.mp4 -vcodec copy -an -f h264 tears_400_x264_raw.h264
$ ffmpeg -i tears_400_x264.mp4 -vcodec copy -an -bsf:v h264_mp4toannexb tears_400_x264_raw.h264
```

### Convert H.265 video to raw stream
```shell
$ ffmpeg -i tears_400_x265.mp4 -vcodec copy -an -f h265 tears_400_x265_raw.h265
$ ffmpeg -i tears_400_x265.mp4 -vcodec copy -an -bsf:v hevc_mp4toannexb tears_400_x265_raw.h265
```
**Parameter description:**

```
-vcodec codec: force video codec ('copy' to copy stream)
-an             : disable audio
-f fmt       : force format
-bsf[:stream_specifier] bitstream_filters (output,per-stream)
           Set bitstream filters for matching streams. bitstream_filters is a comma-separated list of itstream filters. 
           Use the "-bsfs" option to get the list of bitstream filters.
           ffmpeg -i tears_400_x264.mp4 -c:v copy -bsf:v h264_mp4toannexb -an tears_400_x264_raw.h264

# https://www.ffmpeg.org/ffmpeg-bitstream-filters.html#h264_005fmp4toannexb
h264_mp4toannexb: Convert an H.264 bitstream from length prefixed mode to start code prefixed mode (as defined in the Annex B of the ITU-T H.264 specification). 
```

使用 `ffmpeg -bsfs` 命令可以查看 `ffmpeg` 工具支持的 Bitstream Filter 类型。

## How to compile `libyuv`

### Download
Download `libyuv` sources and move all files into `jni` folder.
or use the downloaded sources `libyuv-20260514.tar.xz`
(This is the original official version just excludes `.git` folder. Downloaded date: 2026/05/14)
```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/
$ rm -rf libyuv/jni libyuv/libs libyuv/obj
$ mkdir -p libyuv/jni
$ cd libyuv/jni
$ git clone https://chromium.googlesource.com/libyuv/libyuv .
# or unzip file
$ tar xvJf ../../yuv/libyuv-20260514.tar.xz --strip-components 1
```

### Create `./libyuv/jni/Application.mk` file
```
cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/
cd libyuv/jni
echo -e "# Keep the NDK platform level explicit to avoid default warning.\nAPP_PLATFORM := android-21\n" > Application.mk
````

### Modify `./libyuv/jni/Android.mk` file
Modify `Android.mk` file to ignore JPEG dependency.
Add the following line before the first ```LOCAL_CPP_EXTENSION := .cc```

```
LIBYUV_DISABLE_JPEG := "yes"
```

Insert the following lines after line 46, just after `common_CFLAGS := -Wall -fexceptions`:

```
# ndk-build file list does not include SVE/SME source units.
# Keep them disabled here to avoid unresolved *_SVE2/*_SME symbols.
common_CFLAGS += -DLIBYUV_DISABLE_SVE -DLIBYUV_DISABLE_SME
```

Insert the following lines before line 58, just before `LOCAL_CFLAGS += $(common_CFLAGS)`:

```
# AArch64 sources include dotprod/i8mm inline asm in neon64 files.
# Enable required ISA extensions for arm64 builds.
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
common_CFLAGS += -march=armv8-a+dotprod+i8mm
endif
```

Replace the lines 78, 79 
```
LOCAL_STATIC_LIBRARIES := libyuv_static
LOCAL_SHARED_LIBRARIES := libjpeg
```
with
```
LOCAL_STATIC_LIBRARIES := libyuv_static
ifneq ($(LIBYUV_DISABLE_JPEG), "yes")
LOCAL_SHARED_LIBRARIES := libjpeg
endif
```

### Compile
You can run the handy shell script to compile `libyuv` and `yuv`:
```shell
cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/yuv
sh compile_all_in_one.sh
```

----------

Or you can do that step by step:
```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libyuv/jni
$ ndk-build
```
You'll get the `so` files in the generated folder 
`/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libyuv/libs`

## How to compile `yuv` step by step
First, copy `include` folder from `libyuv/jni` to `yuv/src/main/cpp`.
```shell
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libyuv/
$ rm -rf ../yuv/src/main/cpp/include
$ mkdir -p ../yuv/src/main/cpp/include
$ cp -R jni/include/ ../yuv/src/main/cpp/include
```
Then Copy `so` files from `libyuv/libs` to `yuv/libs`.
```shell
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libyuv/
$ rm -rf ../yuv/libs/
$ mkdir -p ../yuv/libs/armeabi-v7a
$ mkdir -p ../yuv/libs/arm64-v8a
$ cp -R libs/armeabi-v7a/ ../yuv/libs/armeabi-v7a/
$ cp -R libs/arm64-v8a/ ../yuv/libs/arm64-v8a/
```

Finally, you have three ways to compile `yuv` module:
- Compile with `gradlew` command.
```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/
$ ./gradlew :yuv:assembleRelease
```
- Compile from `Gradle` sidebar.
Run from right sidebar **Gradle -> LeoAndroidBaseUtil -> yuv -> build -> assemble **.
- Compile from `Build` menu.
Select `yuv` module then click from menu **Build -> Make Module 'LeoAndroidBaseUtil.yuv'**.
You can select the compile option from `Build Variants` menu. 
