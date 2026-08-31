# H.264/HEVC 解码模块

此 AAR 只包含 FFmpeg H.264 和 HEVC 软件解码器，支持 `arm64-v8a`、`armeabi-v7a`、`x86` 和
`x86_64`。

## FFmpeg runtime 必须三选一

仅需要视频解码时使用此模块；同时需要 ADPCM IMA QT 音频功能和视频功能时，应改用
`adpcm-ima-qt-codec-h264-hevc-decoder`。此模块不能与组合模块或 `adpcm-ima-qt-codec` 同时引入。

Gradle Module Metadata 中的共享 capability 会保护 Gradle 消费端。只读取 Maven POM 的消费端仍需
自行遵守相同约束。不要使用 `pickFirst` 掩盖重复的 FFmpeg 动态库。

## 生命周期

`H264HevcDecoder` 实现了 `Closeable`。每个实例只能初始化一次，解码操作由实例锁串行执行，并应通过
`use {}` 或 `close()` 释放。重复关闭不会产生副作用；初始化前或关闭后解码会抛出
`IllegalStateException`。
