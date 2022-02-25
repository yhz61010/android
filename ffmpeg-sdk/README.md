**DO NOT** add this module to project if you want to push sources to `github`.
This means, do not include `ffmpeg-sdk` module in `settings.gradle` if you push sources to `github`.

**This module can no be imported by other projects.**
If your want to import this module by other projects, you can make a wrapper module just like [adpcm-ima-qt-codec-sdk] and copy any necessary sources form this module to that wrapper project.

### Compile Environment：
- OS：macOS 11.6
- NDK：22.1.7171670
- FFmpeg 5.0 "Lorentz"(5.0 was released on 2022-01-17)
- cmake: 3.20.3
- gcc:
  Configured with: --prefix=/Library/Developer/CommandLineTools/usr --with-gxx-include-dir=/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/c++/4.2.1
  Apple clang version 13.0.0 (clang-1300.0.29.3)
  Target: x86_64-apple-darwin20.6.0
  Thread model: posix
  InstalledDir: /Library/Developer/CommandLineTools/usr/bin

### How to compile ffmpeg and generate so file for Android - Only with `adpcm_ima_qt` decoder
1. Get ffmpeg source
```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni
% wget -c https://www.ffmpeg.org/releases/ffmpeg-5.0.tar.xz
```
Unzip it into the following folder:
> # -z(gzip), -j(bzip2), -J(xz), --lzma(lzma)
```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni
% tar xvJf ffmpeg-5.0.tar.xz
```
2. Compile and get static library
```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni
% ./build_ffmpeg_all.sh
```
3. Generate so file with jni file
In Android Studio, just build project, you will get so files. Or execute the following command under `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni` folder:
```shell
% ndk-build
```
or execute command with full parameters:
```shell
% ndk-build NDK_PROJECT_PATH=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni APP_PLATFORM=android-21 NDK_APPLICATION_MK=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni/Application.mk APP_BUILD_SCRIPT=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni/Android.mk
```
Then you will get each generate `so` file in `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/libs` folder.
