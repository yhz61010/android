## Native Build

This module uses **CMake** to build the native `libleo-bitmap.so` library. The source files are located in `src/main/cpp/`.

### Build via Android Studio

Simply build this module in Android Studio. Gradle will invoke CMake automatically and the `.so` files for all configured ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) will be generated and packaged into the AAR.

### Build via command line

```bash
./gradlew :lib-image:assembleRelease
```

The generated `.so` files can be found under:

```
lib-image/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/<abi>/libleo-bitmap.so
```

PS:
You can use the following command to find the all the `*.so` files.
```
find lib-image/ -type f -name "*.so"
```

See also: 
`00-documents/android-native-build-output-directories.md`

### CMake configuration

The `CMakeLists.txt` is at `src/main/cpp/CMakeLists.txt`. It links against `log` and `jnigraphics`, and enforces **16KB page alignment** via `-Wl,-z,max-page-size=16384` for Android compatibility.

## How to verify 16KB alignment

### Using `readelf`

```bash
readelf -l lib-image/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/libleo-bitmap.so | grep -A 1 "LOAD"
```

If the Align column shows `0x4000` (16KB = 16384 = 0x4000), the alignment is correct. If it shows `0x1000` (4KB), the alignment is still 4KB.

### Using `objdump`

```bash
objdump -p lib-image/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/libleo-bitmap.so | grep -A 2 "LOAD"
```

- `align 2**14` (2^14 = 16384) means 16KB aligned.
- `align 2**12` (2^12 = 4096) means 4KB aligned.
