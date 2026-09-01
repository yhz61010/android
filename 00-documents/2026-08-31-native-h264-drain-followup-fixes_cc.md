# H.264/HEVC 状态机后续复审与整改记录（2026-08-31，_cc）

> 本文最初由 Claude Code 对提交 `50c9949ed fix(native): complete codec review remediation`
> 进行后续复审。维护者将原文复制到 `review/native-modules` 分支后，Codex 又以当前 Kotlin/JNI
> 源码、FFmpeg 8.1.1 契约、Android/Oracle JNI 资料和本机四 ABI 构建结果逐条核对。本版保留成立的
> 结论，订正错误的成因、严重度与修复建议，并记录实际整改和验证边界。

## 1. 核对结论

后续复审列出的 8 项并非全部属于本次状态机改造的新回归，也并非全部需要按原建议修改：

| 项目 | 核对结果 | 来源属性 | 当前处理 |
|---|---|---|---|
| H1：连续 `NewStringUTF` 间缺异常检查 | 成立 | 既有问题 | 已修复 |
| M1：`drain()` 异常时提前移除 Kotlin pending 帧 | 成立；异常会向调用方传播，并非“静默” | 本次新增 | 已修复 |
| M2：热路径连续解析 `ArrayList` 方法 | 成立 | 本次新增 | 已由统一 JNI 缓存修复 |
| M3：`JNI_OnLoad` 返回失败前未清除异常 | 不成立 | 既有写法 | 不修改 |
| M4：每帧解析 `DecodedVideoFrame` 类和构造器 | 成立 | 既有问题 | 已修复 |
| L1：异常路径把 `nativeHandle` 再写为 0 | 原建议不安全 | 既有写法 | 不采用原建议 |
| L2：send 返回 `AVERROR_EOF` 的错误信息不明确 | 成立 | 本次新增 | 已修复 |
| L3：`decode()` 的 pending 队列无上限 | 风险成立，但原成因不准确 | 本次新增 | 保留公开语义决策 |

本轮修复 H1、M1、M2、M4 和 L2。M3 与 L1 不按原文修改；L3 以及“同一次 Native 调用先产出部分帧、
随后发生硬错误”涉及公开 API 的输出/错误语义，现有信息不足以替维护者选择，因此只记录方案，不擅自改变。

## 2. 已实施整改

### 2.1 H1：每次创建字符串后立即检查 JNI 异常

`NativeInit()` 原来连续创建 codec 名称和 pixel format 名称，直到两次 `NewStringUTF()` 都执行后才检查
pending exception。如果第一次分配触发 `OutOfMemoryError`，第二次 JNI 调用会发生在异常挂起状态。

当前实现对两次 `NewStringUTF()` 分别立即检查：

- 有 pending exception 时保留原异常，销毁尚未发布给 Kotlin 的 decoder context 并返回；
- 返回 `null` 但没有 pending exception 时主动补充 `OutOfMemoryError`；
- 第一次失败后不再创建第二个字符串；
- 清理时只调用允许在异常挂起状态使用的资源释放操作。

这项问题在 `50c9949ed` 的父提交中已存在，因此不能描述为 send/receive 状态机新引入的缺陷。

### 2.2 M1：Native drain 成功后才提交 Kotlin pending 队列

旧顺序先从 `pendingFrames` 移除所有帧，再调用可能抛异常的 `nativeDrain()`。Native 失败时，异常会正常
传播给调用方，但已移入局部变量的 Kotlin pending 帧无法再取回。

当前顺序改为：

1. 先标记 `endOfInput = true`，维持“drain 开始后不再接受输入”的状态机契约；
2. 调用 `nativeDrain()`；
3. 仅在 Native 成功后合并 Kotlin pending 帧与 Native 延迟帧；
4. 合并完成后清空 pending 队列并标记 `drainCompleted = true`。

因此 Native drain 抛异常时，Kotlin pending 队列保持不变，调用方可以按现有契约重试 `drain()`。
独立视频模块与音视频组合模块中的 Kotlin 副本保持逐字一致。

### 2.3 M2/M4：在库加载阶段缓存 JNI 类、字段和方法

当前 `JNI_OnLoad()` 一次性缓存以下引用：

