# audio 模块弃用关闭 API 彻底移除设计（2026-09-16）

> **实施状态（2026-10-14 更新）：Task 1–4 的源码已实施（未提交），并经四路静态代码审查，
> 审查修正见 §20；Gradle 与真机验证均未执行。**
> 历史提交 `f27449db0` 与 `484d2c3d9` 的 message 描述了计划中的代码成果，
> 但它们的 diff 只修改本设计和对应计划文档；不得把这两个提交当成代码、
> Gradle 或真机验证已完成的证据。未经明确授权不重写已在远端的历史。
>
> **源码实施后的代码审查（2026-10-14）见 §20。此前最近一次设计复审是第十次（2026-09-20，源码
> 基准仍是 `9eec9807f`），结论见 §19。**
> 该轮复核了第九次复审的全部结论（**全部成立**，见 §19.1），并新发现一个 HIGH：
> **`audioPlayer?.stop()` 与 `ownerJob?.cancelAndJoin()` 被写进同一个 `teardownSteps` step**，
> 使两者共用一个 `try`——前者抛出即跳过 join，而下一步照常 `releaseAndJoin()`，worker 与
> release 并发，正是本方案花最大篇幅要堵的那个竞态。这一步取消了 `teardownSteps()` 被引入的
> 唯一理由（"某一步失败，后续步骤仍然执行"）。两个 MEDIUM 分别是门闩在 catch 内 `is` 判型、
> 以及门闩的取消失败在 **leader 侧**同样无人记录（§19.2）。
>
> 第九次复审（§18）发现 `AudioReceiver.receiverServer` 与 `AudioSender.senderClient` 都因
> `field = Ctor(...).also { ...; 阻塞调用() }` 这个写法而**在 teardown 需要它们的时刻仍为 `null`**
> ——前者使 `stopServer()` 的最后一步成为死代码、netty server 从不停止，后者使第八次复审刚
> 定下的"断连提为第一步"在它要修的场景里空转（§18.2 / §18.3）。
>
> 第八次复审（§17）推翻了两处此前被当作已验证的结论：自调用守卫**写错了、永不触发**（H1），
> netty 断连**排得太靠后、会被重连复活**（H2）。**§16.1 那句"全部命中、可以直接信任"不再成立**，
> 反例见 §17.2，标题已订正。
>
> 实施已完成；复核时按顺序读：**§20 → §19 → §18 → §17 → §15.1 的更正框 → §16**。
> 第七次复审记录见 §16。
>
> ---
>
> ⚠️ **源码基准已不再是 `9eec9807f`（2026-09-20 起）。** 本文全部行号仍按 `9eec9807f` 记载，
> 但以下文件此后已被改动，**行号已经漂移，必须以实施时的 checkout 为准**：
>
> - `demo/.../audio/receiver/AudioReceiver.kt` —— 新增 `cleanupScope`、`startServer()` 与
>   `stopServer()` 改 `suspend`、`receiverServer` 赋值提前（整体下移十余行）
> - `demo/.../audio/sender/AudioSender.kt` —— `senderClient` 赋值提到 `connect()` 之前；
>   新增 `cleanupScope`；两处 netty 回调改 `cleanupScope.launch { }`；**`stop()` 已改为
>   `suspend` 并重排关闭顺序**（整体下移约 30 行）
> - `audio/.../aac/AacStreamPlayer.kt` —— 逐帧日志加 `BuildConfig.DEBUG` 门控
>   （`initDecoderLocked` 的 `onFailure` 块、`stopPlaying()` 的删除范围等下移约 12 行）
> - `audio/.../opus/OpusStreamPlayer.kt` —— 同上，三处逐帧日志加门控（`onDecoded` /
>   `decodeOrDropLocked` 的延迟行 / `consumeDecodedPcm`），`initDecoderLocked` 的 `onFailure`
>   块与 `stopPlaying()` 的删除范围下移约 11 行。**OPUS 是 `defaultAudioType`，即实际热路径**
> - `basenetty/.../BaseNettyServer.kt`、`BaseNettyClient.kt` —— 关闭路径去阻塞化，
>   `startServer()` / `stopServer()` 均改为 `suspend`；`stopServer()` 与 `startServer()` 现共用
>   `lifecycleMutex`，`release()` 抽出 `releaseNetty()` 并加资源层超时
>
> 本文**未逐行回填**这些偏移。函数名与调用结构始终是权威锚点，行号只作辅助。
> 改动记录见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md`。
>
> **§18 的 H1 已于 2026-09-20 实施完毕**（连同根因 `BaseNettyServer.startServer()` 一起修），
> **H1 与 H2 均已于 2026-09-20 实施**，`AudioSender.stop()` 与 `AudioReceiver.stopServer()`
> 也已改为 `suspend`。进度表见 §18.3。

## 1. 背景与目标

`audio` 模块此前一轮"改为确定性一次性会话"的重构，给实现类补上了挂起版本的确定性关闭
（`releaseAndJoin()` / `stopPlayingAndJoin()`），并把对应的非挂起版本标注为 `@Deprecated`。
但迁移停在了实现层，接口层没跟上，模块内部也仍有 8 处调用弃用 API，编译时产生 8 条警告：

```
AudioPlayer.kt:168:26          aacStreamPlayer?.stopPlaying()
AudioPlayer.kt:169:27          opusStreamPlayer?.stopPlaying()
AacStreamPlayer.kt:155:63      audioDecoder?.release()
AacStreamPlayer.kt:224:39      it.release()
AacEncoderWrapper.kt:45:53     encoder.release()
OpusEncoderWrapper.kt:45:53    encoder.release()
OpusStreamPlayer.kt:148:63     audioDecoder?.release()
OpusStreamPlayer.kt:218:39     it.release()
```

**目标**：把这些调用全部替换为确定性的挂起版本，并删除弃用 API 本身。
**明确排除的做法**：不使用 `@Suppress("DEPRECATION")`。

### 1.1 根因不是这 8 个调用点

真正的问题在接口层：

```kotlin
// AudioEncoderWrapper.kt —— 现状
interface AudioEncoderWrapper {
    fun encode(input: ByteArray)
    fun release()
    /** Waits until any asynchronous encoder worker has stopped and its resources are released. */
    suspend fun releaseAndJoin() = release()   // ← 退化默认实现
}

// AudioDecoderWrapper.kt —— 现状
interface AudioDecoderWrapper {
    fun decode(input: ByteArray)
    fun release()                              // ← 从来没有挂起变体
}
```

`AudioEncoderWrapper.releaseAndJoin()` 的默认实现直接委托给 `release()`，意味着**对任何未覆写它的
实现，"确定性关闭"这个 KDoc 承诺是假的**。`AudioDecoderWrapper` 则根本没有挂起变体——这正是
`AudioPlayer.kt:167` 的 `decoderWrapper?.release()` 连警告都不报的原因：不是它没问题，而是没有
正确的替代品可用。

因此本次改造的实际范围大于那 8 个警告点，还包含 `MicRecorder` 的非挂起释放路径与 demo 侧
4 个调用方。

## 2. 已确定的两个前提

经讨论确认，不再重新评估：

1. **弃用方法直接删除**，公开面只保留 suspend 版本。对 JitPack 下游是源码 + 二进制双重破坏性
   变更。本仓库已在同一区域做过同类变更（`BaseMediaCodec` 一次性会话、AAC/OPUS 文件播放器挂起
   入口），风格一致。
2. **`stop()` 一路改为 suspend 推到资源所有者**。Activity 或非挂起回调必须使用
   自己管理且带异常处理的 teardown scope；不把 `lifecycleScope.launch(NonCancellable)` 当成
   通用包裹。仅在已受跟踪的协程内将最终释放段放入 `withContext(NonCancellable)`。

## 3. 关键技术约束与方案选择

### 3.1 约束：`synchronized` 块内不能调用挂起函数

`AacStreamPlayer` / `OpusStreamPlayer` 的解码器状态由 `synchronized(lock)` 保护，
`initDecoderLocked()` 就运行在锁内。Kotlin 禁止在 `synchronized {}` 内调用挂起函数，因此
"把回滚路径改成 `releaseAndJoin()`"无法直接完成。

### 3.2 已否决的方案 B：`synchronized` 换 `Mutex`

把两个 stream player 的锁换成 `kotlinx.coroutines.sync.Mutex`，`startPlayingStream()` 与
`AudioPlayer.play()` 变 suspend，回滚直接 `releaseAndJoin()` 并等待。

**否决理由**：这是对音频热路径并发模型的重写。每包音频都要走 `mutex.withLock`，
`decodeOrDropLocked()` 等一批包含丢帧判定与 latency 计算的函数全部要变 suspend。这些路径
**没有单元测试覆盖**，改错的表现是音频卡顿或时序错乱，只能靠真机听出来。收益仅仅是把
"初始化失败回滚"从异步变同步——而回滚本来就不是关闭路径，调用方不需要等待它。风险与收益
不成比例。

### 3.3 采用的方案 A：热路径保持非挂起，回滚交给 scope

`startPlayingStream()` / `AudioPlayer.play()` 维持非挂起，`synchronized(lock)` 原样保留。
`initDecoderLocked()` 的失败回滚改为在 player 自己的 `ioScope` 上发起——`launch` 本身不是挂起
函数，在 `synchronized` 块内调用合法。

**达成的目标**：所有*关闭*路径（`releaseAndJoin` / `stopPlayingAndJoin`）都是确定性、可等待的；
唯一异步化的是*初始化失败回滚*，该解码器此时已从状态中摘除（`audioDecoder = null`），无人再
引用，回滚只需最终完成。

### 3.4 方案 A 的一个边界：回滚任务必须 `NonCancellable`

`StreamPlayerStopper.prepareStop()` 内部会执行 `ioScope.cancel()`：

```kotlin
private fun prepareStop(): Decoder? {
    LogContext.log.w(tag, "Stop playing audio")
    val decoder = detachDecoder()
    ioScope.cancel()          // ← 会取消 ioScope 上所有未完成的子协程
    ...
}
```

若回滚用普通 `ioScope.launch { }`，一个并发到来的 `stopPlayingAndJoin()` 会把尚未执行的回滚
任务一起取消，导致那个半初始化的解码器**永不释放**。且此时 `detachDecoder()` 返回 null
（回滚路径已把 `audioDecoder` 置空），停止流程也不会替它兜底。

因此回滚必须写成：

```kotlin
ioScope.launch(NonCancellable) { failedDecoder.releaseAndJoin() }
```

`NonCancellable` 作为 `launch` 的上下文元素会替换父 Job，使该协程不再是 `ioScope` 的子协程，
从而不被 `ioScope.cancel()` 取消；调度器仍沿用 `ioScope` 的 `Dispatchers.IO`。

## 4. 详细修改方案

### 4.1 接口层

**`audio/base/iters/AudioEncoderWrapper.kt`**

```kotlin
interface AudioEncoderWrapper {
    /**
     * @param input The byte order of [input] is little endian.
     */
    fun encode(input: ByteArray)

    /** Waits until any asynchronous encoder worker has stopped and its resources are released. */
    suspend fun releaseAndJoin()
}
```

- 删除 `fun release()`
- 删除 `suspend fun releaseAndJoin() = release()` 的**默认实现**，改为抽象声明。这是本次改造最
  重要的一处：默认实现使"确定性关闭"对未覆写者成为假承诺。

**`audio/base/iters/AudioDecoderWrapper.kt`**

```kotlin
interface AudioDecoderWrapper {
    fun decode(input: ByteArray)

    /** Waits until any asynchronous decoder worker has stopped and its resources are released. */
    suspend fun releaseAndJoin()
}
```

- 删除 `fun release()`，新增抽象的 `suspend fun releaseAndJoin()`（同样不给默认实现）

### 4.2 四个 wrapper 实现

**`AacEncoderWrapper.kt` / `OpusEncoderWrapper.kt`**（两者结构相同）

删除整个 `override fun release()`：

```kotlin
// 删除
override fun release() {
    runCatchingPreservingCancellation { encoder.release() }
        .onFailure { LogContext.log.e(TAG, "AAC encoder release failed", it) }
}

// 保留（已存在，无需改动）
override suspend fun releaseAndJoin() {
    encoder.releaseAndJoin()
}
```

**删除后这两个文件会连带清空 `companion object`**（第七次复审核实）。在
`AacEncoderWrapper.kt` 与 `OpusEncoderWrapper.kt` 中，`runCatchingPreservingCancellation`
（`:3`）、`LogContext`（`:10`）和 `TAG`（`:21`）的**唯一使用点**就是被删的 `release()`
（`:44-47`）。因此必须一并删除：

- 两条失效 import（`runCatchingPreservingCancellation`、`LogContext`）；
- `private const val TAG`；
- **以及包着 TAG 的整个 `companion object`**——只删 TAG 会留下 `companion object { }`，
  而 detekt 的 `EmptyClassBlock` 是开启的（`10-configs/detekt.yml:203`），`maxIssues = 0`
  下会直接失败。

**`CompressedPcmEncoderWrapper.kt` / `CompressedPcmDecoderWrapper.kt`**

这两个是纯内存 codec（`compress()` / `decompress()`），没有 worker 线程，`release()` 是空实现：

```kotlin
// 改为
override suspend fun releaseAndJoin() {
    // Nothing to release: compression runs synchronously on the caller's thread.
}
```

保留空体，但补注释说明为什么是空的——否则后续维护者会怀疑是漏写。

### 4.3 `BaseMediaCodec`

删除整个弃用的 `open fun release()`（当前 `:226-246`，含上方 KDoc）：

```kotlin
// 删除
/**
 * Release resource.
 */
@Deprecated(
    "Non-suspend release cannot guarantee the worker has exited. " +
        "Use releaseAndJoin() for deterministic shutdown.",
    ReplaceWith("releaseAndJoin()")
)
open fun release() {
    if (!markReleasing()) return
    // Preserve the legacy synchronous return semantics. New code should use releaseAndJoin()
    // when it must also wait for the worker to finish.
    codecJob?.cancel()
    codecJob = null
    ioScope.cancel()
    try {
        releaseCodecOnce()
    } finally {
        lifecycleState.set(LifecycleState.RELEASED)
    }
}
```

`markReleasing()`、`releaseCodecOnce()`、`codecJob`、`lifecycleState` 均仍被 `releaseAndJoin()`
使用，**不要一起删**。删除后确认 `release()` 是否被子类 `override`（它是 `open`），若有则一并
处理。

### 4.4 两个 stream player

**删除弃用入口**（当前 `AacStreamPlayer.kt:213-225` /
`OpusStreamPlayer.kt:207-219`，含其上方的 KDoc）：

```kotlin
// 删除
@Deprecated(
    "Non-suspend stop cannot guarantee the decoder has been released. " +
        "Use stopPlayingAndJoin() for deterministic shutdown.",
    ReplaceWith("stopPlayingAndJoin()")
)
fun stopPlaying() {
    stopped.set(true)
    streamPlayerStopper.stop { it.release() }
}
```

保留 `suspend fun stopPlayingAndJoin()`（已存在）。

**改造初始化失败回滚**（当前 `AacStreamPlayer.kt:154-159` /
`OpusStreamPlayer.kt:147-154`）：

```kotlin
// 现状
}.onFailure {
    runCatchingPreservingCancellation { audioDecoder?.release() }
    audioDecoder = null
    csd0 = null
    LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
}

// 改为
}.onFailure {
    // Detach first: the rollback below runs asynchronously, so nothing may still reach this
    // half-built decoder through the player's state.
    val failedDecoder = audioDecoder
    audioDecoder = null
    csd0 = null
    // NonCancellable, not a plain ioScope child: StreamPlayerStopper.prepareStop() cancels
    // ioScope, and a concurrent stop would otherwise kill this rollback and leak the decoder
    // that it has already detached and can no longer release itself.
    failedDecoder?.let { decoder ->
        ioScope.launch(NonCancellable) {
            runCatchingPreservingCancellation { decoder.releaseAndJoin() }
                .onFailure { e -> LogContext.log.e(TAG, "rollback release failed", e) }
        }
    }
    LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
}
```

OPUS 版本还需一并清空 `csd1` / `csd2`（与现状一致）。

注意顺序变化：**先摘除再释放**。原代码是先释放再置空，异步化后必须先摘除，否则热路径可能在
释放进行中仍拿到该解码器。

新增 import：`kotlinx.coroutines.NonCancellable`、`kotlinx.coroutines.launch`（后者可能已存在）。

**`StreamPlayerStopper.stop()`**：删除 `stopPlaying()` 后，`fun stop(releaseDecoder: (Decoder) -> Unit)`
应当不再有调用方。确认后一并删除，只保留 `suspend fun stopAndJoin(...)`。届时 `prepareStop()` 与
`releaseAudioTrack()` 仅被 `stopAndJoin()` 使用，可视情况内联，但**本次不做**（YAGNI，且它们的
注释记录了 AudioTrack 释放顺序的坑，保持独立更易读）。

### 4.5 `AudioPlayer`

```kotlin
// 现状
fun release() {
    LogContext.log.w(TAG, "release()")
    audioTrackPlayer.release()

    decoderWrapper?.release()
    aacStreamPlayer?.stopPlaying()
    opusStreamPlayer?.stopPlaying()
}

// 改为
suspend fun releaseAndJoin() {
    LogContext.log.w(TAG, "releaseAndJoin()")
    // Stream players own their own AudioTrack teardown through StreamPlayerStopper, so release
    // the decoder wrapper and the players first and let this player's own track go last.
    //
    // finally, not a plain last statement: StreamPlayerStopper rethrows CancellationException
    // and stopAndJoin() can fail its own require(), and this player's track exists in every
    // session, so an exception above must not be allowed to skip it.
    try {
        decoderWrapper?.releaseAndJoin()
        aacStreamPlayer?.stopPlayingAndJoin()
        opusStreamPlayer?.stopPlayingAndJoin()
    } finally {
        audioTrackPlayer.release()
    }
}
```

**顺序无需真机裁决**（复审修正，原文曾把它列为本次唯一的真机裁决点）。两条理由：

1. **三个 AudioTrack 互不相干。** `AudioPlayer` 在 `:43` 新建自己的 `AudioTrackPlayer`，
   `AacStreamPlayer` 与 `OpusStreamPlayer` 各自又新建一个。`AudioTrackPlayer` 是普通类，持有
   `private val audioTrack: AudioTrack`，因此这是三个独立实例。`stopPlayingAndJoin()` 内部的
   `StreamPlayerStopper.releaseAudioTrack()` 操作的是 stream player 自己那个 track，
   **不存在对同一个 AudioTrack 的重复释放**。
2. **两条路径由构造互斥。** `AudioPlayer.init`（`:46-84`）对 `AudioType.AAC` / `AudioType.OPUS`
   只建 stream player；只有 `else`（`PCM` / `COMPRESSED_PCM`）分支才 `audioTrackPlayer.play()`
   并通过 `AudioDecoderManager` 建 `decoderWrapper`。`play()`（`:86`）也照此分流。因此
   `decoderWrapper` 与两个 stream player **永不同时非空**，`AudioPlayer` 自己的 track 在
   AAC/OPUS 下从不 play、从不写入。

结论：`decoderWrapper?.releaseAndJoin()` 与 `audioTrackPlayer.release()` 的相对顺序，和
`stopPlayingAndJoin()` 之间没有任何交互可言，上面给出的写法直接采用即可，无需 a/b 两案对比。

**但"顺序自由"不等于"异常路径安全"**（第六次复审）：`audioTrackPlayer` 在 `:43` 无条件创建，
而 `StreamPlayerStopper.stopAndJoin()` 会重抛 `CancellationException`（`:44-45`）、其
`require()`（`:36-38`）也会抛。若不套 `finally`，任一前置释放抛出就会漏掉这个每个会话都存在的
AudioTrack，与本方法 KDoc 承诺矛盾。

关于 `AudioReceiver.kt:202-210` 那段 SIGABRT 注释（`releaseBuffer() track ... disabled due to
previous underrun`）：它描述的是 `AudioPlayer` **自己那个** track 在历史 PCM 配置下的问题。当前
`AudioReceiver.defaultAudioType`（`:32`）是 `AudioType.OPUS`，该 track 从不启动，也就不可能
underrun。注释保留作为历史记录即可，措辞上应注明它约束的是 PCM 路径。

**但这里发生了一次顺序反转，必须对 PCM 路径单独论证**（第八次复审新增）。现状 `release()`
（`:163-170`）是**最先**释放 `audioTrackPlayer`，新方案把它放进 `finally`、**最后**释放；而那段历史注释
写的恰恰是 "you must release AudioTrack first, otherwise, you will crash"。上面"三个 track 互不相干"
只说明*同一次调用内部*没有争用，并没有回答"把它挪到最后对真正在播的那个 track 安全吗"。
AAC/OPUS 下该 track 从不启动，问题只可能出在 PCM/COMPRESSED_PCM 上，而这两者恰恰只出现在
`AudioActivity.stopPcmPlayback()`（`AudioType.PCM`，`AudioActivity.kt:144`）。对它成立，理由有两条：

1. **PCM 下 `try` 块全是 no-op。** `AudioDecoderManager.getWrapper()` 对 `AudioType.PCM` 返回
   `null`（`AudioDecoderManager.kt:18`），两个 stream player 也为 null，所以 `finally` 之前没有任何
   语句执行，`audioTrackPlayer.release()` 事实上仍是第一个动作。
   **实际上这个结论比"PCM 例外"更强**（第九次复审）：`getWrapper()` 的四个分支里只有
   `COMPRESSED_PCM` 返回非空实例（`AudioDecoderManager.kt:17-22` —— `PCM -> null`、
   `AAC -> null`、`OPUS -> null`、`COMPRESSED_PCM -> CompressedPcmDecoderWrapper`），
   而 `CompressedPcmDecoderWrapper` 的释放是空体。也就是说 **`decoderWrapper?.releaseAndJoin()`
   在任何 `AudioType` 下都不会触碰任何 AudioTrack**，本节"顺序自由"的论证不依赖分支互斥这一条
   就已经成立。上面第 2 点的互斥论证仍然保留，因为它还负责说明 stream player 那一侧。
   `COMPRESSED_PCM` 下 `decoderWrapper` 是 `CompressedPcmDecoderWrapper`，其释放是空体（纯内存
   codec，见 §4.2），同样不触碰 track。
2. **release 时已经没有写者。** §4.7.5 把 `stopPcmPlayback()` 的顺序改成
   `stop()` → `join()` → `releaseAndJoin()`，写线程在 release 之前已经 join 掉；历史注释描述的
   "写入中被 release" 场景不再成立。

不要把这两条当成"顺序无所谓"的重复论证——它们回答的是另一个问题。实施时请把这段理由抄进
`releaseAndJoin()` 的 KDoc，否则下一个维护者会照 `AudioReceiver.kt:202-210` 的注释把顺序改回去。

### 4.6 `MicRecorder`

以下定位基于当前 checkout，实施时以函数名和调用结构为权威锚点，不仅依赖行号。

删除非挂起释放路径：

- `fun stopRecord()`（`:411`）→ 删除。调用方只有 `AudioSender:152` 与 `AudioReceiver:212`，
  两者本就要按 4.7 改为挂起
- `private fun finishRecorderRelease(stopSucceeded: Boolean)`（`:437`）→ 删除

保留 `suspend fun stopRecordAndJoin()`（`:380`）与
`private suspend fun finishRecorderReleaseAndJoin()`（`:452`）。

**`rollbackFailedInit()` 的处理（`:209-223`）**

该函数在构造器重抛初始化异常前回滚部分资源，当前 `:217` 调用将被删除的
`AudioEncoderWrapper.release()`。它不能直接改调 `finishRecorderReleaseAndJoin()`：
`rollbackFailedInit()` 在 `:212` 已将 `released` 置为 `true`，后者会进入
`releaseCompleted.await()`，而此时还没有任何人会完成该 deferred，形成自锁。

保留 effect 和 `AudioRecord` 的同步回滚；对 encoder 先捕获局部引用并置空字段，
再启动不受随后 `ioScope.cancel()` 影响的异步释放。`releaseCompleted` 必须到
encoder 挂起释放结束后才在 `finally` 中完成：

```kotlin
private fun rollbackFailedInit(record: AudioRecord?, cause: Throwable) {
    LogContext.log.e(TAG, "MicRecorder init failed; rolling back", cause)
    stopped.set(true)
    released.set(true)
    releaseAdvancedFeatures(true)
    runCatchingPreservingCancellation { record?.release() }.onFailure {
        LogContext.log.e(TAG, "rollback: AudioRecord release error", it)
    }
    val failedEncoder = encodeWrapper
    encodeWrapper = null
    ioScope.launch(NonCancellable) {
        try {
            runCatchingPreservingCancellation { failedEncoder?.releaseAndJoin() }.onFailure {
                LogContext.log.e(TAG, "rollback: encoder release error", it)
            }
        } finally {
            releaseCompleted.complete(Unit)
        }
    }
    ioScope.cancel()
}
```

构造器仍会立即重抛原异常，无法向一个尚未构造成功的对象提供可等待的关闭
入口；因此这里和 stream player 初始化回滚一样，是“确定性关闭”目标之外的受控
异步回滚。协程只使用捕获的局部 `failedEncoder` 与已初始化的
`releaseCompleted`，不得访问可能尚未赋值的 `audioRecord`。该协程会有意保持这个
未完成构造的对象，直到 encoder 回滚和 deferred 完成，之后即可回收。

**`failRecordStart()` 的处理（`:289-295`）**

`:294` 对 `finishRecorderRelease()` 的调用位于 `private fun failRecordStart(message: String)`，
而后者由**非挂起的公开入口 `fun startRecord()`（`:225`）**在四处调用（`:228`、`:235`、
`:239`、`:243`）。把它改成挂起会把 `startRecord()` 一并拖成 suspend，扩大破坏面到启动路径。

按 3.3 已确立的原则处理——**关闭路径必须确定性，启动失败回滚可以异步**。`failRecordStart()` 是
启动失败回滚，与 `initDecoderLocked()` 的回滚同类，因此适用同一写法：

```kotlin
// 现状
private fun failRecordStart(message: String) {
    LogContext.log.e(TAG, message)
    stopped.set(true)
    ioScope.cancel()
    stopAudioRecord()
    finishRecorderRelease(stopSucceeded = false)
}

