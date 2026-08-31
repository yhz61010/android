# H.264/HEVC 解码模块

此 AAR 只包含 FFmpeg H.264 和 HEVC 软件解码器，支持 `arm64-v8a`、`armeabi-v7a`、`x86` 和
`x86_64`。

## FFmpeg runtime 必须三选一

仅需要视频解码时使用此模块；同时需要 ADPCM IMA QT 音频功能和视频功能时，应改用
`adpcm-ima-qt-codec-h264-hevc-decoder`。此模块不能与组合模块或 `adpcm-ima-qt-codec` 同时引入。

Gradle Module Metadata 中的共享 capability 会保护 Gradle 消费端。只读取 Maven POM 的消费端仍需
自行遵守相同约束。不要使用 `pickFirst` 掩盖重复的 FFmpeg 动态库。

## 生命周期

`H264HevcDecoder` 实现了 `Closeable`。同一实例每次只处理一条流，解码操作由实例锁串行执行，
并应通过 `use {}` 或 `close()` 释放。重复关闭不会产生副作用；初始化前或关闭后解码会抛出
`IllegalStateException`。

## 解码输出契约

`decode(packet)` 继续供每次调用最多消费一帧的既有代码使用。FFmpeg 对一个 packet 可能产生 0 帧、
1 帧或多帧，解码器会保留额外输出，不再直接丢弃。需要完整接收输出的新代码应使用
`decodeFrames(packet)`。如果之前的兼容 API `decode()` 留下额外帧，`decodeFrames()` 会先返回这些帧，
再返回处理当前 packet 时新产生的所有可取输出。

送入最后一个 packet 后，必须以 `drain()` 作为流结束步骤。它会返回此前保留的帧以及 codec 内部延迟的
帧，包括因 B 帧重排而延迟的输出。开始 drain 后，该实例不再接受新输入；处理另一条流时应关闭并
重新初始化当前实例，或使用新实例。
