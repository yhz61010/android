# How to run dex file
```shell
$ cd /<path>/LeoAndroidBaseUtilProject-Kotlin
$ ./gradlew demo-dex:assembleRelease
$ adb push demo-dex/build/outputs/apk/release/dexdemo.dex /data/local/tmp
$ adb exec-out CLASSPATH=/data/local/tmp/dexdemo.dex app_process / com.leovp.dexdemo.DexMain
```

# How to view screenshot on browser?
1. Install APK & dex then run automation3.py:
```shell
$ cd /<path>/LeoAndroidBaseUtilProject-Kotlin
$ ./gradlew demo-dex:clean demo-dex:installDebug
$ cd demo-dex
$ python scripts/automation3.py
```

2. After running script, the screenshot will be shown in browser.

# How to view live screen on browser?

1. Follow the above steps.
2. Open the `/<path>/LeoAndroidBaseUtilProject-Kotlin/demo-dex/web/image.html` file.

# Others

Check more information for `automation3.py`

```shell
$ cd /<path>/LeoAndroidBaseUtilProject-Kotlin
$ cd demo-dex
$ python scripts/automation3.py -h
```

# Reference

https://github.com/rayworks/DroidCast
