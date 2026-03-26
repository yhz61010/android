### Compile `libjpeg-turbo`

Please read `README.md` in `libjpeg-turbo-main` module. Then compile `libjpeg-turbo`. Copy the
generated header files from `libs/(arm64-v8a|armeabi-v7a)/include/*.h` into `jpeg`
module `src/main/cpp/include`. Copy the generated `so` files from `libs/<ABIS>/lib/*.so`
into `jpeg` module `libs/<ABIS>`. The final file structures like this:

```
jpeg
    |-> libs
    |    |-> arm64-v8a
    |    |        |-> libjpeg.so
    |    |        |-> libturbojpeg.so
    |    |-> armeabi-v7a
    |             |-> libjpeg.so
    |             |-> libturbojpeg.so
    |-> src
         |-> main
              |-> cpp
                   |-> include
                          |-> header files copied from `libjpeg-turbo-main`
```

You can run the following handy shell to do the things mentioned above:

```shell
$ cd /Users/yhz61010/AndroidStudioProjects/android/jpeg/
$ sh copy_necessary_files_from_jpeg_burbo.sh
```

## How to compile `jpeg`

You have three ways to compile `jpeg` module:

- Compile with `gradlew` command.

```
$ cd /Users/yhz61010/AndroidStudioProjects/android/
$ ./gradlew :jpeg:assemble
```

- Compile from `Gradle` sidebar. Run from right sidebar **Gradle -> LeoAndroidBaseUtil -> jpeg
  -> build -> assemble **.
- Compile from `Build` menu. Select `jpeg` module then click from menu **Build -> Make Module '
  LeoAndroidBaseUtil.jpeg'**. You can select the compile option from `Build Variants` menu.
