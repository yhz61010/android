# H.264/HEVC decoder

This AAR contains only the FFmpeg H.264 and HEVC software decoders. It supports `arm64-v8a`,
`armeabi-v7a`, `x86`, and `x86_64`.

## Choose exactly one FFmpeg runtime

Use this artifact only when the application needs video decoding without ADPCM IMA QT. Use
`adpcm-ima-qt-codec-h264-hevc-decoder` when both audio and video features are required. Never combine
this artifact with that combined artifact or with `adpcm-ima-qt-codec`.

Gradle consumers are protected by a shared capability in Gradle Module Metadata. Maven/POM-only
consumers must enforce the same rule themselves. Do not use `pickFirst` to hide duplicate FFmpeg
libraries.

## Lifecycle

`H264HevcDecoder` implements `Closeable`. Initialize each instance once, serialize decode operations
through that instance, and release it with `use {}` or `close()`. Closing is idempotent; decoding before
initialization or after closing fails with `IllegalStateException`.
