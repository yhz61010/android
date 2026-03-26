**Attention**:
`libjpeg-turbo-v2.1.4-202200823.tar.gz` This file is downloaded at 2022/08/23.

## Android 16KB Page Size Alignment Fix

This module has been updated to support Android's 16KB page size requirement for arm64-v8a architecture.
The following changes were made to ensure compatibility with Android 15 and later versions:

1. **Build Script (`00_build_jpeg.sh`)**: Added linker flags to CMake command:
   - `-DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"`
   - `-DCMAKE_MODULE_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"`

2. **CMakeLists.txt**: Added automatic 16KB alignment configuration for arm64-v8a builds:
   ```cmake
   if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
     set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
     set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
     set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
   endif()
   ```

These changes ensure that all ELF segments in the compiled `.so` files are aligned to 16KB boundaries,
meeting the requirements of Android 15+ on arm64-v8a devices.

## How to compile `ibjpeg-turbo`

### Download
Download `libjpeg-turbo` sources or use the downloaded sources `libjpeg-turbo-main-20220325.tar.gz`
(This is the original official version just excludes `.git` and `.github` folder. Downloaded date: 2022/03/25)

```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libjpeg-turbo-main
$ rm -rf source
$ mkdir source
$ cd source
$ git clone https://github.com/libjpeg-turbo/libjpeg-turbo.git .
# or unzip `libjpeg-turbo-v2.1.4-202200823.tar.gz` file
$ tar xvzf ../libjpeg-turbo-v2.1.4-202200823.tar.gz --strip-components 1
```

### Compile
Run the following command:
```shell
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libjpeg-turbo-main/
$ sh 00_build_jpeg_all.sh
```

### How to check 16KB align?
#### By using `readelf`
```bash
$ readelf -l /home/yhz61010/StudioProjects/android/libjpeg-turbo-main/libs/arm64-v8a/lib/libjpeg.so | grep -A 1 "LOAD"
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
$ objdump -p /home/yhz61010/StudioProjects/android/libjpeg-turbo-main/libs/arm64-v8a/lib/libturbojpeg.so | grep -A 2 "LOAD"
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