// 改为
private fun failRecordStart(message: String) {
    LogContext.log.e(TAG, message)
    stopped.set(true)
    stopAudioRecord()
    // NonCancellable so the rollback survives the ioScope.cancel() below, and so a caller that
    // never entered a coroutine still gets its AudioRecord and encoder released.
    //
    // The body must not throw: NonCancellable replaces the parent job, so there is no parent
    // left to absorb an exception, and this scope carries no CoroutineExceptionHandler. An
    // escaping throwable would reach the platform default handler and kill the process.
    ioScope.launch(NonCancellable) {
        runCatchingPreservingCancellation {
            finishRecorderReleaseAndJoin(stopSucceeded = false)
        }.onFailure { LogContext.log.e(TAG, "start rollback release failed", it) }
    }
    ioScope.cancel()
}
```

`ioScope.cancel()` 从 `stopAudioRecord()` **之前**挪到了整个函数**之后**（第二次复审修正：
本文档早期版本称"相对顺序保持不变"，与实际改动不符）。这个换序是安全的：`failRecordStart()`
的四个调用点（`:228`、`:235`、`:239`、`:243`）全部位于 `recordJob = ioScope.launch { }`
（`:246`）之前，此刻 `ioScope` 还没有任何子协程，先取消还是后取消没有可观察差别。之所以要挪，
是为了让上面那个 `launch` 确实排进调度——虽然 `NonCancellable` 已经保证它不会被取消，但把
`cancel()` 放在最后读起来意图更清楚。`startRecord()` 维持非挂起，公开面不变。

**但"签名不变"不等于"行为不变"**（第七次复审新增）。现状 `finishRecorderRelease()` 是同步跑完的，
其 `completeRecorderRelease()` → `callback.onStop(false)` 在 `startRecord()` 返回**之前**、在调用者
自己的线程上完成；也就是说"`startRecord()` 返回 ⇒ 要么在录音，要么已经报过停止"这个不变量成立。
异步化之后 `onStop` 改由 `Dispatchers.IO` 协程稍后送达，调用点看不出任何变化，却多了一个两者都
不成立的窗口。这是一处**可观测的行为变更**，必须写进 §5 与 `CHANGELOG.md`，并明确新契约：
`RecordCallback.onStop` 可能在 `startRecord()` 返回之后、从任意 IO 线程到达。

`rollbackFailedInit()` 里 `releaseCompleted` 的异步完成不在此列：构造器已经重抛，调用方拿不到
对象，也就无人 `await()` 它。

**为什么必须包住异常**（复审补充）：`MicRecorder.ioScope`（`:110`）是
`CoroutineScope(Dispatchers.IO)`，**没有 `CoroutineExceptionHandler`**。而 3.4 的 `NonCancellable`
恰恰把父 Job 换掉了，于是逃逸异常既没有父协程可以传播，也没有 handler 可以兜住，直接走平台默认
处理器 → 进程崩溃。`finishRecorderReleaseAndJoin` 在编码器分支会重抛 `CancellationException`，
`completeRecorderRelease` 还会回调客户端代码，都可能抛。4.4 的 stream player 回滚片段本来就包了
`runCatchingPreservingCancellation`，此处必须同样处理。

同一条约束适用于 4.4：`AacStreamPlayer.ioScope`（`:40`）与 `OpusStreamPlayer.ioScope`（`:46`）
都是 `CoroutineScope(Dispatchers.IO + Job())`，同样没有 handler。

**一个容易误判的细节**（第二次复审补充）：`runCatchingPreservingCancellation`
（`RunCatchingExt.kt:8-9`）会**重抛** `CancellationException`，而
`finishRecorderReleaseAndJoin` 的编码器分支（`:462-467`）恰好会重抛它——看上去像是上面这层包裹
漏了一条逃逸路径。实际不会崩：`launch(NonCancellable)` 的 `parentHandle === NonDisposableHandle`，
`JobSupport.cancelParent()` 对 `CancellationException` 返回 `true`，不会走到
`handleJobException()`，异常被静默丢弃。**只有非取消异常才会打到平台默认处理器**，而那些正是
`.onFailure { }` 捕获的。结论不变，写在这里是为了避免后续维护者误以为此处有洞而"补"出问题。

删除 `finishRecorderRelease()` 时要同步修正两处 KDoc：

- `released` 字段上方（`:122`）的 `Guards [finishRecorderRelease] ...`，改为引用
  `finishRecorderReleaseAndJoin`。
- `releaseAdvancedFeatures()` 上方（`:358-360`）删除
  `[finishRecorderRelease] /` 和“`both of which`”，仅保留
  `[finishRecorderReleaseAndJoin]` 与 `[rollbackFailedInit]` 两条入口的说明。

### 4.7 demo 调用方

#### 4.7.1 共享的一次性关闭门闩

`AudioSender.stop()` / `AudioReceiver.stopServer()` 各自同时可由 Activity 和 netty 回调发起。
挂起关闭增加了交错点，不能继续依赖 `AudioTrack.state` 的 check-then-act 或
`runCatching` 当作并发释放保护；后者也无法阻止 native 层的重复 release。

在 demo 音频包下新增 `SuspendTeardownGate`，两个类各持有一个实例：

```kotlin
/**
 * One-shot teardown coordination for one owner instance.
 *
 * This gate is deliberately not resettable. AudioSender and AudioReceiver are single-session
 * owners: every new start creates a new owner and therefore a new gate. Reusing either owner for
 * another start/stop cycle would make later stop calls observe the first result without running
 * cleanup and is outside this contract.
 *
 * Followers await the same completion and receive the same Throwable instance. There is no gate
 * timeout: timing out here would let a caller proceed while cleanup was still mutating resources.
 * Every operation inside the block must therefore be bounded itself; a stuck operation stalls all
 * followers and must be diagnosed at that resource boundary.
 */
internal class SuspendTeardownGate {
    private val started = AtomicBoolean(false)
    private val completed = CompletableDeferred<Throwable?>()

    suspend fun run(block: suspend () -> Unit) {
        if (!started.compareAndSet(false, true)) {
            completed.await()?.let { throw it }
            return
        }
        var failure: Throwable? = null
        try {
            withContext(NonCancellable) { block() }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            completed.complete(failure)
        }
    }
}
```

该类的契约是：首个调用者在 `NonCancellable` 中执行完整关闭；后续调用者不再进入
关闭体，只等待同一个结果；首个调用失败时，所有等待者都抛出同一个
`Throwable` 实例。它是**每个 owner 实例仅用一次**的门闩，不支持重置；当前成立的前提是
`AudioActivity` 每次启动都创建新的 `AudioSender` / `AudioReceiver`。如果未来要重用 owner，
必须先重设计生命周期，不得仅清空该门闩。

**"每次点击新建 owner"不足以支撑这个契约**（第八次复审新增）。该前提只排除了"同一 owner 跑两轮
start/stop"，没有排除另外两种会让门闩在**资源尚未全部建成**时就被消费掉的情况——两者都会留下
无人释放的资源，而门闩按设计拒绝第二次关闭：

1. **teardown 跑赢 start。** `AudioActivity.onAudioSenderClick`（`:403`）与 `onStop()` 的
   `launchCleanup`（`:372`）跑在同一个 Activity `ioScope` 上、彼此并发。`AudioSender.start()` 先
   `ioScope.launch { connect() }`（`:85-92`），**之后**才在 `:94` 赋值 `micRecorder` 并
   `startRecord()`。点完发送立刻退出 Activity，`stop()` 完全可能在 `:94` 之前整段跑完：
   `micRecorder?.stopRecordAndJoin()` 看到 null，麦克风随后才启动且永不关闭。
2. **netty 重连复活会话。** 见 §4.7.3 的关闭顺序说明——`onFailed` 消费门闩后，
   `BaseNettyClient.doRetry()` 仍可能重连成功并回调 `onConnected()`（`AudioSender.kt:49-61`），
   新建一个 `AudioPlayer`（新的 AudioTrack）。

这不是门闩本身的缺陷（重入式关闭同样处理不了"关完之后又建出新资源"），而是**所有权边界**的问题：
门闩只能保证"关闭动作只跑一次"，保证不了"关闭之后不再有人建资源"。因此 `AudioSender` 必须补一个
所有者级别的终止标志，由 §4.7.3 定案；`AudioReceiver` 无重试机制，但同样需要覆盖第 1 种情况。
旧实现有同样的竞态，区别只在于旧 `stop()` 可重入、第二次调用还能补救，而门闩把它变成不可恢复。

门闩不设超时是有意选择：超时返回会让调用方在关闭仍修改资源时继续执行。代价是某个
底层操作卡住时，netty 与 Activity 路径的所有等待者都会卡住。因此关闭体内每个外部操作
必须自身有界；如需超时，应加在具体资源操作层并保留后续释放，不加在门闩等待层。
必须用单元测试覆盖“并发调用只执行一次”、“后续调用等待完成”和“失败向等待者传播”。

**`CancellationException` 不得作为共享失败广播给等待者**（第七次复审新增）。上面的 `run()` 捕获的是
`Throwable`，而关闭体里**确实有会重抛 `CancellationException` 的路径**：
`StreamPlayerStopper.stopAndJoin()`（`:44-45`）与 `MicRecorder.finishRecorderReleaseAndJoin()`
（`:464-466`）都是故意重抛的。一旦发生，每个 follower 都会在**自己不相干的协程里**收到一个
`CancellationException`：`AudioActivity.launchCleanup`（`:391-392`）对该分支直接 rethrow 而不记日志，
`cleanupScope.launch { }` 里的取消异常也不会进 `CoroutineExceptionHandler`——失败会彻底消失。

因此 `completed` 只记录**非取消**异常；取消只抛给 leader 自己。

> **第十次复审更正（2026-09-20，M1 + M2）：本节此前给出的分支写法有两个问题。**
>
> 曾经写的是：
>
> ```kotlin
> // 旧写法，不要使用
> } catch (error: Throwable) {
>     if (error !is CancellationException) failure = error
>     throw error
> } finally {
>     completed.complete(failure)
> }
> ```
>
> 1. **catch 块内用 `is` 判定异常类型，与仓库既有写法相反。** `StreamPlayerStopper.kt:44-45`
>    与 `AudioActivity.kt:391-393` 对同一问题用的都是**两个 catch 子句**。detekt 的
>    `InstanceOfCheckForException`（`10-configs/detekt.yml:242`，`active: true`，只 exclude
>    测试目录）正是针对这个形状。当前 `./gradlew detekt` 没有配置 `classpath`（根
>    `build.gradle.kts` 的 `detekt { }` 只设了 `config` / `parallel` / `source`），因此跑在
>    无 type resolution 模式，该规则的子类型判定拿不到 `BindingContext.TYPE`、**今天不会报**
>    ——但这是运气，不是设计；仓库里对同一问题已有一致的既有写法，没有理由偏离。
> 2. **leader 侧的取消同样会被静默吞掉。** 本节论证"不广播给 follower"的依据是 follower 侧
>    读不出来；可 leader 侧是一样的：`launchCleanup` 对 `CancellationException` 直接 rethrow
>    且**不记日志**（`AudioActivity.kt:391-392`），`cleanupScope.launch { }` 的
>    `CoroutineExceptionHandler` 也不接收取消。而关闭体跑在 `withContext(NonCancellable)` 内，
>    这里收到的 `CancellationException` **不是"调用方被取消"，而是一次真实的关闭失败**
>    （`StreamPlayerStopper.kt:44-45` 与 `MicRecorder.kt:464-466` 都会主动重抛它）。
>    于是整条链上没有任何一处会留下痕迹。

改用两个 catch 子句，并让 leader 侧的取消至少留下一条日志：

```kotlin
        } catch (cancellation: CancellationException) {
            // Not published to followers: throwing it into an unrelated follower coroutine reads
            // there as "this caller was cancelled" and is swallowed without a log. But it is not
            // a cancelled caller here - the body runs under NonCancellable, so this is a real
            // teardown failure (StreamPlayerStopper.stopAndJoin() and
            // MicRecorder.finishRecorderReleaseAndJoin() both rethrow it on purpose). Log it or
            // it leaves no trace anywhere.
            LogContext.log.e(TAG, "Teardown body was cancelled", cancellation)
            throw cancellation
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            completed.complete(failure)
        }
```

`LogContext` 与 `TAG` 因此成为 `SuspendTeardownGate` 的新依赖：给它自己的 `companion object` 加
`private const val TAG = "TeardownGate"`，并 import `com.leovp.log.LogContext`。`demo` 模块本就
依赖 `projects.log`（`demo/build.gradle.kts:363`），符合仓库的日志约定。

单元测试相应增加第 4 条：**关闭体重抛 `CancellationException` 时，等待者正常返回而不是收到该异常**。
该用例会执行到上面这条 `LogContext.log.e`，实施时确认 JVM 单测环境下 `LogContext` 的行为
（参考同目录既有的 `PcmFramePaddingTest`）；若它在未初始化时抛出，正确的做法是在测试里初始化
日志实现，**不是**为了让测试转绿而删掉这条日志。

#### 4.7.2 netty 回调的清理 scope

四个 netty 回调都是非挂起接口，因此 `AudioSender` / `AudioReceiver` 仍各需一个
`SupervisorJob + Dispatchers.IO + CoroutineExceptionHandler` 的 `cleanupScope`，回调中使用
`cleanupScope.launch { stop() }` / `cleanupScope.launch { stopServer() }`。`cleanupScope` 不参与被清理的
`ioScope` Job，异常由 handler 记录。一次性门闩保证 netty 回调与 Activity 同时发起时
不会重复释放。

> **✅ 两侧均已于 2026-09-20 落地**：`AudioReceiver` 见
> `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.4，
> `AudioSender` 见同文档 §4.8（与 `stop()` 改 `suspend` 同批，因为后者使两处回调无法再直接
> 调用）。两个 `cleanupScope` 逐字同构。**门闩仍未实施**，所以本节末句"一次性门闩保证不会
> 重复释放"目前**尚未成立**——当前只靠 `stop()` / `stopServer()` 入口处清空字段挡住**顺序**
> 重入，并发重入仍可能发生。
>
> **同时更正一处定级**：本节此前把 `cleanupScope` 写成"因为 netty 回调是非挂起接口、改不了
> 签名"这一条纯粹的类型适配理由。它还修掉了一个**真实的 Netty 异常**：
> `onClientDisconnected()` 由 `BaseServerChannelInboundHandler.channelInactive()` 调用，
> **跑在 netty worker 事件循环线程上**；改前它同步调 `stopServer()` →
> `workerGroup.shutdownGracefully().syncUninterruptibly()`，即在 workerGroup 自己的事件循环
> 线程上等该 group 终止，`DefaultPromise.checkDeadLock()` 会因 `executor().inEventLoop()`
> 直接抛 `BlockingOperationException`。
>
> 此前从未爆出来，唯一原因是 §4.7.4 H1 那个 `receiverServer` 恒 null 让最后一步从未执行。
> **因此这两项必须同批落地**：单独修好 `receiverServer` 而不改回调的调用方式，会立刻把这个
> 异常暴露出来。`AudioSender` 侧同理——`onDisconnected()` / `onFailed()` 也来自 netty 线程
> （`BaseNettyClient.handleConnectFailure()` `:359` 同步回调），且 `handleConnectFailure()`
> 本身是从 `connect()` 内部调出来的、而 `connect()` 跑在 `AudioSender.ioScope` 上——若在那里
> 直接跑改成 suspend 的 `stop()`，它的 `cancelAndJoin()` 会等待自己所在的协程，**必死锁**。
> 这正是 §4.8 同批加 `cleanupScope` 的原因，不是可以稍后补的点缀。

#### 4.7.3 `AudioSender`：可取消队列与可等待关闭

`sendRecAudioThread()` 和 `startPlayThread()` 都阻塞在 `ArrayBlockingQueue.take()` 上。
本文档前几轮复审据此判定：`ioScope.cancel()` 无法中断这种 Java 阻塞，两个 worker 会永久
占用 `Dispatchers.IO` 线程并持有 `AudioSender`，并把 Channel 改造列为必须的缺陷修复。

**该前提已失效**（第五次复审，2026-09-18）：提交 `5672ecbbd` 已把两处 `queue.take()` 包进
`runInterruptible { }`，取消时会中断阻塞线程，worker 能够正常退出。同一提交还把
`AudioReceiver` 的两个 worker 从 `poll()` + `delay(10.milliseconds)` 改成了同样的阻塞取数
写法，并在 KDoc 中写明为何废弃轮询。**线程泄漏在当前源码中已经不存在。**

Channel 改造仍然建议实施，但理由已变：它给出明确的 `close()` 语义（关闭后迟到回调的
`trySend()` 直接失败，不在消费者退出后滞留帧），并免去对线程中断的依赖。实施者
**不得**再把它当成缺陷修复——提交信息不要写成修泄漏，真机验证也不要按"worker 泄漏"
设计（§6.3 对应项已改为回归确认）。

两个 `ArrayBlockingQueue<ByteArray>` 分别改为容量相同的 `Channel<ByteArray>`；普通回调用
`trySend()` 保留“队列满时丢当前帧”的既有非阻塞语义，消费协程用 `for (data in channel)`
或 `receive()`。原 `queue.size` 调试日志改为记录 `trySend` 成功/失败，不引入额外计数器。
录音发送 worker 必须保留现有的逐帧 `runCatching { sendAudioToServer(data) }`
与失败记录：Channel 只替代阻塞取数，不应把一次瞬时网络异常放大成 worker
永久退出。`receive` / `for` 迭代本身放在该 `runCatching` 之外，保留 scope 取消能力。
关闭顺序改为（**第 1 步的位置由第八次复审改动，原为第 6 步**）：