- `H264HevcDecoder`、`DecodeVideoInfo`、`DecodedVideoFrame` 和 `ArrayList` 的 global class ref；
- `nativeHandle` 字段 ID；
- 两个 Kotlin 返回类型构造器；
- `ArrayList` 构造器与 `add()` 方法 ID。

每项查找失败都会立即停止后续查找，并在返回 `JNI_ERR` 前释放已创建的 global ref。解码热路径不再逐帧
执行 `FindClass()`/`GetMethodID()`。新增 `JNI_OnUnload()` 释放缓存的 global ref；方法和字段 ID 不需要
单独释放。`NewObject()` 返回 `null` 且没有 pending exception 的防御路径会补充 `OutOfMemoryError`。

### 2.4 L2：区分 decoder 已进入 EOF

`avcodec_send_packet()` 返回 `AVERROR_EOF` 时，现在抛出
`IllegalStateException("Decoder has already reached end of input")`；其它 send 硬错误仍使用原通用信息，
并在 Native 日志中保留 FFmpeg 返回码。

## 3. 未按原建议修改的项目

### 3.1 M3：`JNI_OnLoad` 失败时不主动清除 pending exception

原复审要求所有 `return JNI_ERR` 前调用 `ExceptionClear()`，理由是与 jpeg 实现保持一致。这个要求不成立：

- JNI 规范允许 `JNI_OnLoad()` 返回 `JNI_ERR` 表示库无法加载；
- Android NDK 官方示例在 `FindClass()` 或 `RegisterNatives()` 失败后直接返回 `JNI_ERR`，不清除异常；
- 保留原 pending exception 有助于保留类/方法解析失败的真实根因；
- `ClearJniCache()` 在失败路径只释放 global ref，不调用需要正常 JNI 状态的业务方法。

因此本模块不复制 jpeg 的清除策略。两个模块的初始化结构和错误传播边界不同，“实现一致”不能替代 JNI
契约本身。参考：Android《JNI tips》、Android NDK `native-codec` 示例和 Oracle JNI 设计说明。

### 3.2 L1：不能在 pending exception 下调用 `SetLongField()`

原建议是在 `SetLongField()` 抛异常后再次调用 `SetLongField(object, field, 0L)`，然后释放 context。
第二次字段写入同样不是 pending exception 下允许调用的 JNI 操作，CheckJNI 环境中反而可能把低概率错误
放大为进程终止，所以不采用。

本轮缓存 `nativeHandle` 字段 ID 后，字段解析已经移出初始化热路径。初始化仍遵循：只有 context 完整创建、
Kotlin 返回对象也创建成功后才把 context 交给对象；字段写入失败时保留原异常并释放未成功发布的 context。
如果未来要进一步消除此理论窗口，应重新设计 handle 发布协议，而不是在 pending exception 下二次写字段。

## 4. 仍待维护者确认的公开语义

### 4.1 同一次 Native 调用产生部分帧后又遇到硬错误

`ReceiveFrames()` 可能已经把若干独立 Java 帧加入本次局部列表，随后 FFmpeg receive 或 JNI 分配发生
硬错误。当前 API 采用强失败语义：抛异常，本次局部列表不返回。要同时返回“部分成功帧 + 错误”，需要
新增结果类型或 callback，不能在 `List<DecodedVideoFrame>` 返回值上无损表达。

建议保持当前强失败语义，避免破坏现有 API；如果业务明确要求尽可能播放损坏流，再单独设计
`DecodeResult(frames, error)` 一类新 API。

### 4.2 `decode()` pending 队列的容量和消费方式

`decode()` 为兼容旧签名每次最多返回一帧，单 packet 的额外输出会进入 `pendingFrames`。队列增长取决于
“每次调用产生的帧数”和“调用方消费速度”的差值，不只由 B 帧重排深度决定。正常逐帧 packet 输入通常
不会持续增长，但聚合 packet、异常流或调用模式不匹配时没有硬上限。

建议保持 `decode()` 的兼容行为，并把 `decodeFrames()` 作为完整消费的推荐 API。若必须提供硬性内存上限，
应先确认溢出策略是抛异常、丢最旧帧还是拒绝新输入；静默丢帧不能作为默认修复。

