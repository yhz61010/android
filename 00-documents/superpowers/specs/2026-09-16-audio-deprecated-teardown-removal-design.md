# audio 模块弃用关闭 API 彻底移除设计（2026-09-16）

## 1. 背景与目标

`audio` 模块此前一轮"改为确定性一次性会话"的重构，给实现类补上了挂起版本的确定性关闭
（`releaseAndJoin()` / `stopPlayingAndJoin()`），并把对应的非挂起版本标注为 `@Deprecated`。
但迁移停在了实现层，接口层没跟上，模块内部也仍有 8 处调用弃用 API，编译时产生 8 条警告：

```
AudioPlayer.kt:168:26          aacStreamPlayer?.stopPlaying()
AudioPlayer.kt:169:27          opusStreamPlayer?.stopPlaying()
AacStreamPlayer.kt:154:63      audioDecoder?.release()
AacStreamPlayer.kt:223:39      it.release()
AacEncoderWrapper.kt:45:53     encoder.release()
OpusEncoderWrapper.kt:45:53    encoder.release()
OpusStreamPlayer.kt:147:63     audioDecoder?.release()
OpusStreamPlayer.kt:217:39     it.release()
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

删除后检查 `runCatchingPreservingCancellation` 与 `LogContext` 的 import 是否还有其他使用点；
若失效必须一并删除（detekt `maxIssues = 0`，未使用 import 会直接失败）。

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

删除整个弃用的 `open fun release()`（约 `:229-245`）：

```kotlin
// 删除
@Deprecated(
    "Non-suspend release cannot guarantee the worker has exited. " +
        "Use releaseAndJoin() for deterministic shutdown.",
    ReplaceWith("releaseAndJoin()")
)
open fun release() {
    if (!markReleasing()) return
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

**删除弃用入口**（`AacStreamPlayer.kt:212-224` / `OpusStreamPlayer.kt:206-218`，含其上方的 KDoc）：

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

**改造初始化失败回滚**（`AacStreamPlayer.kt:153-158` / `OpusStreamPlayer.kt:146-152`）：

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
    decoderWrapper?.releaseAndJoin()
    aacStreamPlayer?.stopPlayingAndJoin()
    opusStreamPlayer?.stopPlayingAndJoin()
    audioTrackPlayer.release()
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

关于 `AudioReceiver.kt:183-190` 那段 SIGABRT 注释（`releaseBuffer() track ... disabled due to
previous underrun`）：它描述的是 `AudioPlayer` **自己那个** track 在历史 PCM 配置下的问题。当前
`AudioReceiver.defaultAudioType`（`:31`）是 `AudioType.OPUS`，该 track 从不启动，也就不可能
underrun。注释保留作为历史记录即可，措辞上应注明它约束的是 PCM 路径。

### 4.6 `MicRecorder`

删除非挂起释放路径：

- `fun stopRecord()`（`:251`）→ 删除。调用方只有 `AudioSender:136` 与 `AudioReceiver:193`，
  两者本就要按 4.7 改为挂起
- `private fun finishRecorderRelease(stopSucceeded: Boolean)`（`:277`）→ 删除

保留 `suspend fun stopRecordAndJoin()`（`:220`）与
`private suspend fun finishRecorderReleaseAndJoin()`（`:291`）。

**`failRecordStart()` 的处理（`:187-194`）**

`:192` 对 `finishRecorderRelease()` 的调用位于 `private fun failRecordStart(message: String)`，
而后者由**非挂起的公开入口 `fun startRecord()`（`:123`）**在四处调用（`:126`、`:133`、`:137`、
`:141`）。把它改成挂起会把 `startRecord()` 一并拖成 suspend，扩大破坏面到启动路径。

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
的四个调用点（`:126`、`:133`、`:137`、`:141`）全部位于 `recordJob = ioScope.launch { }`
（`:144`）之前，此刻 `ioScope` 还没有任何子协程，先取消还是后取消没有可观察差别。之所以要挪，
是为了让上面那个 `launch` 确实排进调度——虽然 `NonCancellable` 已经保证它不会被取消，但把
`cancel()` 放在最后读起来意图更清楚。`startRecord()` 维持非挂起，公开面不变。

**为什么必须包住异常**（复审补充）：`MicRecorder.ioScope`（`:55`）是
`CoroutineScope(Dispatchers.IO)`，**没有 `CoroutineExceptionHandler`**。而 3.4 的 `NonCancellable`
恰恰把父 Job 换掉了，于是逃逸异常既没有父协程可以传播，也没有 handler 可以兜住，直接走平台默认
处理器 → 进程崩溃。`finishRecorderReleaseAndJoin` 在编码器分支会重抛 `CancellationException`，
`completeRecorderRelease` 还会回调客户端代码，都可能抛。4.4 的 stream player 回滚片段本来就包了
`runCatchingPreservingCancellation`，此处必须同样处理。

同一条约束适用于 4.4：`AacStreamPlayer.ioScope`（`:39`）与 `OpusStreamPlayer.ioScope`（`:45`）
都是 `CoroutineScope(Dispatchers.IO + Job())`，同样没有 handler。

**一个容易误判的细节**（第二次复审补充）：`runCatchingPreservingCancellation`
（`RunCatchingExt.kt:8-9`）会**重抛** `CancellationException`，而
`finishRecorderReleaseAndJoin` 的编码器分支（`:302-304`）恰好会重抛它——看上去像是上面这层包裹
漏了一条逃逸路径。实际不会崩：`launch(NonCancellable)` 的 `parentHandle === NonDisposableHandle`，
`JobSupport.cancelParent()` 对 `CancellationException` 返回 `true`，不会走到
`handleJobException()`，异常被静默丢弃。**只有非取消异常才会打到平台默认处理器**，而那些正是
`.onFailure { }` 捕获的。结论不变，写在这里是为了避免后续维护者误以为此处有洞而"补"出问题。

`:67` 的 KDoc 注释 "Guards [finishRecorderRelease] so ..." 引用了被删除的函数，必须同步改为引用
`finishRecorderReleaseAndJoin`，否则 KDoc 链接失效。

### 4.7 demo 调用方

#### 4.7.1 共享的一次性关闭门闩

`AudioSender.stop()` / `AudioReceiver.stopServer()` 各自同时可由 Activity 和 netty 回调发起。
挂起关闭增加了交错点，不能继续依赖 `AudioTrack.state` 的 check-then-act 或
`runCatching` 当作并发释放保护；后者也无法阻止 native 层的重复 release。

在 demo 音频包下新增 `SuspendTeardownGate`，两个类各持有一个实例：

```kotlin
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
关闭体，只等待同一个结果；首个调用失败时，所有等待者都观察到同一失败。必须用单元测试
覆盖“并发调用只执行一次”、“后续调用等待完成”和“失败向等待者传播”。

#### 4.7.2 netty 回调的清理 scope

四个 netty 回调都是非挂起接口，因此 `AudioSender` / `AudioReceiver` 仍各需一个
`SupervisorJob + Dispatchers.IO + CoroutineExceptionHandler` 的 `cleanupScope`，回调中使用
`cleanupScope.launch { stop() }` / `cleanupScope.launch { stopServer() }`。`cleanupScope` 不参与被清理的
`ioScope` Job，异常由 handler 记录。一次性门闩保证 netty 回调与 Activity 同时发起时
不会重复释放。

#### 4.7.3 `AudioSender`：可取消队列与可等待关闭

`sendRecAudioThread()` 和 `startPlayThread()` 现在都会阻塞在 `ArrayBlockingQueue.take()`，
`ioScope.cancel()` 不会中断这种 Java 阻塞。两个 worker 因此会永久占用 `Dispatchers.IO`
线程并持有 `AudioSender`。本次必须同步修复，不再将它列为范围外问题。

两个 `ArrayBlockingQueue<ByteArray>` 分别改为容量相同的 `Channel<ByteArray>`；普通回调用
`trySend()` 保留“队列满时丢当前帧”的既有非阻塞语义，消费协程用 `for (data in channel)`
或 `receive()`。原 `queue.size` 调试日志改为记录 `trySend` 成功/失败，不引入额外计数器。
关闭顺序改为：

1. `micRecorder?.stopRecordAndJoin()`，先停止新的录音帧。
2. 关闭两个 Channel，使网络迟到回调的 `trySend()` 失败，不在消费者退出后滞留帧。
3. `ioScope.coroutineContext[Job]?.cancelAndJoin()`，等两个 Channel worker 退出。
4. `audioPlayer?.releaseAndJoin()`，此时已无人再调用 `play()`。
5. 顺序执行 `senderClient.disconnectManually()` 与 `release()`。

整个顺序放在 `teardownGate.run { ... }` 内。这同时修复旧实现中
`ioScope.launch { client release }` 紧跟 `ioScope.cancel()` 导致清理尚未开始就被取消的竞态。
每一步用嵌套 `try/finally` 串联，保证某一资源释放失败时仍会尝试后续资源；最终失败
仍通过门闩传播给所有等待者。

#### 4.7.4 `AudioReceiver`：同一关闭协议

`AudioReceiver.stopServer()` 保持原名，只改为 suspend，并放入自己的
`teardownGate.run { ... }`。关闭顺序为：先 `micRecorder.stopRecordAndJoin()`，再对可取消的
poll/delay worker 执行 `cancelAndJoin()`，然后 `audioPlayer.releaseAndJoin()`，最后停止 server。
同样使用嵌套 `try/finally` 保证后续资源总会被尝试。这保证 PCM / COMPRESSED_PCM 路径
不会在 `AudioTrack.write()` 尚未退出时 release track。

#### 4.7.5 `AudioActivity`

`AudioActivity` 继续使用已有的 `launchCleanup` + `ioScopeJob.complete()`，不新增
`lifecycleScope.launch(NonCancellable)`。PCM 播放的 `player.release()` 换为
`player.releaseAndJoin()`；`audioReceiver.stopServer()` 和 `audioSender.stop()` 调用结构不变。

#### 4.7.6 `ADPCMActivity`：播放 Job 拥有 player

不再让裸 `thread` 和 `onDestroy()` 通过共享可变 `player` 竞争释放。删除
`private var player: AudioPlayer?`，改为 `private var playbackJob: Job?`：

- 点击播放时先取消上一个 Job，再用 `lifecycleScope.launch(Dispatchers.IO)` 启动新 Job。
- `AudioPlayer` 在该 Job 内构造，避免 Job 尚未启动就被取消时泄漏已构造的 player。
- 解码循环每帧前调用 `ensureActive()`，且只访问该 Job 捕获的局部 player。
- Job 的 `finally` 使用 `withContext(NonCancellable) { player.releaseAndJoin() }`，保证先退出
  播放循环，再由同一所有者释放。
- `onDestroy()` 只取消 `playbackJob`；不直接释放 player，也不启动脱离生命周期的根协程。

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

**但"不用新写"不等于"现有测试不受影响"**（第二次复审补充）。本文档早期版本只说"现有用例集中在
codec 本身"，没有核对它们是否调用了即将被删的 API。实际调用量很大，不处理会让
`:audio:testDebugUnitTest` **编译失败**：

| 文件 | 依赖被删 API 的位置 |
|------|--------------------|
| `BaseMediaCodecAsynchronousTest.kt` | 18 处 `subject.release()`（`:35 :62 :91 :108 :124 :153 :182 :200 :234 :255 :284 :303 :304 :387 :405 :419 :448 :500`），其中 `:500` 在私有辅助函数 `assertReleaseWaitsForCallback()` 内，被 3 个用例共用 |
| `BaseMediaCodecSynchronousTest.kt` | `:38` `subject.release()` |
| `StreamPlayerStopperTest.kt` | `:34` `subject.stop { }` |

处理原则：

1. **纯作收尾用的 `subject.release()`** → 非挂起用例包 `runBlocking { subject.releaseAndJoin() }`，
   已经是 `runTest` 的直接改调用。`releaseAndJoin()` 的
   `require(codecJob !== currentCoroutineContext()[Job])` 不会误伤：`runBlocking` 建的是另一个 Job。
2. **专门测非挂起语义、但意图在挂起版仍成立的** → 迁移并改名，因为 `releaseAndJoin()` 同样要过
   `releaseCodecOnce()` 的 `withCodecOperationLock`，"等待活跃回调/迭代结束"的断言依然有效：
   - `async:44 release waits for an active asynchronous input callback`
   - `async:430 release racing start cleans partial codec and keeps session terminal`
   - `async:415 release before start makes the one-shot session terminal`
   - `sync:24 legacy release waits for the active codec iteration` → 去掉 `legacy`
   - `assertReleaseWaitsForCallback()` 辅助函数（一处改动覆盖 3 个用例）
3. **前提随 API 一起消失的** → 重写或删除，并在此处记录放弃了什么：
   - `async:296 successful release remains exactly once across both release APIs`——"两个 API"的
     前提没了。改为连续调用三次 `releaseAndJoin()` 并断言 `mediaCodec.release()` 恰好一次，
     更名为 `... across repeated calls`。幂等性覆盖保留。
   - `StreamPlayerStopperTest:22 legacy stop releases resources in teardown order`——被测函数
     `StreamPlayerStopper.stop()` 整体删除，**此用例删除**。teardown 顺序的覆盖不丢：
     `suspending stop propagates cancellation after common cleanup`（`:54`）与
     `suspending stop logs ordinary decoder failures without throwing`（`:91`）断言的是同一个
     `events` 序列。
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
- **netty 回调触发的关闭**（第二次复审新增）：`cleanupScope` 只在这条路径上生效，Activity 侧
  走不到。从对端断开连接让 `onDisconnected()` / `onClientDisconnected()` 触发关闭，再用不可达
  地址让 `onFailed()` / `onStartFailed()` 触发关闭。确认停止序列正常、不出现
  `Audio sender/receiver teardown failed`、进程不崩，且随后 Activity 退出时的第二次关闭会等待
  同一 teardown，不会再次调用 AudioTrack/codec/netty release
- `AudioSender` 关闭后确认两个 Channel worker 都已退出；重复进出 Activity 后不应累积
  `DefaultDispatcher-worker-*` 阻塞线程
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
| ~~`BaseMediaCodec.release()` 是 `open`，可能有子类覆写~~ | **已排除**：`audio` 模块内 `override fun release()` 共 **4** 处（`AacEncoderWrapper:44`、`OpusEncoderWrapper:44`、`CompressedPcmEncoderWrapper:20`、`CompressedPcmDecoderWrapper:20`），四者实现的都是接口而非本类，无子类覆写，可直接删除。（复审修正：原文误记为 2 处，结论不变） |
| 删除代码后残留失效 import / 私有成员 | detekt `maxIssues = 0`，零容忍。每批结束跑完整静态检查 |
| 异步回滚被并发停止取消 | 已由 3.4 的 `launch(NonCancellable)` 解决，需在代码注释中写明原因，避免后续被"优化"掉 |
| netty 回调无法调用挂起的 `stop()` / `stopServer()` | 4 处非挂起回调使用带 handler 的专用 `cleanupScope`；`SuspendTeardownGate` 保证它们与 Activity 的关闭只执行一次 |
| 删除 API 导致 `audio` 现有单测编译失败 | 20 处 `subject.release()` / `subject.stop {}` 分布在三个测试文件。已在 6.2 逐条定案（迁移 / 重写 / 删除），且必须与删除 API 同提交。**第二次复审新增** |
| worker 未退出时释放 AudioTrack | `AudioSender` 两个阻塞队列改 Channel，与 `AudioReceiver` 一样在释放 player 前 `cancelAndJoin()` |
| Activity 与 netty 并发重复关闭 | 两个类都通过 `SuspendTeardownGate` 共享一次 teardown 结果，不依赖 AudioTrack check-then-act |
| ADPCM 播放线程与 `onDestroy()` 并发 | 由单个 lifecycle `Job` 拥有局部 player，在自身 `finally` 释放；`onDestroy()` 只取消 Job |

## 9. 明确不做的事

- 不把 `synchronized` 换成 `Mutex`（见 3.2）
- 不使用任何 `@Suppress("DEPRECATION")`
- 不内联 `StreamPlayerStopper` 的私有辅助函数
- 不改 `AudioTrackPlayer.release()`（它直接包装 `AudioTrack`，无 worker 需要 join，不属本次范围）
- 不处理 `AudioCipherActivity.kt:95`（那是 `android.media.MediaPlayer`）

## 10. 复审记录（2026-09-16）

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
- §4.7 指出的两个既有 bug 真实存在：`AudioReceiver` 先 `cancel()` 再释放（`:181` vs `:192`）；
  `AudioSender` 的 `ioScope.launch` 紧跟 `ioScope.cancel()`（`:137-140`）构成竞态。
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
- §10.2 的 F2 / F4 修正准确：`AudioActivity` 确有 `launchCleanup`（`:378`）与
  `ioScopeJob.complete()`（`:374`），`:345` / `:362` / `:363` 行号全对；`ADPCMActivity` 确实没有
  任何协程 scope，`:115` 在 `kotlin.concurrent.thread` 块内，`:121` 在 `onDestroy()`。
  这是当时源码事实；实施方案已被 §12 修正为 lifecycle Job 所有权模型。
- 全仓无 `BaseMediaCodec.release()` 的子类覆写；`audio` / `demo` 之外没有任何调用方。
- `CHANGELOG.md` 的 `### 变更 (Changed)` 小节确实存在（`:58`）；demo 依赖
  `lifecycle-runtime-ktx`，`lifecycleScope` 可用。

### 11.2 本轮修正的问题

| 编号 | 级别 | 问题 | 修正位置 |
|------|------|------|---------|
| C1 | CRITICAL | `stop()` / `stopServer()` 改挂起后，4 个 netty 监听回调（`AudioSender:71,77`、`AudioReceiver:96,102`）是非挂起接口实现，改不了签名，会直接编译失败。原 §4.7 只统计了 Activity 侧调用方 | §4.7（新增专用 `cleanupScope` 方案）、§5、§8 |
| C2 | CRITICAL | 原 §6.2 只说"没有值得新写的单元测试"，未核对**现有**测试对被删 API 的依赖。实际三个测试文件共 20 处调用，删除后 `:audio:testDebugUnitTest` 编译失败 | §6.2（逐用例定案）、§7 |
| H2 | HIGH | 原 §4.7 把 `AudioReceiver` 的 `ioScope.cancel()` 挪到函数末尾，使播放循环在整个 `releaseAndJoin()` 期间继续跑；PCM 路径下 `play()` 的 `write()` 会撞 `AudioTrack.release()` | §4.7（改为释放前 `cancelAndJoin()`）、§8 |
| M1 | MEDIUM | §4.6 称 `stopAudioRecord()` 与 `ioScope.cancel()` "相对顺序保持不变"，与实际改动不符（确实反了） | §4.6 |
| M2 | MEDIUM | §10.1 称从 kotlinx-coroutines **1.9.0** 字节码确认，项目实际用 **1.10.2** | §10.1 |
| M3 | MEDIUM | `ADPCMActivity` 的 `:115` / `:121` 重复释放同一个 `player` 且不置空 | §4.7 |
| L1 | LOW | §4.4 给的 `OpusStreamPlayer` 删除范围 `:210-218` 漏掉了上方 KDoc（实为 `:206-218`） | §4.4 |
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

## 12. 第三次复审修正（2026-09-18）

本轮专门核对了确定性关闭的实际 worker 退出、并发重入、Activity 所有权和仓库执行边界，
修正以下问题：

1. `AudioSender` 的录音发送和接收播放 worker 都阻塞在 `ArrayBlockingQueue.take()`，bare
   cancel 无法使它们退出。改为有界 Channel + `cancelAndJoin()`。
2. Activity 和 netty 回调可并发调用关闭，`AudioTrack.state` 的 check-then-act 不是并发保护，
   `runCatching` 也无法阻止 native 重复 release。新增有单测覆盖的 `SuspendTeardownGate`。
3. `ADPCMActivity` 的裸 thread 和 `onDestroy()` 共享可变 player，可以与正在进行的 write 并发
   release，或在连续点击时释放错误实例。改为由 lifecycle Job 构造、使用并在
   `finally` 释放局部 player。
4. 不再向下游推荐裸 `lifecycleScope.launch(NonCancellable)`；改用受管理的 teardown scope
   和明确异常处理。
5. Codex 在本机实施时执行定向 Gradle 验证；Claude Code 仅提供静态分析。所有提交、
   推送和发布仍以用户明确授权为前提。