1. `senderClient?.disconnectManually()`，**先关掉会造新会话的那一侧**。
2. `micRecorder?.stopRecordAndJoin()`，停止新的录音帧。
3. 关闭两个 Channel，使网络迟到回调的 `trySend()` 失败，不在消费者退出后滞留帧。
4. `audioPlayer?.stop()`，与 `StreamPlayerStopper.prepareStop()` 的既有顺序保持一致：
   join 之前先把 track 停掉，只停不放，真正的释放仍由第 6 步完成。
   **这是防御性写法，不是本类当前存在的死锁修复**——理由见 §15.1 的第七次复审更正：
   `AudioSender` 的 `AudioPlayer` 恒为 OPUS 类型，其自身 track 从不 `play()`，
   `AudioTrackPlayer.stop()` 会在 `playState == PLAYSTATE_STOPPED` 守卫处直接返回，
   代价为零；一旦将来 `defaultAudioType` 改成 PCM，这一步立刻变成必需。
5. `ioScope.coroutineContext[Job]?.cancelAndJoin()`，等两个 Channel worker 退出。
6. `audioPlayer?.releaseAndJoin()`，此时已无人再调用 `play()`。
7. `senderClient?.release()`。

> **第十次复审更正（2026-09-20，H1）：第 4 步与第 5 步必须是 `teardownSteps()` 的两个独立
> step，不能写进同一个 lambda。**
>
> 前一版的代码模板把它们合并成了一个：
>
> ```kotlin
> // 错误示范，不要使用
> {
>     audioPlayer?.stop()
>     ownerJob?.cancelAndJoin()
> },
> ```
>
> `teardownSteps()` 存在的**全部理由**就是"某一步失败，后续步骤仍然执行"（见本节末尾）。
> 把两个动作塞进同一个 lambda，等于让它们共用一个 `try`：`audioPlayer?.stop()` 一旦抛出，
> `ownerJob?.cancelAndJoin()` 就被跳过，而下一步 `audioPlayer?.releaseAndJoin()` 照常执行
> ——worker 仍在跑 `play()` 的同时 release 掉 player，**正是 §4.7.4"三个关键点"第 1 条
> 和历史 SIGABRT 注释所描述的那个场景**。合并写法把一个"尽力而为"的编排悄悄退化成了
> "一损俱损"。
>
> 今天不会触发：`AudioPlayer.stop()`（`AudioPlayer.kt:158`）只委托
> `AudioTrackPlayer.stop()`（`:270-283`），后者全程被 `runCatchingPreservingCancellation`
> 包住并以 `.onFailure { }` 记日志（`:276-282`），普通异常出不来。但本节自己写明这一步是
> **为 `defaultAudioType` 改成 PCM 那天准备的**——那时它会真正做事（`pause()` + `flush()` +
> `stop()`，见 §4.7.5 的 M3 更正框），抛出的概率随之上升，而那恰恰也是 worker 真的会卡在
> `AudioTrack.write()` 上、第 5 步最不能被跳过的场景。
>
> 正确写法是两个 step：
>
> ```kotlin
>                 { audioPlayer?.stop() },
>                 { ownerJob?.cancelAndJoin() },
> ```
>
> §4.7.4 的 `AudioReceiver` 关闭序列有完全相同的一处，同样拆开。

整个顺序放在 `teardownGate.run { ... }` 内。这同时修复旧实现中
`ioScope.launch { client release }` 紧跟 `ioScope.cancel()` 导致清理尚未开始就被取消的竞态。

**为什么 `disconnectManually()` 必须排在第一位**（第八次复审新增，H2）。前一版把
`disconnectManually()` + `release()` 一起放在最后，理由只考虑了音频资源的依赖关系，忽略了
`senderClient` 在 teardown 期间**仍是一个会生产新会话的活动组件**：

- `BaseNettyClient.handleConnectFailure()`（`:352-362`）**同步**回调
  `connectionListener.onFailed(...)`（`:359`），也就是 `AudioSender` 的
  `cleanupScope.launch { stop() }` —— 门闩在这一刻被消费；
- 紧接着 `:361` 调 `doRetry()`（`:751-789`），它在 **`retryScope`（`BaseNettyClient` 自己的 scope，
  不是 `AudioSender.ioScope`）** 上 `delay(ConstantRetry(10, 2000) 的 2000ms)` 后重新 `connect()`；
- 只要第 2 步 `micRecorder?.stopRecordAndJoin()`（join 录音 Job + 释放 MediaCodec 编码器）
  超过约 2 秒，重连就可能先成功 → `onConnected()`（`AudioSender.kt:49-61`）新建一个
  `AudioPlayer`（新的 AudioTrack）并在已取消的 `ioScope` 上起 worker。**这个 player 无人释放**，
  而门闩已消费，`AudioActivity.onStop()` 的第二次 `stop()` 只会 `await` 一个已完成的结果。

`disconnectManually()` 会置 `disconnectManually = true`，`doRetry()` 在 `:756` 的
`if (released || disconnectManually) return` 处直接返回，重试链就此终止。把它提到第一步的代价是
零：它不触碰任何音频资源，且本来就要执行。`release()` 仍留在最后（它会 shutdown
EventLoopGroup，不能在 worker 还在用 channel 时执行）。

> **第九次复审补充（H2）：这条排序在它要修的那个场景里会落空——`senderClient` 那时还是 `null`。**
>
> **✅ 下面的"修法"已于 2026-09-20 实施，实施者请勿重复改。**
> 记录见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.7 与 §4.8。
> 同批还把 `AudioSender.stop()` 改成了 `suspend`，并按本节的顺序重排：
> `disconnectManually()` → `stopRecordAndJoin()` → `player.stop()` → `cancelAndJoin()` →
> `player.release()` → `client.release()`；`cleanupScope` 与两处 netty 回调改写（§4.7.2）
> 也一并落地。**当时仍未实施、已于 2026-10-14 随 Task 2/3 落地（见 §20）**：`teardownGate`、
> `teardownSteps()`、`stopping` 标志、`containsJob()` 守卫、队列改 `Channel`、`releaseAndJoin()`。
> 下面保留原分析作为依据。
>
> `AudioSender.start()`（`:85-92`）与 §4.7.4 的 `AudioReceiver` 是同一个形状：
>
> ```kotlin
> senderClient = AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000))
>     .also {
>         senderHandler = AudioSenderWebSocketHandler(it)
>         it.initHandler(senderHandler)
>         it.connect()      // suspend；内部 f.sync() 最长 30 s
>     }
> ```
>
> 赋值发生在 `also { }` **返回之后**。而 H2 描述的场景正是"`connect()` 内部同步回调 `onFailed`"
> ——此刻 `connect()` 尚未返回，`senderClient` **仍是 `null`**：
>
> - 第 1 步 `senderClient?.disconnectManually()` 空转，于是 `doRetry()` 既不会在 `:756` 早退，
>   也不会被 `disconnectManually()` 内 `:627` 的 `stopRetryHandler()` 取消 —— 重试链照常推进。
> - 第 7 步 `senderClient?.release()` 同样空转，该 client 的两个 EventLoopGroup 永不 shutdown。
> - 更糟：第 5 步 `ownerJob?.cancelAndJoin()` 若在 `connect()` 挂起期间取消了 `start()` 协程，
>   赋值**永远不会发生**。
>
> `stopping` 标志仍然有效——它挡住了 `onConnected()` 新建 `AudioPlayer`，H2 点名的那个泄漏被堵住
> 了；但"终止重试链 + 释放 netty client"这两件事必须靠 `senderClient` 非空才能做到。
>
> **修法与 §4.7.4 相同：先赋值，再做阻塞调用。**
>
> ```kotlin
>     fun start(ctx: Context, uri: URI) {
>         this.ctx = ctx
>         ioScope.launch {
>             // Assign before connect(), not after: connect() can park for up to
>             // CONNECTION_TIMEOUT_IN_MILLS and calls onFailed() from inside itself, so an
>             // `also { }` that assigns on the way out leaves senderClient null for exactly the
>             // window in which teardown needs it to stop retries and release the client.
>             // Wrapped, not one line: at 12 spaces of indent the single-line form is 101
>             // characters and ktlint's limit is 100 (第十次复审 L1).
>             val client =
>                 AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000))
>             senderClient = client
>             senderHandler = AudioSenderWebSocketHandler(client)
>             client.initHandler(senderHandler)
>             client.connect()
>         }
>         // ... MicRecorder 构造不变，另见 stopping 标志一节
>     }
> ```
>
> 改完之后第 1 步才真正生效，H2 的排序论证才完整。

**仅靠排序还不够：`AudioSender` 需要一个所有者级终止标志。**
`disconnectManually()` 自身是挂起函数，且第 1 步开始执行时 `start()` 的赋值可能还没发生
（§4.7.1 的第 1 种情况）。因此在 `AudioSender` 中增加：

```kotlin
    /**
     * Set before teardown starts, checked by everything that builds a new resource.
     *
     * The one-shot gate guarantees teardown runs once; it cannot guarantee nobody builds new
     * resources afterwards. Two things still can: start() assigns micRecorder after launching
     * connect(), and BaseNettyClient.doRetry() can reconnect and call onConnected() while
     * teardown is in flight. Either would leave an AudioRecord or an AudioTrack that the
     * already-consumed gate refuses to release.
     */
    private val stopping = AtomicBoolean(false)
```

- `stop()` 在 `teardownGate.run { }` **之前**执行 `stopping.set(true)`（放在门闩外，因为
  follower 不进入关闭体，但同样要让这个标志尽早可见）。
- `onConnected()` 开头 `if (stopping.get()) return`，不再新建 `audioPlayer`、不再起 worker。
- `start()` 在赋值 `micRecorder` 之前检查：若 `stopping.get()` 为真，则不构造 `MicRecorder`
  （构造即会开麦，构造后再补一次 `stopRecordAndJoin()` 等于把关闭体再跑一遍，与门闩冲突）。

> **2026-10-14 订正（§20.1）**：构造前的检查只缩窄窗口、关不掉它。补偿释放与门闩并不冲突——
> 前提是先 `teardownGate.awaitCompletion()`，再只释放刚建成的那一个对象，而不是重跑关闭体。
> 四处构造点现在都在赋值之后再读一次 `stopping`，被 teardown 读取的字段加了 `@Volatile`。

`AudioReceiver` 没有重试机制，但 `startServer()` 同样可能与 `stopServer()` 交错，需要同一个标志；
见 §4.7.4。

**串联方式用平铺的 step 列表，不用嵌套 `try/finally`**（第七次复审修正）。前一版方案把六步写成
六层嵌套 `try/finally`，有两个问题：裸 `try/finally` **不记录 suppressed**，若第 1 步抛出、第 6 步
也抛出，根因会被静默丢弃，门闩广播给所有等待者的反而是最后那个不相干的异常；而且 6 层嵌套违反
本仓库 `.claude/rules/common/coding-style.md` 的 "No deep nesting (>4 levels)"（detekt
`NestedBlockDepth` 阈值为 8，工具会放行，但仓库规则不放行）。改用一个小工具函数，既保住"某步失败
后续仍尝试"，又保住根因：

```kotlin
/**
 * Runs every teardown step even if an earlier one fails, then rethrows the first failure with
 * the later ones attached as suppressed.
 *
 * Nested try/finally would do the first half only: a throwing finally discards the throwable
 * the try had already raised, so the root cause would never reach the gate's followers.
 */
private suspend fun teardownSteps(vararg steps: suspend () -> Unit) {
    var failure: Throwable? = null
    for (step in steps) {
        try {
            step()
        } catch (e: Throwable) {
            failure?.addSuppressed(e) ?: run { failure = e }
        }
    }
    failure?.let { throw it }
}
```

**关闭入口需要自调用守卫**（第七次复审新增，**写法由第八次复审更正**）。`stop()` 对**自己的**
`ioScope` 执行 `cancelAndJoin()`，因此不能从该 scope 内部调用，否则静默死锁。`audio` 模块对同一
问题已有现成做法——`StreamPlayerStopper.stopAndJoin()`（`:36-38`）用 `require()` 显式拒绝。

> **第八次复审更正（H1）：本节此前给出的那行 `require` 是错的，且与它声称照抄的源码不是一回事。**
>
> 曾经写的是：
>
> ```kotlin
> // 错误示范，不要使用
> require(currentCoroutineContext()[Job] !== ioScope.coroutineContext[Job])
> ```
>
> `ioScope.launch { }` 产生的是 `ioScope` 根 Job 的**子 Job**，`currentCoroutineContext()[Job]`
> 拿到的永远是那个子 Job，**永远 `!==` 根 Job**。也就是说这条 `require` 在任何现实调用路径上
> 都不会触发，是一行死代码。而真正会死锁的场景恰恰是"从 `ioScope` 的某个子协程调用 `stop()`"：
> 门闩的 `withContext(NonCancellable)` 里执行 `cancelAndJoin()`，`cancel()` 连调用者自己一起取消，
> 但 `NonCancellable` 使它不响应取消，于是它在 `join()` 里永远等自己完成。
>
> 源码用的是**递归后代检查**，不是身份比较：
>
> ```kotlin
> // StreamPlayerStopper.kt:34-38
> val ownerJob = ioScope.coroutineContext[Job]
> val callerJob = currentCoroutineContext()[Job]
> require(ownerJob == null || callerJob == null || !ownerJob.containsJob(callerJob)) {
>     "stopAndJoin() must be called outside the player-owned coroutine scope"
> }
>
> // StreamPlayerStopper.kt:55-56
> private fun Job.containsJob(target: Job): Boolean =
>     children.any { child -> child === target || child.containsJob(target) }
> ```
>
> 这不是理论差异：`StreamPlayerStopperTest.kt:121-139` 的
> `suspending stop rejects calls from its owned scope before side effects` 正是用
> `scope.launch(start = CoroutineStart.UNDISPATCHED)` 的**子协程**验证这个守卫的；换成 `!==`
> 版本，该测试必然失败。

因此两个 demo 关闭入口应照搬后代检查版本：

```kotlin
    // Descendant check, not identity: ioScope.launch() children never equal the scope's own Job,
    // so `caller !== ioScope's Job` would pass for exactly the callers that deadlock here.
    // Same shape as StreamPlayerStopper.stopAndJoin(), which StreamPlayerStopperTest covers.
    private fun Job.containsJob(target: Job): Boolean =
        children.any { child -> child === target || child.containsJob(target) }

    suspend fun stop() {
        val ownerJob = ioScope.coroutineContext[Job]
        val callerJob = currentCoroutineContext()[Job]
        require(ownerJob == null || callerJob == null || !ownerJob.containsJob(callerJob)) {
            "stop() must be called from outside AudioSender's own scope"
        }
        // ...
    }
```

`containsJob` 与 `teardownSteps()` 一样会被两个类共用，应一并放在 `demo/.../audio/` 下与
`SuspendTeardownGate` 同包的 internal 顶层工具里，不要各留一份私有副本。

**如果确实只想要弱守卫**，那就照 `MicRecorder.stopRecordAndJoin()`（`:386`）的做法——它用的是对
**具体子 Job** 的身份比较（`require(job !== currentCoroutineContext()[Job])`），并在 KDoc
（`:383-384`）明写"只拒绝*直接*自调用"。但那种写法要求手里有那个子 Job 的引用；`stop()` 要 join
的是整个 scope，没有单一子 Job 可比，所以这里只有后代检查一条路。无论选哪种，**都不要再写
"照抄 `StreamPlayerStopper`"却给出身份比较**。

**`cancelAndJoin()` 还会等待连接协程**（第七次复审新增）。§15.1 当时只枚举了两个 worker，漏了
`start()` 在同一个 `ioScope` 上起的连接协程（`AudioSender.kt:85-92`）。
`BaseNettyClient.connect()`（`:342`）内部执行 `f.sync()`（`:421`），其自身注释写明
"blocks the current thread for up to CONNECTION_TIMEOUT_IN_MILLS"，该常量 = `30_000`（`:99`）。
旧实现 `ioScope.cancel()` 后立即返回，新实现则会**等这次连接**，而门闩按设计不设超时，
所有 follower 一起等。缓解因素确实存在：`cont.invokeOnCancellation { cancelConnectFuture() }`
（`:399-402`）会在取消时取消 `ChannelFuture`，使阻塞的 `sync()` 尽快抛出，因此实际等待应远小于
30 秒。但这是必须实测的假设，不是可以默认成立的结论——§6.3 已为此新增"连接进行中退出"一项。

#### 4.7.4 `AudioReceiver`：同一关闭协议

`AudioReceiver.stopServer()` 保持原名，只改为 suspend，并放入自己的
`teardownGate.run { ... }`。关闭顺序为：先 `micRecorder.stopRecordAndJoin()`，再 `audioPlayer.stop()`，
然后对两个 worker 执行 `cancelAndJoin()`，再 `audioPlayer.releaseAndJoin()`，最后停止 server。
**`audioPlayer.stop()` 与 `cancelAndJoin()` 是 `teardownSteps()` 的两个独立 step**，理由见
§4.7.3 的第十次复审更正框（合并写法会让前者的失败吞掉后者，从而把 release 放到 worker 还活着
的时候）。

**这里没有与 §4.7.3 第 3 步（关闭两个 Channel）对等的一步，是有意的，不是漏写**（第十次复审
补记）。`AudioReceiver` 的两个队列本次不改 Channel（理由见本节末尾的命名冲突说明），而
`ArrayBlockingQueue` 没有 `close()`。这不构成缺口：两个写入点
（`onReceivedData()` 的 `receiveAudioQueue.offer()`、`MicRecorder.onRecording()` 的
`recAudioQueue.offer()`）用的都是**非阻塞的 `offer()`**，队列有界，消费者退出后迟到的帧只会被
丢弃，既不会阻塞 netty 线程或录音回调，也不会滞留到对象回收之后。Channel 那一步换来的是更明确的
`close()` 语义，不是这里缺少的某种安全性。

**`stop()` 这一步保留，但它在本类里是防御性写法**（第六次复审提出，第七次复审更正定级）。
第六次复审的论证是：worker 若已进入 `AudioPlayer.play()`，PCM 路径会停在
`AudioTrackPlayer.write()`（`:194`）的阻塞 `AudioTrack.write(short[], int, int)` 上，协程取消
中断不了这个 native 调用，`cancelAndJoin()` 会无限期等待；而唯一能让它返回的
`AudioTrack.stop()` 藏在排在 join 之后的 `releaseAndJoin()` 里，形成死等。
**这条论证对 `AudioReceiver` / `AudioSender` 当前的配置不成立**：两者的 `AudioPlayer` 都用
`defaultAudioType`（`AudioReceiver.kt:32` = `AudioType.OPUS`），`AudioPlayer.play()` 只会走
`:99-108` 的 stream player 分支，`:88-98` 的 PCM 分支恒不命中，该 track 从不 `write()`。
这与 §4.5 的既有结论（"该 track 从不启动，也就不可能 underrun"）是同一个事实，详见 §15.1 的更正。

保留这一步的理由改为：与 `StreamPlayerStopper.prepareStop()`（`:58-70`）的既有顺序一致，
代价为零（`AudioPlayer.stop()`（`:158`）只委托 `audioTrackPlayer.stop()`，不触碰 stream player；
该 track 从未 `play()` 过时 `stop()` 在 `playState == PLAYSTATE_STOPPED` 守卫处直接返回），
并且一旦 `defaultAudioType` 改成 PCM 就立刻变成必需。

这与 `audio` 模块自身做法一致：`StreamPlayerStopper.prepareStop()`（`:58-70`）就在
`stopAndJoin()` 的 `cancelAndJoin()` 之前调用 `audioTrackPlayer.stop()`，注释写明
"Stop first to wake any blocking write"。

**本次不改 `AudioReceiver` 的两个 `ArrayBlockingQueue`**，与 §4.7.3 的 `AudioSender` 有意不对称：
该文件已 import `io.netty.channel.Channel`，再引入 `kotlinx.coroutines.channels.Channel` 会同名
冲突，而 `runInterruptible` 已使其可取消，改造收益不足以抵消这处命名代价。
同样使用 §4.7.3 的 `teardownSteps()` 平铺串联（不用嵌套 `try/finally`），保证后续资源总会被尝试
且根因不被丢弃；同样需要 §4.7.3 的自调用守卫——**注意用后代检查版本，不是身份比较**，
理由见 §4.7.3 的第八次复审更正框。这保证 PCM / COMPRESSED_PCM 路径不会在
`AudioTrack.write()` 尚未退出时 release track。

**同样需要 `stopping` 标志**（第八次复审新增）。`AudioReceiver` 没有 netty 重试，
不存在 §4.7.3 那条"重连复活"路径，但 §4.7.1 的第 1 种情况一样成立：
`AudioActivity.onAudioReceiverClick`（`:408`）与 `onStop()` 的 `launchCleanup`（`:371`）并发跑在同一个
Activity `ioScope` 上，`stopServer()` 可能先于 `startServer()` 完成。更关键的是
`AudioReceiver` 的 `micRecorder` 与两个 worker 都是在 **netty 回调
`onClientConnected()`（`:70-78`）** 里建立的，而 server 在第 4 步才停——一个在 teardown 期间接入的
客户端会新建 `MicRecorder` 并起 worker，全部逃过已消费的门闩。因此：

- `stopServer()` 在 `teardownGate.run { }` 之前 `stopping.set(true)`；
- `onClientConnected()` 开头 `if (stopping.get()) return`；
- `startServer()` 在构造 `AudioPlayer` / 启动 server 之前检查该标志。
- （2026-10-14）以上三处之外，`startServer()` 与 `onClientConnected()` 在赋值之后再读一次该标志，
  为真则等门闩完成后补偿释放，见 §20.1。

> **netty 那一层的同名竞态已于 2026-09-20 单独关闭，但不能替代这个标志。**
> `BaseNettyServer` 的 `startMutex` 已更名 `lifecycleMutex` 并由 `stopServer()` 一并持有，
> 所以"停止在 bind 完成前插进来 → 早退返回 false → 服务器占着端口没人停"这条已经堵上
> （见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.5 与 §10 的 R2）。
>
> 但那把锁只保护 `BaseNettyServer` 自己的 channel 与两个 EventLoopGroup。
> `AudioReceiver` 自己的 `audioPlayer`（`startServer()` 里构造）、`micRecorder` 与两个 worker
> （`onClientConnected()` 里构造）都在它管辖之外，仍然可能在门闩消费之后才建出来。
> **`stopping` 标志照旧必须实施**（已实施，连同 §20.1 的赋值后 re-check）。

`AudioReceiver` **不**把 `receiverServer?.stopServer()` 提前（与 §4.7.3 的
`disconnectManually()` 不同）：`BaseNettyServer.stopServer()` 会 shutdown 两个 EventLoopGroup，
提前执行会在 worker 仍可能经 `receiverHandler.sendAudioToClient()` 使用 channel 时抽掉它。
新客户端接入这条路径由上面的 `stopping` 标志挡住，不需要靠排序。

**但在此之前有一个更基础的问题：`receiverServer` 在服务器正常运行时恒为 `null`，
上面那个"最后停止 server"是死代码**（第九次复审新增，H1）。

> **✅ 本项已于 2026-09-20 实施，不在本计划的待办范围内。**
> 实施记录见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.5。
> 落地方式与下面给出的修法一致，且**根因也一并修掉了**：
> `BaseNettyServer.startServer()` 已改为 `suspend` 且不再阻塞到服务器关闭，
> `AudioReceiver.startServer()` 随之改为 `suspend` 并把 `receiverServer` 的赋值提到
> `startServer()` 之前。同批还把 `BaseNettyServer.stopServer()` 改成了 `suspend`，
> 因此 `AudioReceiver.stopServer()` 与本节所需的 `cleanupScope` 也已提前落地
> （见 §4.7.2 与该文档 §4.4）。
> 下面保留原分析，作为"为什么必须这么改"的依据。

```kotlin
// AudioReceiver.kt:191-195 —— 现状
receiverServer = AudioReceiverWebSocket(10020, connectionListener).also {
    receiverHandler = AudioReceiverWebSocketHandler(it)
    it.initHandler(receiverHandler)
    it.startServer()          // 阻塞到服务器关闭为止
}
```

`also` 把返回值赋给 `receiverServer` 的动作发生在**块执行完之后**，而块里的
`it.startServer()` 根本不会返回：

- `AudioReceiverWebSocket`（整文件 21 行）只覆写 `getTagName()` 与 `addLastToPipeline()`，
  **没有**覆写 `startServer()`；`BaseNettyServer` 全文没有任何 thread / coroutine。
- `BaseNettyServer.startServer()`（`:129`）在 `:136` bind 成功后，于 `:142` 执行
  `serverChannel.closeFuture().sync()`，源码注释自己写着
  "the process will get stuck there and wait for client connection"。

⇒ **服务器成功启动 ⇒ `receiverServer` 永远是 `null`**；只有 bind 失败、`startServer()` 提前返回
时它才会被赋值。换句话说，`stopServer()` 里的 `receiverServer?.stopServer()` **只在"服务器压根
没起来"的情况下才有效**，正常会话里它是一条空语句。

连带后果：

1. 本节的关闭步骤表最后一步不可达，§6.3 与计划 Task 5 Step 7 中"确认 server 已停止"无法达成。
2. `AudioActivity.kt:408` 的 `ioScope.launch { audioReceiver?.startServer(...) }` 会永久阻塞在
   `sync()` 上。`onDestroy()` 用的是 `ioScopeJob.complete()`（不是 `cancel()`，`:383`），
   该协程连同它持有的 Activity 引用一起留存。
3. `stopping` 标志仍然需要，但它挡不住这个洞。

**修法（赋值提前，属本次范围）**：

```kotlin
    fun startServer(ctx: Context) {
        this.ctx = ctx
        if (stopping.get()) return
        audioPlayer = AudioPlayer(/* unchanged */)
        // Assign before the blocking call, not after: BaseNettyServer.startServer() parks in
        // serverChannel.closeFuture().sync() until the server stops, so an `also { }` that
        // assigns on the way out never assigns at all while the server is up - and stopServer()
        // would then have nothing to stop.
        val server = AudioReceiverWebSocket(10020, connectionListener)
        receiverServer = server
        receiverHandler = AudioReceiverWebSocketHandler(server)
        server.initHandler(receiverHandler)
        server.startServer()
    }