## 5. 本机验证记录

已完成：

- 使用 JBR 17.0.14 临时启用 `ffmpeg-sdk`，执行
  `:ffmpeg-sdk:assembleRelease --rerun-tasks`，四 ABI JNI 编译通过；随后恢复 `settings.gradle.kts`；
- 分别执行独立 H.264/HEVC 和 ADPCM+H.264/HEVC 组合 profile 的 FFmpeg 8.1.1 构建脚本，四 ABI
  均重新生成并复制到发布 wrapper；上游裁剪源码只有 unused/deprecated warning；
- 两个 wrapper 的 `assembleDebug`、ktlint、detekt，以及 `demo:assembleDevDebug`、ktlint、detekt 使用
  `--rerun-tasks` 通过，共执行 659 个任务；
- 两个 wrapper 的 Kotlin 实现逐字一致；
- 8 个重建后的 `libh264-hevc-decoder.so` 架构、Android API 21、`JNI_OnLoad`/`JNI_OnUnload` 导出符合预期；
- 默认构建配置没有把 `ffmpeg-sdk` 留在 `settings.gradle.kts` 中。

尚未完成：

- CheckJNI/HWASan 故障注入；
- 带 B 帧、单 packet 多帧、损坏/截断流、自然 EOS、重复 drain 的真机输出验证；
- 16KB 页设备加载和长时间 Native heap/性能曲线；
- 上述两项公开语义在维护者确认后的针对性测试。

上述 JNI 整改结论仍以源码、四 ABI 重建和本机构建验证为主；随后新增的 P3H 样本播放回归单独记录在
第 6 节。该局部真机结果不能替代 CheckJNI/HWASan、异常流、16KB page 和长时间内存验证，也不能据此
宣称“已证明没有内存安全回归”。

## 6. 真机回归发现：Demo 把 Annex-B NAL 单元误当成完整 packet

### 6.1 现象与证据

在 P3H（Android 11 / API 30、arm64-v8a、4KB page）打开 `FFMpegH264Activity` 时，最初出现主线程
闪退：

```text
java.lang.IllegalStateException: Unable to send video packet
    at com.leovp.ffmpeg.video.H264HevcDecoder.nativeDecodeFrames(Native Method)
    at com.leovp.ffmpeg.video.H264HevcDecoder.decode(H264HevcDecoder.kt:54)
    at DecodeH264RawFileByFFMpeg.decodeVideo(DecodeH264RawFileByFFMpeg.kt:80)
    at DecodeH264RawFileByFFMpeg.init(DecodeH264RawFileByFFMpeg.kt:69)
```

Native 日志中的 FFmpeg 返回码为 `-1094995529`，即 `AVERROR_INVALIDDATA`。对仓库原始样本按 Annex-B
start code 实际解析后，开头顺序如下：

| 样本 | offset 与 NAL 类型 |
|---|---|
| H.264 | `0: SEI(6)`、`734: SPS(7)`、`764: PPS(8)`、`775: IDR(5)`；首个 IDR 使用 3 字节 start code |
| H.265 | `0: prefix SEI(39)`、`861: prefix SEI(39)`、`871: VPS(32)`、`899: SPS(33)`、`942: PPS(34)`、`952: IDR(19)` |

旧 H.264 Demo 固定把前两个 NAL 当作 SPS/PPS，实际传入的是 SEI/SPS，真实 PPS 被遗漏；旧 H.265 Demo
同样把前五个 NAL 固定解释为 VPS/SPS/PPS/prefix SEI/suffix SEI，与样本实际顺序不符。旧读取器还只识别
4 字节 start code，会漏掉 H.264 首个 3 字节 start code。

旧 Demo 随后又在 Activity `onCreate()` 调用链中把拼接的 codec-specific data 当作普通视频 packet
立即送入 decoder。旧 Native 实现吞掉 `avcodec_send_packet()` 的硬错误并返回 `null`，所以错误长期隐藏；
本轮状态机整改把硬错误正确抛给 Kotlin 后，Demo 的既有输入契约错误才表现为启动闪退。也就是说，抛出
异常的 Native 改动是暴露条件，不是产生无效 packet 的根因。

