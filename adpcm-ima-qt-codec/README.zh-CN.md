# ADPCM IMA QT 编解码模块

此 AAR 只包含 FFmpeg ADPCM IMA QuickTime 编码器和解码器，支持 `arm64-v8a`、
`armeabi-v7a`、`x86` 和 `x86_64`。

## FFmpeg runtime 必须三选一

以下三个产物包含不同裁剪配置的同名 FFmpeg runtime，不能同时引入：

- `adpcm-ima-qt-codec`：仅音频功能。
- `h264-hevc-decoder`：仅视频功能。
- `adpcm-ima-qt-codec-h264-hevc-decoder`：音频和视频组合功能。

Gradle Module Metadata 已声明共享 capability，同时选择任意两个产物时，Gradle 会在依赖解析阶段报错。
只读取 Maven POM 的消费端无法识别 capability，仍需自行遵守此约束。不要使用 `pickFirst` 掩盖重复的
FFmpeg 动态库。

## 生命周期与输入契约

`AdpcmImaQtEncoder` 和 `AdpcmImaQtDecoder` 均实现 `Closeable`，应使用 `use {}` 或显式调用
`close()`。重复关闭不会产生副作用，关闭后继续操作会抛出 `IllegalStateException`。

解码器每次只接受每声道一个 34 字节 IMA QT chunk。编码器输入必须由一个或多个完整 PCM16 帧组成
（每声道 64 个采样）；不完整的尾部数据会抛出 `IllegalArgumentException`。
