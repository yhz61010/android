# How to run dex file

```shell
$ cd /<path>/LeoAndroidBaseUtilProject-Kotlin
$ ./gradlew demo-dex:assembleRelease
$ adb push demo-dex/build/outputs/apk/release/dexdemo.dex /data/local/tmp
$ adb exec-out CLASSPATH=/data/local/tmp/dexdemo.dex app_process / com.leovp.demo_dex.DexMain
```

# How to view screenshot on browser?

1. Install APK.
2. Run automation3.py

```shell
$ ./gradlew clean
$ cd demo-dex
$ python scripts/automation3.py
```

Check more information for `automation3.py`

```shell
$ python scripts/automation3.py -h
```

3. After running script, the screenshot will be shown in browser.

# Reference

https://github.com/rayworks/DroidCast