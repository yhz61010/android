### How to compile ffmpeg and generate so file for Android - Only with `adpcm_ima_qt` decoder
1. Get ffmpeg source
```shell
$ cd /home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni
$ git clone https://git.ffmpeg.org/ffmpeg.git ffmpeg
```
2. Compile and get static library
```shell
./libadpcm_ima_qt_jni
```
3. Generate so file with jni file
In Android Studio, just build project, you will get so files. Or execute the following command under `./LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni` folder:
```shell
$ ndk-build
```
or execute command with full parameters:
```shell
$ ndk-build NDK_PROJECT_PATH=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni APP_PLATFORM=android-21 NDK_APPLICATION_MK=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni/Application.mk APP_BUILD_SCRIPT=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni/Android.mk
```
Then you will get each generate `so` file in `./AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/adpcm-ima-qt-sdk/src/main/jni/libs` folder.
