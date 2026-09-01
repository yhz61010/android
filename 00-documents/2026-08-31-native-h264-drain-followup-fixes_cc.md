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

本轮结论只能标记为“源码整改、四 ABI 重建和本机构建验证完成”，不能标记为“真机验证完成”或
“已证明没有内存安全回归”。

## 6. 交叉参考

- `2026-08-31-native-modules-implementation-review_cc.md`
- `2026-08-27-native-modules-remediation-plan-zh.md`
- Android JNI tips：<https://developer.android.com/ndk/guides/jni-tips>
- Android NDK native-codec 示例：<https://github.com/android/ndk-samples/blob/main/native-codec/app/src/main/cpp/native-codec-jni.cpp>
- Oracle JNI 设计说明：<https://docs.oracle.com/en/java/javase/17/docs/specs/jni/design.html>
