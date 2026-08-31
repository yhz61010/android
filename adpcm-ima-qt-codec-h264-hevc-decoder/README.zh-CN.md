# ADPCM IMA QT 与 H.264/HEVC 组合模块

此 AAR 面向同时需要 ADPCM IMA QuickTime 音频功能和 H.264/HEVC 视频功能的应用，支持
`arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64`。

## FFmpeg runtime 必须三选一

不要把此模块与 `adpcm-ima-qt-codec` 或 `h264-hevc-decoder` 同时引入。两个独立模块分别用于只需要
音频或只需要视频功能的应用。Gradle Module Metadata 已声明共享 capability，同时选择两个模块时会在
依赖解析阶段报错。只读取 Maven POM 的消费端仍需自行遵守此约束。不要使用 `pickFirst` 掩盖重复的
FFmpeg 动态库。

所有编码器和解码器类均实现 `Closeable`，应优先使用 `use {}` 或显式调用 `close()`。
