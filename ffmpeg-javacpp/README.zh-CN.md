# FFmpeg JavaCPP ADPCM 解码模块

此模块通过 JavaCPP 提供 ADPCM IMA QuickTime 解码能力。当前发布模块**仅支持
`arm64-v8a`**，并且只声明 FFmpeg 和 JavaCPP 的 `android-arm64` classifier 依赖；这些 Native
库由消费端传递解析，不直接内嵌到本模块 AAR。需要 `armeabi-v7a`、`x86` 或 `x86_64` 的应用应选择
其它模块。

`AdpcmImaQTDecoder` 由实例唯一持有并复用 codec context、packet 和 frame。该类实现了
`Closeable`，应使用 `use {}` 或显式调用 `close()`。单声道输出约定为
`leftBytes to ByteArray(0)`。
