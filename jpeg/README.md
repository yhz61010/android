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


## How to check 16KB align?
### By using `readelf`
```bash
$ readelf -l /home/yhz61010/StudioProjects/android/jpeg/libs/arm64-v8a/libjpeg.so | grep -A 1 "LOAD"
  LOAD           0x0000000000000000 0x0000000000000000 0x0000000000000000
                 0x0000000000057030 0x0000000000057030  R E    0x4000
  LOAD           0x0000000000057030 0x000000000005b030 0x000000000005b030
                 0x0000000000000be8 0x0000000000000be8  RW     0x4000
  LOAD           0x0000000000057c18 0x000000000005fc18 0x000000000005fc18
                 0x0000000000000008 0x0000000000000009  RW     0x4000
```

如果 Align 列显示 0x4000（16KB = 16384 = 0x4000），说明已正确设置 16KB 对齐。如果是 0x1000（4KB），则还是 4KB 对齐。

### By using `objdump`
```bash
$ objdump -p /home/yhz61010/StudioProjects/android/jpeg/libs/arm64-v8a/libturbojpeg.so | grep -A 2 "LOAD"
    LOAD off    0x0000000000000000 vaddr 0x0000000000000000 paddr 0x0000000000000000 align 2**14
         filesz 0x000000000006cdd0 memsz 0x000000000006cdd0 flags r-x
    LOAD off    0x000000000006cdd0 vaddr 0x0000000000070dd0 paddr 0x0000000000070dd0 align 2**14
         filesz 0x0000000000000b28 memsz 0x0000000000000b28 flags rw-
    LOAD off    0x000000000006d8f8 vaddr 0x00000000000758f8 paddr 0x00000000000758f8 align 2**14
         filesz 0x0000000000000110 memsz 0x0000000000000158 flags rw-
 DYNAMIC off    0x000000000006d360 vaddr 0x0000000000071360 paddr 0x0000000000071360 align 2**3
```

objdump -p 输出中的 align `2**14` (2 的 14 次方 = 16384) 表示 16KB 对齐
objdump -p 输出中的 align `2**12` (2 的 12 次方 = 4096) 表示 4KB 对齐
