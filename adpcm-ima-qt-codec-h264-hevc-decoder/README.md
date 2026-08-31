# ADPCM IMA QT and H.264/HEVC codecs

This AAR is the combined FFmpeg runtime for applications that require both ADPCM IMA QuickTime audio
and H.264/HEVC video features. It supports `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

## Choose exactly one FFmpeg runtime

Do not combine this artifact with `adpcm-ima-qt-codec` or `h264-hevc-decoder`. Those smaller artifacts
exist for applications that need only audio or only video. Gradle Module Metadata declares a shared
capability so Gradle consumers fail dependency resolution when two variants are selected.
Maven/POM-only consumers must enforce this rule themselves. Do not use `pickFirst` to hide duplicate
FFmpeg libraries.

All encoder and decoder classes implement `Closeable`. Prefer `use {}` or call `close()` explicitly.
