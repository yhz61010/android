# Table of Contents
1. Play raw H.264
2. Convert video to raw stream
3. How to compile `libyuv`
4. How to compile `yuv-sdk`

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
or use the downloaded sources `libyuv-20220324.tar.gz`
(This is the original official version just excludes `.git` folder. Downloaded date: 2022/03/24)
```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/
$ mkdir -p libyuv/jni
$ cd libyuv/jni
$ git clone https://chromium.googlesource.com/libyuv/libyuv .
# or unzip `libyuv-20220324.tar.gz` file
$ tar xvzf ../../yuv-sdk/libyuv-20220324.tar.gz --strip-components 1
```

### Modify `./libyuv/jni/Android.mk` file
Modify `Android.mk` file to ignore JPEG dependency.
Add the following line before ```LOCAL_CPP_EXTENSION := .cc```

```
LIBYUV_DISABLE_JPEG := "yes"
```

Replace the following lines(from lines 86 to 87)
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
```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libyuv/jni
$ ndk-build
```
You'll get the `so` files in the generated folder `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libyuv/libs`

## How to compile `yuv-sdk`
First, copy `include` folder from `libyuv/jni` to `yuv-sdk/main/cpp`.
Then, you have three ways to compile `yuv-sdk` module:
- Compile with `gradlew` command.
```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/
$ ./gradlew yuv-sdk:assemble
```
- Compile from `Gradle` sidebar.
Run from right sidebar **Gradle -> LeoAndroidBaseUtil -> yuv-sdk -> build -> assemble **.
- Compile from `Build` menu.
Select `yuv-sdk` module then click from menu **Build -> Make Module 'LeoAndroidBaseUtil.yuv-sdk'**.
ONLY **debug** aar will be generated. 
