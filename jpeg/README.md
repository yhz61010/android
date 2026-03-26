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
$ readelf -l /home/yhz61010/StudioProjects/android/lib-image/obj/local/arm64-v8a/libleo-bitmap.so | grep -A 1 "LOAD"
LOAD           0x0000000000000000 0x0000000000000000 0x0000000000000000
0x0000000000002580 0x0000000000002580  R E    0x4000
LOAD           0x0000000000002580 0x0000000000006580 0x0000000000006580
0x0000000000000268 0x0000000000000268  RW     0x4000
LOAD           0x00000000000027e8 0x000000000000a7e8 0x000000000000a7e8
0x0000000000000108 0x0000000000000108  RW     0x4000
```

如果 Align 列显示 0x4000（16KB = 16384 = 0x4000），说明已正确设置 16KB 对齐。如果是 0x1000（4KB），则还是 4KB 对齐。

### By using `objdump`
```bash
$ objdump -p /home/yhz61010/StudioProjects/android/lib-image/obj/local/arm64-v8a/libleo-bitmap.so | grep -A 2 "LOAD"
LOAD off    0x0000000000000000 vaddr 0x0000000000000000 paddr 0x0000000000000000 align 2**14
filesz 0x0000000000002580 memsz 0x0000000000002580 flags r-x
LOAD off    0x0000000000002580 vaddr 0x0000000000006580 paddr 0x0000000000006580 align 2**14
filesz 0x0000000000000268 memsz 0x0000000000000268 flags rw-
LOAD off    0x00000000000027e8 vaddr 0x000000000000a7e8 paddr 0x000000000000a7e8 align 2**14
filesz 0x0000000000000108 memsz 0x0000000000000108 flags rw-
DYNAMIC off    0x0000000000002598 vaddr 0x0000000000006598 paddr 0x0000000000006598 align 2**3
```

objdump -p 输出中的 align `2**14` (2 的 14 次方 = 16384) 表示 16KB 对齐
objdump -p 输出中的 align `2**12` (2 的 12 次方 = 4096) 表示 4KB 对齐
