**DO NOT** add this module to project if you want to push sources to `github`.
This means do not include `ffmpeg-sdk` module in `settings.gradle.kts` if you push sources to `github`.

**Suggestion**

If you want to develop, you'd better add this module into `settings.gradle.kts` or else the source code won't be highlighted and can't be compiled.

**This module can not be imported by other projects.**
If you want to import this module by other projects, you can make a wrapper module just like [adpcm-ima-qt-codec-sdk] and copy any necessary sources from this module to that wrapper project.

## Build Process

**Prerequisites:** Add `include(":ffmpeg-sdk")` to `settings.gradle.kts` before building.

### One-command build (recommended)

The `build_ffmpeg_*.sh` scripts handle the entire build process automatically:
1. Compile FFmpeg source code into prebuilt shared libraries (with only the required codecs enabled)
2. Build JNI wrapper libraries via Gradle/CMake
3. Copy the generated `.so` files to the corresponding wrapper module

```bash
cd ffmpeg-sdk/src/main/ffmpeg_build
```

Modify the FFmpeg version and NDK path in `config.sh` first, then run one of:

```bash
# ADPCM IMA QT codec only → copies to [adpcm-ima-qt-codec] module
./build_ffmpeg_adpcm_ima_qt_codec.sh

# H.264/HEVC decoder only → copies to [h264-hevc-decoder] module
./build_ffmpeg_h264_hevc_decoder.sh

# Both ADPCM codec + H.264/HEVC decoder → copies to [adpcm-ima-qt-codec-h264-hevc-decoder] module
./build_ffmpeg_adpcm_ima_qt_codec_h264_h265_decoder.sh
```

> **IMPORTANT: Build order matters.**
>
> Each `build_ffmpeg_*.sh` script configures FFmpeg with **different codec options**, producing a different `prebuilt/` directory (i.e., `libavcodec.so` with only the required codecs, minimizing binary size). Running a different script **overwrites** the `prebuilt/` directory.
>
> The script automatically calls `./gradlew :ffmpeg-sdk:assembleRelease` at the end, which builds **all three** JNI libraries (`h264-hevc-decoder`, `adpcm-ima-qt-decoder`, `adpcm-ima-qt-encoder`) regardless of the FFmpeg configuration. However, only the relevant `.so` files are copied to the target wrapper module.
>
> Therefore, **you must run scripts one at a time** and let each one complete fully (including the copy step) before running the next. Do NOT run all `build_ffmpeg_*.sh` scripts first and then do a single Gradle build — this would result in all wrapper modules using the same FFmpeg prebuilt from the last script, defeating the purpose of minimal codec builds.

### Step-by-step breakdown

If you need to run each step separately:

#### Step 1 — Build FFmpeg prebuilt libraries

Download and extract FFmpeg source:

```bash
cd ffmpeg-sdk/src/main/ffmpeg_build
wget -c https://www.ffmpeg.org/releases/ffmpeg-8.1.tar.xz
tar xvJf ffmpeg-8.1.tar.xz
```

Modify `config.sh`:

```bash
FFMPEG_FOLDER=ffmpeg-8.1
NDK_PATH=/path/to/your/ndk
```

Run the build script (without the auto-copy at the end, just Ctrl+C before the last line, or comment out the last `sh` line):

This generates `prebuilt/<abi>/lib/*.so` and `prebuilt/<abi>/include/` directories.

#### Step 2 — Build JNI libraries via Gradle/CMake

This compiles the JNI wrapper libraries (`libh264-hevc-decoder.so`, `libadpcm-ima-qt-decoder.so`, `libadpcm-ima-qt-encoder.so`) that link against the FFmpeg prebuilt libraries from Step 1.

```bash
./gradlew :ffmpeg-sdk:assembleRelease
```

#### Step 3 — Copy .so files to wrapper modules

Run the corresponding copy script:

```bash
cd ffmpeg-sdk/src/main/ffmpeg_build

./ndk-build-and-copy-to-module_ffmpeg_adpcm_ima_qt_codec.sh
./ndk-build-and-copy-to-module_ffmpeg_h264_hevc_decoder.sh
./ndk-build-and-copy-to-module_ffmpeg_adpcm_ima_qt_codec_h264_h265_decoder.sh
```

> Note: These scripts will also run `./gradlew :ffmpeg-sdk:assembleRelease` internally, so Step 2 can be skipped if you run these directly.

### Output locations

The generated `.so` files can be found under:

```
ffmpeg-sdk/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/<abi>/
```

You can use the following command to find all `.so` files:

```bash
find ffmpeg-sdk/ -type f -name "*.so"
```

See also: `00-documents/android-native-build-output-directories.md`

### CMake configuration

The `CMakeLists.txt` is at `src/main/cpp/CMakeLists.txt`. It builds three shared libraries:

- `libh264-hevc-decoder.so` — links against `avcodec`, `avutil`, `swscale`
- `libadpcm-ima-qt-decoder.so` — links against `avcodec`, `avutil`
- `libadpcm-ima-qt-encoder.so` — links against `avcodec`, `avutil`

All libraries enforce **16KB page alignment** via `-Wl,-z,max-page-size=16384` for Android compatibility.

## How to verify 16KB alignment

### Using `readelf`

```bash
readelf -l ffmpeg-sdk/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/libadpcm-ima-qt-encoder.so | grep -A 1 "LOAD"
```

If the Align column shows `0x4000` (16KB = 16384 = 0x4000), the alignment is correct. If it shows `0x1000` (4KB), the alignment is still 4KB.

### Using `objdump`

```bash
objdump -p ffmpeg-sdk/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/libavutil.so | grep -A 2 "LOAD"
```

- `align 2**14` (2^14 = 16384) means 16KB aligned.
- `align 2**12` (2^12 = 4096) means 4KB aligned.