### 6.2 第一次修复后发现的第二层 packet 边界问题

按类型找到首组参数集并停止重复发送 CSD 后，页面已经不再闪退，也能输出 1920×800 图像；但真机日志
仍会在部分 IDR 前出现 `AVERROR_INVALIDDATA`。进一步用 `ffprobe` 对照后确认，样本中的关键访问单元包含
前置非图像 NAL，而不是每个 NAL 都能单独作为 `avcodec_send_packet()` 的输入。例如：

- H.264 offset `531130` 的完整关键 packet 长 9544 字节，由 `PPS + IDR` 组成；
- H.265 offset `579962` 的完整关键 packet 长 16549 字节，由
  `prefix SEI + VPS + SPS + PPS + IDR` 组成。

逐 NAL 调用 decoder 会先单独发送 PPS/VPS/SPS/SEI；FFmpeg 可以拒绝这种不完整 packet，而紧随其后的
IDR 又可能继续成功，因此表现为“播放继续但周期性记录发送错误”。这不是 send/receive 状态机可以在
Native 内猜测并修补的问题：`decodeFrames()` 的公开输入契约是一个完整 encoded packet，Demo 必须先恢复
样本的 packet 边界。

### 6.3 实施的修复

Demo 侧现采用以下处理：

1. 新增顺序式 `AnnexBNalUnitReader`，同时识别 3 字节和 4 字节 start code，并保留原始 start code；不再
   用固定长度缓冲和随机文件偏移反复扫描。
2. H.264 按 NAL type 查找真实 SPS/PPS，H.265 按 type 查找真实 VPS/SPS/PPS；忽略参数集之前的 SEI，
   不再假设参数集位于固定序号。
3. 参数集只交给 `H264HevcDecoder.init()` 建立 decoder，不再把 CSD 额外调用 `decode()`/`decodeFrames()`
   当作视频 packet 重送。
4. 后续读取将下一幅 VCL 图像之前的非图像 NAL 与该 VCL NAL 合并，再以一个 Annex-B packet 调用
   `decodeFrames()`。因此重复参数集和 SEI 会与其对应的 IDR 一起提交，包长与 `ffprobe` 边界一致。
5. 解码循环使用 `decodeFrames()` 消费一个 packet 的全部即时输出，并在自然 EOF 调用 `drain()`；渲染尺寸
   使用实际 `DecodedVideoFrame` 的宽高，不再写死 1920×800，也不再假设 decoder 初始化阶段一定能返回
   有效尺寸。
6. Activity 初始化增加失败收口：记录完整异常、关闭 decoder 并结束页面，避免无效或损坏样本再次造成
   未捕获的 Activity 启动异常。实际逐 packet 解码继续在 `Dispatchers.IO` 协程执行。
7. 新增 JVM 单元测试，覆盖 3/4 字节 start code、EOF 最后一个 NAL、前导无关字节、H.264/H.265 type
   提取，以及“重复参数集必须与下一幅图像合并”的回归场景；`demo` 同步补齐统一 JVM 测试依赖。

### 6.4 验证结果与边界

已完成：

- `AnnexBNalUnitReaderTest` 全部通过；
- `:demo:ktlintCheck`、`:demo:detekt` 通过；
- `:demo:assembleDevDebug --rerun-tasks` 通过，600 个任务实际执行；
- APK 安装到 P3H 后，H.264 与 H.265 页面日志均持续输出 1920×800 帧；日志中的关键 packet 长度分别可见
  H.264 的 11924 字节和 H.265 的 16549 字节，与样本访问单元边界一致；
- 最终两轮日志均未再出现 `Unable to send video packet`、`Unable to receive video frame` 或
  `FATAL EXCEPTION`。

上述第一轮结论只验证了解码输出和错误日志，没有核对 Surface 实际画面，因此不能表述为两个页面都已完成
可视播放验证。后续实际查看页面时发现 H.264 仍为黑屏，相关原因、修复和重新验证见第 7 节。

本次真机结论只覆盖仓库内两份 Annex-B 样本和组合 wrapper。多 slice access unit、AUD/suffix SEI、损坏流、
带 B 帧输入、独立视频 wrapper、16KB page 设备及长时间内存曲线仍属于独立发布验证项，不能由本次结果外推。