```

这不改变任何公开签名，也不算扩大范围：本节的关闭协议以 `receiverServer?.stopServer()` 收尾，
不修它这条协议就不成立。

#### 4.7.5 `AudioActivity`

`AudioActivity` 继续使用已有的 `launchCleanup` + `ioScopeJob.complete()`，不新增
`lifecycleScope.launch(NonCancellable)`。PCM 播放的 `player.release()` 换为
`player.releaseAndJoin()`；`audioReceiver.stopServer()` 和 `audioSender.stop()` 调用结构不变。

**`stopPcmPlayback()` 还要补一次 "stop 在 join 之前"**（第七次复审新增）。§15.1 描述的"阻塞写入
挡住 join"这个模式，在 demo 里真正存在的位置就是这里，而不是 `AudioSender` / `AudioReceiver`：

- 这个 player 确实是 `AudioType.PCM`（`AudioActivity.kt:144`），它的裸线程 `playPcmThread`
  （`:148-176`）在循环里调 `audioPlayer?.play(pcmData)`，PCM 路径直通
  `AudioTrackPlayer.write()`（`:194`）的阻塞 `AudioTrack.write(short[], int, int)`。
- 现有 teardown 是 `playbackThread?.interrupt()`（`:351`）→ `playbackThread?.join()`（`:353`）
  → `player?.release()`（`:354`）。`Thread.interrupt()` **唤不醒** native 的 `AudioTrack.write()`；
  能唤醒它的 `AudioTrack.stop()` 又排在 `release()` 里、在 join 之后——与 §15.1 的形状完全一致。
  实际阻塞时长通常由缓冲区排空决定（有界），但 track 若在写入期间被 `pause()`，写入会一直挂住。

因此把 `launchCleanup("Stop PCM playback")` 的块改为先停后 join：

```kotlin
        launchCleanup("Stop PCM playback") {
            // Stop before joining: interrupt() cannot wake a blocking native AudioTrack.write(),
            // and the AudioTrack.stop() that can is inside releaseAndJoin(), after this join.
            // Same ordering as StreamPlayerStopper.prepareStop() and AacFilePlayer.
            player?.stop()
            playbackThread?.join()
            player?.releaseAndJoin()
        }
