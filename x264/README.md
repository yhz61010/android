### How to compile x264 and generate so file for Android
1. Get x264 source
```shell
$ cd /home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject/x264/src/main/cpp
$ git clone http://git.videolan.org/git/x264.git libx264
```
2. Compile and get static library
```shell
./build_x264_all.sh
```
3. Generate so file with jni file
In Android Studio, just build project, you will get so files. Or execute the following command under `./LeoAndroidBaseUtilProject/x264/src/main/cpp` folder:
```shell
$ ndk-build NDK_PROJECT_PATH=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject/x264/src/main/cpp APP_PLATFORM=android-21 NDK_APPLICATION_MK=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject/x264/src/main/cpp/Application.mk APP_BUILD_SCRIPT=/home/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject/x264/src/main/cpp/Android.mk
```
Then you will get each generate `so` file in `./AndroidStudioProjects/LeoAndroidBaseUtilProject/x264/src/main/cpp/libs` folder.
