# ADPCM IMA QT codec

This AAR contains only the FFmpeg ADPCM IMA QuickTime encoder and decoder. It supports
`arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

## Choose exactly one FFmpeg runtime

The following artifacts contain different builds of the same FFmpeg runtime and must not coexist:

- `adpcm-ima-qt-codec`: audio only.
- `h264-hevc-decoder`: video only.
- `adpcm-ima-qt-codec-h264-hevc-decoder`: audio and video.

Gradle Module Metadata declares a shared capability, so Gradle consumers that select two of these
artifacts fail dependency resolution. Maven/POM-only consumers do not understand that capability and
must enforce this rule themselves. Do not use `pickFirst` to hide duplicate FFmpeg libraries.

## Lifecycle and input contract

`AdpcmImaQtEncoder` and `AdpcmImaQtDecoder` implement `Closeable`; use `use {}` or call `close()`.
Closing is idempotent and operations after closing fail with `IllegalStateException`.

The decoder accepts exactly one 34-byte IMA QT chunk per channel. Encoder input must contain one or
more complete PCM16 frames (64 samples per channel); incomplete trailing data is rejected with
`IllegalArgumentException`.