```

这与仓库既有做法一致：`AacFilePlayer.kt:231-234` 的注释写的就是
"stop() wakes a potentially blocking write before releaseAndJoin() waits for the codec worker"。

**但 `AudioPlayer.stop()` 不是一个干净的"唤醒阻塞写入"原语**（第八次复审新增，M3）。它委托
`AudioTrackPlayer.stop()`（`:270-283`），而后者的第一步是 `pause()`（`:274`）——
`AudioTrackPlayer.pause()`（`:246-257`）执行的是 `audioTrack.pause()` **加 `audioTrack.flush()`**，
之后才轮到 `audioTrack.stop()`（`:280`）。也就是说这条路径是"先加深阻塞、并对一个可能正在进行的
写入做 flush，再由 stop() 解除阻塞"，而不是直接唤醒。本节上文自己也写了"track 若在写入期间被
`pause()`，写入会一直挂住"。

窗口只有两行日志加一个状态判断，实际上 `audioTrack.stop()` 会紧接着解除阻塞，因此**结论不变，
仍然采用这个顺序**（这也是 `StreamPlayerStopper.prepareStop()` 与 `AacFilePlayer` 的既有做法，
改掉会引入不一致）。但它意味着 §6.3 那条"PCM 写入中触发关闭"不能只看 join 是否返回：还要确认
logcat 中没有因 flush/write 并发产生的 `AudioTrack.write failed` 或 `AudioTrack.write error=`。
如果真机上观察到这类日志，正确的修法是给 `AudioTrackPlayer` 增加一个只做 `audioTrack.stop()`
的解阻塞入口，而不是调整本节的调用顺序。

#### 4.7.6 `ADPCMActivity`：播放 Job 拥有 player

不再让裸 `thread` 和 `onDestroy()` 通过共享可变 `player` 竞争释放。删除
`private var player: AudioPlayer?`（`:38`），改为 `private var playbackJob: Job?`：

> **要删的是 4 处，不是 3 处**（第十次复审 L2）。此前的清单只列了字段本身与两处 release
> （`:115`、`:121`），漏了 **`:88` 的 `player = AudioPlayer(this, decoderInfo, AudioType.PCM)`**
> ——它位于 `onPlayADPCMClick()` 里、`thread { }` 块**之外**，必须一并删除，由下面 Job 体内的
> `currentPlayer` 取代。编译器会以未解析引用挡住漏删，所以这不是会流到运行期的缺陷；但真正的
> 风险是实施者顺手把它改成 `val player = ...` 留在 Job **外面**——那会重新引入"Job 在首次调度前
> 被取消 ⇒ `finally` 不执行 ⇒ 已构造的 player 泄漏"，也就是下面第 3 条明令禁止的写法。

- 点击播放时先取消上一个 Job，再用 `lifecycleScope.launch(Dispatchers.IO)` 启动新 Job。
- **新 Job 在构造 player 之前先 `join` 掉上一个 Job**（第七次复审新增）。`cancel()` 不等待，
  旧 Job 的 `finally { withContext(NonCancellable) { releaseAndJoin() } }` 会与新 player 的构造
  重叠；连续点击时会同时存活多个 `AudioTrack`。捕获上一个 Job 的引用，在新 Job 体内先
  `previous?.join()`（不能在主线程 `runBlocking`），再构造 player。虽然它们是互不相干的独立
  `AudioTrack` 实例、不会 native 重复释放，但没有理由让它们堆积。
- `AudioPlayer` 在该 Job 内构造，避免 Job 尚未启动就被取消时泄漏已构造的 player。
- 解码循环每帧前调用 `ensureActive()`，且只访问该 Job 捕获的局部 player。
- Job 的 `finally` 使用 `withContext(NonCancellable) { player.releaseAndJoin() }`，保证先退出
  播放循环，再由同一所有者释放。
- `onDestroy()` 只取消 `playbackJob`；不直接释放 player，也不启动脱离生命周期的根协程。

**`kotlin.concurrent.thread` 的 import 必须保留**（第八次复审新增，M4）。本次只替换*播放*路径的
裸线程；`onEncodeToADPCMClick()`（`ADPCMActivity.kt:40-73`）里 `:46` 的 `thread { }` 属于编码路径，
不在本次范围，该 import（`:23`）仍有使用点。把它当成"随播放路径一起删掉的失效 import"会直接编译失败。

`AudioCipherActivity.kt:95` 的 `player` 是 `android.media.MediaPlayer`，不在本次范围内。

## 5. 破坏性变更清单

对 JitPack 下游是源码 + 二进制双重破坏性变更，需完整写入 `CHANGELOG.md` 的"变更 (Changed)"节。

| 被删除的 API | 替代品 |
|--------------|--------|
| `AudioEncoderWrapper.release()` | `suspend releaseAndJoin()` |
| `AudioDecoderWrapper.release()` | `suspend releaseAndJoin()`（新增） |
| `AudioEncoderWrapper.releaseAndJoin()` 的默认实现 | 实现类必须自行提供 |
| `BaseMediaCodec.release()` | `suspend releaseAndJoin()` |
| `AacStreamPlayer.stopPlaying()` | `suspend stopPlayingAndJoin()` |
| `OpusStreamPlayer.stopPlaying()` | `suspend stopPlayingAndJoin()` |
| `AudioPlayer.release()` | `suspend releaseAndJoin()` |
| `MicRecorder.stopRecord()` | `suspend stopRecordAndJoin()` |
| `StreamPlayerStopper.stop()` | `suspend stopAndJoin()`（`internal`，不影响下游） |

给下游消费者的迁移指引：**所有 `X.release()` / `X.stopXxx()` → `X.releaseAndJoin()` /
`X.stopXxxAndJoin()`，调用方必须在自己管理的协程中等待关闭完成。** 生命周期回调不应
盲目使用 `lifecycleScope.launch(NonCancellable)`：它会替换 lifecycle Job，使任务脱离生命周期父子
关系，且未处理的普通异常会到达平台默认处理器。调用方应复用已有的 teardown scope，
或建立带 `SupervisorJob` 与 `CoroutineExceptionHandler` 的专用 scope；仅在已受跟踪的协程内将
必要的最终释放段放入 `withContext(NonCancellable)`。

这是**给下游的通用建议，不是仓库内的统一改法**（复审修正）。仓库内已有清理机制的文件应沿用
自己的机制：`AudioActivity` 用它现成的 `launchCleanup` + `ioScopeJob.complete()`，只换方法名；
生命周期入口（如 `ADPCMActivity`）让受跟踪的 lifecycle Job 拥有资源，并在自身
`finally` 里完成最终释放；而被**非挂起接口回调**调用的位置
（`AudioSender` / `AudioReceiver` 的 netty 监听器）需要一个专用的 teardown scope。
详见 §4.7。

**另有一处不改签名、但下游可观测的行为变更**（第七次复审新增）：`MicRecorder.startRecord()`
仍是非挂起、签名不变，但它的失败回滚（`failRecordStart()`）改为异步执行，因此
`RecordCallback.onStop(false)` **不再保证在 `startRecord()` 返回之前送达**，而是稍后从
`Dispatchers.IO` 线程到达。依赖"`startRecord()` 返回即已收到 onStop"的下游需要调整。
理由与代码见 §4.6。

demo 内部另有两个函数签名变化，不属公开面、不影响 JitPack 下游，但实施时会连带改动：
`AudioSender.stop()` 与 `AudioReceiver.stopServer()` 均变为 `suspend`。

## 6. 测试策略

### 6.1 现实评估

这批路径**几乎没有单元测试覆盖**——`audio/src/test` 下现有用例集中在 codec 本身。本次改造大部分
正确性依赖真机验证，不应以"编译通过 + detekt 通过"当作验证完成。

### 6.2 单元测试：新增关闭门闩测试，并迁移现有用例

原文档列出的两条在核实后价值接近于零，不再要求：

1. ~~两个 `CompressedPcm*Wrapper.releaseAndJoin()` 幂等~~——它们的 `release()` 现在是空实现
   （`CompressedPcmEncoderWrapper:20`、`CompressedPcmDecoderWrapper:20`），改造后 `releaseAndJoin()`
   仍是空体，测试等于断言空方法不抛异常。
2. ~~接口层"编译期契约测试"~~——删除 `release()` 后编译通过即已保证，额外写 fake 实现不增加任何
   信息。

`AacStreamPlayer` / `OpusStreamPlayer` / `MicRecorder` / `AudioPlayer` 依赖 `MediaCodec`、
`AudioTrack` 与 `AudioRecord`，按本仓库既有做法不强求用 JVM 测试假冒驱动行为。但本次新增的
`SuspendTeardownGate` 是纯 Kotlin 并发协议，必须在 `demo/src/test` 新增单元测试，覆盖：

1. 两个并发调用只执行一次 block，第二个在首个完成前不返回。
2. 首个调用的普通异常传播给所有等待者。
3. 取消首个调用者不会中断已开始的 `NonCancellable` 关闭体。
4. **关闭体重抛 `CancellationException` 时，等待者正常返回而不是收到该异常**
   （第七次复审新增，对应 §4.7.1 的取消处理）。

同批提到 `demo/.../audio/` 的还有两个 internal 顶层工具，都是纯 Kotlin、必须一并覆盖
（第八次复审新增）：

5. `teardownSteps()`：某一步抛出后**后续步骤仍然执行**；最终抛出的是**第一个**失败，
   后续失败以 `suppressed` 附加（这正是它取代嵌套 `try/finally` 的理由，见 §4.7.3）。
6. `Job.containsJob()`：对**直接子 Job** 与**孙 Job** 都返回 `true`，对无关 Job 返回 `false`。
   这条是 §4.7.3 H1 更正的回归锁——身份比较版本会让前两个断言失败。
   `AudioSender` / `AudioReceiver` 本身需要 Android `Context`，其 `require` 守卫无法在 JVM 单测里
   直接构造验证；把判定逻辑提成可测的顶层函数，正是为了让这一条能被覆盖。守卫在真实入口上的
   行为仍由 §6.3 的真机清单兜底。

**但"不用新写"不等于"现有测试不受影响"**（第二次复审补充）。本文档早期版本只说"现有用例集中在
codec 本身"，没有核对它们是否调用了即将被删的 API。实际调用量很大，不处理会让
`:audio:testDebugUnitTest` **编译失败**：

| 文件 | 依赖被删 API 的位置 |
|------|--------------------|
| `BaseMediaCodecAsynchronousTest.kt` | 18 处 `subject.release()`（`:35 :62 :91 :108 :124 :153 :182 :200 :234 :255 :284 :303 :304 :387 :405 :419 :448 :500`），其中 `:500` 在私有辅助函数 `assertReleaseWaitsForCallback()`（`:483`）内，被 **2** 个用例共用（第八次复审更正，此前误记为 3） |
| `BaseMediaCodecSynchronousTest.kt` | `:38` `subject.release()` |
| `StreamPlayerStopperTest.kt` | `:34` `subject.stop { }` |

处理原则：

1. **纯作收尾用的 `subject.release()`** → 非挂起用例包 `runBlocking { subject.releaseAndJoin() }`，
   已经是 `runTest` 的直接改调用。`releaseAndJoin()` 的
   `require(codecJob !== currentCoroutineContext()[Job])` 不会误伤：`runBlocking` 建的是另一个 Job。
2. **专门测非挂起语义、但意图在挂起版仍成立的** → 迁移并改名，因为 `releaseAndJoin()` 同样要过
   `releaseCodecOnce()` 的 `withCodecOperationLock`（`BaseMediaCodec.kt:277-278`，阻塞
   `ReentrantLock.withLock`），"等待活跃回调/迭代结束"的断言依然有效；
   `releaseAndJoin()` 的 `require(codecJob !== currentCoroutineContext()[Job])`（`:161`）也不会误伤，
   `runBlocking` 建的是另一个 Job：
   - `async:44 release waits for an active asynchronous input callback`（调用点 `:62`，**自带内联
     写法，不走 helper**）
   - `async:430 release racing start cleans partial codec and keeps session terminal`（调用点 `:448`）
   - `async:415 release before start makes the one-shot session terminal`（调用点 `:419`）
   - `sync:24 legacy release waits for the active codec iteration` → 去掉 `legacy`（调用点 `:38`）
   - `assertReleaseWaitsForCallback()` 辅助函数（`:483`，调用点 `:500`）——**一处改动覆盖 2 个用例**

   > **第八次复审更正（M1）：helper 的调用方只有 2 个，不是 3 个。**
   > 实测 `assertReleaseWaitsForCallback(` 在全文件只出现在 `:316`
   > （`release waits for active format callback`）与 `:364`
   > （`release waits for active error callback`）。
   > `release waits for an active asynchronous input callback`（`:44`）有**自己的内联**
   > `releaseExecutor.submit { subject.release() }`（`:62`），不经过 helper。
   > 本文此前把它同时算进"helper 覆盖的 3 个用例"和"`:62` 那一组"，属重复计数；
   > §16.1 的"无重无漏"因此不成立。**迁移动作本身不受影响**——18 个调用点仍被完整覆盖，
   > 只是 helper 那一处改动的收益是 2 个用例而非 3 个。
3. **前提随 API 一起消失的** → 重写或删除，并在此处记录放弃了什么：
   - `async:296 successful release remains exactly once across both release APIs`——"两个 API"的
     前提没了。改为连续调用三次 `releaseAndJoin()` 并断言 `mediaCodec.release()` 恰好一次，
     更名为 `... across repeated calls`。幂等性覆盖保留。
   - `StreamPlayerStopperTest:22 legacy stop releases resources in teardown order`——被测函数
     `StreamPlayerStopper.stop()` 整体删除，**此用例删除**。teardown 顺序的覆盖不丢：
     `suspending stop propagates cancellation after common cleanup`（`:54`）与
     `suspending stop logs ordinary decoder failures without throwing`（`:91`）断言的是同一个
     `events` 序列。

     > **第八次复审补登记**：该文件还有**第 4 个**用例
     > `suspending stop rejects calls from its owned scope before side effects`（`:122`），
     > 前几轮都没有登记。它不调用被删的 API，**不受本次改动影响、不得删除**；同时它是
     > §4.7.3 那条自调用守卫的现成模板——用 `scope.launch(start = CoroutineStart.UNDISPATCHED)`
     > 的**子协程**验证 `require` 会拒绝并且"在任何副作用之前"拒绝
     > （`verify(exactly = 0) { audioTrackPlayer.stop() }`）。给 `AudioSender.stop()` /
     > `AudioReceiver.stopServer()` 补守卫测试时照这个形状写。
4. **顺带清掉 20 处 `@Suppress("DEPRECATION")`**：`BaseMediaCodecAsynchronousTest` 19 处、
   `BaseMediaCodecSynchronousTest` 1 处，全是为调用这些弃用 API 而加的，随之失效。

**这批测试改动必须与删除 API 的提交在同一个提交里**，否则该提交单独 checkout 时测试编译不过。

队列取消、AudioTrack 的真实驱动行为与 Activity 生命周期仍需依赖 6.3 的真机清单；不得以
"单测 + 编译 + detekt" 当作完整的媒体验证。

### 6.3 真机验证清单（必做）

- AAC 流播放：启动 → 播放 → 停止 → 再启动，循环 5 次以上，确认无 native crash、无音频残留
- OPUS 流播放：同上
- `ADPCMActivity`：反复进入退出 Activity，确认 `AudioPlayer` 正常释放、无泄漏
- `AudioActivity`：录制 + 播放组合操作后退出
- PCM 路径（`ADPCMActivity`，`AudioType.PCM`）：反复进入退出，观察 logcat 是否出现
  `releaseBuffer() track ... disabled due to previous underrun` 及后续 `SIGABRT`。这是
  `AudioReceiver` 那段历史注释真正约束的路径；AAC/OPUS 路径不涉及 `AudioPlayer` 自己的 track
- 初始化失败回滚路径：构造一个解码器初始化失败的场景（如喂入损坏的 csd），确认回滚任务完成、
  无解码器泄漏，且随后的停止流程不受影响
- `MicRecorder` 构造失败回滚：分别覆盖 `AudioRecord` 构造抛异常和高级音频效果
  初始化抛异常，确认原异常重抛、encoder 最终完成 `releaseAndJoin()`、
  `releaseCompleted` 不在 encoder 释放前提前完成，且异步任务不访问未初始化的
  `audioRecord`。如果当前环境无法注入这两种构造失败，必须明确记为未验证
- **netty 回调触发的关闭**（第二次复审新增）：`cleanupScope` 只在这条路径上生效，Activity 侧
  走不到。从对端断开连接让 `onDisconnected()` / `onClientDisconnected()` 触发关闭，再用不可达
  地址让 `onFailed()` / `onStartFailed()` 触发关闭。确认停止序列正常、不出现
  `Audio sender/receiver teardown failed`、进程不崩，且随后 Activity 退出时的第二次关闭会等待
  同一 teardown，不会再次调用 AudioTrack/codec/netty release
- `AudioSender` 关闭后确认两个 Channel worker 都已退出；重复进出 Activity 后不应累积
  `DefaultDispatcher-worker-*` 阻塞线程。**这是回归确认而非缺陷验证**：`5672ecbbd` 的
  `runInterruptible` 已消除该泄漏，本项只确认 Channel 改造没有把它带回来
- **PCM 写入中触发关闭**（第六次复审新增，第七次复审改写落点）：正确的落点是
  **`AudioActivity` 的 PCM 播放**（`AudioType.PCM`，`AudioActivity.kt:144`），不是
  `AudioSender` / `AudioReceiver`——后两者恒为 OPUS，走不到 `AudioTrackPlayer.write()`（见 §15.1）。
  勾上 PCM 播放开关持续播放时退出 Activity，确认 `stopPcmPlayback()` 的
  `player?.stop()` → `playbackThread?.join()` → `releaseAndJoin()` 序列在有限时间内结束、
  无 ANR、无 write/release 并发。若 join 不返回，回到 §4.7.5。
  **另外必须看 flush/write 并发**（第八次复审新增）：`AudioPlayer.stop()` 经
  `AudioTrackPlayer.stop()`（`:274`）会先 `pause()` + `flush()` 再 `stop()`，
  确认 logcat 中不出现 `AudioTrack.write failed` / `AudioTrack.write error=`。
  若出现，按 §4.7.5 的更正框处理（加只做 `audioTrack.stop()` 的解阻塞入口），**不要**改调用顺序
- **teardown 期间的重连与迟到接入**（第八次复审新增，对应 §4.7.3 H2 与 §4.7.1）：
  sender 填不可达地址触发 `onFailed()` 后**不要立刻退出 Activity**，让 teardown 与
  `ConstantRetry(10, 2000)` 的重试窗口重叠，确认 logcat 中 `onConnected` **不再出现**、
  不出现第二个 `AudioPlayer` 的 `AAC/OPUS Decoder` 构造日志。
  receiver 侧：在 `stopServer()` 进行中让对端接入，确认 `onClientConnected` 被 `stopping` 标志挡住、
  不新建 `MicRecorder`。
  再测一次"点完发送立刻退出"：确认麦克风指示灯熄灭、没有 `MicRec` 的持续录音日志
  （这是 §4.7.1 第 1 种情况：`stop()` 跑赢 `start()` 的 `micRecorder` 赋值）
- **连接进行中触发关闭**（第七次复审新增）：把 sender 目标填成不可达地址，在
  `ConstantRetry(10, 2000)` 的连接/重试窗口内退出 Activity。新的 `stop()` 会
  `cancelAndJoin()` 等待 `start()` 起在同一 `ioScope` 上的连接协程，而
  `BaseNettyClient.connect()` 内部 `f.sync()` 最长阻塞 `CONNECTION_TIMEOUT_IN_MILLS` = 30 秒。
  **记录 teardown 实际耗时**：预期 `cont.invokeOnCancellation { cancelConnectFuture() }`
  会让它很快返回；若观察到接近 30 秒的停顿，说明该缓解不生效，需回到 §4.7.3 重新设计
  （例如把连接协程移出被 join 的 scope）
- **server 确实停止**（第九次复审新增，H1）：退出 `AudioActivity` 后确认 logcat 出现
  `BaseNettyServer` 的 `===== stopServer() ... =====` / `=====> Server released <=====`，
  并确认 10020 端口已释放（再次进入 Activity 能重新 bind，不报 `BindException`）。
  **该修法已于 2026-09-20 实施，因此这一项现在可以、也应当通过**；它正是 §4.7.4 赋值提前
  （连同 `startServer()` 去阻塞）的直接验收。若仍失败，回到
  `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.5
- **重试链确实终止**（第九次复审新增，H2）：sender 填不可达地址、在第一次连接失败后**立刻**
  退出 Activity。确认 logcat **不再**出现 `Reconnect(n) in 2000ms`，也不出现 teardown 之后的
  `onConnected`；确认 `disconnectManually() done` 与 client `release()` 各出现一次。
  **该修法已于 2026-09-20 实施（赋值提前 + `stop()` 改 suspend 并重排顺序），因此这一项
  现在第一次具备通过的条件，应当实测。** 改动前它必然失败，且失败有两个叠加原因：
  `senderClient` 在该时刻为 `null`，以及 `stop()` 的 `ioScope.cancel()` 会取消掉释放协程。
  若仍失败，回到 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.7 / §4.8。
  **门闩已于 2026-10-14 实施**：本项若观察到 `release()` 日志出现两次，就是
  `SuspendTeardownGate` 失效，属回归，必须查明
- `ADPCMActivity` 播放中立即退出，以及连续点击播放：旧 Job 只释放自己的 player，不得释放
  新 Job 创建的 player，且不得出现 write/release 并发

### 6.4 每批必跑

```bash
./gradlew --continue --rerun-tasks :audio:testDebugUnitTest :audio:detekt \
  :audio:ktlintCheck :demo:testDevDebugUnitTest :demo:ktlintCheck :demo:detekt \
  :demo:compileDevDebugKotlin
```

必须带 `--rerun-tasks`：刚修改过的路径不得只依赖 `UP-TO-DATE`。Codex 在本机
实施时执行这些命令；Claude Code 只能进行源码静态分析，必须将 Gradle 结果标为未验证。

## 7. 实施批次

实施顺序以“每批结束都可编译、可测试”为准：

| 批 | 内容 | 验证门 |
|----|------|---------|
| 1 | 先补齐 wrapper suspend 契约，保留旧 API | `audio` 编译/质量检查 |
| 2 | 迁移 `audio` 内部调用，保留旧 API | `audio` 编译/质量检查 |
| 3 | 先以 RED/GREEN 方式新增 `SuspendTeardownGate`，再迁移 demo 的 Channel、关闭编排与 ADPCM Job 所有权 | `demo` 单测/编译/质量检查 |
| 4 | 删除旧 API，同提交迁移依赖它们的现有测试 | `audio` + `demo` 全部目标验证 |
| 5 | 真机验证并记录设备结果 | 媒体与并发清单 |

每批的提交命令只是建议；未经用户明确授权不得执行 `git commit`、`git push` 或发布。
第 4 批的生产代码删除与现有测试迁移必须保持在同一批，避免中间状态测试编译失败。

## 8. 风险与未决项

| 风险 | 缓解 |
|------|------|
| ~~4.5 的 AudioTrack 释放顺序~~ | **已排除**（复审）：三个 `AudioTrackPlayer` 是独立实例，且 `decoderWrapper` 与 stream player 由 `AudioPlayer.init` 保证互斥，顺序无可争议。不再需要真机裁决 |
| 异步回滚逃逸异常杀进程 | 三个 `ioScope` 均无 `CoroutineExceptionHandler`，而 `NonCancellable` 换掉了父 Job。回滚 body 必须整体包 `runCatchingPreservingCancellation`，见 4.4 与 4.6 |
| `MicRecorder.startRecord()` 保持非挂起，其失败回滚异步化 | 已在 4.6 定案：与 3.3 的原则一致（关闭确定性、启动失败回滚可异步）。`startRecord()` 公开面不变，不扩大破坏面 |
| `MicRecorder.rollbackFailedInit()` 在构造失败后异步释放 encoder | 不复用已被 `released` guard 短路的 `finishRecorderReleaseAndJoin()`；捕获局部 encoder，并且只在同一协程的 `finally` 中完成 `releaseCompleted`。协程不访问可能未赋值的 `audioRecord` |
| ~~`BaseMediaCodec.release()` 是 `open`，可能有子类覆写~~ | **已排除**：`audio` 模块内 `override fun release()` 共 **4** 处（`AacEncoderWrapper:44`、`OpusEncoderWrapper:44`、`CompressedPcmEncoderWrapper:20`、`CompressedPcmDecoderWrapper:20`），四者实现的都是接口而非本类，无子类覆写，可直接删除。（复审修正：原文误记为 2 处，结论不变） |
| 删除代码后残留失效 import / 私有成员 | detekt `maxIssues = 0`，零容忍。每批结束跑完整静态检查 |
| 异步回滚被并发停止取消 | 已由 3.4 的 `launch(NonCancellable)` 解决，需在代码注释中写明原因，避免后续被"优化"掉 |
| netty 回调无法调用挂起的 `stop()` / `stopServer()` | 4 处非挂起回调使用带 handler 的专用 `cleanupScope`；`SuspendTeardownGate` 保证它们与 Activity 的关闭只执行一次 |
| 删除 API 导致 `audio` 现有单测编译失败 | 20 处 `subject.release()` / `subject.stop {}` 分布在三个测试文件。已在 6.2 逐条定案（迁移 / 重写 / 删除），且必须与删除 API 同提交。**第二次复审新增** |
| worker 未退出时释放 AudioTrack | 两个类的 worker 自 `5672ecbbd` 起均可被取消（`runInterruptible`）；`AudioSender` 进一步改 Channel 以免去线程中断依赖，两者都在释放 player 前 `cancelAndJoin()` |
| ~~`AudioSender`/`AudioReceiver` 的 `cancelAndJoin()` 卡在阻塞的 `AudioTrack.write()` 上~~ | **已排除**（第七次复审）：两者的 `AudioPlayer` 恒为 OPUS，`play()` 只走 stream player 分支，自身 track 从不 `write()`。join 前的 `audioPlayer?.stop()` 保留为零成本的防御性写法，不再作为死锁修复。见 §15.1 |
| 阻塞 `AudioTrack.write()` 挡住 join —— 真正的落点在 `AudioActivity.stopPcmPlayback()` | 该 player 确为 PCM，裸线程 `interrupt()` 唤不醒 native 写入。join 之前补 `player?.stop()`，与 `StreamPlayerStopper.prepareStop()` / `AacFilePlayer` 一致。见 §4.7.5。**第七次复审新增** |
| `AudioPlayer.releaseAndJoin()` 异常时漏放自身 AudioTrack | `audioTrackPlayer.release()` 放进 `finally`，见 §4.5。**第六次复审新增** |
| Activity 与 netty 并发重复关闭 | 两个类都通过 `SuspendTeardownGate` 共享一次 teardown 结果，不依赖 AudioTrack check-then-act |
| 一次性门闩被误用于第二个会话 | KDoc 明确不可重置；`AudioSender` / `AudioReceiver` 是单会话 owner，每次启动必须新建实例 |
| 共享 teardown 内的某个操作卡住，所有等待者一起卡住 | 门闩不设会破坏确定性的等待超时；要求关闭体内各资源操作自身有界，真机检查 Activity/netty 并发关闭能完成 |
| ADPCM 播放线程与 `onDestroy()` 并发 | 由单个 lifecycle `Job` 拥有局部 player，在自身 `finally` 释放；`onDestroy()` 只取消 Job |
| `cancelAndJoin()` 连带等待 netty 连接协程（最长 30 s） | `start()` 在同一 `ioScope` 上跑 `connect()`，其 `f.sync()` 最长阻塞 `CONNECTION_TIMEOUT_IN_MILLS`；门闩无超时，follower 一起等。依赖 `cont.invokeOnCancellation { cancelConnectFuture() }` 提前解除阻塞，必须由 §6.3 的"连接进行中触发关闭"实测确认。见 §4.7.3。**第七次复审新增** |
| 嵌套 `try/finally` 丢弃根因异常 | 裸 `try/finally` 不记 suppressed，后一步的异常会替换掉前一步的根因，门闩广播的是错误的那个。改用 §4.7.3 的 `teardownSteps()` 平铺串联，首个失败保留、后续以 suppressed 附加；同时避免 6 层嵌套违反仓库的 "No deep nesting (>4 levels)"。**第七次复审新增** |
| 门闩把 `CancellationException` 广播给等待者 | `stopAndJoin()`（`:44-45`）与 `finishRecorderReleaseAndJoin()`（`:464-466`）都会重抛取消；广播后 follower 侧读作"自己被取消"，`launchCleanup` 与 `cleanupScope` 都不会记录。门闩只记录非取消异常，见 §4.7.1，并由第 4 条单测覆盖。**第七次复审新增** |
| `RecordCallback.onStop` 交付时机变化未被下游感知 | `startRecord()` 签名不变但失败回滚异步化，onStop 可能在其返回之后到达。必须写入 §5 与 CHANGELOG，见 §4.6。**第七次复审新增** |
| 删除 encoder `release()` 后留下空的 `companion object` | `TAG`/`LogContext`/`runCatchingPreservingCancellation` 的唯一使用点都在被删函数内；只删 TAG 会留下 `companion object { }`，触发 detekt `EmptyClassBlock`。整个 companion 一并删除，见 §4.2。**第七次复审新增** |
| 从被 join 的 scope 内部调用 `stop()` / `stopServer()` | 两者对自己的 `ioScope` 做 `cancelAndJoin()`，自调用即静默死锁。用 `StreamPlayerStopper.stopAndJoin()`（`:34-38` + `:55-56`）的**后代检查**守卫，见 §4.7.3。**第七次复审新增；第八次复审更正写法——此前给的身份比较版本永不触发** |
| 关闭序列把 netty 断连排在最后，重连在 teardown 期间复活会话 | `handleConnectFailure()`（`:359`）同步回调 `onFailed` 消费门闩后立即 `doRetry()`（`:361`），2 秒后可能重连成功并触发 `onConnected()` 新建 `AudioPlayer`，而门闩已消费、无人释放。`disconnectManually()` 提为第一步（`:756` 据此终止重试），并加 `stopping` 标志。见 §4.7.3。**第八次复审新增** |
| 门闩消费之后仍有人新建资源（start/stop 交错、迟到的客户端接入） | 门闩只保证"关闭跑一次"，保证不了"关闭后没人再建"。`AudioSender.start()` 在 `:94` 才赋值 `micRecorder`，`AudioReceiver` 在 `onClientConnected()` 才建 `MicRecorder` 与 worker。两个类各加 `stopping: AtomicBoolean`，在 `start*` 与 netty 接入回调开头自查。见 §4.7.1、§4.7.3、§4.7.4。**第八次复审新增** |
| `AudioPlayer` 自身 AudioTrack 由"最先释放"改为"最后释放"，与历史 SIGABRT 注释相反 | 该注释（`AudioReceiver.kt:202-210`）约束的是 PCM 路径。PCM 下 `finally` 之前的语句全是 no-op（`AudioDecoderManager.kt:18` 对 PCM 返回 null），且 §4.7.5 已保证 release 时写线程已 join。论证须抄进 `releaseAndJoin()` 的 KDoc，见 §4.5。**第八次复审新增** |
| `AudioPlayer.stop()` 先 `pause()`+`flush()` 才 `stop()`，对进行中的写入不是干净的唤醒 | 顺序不变（与 `StreamPlayerStopper.prepareStop()` / `AacFilePlayer` 一致），但 §6.3 的 PCM 项须同时观察 `AudioTrack.write failed`；若出现则加只做 `audioTrack.stop()` 的解阻塞入口。见 §4.7.5。**第八次复审新增** |
| ~~`receiverServer` 恒为 null，`stopServer()` 的最后一步是死代码~~ | **✅ 已于 2026-09-20 实施。** 原因：`AudioReceiver.kt` 的 `also { ... it.startServer() }` 在阻塞调用**之后**才赋值，而 `BaseNettyServer.startServer()` 会一直停在 `closeFuture().sync()`，服务器成功启动即永远赋不上值。修法连同根因一起落地：`startServer()` 改 `suspend` 且不再阻塞，`receiverServer` 赋值提前。见 §4.7.4 与 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.5。**第九次复审新增** |
| ~~`senderClient` 在首次连接窗口内为 null，使"断连提为第一步"落空~~ ✅**已实施（2026-09-20）** | 同一 `also { ... it.connect() }` 形状（`AudioSender.kt:85-92`）；而 H2 场景正是 `connect()` 内部同步回调 `onFailed`，此刻尚未赋值。于是第 1 步 `disconnectManually()` 与第 7 步 `release()` 双双空转，重试链继续、EventLoopGroup 不 shutdown；若第 5 步的 `cancelAndJoin()` 先取消 `start()` 协程，则永远赋不上值。`stopping` 标志仍挡住了新 `AudioPlayer`。修法同上，见 §4.7.3。**第九次复审新增** |
| `audioPlayer?.stop()` 与 `cancelAndJoin()` 合并成一个 `teardownSteps` step，前者失败即吞掉后者 | 合并写法让两个动作共用一个 `try`，`stop()` 抛出则 worker 不被 join，而下一步照常 `releaseAndJoin()` ⇒ write 与 release 并发，正是本方案要消除的那个竞态。今天 `AudioTrackPlayer.stop()`（`:276-282`）全程 `runCatching` 不抛，但这一步的存在理由本就是"`defaultAudioType` 改 PCM 那天"，届时它会真正做事。拆成两个 step，见 §4.7.3 更正框、§4.7.4。**第十次复审新增** |
| 门闩把关闭体的 `CancellationException` 在 leader 侧也静默丢弃 | `launchCleanup`（`AudioActivity.kt:391-392`）对取消直接 rethrow 不记日志，`cleanupScope` 的 handler 也不接收取消；而关闭体跑在 `NonCancellable` 下，此处的取消是**真实的关闭失败**（`StreamPlayerStopper.kt:44-45`、`MicRecorder.kt:464-466` 主动重抛）。gate 的取消分支必须自己记一条日志，见 §4.7.1。**第十次复审新增** |
| gate 在 catch 块内用 `is` 判定异常类型 | 与仓库既有写法（`StreamPlayerStopper.kt:44-45`、`AudioActivity.kt:391-393` 的双 catch 子句）相反，并落在 detekt `InstanceOfCheckForException`（`10-configs/detekt.yml:242`，active）的形状上；当前 `detekt` 任务未启用 type resolution 故不报，但无理由偏离既有写法。改用两个 catch 子句，见 §4.7.1。**第十次复审新增** |

## 9. 明确不做的事

- 不把 `synchronized` 换成 `Mutex`（见 3.2）
- 不使用任何 `@Suppress("DEPRECATION")`
- 不内联 `StreamPlayerStopper` 的私有辅助函数
- 不改 `AudioTrackPlayer.release()`（它直接包装 `AudioTrack`，无 worker 需要 join，不属本次范围）
- 不处理 `AudioCipherActivity.kt:95`（那是 `android.media.MediaPlayer`）

## 10. 复审记录（2026-09-16）

> **§10 至 §14 是历史复审记录。** 其中的行号反映各轮复审当时的源码，之后未逐轮回填，
> 因此**不要拿它们定位代码**。当前有效的行号只在 §1 至 §9、§15 至 §17 与计划文档中维护
> （以 `9eec9807f` 为准）；与 §10–§14 冲突时以前者为准，并以实施时的 checkout 复核。
> §11.2 的 C1 与 L1 两行本身给出过错误的"更正值"，已就地标注，见 §16.2 的 L1。

本设计文档经过一次独立复审，逐条核对了它对源码的事实声明。**支点性结论全部成立**，7 处需要修正
的已就地改入正文。

### 10.1 已验证成立

- §1 列出的 8 处弃用调用及行号准确；§1.1 的根因判断（`AudioEncoderWrapper.releaseAndJoin()` 的
  退化默认实现、`AudioDecoderWrapper` 无挂起变体）与源码逐字相符。
- §3.1 `synchronized` 块内不能调用挂起函数——正确。
- **§3.4 是整个方案的支点，已从 kotlinx-coroutines 字节码确认**（第二次复审修正：首轮复审写的是
  1.9.0，本项目实际用的是 **1.10.2**，见 `gradle/libs.versions.toml:32`；结论在 1.10.x 未变）：
  `NonCancellable.attachChild()` 返回 `NonDisposableHandle.INSTANCE`，不建立父子链。
  `launch(NonCancellable)` 确实脱离 scope 的 Job，能存活过 `ioScope.cancel()`。
- §4.7 指出的两个既有 bug 真实存在：`AudioReceiver` 先 `cancel()` 再释放（`:201` vs `:211`）；
  `AudioSender` 的 `ioScope.launch` 紧跟 `ioScope.cancel()`（`:153-157`）构成竞态。
- §8 关于无子类覆写 `BaseMediaCodec.release()` 的结论成立。

### 10.2 已修正的 7 处

| 编号 | 问题 | 修正位置 |
|------|------|---------|
| F1（HIGH） | §4.5 的 AudioTrack 释放顺序两难基于错误前提：三个 `AudioTrackPlayer` 是独立实例，且 `decoderWrapper` 与 stream player 由 `AudioPlayer.init` 保证互斥。原文把它列为"唯一需真机裁决的设计点"并禁止静态决定，会浪费一轮真机验证 | §4.5 重写、§6.3、§8 |
| F2（MEDIUM） | §4.7 要求 `AudioActivity` 改用 `lifecycleScope.launch(NonCancellable)`，但该文件已有 `launchCleanup` + `ioScopeJob.complete()` 的成熟机制，三处调用本来就在 suspend 块内。原方案会引入第二个清理 scope 并绕开失败日志 | §4.7 |
| F3（MEDIUM） | §4.7 把 `AudioReceiver.stopServer()` 写成 `stop()`，是一次未声明的重命名，§5 也未列 | §4.7 |
| F4（MEDIUM） | §4.7 对 `ADPCMActivity` 一刀切，但该文件无协程 scope，`:115` 在裸 `thread { }` 内，`:121` 在 `onDestroy()`，两者处境不同 | §4.7 |
| F5（MEDIUM） | §4.6 的 `MicRecorder` 回滚片段未包异常。三个 `ioScope` 都没有 `CoroutineExceptionHandler`，而 `NonCancellable` 换掉了父 Job，逃逸异常直达平台默认处理器 → 进程崩溃 | §4.6、§8 |
| F6（LOW） | §8 记 `override fun release()` 为 2 处，实为 4 处（漏算两个 `CompressedPcm*Wrapper`）。结论不变 | §8 |
| F7（LOW） | §7 的两提交方案会留下一个编译不过的中间提交 | §7 |

另：§6.2 原列的两条单元测试经核实价值接近于零（一个断言空方法体，一个编译期即已保证），已改为
明确不要求，并强化"正确性依赖真机验证"的表述。

### 10.3 未改动的前提

本节当时未重新评估 §2 的两个既定前提（弃用方法直接删除、`stop()` 改为
suspend）。其中关闭入口如何承接的细节已被 §12 的第三次复审修正，当前以
§2、§4.7 和 §12 为准。

## 11. 第二次复审记录（2026-09-16）

本文档在 §10 的首轮复审之后又做了一次独立核对，这次同时核对了**计划文档**
（`00-documents/superpowers/plans/2026-09-16-audio-deprecated-teardown-removal.md`）与实际源码。
**核心方案依然成立**，但发现两处会直接卡住实施的漏洞。

### 11.1 再次确认成立

- §1 列出的 8 处弃用调用及行号，逐行核对全部准确。
- §1.1 的根因判断与源码逐字相符（`AudioEncoderWrapper.kt:15` 的退化默认实现、
  `AudioDecoderWrapper.kt` 无挂起变体）。
- §3.4 的 `NonCancellable` 支点结论正确（版本更正见 §10.1）。
- §4.5 的"三个 `AudioTrackPlayer` 相互独立 + `decoderWrapper` 与 stream player 互斥"——按
  `AudioPlayer.init`（`:46-84`）与 `play()`（`:86`）逐分支核对，成立。
- §10.2 的 F2 / F4 修正准确：`AudioActivity` 确有 `launchCleanup`（当前 `:387`）与
  `ioScopeJob.complete()`（当前 `:383`），PCM / receiver / sender 入口当前分别在
  `:354` / `:371` / `:372`；`ADPCMActivity` 确实没有
  任何协程 scope，`:115` 在 `kotlin.concurrent.thread` 块内，`:121` 在 `onDestroy()`。
  这是当时源码事实；实施方案已被 §12 修正为 lifecycle Job 所有权模型。
- 全仓无 `BaseMediaCodec.release()` 的子类覆写；`audio` / `demo` 之外没有任何调用方。
- `CHANGELOG.md` 的 `### 变更 (Changed)` 小节确实存在（`:58`）；demo 依赖
  `lifecycle-runtime-ktx`，`lifecycleScope` 可用。

### 11.2 本轮修正的问题

| 编号 | 级别 | 问题 | 修正位置 |
|------|------|------|---------|
| C1 | CRITICAL | `stop()` / `stopServer()` 改挂起后，4 个 netty 监听回调是非挂起接口实现，改不了签名，会直接编译失败。原 §4.7 只统计了 Activity 侧调用方。（本行原记的行号 `AudioSender:71,77` / `AudioReceiver:96,102` 已过期；按 `9eec9807f` 实为 `AudioSender.kt:73,79` 与 `AudioReceiver.kt:97,103`，以计划文档 Task 3 的表格为准。第七次复审更正） | §4.7（新增专用 `cleanupScope` 方案）、§5、§8 |
| C2 | CRITICAL | 原 §6.2 只说"没有值得新写的单元测试"，未核对**现有**测试对被删 API 的依赖。实际三个测试文件共 20 处调用，删除后 `:audio:testDebugUnitTest` 编译失败 | §6.2（逐用例定案）、§7 |
| H2 | HIGH | 原 §4.7 把 `AudioReceiver` 的 `ioScope.cancel()` 挪到函数末尾，使播放循环在整个 `releaseAndJoin()` 期间继续跑；PCM 路径下 `play()` 的 `write()` 会撞 `AudioTrack.release()` | §4.7（改为释放前 `cancelAndJoin()`）、§8 |
| M1 | MEDIUM | §4.6 称 `stopAudioRecord()` 与 `ioScope.cancel()` "相对顺序保持不变"，与实际改动不符（确实反了） | §4.6 |
| M2 | MEDIUM | §10.1 称从 kotlinx-coroutines **1.9.0** 字节码确认，项目实际用 **1.10.2** | §10.1 |
| M3 | MEDIUM | `ADPCMActivity` 的 `:115` / `:121` 重复释放同一个 `player` 且不置空 | §4.7 |
| L1 | LOW | §4.4 给的 `OpusStreamPlayer` 删除范围 `:210-218` 漏掉了上方 KDoc。（本行当时给出的更正值 `:206-218` 本身也是错的；按 `9eec9807f` 实测，KDoc 起于 `:207`、函数体止于 `:219`，以 §4.4 与计划文档的 `:207-219` 为准。第七次复审更正） | §4.4 |
| L2 | LOW | `runCatchingPreservingCancellation` 会重抛 `CancellationException`，看似是 §4.6 包裹的一条逃逸缺口；实际因 `parentHandle === NonDisposableHandle` 而被静默丢弃，不会崩进程 | §4.6 |

### 11.3 本轮做出的两个新决策

1. **netty 回调的关闭入口（C1）**：`AudioSender` / `AudioReceiver` 各加一个
   `SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler` 的 `cleanupScope`，四处回调改为
   `cleanupScope.launch { ... }`。备选方案（复用 `ioScope.launch(NonCancellable)`、或保留一个
   非挂起薄包装）均被否决：前者需要在四处重复 §4.6 那套异常包裹，后者等于重新引入一个
   "不确定性关闭"公开入口，与本次目标冲突。
2. **播放循环竞态的修复程度（H2），已被第三次复审推翻**：当时决定只修
   `AudioReceiver`，让 `AudioSender` 维持 bare cancel。第三次复审证明两个 `take()` worker 都会
   永久阻塞并持有所有者，与确定性关闭目标相冲突；当前方案已改为 Channel +
   `cancelAndJoin()`，见 §4.7。
   **第五次复审补充**：该"永久阻塞"前提已被提交 `5672ecbbd` 的 `runInterruptible` 消除，
   Channel 改造因此降级为简化而非缺陷修复，见 §14。

## 12. 第三次复审修正（2026-09-18）

本轮专门核对了确定性关闭的实际 worker 退出、并发重入、Activity 所有权和仓库执行边界，
修正以下问题：

1. `AudioSender` 的录音发送和接收播放 worker 都阻塞在 `ArrayBlockingQueue.take()`，bare
   cancel 无法使它们退出。改为有界 Channel + `cancelAndJoin()`。
   **本条前提已于第五次复审作废**：提交 `5672ecbbd` 用 `runInterruptible` 修复了取消问题，
   见 §14。
2. Activity 和 netty 回调可并发调用关闭，`AudioTrack.state` 的 check-then-act 不是并发保护，
   `runCatching` 也无法阻止 native 重复 release。新增有单测覆盖的 `SuspendTeardownGate`。
3. `ADPCMActivity` 的裸 thread 和 `onDestroy()` 共享可变 player，可以与正在进行的 write 并发
   release，或在连续点击时释放错误实例。改为由 lifecycle Job 构造、使用并在
   `finally` 释放局部 player。
4. 不再向下游推荐裸 `lifecycleScope.launch(NonCancellable)`；改用受管理的 teardown scope
   和明确异常处理。
5. Codex 在本机实施时执行定向 Gradle 验证；Claude Code 仅提供静态分析。所有提交、
   推送和发布仍以用户明确授权为前提。

## 13. 第四次复审修正（2026-09-18）

本轮以当前 checkout 重新核对了计划与尚未实施的源码，补齐以下问题：

1. 新增的 `MicRecorder.rollbackFailedInit()` 仍调用将被删除的
   `AudioEncoderWrapper.release()`。已在 §4.6 单独定案：捕获局部 encoder，
   在 `NonCancellable` 协程内等待 `releaseAndJoin()`，并且只在同一协程的
   `finally` 中完成 `releaseCompleted`。
2. 更新了 MicRecorder、AudioSender、AudioReceiver、两个 stream player 和弃用警告的
   当前行号，并明确函数名与调用结构才是实施时的权威锚点。
3. 删除 `finishRecorderRelease()` 时需同步修正两处 KDoc，而非仅修正
   `released` 字段上的一处。
4. `AudioSender` 的 Channel worker 重写必须保留原有逐帧发送异常隔离；计划已给出
   完整函数体，不再只写“换成 `for`”。
5. `SuspendTeardownGate` 的 KDoc 现在明确它是单 owner、单会话、不可重置的
   门闩；同时记录了共享同一 `Throwable` 和无门闩层超时所带来的等待耦合。
6. 提交 `f27449db0` / `484d2c3d9` 的 message 过度描述了未实施的代码结果。
   本文已在首部显式更正状态；因提交已在远端，未经用户授权不改写历史。

## 14. 第五次复审修正（2026-09-18）

本轮以当前 checkout（`9eec9807f`）重新核对，修正两类问题。

### 14.1 Channel 改造的事实前提已失效

第三次复审（§12 第 1 条）把"两个 worker 阻塞在 `take()` 上、`ioScope.cancel()` 无法唤醒、
因此永久占用 `Dispatchers.IO` 线程"作为 Channel 改造的依据。**该缺陷已在提交 `5672ecbbd`
中由另一种方式修复**：两处 `queue.take()` 被包进 `runInterruptible { }`，取消会中断阻塞线程。
同一提交还把 `AudioReceiver` 的两个 worker 从 `poll()` + `delay(10.milliseconds)` 改成了相同的
阻塞取数写法。

影响范围：§4.7.3、§4.7.4、§6.3、§8、§11.3、§12 已就地更正。结论是 Channel 改造由"缺陷修复"
降级为"简化"，仍建议实施（`close()` 语义更清晰、免去线程中断依赖），但不得据此撰写修复性
提交信息或缺陷验证项。

同时明确了一处此前未记录的不对称：`AudioReceiver` 的两个队列**不**改 Channel，因为该文件
已 import `io.netty.channel.Channel`。

### 14.2 demo 侧行号取自过期快照

第四次复审刷新了行号，但 `AudioSender` / `AudioReceiver` 两处取自 `5672ecbbd` 之前的版本，
`AudioActivity` 与 `audio` 模块则正确。已更正的引用：

| 位置 | 原记 | 当前 |
|------|------|------|
| `AudioReceiver` SIGABRT 注释 | `:190-198` | `:202-210` |
| `AudioReceiver.defaultAudioType` | `:33` | `:32` |
| `stopRecord()` 调用方 | `AudioSender:142` / `AudioReceiver:200` | `:152` / `:212` |
| `AudioReceiver` cancel 早于 release | `:189` vs `:199` | `:201` vs `:211` |
| `AudioSender` launch 紧跟 cancel | `:143-147` | `:153-157` |

计划文档中的对应引用同批更正。**行号只是辅助定位，函数名与调用结构才是权威锚点**；实施前
应再次以当时的 checkout 核对。

### 14.3 §4.3 代码块补全

`BaseMediaCodec.release()` 的删除范围 `:226-246` 正确，但引用的代码块此前漏掉了上方的
`/** Release resource. */` KDoc 与函数体内关于 legacy 同步语义的两行注释，已补回，避免实施者
按不完整的片段比对源码。

## 15. 第六次复审修正（2026-09-18）

本轮针对"确定性关闭"是否真的有界做了一次对抗性核对，发现两处此前几轮都漏掉的问题。

### 15.1 HIGH：`cancelAndJoin()` 可能永久等待阻塞的 PCM 写入（**已被第七次复审更正，见本节末尾的框**）

前几轮把"取消能唤醒 worker"当成了 join 有界的充分条件。`runInterruptible` 只包住
`queue.take()`；worker 一旦进入 `AudioPlayer.play()`，PCM 路径就停在
`AudioTrackPlayer.write()`（`:194`）的阻塞 `AudioTrack.write(short[], int, int)` 上，协程取消
无法中断该 native 调用。于是形成死等：`cancelAndJoin()` 等 worker，worker 等写入返回，
而能让写入返回的 `AudioTrack.stop()` 排在尚未执行的 `releaseAndJoin()` 里。

`AudioSender` 改 Channel 不解决这一点——`Channel.receive()` 可取消，不代表已经开始的
`play()` / `write()` 可取消。

修正：在 `cancelAndJoin()` 之前插入只停不放的 `audioPlayer?.stop()`。已改入 §4.7.3、§4.7.4、
§8 与 §6.3 真机清单。该做法与 `StreamPlayerStopper.prepareStop()` 的既有顺序一致。

> **第七次复审更正（2026-09-18）：上面这段的前提对本节所针对的两个类不成立。**
>
> `AudioSender` 与 `AudioReceiver` 的 `AudioPlayer` 都以 `AudioReceiver.defaultAudioType`
> 构造（`AudioReceiver.kt:32` = `AudioType.OPUS`；`AudioSender.kt:55`、`AudioReceiver.kt:184`）。
> OPUS 下 `AudioPlayer.play()` 只走 `:99-108` 的 `opusStreamPlayer?.startPlayingStream()` 分支，
> `:88-98` 的 PCM 分支恒不命中，`AudioPlayer` 自己那个 `AudioTrackPlayer` 从不 `play()`、
> 从不 `write()`。**因此这两个类的 worker 不可能卡在 `AudioTrackPlayer.write()`（`:194`）上，
> 本节描述的死等在当前源码中不存在。**
>
> 这与 §4.5 早已写明的事实是同一条（"当前 `AudioReceiver.defaultAudioType`（`:32`）是
> `AudioType.OPUS`，该 track 从不启动"）；第六次复审在写 §15.1 时与 §4.5 自相矛盾，未被发现。
>
> 结论的调整：
>
> 1. `audioPlayer?.stop()` **保留**。它零成本（`AudioTrackPlayer.stop()` 在
>    `playState == PLAYSTATE_STOPPED` 守卫处直接返回，`:271`），与
>    `StreamPlayerStopper.prepareStop()` 一致，且 `defaultAudioType` 一旦改成 PCM 就变成必需。
>    但它是**防御性写法，不是缺陷修复**——提交信息与验证项都不得写成修死锁。
> 2. §6.3 原来那条"若出现 ANR 或 teardown 不返回，说明 `audioPlayer?.stop()` 的位置不对"是一条
>    **永远不会失败**的验证项，已改写落点。
> 3. 这个"阻塞写入挡住 join"的模式在 demo 里**确实存在**，只是位置不同：
>    `AudioActivity.stopPcmPlayback()`（`:343-356`）的 player 确为 `AudioType.PCM`（`:144`），
>    其裸线程 `playPcmThread`（`:148-176`）直通阻塞写入，而 teardown 只 `interrupt()`（`:351`）
>    就 `join()`（`:353`）。前几轮都没有覆盖这里。修正已改入 §4.7.5。

### 15.2 MEDIUM：`AudioPlayer.releaseAndJoin()` 的异常路径会漏放自身 AudioTrack

§4.5 论证了三个 `AudioTrackPlayer` 相互独立、释放顺序自由，但该结论只覆盖顺序，不覆盖
异常。`StreamPlayerStopper.stopAndJoin()` 会重抛 `CancellationException`，其 `require()` 也会抛；
任一前置释放抛出，`AudioPlayer` 在 `:43` 无条件创建的那个 track 就不会被释放。
修正：`audioTrackPlayer.release()` 移入 `finally`。已改入 §4.5 与 §8。

### 15.3 本轮确认无误

`rollbackFailedInit()` 的独立处理与 `releaseCompleted` 屏障位置、两处
`finishRecorderRelease` KDoc、Channel worker 的逐帧异常隔离、一次性门闩的三条契约、
§14 对 `5672ecbbd` 的前提作废、以及刷新后的行号与 20 处测试调用点，均与当前源码一致。

## 16. 第七次复审修正（2026-09-18）

本轮以 `9eec9807f` 为基准，逐条核对了两份文档对源码的全部事实声明，并针对"确定性关闭"的
异常路径与等待边界做了一次对抗性核对。

### 16.1 引用类事实声明的核对结果（标题已订正；方案代码未核，见 §17.2）

> **第八次复审更正：本节标题与"可以直接信任"的措辞过强，已被两处反例推翻，见 §17.2。**
> 下面这份清单里绝大多数条目经第八次复审重新核对后**确实成立**（复核明细见 §17.3），
> 但至少有两条不成立：
> - "`:500` ... 被 3 个用例共用"与"§6.2 逐用例迁移方案 ... 无重无漏"——helper 实际只有
>   2 个调用方，且有一个用例被重复计数（§17.1 M1）；
> - 本节完全没有核对第七次复审**自己新写进正文的方案代码**是否与它引用的源码一致，
>   §4.7.3 那条"照抄 `StreamPlayerStopper`"的 `require` 就是在这一轮被写错的（§17.1 H1）。
>
> 结论：**行号与计数类声明可以信任，方案代码块必须重新对照源码**。

以下声明逐条比对源码后**全部准确**，实施时可以直接信任：

- §1 的 8 处弃用调用及行号；且 `audio/src/main` 中对 `BaseMediaCodec.release()` 的调用点
  **就只有这 8 处**，无遗漏的隐藏调用者。
- §6.2 的 20 处测试调用点行号（`BaseMediaCodecAsynchronousTest` 18 处、
  `BaseMediaCodecSynchronousTest` 1 处、`StreamPlayerStopperTest` 1 处），以及
  §6.2 逐用例迁移方案覆盖的正是这 18 处，无重无漏。
- 测试中 20 处 `@Suppress("DEPRECATION")`（19 + 1）与 `audio/src/main` + `demo/src/main`
  中 13 处无关抑制的**逐文件拆分**（AudioExt 2 / WifiActivity 4 / BluetoothClientActivity 3 /
  BluetoothServerActivity 2 / MainActivity 1 / SimpleAdapter 1）。
- §4.3 `BaseMediaCodec.release()` 的 `:226-246` 范围与所引代码块逐字相符。
- §8 的"`override fun release()` 共 4 处、无 `BaseMediaCodec` 子类覆写、`audio`/`demo`
  之外无任何调用方"。
- §4.5 的"三个 `AudioTrackPlayer` 相互独立"与"`decoderWrapper` 与 stream player 互斥"。
- `MicRecorder`、两个 stream player、`StreamPlayerStopper`、`AudioPlayer`、`AudioTrackPlayer`
  以及 demo 侧 `AudioSender` / `AudioReceiver` / `AudioActivity` / `ADPCMActivity` 的全部行号。
- 环境类声明：coroutines `1.10.2`（`gradle/libs.versions.toml:32`）、ktlint 行长 100
  （`.editorconfig`）、`CHANGELOG.md:58` 的 `### 变更 (Changed)` 确在 `## [Unreleased]` 之下、
  `demo` 已具备 `kotlin-test-junit5` 与 `kotlinx-coroutines-test`（`libs.versions.toml` 的
  `test` bundle）且存在 `dev` flavor，因此 §6.2 新增的门闩单测可以编译运行。
- 两份文档所有 Kotlin 代码块**无一行超过 100 字符**。

### 16.2 本轮修正的问题

| 编号 | 级别 | 问题 | 修正位置 |
|------|------|------|---------|
| H1 | HIGH | §15.1 的死等前提对 `AudioSender` / `AudioReceiver` 不成立（两者恒为 OPUS，走不到 PCM 写入路径），与 §4.5 自相矛盾；相应的真机验证项永远不会失败；而该模式真正存在的 `AudioActivity.stopPcmPlayback()` 前几轮都没覆盖 | §15.1、§4.7.3、§4.7.4、§4.7.5、§6.3、§8 |
| H2 | HIGH | §15.1 枚举 `cancelAndJoin()` 的等待对象时漏了 `start()` 起在同一 `ioScope` 上的连接协程；`BaseNettyClient.connect()` 的 `f.sync()` 最长阻塞 30 秒，而门闩按设计无超时 | §4.7.3、§6.3、§8 |
| M1 | MEDIUM | 关闭序列用 6 层嵌套 `try/finally`：裸 `try/finally` 不记 suppressed，后一步异常会顶掉根因；且违反仓库自己的 "No deep nesting (>4 levels)" | §4.7.3（改为 `teardownSteps()`）、§4.7.4、§8 |
| M2 | MEDIUM | `SuspendTeardownGate` 捕获 `Throwable` 后把 `CancellationException` 也广播给等待者，而关闭体里确有重抛取消的路径；follower 侧会把它读成"自己被取消"并静默吞掉 | §4.7.1、§6.2（第 4 条单测）、§8 |
| M3 | MEDIUM | `failRecordStart()` 异步化改变了 `RecordCallback.onStop` 的交付时机（不再保证在 `startRecord()` 返回前送达），签名不变因而下游无感，却未列入破坏性变更 | §4.6、§5、§8 |
| M4 | MEDIUM | 删除两个 encoder wrapper 的 `release()` 后，`TAG` 是 `companion object` 的唯一成员，只删 TAG 会留下空 companion，触发 detekt `EmptyClassBlock`（`10-configs/detekt.yml:203`） | §4.2、§8 |
| L1 | LOW | §11.2 的两处历史行号本身是错的更正：C1 的 `AudioSender:71,77` / `AudioReceiver:96,102` 已过期；L1 给的 `:206-218` 与 §4.4 的 `:207-219` 冲突，且后者才对 | §11.2 |
| L2 | LOW | 新的 `stop()` / `stopServer()` 对自己的 `ioScope` 做 `cancelAndJoin()`，却没有 `StreamPlayerStopper.stopAndJoin()`（`:36-38`）那样的自调用 `require()` 守卫 | §4.7.3、§4.7.4、§8 |
| L3 | LOW | 计划文档把 OPUS 版日志文案记作 `"OPUS encoder release failed"`，源码实为 `"Opus encoder release failed"`（`OpusEncoderWrapper.kt:46`） | 计划 Task 4 Step 2 |
| L4 | LOW | `ADPCMActivity` 连续点击时 `playbackJob?.cancel()` 不等待，旧 Job 的异步释放会与新 player 的构造重叠，堆积多个 `AudioTrack` | §4.7.6 |

### 16.3 本轮未改动的结论

§2 的两个既定前提、§3.2 对 `Mutex` 方案的否决、§3.4 的 `NonCancellable` 支点、
§4.5 的释放顺序与 `finally` 论证、§6.2 的逐用例迁移方案、§14 对 `5672ecbbd` 的前提作废，
均经复核后维持不变。

## 17. 第八次复审修正（2026-09-18）

本轮以 `9eec9807f` 为源码基准（其后的 `fd3cd48b7`、`d3058e2bc` 两个提交只改这两份文档，
源码一行未动，因此全部行号仍然有效）。与前几轮不同，本轮**没有**重复核对行号，而是集中做两件事：

1. 把第六、七次复审新写进正文的**方案代码块**逐段与它们所引用的源码对照——此前从未有人做过这件事；
2. 沿着"资源所有权"而不是"关闭顺序"再走一遍，问的是"关闭之后还有谁会建出新资源"。

两个 HIGH 都出自这两条线索。

### 17.1 本轮修正的问题

| 编号 | 级别 | 问题 | 修正位置 |
|------|------|------|---------|
| H1 | HIGH | §4.7.3/§4.7.4 的自调用 `require()` 用身份比较（`caller !== ioScope 的 Job`），而 `ioScope.launch` 的子 Job 永远 `!==` 根 Job，**该守卫在任何现实路径上都不会触发**；真正会死锁的"从子协程调用 `stop()`"恰好被放行。文档同时声称这是"照抄 `StreamPlayerStopper.stopAndJoin()`（`:36-38`）"，而源码用的是递归后代检查（`:34-38` + `:55-56`），`StreamPlayerStopperTest.kt:121-139` 正是用子协程验证它 | §4.7.3（更正框 + 正确写法）、§4.7.4、§6.2（第 6 条单测）、§8 |
| H2 | HIGH | 关闭序列把 `senderClient.disconnectManually()` 排在最后，只考虑了音频资源依赖，忽略 `senderClient` 在 teardown 期间仍是新会话的生产者：`handleConnectFailure()`（`:359`）同步回调 `onFailed` 消费门闩、`:361` 随即 `doRetry()`，2 秒后重连成功即 `onConnected()` 新建 `AudioPlayer`，而门闩已消费、无人释放 | §4.7.3（改为第一步 + `stopping` 标志）、§4.7.1、§6.3、§8 |
| M1 | MEDIUM | `assertReleaseWaitsForCallback()` 实际只有 2 个调用方（`:316`、`:364`），`release waits for an active asynchronous input callback`（`:44`）走的是自己的 `:62`；文档把它同时算进 helper 组和 `:62` 组，属重复计数，§16.1 的"无重无漏"随之不成立 | §6.2、§16.1 |
| M2 | MEDIUM | `AudioPlayer` 自身 AudioTrack 的释放由"最先"改为"最后"，与 `AudioReceiver.kt:202-210` 历史注释的 "release AudioTrack first" 相反；§4.5 只论证了 AAC/OPUS，而唯一真正播放该 track 的是 `AudioActivity` 的 PCM 路径 | §4.5、§8 |
| M3 | MEDIUM | `AudioPlayer.stop()` 经 `AudioTrackPlayer.stop()`（`:274`）会先 `pause()` + `flush()` 才 `stop()`，不是干净的"唤醒阻塞写入"；§4.7.5 把它当解阻塞手段，却在同一节写了 `pause()` 会让写入挂住 | §4.7.5、§6.3、§8 |
| M4 | MEDIUM | 计划要求删除 `kotlin.concurrent.thread` import，但它在 `ADPCMActivity.onEncodeToADPCMClick()`（`:46`）仍有使用点，本次不改编码路径，照做即编译失败 | §4.7.6、计划 Task 3 Step 7 |
| L1 | LOW | 计划给出的 `SuspendTeardownGateTest.kt`"完整内容"缺 `kotlin.test.assertIs` / `kotlin.test.assertNull` 两条 import（第 4 个用例要用），紧随其后的正文才补充说明，照抄即编译不过 | 计划 Task 3 Step 1 |
| L2 | LOW | `teardownSteps()` 在计划 Task 3 Step 4 以 `private suspend fun` 给出，Step 5 与 File Structure 又推荐提成同包 internal 顶层函数，两处并存；且未说明两个类分别位于 `.sender` / `.receiver` 子包、需要为它新增 import。`containsJob()` 有同样问题 | §4.7.3、计划 Task 3 Step 4/5 |
| L3 | LOW | 两个 encoder wrapper 的 `import ...runCatchingPreservingCancellation`（`:3`）下方紧跟一个空行（`:4`）；删掉该 import 会在 `package` 与首个 import 之间留下空行，零容忍的 ktlint 下应一并清掉。文档只列了"两条失效 import + TAG + companion" | §4.2、计划 Task 4 Step 2 |
| L4 | LOW | 两份文档对同一测试的行号引用约定不一致：计划引 `@Test` 行（`:53` / `:90` / `:21-51`），本文引 `fun` 行（`:54` / `:91`）。都不算错，但对照时易误判 | 计划 Task 4 Step 8（已注明约定） |

另补登记一处遗漏：`StreamPlayerStopperTest` 有**第 4 个**用例
`suspending stop rejects calls from its owned scope before side effects`（`:122`），
前七轮的测试清单里都没有它。它不受本次改动影响、不得删除，且正是 H1 守卫的现成模板（见 §6.2）。

### 17.2 §16.1 "全部命中"为何不成立

这不是措辞问题，而是核对范围的问题。§16.1 核的是**文档对源码的引用**（行号、计数、符号名），
这部分确实几乎全中。它没有核的是**文档自己新写的方案代码**是否与所引源码一致——H1 就诞生在
第七次复审自己新增的那段"照抄 `StreamPlayerStopper`"里，当轮既没人回头对照 `StreamPlayerStopper`
的真实实现，也没人注意到仓库里已有一个专测该守卫的用例。M1 则说明连计数类声明也不是零错误。

**给后续复审的操作性结论**：凡是正文里出现"照抄 / 与 X 一致 / 同 X 的写法"，必须把 X 的源码
贴到旁边逐行比对，不能只核 X 的行号存在。

### 17.3 本轮重新核对后确认无误

以下在本轮被重新验证过，可以继续信任：

- §1 的 8 处弃用调用；`AacStreamPlayer:154-159` / `OpusStreamPlayer:147-154` / `:213-225` /
  `:207-219` / `BaseMediaCodec:226-246` 的删除范围与所引代码块**逐字相符**
- `AacEncoderWrapper:44-47` 与 `OpusEncoderWrapper:46` 的 `"Opus encoder release failed"`；
  `TAG` / `LogContext` / `runCatchingPreservingCancellation` 的唯一使用点确实都在被删函数内
  → companion 必须整体删除（§4.2 成立）
- detekt `EmptyClassBlock`（`10-configs/detekt.yml:203`）、
  `EmptyFunctionBlock ignoreOverridden: true`（`:215-217`）、`NestedBlockDepth threshold: 8`（`:148`）
- coroutines `1.10.2`（`gradle/libs.versions.toml:32`）、`.editorconfig` 的
  `max_line_length = 100`、`CHANGELOG.md:58` 的 `### 变更 (Changed)`
- demo 的 `test` bundle 含 `kotlin-junit5` 与 `test-coroutines`
  （`libs.versions.toml:463-472` + `demo/build.gradle.kts:422`）→ 新增单测可编译运行；
  同包已有 `demo/src/test/.../audio/PcmFramePaddingTest.kt`
- 测试侧 18 + 1 + 1 处调用点行号、19 + 1 处 `@Suppress("DEPRECATION")`、main 侧 13 处
- wrapper 实现只有 3 个 encoder（Aac / Opus / CompressedPcm）+ 1 个 decoder
  （`CompressedPcmDecoderWrapper` 确为唯一实现）；无 `BaseMediaCodec.release()` 的子类覆写
- §4.5 的"三个 `AudioTrackPlayer` 独立"与"`decoderWrapper` 与 stream player 互斥"
  （按 `AudioPlayer.init` `:46-84` 与 `play()` `:86-113` 逐分支复核）
- `AudioTrackPlayer.stop()` 在 `playState == PLAYSTATE_STOPPED` 处早返回（`:271`）
  → §4.7.3/§4.7.4 "代价为零"成立
- `BaseNettyClient` 的 `:99`（`CONNECTION_TIMEOUT_IN_MILLS = 30_000`）、`:342`、
  `:399-402`、`:421`
- §6.2 迁移的可行性：`releaseAndJoin()`（`:160-179`）经 `releaseCodecOnce()` →
  `withCodecOperationLock`（`:277-278`，阻塞 `ReentrantLock.withLock`），
  因此"release 等待活跃回调"的 100 ms 超时断言迁移后依然成立；`runBlocking` 的 Job
  也确实过得了 `:161` 的 `require`

### 17.4 本轮未改动的结论

§2 的两个既定前提、§3.2 对 `Mutex` 方案的否决、§3.3 的"关闭确定性 / 启动回滚可异步"原则、
§3.4 的 `NonCancellable` 支点、§4.6 的 `MicRecorder` 两处回滚方案与 `releaseCompleted` 屏障位置、
§4.7.1 门闩本身的取消处理（第七次复审 M2）、§7 的四批次划分，均经复核后维持不变。

## 18. 第九次复审修正（2026-09-20）

源码基准仍是 `9eec9807f`（其后的 `fd3cd48b7`、`d3058e2bc` 只改这两份文档，源码一行未动）。
本轮做两件事：**复核第八次复审的结论**，以及**沿它自己提出的"关闭之后还有谁会建出新资源"
这条线索再走一步**。第二件事找到了两个 HIGH，它们恰好使 §17 的 H2 修复落空。

### 18.1 第八次复审的结论：复核后全部成立

| §17 条目 | 复核结果 |
|------|---------|
| H1 身份比较的 `require` 是死代码 | **成立**。`ioScope.launch{}` 产生子 Job，`!==` 恒真；`StreamPlayerStopperTest.kt:121-139` 正是用 `scope.launch(start = UNDISPATCHED)` 的子协程验证这个守卫，换成身份比较该测试必挂。§4.7.3 的更正框与 `containsJob` 写法正确 |
| H2 `doRetry()` 会在 teardown 期间复活会话 | **机制属实**，引用的 `:751`、`:756`、`:352-362`、`:359`、`:361` 全部精确。`doRetry()` 确实跑在 `BaseNettyClient` 自己的 `retryScope`（`:779`）上，`AudioSender.ioScope` 的 `cancelAndJoin()` 管不着 |
| M1 helper 只覆盖 2 个用例 | **成立**。实测调用点只有 `:316`、`:364`；`:44` 用例走自己的 `:62` |
| M2 顺序反转需单独论证 PCM | **成立**，新增论证正确（`AudioDecoderManager.kt:18` 确为 `PCM -> null`） |
| M3 `stop()` 先 `pause()`+`flush()` | **成立**（`:270-283` / `:246-257` / `:280`），"结论不变 + 加 logcat 观察项"的处理恰当 |
| M4 `kotlin.concurrent.thread` 在 `:46` 仍有使用点 | **成立**，是原计划的错误 |
| L1–L4 | **全部成立**。L3 尤其细：两个 wrapper 的 `:3` import 下方紧跟空行（`:4`） |

**一处可以写得更强**：§17 在论证 H2 时只提到 `doRetry()` 会在 `:756` 的
`if (released || disconnectManually) return` 处早退。实际上 `disconnectManually()` 的效果更直接
——它在 `:627` 调用 `stopRetryHandler()`，**主动取消已经挂起的 `retryJob`**，不只是让下一次重试
自行退出。而且 `handleConnectFailure()` 在 `:358` 先把状态置为 `FAILED`，不落在
`disconnectManually()` `:606-616` 的早退集合里，所以该调用确实会走到 `:623-624` 置位。
修复方向正确，且比文档写的更有力。

### 18.2 本轮修正的问题

| 编号 | 级别 | 问题 | 修正位置 |
|------|------|------|---------|
| H1 ✅**已实施** | HIGH | `AudioReceiver.receiverServer` 在服务器正常运行时恒为 `null`（`also { ... it.startServer() }` 在阻塞调用之后才赋值，而 `BaseNettyServer.startServer()` 停在 `closeFuture().sync()` 不返回）。`stopServer()` 的最后一步是死代码，netty server 从不停止 | §4.7.4（事实 + 赋值提前的修法）、§6.3、§8。**2026-09-20 已连同根因一起修复**：`startServer()` 改 suspend 且不再阻塞，`receiverServer` 赋值提前。见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.5 |
| H2 ✅**已实施** | HIGH | `AudioSender.senderClient` 在首次连接窗口内同样为 `null`，恰好使 §17 的"`disconnectManually()` 提为第一步"在它要修的场景里空转；第 7 步 `release()` 一并落空，EventLoopGroup 不 shutdown。**2026-09-20 已按修法把赋值提到 `connect()` 之前**（`00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.7）；`stop()` 本体仍待 Task 3 Step 4 | §4.7.3（更正框 + 修法）、§6.3、§8 |
| L1 | LOW | §4.5 的"PCM 下 `try` 块全是 no-op"可以写得更强：`getWrapper()` 四个分支里只有 `COMPRESSED_PCM` 非空，且它的释放是空体，因此 `decoderWrapper?.releaseAndJoin()` 在**任何** `AudioType` 下都不触碰 AudioTrack | §4.5 |
| L2 | LOW | §16.1 标题"全部命中"虽已被 §17 加注更正，标题本身仍然过强 | §16.1（标题订正） |

### 18.3 两个 HIGH 的共同根因与统一修法

两处是同一个写法造成的：

```kotlin
field = Ctor(...).also { ...; 阻塞调用() }   // 赋值发生在块执行完之后
```

`AudioSender.start()`（`:85-92`，阻塞调用是最长 30 s 的 `connect()`）与
`AudioReceiver.startServer()`（`:191-195`，阻塞调用是永不返回的 `startServer()`）都是这个形状。
结果是：**teardown 需要这两个字段的那个时刻，它们恰恰还没被赋值。**

统一修法是把赋值提到阻塞调用之前（两节各给了完整片段）。这属于本次范围，不是扩大范围——
§4.7.3 与 §4.7.4 的关闭协议分别以 `senderClient` 与 `receiverServer` 为首/末步骤，不修它们，
这两条协议就不成立。同时给 §6.3 增加了两条**改前必然失败、改后才能通过**的真机项，
用来确认修法确实生效。

> **实施进度（2026-09-20）：H1、H2 的赋值时机均已实施。**
>
> | | 状态 | 说明 |
> |---|---|---|
> | **H1** `receiverServer` | ✅ **已实施** | 连同根因一起修掉：`BaseNettyServer.startServer()` 改 `suspend` 且不再阻塞到服务器关闭，`AudioReceiver.startServer()` 随之改 `suspend` 并把赋值提前。见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.5 |
> | **H2** `senderClient` | ✅ **已实施** | 赋值提到 `connect()` 之前，`also { }` 出口赋值已去掉。见同文档 §4.7 |
>
> **`AudioSender.stop()` 也已在同批改写**（见该文档 §4.8）：改为 `suspend`，去掉
> `ioScope.launch { }` + `ioScope.cancel()` 那对组合（释放协程此前在派发前就被取消，
> **两个 suspend 释放调用一次都没执行过**），并按 §4.7.3 的顺序重排。赋值提前是必要条件，
> 不是充分条件——两处都改，H2 的收益才真正可见。
>
> §6.3 的两条真机项**现在都可以且应当验证**：H1 对应"server 确实停止"，
> H2 + `stop()` 重写对应"重试链确实终止"。
>
> **当时仍未实施（已于 2026-10-14 随 Task 2/3 全部落地，见 §20）**：`SuspendTeardownGate`、
> `teardownSteps()`、`stopping` 标志、`containsJob()` 自调用守卫、两个队列改 `Channel`、
> `AudioPlayer.releaseAndJoin()`。在那之前并发的两次 `stop()` 仍可能同时进入，
> 且任一步抛出会跳过其后全部步骤。

### 18.4 本轮未改动的结论

§2 的两个既定前提、§3.2 / §3.3 / §3.4、§4.2 的 companion 删除、§4.6 的 `MicRecorder` 两处回滚、
§4.7.1 的门闩契约（含第七次复审的取消处理与第八次复审的 `stopping` 标志）、§4.7.5 / §4.7.6、
§6.2 的逐用例迁移、§7 的四批次划分，均经复核后维持不变。

## 19. 第十次复审修正（2026-09-20）

源码基准仍是 `9eec9807f`。本轮的着力点是 §17.2 给出的那条操作性结论——**"凡是正文里出现
'照抄 / 与 X 一致 / 同 X 的写法'，必须把 X 的源码贴到旁边逐行比对"**——并把它推广了一步：
不只比对"照抄"声明，而是把两份文档里**每一段方案代码**当作即将进入 `staticCheck` 的真实
Kotlin 来读，同时问三个问题：

1. 它能过 detekt / ktlint 吗（不是"应该能"，而是去 `10-configs/detekt.yml` 和 `.editorconfig`
   里查具体规则）？
2. 它所使用的每一个工具（`teardownSteps()`、门闩、`containsJob()`），**在它自己被使用的这一处**
   是否还在兑现当初被引入时的那条理由？
3. 失败路径上，谁会看见这次失败？

第 2 问找到了本轮唯一的 HIGH，第 3 问找到了 M2。

### 19.1 第九次复审的结论：复核后全部成立

| §18 条目 | 复核结果 |
|------|---------|
| H1 `receiverServer` 恒为 null | **成立，引用全部精确**。`AudioReceiver.kt:191-195` 确为 `also { ... it.startServer() }`；`AudioReceiverWebSocket.kt` 整文件 21 行、只覆写 `getTagName()`（`:16`）与 `addLastToPipeline()`（`:18`），**没有**覆写 `startServer()`；`BaseNettyServer.startServer()` 在 `:129`，`:136` bind、`:142` `serverChannel.closeFuture().sync()`。`stopServer()` 在 `:175`，其日志文案 `:176` `===== stopServer() ... =====` 与 `:209` `=====> Server released <=====` 与 §6.3 的验证项逐字一致 |
| H2 `senderClient` 在首次连接窗口内为 null | **成立**。`AudioSender.kt:85-92` 形状一致；`BaseNettyClient` 的 `:99`（`CONNECTION_TIMEOUT_IN_MILLS = 30_000`）、`:342`、`:352`、`:358`、`:359`、`:361`、`:399-402`、`:421`、`:606`、`:623-624`、`:627`、`:751`、`:756`、`:779` **逐行核对全部精确** |
| H1/H2 的修法（赋值提到阻塞调用之前）是否充分 | **充分**。追问过一个反例："协程尚未被调度就被第 5 步 `cancelAndJoin()` 取消，赋值不还是不会发生吗？"——答案是这种情况下 `AudioSenderWebSocket` 同样从未被构造，没有需要释放的对象；而构造与赋值之间**没有挂起点**，协程取消只在挂起点生效，因此不存在"已构造但未赋值"的中间态。`AudioReceiver` 同理，且它的 `startServer()` 跑在 **Activity 的 ioScope** 上、不是 `AudioReceiver.ioScope` 的后代，第 2 步的 `cancelAndJoin()` 不会等它，无死锁 |
| L1 `decoderWrapper?.releaseAndJoin()` 在任何 `AudioType` 下都不触碰 track | **成立**。`AudioDecoderManager.kt:18-21` 四分支确为 `PCM/AAC/OPUS -> null`、`COMPRESSED_PCM -> CompressedPcmDecoderWrapper`，后者 `release()` 为空体（`:20-21`）。另核实：`COMPRESSED_PCM` 在 `audio` / `demo` 全仓**没有任何使用点**（只出现在两个 manager 的 `when` 分支里），因此 §4.5 那条"顺序反转"论证不存在未覆盖的分支 |
| L2 §16.1 标题过强 | 成立，已订正 |

### 19.2 本轮修正的问题

| 编号 | 级别 | 问题 | 修正位置 |
|------|------|------|---------|
| H1 | HIGH | `audioPlayer?.stop()` 与 `ownerJob?.cancelAndJoin()` 被写进**同一个** `teardownSteps` lambda，使两者共用一个 `try`：前者抛出即跳过 join，而下一步照常 `releaseAndJoin()` ⇒ worker 与 release 并发。这恰好取消了 `teardownSteps()` 被引入的唯一理由，也重新打开了本方案花最大篇幅要堵的那个竞态。今天 `AudioTrackPlayer.stop()`（`:276-282`）不抛，但这一步的存在理由本就是"PCM 那天" | §4.7.3（更正框）、§4.7.4、§8、计划 Task 3 Step 4/5 |
| M1 | MEDIUM | 门闩在 `catch (error: Throwable)` 内用 `error !is CancellationException` 判型，与仓库既有的双 catch 子句写法（`StreamPlayerStopper.kt:44-45`、`AudioActivity.kt:391-393`）相反，且正落在 detekt `InstanceOfCheckForException`（`10-configs/detekt.yml:242`，`active: true`，只 exclude 测试目录）的形状上。**当前不会失败**——根 `build.gradle.kts` 的 `detekt { }` 未配置 `classpath`，任务跑在无 type resolution 模式，该规则的子类型判定拿不到 `BindingContext.TYPE`；但这是运气而非设计 | §4.7.1、§8 |
| M2 | MEDIUM | §4.7.1 论证了"取消不广播给 follower 否则会被静默吞掉"，却漏了**leader 侧同样会吞掉**：`launchCleanup` 对取消直接 rethrow 且不记日志，`cleanupScope` 的 handler 也不接收取消。而关闭体跑在 `NonCancellable` 下，此处的 `CancellationException` 不是"调用方被取消"而是一次真实的关闭失败。结果是整条链上没有任何痕迹 | §4.7.1（取消分支加日志）、§8 |
| L1 | LOW | §4.7.3 第九次复审更正框里的 `val client = AudioSenderWebSocket(...)` 单行长 **101** 字符（含 12 空格缩进），超过 `.editorconfig` 的 `max_line_length = 100` 与 detekt `MaxLineLength`；计划 Task 3 Step 4 的同一段已换行（92 字符），两处不一致，照抄 spec 版即 ktlint 失败。§16.1 的"所有 Kotlin 代码块无一行超过 100 字符"是第七次复审的结论，第九次新增的代码块未重跑该检查 | §4.7.3 |
| L2 | LOW | §4.7.6 / 计划 Task 3 Step 7 的删除清单只列了字段本身与两处 release（`:115`、`:121`），漏了 **`ADPCMActivity.kt:88` 的 `player = AudioPlayer(...)`**（在 `thread { }` 块之外）。编译器会挡住漏删，但清单不完整易诱导实施者把它改成 Job 体外的 `val` | §4.7.6、计划 Task 3 Step 7 |
| L3 | LOW | §4.7.4 的关闭步骤表没有与 §4.7.3 第 3 步（关闭两个 Channel）对等的一步，也没有一句说明为何不需要，读起来像漏写 | §4.7.4 |
| L4 | LOW | §4.7.3 的文字清单是 7 步，计划 Task 3 Step 4 的代码模板只有 6 个 lambda（第 4、5 步曾被合并），而 §18.2 与计划卷首又按 7 步编号引用"第 7 步 `release()`"。H1 拆开之后两者恢复一致 | §4.7.3、计划 Task 3 Step 4 |

### 19.3 本轮另外重新核对、确认无误的事实

- `StreamPlayerStopper.kt` 的守卫写法：`require` 在 `:36-38`、`containsJob` 在 `:55-56`、
  `withContext(Dispatchers.IO + NonCancellable)` 在 `:39`——**守卫在 `withContext` 之前**，
  因此 §4.7.3 / §4.7.4 把 `require` 放在 `teardownGate.run { }` **之前**是对的：门闩内部是
  `withContext(NonCancellable)`，进去之后 `currentCoroutineContext()[Job]` 已经不是调用者的 Job
- `StreamPlayerStopperTest` 确有 4 个用例，`@Test` 在 `:21` / `:53` / `:90` / `:121`，
  `fun` 在 `:22` / `:54` / `:91` / `:122`——§17.1 L4 记录的两份文档行号约定差 1，**成立**
- 三条 `require` 守卫互不误伤：`launchCleanup { audioSender?.stop() }` 跑在 **Activity** 的
  `ioScope` 上、netty 回调走 **`cleanupScope`**，两者都不是 `AudioSender.ioScope` 的后代；
  `MicRecorder.stopRecordAndJoin()` 的 `require(job !== ...)`（`:386`）比较的是 `recordJob`，
  门闩协程与它必然不同
- `AudioPlayer` 的全部行号：`audioTrackPlayer` 构造 `:43`、`init` `:46-84`、`play()` `:86`、
  PCM 分支 `:88-98`、stream 分支 `:99-108`、`stop()` `:158`、`release()` `:163-170`、
  其中 `decoderWrapper?.release()` `:167`、两处 `stopPlaying()` `:168` / `:169`
- `AudioTrackPlayer` 的全部行号：阻塞写入 `:194`、`pause()` `:246-257`（`audioTrack.pause()`
  `:253` + `flush()` `:254`）、`stop()` `:270-283`（`PLAYSTATE_STOPPED` 早返回 `:271`、
  `pause()` `:274`、`audioTrack.stop()` `:280`）、`release()` `:285-286`。
  **补一条此前未记录的事实**：`release()` 自己的第二步就是 `stop()`（`:289`），所以
  §4.7.5 的 `player?.stop()` → `join()` → `releaseAndJoin()` 序列里，第一次 `stop()` 之后
  `release()` 内部的那次会在 `:271` 的守卫处直接返回，无副作用
- `MicRecorder` 的全部行号：`ioScope` `:110`、`released` KDoc `:122`、
  `rollbackFailedInit` `:209-223`（`released.set(true)` `:212`、`encodeWrapper?.release()` `:217`、
  `releaseCompleted.complete(Unit)` `:222`）、构造失败重抛 `:184` / `:193`、
  `startRecord()` `:225` 与四个 `failRecordStart` 调用点 `:228` / `:235` / `:239` / `:243`、
  `recordJob = ioScope.launch` `:246`、`failRecordStart` `:289-295`、
  `releaseAdvancedFeatures` KDoc `:358-360`、`stopRecordAndJoin` `:380`（KDoc `:383-384`、
  `require` `:386`）、`stopRecord` `:411-418`、`finishRecorderRelease` `:437-450`
  （`encodeWrapper?.release()` `:443`）、`finishRecorderReleaseAndJoin` `:452`（重抛 `:464-466`）
- 两个 encoder wrapper 的 `:3` import 下方确有孤立空行（`:4`），`companion object` `:20`、
  `TAG` `:21`、`release()` `:44-47`、`OpusEncoderWrapper.kt:46` 确为 `"Opus encoder release failed"`
- `CHANGELOG.md:58` 的 `### 变更 (Changed)`；`demo/src/test/.../audio/` 下目前只有
  `PcmFramePaddingTest.kt`
- `AudioActivity` 的全部行号：`ioScope` `:79`、PCM player `:144`、裸线程 `:148-176`、
  `stopPcmPlayback()` `:343-356`（`interrupt()` `:351`、`launchCleanup` 块 `:352-355`、
  `join()` `:353`、`release()` `:354`）、`onStop()` 的两处 `:371` / `:372`、
  `ioScopeJob.complete()` `:383`、`launchCleanup` `:387`（取消分支 `:391-392`）、
  `onAudioSenderClick` 的 `:403`、`onAudioReceiverClick` 的 `:408`

### 19.4 本轮未改动的结论

§2 的两个既定前提、§3.2 / §3.3 / §3.4、§4.2 / §4.3 / §4.4 / §4.5 / §4.6、
§4.7.1 的门闩契约本身（单 owner、不可重置、无门闩层超时、取消不广播给 follower）、
§4.7.2 的 `cleanupScope`、§4.7.3 的 `disconnectManually()` 提为第一步与 `stopping` 标志、
§4.7.4 的赋值提前、§4.7.5 的 `stop()` → `join()` → `releaseAndJoin()` 顺序、
§6.2 的逐用例迁移、§7 的四批次划分，均经复核后维持不变。

### 19.5 给第十一次复审的操作性结论

§17.2 要求比对"照抄"声明，本轮把它扩成了下面这条，建议继续沿用：

> **每一段方案代码都要回答"它引入时的那条理由，在它现在被放置的这个位置还成立吗"。**

H1 就是这样浮出来的：`teardownSteps()` 被引入的理由白纸黑字写在同一节里（"某一步失败，
后续步骤仍然执行"），但它自己的调用点把两个动作合并成了一个 step，当场违反了那条理由。
前几轮复审都读过这段代码，却只核了"它有没有用 `teardownSteps()`"，没核"它用得对不对"。

同理，凡是文档里写着"代价为零 / 防御性 / 将来某个开关翻转就变成必需"的步骤，都要按
**那个开关已经翻转**的假设再读一遍它所在的编排——`stop()` 这一步的问题正是在这个假设下才显形的。

## 20. 代码审查修正（2026-10-14）

Task 1–4 实施完成后做了一轮四路并行静态审查（audio 模块 / demo / basenetty / 计划对齐），
以下修正已落地。**全部未经 Gradle 或真机验证。**

### 20.1 `stopping` 标志只缩窄、未关闭 check-then-act 窗口（Important）

§4.7.1 选定的 `stopping` 标志在四处都是"读标志 → 构造资源 → 赋值字段"。teardown 若恰好落在
"检查已过、字段未赋"的窗口内，会读到 `null` 并消费掉门闩，随后建成的资源永远无人释放：
`AudioReceiver.startServer()` 会绑定 10020 端口不再释放并泄漏两个 AudioTrack；
`AudioSender.start()` 会留下开着麦的 `AudioRecord`；两个 netty 回调（`onConnected()` /
`onClientConnected()`）同理。触发条件就是 §4.7.1 第 1 种情况描述的操作：点完发送立刻退出。

修正：四处在**赋值之后**再读一次 `stopping`；为真则先 `teardownGate.awaitCompletion()`
（`SuspendTeardownGate` 新增：只等待、不成为 leader、不重抛失败），再对刚建成的对象做补偿释放
（`MicRecorder.stopRecordAndJoin()` / `AudioPlayer.releaseAndJoin()` /
`BaseNettyServer.stopServer()`，三者均幂等，门闩若其实已经看到该资源也无妨）。
先等门闩再释放，是为了不与门闩自己的步骤并发——否则补偿的 `releaseAndJoin()` 可能跑在
`ownerJob.cancelAndJoin()` 之前，正是本方案花最大篇幅要堵的 worker 与 release 并发。

被 teardown 读取的字段（`micRecorder` / `audioPlayer` / `senderClient` / `receiverServer`）加
`@Volatile`：发布侧"写字段 → 读标志"，teardown 侧"写标志 → 读字段"，两侧都 volatile 才能保证
至少一侧看见对方的写入；只有 `stopping` 一个 AtomicBoolean 不构成 happens-before。

### 20.2 `SuspendTeardownGateTest` 第 2 个用例在 Gradle 测试 JVM 下必红（Important）

计划模板的 `assertSame(expected, first)` 不成立：Gradle `Test` 任务默认 `-ea`，
kotlinx.coroutines 的 DEBUG 模式随之开启，`withContext()` 异常退出时抛出的是
stack-trace-recovered 的**副本**（原异常成为其 `cause`）。改为断言类型与 message 相同，并保留
`assertSame(first, second)`——门闩的真实契约是"follower 拿到 leader 从 `run()` 抛出的那个实例"，
两种模式下都成立。另增一个用例覆盖 `awaitCompletion()`（等待中不成为 leader，leader 完成后返回）。
`TeardownSupportTest` 的 `assertSame` 不受影响：`teardownSteps()` 没有协程边界。

### 20.3 其余修正

- `ADPCMActivity`：`previousJob?.join()` 改为 `withContext(NonCancellable) { join() }` 后接
  `ensureActive()`。三连击时第二个 Job 被取消会让它的 `join()` 立刻以 CE 退出，第三个 Job 随即
  构造 player，与第一个 Job 仍在跑的 `finally` 短暂共存；非取消的 join 保住了整条链。
- `AudioActivity.onStop()`：`audioReceiver` / `audioSender` 与其余四个清理一样先置 `null`
  再调度关闭。
- `AudioPlayer.releaseAndJoin()` KDoc 补上 §4.5（第八次复审 M2）要求的 PCM 路径论证，
  以及"stream player 的 init 失败回滚是唯一不等待的 worker"。
- `MicRecorder`：挂在 `stopAudioRecord()` 上、实际描述 `finishRecorderRelease*` 的 KDoc 移到
  `finishRecorderReleaseAndJoin()` 上（既有漂移，本次删除后更易误读）。
- `StreamPlayerStopper`：日志 `"stopPlaying() done"` 改为 `"stopAndJoin() done"`。
- `AacStreamPlayer`：debug 日志的 `cal` 与 OPUS 侧一致，复用已算出的 `elapsedUs`。
- `AudioReceiver.sendRecAudioThread()` KDoc：删去"与 AudioSender 用同样的队列"（已不成立），
  改为说明保留 `ArrayBlockingQueue` 的原因（§4.7.4 的 `io.netty.channel.Channel` 同名冲突）。
- basenetty 侧的修正见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §11。其中
  `awaitCompletion()` 对已完成 future 的快速路径直接影响 Task 5 Step 7 的"server 确实停止"：
  没有它，`stopServer()` 之后再 `startServer()` 会在 `lifecycleMutex` 内永久挂起。

### 20.4 审查确认成立、未改的结论

- 两处有意偏离计划模板维持：`containsJob` KDoc 改为"后代、不含自身"（与实现及
  `StreamPlayerStopper` 私有版一致）；`AudioSender.stop()` 的两个 `Channel.close()` 拆为两个
  step（遵守第十次复审 H1）。
- `AudioSender.stop()` 把关闭 Channel 排在 `cancelAndJoin()` 之前，会让 worker 对已断开的客户端
  排空至多 64 帧；`sendAudioToServer()` 内部 `runCatching` 兜底、返回 `false`，只多几条日志，
  不改顺序。
- Task 5 真机项全部未执行。20.1 新增的补偿路径需要在"点完发送立刻退出"与"点 receiver 后在
  bind 完成前退出"两个场景下实测，已写入计划 Task 5 Step 7。

## 21. 第五轮审查修正（2026-10-15，外部）

两路独立审查：主审查者逐路径复核 + 一个通用审查代理全量扫 diff。audio / demo 侧的修正如下，
basenetty 侧的两条见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §12，完整上下文见
`00-documents/2026-10-15-audio-netty-teardown-review-package_cc.md` §I。**全部未经 Gradle 或真机验证。**

### 21.1 `AudioActivity` 的录音回调改为每次录音一个（MEDIUM，§3.4 异步回滚的连带后果）

§3.4 / §4.6 决定让 `MicRecorder.startRecord()` 的启动失败回滚变成 fire-and-forget
（`ioScope.launch(NonCancellable)`）。这条决定本身不变，但它有一个当时没有追到调用方的后果：

`AudioActivity` 把**同一个 `recordCallback` 实例**传给每一个 `MicRecorder`，而它的 `onStop` 无条件
close `pcmOs` / `aacOs` / `opusOs`，没有任何"哪个 recorder 停了"的判别；`MicRecorder.onStopNotified`
是**实例级** `AtomicBoolean`，跨实例不设防。于是：

```
sender/receiver 已占麦 → record(PCM) 的 startRecord() 失败 → 回滚丢到 Dispatchers.IO
  → 用户再点一次录音 → record() 重开流、建新 recorder
  → 旧回滚这时才落地，把刚开好的流关了
  → onRecording 的写入被 runCatching{}.onFailure 吞掉 → 空文件，界面无任何提示
```

同步版也会关流，只是关的是自己这次（失败的那次）的。**异步化把"毁掉自己"变成了"毁掉下一次"。**
CHANGELOG 已为下游写了这条行为变更的提醒，但仓库内唯一的调用方当时没跟上。

修法是消掉共享状态而非加身份判别：删除 `pcmOs` / `aacOs` / `opusOs` / `recordType` 四个字段，改为
`recordCallbackFor(type, out)` —— 每次 `record()` 打开一个流，交给这次录音自己的回调，`onStop` 只关
自己那一个。顺带修掉同一组字段在 `onRecording` 侧的别名问题（一次 PCM 停止原先会把上一轮残留的
aac 流也关掉）。

### 21.2 `AudioReceiver.startServer()` 的补偿路径（MEDIUM，§20.1 的遗漏）

§20.1 给四处发布点都加了"赋值后 re-check `stopping`"，但 `AudioReceiver.startServer()` 这一处的补偿
是**内联**跑在调用方协程上的，另外三处（`AudioSender` 两处 + `AudioReceiver.onClientConnected()`）都
丢进了永不取消的 `cleanupScope`。两个问题：

1. 内联意味着补偿可被调用方的取消掀掉 —— `awaitCompletion()` 正等着一个仍在跑的 teardown，而调用方
   是 Activity 的 scope。今天不出事只因为 `AudioActivity.onDestroy()` 用的是 `ioScopeJob.complete()`
   而不是 `cancel()`（已核：全文件无 `cancel()`）。这是一条**没写进 §4.7 的隐式依赖**：换成
   `lifecycleScope` 之类可取消的 scope，10020 端口和 AudioTrack 就一起留到进程死。
2. `server.stopServer()` 与 `player.releaseAndJoin()` 三步裸跑，前者抛出就跳过后者 —— 违反本设计
   §4.7.2 自己立的"一步失败不得跳过后续步骤"。

两处都改：移到 `cleanupScope.launch { }`，内部走 `teardownSteps(...)`。代价是 `startServer()` 不再
等待补偿完成，而调用方并不需要这个等待。

**2026-10-16 补正**：上面的 re-check 只在 `server.startServer()` 正常返回后执行。`startServer()` 唯一会
逃出的异常是调用方的 `CancellationException`，而被取消的调用方同样已经发布了两个字段——这正是第 1 点
想消掉的依赖，却还留着一条。现把 re-check 放进 `try { server.startServer() } finally { ... }`；
`finally` 里只有 `stopping.get()` 与 `cleanupScope.launch`，都不挂起，已取消的协程也能把补偿交出去。

### 21.3 `teardownSteps()` 的自抑制守卫（LOW）

`failure?.addSuppressed(e)` 在两步抛出**同一个实例**时抛 `IllegalArgumentException: Self-suppression
not permitted`，该异常从 catch 里逃出、跳过剩余所有步骤 —— 正是这个 helper 存在的理由被它自己违反。
今天不可达（`MicRecorder.releaseCompleted` 是 `CompletableDeferred<Unit>`，不向多个 awaiter 转发同一个
Throwable 实例；`AudioPlayer.releaseAndJoin()` 不缓存异常），按防御性修复处理，并新增用例
`two steps reporting one instance do not abort the run`（去掉守卫就会红）。

### 21.4 `SuspendTeardownGate` 的 KDoc 自相矛盾（LOW，只改文档不改行为）

KDoc 说 cancellation "belongs to the leader's own coroutine"；catch 块的注释正好反驳它，而**注释那个
才是对的**（body 在 `NonCancellable` 下，逃出来的 CancellationException 是真实的关闭失败）。行为由
`SuspendTeardownGateTest` 第 5 例锁定，刻意如此，只改 KDoc。同时补上两边都没写的后果：
`completed.complete(failure)` 在 cancellation 分支传的仍是 `null`，所以**关闭真失败时 follower 观察到
的是成功**，`AudioActivity.launchCleanup` 会记一条成功日志，leader 那条 error 日志是唯一记录。

### 21.5 两处注释理由失准（只改注释）

- `AudioSender.stop()` 第 1 步原注释说 `disconnectManually()` "sets the flag doRetry() bails out on"。
  在状态尚未到 `CONNECTING` 时它在 `UNINITIALIZED` 守卫处直接返回、什么都不设 —— 而这恰好就是
  "teardown 撞上正在 start"的窗口。真正终止重试链的是 `stopping`（挡回 `onConnected()`）加最后一步
  `release()` → `stopRetryHandler()`。顺带核清：`connect()` 的状态检查与 `disconnectManually()` 用的是
  同一个 `synchronized(this)`，所以"`connect()` 里的 `disconnectManually = false` 覆盖刚设上的标志"
  这条竞态不存在。
- `AudioReceiver.sendRecAudioThread()` 的 KDoc 说保留 `ArrayBlockingQueue` 是因为与
  `io.netty.channel.Channel` 命名冲突 —— import 别名就能解决，站不住。改为承认这是迁移顺序差异，
  并写明真正让两者在关闭语义上等价的是 `runInterruptible`。

### 21.6 本轮新增的未验证项

- 21.1：真机需验"录音启动失败后再次录音，第二次文件非空"（已写入计划 Task 5 Step 7）。
- 21.2：真机需验"receiver 启动中退出"端口与 AudioTrack 都释放 —— 该场景 §20.1 已列入，本轮只是让它
  不再依赖调用方 scope 的存活。
- 21.3 有新单测；21.4–21.5 只动注释与文档。
