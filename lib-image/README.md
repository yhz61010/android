## Generate `so` files with jni file

In Android Studio, just build project, you will get `so` files. Or execute the following command
under
`/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/lib-image` folder:

```shell
% ndk-build
```

or execute command with full parameters:

```shell
% ndk-build NDK_PROJECT_PATH=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/lib-image/jni APP_PLATFORM=android-21 NDK_APPLICATION_MK=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/lib-image/jni/Application.mk APP_BUILD_SCRIPT=/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/lib-image/jni/Android.mk
```

Then you will get each generate `so` file
in `/Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/lib-image/libs` folder.
