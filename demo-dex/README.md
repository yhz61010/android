# How to run dex file

```shell
$ cd /<path>/LeoAndroidBaseUtilProject-Kotlin
$ ./gradlew demo-dex:assembleRelease
$ adb push dexdemo.dex /data/local/tmp
$ adb exec-out CLASSPATH=/data/local/tmp/dexdemo.dex app_process / com.leovp.demo_dex.DexMain
```