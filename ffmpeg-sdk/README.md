**DO NOT** add this module to project if you want to push sources to `github`.
These means do not include `ffmpeg-sdk` module in `settings.gradle.kts` if you push sources to `github`.


**Suggestion**

If you want to develop, you'd better add this module into `settings.gradle.kts` or else the source code won't be highlighted.


**This module can not be imported by other projects.**
If your want to import this module by other projects, you can make a wrapper module just like [adpcm-ima-qt-codec-sdk] and copy any necessary sources form this module to that wrapper project.

### Compile Environment：

- Android Studio: Flamingo | 2022.2.1
- OS：macOS 13.2
- NDK：25.2.9519653
- Min SDK: 23 (Android 6.0)
- FFmpeg 6.0 "Von Neumann"(6.0 was released on 2023-02-27)
- cmake: 3.23.0
- gcc:
  Apple clang version 14.0.0 (clang-1400.0.29.202)
  Target: x86_64-apple-darwin22.3.0
  Thread model: posix
  InstalledDir: /Library/Developer/CommandLineTools/usr/bin
- JDK: java 17.0.6 2023-01-17 LTS

### How to compile ffmpeg and generate so file for Android

You can download from official website and scroll to the **Releases** section:

1. Get ffmpeg source

```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/ffmpeg_build
% wget -c https://www.ffmpeg.org/releases/ffmpeg-6.0.tar.xz
```

Unzip it into the following folder:

> -z(gzip), -j(bzip2), -J(xz), --lzma(lzma)

```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/ffmpeg_build
% tar xvJf ffmpeg-6.0.tar.xz
```

2. Compile and get static library

```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/ffmpeg_build
```

Modify the ffmpeg version in `config.sh` file:

```shell
FFMPEG_FOLDER=ffmpeg-<ffmepg version>
```

Run any one of the following scripts as you want:

```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/ffmpeg_build
```

```shell
% ./build_ffmpeg_adpcm_ima_qt_codec.sh
% ./build_ffmpeg_h264_hevc_decoder.sh
% ./build_ffmpeg_adpcm_ima_qt_codec_h264_h265_decoder.sh
```

3. Generate `so` files.
   The above shell script in `Step 2` has already generated `so` files. However, if you want to generate it again,
   in Android Studio, just build project, you will get `so` files.
   Or execute the following command under
   `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni`
   folder:

```shell
% ndk-build
```

or execute command with full parameters:

```shell
% ndk-build NDK_PROJECT_PATH=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni APP_PLATFORM=android-21 NDK_APPLICATION_MK=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni/Application.mk APP_BUILD_SCRIPT=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni/Android.mk
```

Then you will get each generate `so` file
in `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/libs`
folder.

## How to check 16KB align?
### By using `readelf`
```bash
$ readelf -l /home/yhz61010/StudioProjects/android/ffmpeg-sdk/src/main/libs/arm64-v8a/libadpcm-ima-qt-encoder.so | grep -A 1 "LOAD"
  LOAD           0x0000000000000000 0x0000000000000000 0x0000000000000000
                 0x00000000000023d0 0x00000000000023d0  R E    0x4000
  LOAD           0x00000000000023d0 0x00000000000063d0 0x00000000000063d0
                 0x0000000000000370 0x0000000000000370  RW     0x4000
  LOAD           0x0000000000002740 0x000000000000a740 0x000000000000a740
                 0x0000000000000068 0x0000000000000090  RW     0x4000
  DYNAMIC        0x00000000000023e8 0x00000000000063e8 0x00000000000063e8
```

如果 Align 列显示 0x4000（16KB = 16384 = 0x4000），说明已正确设置 16KB 对齐。如果是 0x1000（4KB），则还是 4KB 对齐。

### By using `objdump`
```bash
$ objdump -p /home/yhz61010/StudioProjects/android/ffmpeg-sdk/src/main/libs/arm64-v8a/libavutil.so | grep -A 2 "LOAD"
    LOAD off    0x0000000000000000 vaddr 0x0000000000000000 paddr 0x0000000000000000 align 2**14
         filesz 0x0000000000029a14 memsz 0x0000000000029a14 flags r--
    LOAD off    0x0000000000029a20 vaddr 0x000000000002da20 paddr 0x000000000002da20 align 2**14
         filesz 0x0000000000043bc0 memsz 0x0000000000043bc0 flags r-x
    LOAD off    0x000000000006d5e0 vaddr 0x00000000000755e0 paddr 0x00000000000755e0 align 2**14
         filesz 0x000000000000e870 memsz 0x000000000000e870 flags rw-
    LOAD off    0x000000000007be50 vaddr 0x0000000000087e50 paddr 0x0000000000087e50 align 2**14
         filesz 0x0000000000000028 memsz 0x00000000001076d9 flags rw-
 DYNAMIC off    0x000000000007b6f8 vaddr 0x00000000000836f8 paddr 0x00000000000836f8 align 2**3
```

objdump -p 输出中的 align `2**14` (2 的 14 次方 = 16384) 表示 16KB 对齐
objdump -p 输出中的 align `2**12` (2 的 12 次方 = 4096) 表示 4KB 对齐