## 7. 真机回归发现：H.264 已解码但 OpenGL 仍为黑屏

### 7.1 现象与根因

packet 边界修复后，H.264 日志持续报告 `outputs=1 1920x800`，Surface 也持续提交 buffer，但 P3H 页面实际
只有黑色背景；同一 APK 的 H.265 画面正常。进一步对比初始化日志和渲染器状态后确认：

- H.264 的 `H264HevcDecoder.init()` 在首帧解码前返回宽高 `0×0`、像素格式 `-1`；这是 FFmpeg 此时尚未
  从图像数据解析出完整视频属性的合法状态；
- H.265 样本初始化时已经返回 `1920×800` 和 `yuv420p`，所以原路径可以正常建立渲染缓冲；
- 旧 H.264 Demo 曾硬编码 `1920×800`，packet 重构删除硬编码后直接把初始化返回的 `0×0` 传给
  `LeoGLSurfaceView.setVideoDimension()`；
- `GLRenderer.setVideoDimension()` 会忽略非正尺寸，因此 Y/U/V 缓冲容量一直为 0；`onDrawFrame()` 又只在
  Y 缓冲容量大于 0 时上传纹理和绘制。结果是 FFmpeg 正常输出帧、`requestRender()` 正常执行，但 OpenGL
  始终没有可绘制的像素缓冲，最终表现为无异常日志的黑屏。

因此，问题不在 H.264 packet、FFmpeg 解码输出或像素格式，而是 Demo 过早信任初始化阶段尚未就绪的尺寸。
第一轮仅凭 `outputs=1` 和无异常日志判断“播放通过”也不充分；解码成功与 Surface 有可见画面必须分别验证。

### 7.2 修复方法

H.264 与 H.265 Demo 均采用相同的动态尺寸策略：

1. 初始化时缓存目标渲染区域；只有 `DecodeVideoInfo` 宽高均为正数时才预配置 OpenGL；
2. 每个实际解码帧渲染前检查 `DecodedVideoFrame.width/height`，首帧或分辨率变化时重新调用
   `setVideoDimension()`，再提交对应像素数据；
3. 缓存最近已配置的帧尺寸，相同尺寸不重复分配 YUV 缓冲；
4. `GLRenderer.setVideoDimension()` 改为同步方法，与 `feedData()`、`onDrawFrame()` 使用同一实例锁，防止
   解码线程调整尺寸时与 GL 线程读写缓冲发生竞争；
5. 尺寸变化条件由“宽和高都变化”修正为“宽或高任一变化”，确保只改变一个维度时也会重建缓冲。

### 7.3 重新验证

使用 JBR 17.0.14 完成以下验证：

- `:demo:compileDevDebugKotlin`、`:demo:testDevDebugUnitTest`；
- `:opengl:ktlintCheck`、`:demo:ktlintCheck`、`:opengl:detekt`、`:demo:detekt`，均使用
  `--rerun-tasks` 并通过；
- `:demo:assembleDevDebug --rerun-tasks` 通过，600 个任务实际执行；
- APK 重新安装到 P3H 后，H.264 页面截图可见视频画面，不再是黑色背景，日志持续输出 1920×800 帧，
  Surface buffer 提交约为 26–29 FPS；
- 随后返回列表打开 H.265 页面，截图同样可见正常视频画面，持续输出 1920×800 帧，未发现回归；
- 两轮日志均未出现 `AndroidRuntime` 崩溃、视频 packet 发送失败或视频帧接收失败。

本次可视验证解决了仓库两份样本在 P3H 上的黑屏回归，但不扩大第 6.4 节所列的其它发布验证边界。

## 8. 交叉参考

- `2026-08-31-native-modules-implementation-review_cc.md`
- `2026-08-27-native-modules-remediation-plan-zh.md`
- Android JNI tips：<https://developer.android.com/ndk/guides/jni-tips>
- Android NDK native-codec 示例：<https://github.com/android/ndk-samples/blob/main/native-codec/app/src/main/cpp/native-codec-jni.cpp>
- Oracle JNI 设计说明：<https://docs.oracle.com/en/java/javase/17/docs/specs/jni/design.html>
