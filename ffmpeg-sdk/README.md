### Compile Environment：
- OS：macOS 10.15.5
- NDK：22.1.7171670
- Ffmpeg：4.4 "Rao"(Until 20210609)
- cmake: 3.20.3
- gcc:
  Configured with: --prefix=/Library/Developer/CommandLineTools/usr --with-gxx-include-dir=/Library/Developer/CommandLineTools/SDKs/MacOSX10.15.sdk/usr/include/c++/4.2.1
  Apple clang version 12.0.0 (clang-1200.0.32.29)
  Target: x86_64-apple-darwin19.5.0
  Thread model: posix
  InstalledDir: /Library/Developer/CommandLineTools/usr/bin

### How to compile ffmpeg and generate so file for Android - Only with `adpcm_ima_qt` decoder
1. Get ffmpeg source
```shell
% wget -c https://www.ffmpeg.org/releases/ffmpeg-4.4.tar.bz2
```
Unzip it into the following folder:
```shell
cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni
tar xvjf ffmpeg-4.4.tar.bz2
```
2. Compile and get static library
```shell
% cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni
% ./build_ffmpeg_all.sh
```
3. Generate so file with jni file
In Android Studio, just build project, you will get so files. Or execute the following command under `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni` folder:
```shell
$ ndk-build
```
or execute command with full parameters:
```shell
$ ndk-build NDK_PROJECT_PATH=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni APP_PLATFORM=android-21 NDK_APPLICATION_MK=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni/Application.mk APP_BUILD_SCRIPT=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/jni/Android.mk
```
Then you will get each generate `so` file in `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/ffmpeg-sdk/src/main/libs` folder.
