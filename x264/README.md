### How to compile x264 and generate so file for Android
1. Get x264 source(until 20211102)
```shell
$ cd /home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/x264/src/main/jni
$ git clone http://git.videolan.org/git/x264.git libx264
```
or get the zip file:
```shell
% wget https://code.videolan.org/videolan/x264/-/archive/master/x264-master.tar.bz2
% mkdir libx264 && tar xvjf x264-master.tar.bz2 -C `pwd`/libx264 --strip-components=1
```
2. Compile and get static library
```shell
./build_x264_all.sh
```
3. Generate so file with jni file
In Android Studio, just build project, you will get so files. Or execute the following command under `./LeoAndroidBaseUtilProject-Kotlin/x264/src/main/jni` folder:
```shell
$ ndk-build
```
or execute command with full parameters:
```shell
$ ndk-build NDK_PROJECT_PATH=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/x264/src/main/jni APP_PLATFORM=android-21 NDK_APPLICATION_MK=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/x264/src/main/jni/Application.mk APP_BUILD_SCRIPT=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/x264/src/main/jni/Android.mk
```
Then you will get each generate `so` file in `./AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/x264/src/main/jni/libs` folder.

### Reference
- https://github.com/bakaoh/X264Android