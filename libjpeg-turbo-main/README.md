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
