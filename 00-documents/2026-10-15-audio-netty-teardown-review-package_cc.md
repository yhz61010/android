# audio 关闭路径重构 + basenetty 非阻塞关闭：代码级审查包（2026-10-15）

> **用途**：本文给未参与本次开发的外部审查者（Codex）使用，把当前工作树里**全部未提交改动**
> 还原到代码级别，并列出每处改动要核对的不变量。读完本文应能在不翻聊天记录的前提下完成审查。
>
> **状态**：源码已落地、经四路静态审查与三轮复审修正，并于 2026-10-15 经第五轮外部审查后再次修正
> （见 §I）；**Gradle（编译 / detekt / ktlint / 单测）与真机验证均未执行**，由仓库所有者本地完成。
> 文中所有「预期」均为静态推断。

## 0. 基线与清单

| 项 | 值 |
|---|---|
| 分支 | `review/native-modules` |
| 基线提交 | `d3058e2bc89f2bf5c277abd1b5ab165c36f33c1b` |
| 改动规模（代码） | **25 个已跟踪 `.kt` 修改（+1298 / −512），5 个新 `.kt`** —— 这才是审查对象（2026-10-16 第六轮后刷新） |
| 改动规模（文档） | 3 个已跟踪文档修改（`CHANGELOG.md` + spec + plan），3 个新文档（netty 记录、`2026-10-16-basenetty-client-release-terminal-state_cc.md`（提案，未改代码）与本文）|
| 不属于本改动集 | 工作树里另有 `CLAUDE.md`、`.claude/memory/` 两处修改，内容是「API 演进约定」协作规则，与本次 audio/netty 改动无关，审查时请排除 |
| 生成 diff | `git diff d3058e2bc` + `git ls-files --others --exclude-standard` |

> §0 这里刻意只记**代码**的增删行数。上一版写的是含文档的总数，而本文自己就在那个总数里 ——
> 任何一次文档订正都会让它失准（第五轮发现时它已经偏了 +18 / −1）。代码行数不受文档编辑影响，
> 可以直接用 `git diff --numstat` 过滤 `*.kt` 复核。

### 0.1 文件清单（按区）

**A. `audio` 模块（15 个修改）**

```
audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt
audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt
audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt
audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt
audio/src/main/kotlin/com/leovp/audio/base/StreamPlayerStopper.kt
audio/src/main/kotlin/com/leovp/audio/base/iters/AudioEncoderWrapper.kt
audio/src/main/kotlin/com/leovp/audio/base/iters/AudioDecoderWrapper.kt
audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/AacEncoderWrapper.kt
audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/OpusEncoderWrapper.kt
audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt
audio/src/main/kotlin/com/leovp/audio/base/decoderWrapper/CompressedPcmDecoderWrapper.kt
audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt
audio/src/test/kotlin/com/leovp/audio/base/StreamPlayerStopperTest.kt
audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronousTest.kt
audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronousTest.kt
```

**B. demo 音频示例（4 个修改 + 4 个新增）**

```
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/ADPCMActivity.kt
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/receiver/AudioReceiver.kt
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/sender/AudioSender.kt
NEW demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt
NEW demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupport.kt
NEW demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt
NEW demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupportTest.kt
```

**C. `basenetty` + socket demo 调用方（6 个修改 + 1 个新增）**

```
NEW basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/NettyFutureExt.kt
basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt
basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt
basenetty/src/test/kotlin/com/leovp/basenetty/BaseNettyLifecycleTest.kt
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/socket/websocket/WebSocketServerActivity.kt
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/socket/websocket/WebSocketClientActivity.kt
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/socket/eventbusbridge/EventBusBridgeClientActivity.kt
```

**D. 文档（3 个修改 + 1 个新增，本文不展开，只列出）**

```
CHANGELOG.md                                  （### 变更 (Changed) 下新增 audio 与 basenetty 两个条目）
00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md （头部状态 + §20）
00-documents/superpowers/plans/2026-09-16-audio-deprecated-teardown-removal.md       （步骤勾选 + 末节）
NEW 00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md                          （netty 改动记录 + §10/§11 复审）
NEW 00-documents/2026-10-15-audio-netty-teardown-review-package_cc.md                 （本文）
```

## 1. 改动目标（两条线）

### 1.1 audio：删除非挂起的弃用关闭 API

`audio` 模块上一轮重构给实现类补了挂起版确定性关闭（`releaseAndJoin()` / `stopPlayingAndJoin()` /
`stopRecordAndJoin()`），并把非挂起版标为 `@Deprecated`，但迁移停在实现层：接口没跟上，模块内仍有
8 处调用弃用 API。本次目标：

1. 两个 wrapper 接口（`AudioEncoderWrapper` / `AudioDecoderWrapper`）删掉 `release()`，
   `releaseAndJoin()` 改为**抽象**（不再提供会静默退回非确定性关闭的默认实现）；
2. 删除 `BaseMediaCodec.release()`、`AacStreamPlayer.stopPlaying()`、`OpusStreamPlayer.stopPlaying()`、
   `StreamPlayerStopper.stop(...)`、`MicRecorder.stopRecord()`、`MicRecorder.finishRecorderRelease()`、
   `AudioPlayer.release()`，新增 `AudioPlayer.releaseAndJoin()`；
3. 三处初始化失败回滚（两个 StreamPlayer 的 `initDecoderLocked().onFailure`、`MicRecorder` 的
   构造失败与 `startRecord()` 失败）改为 `ioScope.launch(NonCancellable) { releaseAndJoin() }`，
   必须在紧随其后的 `ioScope.cancel()` 下存活；
4. 全部调用方（audio 内部、3 个 audio 测试文件、4 个 demo 文件）迁到挂起版；
   `@Suppress("DEPRECATION")` 在 `audio/src/test` 清零，不新增任何抑制。

设计与十轮复审记录：`00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md`
（§20 为实施后审查修正）；实施计划：同目录 `plans/` 下同名文件。

### 1.2 basenetty：关闭路径去阻塞化

`BaseNettyServer.stopServer()` 原先 `close().syncUninterruptibly()` 后**串行**
`bossGroup.shutdownGracefully().syncUninterruptibly()`、`workerGroup...`；无参 `shutdownGracefully()`
的 quiet period 默认 2 秒，两段相加就是 KDoc 里「almost 4s」的来源；`syncUninterruptibly()` 让协程取消
对它无效。demo 里所有调用点都在 `Dispatchers.IO` 协程中。本次：

1. `startServer()` / `stopServer()` 改 `suspend`；`startServer()` 绑定成功即返回，不再阻塞到服务器关闭；
2. 新增 `internal suspend fun <V> Future<V>.awaitCompletion()`（listener 桥接 + `isDone` 快速路径）；
3. 两个 group **先都发出** `shutdownGracefully(0, 2, SECONDS)` 再分别等待；netty 部分带 3 秒资源层超时；
4. `@Synchronized` → `lifecycleMutex`，`stopServer()` 一并持有；stop 先于 start 时在锁内释放 group
   并置 `STOPPED`，随后的 `startServer()` 按状态拒绝；
5. `BaseNettyClient.release()` / `disconnectManually()` 去掉最后的 `syncUninterruptibly()`，
   `release()` 加 3 秒超时；三个关闭入口过了状态守卫后在 `withContext(NonCancellable)` 下运行。

完整记录与两轮复审（R1–R19）：`00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md`。

## 2. 审查历史（本文之前已做过什么）

| 轮次 | 范围 | 结果 |
|---|---|---|
| 第一轮（4 路并行） | audio / demo / basenetty / 计划对齐 | 3 条 Important：`awaitCompletion()` 对已终止 executor 永不恢复；`stopping` 标志只缩窄不关闭 check-then-act 窗口；门闩测试第 2 例在 Gradle `-ea` 下必红。全部修复 |
| 第二轮 | 9 组修复复核 + 全量复扫 + 四份文档一致性 | 代码 ready；文档 5 处过期表述订正 |
| 第三轮（聚焦） | stop-before-start 改动 | 新发现：3 秒预算在关 channel 阶段耗尽时两个 group 永久泄漏。修复 |
| 第四轮（聚焦） | NonCancellable 包裹 | ready；修 `released` 局部变量遮蔽字段、`disconnectNow()` 的 handler 释放加守卫 |
| 第五轮（外部，2026-10-15） | 全量代码 + 本文一致性 | 3 条 MEDIUM、3 条 LOW、3 处注释失准、2 处文档失准。全部已修，见 §I |
| 第六轮（2026-10-16，两路并行） | basenetty + socket demo / audio + demo 音频 | basenetty 0 Critical 0 Important 5 Minor；audio 2 Important 5 Minor。除一条"仅记录"外全部已修，见 §J |
| 第七轮（2026-10-16，复核） | 仅第六轮的 9 组修正 | 全部 OK；3 处"旧行为"措辞不实（`MicRecorder` / 两个 stream player 的 KDoc、`WebSocketClientActivity` 注释前提），已订正，见 §J.13 |

reviewer 不必重做上述工作，但**欢迎推翻**：所有结论都是静态得出的。

## 3. 全局约束（代码必须满足）

- detekt `maxIssues = 0`（`10-configs/detekt.yml`）：未用 import / 私有成员即失败；`SwallowedException`
  激活；`InstanceOfCheckForException` 激活（测试除外）；`LongMethod` 150；`TooManyFunctions` 33；
  `TooGenericExceptionCaught` 未激活。
- ktlint：`max_line_length = 100`（import 行豁免）；`ktlint_standard_import-ordering = disabled`；
  调用处**不加**尾逗号（`android_studio` code style）。
- 代码注释与 KDoc 英文；本仓库 `runCatching` 包挂起代码时必须用 `runCatchingPreservingCancellation`，
  `CancellationException` 必须上抛。
- 测试栈：JUnit 5 + kotlin-test + kotlinx-coroutines-test + Mockk；`unitTests.isReturnDefaultValues = true`；
  Gradle `Test` 任务默认 `-ea`，因此 kotlinx.coroutines DEBUG 模式开启（影响 `withContext` 异常是否为副本）。

## A. audio 模块

基线 `HEAD = d3058e2bc`，分支 `review/native-modules`，以下全部为工作树未提交改动。
`git diff HEAD --stat -- audio/`：15 个文件，+200 / −242。

背景（摘自 spec §1 / §20 与 plan Task 1/2/4）：上一轮"确定性一次性会话"重构给实现类补了挂起版关闭
（`releaseAndJoin()` / `stopPlayingAndJoin()` / `stopRecordAndJoin()`），但接口层没跟上——
`AudioEncoderWrapper.releaseAndJoin()` 的默认实现退化为 `release()`，`AudioDecoderWrapper` 根本没有挂起变体，
模块内仍有 8 处弃用调用。本次目标：接口层改为抽象 `suspend fun releaseAndJoin()`；删除所有非挂起弃用关闭 API
（`BaseMediaCodec.release()`、`*StreamPlayer.stopPlaying()`、`MicRecorder.stopRecord()`、
`StreamPlayerStopper.stop()`、`AudioPlayer.release()`）；非挂起入口里的 init 失败回滚改为
`ioScope.launch(NonCancellable) { ... releaseAndJoin() }`，使其在紧随其后的 `ioScope.cancel()` 之后仍能跑完；
全程不新增 `@Suppress("DEPRECATION")`。同一工作树里另有一项独立改动：两个 StreamPlayer 的逐帧日志用
`BuildConfig.DEBUG` 门控。spec §20 记录了实施后一轮静态审查的修正，其中落到 audio 的有：
`AudioPlayer.releaseAndJoin()` KDoc 补 PCM 路径论证、`MicRecorder` 漂移的 KDoc 归位、
`StreamPlayerStopper` 日志改名、`AacStreamPlayer` 的 `cal` 复用 `elapsedUs`。spec 明确声明：**全部未经 Gradle 或真机验证**。

---

### A.1 audio/src/main/kotlin/com/leovp/audio/base/iters/AudioEncoderWrapper.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.7）。

删除 `fun release()` 及退化默认实现 `suspend fun releaseAndJoin() = release()`，`releaseAndJoin()` 改为抽象方法。
原因：默认实现让"等待 worker 退出"的 KDoc 承诺对所有未覆写者为假（spec §1.1）。

`AudioEncoderWrapper.kt:7-16`（改动前，HEAD）

```kotlin
interface AudioEncoderWrapper {
    /**
     * @param input The byte order of [input] is little endian.
     */
    fun encode(input: ByteArray)
    fun release()

    /** Waits until any asynchronous encoder worker has stopped and its resources are released. */
    suspend fun releaseAndJoin() = release()
}
```

`AudioEncoderWrapper.kt:1-23`（改动后，工作树全文；第六轮在 `releaseAndJoin()` KDoc 末尾追加迁移说明，§J.7）

```kotlin
package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:27
 */
interface AudioEncoderWrapper {
    /**
     * @param input The byte order of [input] is little endian.
     */
    fun encode(input: ByteArray)

    /**
     * Waits until any asynchronous encoder worker has stopped and its resources are released.
     *
     * Deliberately abstract: a default delegating to a non-suspend release would make this
     * method's own promise false for every implementation that did not override it.
     *
     * Replaces the removed non-suspend release(). Migration: implementations move the whole
     * release into this method and callers await it from a coroutine they own.
     */
    suspend fun releaseAndJoin()
}
```

审查要点：
- 这是源码级破坏性变更：任何外部实现类都必须补 `override suspend fun releaseAndJoin()`。仓库内实现者只有
  `AacEncoderWrapper` / `OpusEncoderWrapper` / `CompressedPcmEncoderWrapper`（grep 全仓 `*.kt`，见 A.z），三者都已覆写。
- `AudioEncoderManager.getWrapper()` 返回类型仍是该接口，未改（diff 未触及该文件）。
- CHANGELOG 已记录该破坏性变更（`### 变更 (Changed)` 下 audio 条目的第二个子项）。

---

### A.2 audio/src/main/kotlin/com/leovp/audio/base/iters/AudioDecoderWrapper.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.7）。

`fun release()` 直接替换为抽象 `suspend fun releaseAndJoin()`（此前根本没有挂起变体，这是
`AudioPlayer` 里 `decoderWrapper?.release()` 从不报弃用警告的根因）。

`AudioDecoderWrapper.kt:7-10`（改动前，HEAD）

```kotlin
interface AudioDecoderWrapper {
    fun decode(input: ByteArray)
    fun release()
}
```

`AudioDecoderWrapper.kt:1-17`（改动后，工作树全文；第六轮在 `releaseAndJoin()` KDoc 末尾追加迁移说明，§J.7）

```kotlin
package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:27
 */
interface AudioDecoderWrapper {
    fun decode(input: ByteArray)

    /**
     * Waits until any asynchronous decoder worker has stopped and its resources are released.
     *
     * Replaces the removed non-suspend release(). Migration: implementations move the whole
     * release into this method and callers await it from a coroutine they own.
     */
    suspend fun releaseAndJoin()
}
```

审查要点：
- 仓库内唯一实现者是 `CompressedPcmDecoderWrapper`（已覆写）；`AudioDecoderManager` 未改。
- 同样是源码级破坏性变更。

---

### A.3 audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/AacEncoderWrapper.kt

删除 `override fun release()`（原本 `runCatching { encoder.release() }` + 打日志），随之删掉只被它使用的
`companion object { TAG }` 与两个 import。保留的 `releaseAndJoin()` 直接委托 `AacEncoder.releaseAndJoin()`。

`AacEncoderWrapper.kt:1-10`（改动前，HEAD，import 区）

```kotlin
package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.base.runCatchingPreservingCancellation

import com.leovp.audio.aac.AacEncoder
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.log.LogContext
```

`AacEncoderWrapper.kt:20-22 / 44-51`（改动前，HEAD，companion 与 release）

```kotlin
    companion object {
        private const val TAG = "AacEncoderWrapper"
    }
// ...
    override fun release() {
        runCatchingPreservingCancellation { encoder.release() }
            .onFailure { LogContext.log.e(TAG, "AAC encoder release failed", it) }
    }

    override suspend fun releaseAndJoin() {
        encoder.releaseAndJoin()
    }
```

`AacEncoderWrapper.kt:1-7 / 33-39`（改动后）

```kotlin
package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.aac.AacEncoder
import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.base.iters.OutputCallback
// ...
    override fun encode(input: ByteArray) {
        encoder.encode(input)
    }

    override suspend fun releaseAndJoin() {
        encoder.releaseAndJoin()
    }
```

审查要点：
- 行为差异：旧 `release()` 吞掉非取消异常并记日志；新 `releaseAndJoin()` **不吞异常**，`AacEncoder.releaseAndJoin()`
  （即 `BaseMediaCodec.releaseAndJoin()`，见 A.8）的 `require(...)` 失败或内部异常会直接向上抛。
  调用方 `MicRecorder.finishRecorderReleaseAndJoin()` 已自带 try/catch（A.11），`rollbackFailedInit` 用
  `runCatchingPreservingCancellation` 包住（A.11）。
- detekt 零容忍：`TAG`、`LogContext`、`runCatchingPreservingCancellation` 已一并删除，文件内无残留引用（已核）。

---

### A.4 audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/OpusEncoderWrapper.kt

与 A.3 完全对称：删 `release()`、`companion TAG`、两个 import。

`OpusEncoderWrapper.kt:44-51`（改动前，HEAD）

```kotlin
    override fun release() {
        runCatchingPreservingCancellation { encoder.release() }
            .onFailure { LogContext.log.e(TAG, "Opus encoder release failed", it) }
    }

    override suspend fun releaseAndJoin() {
        encoder.releaseAndJoin()
    }
```

`OpusEncoderWrapper.kt:1-7 / 37-39`（改动后）

```kotlin
package com.leovp.audio.base.encoderWrapper

import com.leovp.audio.base.bean.AudioEncoderInfo
import com.leovp.audio.base.iters.AudioEncoderWrapper
import com.leovp.audio.base.iters.IEncodeCallback
import com.leovp.audio.base.iters.OutputCallback
import com.leovp.audio.opus.OpusEncoder
// ...
    override suspend fun releaseAndJoin() {
        encoder.releaseAndJoin()
    }
```

审查要点：同 A.3。

---

### A.5 audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt

空实现 `release()` 改为空实现 `suspend fun releaseAndJoin()`，加注释说明为何无事可做。

`CompressedPcmEncoderWrapper.kt:20-21`（改动前，HEAD）

```kotlin
    override fun release() {
    }
```

`CompressedPcmEncoderWrapper.kt:20-23`（改动后）

```kotlin
    override suspend fun releaseAndJoin() {
        // Nothing to release: compression runs synchronously on the caller's thread, so there is
        // no worker to wait for.
    }
```

审查要点：
- 注释声明"compression runs synchronously on the caller's thread"——`encode()` 第 17 行确实是同步调用 `input.compress()`，成立。
- detekt `EmptyFunctionBlock`：项目 `10-configs/detekt.yml:215-217` 设 `ignoreOverridden: true`，这两个 `override` 空体不会报。

---

### A.6 audio/src/main/kotlin/com/leovp/audio/base/decoderWrapper/CompressedPcmDecoderWrapper.kt

与 A.5 对称。

`CompressedPcmDecoderWrapper.kt:20-21`（改动前，HEAD）

```kotlin
    override fun release() {
    }
```

`CompressedPcmDecoderWrapper.kt:20-23`（改动后）

```kotlin
    override suspend fun releaseAndJoin() {
        // Nothing to release: decompression runs synchronously on the caller's thread, so there
        // is no worker to wait for.
    }
```

审查要点：同 A.5。`AudioPlayer.releaseAndJoin()` KDoc 中"With COMPRESSED_PCM the wrapper's releaseAndJoin() is a no-op"
依赖此处为空实现，两边一致。

---

### A.7 audio/src/main/kotlin/com/leovp/audio/base/StreamPlayerStopper.kt

删除非挂起 `stop(releaseDecoder: (Decoder) -> Unit)`；`stopAndJoin()` 末尾日志 `"stopPlaying() done"` 改为 `"stopAndJoin() done"`。

`StreamPlayerStopper.kt:22-31`（改动前，HEAD）

```kotlin
    fun stop(releaseDecoder: (Decoder) -> Unit) {
        val decoder = prepareStop()
        try {
            runCatchingPreservingCancellation { decoder?.let(releaseDecoder) }
                .onFailure { LogContext.log.e(tag, "audioDecoder release error", it) }
        } finally {
            releaseAudioTrack()
        }
        LogContext.log.w(tag, "stopPlaying() done")
    }
```

`StreamPlayerStopper.kt:22-42`（改动后，完整保留的 `stopAndJoin`）

```kotlin
    suspend fun stopAndJoin(releaseDecoderAndJoin: suspend (Decoder) -> Unit) {
        val ownerJob = ioScope.coroutineContext[Job]
        val callerJob = currentCoroutineContext()[Job]
        require(ownerJob == null || callerJob == null || !ownerJob.containsJob(callerJob)) {
            "stopAndJoin() must be called outside the player-owned coroutine scope"
        }
        withContext(Dispatchers.IO + NonCancellable) {
            val decoder = prepareStop()
            try {
                ownerJob?.cancelAndJoin()
                decoder?.let { releaseDecoderAndJoin(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogContext.log.e(tag, "audioDecoder release error. msg=${e.message}", e)
            } finally {
                releaseAudioTrack()
            }
        }
        LogContext.log.w(tag, "stopAndJoin() done")
    }
```

审查要点：
- 旧 `stop()` 与 `stopAndJoin()` 的差异：旧版不 `cancelAndJoin()` ownerJob（只在 `prepareStop()` 里 `ioScope.cancel()`），
  释放 decoder 时不等待 worker；新版两者都做。调用方只剩 `AacStreamPlayer` / `OpusStreamPlayer` 的 `stopPlayingAndJoin()`。
- `runCatchingPreservingCancellation` 在 `prepareStop()` / `releaseAudioTrack()` 仍有使用，import 不悬空（已核，该文件通过同包无 import）。
- `ownerJob?.cancelAndJoin()` **不会**等待 A.9/A.10 的 `launch(NonCancellable)` 回滚协程（它不是 ownerJob 的子协程），
  这与 `AudioPlayer.releaseAndJoin()` KDoc "One worker is deliberately not awaited" 一致。
- `require(...)` 失败会以 `IllegalArgumentException` 抛出，发生在任何副作用之前；`AudioPlayer.releaseAndJoin()` 的
  `finally` 正是为此兜底（A.12）。

---

### A.8 audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.7）。

删除 `@Deprecated open fun release()`（HEAD 226-246 行，含 KDoc）。`releaseAndJoin()`（工作树 152-183 行）本体未改动，第六轮只在其 KDoc 末尾追加了迁移说明（§J.7）。

`BaseMediaCodec.kt:226-246`（改动前，HEAD）

```kotlin
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

`BaseMediaCodec.kt:152-183`（改动后，保留且现在是唯一的终态入口）

```kotlin
    /**
     * Deterministic lifecycle teardown: cancel and join the codec worker, cancel [ioScope], then
     * release the codec exactly once.
     *
     * This MUST be called by an external owner, never by the codec worker itself, otherwise the
     * worker would join on itself and deadlock. If a worker needs to request teardown, it should
     * expose a separate `requestRelease()` returning a [Job] instead of calling this.
     *
     * Replaces the removed non-suspend release(). Migration: await this from a coroutine the
     * owner controls; subclasses that overrode release() put that work in [onCodecReleased]
     * instead, as this method is not open.
     */
    suspend fun releaseAndJoin() {
        require(codecJob !== currentCoroutineContext()[Job]) {
            "releaseAndJoin() must be called by an external owner, not the codec worker"
        }
        if (!markReleasing()) return
        try {
            withContext(NonCancellable) {
                ioScope.cancel()
                codecJob?.cancelAndJoin()
                releaseCodecOnce()
                // start() may have installed a synchronous worker while release was waiting for
                // the codec operation lock. Re-read and join it before deterministic teardown
                // returns.
                codecJob?.cancelAndJoin()
                codecJob = null
            }
        } finally {
            lifecycleState.set(LifecycleState.RELEASED)
        }
    }
```

审查要点：
- 删除后 `markReleasing()` 只剩 `releaseAndJoin()` 一个调用方（第 168 行）、`releaseCodecOnce()` 剩 `start()` 失败路径与
  `releaseAndJoin()` 两个调用方（127、173 行）；两者均不构成未用私有成员。
- 子类是否仍有 `override fun release()`：`audio/` 与 `demo/` grep 无 BaseMediaCodec 子类覆写（demo 里 9 处
  `override fun release()` 全是 netty handler 的 `BaseChannelInboundHandler.release`，与此无关）。
  `BaseMediaCodec` 子类（`AacEncoder` / `AacDecoder` / `OpusEncoder` / `OpusDecoder` / `AacFilePlayer` /
  两个 `BaseMediaCodec*` / 两个测试）全部位于 `audio/`。
- `release()` 的删除使 `lifecycleState`、`codecJob`、`ioScope` 的终态写入只剩一条路径；`start()` 的 catch 分支
  （124-130 行）仍直接写 `RELEASING`/`RELEASED`，与 `releaseAndJoin()` 的 `markReleasing()` 之间是否存在
  "start 失败后 releaseAndJoin 变为 no-op"：`markReleasing()` 对 `RELEASED` 返回 false → 正是预期（测试
  `release before start makes the one-shot session terminal` / `start failure releases ... and is terminal` 覆盖）。
- 这对 A.9/A.10 很重要：如果 `AacDecoder(...).apply { start() }` 在 `start()` 内抛出，`BaseMediaCodec.start()` 的
  catch 已自行释放 codec，并且赋值 `audioDecoder = ...` 不会发生，所以回滚里 `failedDecoder` 为 `null`，不会双重释放。

---

### A.9 audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.7）。

三类改动：(1) init 失败回滚由同步 `audioDecoder?.release()` 改为"先 detach，再 `releaseDecoderAsync()` 用
`ioScope.launch(NonCancellable)` 异步 `releaseAndJoin()`"；(2) 删除 `@Deprecated fun stopPlaying()`；
(3) 三处逐帧日志用 `BuildConfig.DEBUG` 门控，`cal` 复用 `elapsedUs`（独立改动）。新增 import
`com.leovp.audio.BuildConfig`、`kotlinx.coroutines.NonCancellable`。

`AacStreamPlayer.kt:154-159`（改动前，HEAD，`initDecoderLocked` 的 `onFailure`）

```kotlin
        }.onFailure {
            runCatchingPreservingCancellation { audioDecoder?.release() }
            audioDecoder = null
            csd0 = null
            LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
        }
```

`AacStreamPlayer.kt:163-192`（改动后）

```kotlin
        }.onFailure {
            // Detach before releasing: the rollback below runs asynchronously, so nothing may
            // still reach this half-built decoder through the player's state.
            val failedDecoder = audioDecoder
            audioDecoder = null
            csd0 = null
            releaseDecoderAsync(failedDecoder)
            LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
        }
    }

    /**
     * Releases a decoder that failed to initialize, off the caller's thread.
     *
     * The caller holds [lock] and is not suspending, so the deterministic release cannot be
     * awaited here. NonCancellable rather than a plain ioScope child: StreamPlayerStopper
     * cancels ioScope while stopping, and a concurrent stop would otherwise kill this rollback
     * and leak a decoder it has already detached and can no longer reach.
     *
     * The body must not throw. NonCancellable replaces the parent job and this scope carries no
     * CoroutineExceptionHandler, so an escaping throwable would reach the platform default
     * handler and kill the process.
     */
    private fun releaseDecoderAsync(decoder: AacDecoder?) {
        val failed = decoder ?: return
        ioScope.launch(NonCancellable) {
            runCatchingPreservingCancellation { failed.releaseAndJoin() }
                .onFailure { LogContext.log.e(TAG, "rollback release failed", it) }
        }
    }
```

`AacStreamPlayer.kt:213-226`（改动前，HEAD，已删除）

```kotlin
    /**
     * Legacy non-suspend entry point. It preserves synchronous resource release, but cannot wait
     * for the decoder worker to finish; use [stopPlayingAndJoin] when completion matters.
     */
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

`AacStreamPlayer.kt:239-251`（改动后，保留的唯一停止入口；KDoc 第二段为第六轮追加的迁移说明，§J.7）

```kotlin
    /**
     * Deterministic teardown. Detaches decoder/CSD and advances the generation under the lock,
     * then awaits the old decoder OUTSIDE the lock to avoid suspension or callback re-entry.
     *
     * Replaces the removed non-suspend stopPlaying(). Migration: await this from a coroutine the
     * owner controls; it returns only once the decoder worker has exited and the track is
     * released, where stopPlaying() released the track synchronously but returned without
     * waiting for the decoder worker to exit.
     */
    suspend fun stopPlayingAndJoin() {
        stopped.set(true)
        streamPlayerStopper.stopAndJoin { it.releaseAndJoin() }
    }
```

`AacStreamPlayer.kt:70-80`（改动前，HEAD，`onDecoded`）

```kotlin
                override fun onDecoded(pcmData: ByteArray) {
                    LogContext.log.i(TAG, "onDecoded PCM[${pcmData.size}]")
                    if (pcmData.isNotEmpty()) {
                        if (AudioTrack.STATE_UNINITIALIZED == audioTrackPlayer.state) return
                        if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayer.playState) {
                            LogContext.log.i(TAG, "Play PCM[${pcmData.size}]")
                            // Play decoded audio data in PCM
                            audioTrackPlayer.write(pcmData)
                        }
                    }
                }
```

`AacStreamPlayer.kt:72-89`（改动后）

```kotlin
                override fun onDecoded(pcmData: ByteArray) {
                    // Gated: this fires once per decoded frame, and the message is built before
                    // the log level is ever consulted. Same treatment AudioTrackPlayer and
                    // AudioPlayer already give their per-frame logs.
                    if (BuildConfig.DEBUG) {
                        LogContext.log.i(TAG, "onDecoded PCM[${pcmData.size}]")
                    }
                    if (pcmData.isNotEmpty()) {
                        if (AudioTrack.STATE_UNINITIALIZED == audioTrackPlayer.state) return
                        if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayer.playState) {
                            if (BuildConfig.DEBUG) {
                                LogContext.log.i(TAG, "Play PCM[${pcmData.size}]")
                            }
                            // Play decoded audio data in PCM
                            audioTrackPlayer.write(pcmData)
                        }
                    }
                }
```

`AacStreamPlayer.kt:165-174`（改动前，HEAD，`decodeOrDropLocked` 延迟日志）

```kotlin
        val latencyInMs =
            (
                SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs
                ) / 1000 - getAudioTimeUs() / 1000
        LogContext.log.d(
            TAG,
            "st=$playStartTimeInUs\t " +
                "cal=${(SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000}\t " +
                "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
        )
```

`AacStreamPlayer.kt:197-209`（改动后）

```kotlin
        val elapsedUs = SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs
        val latencyInMs = elapsedUs / 1000 - getAudioTimeUs() / 1000
        // Gated: one call per incoming frame, concatenating four values - including a second
        // getAudioTimeUs(), which reads AudioTrack.playbackHeadPosition through a native call.
        // `cal` reuses elapsedUs instead of re-reading the clock, so the logged value is the one
        // latencyInMs was derived from.
        if (BuildConfig.DEBUG) {
            LogContext.log.d(
                TAG,
                "st=$playStartTimeInUs\t cal=${elapsedUs / 1000}\t " +
                    "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
            )
        }
```

审查要点：
- **`launch(NonCancellable)` 是否真正脱离随后被取消的 scope**：`ioScope = CoroutineScope(Dispatchers.IO + Job())`（42 行）。
  `launch(NonCancellable)` 的上下文 = scope 上下文 + `NonCancellable`；`NonCancellable` 是 `Job` 元素，**替换**了 scope 的 Job
  成为新协程的父 Job，因此 `StreamPlayerStopper.prepareStop()` 里的 `ioScope.cancel()` 与 `stopAndJoin()` 里的
  `ownerJob?.cancelAndJoin()` 都够不到它；`Dispatchers.IO` 作为 `ContinuationInterceptor` 元素被保留。
  代价：该协程也不会被任何人 join，是整条 teardown 链中唯一不被等待的 worker（KDoc 已声明）。
  另：kotlinx.coroutines 对 `launch(NonCancellable)` 有 IDE 级 inspection 警告（"NonCancellable can be used only in withContext"），
  detekt 默认规则集不含此项；项目 `10-configs/detekt.yml` 中无 `NonCancellable` 相关自定义规则（已 grep）。
- **回滚协程体不得抛出**：`runCatchingPreservingCancellation` 只放行 `CancellationException`（`RunCatchingExt.kt:6-12`），
  其余 `Throwable` 全部转 `Result.failure` 并记日志。`CancellationException` 从 `launch` 体逃逸会被视为协程正常取消、
  不进 uncaught handler，所以 KDoc "must not throw" 的实际风险只剩 CE，而 CE 不致命。成立。
- **detach 先于 release 的不变量**：`failedDecoder` 取自 `audioDecoder`，之后 `audioDecoder = null; csd0 = null`。
  `initDecoderLocked` 只在 `activeCsd == null` 时进入（110-121 行），而 `csd0` 与 `audioDecoder` 总是同置同清
  （148 行 onSuccess、167-168 行 onFailure、251-253 行 detach），因此进入时 `audioDecoder == null`。
  推论：若 `AacDecoder` 构造或 `start()` 抛出，赋值不发生，`failedDecoder == null`，`releaseDecoderAsync` 直接 return，
  且 `BaseMediaCodec.start()` 已自行释放 codec（A.8）；只有 `audioTrackPlayer.play()` 抛出时 decoder 才真正进入异步释放。
- 在持有 `lock` 的 `synchronized` 块内调用 `ioScope.launch(...)`：`launch` 本身不阻塞、协程体不取 `lock`，无死锁；
  但 `failed.releaseAndJoin()` 会 `cancelAndJoin()` decoder 的 codec worker，worker 的 `onDecoded` 回调会访问
  `audioTrackPlayer`（已在 stopper 中可能被 release）——`onDecoded` 先查 `STATE_UNINITIALIZED`（80 行），
  check-then-write 之间仍有理论窗口，属改动前既有行为，本次未扩大。
- 日志门控：`BuildConfig.DEBUG` 为编译期常量，release 构建下字符串拼接与第二次 `getAudioTimeUs()`（native 调用）被整体消除；
  `cal` 现在等于 `latencyInMs` 的推导输入，AAC 侧与 OPUS 侧口径统一（spec §20.3）。
- 残留检查：`runCatchingPreservingCancellation` 仍在 144/189/220-222/262 行使用；`stopped`、`generation` 仍被
  `detachDecoderForStop` 使用；无悬空 import（已核）。

---

### A.10 audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.7）。

与 A.9 同构：回滚改异步 NonCancellable、删 `stopPlaying()`、三处逐帧日志门控（第三处在 `consumeDecodedPcm()`）。
新增 import `com.leovp.audio.BuildConfig`、`kotlinx.coroutines.NonCancellable`。

`OpusStreamPlayer.kt:147-154`（改动前，HEAD，`onFailure`）

```kotlin
        }.onFailure {
            runCatchingPreservingCancellation { audioDecoder?.release() }
            audioDecoder = null
            csd0 = null
            csd1 = null
            csd2 = null
            LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
        }
```

`OpusStreamPlayer.kt:154-185`（改动后）

```kotlin
        }.onFailure {
            // Detach before releasing: the rollback below runs asynchronously, so nothing may
            // still reach this half-built decoder through the player's state.
            val failedDecoder = audioDecoder
            audioDecoder = null
            csd0 = null
            csd1 = null
            csd2 = null
            releaseDecoderAsync(failedDecoder)
            LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
        }
    }

    /**
     * Releases a decoder that failed to initialize, off the caller's thread.
     *
     * The caller holds [lock] and is not suspending, so the deterministic release cannot be
     * awaited here. NonCancellable rather than a plain ioScope child: StreamPlayerStopper
     * cancels ioScope while stopping, and a concurrent stop would otherwise kill this rollback
     * and leak a decoder it has already detached and can no longer reach.
     *
     * The body must not throw. NonCancellable replaces the parent job and this scope carries no
     * CoroutineExceptionHandler, so an escaping throwable would reach the platform default
     * handler and kill the process.
     */
    private fun releaseDecoderAsync(decoder: OpusDecoder?) {
        val failed = decoder ?: return
        ioScope.launch(NonCancellable) {
            runCatchingPreservingCancellation { failed.releaseAndJoin() }
                .onFailure { LogContext.log.e(TAG, "rollback release failed", it) }
        }
    }
```

`OpusStreamPlayer.kt:207-220`（改动前，HEAD，已删除）

```kotlin
    /**
     * Legacy non-suspend entry point. It preserves synchronous resource release, but cannot wait
     * for the decoder worker to finish; use [stopPlayingAndJoin] when completion matters.
     */
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

`OpusStreamPlayer.kt:233-245`（改动后；KDoc 第二段为第六轮追加的迁移说明，§J.7）

```kotlin
    /**
     * Deterministic teardown. Detaches decoder/CSD and advances the generation under the lock,
     * then awaits the old decoder OUTSIDE the lock to avoid suspension or callback re-entry.
     *
     * Replaces the removed non-suspend stopPlaying(). Migration: await this from a coroutine the
     * owner controls; it returns only once the decoder worker has exited and the track is
     * released, where stopPlaying() released the track synchronously but returned without
     * waiting for the decoder worker to exit.
     */
    suspend fun stopPlayingAndJoin() {
        stopped.set(true)
        streamPlayerStopper.stopAndJoin { it.releaseAndJoin() }
    }
```

`OpusStreamPlayer.kt:83-88`（改动前，HEAD，`onDecoded`）

```kotlin
                override fun onDecoded(pcmData: ByteArray) {
                    LogContext.log.i(TAG, "onDecoded PCM[${pcmData.size}]")
                    if (pcmData.isNotEmpty() && !decodedPcmQueue.offer(pcmData)) {
                        LogContext.log.w(TAG, "Drop decoded PCM: playback queue is full")
                    }
                }
```

`OpusStreamPlayer.kt:85-95`（改动后）

```kotlin
                override fun onDecoded(pcmData: ByteArray) {
                    // Gated: this fires once per decoded frame, and the message is built before
                    // the log level is ever consulted. Same treatment AudioTrackPlayer and
                    // AudioPlayer already give their per-frame logs.
                    if (BuildConfig.DEBUG) {
                        LogContext.log.i(TAG, "onDecoded PCM[${pcmData.size}]")
                    }
                    if (pcmData.isNotEmpty() && !decodedPcmQueue.offer(pcmData)) {
                        LogContext.log.w(TAG, "Drop decoded PCM: playback queue is full")
                    }
                }
```

`OpusStreamPlayer.kt:160-167`（改动前，HEAD，`decodeOrDropLocked` 延迟日志；OPUS 侧 HEAD 已有 `elapsedUs`，但 `cal` 仍重读时钟）

```kotlin
        val elapsedUs = SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs
        val latencyInMs = elapsedUs / 1000 - getAudioTimeUs() / 1000
        LogContext.log.d(
            TAG,
            "st=$playStartTimeInUs\t " +
                "cal=${(SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs) / 1000}\t " +
                "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
        )
```

`OpusStreamPlayer.kt:190-202`（改动后）

```kotlin
        val elapsedUs = SystemClock.elapsedRealtimeNanos() / 1000 - playStartTimeInUs
        val latencyInMs = elapsedUs / 1000 - getAudioTimeUs() / 1000
        // Gated: one call per incoming frame, concatenating four values - including a second
        // getAudioTimeUs(), which reads AudioTrack.playbackHeadPosition through a native call.
        // `cal` reuses elapsedUs instead of re-reading the clock, so the logged value is the one
        // latencyInMs was derived from.
        if (BuildConfig.DEBUG) {
            LogContext.log.d(
                TAG,
                "st=$playStartTimeInUs\t cal=${elapsedUs / 1000}\t " +
                    "play=${getAudioTimeUs() / 1000}\t latency=$latencyInMs"
            )
        }
```

`OpusStreamPlayer.kt:241-244`（改动前，HEAD，`consumeDecodedPcm` 内）

```kotlin
                if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayer.playState) {
                    LogContext.log.i(TAG, "Play PCM[${pcmData.size}]")
                    audioTrackPlayer.write(pcmData)
                }
```

`OpusStreamPlayer.kt:267-272`（改动后）

```kotlin
                if (AudioTrack.PLAYSTATE_PLAYING == audioTrackPlayer.playState) {
                    if (BuildConfig.DEBUG) {
                        LogContext.log.i(TAG, "Play PCM[${pcmData.size}]")
                    }
                    audioTrackPlayer.write(pcmData)
                }
```

审查要点：
- A.9 的全部要点同样适用。OPUS 侧 `initDecoderLocked` 入口守卫是 `csd0 == null && audioData.size == 83`（106 行），
  `csd0/csd1/csd2` 与 `audioDecoder` 同置同清（136-138、158-161、246-249 行），"进入时 `audioDecoder == null`" 同样成立。
- OPUS 独有：`onSuccess` 里 `ioScope.launch { consumeDecodedPcm() }`（140 行）是 ioScope 的普通子协程，会被 stopper 的
  `cancelAndJoin()` 等到；而 `releaseDecoderAsync` 的协程不会。两者不要混淆。
- OPUS 侧 `onDecoded` 只 `offer` 到队列，不碰 `audioTrackPlayer`；真正写 track 的是 `consumeDecodedPcm()`，它在 stopper
  的 `cancelAndJoin()` 之后不可能再运行，所以 OPUS 的"回滚协程晚于 track release"风险比 AAC 更小。

---

### A.11 audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.7）。

四处改动：(1) `rollbackFailedInit()` 的 encoder 释放改为 `launch(NonCancellable) { releaseAndJoin() }`，
`releaseCompleted.complete(Unit)` 移入该协程的 `finally`；(2) `failRecordStart()` 调序并改为
`launch(NonCancellable) { finishRecorderReleaseAndJoin(false) }`；(3) 删除 `@Deprecated fun stopRecord()` 与
`finishRecorderRelease()`；(4) 三处 KDoc 修正（`released` 字段注释、`releaseAdvancedFeatures` 注释、原挂在
`stopAudioRecord()` 上但描述 `finishRecorderRelease*` 的 KDoc 移回 `finishRecorderReleaseAndJoin()`）。

`MicRecorder.kt:209-223`（改动前，HEAD，`rollbackFailedInit`）

```kotlin
    private fun rollbackFailedInit(record: AudioRecord?, cause: Throwable) {
        LogContext.log.e(TAG, "MicRecorder init failed; rolling back", cause)
        stopped.set(true)
        released.set(true)
        releaseAdvancedFeatures(true)
        runCatchingPreservingCancellation { record?.release() }.onFailure {
            LogContext.log.e(TAG, "rollback: AudioRecord release error", it)
        }
        runCatchingPreservingCancellation { encodeWrapper?.release() }.onFailure {
            LogContext.log.e(TAG, "rollback: encoder release error", it)
        }
        encodeWrapper = null
        ioScope.cancel()
        releaseCompleted.complete(Unit)
    }
```

`MicRecorder.kt:209-229`（改动后）

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

`MicRecorder.kt:289-295`（改动前，HEAD，`failRecordStart`）

```kotlin
    private fun failRecordStart(message: String) {
        LogContext.log.e(TAG, message)
        stopped.set(true)
        ioScope.cancel()
        stopAudioRecord()
        finishRecorderRelease(stopSucceeded = false)
    }
```

`MicRecorder.kt:295-311`（改动后）

```kotlin
    private fun failRecordStart(message: String) {
        LogContext.log.e(TAG, message)
        stopped.set(true)
        stopAudioRecord()
        // startRecord() is a non-suspend public entry point, so the deterministic release cannot
        // be awaited here. NonCancellable so the rollback survives the ioScope.cancel() below.
        //
        // The body must not throw: NonCancellable replaces the parent job, this scope carries no
        // CoroutineExceptionHandler, and both finishRecorderReleaseAndJoin() and the client
        // callback it invokes can throw. An escaping throwable would kill the process.
        ioScope.launch(NonCancellable) {
            runCatchingPreservingCancellation {
                finishRecorderReleaseAndJoin(stopSucceeded = false)
            }.onFailure { LogContext.log.e(TAG, "start rollback release failed", it) }
        }
        ioScope.cancel()
    }
```

`MicRecorder.kt:402-450`（改动前，HEAD，已删除的 `stopRecord()` 与 `finishRecorderRelease()`，及被挪走的 KDoc）

```kotlin
    /**
     * Legacy non-suspend entry point. It keeps the historical synchronous release semantics but
     * cannot wait for the worker to finish; use [stopRecordAndJoin] when completion matters.
     */
    @Deprecated(
        "Non-suspend stop cannot guarantee the record job has exited. " +
            "Use stopRecordAndJoin() for deterministic shutdown.",
        ReplaceWith("stopRecordAndJoin()")
    )
    fun stopRecord() {
        if (!stopped.compareAndSet(false, true)) return
        val ok = stopAudioRecord()
        recordJob?.cancel()
        recordJob = null
        ioScope.cancel()
        finishRecorderRelease(ok)
    }

    /**
     * Idempotent resource release: releases AudioRecord and encoder, and delivers onStop exactly
     * once. Safe to call from either the record job's error path or the teardown flow.
     */
    private fun stopAudioRecord(): Boolean {
        // ...（未改动）
    }

    private fun finishRecorderRelease(stopSucceeded: Boolean) {
        if (!released.compareAndSet(false, true)) return
        var ok = stopSucceeded
        try {
            ok = releaseAdvancedFeatures(ok)
            ok = releaseAudioRecord(ok)
            runCatchingPreservingCancellation { encodeWrapper?.release() }.onFailure {
                ok = false
                LogContext.log.e(TAG, "encoder release error", it)
            }
        } finally {
            completeRecorderRelease(ok)
        }
    }
```

`MicRecorder.kt:392-421`（改动后，保留的唯一停止入口 `stopRecordAndJoin()`；KDoc 第二段为第六轮追加的迁移说明，§J.7）

```kotlin
    /**
     * Deterministic teardown: stop the AudioRecord first (so the native read returns), then join
     * the record job and release resources. This API must be called by an external owner.
     *
     * Replaces the removed non-suspend stopRecord(). Migration: await this from a coroutine the
     * owner controls; it returns only once the record job and the encoder worker have exited and
     * the AudioRecord is released, where stopRecord() released the AudioRecord synchronously but
     * returned without waiting for either worker to exit.
     */
    suspend fun stopRecordAndJoin() {
        // Guard BEFORE any side effect: a self-call (from the record job's own context) must fail
        // fast without half-stopping the recorder or consuming the one-shot `stopped` flag
        // (remediation R-8). NOTE: this rejects only a *direct* self-call; a call wrapped in
        // runBlocking on the record thread has a different Job and still cannot be made safe here.
        val job = recordJob
        require(job !== currentCoroutineContext()[Job]) {
            "stopRecordAndJoin() must be called by an external owner"
        }
        if (!stopped.compareAndSet(false, true)) {
            releaseCompleted.await()
            return
        }
        LogContext.log.i(TAG, "Stop recording audio")
        val ok = stopAudioRecord()

        job?.cancelAndJoin()
        recordJob = null
        ioScope.cancel()
        finishRecorderReleaseAndJoin(ok)
    }
```

`MicRecorder.kt:423-465`（改动后，`stopAudioRecord` 新 KDoc 与保留的 `finishRecorderReleaseAndJoin`）

```kotlin
    /** Stops the AudioRecord so a blocked read() returns. Releases nothing. */
    private fun stopAudioRecord(): Boolean {
        var ok = true
        runCatchingPreservingCancellation {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.stop()
            }
        }.onFailure {
            ok = false
            LogContext.log.e(TAG, "stop error", it)
        }
        return ok
    }

    /**
     * Idempotent resource release: releases AudioRecord and encoder, and delivers onStop exactly
     * once. Safe to call from either the record job's error path or the teardown flow; a second
     * caller waits for the first one's release to complete.
     */
    private suspend fun finishRecorderReleaseAndJoin(stopSucceeded: Boolean) {
        if (!released.compareAndSet(false, true)) {
            releaseCompleted.await()
            return
        }
        withContext(NonCancellable) {
            var ok = stopSucceeded
            try {
                ok = releaseAdvancedFeatures(ok)
                ok = releaseAudioRecord(ok)
                try {
                    encodeWrapper?.releaseAndJoin()
                } catch (e: CancellationException) {
                    ok = false
                    throw e
                } catch (e: Exception) {
                    ok = false
                    LogContext.log.e(TAG, "encoder release error", e)
                }
            } finally {
                completeRecorderRelease(ok)
            }
        }
    }
```

`MicRecorder.kt:119-123 / 369-377`（改动后，KDoc 修正）

```kotlin
    /** Guards [stopRecordAndJoin] re-entry. */
    private val stopped = AtomicBoolean(false)

    /** Guards [finishRecorderReleaseAndJoin] so AudioRecord/encoder release runs once. */
    private val released = AtomicBoolean(false)
// ...
     * Reached through [finishRecorderReleaseAndJoin], which is already behind the one-shot
     * [released] guard, and through [rollbackFailedInit], which burns that guard before calling
     * in. So this runs exactly once either way.
```

审查要点：
- **`launch(NonCancellable)` 脱离 scope**：`ioScope = CoroutineScope(Dispatchers.IO)`（110 行）——`CoroutineScope()` 工厂会
  自动补一个 `Job()`；`launch(NonCancellable)` 用 `NonCancellable` 替换该 Job 作父，`Dispatchers.IO` 保留。
  因此紧随其后的 `ioScope.cancel()` 不影响它。还有一个值得确认的推论：**即使 `ioScope` 已被取消**（例如
  `stopRecordAndJoin()` 之后再调 `startRecord()`），`launch(NonCancellable)` 仍会启动并执行——因为 `launch` 判断活跃性
  看的是新协程自己的父 Job（`NonCancellable`，永远 active）。这保证了迟到的 `failRecordStart()` 也能走到
  `finishRecorderReleaseAndJoin()` → `released` 已为 true → `releaseCompleted.await()` 立即返回，不会二次 `onStop`。
- **`releaseCompleted` 是否在所有路径完成**：
  - 正常 `stopRecordAndJoin()` → `finishRecorderReleaseAndJoin()` → `finally { completeRecorderRelease(ok) }` →
    `finally { releaseCompleted.complete(Unit) }`（479-485 行）。
  - `handleRecordingFailure()`（record job 内）→ 同上。
  - `failRecordStart()` → NonCancellable 协程 → 同上；`runCatchingPreservingCancellation` 外层只负责不让异常逃逸，
    不影响内层 `finally`。
  - `rollbackFailedInit()` → 直接写 `released = true` 绕过 `finishRecorderReleaseAndJoin`，所以由 NonCancellable 协程的
    `finally` 完成——这是本次新增的完成点（改动前是同步完成）。构造函数随即 rethrow，外界拿不到实例，实际无人 await。
  - 唯一未覆盖：`failedEncoder?.releaseAndJoin()` 若永久挂起（codec worker 卡死），deferred 永不完成且占用一个 IO 线程。
    无 awaiter，所以不构成死锁，但属"泄漏一个线程"；改动前同步版会直接卡住构造函数所在线程，相比之下是改善而非退化。
- **`onStop` 恰好一次**：仍由 `onStopNotified.compareAndSet`（488 行）保证，未改。但 `failRecordStart()` 路径的
  **线程与时序语义变了**：改动前 `callback.onStop(false)` 在 `startRecord()` 返回前、调用方线程上同步触发；
  改动后在 `startRecord()` 返回后、`Dispatchers.IO` 线程上异步触发。demo 侧 `RecordCallback.onStop` 若直接操作 UI
  或依赖"startRecord 返回即已回调"，需要核对。仓库内三处实现已核对：`AudioSender.kt:174` 与
  `AudioReceiver.kt:184` 是空体；`AudioActivity.kt:296-300` 在 `runCatching` 里 flush/close 一个输出流，不碰 UI、
  不依赖调用线程。CHANGELOG 已把这一时序变化单列为"行为变更（签名不变）"。
- **自等待风险**：`finishRecorderReleaseAndJoin()` 在 `failRecordStart()` 路径里运行于 NonCancellable 协程，而
  `recordJob` 此时必为 `null`（launch 在 252 行，`failRecordStart` 全部 return 在它之前），不存在"自己 join 自己"。
  `stopRecordAndJoin()` 的 `require(job !== currentCoroutineContext()[Job])` 未改。
- **并发 `stopRecordAndJoin()` 与 `failRecordStart()`**：谁先 CAS 到 `stopped` 谁做 release，另一方
  `releaseCompleted.await()`。两个方向都收敛；`stopAudioRecord()` 可能被调两次（`AudioRecord.stop()` 幂等），
  `stopped` 为 `AtomicBoolean` 但 `failRecordStart` 用 `set(true)` 而非 CAS，所以它永远走 launch → `released` CAS 决定胜负。
- `failRecordStart()` 调序：`ioScope.cancel()` 从第一步移到最后。改动前先 cancel 再 `stopAudioRecord()`，二者无依赖；
  新顺序是 launch 必须先于 cancel（否则普通 `launch` 不会启动——虽然此处用了 NonCancellable 本不受影响，顺序仍是正确的防御）。
- `rollbackFailedInit()` 调序：`encodeWrapper = null` 现在在 launch 之前执行，协程只持有局部 `failedEncoder`，与
  A.9/A.10 的"先 detach 再异步释放"一致。
- 残留检查：`finishRecorderRelease(` 全仓无调用；`cancel`/`cancelAndJoin`/`NonCancellable`/`CompletableDeferred` 等
  import 仍被使用；`stopped`、`released`、`onStopNotified` 三个门闩的 KDoc 引用名已更新，无指向已删方法的 `[...]` 链接（已核）。
- `encodeWrapper` 仍是 `private var ... : AudioEncoderWrapper?`（115 行），在 record job 里以 `encodeWrapper?.encode(frame)`
  读取（263 行）；`rollbackFailedInit` 置 `null` 发生在构造期，record job 尚不存在，无可见性问题。

---

### A.12 audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.7）。

`fun release()` 替换为 `suspend fun releaseAndJoin()`：三个子对象改调挂起版本，**自身 `audioTrackPlayer.release()`
从第一步移到 `finally`**（最后一步）；新增大段 KDoc 论证顺序安全性（spec §20.3 / 第八次复审 M2）。

`AudioPlayer.kt:163-170`（改动前，HEAD）

```kotlin
    fun release() {
        LogContext.log.w(TAG, "release()")
        audioTrackPlayer.release()

        decoderWrapper?.release()
        aacStreamPlayer?.stopPlaying()
        opusStreamPlayer?.stopPlaying()
    }
```

`AudioPlayer.kt:163-198`（改动后；KDoc 末段为第六轮追加的迁移说明，§J.7）

```kotlin
    /**
     * Releases every resource this player owns and waits for the workers to exit.
     *
     * The three AudioTrackPlayer instances involved are independent: this player builds its own,
     * and each stream player builds another. AudioPlayer's constructor also gives a session
     * either a decoder wrapper or a stream player, never both, so these calls cannot contend for
     * the same track and their relative order is free.
     *
     * The track this player always builds is released in a finally: StreamPlayerStopper rethrows
     * CancellationException and stopAndJoin() can also fail its own require(), so an exception
     * from the calls above would otherwise leak the one AudioTrack that exists in every session.
     *
     * Releasing that track last is also safe on the PCM path, where it is the one being written:
     * with AudioType.PCM there is no wrapper and no stream player, so nothing before the finally
     * touches it, and callers join their writer before calling this (AudioActivity's PCM
     * playback does exactly that). With COMPRESSED_PCM the wrapper's releaseAndJoin() is a no-op.
     *
     * One worker is deliberately not awaited: a stream player's init-failure rollback is
     * fire-and-forget by design, see AacStreamPlayer.releaseDecoderAsync().
     *
     * Replaces the removed non-suspend release(). Migration: await this from a coroutine the
     * owner controls instead of calling release() synchronously. Two things changed on the way:
     * the decoder wrapper and the stream players are now awaited rather than signalled, and this
     * player's own track is released last, in the finally, where release() used to release it
     * first.
     */
    suspend fun releaseAndJoin() {
        LogContext.log.w(TAG, "releaseAndJoin()")
        try {
            decoderWrapper?.releaseAndJoin()
            aacStreamPlayer?.stopPlayingAndJoin()
            opusStreamPlayer?.stopPlayingAndJoin()
        } finally {
            audioTrackPlayer.release()
        }
    }
```

审查要点：
- KDoc 第一段的事实核对：`audioTrackPlayer` 由本类无条件构造（43-44 行）；`AacStreamPlayer` / `OpusStreamPlayer` 各自再建一个
  （A.9 55 行、A.10 60 行）；`init` 的 `when(type)` 三分支互斥（46-84 行），`decoderWrapper` 只在 `else` 分支赋值。成立。
- KDoc 第二段：`StreamPlayerStopper.stopAndJoin()` 确实重抛 CE 且有 `require`（A.7）。改动前 `audioTrackPlayer.release()`
  在第一行，异常时不会漏放；改动后挪到最后，所以必须用 `finally` 兜底——两者等价于"任何路径都释放"，但新版在异常发生时
  `audioTrackPlayer.release()` 会在异常传播中执行。它不会再抛：`AudioTrackPlayer.release()`（`:285-296`）与它内部
  调用的 `stop()` / `pause()` 都用 `runCatchingPreservingCancellation { }.onFailure { log }` 包住，`STATE_UNINITIALIZED`
  时直接返回，因此不会掩盖原异常。
- KDoc 第三段依赖外部契约"callers join their writer before calling this"，仓库内 PCM 调用方是 demo 的 `AudioActivity`
  （`player?.releaseAndJoin()`，`AudioActivity.kt:356`）——它在 `:354-356` 按 `player?.stop()` → `playbackThread?.join()`
  → `player?.releaseAndJoin()` 的顺序执行，契约成立（详见 B.4）。
- `@file:Suppress("unused", "MemberVisibilityCanBePrivate")` 未动；`runCatchingPreservingCancellation` import 仍被
  `getAudioTimeUs()` 使用；`computePresentationTimeUs` 的既有 `@Deprecated` 是 HEAD 已有（305690cee），非本次。
- AudioPlayer 不再有任何非挂起释放入口；Java 调用方（若有）需要自行桥接——仓库内无 Java 调用方（`demo` 为 Kotlin，已核 grep 结果）。

---

### A.13 audio/src/test/kotlin/com/leovp/audio/base/StreamPlayerStopperTest.kt

删除唯一覆盖 `stop()` 的用例；其余 3 个挂起版用例未改。文件内无 `@Suppress("DEPRECATION")`（HEAD 亦无）。

`StreamPlayerStopperTest.kt:21-51`（改动前，HEAD，已删除）

```kotlin
    @Test
    fun `legacy stop releases resources in teardown order`() {
        val events = mutableListOf<String>()
        val scope = CoroutineScope(Job())
        val audioTrackPlayer = mockk<AudioTrackPlayer>()
        val decoder = Any()
        every { audioTrackPlayer.stop() } answers { events += "stop-audio-track" }
        every { audioTrackPlayer.release() } answers { events += "audio-track" }
        val subject = StreamPlayerStopper("Test", scope, audioTrackPlayer) {
            events += "detach-decoder"
            decoder
        }

        subject.stop {
            assertEquals(decoder, it)
            events += "release-decoder"
        }

        assertEquals(
            listOf(
                "detach-decoder",
                "stop-audio-track",
                "release-decoder",
                "audio-track"
            ),
            events
        )
        assertFalse(scope.isActive)
        verify(exactly = 1) { audioTrackPlayer.stop() }
        verify(exactly = 1) { audioTrackPlayer.release() }
    }
```

保留用例（改动后 22 / 59 / 90 行）：
- `suspending stop propagates cancellation after common cleanup`：release 回调抛 CE → 断言 CE 被重抛、事件顺序
  `detach-decoder → stop-audio-track → release-decoder → audio-track`、scope 不活跃、`stop()`/`release()` 各一次。
- `suspending stop logs ordinary decoder failures without throwing`：release 回调 `error(...)` → 不抛，顺序与计数同上。
- `suspending stop rejects calls from its owned scope before side effects`：从 ownerJob 子协程调用 → `IllegalArgumentException`，
  scope 仍活跃，`stop()`/`release()` 零次。

审查要点：
- 被删用例的**全部顺序/计数断言**在前两个保留用例中都有等价覆盖；唯一未被复制的是 `assertEquals(decoder, it)`
  ——"传给 release 回调的正是 detach 返回的那个实例"这一身份断言。保留用例的 detach 返回内联 `Any()`，回调忽略参数。
  属轻微覆盖缺口，不是回归。
- 删除后 import 无悬空：`assertEquals`/`assertFalse`/`isActive`/`Job`/`verify`/`every` 均仍被使用（已核）。

---

### A.14 audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronousTest.kt

删除 19 处 `@Suppress("DEPRECATION")`（18 个测试 + 1 个私有辅助函数）；所有 `subject.release()` 改为
`runBlocking { subject.releaseAndJoin() }`（非 `runTest` 用例）或直接 `subject.releaseAndJoin()`（`runTest` 用例）；
新增 import `kotlinx.coroutines.runBlocking`。**所有断言原样保留**。一个用例改名并改写体。

逐个用例（改动后行号；"断言"列为改动前后相同，除非另注）：

| 用例 | 转换 | 断言（不变） |
|---|---|---|
| `late input callback is ignored after release begins`（28） | `release()` → `runBlocking { releaseAndJoin() }` | release 后触发 `onInputBufferAvailable` → `getInputBuffer`/`queueInputBuffer` 零次 |
| `release waits for an active asynchronous input callback`（43） | executor 内 `release()` → `runBlocking { releaseAndJoin() }` | 回调阻塞时 release future 100ms 超时、`release()` 零次；放行后 `queueInputBuffer(2,…)` 一次、`release()` 一次 |
| `input callback failure returns dequeued buffer`（80） | 同上 | `queueInputBuffer(4,0,0,0,0)` 一次、`failureCount == 1` |
| `null input buffer is returned to codec`（96） | 同上 | `queueInputBuffer(5,0,0,0,0)` 一次 |
| `oversized input returns buffer and reports failure`（111） | 同上 | `queueInputBuffer(6,0,0,0,0)` 一次、`failureCount == 1` |
| `failed EOS submission preserves EOS flag during compensation`（127） | 同上 | EOS `queueInputBuffer(7,…)` 恰两次、`failureCount == 1` |
| `output lookup failure still returns buffer and reports failure`（163） | 同上 | `releaseOutputBuffer(8,false)` 一次、`failureCount == 1` |
| `late callbacks are ignored after terminal release`（183） | 同上 | 四类迟到回调全部被忽略：`getInputBuffer`/`getOutputBuffer` 零次、format 仍为 initial、`errorCount == 0` |
| `EOS output data is delivered before completion and buffer is released`（210） | 同上 | `outputCount == 1`、`endCount == 1`、`releaseOutputBuffer(3,false)` 一次 |
| `null EOS output still reports completion and releases the buffer`（234） | 同上 | `outputCount == 0`、`endCount == 1`、`releaseOutputBuffer(9,false)` 一次 |
| `concurrent start admits only one caller`（254） | 同上 | 两个并发 `start()` 恰一个成功、`start()`/`release()` 各一次 |
| **`successful release remains exactly once across both release APIs` → `successful release remains exactly once across repeated calls`**（285，`runTest`） | 体由 `release(); release(); releaseAndJoin()` 改为三次 `releaseAndJoin()` | `releaseHookCount == 1`、`release()` 一次 |
| `release waits for active format callback`（301） | 经辅助函数 | 见辅助函数 |
| `release waits for active error callback`（348） | 经辅助函数 | 见辅助函数 |
| `codec session can only be started once`（365） | `release()` → `runBlocking { releaseAndJoin() }` | 第二次 `start()` 抛 ISE 且 message 相同、`start()`/`release()` 各一次 |
| `start failure releases partially initialized codec and is terminal`（381） | 同上 | 两个 ISE message、`releaseHookCount == 1`、`release()` 一次 |
| `release before start makes the one-shot session terminal`（399） | 同上 | 之后 `start()` 抛 ISE、`releaseHookCount == 1`、`start()`/`release()` 零次 |
| `release racing start cleans partial codec and keeps session terminal`（413） | executor 内 `release()` → `runBlocking { releaseAndJoin() }` | release 等待 start、start 以 ISE 失败、之后 `start()` 抛 ISE、`release()` 一次 |
| 私有 `assertReleaseWaitsForCallback`（465） | executor 内 `release()` → `runBlocking { releaseAndJoin() }` | 回调阻塞时 release future 100ms 超时；放行后 `release()` 一次 |

改动后 `BaseMediaCodecAsynchronousTest.kt:284-298`（改名用例全文）

```kotlin
    @Test
    fun `successful release remains exactly once across repeated calls`() = runTest {
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        val callbackSlot = slot<MediaCodec.Callback>()
        every { mediaCodec.setCallback(capture(callbackSlot)) } just Runs
        val subject = TestCodec(mediaCodec)

        subject.start()
        subject.releaseAndJoin()
        subject.releaseAndJoin()
        subject.releaseAndJoin()

        assertEquals(1, subject.releaseHookCount)
        verify(exactly = 1) { mediaCodec.release() }
    }
```

未改动用例（HEAD 已用 `releaseAndJoin()` 或不涉及 release）：`error callback notifies owner once outside the codec lock`、
`error hook failure is isolated and still notifies owner`、`deterministic release invokes subclass cleanup hook`。

审查要点：
- **`runBlocking` 自等待风险**：`BaseMediaCodec.releaseAndJoin()` 开头 `require(codecJob !== currentCoroutineContext()[Job])`
  ——`runBlocking` 块内的 Job 是 runBlocking 自己的，永远不等于 `codecJob`，require 必过。异步模式（`BaseMediaCodecAsynchronous`）
  是否根本不设置 `codecJob` 未核实；即便设置，其 worker 也不会运行在测试线程/executor 线程的 runBlocking 里。
- **阻塞语义是否与旧 `release()` 等价**：旧 `release()` 是 `codecJob?.cancel()`（不 join）+ `releaseCodecOnce()`（取
  `codecOperationLock`）；新版多了 `cancelAndJoin()`。在"release waits for ..."三个用例里，让 release 等待的是回调线程持有的
  `ReentrantLock`，而非 codecJob；锁由另一线程持有、runBlocking 阻塞当前线程等锁，不是协程自等待。100ms 超时断言的
  成立条件没变。
- 对 `runTest` 用例（285 行）：`releaseAndJoin()` 内 `withContext(NonCancellable)` + `ioScope.cancel()` 在
  `runTest` 虚拟时间下的行为与 HEAD 已有的 `deterministic release invokes subclass cleanup hook`（452 行）相同模式，非新引入。
- 改名用例的意图从"两套 API 混用仍只释放一次"收窄为"同一 API 重复调用仍只释放一次"——因为另一套 API 已不存在，
  这是唯一可能的保留方式；`release()` 旧实现的"第二次调用 no-op"分支已随方法一起删除，不再需要覆盖。
- `assertFalse` 未在本文件 import（HEAD 亦无），无悬空；新增 `runBlocking` import 在 16 处被使用（15 个用例 + 辅助函数）。

---

### A.15 audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronousTest.kt

删除 1 处 `@Suppress("DEPRECATION")`；唯一用旧 API 的用例改名 `legacy release waits for the active codec iteration` →
`release waits for the active codec iteration`，executor 内改为 `runBlocking { subject.releaseAndJoin() }`，断言消息
`"release() must wait ..."` → `"releaseAndJoin() must wait ..."`；新增 import `kotlinx.coroutines.runBlocking`。其余 6 个用例未动。

`BaseMediaCodecSynchronousTest.kt:22-42`（改动前，HEAD，核心片段）

```kotlin
    @Suppress("DEPRECATION")
    @Test
    fun `legacy release waits for the active codec iteration`() {
        // ...
            val releaseFuture = releaseExecutor.submit { subject.release() }

            assertFailsWith<TimeoutException> { releaseFuture.get(100, TimeUnit.MILLISECONDS) }
            assertFalse(releaseFuture.isDone, "release() must wait for the active codec iteration")
            verify(exactly = 0) { mediaCodec.release() }
        // ...
```

`BaseMediaCodecSynchronousTest.kt:23-55`（改动后，完整用例）

```kotlin
    @Test
    fun `release waits for the active codec iteration`() {
        val enteredInput = CountDownLatch(1)
        val continueInput = CountDownLatch(1)
        val mediaCodec = mockk<MediaCodec>(relaxed = true)
        every { mediaCodec.dequeueInputBuffer(0) } returns 0
        every { mediaCodec.getInputBuffer(0) } returns ByteBuffer.allocate(16)
        every { mediaCodec.dequeueOutputBuffer(any(), 0) } returns MediaCodec.INFO_TRY_AGAIN_LATER
        val subject = BlockingCodec(mediaCodec, enteredInput, continueInput)
        val releaseExecutor = Executors.newSingleThreadExecutor()

        try {
            subject.start()
            assertTrue(enteredInput.await(2, TimeUnit.SECONDS), "Worker did not enter onInputData")

            val releaseFuture =
                releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }

            assertFailsWith<TimeoutException> { releaseFuture.get(100, TimeUnit.MILLISECONDS) }
            assertFalse(
                releaseFuture.isDone,
                "releaseAndJoin() must wait for the active codec iteration"
            )
            verify(exactly = 0) { mediaCodec.release() }

            continueInput.countDown()
            releaseFuture.get(2, TimeUnit.SECONDS)
            verify(exactly = 1) { mediaCodec.release() }
        } finally {
            continueInput.countDown()
            releaseExecutor.shutdownNow()
        }
    }
```

审查要点：
- 断言集合不变：100ms 超时、`isDone == false`、`release()` 零次 → 放行后 `release()` 一次。
- 同步模式下 `codecJob` 非空（worker 在 `onInputData` 里阻塞于 latch，同时持有 `codecOperationLock`）。
  新版 `releaseAndJoin()` 在 `cancelAndJoin()` 处等待 worker 退出（worker 在 `continueInput.await(2s)` 后返回），
  旧版在 `releaseCodecOnce()` 取锁处等待；两者都在 `continueInput.countDown()` 前不可能完成，断言语义等价，
  新版额外保证 worker 已 join。
- `runBlocking` 跑在 executor 线程，`codecJob` 跑在 `Dispatchers.IO`；`require(codecJob !== currentJob)` 必过，无自等待。
- 两处换行是为了 100 列限制（39、42-45 行）；该文件现在没有任何 `@Suppress`。

---

### A.z 本区自查结果

以下 grep 均在工作树（未提交状态）上执行，仓库根目录为 `/home/coding04/yhz61010/leo_projects/android`。

1. 旧 API 调用方残留（`audio/` 与 `demo/`）

```
grep -rnE '\.stopPlaying\(\)|\.stopRecord\(\)|finishRecorderRelease\(|StreamPlayerStopper\.stop\(' audio/ demo/
```

结果：无匹配（exit=1）。

2. `audio/src/main` 中残留的 `fun release()`

```
grep -rn 'fun release()' audio/src/main
```

结果：仅 `audio/src/main/kotlin/com/leovp/audio/AudioTrackPlayer.kt:285:    fun release() {`（AudioTrack 的同步释放，
不在本次删除范围，`StreamPlayerStopper.releaseAudioTrack()` 与 `AudioPlayer.releaseAndJoin()` 的 `finally` 都依赖它）。
`audio/` 与 `demo/` 中无任何 `override fun release()` 属于 `BaseMediaCodec` 子类或两个 wrapper 接口的实现
（demo 的 9 处 `override fun release()` 全部是 netty `BaseChannelInboundHandler` 的覆写）。

3. `@Suppress("DEPRECATION")` 计数

```
grep -rn '@Suppress("DEPRECATION")' audio/src/test | wc -l   # → 0
grep -rn '@Suppress("DEPRECATION")' audio/src/main | wc -l   # → 2
grep -rn '@Suppress("DEPRECATION")' demo/src/main  | wc -l   # → 11
```

合计 `audio/src/main + demo/src/main = 13`，与目标一致。按文件分布：
`audio/.../exts/AudioExt.kt` 2 处（`AudioManager.isSpeakerphoneOn` 的 API 级别兼容分支，与本次无关）；
`demo/.../MainActivity.kt` 1、`BluetoothClientActivity.kt` 3、`BluetoothServerActivity.kt` 2、
`SimpleAdapter.kt` 1、`WifiActivity.kt` 4。audio 模块的测试目录为 0，本次未新增任何抑制。

4. 已改 audio 文件中超过 100 列的非 import 行

```
for f in $(git diff HEAD --name-only -- audio/); do
  awk -v f="$f" 'length($0)>100 && $0 !~ /^import / { print f":"NR": len="length($0) }' "$f"
done
```

结果：无输出（15 个文件均无 >100 列的非 import 行）。

5. 附加核对

- 两个 wrapper 接口在全仓 `*.kt` 的实现者：`AacEncoderWrapper`、`OpusEncoderWrapper`、`CompressedPcmEncoderWrapper`、
  `CompressedPcmDecoderWrapper`，全部已覆写 `suspend fun releaseAndJoin()`；其余命中（`AudioPlayer`、`MicRecorder`、
  `AudioEncoderManager`、`AudioDecoderManager`）只是字段/返回类型引用。
- `BaseMediaCodec` 子类全部位于 `audio/`（`AacEncoder`、`AacDecoder`、`OpusEncoder`、`OpusDecoder`、`AacFilePlayer`、
  `BaseMediaCodecSynchronous`、`BaseMediaCodecAsynchronous` 与两个测试），无一覆写已删除的 `release()`。
- `AacEncoderWrapper` / `OpusEncoderWrapper` 中 `LogContext`、`runCatchingPreservingCancellation` 引用已随 `release()` 清零。
- KDoc 中 `[stop]` 链接的 4 处命中（`AacFilePlayer.kt:151`、`AudioPlayer.kt:148`、`AudioTrackPlayer.kt:260`、
  `OpusFilePlayer.kt:118`）指向的都是仍然存在的 `stop()` 方法，不是已删除的 `StreamPlayerStopper.stop()`。
- demo 侧对新 API 的调用点（仅计数，内容属 demo 分区）：`AudioSender.kt`、`AudioReceiver.kt`、`AudioActivity.kt`、
  `ADPCMActivity.kt` 均已出现 `releaseAndJoin()` / `stopRecordAndJoin()`。

6. 本区未执行、须由 Codex 或作者另行确认

- 未运行 Gradle（编译 / `testDebugUnitTest` / `detekt` / `ktlintCheck`），与 spec §20 "全部未经 Gradle 或真机验证"一致。
- 未确认项目 `10-configs/detekt.yml` 是否对 `launch(NonCancellable)` 或带注释空函数体有自定义规则。
- 未确认 `AudioTrackPlayer.release()` 内部是否自行捕获异常（影响 `AudioPlayer.releaseAndJoin()` `finally` 掩盖原异常的可能性）。
- 未确认 `MicRecorder.failRecordStart()` 路径下 `onStop(false)` 改为异步 IO 线程回调后，demo 的 `RecordCallback.onStop`
  实现是否仍正确（UI 线程 / 时序假设）。
- 真机项（Camera/音频/MediaCodec 相关修改按 CLAUDE.md 必须真机验证）全部未执行。

## B. demo 音频示例

**范围**：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/` 下 4 个已修改文件（`AudioActivity.kt`、`ADPCMActivity.kt`、`receiver/AudioReceiver.kt`、`sender/AudioSender.kt`）与 4 个未跟踪新文件（`SuspendTeardownGate.kt`、`TeardownSupport.kt` 及对应两个测试）。基线 `HEAD = d3058e2bc`，所有改动均未提交。本节所有「改动前」代码取自 `git show HEAD:<file>`，「改动后」代码与行号取自当前工作树。

**背景摘要**（spec `00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md` §4.7 与 §20，plan Task 3）：

- `AudioSender.stop()` / `AudioReceiver.stopServer()` 都可同时由 Activity（`onStop()`）与 netty 回调（`onDisconnected`/`onFailed`、`onClientDisconnected`/`onStartFailed`）发起。改为 suspend 关闭后交错点增多，不能再靠 `AudioTrack.state` 的 check-then-act 或 `runCatching` 充当并发释放保护。方案：每个 owner 持有一个**一次性** `SuspendTeardownGate`，首个调用者在 `NonCancellable` 中跑完整关闭，后续调用者只等待同一结果（§4.7.1）。
- netty 回调是非挂起接口，且 `onFailed()` 会从 `connect()` 内部同步回调、而 `connect()` 跑在 `AudioSender.ioScope` 上；若在那里直接跑会 `cancelAndJoin()` 自己 scope 的 `stop()` 必死锁。因此两个类各加一个永不取消的 `cleanupScope`（`SupervisorJob + Dispatchers.IO + CoroutineExceptionHandler`），回调里 `cleanupScope.launch { stop() }`（§4.7.2）。
- `AudioSender` 两个 `ArrayBlockingQueue` 改为 `Channel`（获得 `close()` 语义；**不是**修线程泄漏——`runInterruptible { take() }` 早已解决）；`AudioReceiver` 保留 `ArrayBlockingQueue`，因为该文件已 import `io.netty.channel.Channel`（§4.7.3/§4.7.4）。
- 关闭顺序固定为 `stop()` → `cancelAndJoin()` → `releaseAndJoin()`，且 `audioPlayer?.stop()` 与 `ownerJob?.cancelAndJoin()` 必须是 `teardownSteps()` 的两个独立 step（第十次复审 H1）。
- 门闩只保证"关闭跑一次"，保证不了"关闭后无人再建资源"，故加 owner 级 `stopping: AtomicBoolean`，在 `start*()` 与接入回调开头自查（第八次复审 H2）。
- §20（2026-10-14 代码审查修正）：`stopping` 只缩窄未关闭 check-then-act 窗口 → 四处在**赋值之后再读一次** `stopping`，为真则先 `teardownGate.awaitCompletion()` 再补偿释放（`SuspendTeardownGate` 新增 `awaitCompletion()`）；被 teardown 读取的字段加 `@Volatile`；`ADPCMActivity` 的 `previousJob?.join()` 包进 `withContext(NonCancellable)` 并后接 `ensureActive()`；`AudioActivity.onStop()` 先把 `audioReceiver`/`audioSender` 置 `null` 再调度关闭；`SuspendTeardownGateTest` 第 2 个用例因 Gradle `-ea` 开启 coroutines DEBUG 模式（`withContext()` 重抛 stack-trace-recovered 副本）而把 `assertSame(expected, first)` 改为断言类型与 message。

---

### B.1 新增文件

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.10）。

#### B.1.1 `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt`（85 行，未跟踪）

契约：**单 owner、单会话、不可重置、无门闩层超时**。`run()` 用 `AtomicBoolean.compareAndSet` 选出 leader；leader 在 `withContext(NonCancellable)` 中执行 block，follower 只 `completed.await()`，拿到 leader 抛出的**同一个 Throwable 实例**并重抛。两个 catch 子句而不是 catch 内 `is` 判型，是为与仓库既有写法（`StreamPlayerStopper.kt:33-36`、`AudioActivity.launchCleanup`）一致并避开 detekt `InstanceOfCheckForException`。`CancellationException` 不写入 `completed`（`failure` 保持 null）：发到 follower 的无关协程里会被当成"自己被取消"静默吞掉；但 body 跑在 `NonCancellable` 下，这里的 CE 是 `StreamPlayerStopper.stopAndJoin()` / `MicRecorder.finishRecorderReleaseAndJoin()` 故意重抛的**真实关闭失败**，所以 leader 侧至少记一条 `LogContext.log.e` 再重抛。`awaitCompletion()`（§20.1 新增）只等待、不成为 leader、不重抛失败，供"赋值后再读 `stopping` 为真"的发布方在补偿释放前先等门闩跑完，避免补偿的 `releaseAndJoin()` 与门闩自己的 `cancelAndJoin()` 并发；若无人调用 `run()` 它会永久等待，所以调用方必须先检查 `stopping`。第六轮（§J.10）在类 KDoc 末尾追加「Not re-entrant」一段：block 不得在同一协程内同步再调 `run()` / `awaitCompletion()`——`started` 已置位，等的是自己；要在 teardown 内部再触发 teardown 的回调必须 launch 到另一个 scope，现有调用点都是这么做的。

```kotlin
package com.leovp.demo.basiccomponents.examples.audio

import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * One-shot teardown coordination for one owner instance.
 *
 * This gate is deliberately not resettable. AudioSender and AudioReceiver are single-session
 * owners: every new start creates a new owner and therefore a new gate. Reusing either owner for
 * another start/stop cycle would make later stop calls observe the first result without running
 * cleanup and is outside this contract.
 *
 * Followers await the same completion and receive the very Throwable instance the leader threw
 * out of [run].
 *
 * Cancellation is the exception, and the asymmetry is deliberate: the body runs under
 * [NonCancellable], so a CancellationException leaving it is a real teardown failure rather than
 * a cancelled caller - but republished into an unrelated follower it would read as "you were
 * cancelled" and be swallowed without a trace. The leader therefore logs it and rethrows, while
 * followers are told the teardown completed. Worth knowing at the call sites: after such a
 * failure a follower - and AudioActivity.launchCleanup - reports success, and the leader's log
 * line is the only record of it.
 *
 * There is no gate timeout: timing out here would let a caller proceed while
 * cleanup was still mutating resources. Every operation inside the block must therefore be
 * bounded itself; a stuck operation stalls all followers and must be diagnosed at that resource
 * boundary.
 *
 * Not re-entrant: the block must not call [run] or [awaitCompletion] synchronously on its own
 * coroutine - `started` is already set, so it would await its own completion for good. A callback
 * fired from inside the teardown that wants a teardown launches it on another scope, which is
 * what every current call site does.
 */
internal class SuspendTeardownGate {
    companion object {
        private const val TAG = "TeardownGate"
    }

    private val started = AtomicBoolean(false)
    private val completed = CompletableDeferred<Throwable?>()

    /**
     * Suspends until the teardown has run to completion, without becoming its leader and without
     * rethrowing its failure.
     *
     * For code that lost a race with [run]: it built a resource after the teardown had already
     * read the field as absent, so it has to release that resource itself. Waiting here first
     * keeps that release from overlapping the teardown's own steps. Waits forever if nobody ever
     * calls [run]; callers check the owner's `stopping` flag before coming here.
     */
    suspend fun awaitCompletion() {
        completed.await()
    }

    suspend fun run(block: suspend () -> Unit) {
        if (!started.compareAndSet(false, true)) {
            completed.await()?.let { throw it }
            return
        }
        var failure: Throwable? = null
        try {
            withContext(NonCancellable) { block() }
        } catch (cancellation: CancellationException) {
            // Not published to followers: thrown into an unrelated follower coroutine it reads as
            // "this caller was cancelled" and is swallowed - AudioActivity.launchCleanup rethrows
            // that branch without a log, and a launch on cleanupScope never routes it to the
            // exception handler. But it is not a cancelled caller here either: the body runs
            // under NonCancellable, so this is a real teardown failure that
            // StreamPlayerStopper.stopAndJoin() or MicRecorder.finishRecorderReleaseAndJoin()
            // rethrew on purpose. Log it or it leaves no trace anywhere.
            LogContext.log.e(TAG, "Teardown body was cancelled", cancellation)
            throw cancellation
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            completed.complete(failure)
        }
    }
}
```

#### B.1.2 `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupport.kt`（46 行，未跟踪）

契约：`teardownSteps()` 把关闭平铺成 vararg lambda，**每一步都执行**，首个失败作为根因重抛、后续失败以 `addSuppressed` 挂上；嵌套 `try/finally` 做不到——finally 里抛出会丢掉 try 已抛出的那个，门闩会把一个晚到的无关失败广播给 follower。每个 step 只做一件事：两个动作共用一个 lambda就共用一个 `try`，前者抛出会静默跳过后者。`containsJob()` 判断 `target` 是否是 this 的**后代**（不含自身）：关闭入口要拒绝"从被关闭 scope 内部调用自己"，而 `launch()` 返回的总是子 Job，身份比较 `caller !== ownerJob` 对恰好会死锁的调用方永远不触发。与 `StreamPlayerStopper` 的私有同名函数镜像。第五轮 I.5 之后 `catch` 里多了同一实例守卫：只有 `first !== e` 时才 `addSuppressed`，否则 `addSuppressed(itself)` 抛出的 `IllegalArgumentException` 会逃出 catch、跳过其余步骤。

```kotlin
package com.leovp.demo.basiccomponents.examples.audio

import kotlinx.coroutines.Job

/**
 * Runs every teardown step even if an earlier one fails, then rethrows the first failure with
 * the later ones attached as suppressed.
 *
 * Nested try/finally would do the first half only: a throwing finally discards the throwable
 * the try had already raised, so the gate would publish a late, unrelated failure to its
 * followers while the root cause vanished.
 *
 * Each step must do exactly one thing. Two actions in one lambda share one try, so a failure in
 * the first silently skips the second - which is the behaviour this helper exists to prevent.
 */
internal suspend fun teardownSteps(vararg steps: suspend () -> Unit) {
    var failure: Throwable? = null
    for (step in steps) {
        try {
            step()
        } catch (e: Throwable) {
            val first = failure
            if (first == null) {
                failure = e
            } else if (first !== e) {
                // Guarded: addSuppressed(itself) throws IllegalArgumentException, and that
                // throwable would escape this catch and skip every remaining step - exactly
                // what this helper exists to prevent. Two steps report one instance when a
                // resource stores its failure and republishes it to every caller.
                first.addSuppressed(e)
            }
        }
    }
    failure?.let { throw it }
}

/**
 * True when [target] is a descendant of this job. A job does not contain itself.
 *
 * A teardown entry point that cancel-and-joins its own scope must reject callers running
 * anywhere inside that scope, not just callers holding the scope's own Job - launch() always
 * hands a child Job, so an identity check would never fire. Mirrors the private helper in
 * StreamPlayerStopper.
 */
internal fun Job.containsJob(target: Job): Boolean =
    children.any { child -> child === target || child.containsJob(target) }
```

注意：`teardownSteps()` 的 `catch (e: Throwable)` 也会捕获 `CancellationException` 并继续下一步——这是有意的：它总在门闩的 `NonCancellable` 体内运行，CE 在这里同样是"某一步的真实失败"，最终作为 `failure` 重抛给门闩的 CE 分支。

#### B.1.3 `demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt`（127 行，未跟踪）

5 个用例，各自钉住的行为：

1. `concurrent callers share one teardown` — 第二个调用者不进入 block（`error("must not run twice")` 不触发），在 leader 完成前保持未完成，完成后正常返回；`calls == 1`。
2. `failure is shared with later callers` — leader 抛出的 `IllegalStateException` 类型与 message 与原异常一致（不断言同一实例，原因见代码注释：coroutines DEBUG 模式下 `withContext()` 重抛副本），follower 拿到的是 **leader 从 `run()` 抛出的同一实例**（`assertSame(leaderFailure, second)`）；失败的 teardown 也是完成的 teardown，`awaitCompletion()` 不重抛。
3. `awaitCompletion waits for the leader without becoming one` — 先于 `run()` 调用的 `awaitCompletion()` 不会触发 block，也不会在 leader 完成前返回；leader 完成后返回；`calls == 1`。
4. `caller cancellation does not interrupt active teardown` — leader 协程被 `cancel()` 后 block 仍跑到 `finished.complete(Unit)`（`NonCancellable`），follower 一直等到它完成。
5. `cancellation from the teardown body is not shared with later callers` — block 抛 `CancellationException` 时 leader 收到 CE，follower 收到 `null`（正常返回）。

```kotlin
package com.leovp.demo.basiccomponents.examples.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SuspendTeardownGateTest {
    @Test
    fun `concurrent callers share one teardown`() = runTest {
        val gate = SuspendTeardownGate()
        val entered = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        var calls = 0

        val first = async {
            gate.run {
                calls += 1
                entered.complete(Unit)
                allowCompletion.await()
            }
        }
        entered.await()
        val second = async { gate.run { error("must not run twice") } }
        runCurrent()

        assertFalse(second.isCompleted)
        allowCompletion.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, calls)
    }

    @Test
    fun `failure is shared with later callers`() = runTest {
        val gate = SuspendTeardownGate()
        val expected = IllegalStateException("teardown failed")

        val first = runCatching { gate.run { throw expected } }.exceptionOrNull()
        val second = runCatching { gate.run { error("must not run twice") } }.exceptionOrNull()

        // Not assertSame(expected, first): with kotlinx.coroutines debug mode on - which the
        // Gradle test JVM switches on through -ea - withContext() rethrows a stack-trace-recovered
        // copy whose cause is the original. The contract is that followers get the very instance
        // the leader threw out of run(), and that holds either way.
        val leaderFailure = assertIs<IllegalStateException>(first)
        assertEquals(expected.message, leaderFailure.message)
        assertSame(leaderFailure, second)
        // A failed teardown is still a completed one, and waiting for it must not rethrow.
        gate.awaitCompletion()
    }

    @Test
    fun `awaitCompletion waits for the leader without becoming one`() = runTest {
        val gate = SuspendTeardownGate()
        val allowCompletion = CompletableDeferred<Unit>()
        var calls = 0

        val waiter = async { gate.awaitCompletion() }
        runCurrent()
        assertFalse(waiter.isCompleted)

        val leader = async {
            gate.run {
                calls += 1
                allowCompletion.await()
            }
        }
        runCurrent()
        assertFalse(waiter.isCompleted)

        allowCompletion.complete(Unit)
        leader.await()
        waiter.await()
        assertEquals(1, calls)
    }

    @Test
    fun `caller cancellation does not interrupt active teardown`() = runTest {
        val gate = SuspendTeardownGate()
        val entered = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()

        val first = launch {
            gate.run {
                entered.complete(Unit)
                allowCompletion.await()
                finished.complete(Unit)
            }
        }
        entered.await()
        first.cancel()
        val waiter = async { gate.run { error("must not run twice") } }
        runCurrent()

        assertFalse(waiter.isCompleted)
        allowCompletion.complete(Unit)
        waiter.await()
        first.join()
        assertTrue(finished.isCompleted)
    }

    @Test
    fun `cancellation from the teardown body is not shared with later callers`() = runTest {
        val gate = SuspendTeardownGate()

        // StreamPlayerStopper.stopAndJoin() and MicRecorder.finishRecorderReleaseAndJoin() both
        // rethrow CancellationException on purpose, so a teardown body really can end this way.
        val first = runCatching {
            gate.run { throw CancellationException("teardown cancelled") }
        }.exceptionOrNull()
        val second = runCatching { gate.run { error("must not run twice") } }.exceptionOrNull()

        assertIs<CancellationException>(first)
        assertNull(second)
    }
}
```

说明：第 5 个用例会执行到 `LogContext.log.e(...)`。spec §4.7.1 要求实施时确认 JVM 单测环境下 `LogContext` 未初始化时的行为（参考同目录 `PcmFramePaddingTest`）。本次**未运行** Gradle，该点 **未核实**，请 Codex 在本机跑 `:demo:testDevDebugUnitTest` 时留意。

#### B.1.4 `demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupportTest.kt`（105 行，未跟踪）

4 个用例：

1. `every step runs and the first failure wins` — 四步全部执行（`a,b,c,d`），抛出的是第一个失败的**同一实例**，第二个失败在 `suppressedExceptions` 里。
2. `two steps reporting one instance do not abort the run`（第五轮 I.5 的回归测试）— 两步抛出**同一个**实例时后续步骤仍执行（`b,c`），抛出的仍是那个实例且 `suppressedExceptions` 为空；没有同一实例守卫，`addSuppressed(itself)` 会抛 `IllegalArgumentException` 让第三步跑不到。
3. `nothing is thrown when every step succeeds` — 全成功时不抛，顺序执行。
4. `containsJob accepts children and grandchildren but not strangers` — 对 `scope.launch` 的子 Job 与孙 Job 返回 `true`，对无关 `Job()` 返回 `false`。

```kotlin
package com.leovp.demo.basiccomponents.examples.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class TeardownSupportTest {
    @Test
    fun `every step runs and the first failure wins`() = runTest {
        val ran = mutableListOf<String>()
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")

        val thrown = runCatching {
            teardownSteps(
                { ran += "a" },
                {
                    ran += "b"
                    throw first
                },
                {
                    ran += "c"
                    throw second
                },
                { ran += "d" }
            )
        }.exceptionOrNull()

        // Every step ran: that is the whole reason teardownSteps() exists.
        assertContentEquals(listOf("a", "b", "c", "d"), ran)
        // The root cause is what propagates; the later failure rides along as suppressed.
        assertSame(first, thrown)
        assertContentEquals(listOf(second), thrown?.suppressedExceptions)
    }

    @Test
    fun `two steps reporting one instance do not abort the run`() = runTest {
        val ran = mutableListOf<String>()
        val shared = IllegalStateException("shared")

        val thrown = runCatching {
            teardownSteps(
                { throw shared },
                {
                    ran += "b"
                    throw shared
                },
                { ran += "c" }
            )
        }.exceptionOrNull()

        // Without the identity guard, addSuppressed(itself) throws IllegalArgumentException out
        // of the catch and step "c" never runs.
        assertContentEquals(listOf("b", "c"), ran)
        assertSame(shared, thrown)
        assertContentEquals(emptyList(), thrown?.suppressedExceptions)
    }

    @Test
    fun `nothing is thrown when every step succeeds`() = runTest {
        val ran = mutableListOf<String>()

        teardownSteps({ ran += "a" }, { ran += "b" })

        assertContentEquals(listOf("a", "b"), ran)
    }

    @Test
    fun `containsJob accepts children and grandchildren but not strangers`() = runTest {
        val owner = Job()
        val scope = CoroutineScope(owner)
        val child = CompletableDeferred<Job>()
        val grandChild = CompletableDeferred<Job>()
        val hold = CompletableDeferred<Unit>()

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            child.complete(currentCoroutineContext()[Job]!!)
            launch(start = CoroutineStart.UNDISPATCHED) {
                grandChild.complete(currentCoroutineContext()[Job]!!)
                hold.await()
            }
            hold.await()
        }

        // The identity check this replaced would fail both of these: launch() never hands back
        // the scope's own Job, so `caller !== ownerJob` passes for exactly the callers that
        // deadlock on cancelAndJoin().
        assertTrue(owner.containsJob(child.await()))
        assertTrue(owner.containsJob(grandChild.await()))
        assertFalse(owner.containsJob(Job()))

        hold.complete(Unit)
        owner.cancelAndJoin()
    }
}
```

说明：此处 `assertSame(first, thrown)` 不受 coroutines DEBUG 模式影响——`teardownSteps()` 内部没有协程边界（spec §20.2）。

---

### B.2 `AudioSender.kt`

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.9）。

**做了什么 / 为什么**：`stop()` 改为 suspend，用 `SuspendTeardownGate` + `teardownSteps()` 固定为 7 步关闭（`disconnectManually` → `stopRecordAndJoin` → 关两个 Channel → `audioPlayer.stop()` → `cancelAndJoin` → `releaseAndJoin` → `release`），并在入口加 `containsJob` 自调用守卫；两个阻塞队列改为 `Channel`；netty 回调改走 `cleanupScope`；加 `stopping` 标志（建资源前检查、发布后再检查并补偿）与 `@Volatile` 字段；`start()` 把 `senderClient` 赋值提前到 `connect()` 之前。

#### B.2.1 import 变化

**改动前**（HEAD `:21-27`）：

```kotlin
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
```

**改动后**（工作树 `:14-15`、`:19`、`:22-31`）：

```kotlin
import com.leovp.demo.basiccomponents.examples.audio.SuspendTeardownGate
import com.leovp.demo.basiccomponents.examples.audio.containsJob
...
import com.leovp.demo.basiccomponents.examples.audio.teardownSteps
...
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
```

移除：`ArrayBlockingQueue`、`cancel`、`ensureActive`、`runInterruptible`（均已无使用点）。

#### B.2.2 字段：常量、`cleanupScope`、`teardownGate`、`stopping`、`@Volatile`、Channel

**改动前**（HEAD `:32-46`）：

```kotlin
    companion object {
        private const val TAG = "AudioSender"
    }

    private var ctx: Context? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var senderClient: AudioSenderWebSocket? = null
    private var senderHandler: AudioSenderWebSocketHandler? = null

    private var micRecorder: MicRecorder? = null
    private var audioPlayer: AudioPlayer? = null

    private var recAudioQueue = ArrayBlockingQueue<ByteArray>(64)
    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(64)
```

**改动后**（工作树 `:38-95`）：

```kotlin
    companion object {
        private const val TAG = "AudioSender"
        private const val AUDIO_QUEUE_CAPACITY = 64
    }

    private var ctx: Context? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * Teardown runs here rather than on [ioScope]: stop() cancel-and-joins ioScope itself, and
     * the netty listener callbacks that trigger it are non-suspend interface methods that cannot
     * simply be made suspend. The handler is what keeps a failed teardown off the platform
     * default handler.
     *
     * Deliberately never cancelled - cancelling it from stop() would cancel the very coroutine
     * running stop(), which is the situation this separate scope exists to avoid.
     */
    private val cleanupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                LogContext.log.e(TAG, "Audio sender teardown failed", error)
            }
    )

    /** Makes the Activity path and the netty callback path share one teardown. */
    private val teardownGate = SuspendTeardownGate()

    /**
     * Set before teardown starts, checked by everything that builds a new resource - once before
     * building it and once more after publishing it.
     *
     * The one-shot gate guarantees teardown runs once; it cannot guarantee nobody builds new
     * resources afterwards. Two things still can: start() builds a MicRecorder after launching
     * connect(), and BaseNettyClient.doRetry() can reconnect and call onConnected() while
     * teardown is in flight. Either would leave an AudioRecord or an AudioTrack that the
     * already-consumed gate refuses to release. The check before building turns most of them
     * away; the re-check after publishing catches a stop() that ran in between and read the
     * field as null - the publisher then waits for the gate and releases what it built. Both
     * releases are idempotent, so it does not matter if the gate saw the resource after all.
     */
    private val stopping = AtomicBoolean(false)

    // Volatile where stop() reads them. Publishers write a field and then read `stopping`; stop()
    // writes `stopping` and then reads the fields. With both sides volatile at least one of them
    // sees the other's write, so a resource is released by the gate or by the re-check, never by
    // neither.
    @Volatile
    private var senderClient: AudioSenderWebSocket? = null
    private var senderHandler: AudioSenderWebSocketHandler? = null

    @Volatile
    private var micRecorder: MicRecorder? = null

    @Volatile
    private var audioPlayer: AudioPlayer? = null

    private val recAudioQueue = Channel<ByteArray>(AUDIO_QUEUE_CAPACITY)
    private val receiveAudioQueue = Channel<ByteArray>(AUDIO_QUEUE_CAPACITY)
```

#### B.2.3 `onConnected()`：前置检查 + 发布后再检查

**改动前**（HEAD `:49-61`）：

```kotlin
        override fun onConnected(netty: BaseNettyClient) {
            LogContext.log.i(TAG, "onConnected")
            ctx?.toast("onConnected", debug = true)
            audioPlayer = AudioPlayer(
                ctx!!,
                AudioActivity.audioDecoderInfo,
                defaultAudioType,
                usage = AudioActivity.AUDIO_ATTR_USAGE,
                contentType = AudioActivity.AUDIO_ATTR_CONTENT_TYPE
            )
            sendRecAudioThread()
            startPlayThread()
        }
```

**改动后**（工作树 `:98-120`）：

```kotlin
        override fun onConnected(netty: BaseNettyClient) {
            if (stopping.get()) return
            LogContext.log.i(TAG, "onConnected")
            ctx?.toast("onConnected", debug = true)
            val player = AudioPlayer(
                ctx!!,
                AudioActivity.audioDecoderInfo,
                defaultAudioType,
                usage = AudioActivity.AUDIO_ATTR_USAGE,
                contentType = AudioActivity.AUDIO_ATTR_CONTENT_TYPE
            )
            audioPlayer = player
            if (stopping.get()) {
                // stop() ran between the check above and this assignment and saw no player.
                cleanupScope.launch {
                    teardownGate.awaitCompletion()
                    player.releaseAndJoin()
                }
                return
            }
            sendRecAudioThread()
            startPlayThread()
        }
```

#### B.2.4 `onReceivedData()` / `onDisconnected()` / `onFailed()`

**改动前**（HEAD `:64-80`）：

```kotlin
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            val audioData = data as ByteArray
            LogContext.log.i(TAG, "onReceivedData Length[${audioData.size}]")
            receiveAudioQueue.offer(audioData)
        }

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            LogContext.log.w(TAG, "onDisconnect")
            ctx?.toast("onDisconnect", debug = true)
            stop()
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            stop()
        }
```

**改动后**（工作树 `:123-143`）：

```kotlin
        override fun onReceivedData(netty: BaseNettyClient, data: Any?, action: Int) {
            val audioData = data as ByteArray
            LogContext.log.i(TAG, "onReceivedData Length[${audioData.size}]")
            receiveAudioQueue.trySend(audioData)
        }

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            LogContext.log.w(TAG, "onDisconnect")
            ctx?.toast("onDisconnect", debug = true)
            // Not on ioScope: stop() cancel-and-joins it. And not blocking here either - this is
            // a netty event loop thread, and stop() waits for that same client to release.
            cleanupScope.launch { stop() }
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            // onFailed() is also called synchronously from inside connect(), which runs on
            // ioScope - one more reason teardown must not run on the caller's context.
            cleanupScope.launch { stop() }
        }
```

#### B.2.5 `start()`：`senderClient` 提前赋值、`stopping` 前检查与发布后再检查

**改动前**（HEAD `:83-117`）：

```kotlin
    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
        ioScope.launch {
            senderClient =
                AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000)).also {
                    senderHandler = AudioSenderWebSocketHandler(it)
                    it.initHandler(senderHandler)
                    it.connect()
                }
        }

        micRecorder = MicRecorder(
            AudioActivity.audioEncoderInfo,
            object : MicRecorder.RecordCallback {
                override fun onRecording(data: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
                    recAudioQueue.offer(data)
                    if (BuildConfig.DEBUG) {
                        LogContext.log.d(
                            TAG,
                            "mic rec data[${data.size}] queue=${recAudioQueue.size}"
                        )
                    }
                }

                override fun onStop(stopResult: Boolean) {
                }
            },
            defaultAudioType,
            // Two-way voice: keep the VoIP capture chain. The library default
            // (MIC, no effects) is meant for record-to-file playback, and using it
            // here would let the far end hear its own echo.
            audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            enableAdvancedFeatures = true
        ).apply { startRecord() }
    }
```

**改动后**（工作树 `:146-193`）：

```kotlin
    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
        ioScope.launch {
            // Assign before connect(), not through `also { }` on the way out: connect() parks for
            // up to CONNECTION_TIMEOUT_IN_MILLS and calls onFailed() from inside itself, so the
            // trailing assignment leaves senderClient null for exactly the window in which stop()
            // needs it to end the retry chain and release the client.
            val client =
                AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000))
            senderClient = client
            senderHandler = AudioSenderWebSocketHandler(client)
            client.initHandler(senderHandler)
            client.connect()
        }

        // Before the constructor, not after: constructing a MicRecorder opens the microphone, and
        // a teardown already in flight would never see this instance to close it again.
        if (stopping.get()) return
        val recorder = MicRecorder(
            AudioActivity.audioEncoderInfo,
            object : MicRecorder.RecordCallback {
                override fun onRecording(data: ByteArray, isConfig: Boolean, isKeyFrame: Boolean) {
                    val queued = recAudioQueue.trySend(data).isSuccess
                    if (BuildConfig.DEBUG) {
                        LogContext.log.d(TAG, "mic rec data[${data.size}] queued=$queued")
                    }
                }

                override fun onStop(stopResult: Boolean) {
                }
            },
            defaultAudioType,
            // Two-way voice: keep the VoIP capture chain. The library default
            // (MIC, no effects) is meant for record-to-file playback, and using it
            // here would let the far end hear its own echo.
            audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            enableAdvancedFeatures = true
        ).apply { startRecord() }
        micRecorder = recorder
        // Re-check after publishing: a stop() that ran while the recorder was being built read
        // micRecorder as null and has consumed the gate, so nothing else will ever stop it.
        if (stopping.get()) {
            cleanupScope.launch {
                teardownGate.awaitCompletion()
                recorder.stopRecordAndJoin()
            }
        }
    }
```

#### B.2.6 两个 worker：`for (data in channel)`

**改动前**（HEAD `:119-148`）：

```kotlin
    private fun sendRecAudioThread() {
        ioScope.launch {
            while (true) {
                ensureActive()
                // take() outside runCatching, and through runInterruptible: the call blocks
                // uninterruptibly, so a plain take() would keep this coroutine parked on a
                // Dispatchers.IO thread after ioScope.cancel() until another item happened to
                // arrive - which, once sending has stopped, never comes.
                val pcmData = runInterruptible { recAudioQueue.take() }
                runCatching {
                    // LogContext.log.i(ITAG, "PCM[${pcmData.size}] to be sent.")
                    senderHandler?.sendAudioToServer(pcmData)
                }.onFailure { it.printStackTrace() }
            }
        }
    }

    private fun startPlayThread() {
        LogContext.log.i(TAG, "Start decodeThread()")
        ioScope.launch(Dispatchers.IO) {
            while (true) {
                ensureActive()
                // Read before the safe call, not inside it: `audioPlayer?.play(queue.take())`
                // never evaluates take() when audioPlayer is null, which would turn this into
                // a tight loop on a dispatcher thread instead of a wait.
                val audioData = runInterruptible { receiveAudioQueue.take() }
                audioPlayer?.play(audioData)
            }
        }
    }
```

**改动后**（工作树 `:195-216`）：

```kotlin
    private fun sendRecAudioThread() {
        ioScope.launch {
            // The receive suspension point sits outside runCatching, so cancelling the scope - or
            // closing the channel in stop() - ends the worker, while a single failed send only
            // drops that frame.
            for (data in recAudioQueue) {
                runCatching {
                    // LogContext.log.i(ITAG, "PCM[${data.size}] to be sent.")
                    senderHandler?.sendAudioToServer(data)
                }.onFailure { it.printStackTrace() }
            }
        }
    }

    private fun startPlayThread() {
        LogContext.log.i(TAG, "Start decodeThread()")
        ioScope.launch(Dispatchers.IO) {
            for (data in receiveAudioQueue) {
                audioPlayer?.play(data)
            }
        }
    }
```

#### B.2.7 `stop()`：完整新实现

**改动前**（HEAD `:150-158`）：

```kotlin
    fun stop() {
        audioPlayer?.release()
        micRecorder?.stopRecord()
        ioScope.launch {
            senderClient?.disconnectManually()
            senderClient?.release()
        }
        ioScope.cancel()
    }
```

**改动后**（工作树 `:218-283`；第 1 步注释的第三段为第六轮 J.9 追加）：

```kotlin
    /**
     * Cancel-and-joins [ioScope], so it must never be called from ioScope or any of its
     * children. All current call sites satisfy that: the two netty callbacks go through
     * [cleanupScope], and AudioActivity.onStop() uses the Activity's own scope.
     *
     * Concurrent callers share one teardown through [teardownGate] and observe the same result.
     */
    suspend fun stop() {
        // Descendant check, not identity: ioScope.launch() children never equal the scope's own
        // Job, so `caller !== ioScope's Job` would pass for exactly the callers that deadlock.
        // Same shape as StreamPlayerStopper.stopAndJoin(), which StreamPlayerStopperTest covers.
        val ownerJob = ioScope.coroutineContext[Job]
        val callerJob = currentCoroutineContext()[Job]
        require(ownerJob == null || callerJob == null || !ownerJob.containsJob(callerJob)) {
            "stop() must be called from outside AudioSender's own scope"
        }
        // Before the gate, not inside it: followers never enter the block, and start() may still
        // be on its way to building a MicRecorder that this teardown would never see.
        stopping.set(true)
        teardownGate.run {
            teardownSteps(
                // Disconnect first, before anything slow. handleConnectFailure() calls onFailed
                // synchronously and then doRetry(), so the client can reconnect ~2s later and
                // hand onConnected() a fresh AudioPlayer that this one-shot gate would refuse to
                // release.
                //
                // Not what ends the retry chain on its own: on a client that has not reached
                // CONNECTING - the window where start()'s coroutine is still on its way to
                // connect() - disconnectManually() returns at its UNINITIALIZED guard and sets
                // nothing. What closes that window is `stopping`, which turns onConnected()
                // away, and the last step's release() calling stopRetryHandler(). This step is
                // what makes the common case cheap, not what makes the race safe.
                //
                // release() only reaches stopRetryHandler() past its own UNINITIALIZED guard,
                // and it does: there is no suspension point between `senderClient = client` and
                // the CONNECTING transition inside connect(), and start()'s job is
                // cancel-and-joined before the last step runs, so a non-null senderClient is
                // never UNINITIALIZED by then. A suspension point added to that stretch would
                // reopen a NioEventLoopGroup leak - the group is built in the constructor.
                { senderClient?.disconnectManually() },
                // Stop producing new frames.
                { micRecorder?.stopRecordAndJoin() },
                // Close the channels so a late netty callback's trySend() fails instead of
                // parking frames behind a consumer that is about to exit. One close per step,
                // like every other step here: two in one lambda would share a try again.
                { recAudioQueue.close() },
                { receiveAudioQueue.close() },
                // Stop the track before joining, not after: the same order
                // StreamPlayerStopper.prepareStop() uses. It releases nothing, so
                // releaseAndJoin() below still does the real teardown. Defensive here rather
                // than required - this player is always OPUS, so its own track is never played
                // and stop() returns at the PLAYSTATE_STOPPED guard (spec 15.1).
                //
                // Its own step, not merged with the join below: teardownSteps() exists so one
                // failing step does not skip the rest, and skipping the join is the one thing
                // that must never happen - releaseAndJoin() would then run while the workers
                // are still calling play().
                { audioPlayer?.stop() },
                { ownerJob?.cancelAndJoin() },
                { audioPlayer?.releaseAndJoin() },
                // Last: release() shuts the EventLoopGroups down, which the workers above may
                // still need a channel from.
                { senderClient?.release() }
            )
        }
    }
```

#### B.2.8 审查要点（AudioSender）

1. **`require(containsJob)` 守卫位于一切副作用之前**：在 `stopping.set(true)` 之前、在 `teardownGate.run`（即进入 `withContext(NonCancellable)`）之前。自调用会 fail-fast 而不会消费一次性门闩，也不会把 `stopping` 置位后半途退出。`ownerJob == null || callerJob == null` 短路：`CoroutineScope(Dispatchers.IO)` 一定带 Job（`CoroutineScope()` 工厂会补），所以实际只有 `callerJob == null` 分支可能短路（非协程上下文不会发生，`stop()` 是 suspend）。
2. **step 顺序与各自独立 lambda 的理由**：
   - ① `disconnectManually()` 最先：`BaseNettyClient.connect()` 的 `handleConnectFailure()` 同步回调 `onFailed()` 后调 `doRetry()`；`disconnectManually()` 置 `disconnectManually = true`（`BaseNettyClient.kt:259`，多处 `if (disconnectManually || ...)` 守卫，`:442/:488/:509/:523/:569/:593`），终止重试链，否则 ~2s 后重连成功会让 `onConnected()` 再建一个 AudioTrack。它已在 `withContext(NonCancellable)` 内自带有界超时（`BaseNettyClient.kt:620-650`）。
   - ② `stopRecordAndJoin()`：停止新帧进入 `recAudioQueue`。
   - ③④ 两个 `close()` 各一步：关闭后迟到的 `trySend()` 直接失败；拆成两步是因为第十次复审 H1 的"每步只做一件事"。
   - ⑤ `audioPlayer?.stop()` 独立一步、⑥ `cancelAndJoin()` 独立一步：合并会让 ⑤ 抛出时跳过 ⑥，而 ⑦ `releaseAndJoin()` 照常执行 → 在 worker 仍可能调用 `play()` 时 release（历史 SIGABRT 场景）。今天 ⑤ 不会抛（`AudioTrackPlayer.stop()` 全程 `runCatchingPreservingCancellation`），且该 player 恒为 OPUS、自身 track 从未 `play()`，`AudioTrackPlayer.stop()` 在 `playState == PLAYSTATE_STOPPED` 守卫处直接返回（已核对 `AudioTrackPlayer.kt:270-273`）——此步是为 `defaultAudioType` 改 PCM 那天准备的防御。
   - ⑧ `release()` 最后：它关闭 EventLoopGroup，前面 worker 可能仍需 channel。
3. **`@Volatile` + `stopping` 的 Dekker 式论证**：发布侧"写字段 → 读 `stopping`"，teardown 侧"写 `stopping`（AtomicBoolean，volatile 语义）→ 读字段"。两侧都是 volatile 读写构成全序，不可能出现"teardown 读到字段 null **且** 发布方读到 `stopping == false`"；因此资源要么被门闩释放、要么被补偿释放，不会两者皆无。只给 `stopping` 用 AtomicBoolean 而字段不 volatile 时，字段写可能对 teardown 线程不可见，不构成该保证。
4. **补偿先等门闩再释放**：`teardownGate.awaitCompletion()` 后再 `releaseAndJoin()` / `stopRecordAndJoin()`，避免补偿的 release 跑在门闩的 `cancelAndJoin()` 之前（即 worker 与 release 并发——本方案最花篇幅堵的那个场景）。`awaitCompletion()` 不重抛 leader 的失败，所以门闩失败也不会让补偿路径半途退出。
5. **补偿调用的幂等性**（门闩可能其实已经看到了该资源）：`MicRecorder.stopRecordAndJoin()` 第二次调用走 `stopped.compareAndSet` 失败分支 → `releaseCompleted.await()` 后返回（`MicRecorder.kt:405-408`）；`AudioPlayer.releaseAndJoin()` 第二次：`opusStreamPlayer.stopPlayingAndJoin()` → `StreamPlayerStopper.stopAndJoin()` 的 `detachDecoder()` 返回 null、`cancelAndJoin` 对已取消 Job 立即返回、track 操作都包在 `runCatchingPreservingCancellation` 里；`AudioPlayer` 自身的 `audioTrackPlayer.release()` 以 `state == STATE_UNINITIALIZED` 早返回（`AudioTrackPlayer.kt:285-288`）。结论：幂等成立（以日志代替异常）。
6. **`for (data in channel)` 的退出条件**：① `close()` 后缓冲区排空时迭代正常结束；② scope 取消时 `receive` 挂起点抛 CE。`runCatching` 只包 `sendAudioToServer`，所以单帧失败不杀 worker，CE 不会被 `runCatching` 吞掉。由于 ③④ `close()` 排在 ⑥ `cancelAndJoin()` 之前，worker 可能先对已断开的 client 排空至多 64 帧——`sendAudioToServer()` 内部 `runCatching` 兜底（spec §20.4 已接受，不改顺序）。
7. **`senderClient` 的 null 窗口**：赋值已提前到 `connect()` 之前（第九次复审 H1）。但请注意：**`senderClient` 没有像 `micRecorder`/`audioPlayer` 那样的"发布后再读 `stopping`"补偿**。若 `stop()` 的第 ① 步读 `senderClient` 为 null，随后 `start()` 的 launch 协程才构造 client 并赋值，则 ⑥ `cancelAndJoin()` 会取消该协程（`connect()` 的 `invokeOnCancellation` 会 `cancelConnectFuture()`，`BaseNettyClient.kt:418-421`），⑧ 再读 `senderClient` 时**可能**已非 null 并 `release()`——是否一定非 null 取决于 ⑧ 的读是否发生在赋值之后（同一 volatile 字段，⑥ 已 join 该协程，因此 happens-before 成立，⑧ 应能看到赋值）。但若协程在 `launch` 后、构造 client 前就被取消（ioScope 已 cancel），client 根本不会构造，无泄漏。**本人判断该窗口已被 ⑥→⑧ 的顺序覆盖，但 spec §20.1 未把 `senderClient` 列入四个补偿点，建议 Codex 复核**。`NioEventLoopGroup` 在 `BaseNettyClient` 构造时即创建（`:247`），若有遗漏就是两组线程泄漏。
8. `start()` 在 `ioScope.launch { connect }` **之后**才检查 `stopping`：若 `stop()` 已跑完，ioScope 已取消，launch 的协程不会执行 body；若正在跑，由 ⑥ 取消。无额外窗口。
9. `onConnected()` 的 `ctx!!`：既有写法，未改动。

---

### B.3 `AudioReceiver.kt`

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.8）。

**做了什么 / 为什么**：与 `AudioSender` 同一关闭协议（门闩、`cleanupScope`、`stopping`、`@Volatile`、`containsJob` 守卫、`teardownSteps()` 5 步），`startServer()`/`stopServer()` 改 suspend；`receiverServer` 赋值提前到 `startServer()` 之前（第九次复审 H2：旧写法 `also { ...; it.startServer() }` 在 server 运行期间从未完成赋值，`stopServer()` 恒读到 null）；`startMicRecording()` 改为返回 `MicRecorder`，由调用方发布并再检查；队列**保留** `ArrayBlockingQueue`（文件已 import `io.netty.channel.Channel`）。

#### B.3.1 import 变化

**改动前**（HEAD `:19-27`）：

```kotlin
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
```

**改动后**（工作树 `:13-14`、`:17`、`:20-31`）：

```kotlin
import com.leovp.demo.basiccomponents.examples.audio.SuspendTeardownGate
import com.leovp.demo.basiccomponents.examples.audio.containsJob
...
import com.leovp.demo.basiccomponents.examples.audio.teardownSteps
import com.leovp.log.LogContext
import io.netty.channel.Channel
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
```

移除：`kotlinx.coroutines.cancel`。

#### B.3.2 字段

**改动前**（HEAD `:37-48`）：

```kotlin
    private var receiverServer: AudioReceiverWebSocket? = null
    private var receiverHandler: AudioReceiverWebSocketHandler? = null

    private var audioPlayer: AudioPlayer? = null
    private var micRecorder: MicRecorder? = null
    // private var micOs: BufferedOutputStream? = null
    // private var rcvOs: BufferedOutputStream? = null

    private var recAudioQueue = ArrayBlockingQueue<ByteArray>(10)
    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(64)

    private val ioScope = CoroutineScope(Dispatchers.IO)
```

**改动后**（工作树 `:45-95`）：

```kotlin
    // Volatile where stopServer() reads them - see AudioSender for the pairing with `stopping`.
    @Volatile
    private var receiverServer: AudioReceiverWebSocket? = null
    private var receiverHandler: AudioReceiverWebSocketHandler? = null

    @Volatile
    private var audioPlayer: AudioPlayer? = null

    @Volatile
    private var micRecorder: MicRecorder? = null
    // private var micOs: BufferedOutputStream? = null
    // private var rcvOs: BufferedOutputStream? = null

    private var recAudioQueue = ArrayBlockingQueue<ByteArray>(10)
    private var receiveAudioQueue = ArrayBlockingQueue<ByteArray>(64)

    private val ioScope = CoroutineScope(Dispatchers.IO)

    /**
     * Teardown runs here rather than on [ioScope]: stopServer() terminates ioScope itself, and the
     * netty listener callbacks that trigger it are non-suspend interface methods that cannot
     * simply be made suspend. The handler is what keeps a failed teardown off the platform
     * default handler.
     *
     * Deliberately never cancelled - cancelling it from stopServer() would cancel the very
     * coroutine running stopServer(), which is the situation this separate scope exists to avoid.
     */
    private val cleanupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                LogContext.log.e(TAG, "Audio receiver teardown failed", error)
            }
    )

    /** Makes the Activity path and the netty callback path share one teardown. */
    private val teardownGate = SuspendTeardownGate()

    /**
     * Set before teardown starts, checked by everything that builds a new resource - once before
     * building it and once more after publishing it.
     *
     * The one-shot gate guarantees teardown runs once; it cannot guarantee nobody builds new
     * resources afterwards. Two things still can: startServer() builds the player before the
     * server is up, and onClientConnected() builds the recorder and both workers. Either would
     * leave an AudioRecord or an AudioTrack that the already-consumed gate refuses to release.
     * The check before building turns most of them away; the re-check after publishing catches a
     * stopServer() that ran in between and read the field as null - the publisher then waits for
     * the gate and releases what it built. Both releases are idempotent, so it does not matter
     * if the gate saw the resource after all.
     */
    private val stopping = AtomicBoolean(false)
```

#### B.3.3 `onClientConnected()`

**改动前**（HEAD `:70-78`）：

```kotlin
        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            nettyServer = netty
            this.clientChannel = clientChannel
            LogContext.log.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ctx?.toast("onClientConnected: ${clientChannel.remoteAddress()}", debug = true)
            startMicRecording()
            sendRecAudioThread()
            startPlayThread()
        }
```

**改动后**（工作树 `:117-136`）：

```kotlin
        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) {
            if (stopping.get()) return
            nettyServer = netty
            this.clientChannel = clientChannel
            LogContext.log.i(TAG, "onClientConnected: ${clientChannel.remoteAddress()}")
            ctx?.toast("onClientConnected: ${clientChannel.remoteAddress()}", debug = true)
            val recorder = startMicRecording()
            micRecorder = recorder
            if (stopping.get()) {
                // stopServer() ran between the check above and this assignment and saw no
                // recorder, so nothing else will ever stop it.
                cleanupScope.launch {
                    teardownGate.awaitCompletion()
                    recorder.stopRecordAndJoin()
                }
                return
            }
            sendRecAudioThread()
            startPlayThread()
        }
```

#### B.3.4 `onClientDisconnected()` / `onStartFailed()` / `startMicRecording()`

**改动前**（HEAD `:94-107`）：

```kotlin
        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(TAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            ctx?.toast("onClientDisconnected: ${clientChannel.remoteAddress()}", debug = true)
            stopServer()
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            stopServer()
        }

        private fun startMicRecording() {
            micRecorder = MicRecorder(
```

**改动后**（工作树 `:152-166`；函数体其余部分 `:167-196` 未变，仍以 `.apply { startRecord() }` 结尾）：

```kotlin
        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) {
            LogContext.log.w(TAG, "onClientDisconnected: ${clientChannel.remoteAddress()}")
            ctx?.toast("onClientDisconnected: ${clientChannel.remoteAddress()}", debug = true)
            cleanupScope.launch { stopServer() }
        }

        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) {
            LogContext.log.w(TAG, "onFailed code: $code message: $msg")
            ctx?.toast("onFailed code: $code message: $msg", debug = true)
            cleanupScope.launch { stopServer() }
        }

        /** Builds and starts the recorder; the caller publishes it and re-checks `stopping`. */
        private fun startMicRecording(): MicRecorder {
            return MicRecorder(
```

#### B.3.5 `sendRecAudioThread()` KDoc 与注释（逻辑未变）

**改动前**（HEAD，对应 diff 旧行）：

```kotlin
        /**
         * Blocks on the queue rather than polling it every 10ms, which is what [AudioSender]
         * already does with the same queues. Polling capped the drain at about 100 items a
         * second whatever the arrival rate, so a burst could only be worked off at that speed
         * and the latency it created was never recovered - and in between, the loop woke 100
         * times a second to find nothing.
         */
        private fun sendRecAudioThread() {
            ioScope.launch {
                while (true) {
                    ensureActive()
                    // take() outside runCatching, and through runInterruptible: the call blocks
                    // uninterruptibly, so a plain take() would keep this coroutine parked on a
                    // Dispatchers.IO thread after ioScope.cancel() until another item happened to
                    // arrive - which, after stopServer(), never comes.
                    val pcmData = runInterruptible { recAudioQueue.take() }
```

**改动后**（工作树 `:198-225`）：

```kotlin
        /**
         * Blocks on the queue rather than polling it every 10ms. Polling capped the drain at
         * about 100 items a second whatever the arrival rate, so a burst could only be worked
         * off at that speed and the latency it created was never recovered - and in between,
         * the loop woke 100 times a second to find nothing.
         *
         * Still an ArrayBlockingQueue, unlike [AudioSender]'s coroutine channels. That is a
         * difference in migration order, not a design decision - an import alias would settle
         * the collision with io.netty.channel.Channel that this file has. What makes the two
         * equivalent for teardown is runInterruptible below: without it a parked take() would
         * outlive the scope's cancellation and hang the join in stopServer().
         */
        private fun sendRecAudioThread() {
            ioScope.launch {
                while (true) {
                    ensureActive()
                    // take() outside runCatching, and through runInterruptible: the call blocks
                    // uninterruptibly, so a plain take() would keep this coroutine parked on a
                    // Dispatchers.IO thread after the scope is cancelled until another item
                    // happened to arrive - which, after stopServer(), never comes.
                    val pcmData = runInterruptible { recAudioQueue.take() }
                    runCatching {
                        // LogContext.log.i(TAG, "Rec pcm[${pcmData.size}]")
                        receiverHandler?.sendAudioToClient(clientChannel!!, pcmData)
                    }.onFailure { it.printStackTrace() }
                }
            }
        }
```

`startPlayThread()`（工作树 `:227-240`）逻辑未改动——此后只在 `take()` 前补了一段「先读再安全调用」的注释——仍为 `while (true) { ensureActive(); val audioData = runInterruptible { receiveAudioQueue.take() }; audioPlayer?.play(audioData) }`。

#### B.3.6 `startServer()`

**改动前**（HEAD `:179-196`）：

```kotlin
    fun startServer(ctx: Context) {
        this.ctx = ctx
        audioPlayer = AudioPlayer(
            ctx,
            AudioActivity.audioDecoderInfo,
            defaultAudioType,
            usage = AudioActivity.AUDIO_ATTR_USAGE,
            contentType = AudioActivity.AUDIO_ATTR_CONTENT_TYPE
        )
        // micOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "mic.aac")))
        // rcvOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "rcv.aac")))

        receiverServer = AudioReceiverWebSocket(10020, connectionListener).also {
            receiverHandler = AudioReceiverWebSocketHandler(it)
            it.initHandler(receiverHandler)
            it.startServer()
        }
    }
```

**改动后**（工作树 `:243-297`；`try { server.startServer() } finally { … }` 为第六轮 J.8）：

```kotlin
    suspend fun startServer(ctx: Context) {
        this.ctx = ctx
        // Before the player is built, not after: a teardown already in flight would never see
        // this AudioTrack, and the consumed gate cannot release it later.
        if (stopping.get()) return
        val player = AudioPlayer(
            ctx,
            AudioActivity.audioDecoderInfo,
            defaultAudioType,
            usage = AudioActivity.AUDIO_ATTR_USAGE,
            contentType = AudioActivity.AUDIO_ATTR_CONTENT_TYPE
        )
        audioPlayer = player
        // micOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "mic.aac")))
        // rcvOs = BufferedOutputStream(FileOutputStream(FileUtil.createFile(ctx, "rcv.aac")))

        // Assign before starting, not through `also { }` on the way out: onStarted() and the first
        // onClientConnected() can fire from inside startServer(), and stopServer() needs a non-null
        // receiverServer to have anything to stop. Back when startServer() blocked until the server
        // stopped, that assignment never happened at all while the server was up.
        val server = AudioReceiverWebSocket(10020, connectionListener)
        receiverServer = server
        receiverHandler = AudioReceiverWebSocketHandler(server)
        server.initHandler(receiverHandler)
        try {
            server.startServer()
        } finally {
            // Re-check after publishing, not only before building: a stopServer() that ran in
            // between read both fields as null and has consumed the gate, so the port and the
            // track would otherwise stay held for good. Wait for that teardown first, so this
            // release never overlaps its steps; both calls are then idempotent against what it
            // may already have done - BaseNettyServer.stopServer() returns false for a stopped
            // server.
            //
            // In a finally: the only exception startServer() lets out is the caller's
            // CancellationException, and a cancelled caller has published both fields just the
            // same. Without this, the compensation would hang on the caller's scope staying
            // alive - exactly the dependency this re-check exists to remove.
            if (stopping.get()) {
                // On cleanupScope, not inline: this waits for a teardown that is still running,
                // and the caller's scope is the Activity's. Inline, a cancelled caller would skip
                // both releases and leave the port and the AudioTrack held - the same reason
                // AudioSender routes its two compensation paths here. Through teardownSteps() for
                // the same reason the teardown itself is: stopServer() throwing must not skip the
                // track.
                cleanupScope.launch {
                    teardownGate.awaitCompletion()
                    teardownSteps(
                        { server.stopServer() },
                        { player.releaseAndJoin() }
                    )
                }
            }
        }
    }
```

#### B.3.7 `stopServer()`：完整新实现

**改动前**（HEAD `:198-214`）：

```kotlin
    fun stopServer() {
        // micOs?.closeQuietly()
        // rcvOs?.closeQuietly()
        ioScope.cancel()
        // Please initialize AudioTrack with sufficient buffer, or else, it will crash when you
        // release it.
        //
        // And you must release AudioTrack first, otherwise, you will crash due to following
        // exception:
        // releaseBuffer() track 0xde4c9100 disabled due to previous underrun, restarting
        // AudioTrackShared: Assertion failed: !(stepCount <= mUnreleased && mUnreleased <=
        // mFrameCount)
        // Fatal signal 6 (SIGABRT), code -6 in tid 26866 (DefaultDispatch)
        audioPlayer?.release()
        micRecorder?.stopRecord()
        receiverServer?.stopServer()
    }
```

**改动后**（工作树 `:299-346`）：

```kotlin
    /**
     * Cancel-and-joins [ioScope], so it must never be called from ioScope or any of its
     * children. All current call sites satisfy that: the two netty callbacks go through
     * [cleanupScope], and AudioActivity.onStop() uses the Activity's own scope.
     *
     * Concurrent callers share one teardown through [teardownGate] and observe the same result.
     */
    suspend fun stopServer() {
        // micOs?.closeQuietly()
        // rcvOs?.closeQuietly()
        //
        // Descendant check, not identity - see AudioSender.stop() for why.
        val ownerJob = ioScope.coroutineContext[Job]
        val callerJob = currentCoroutineContext()[Job]
        require(ownerJob == null || callerJob == null || !ownerJob.containsJob(callerJob)) {
            "stopServer() must be called from outside AudioReceiver's own scope"
        }
        // Before the gate: onClientConnected() builds the MicRecorder and both workers, and the
        // server is only stopped in the last step below, so a client arriving mid-teardown would
        // otherwise hand this instance resources the consumed gate can no longer release.
        stopping.set(true)
        teardownGate.run {
            teardownSteps(
                { micRecorder?.stopRecordAndJoin() },
                // Stop the track before joining, not after: the same order
                // StreamPlayerStopper.prepareStop() uses. It releases nothing, so
                // releaseAndJoin() below still does the real teardown. Defensive here rather
                // than required - this player is always OPUS, so its own track is never played
                // and stop() returns at the PLAYSTATE_STOPPED guard (spec 15.1).
                //
                // Its own step, not merged with the join below - see AudioSender.stop().
                { audioPlayer?.stop() },
                { ownerJob?.cancelAndJoin() },
                // Please initialize AudioTrack with sufficient buffer, or else, it will crash
                // when you release it. Releasing while a worker still writes gave:
                // releaseBuffer() track 0xde4c9100 disabled due to previous underrun, restarting
                // AudioTrackShared: Assertion failed: !(stepCount <= mUnreleased && mUnreleased
                // <= mFrameCount)
                // Fatal signal 6 (SIGABRT), code -6 in tid 26866 (DefaultDispatch)
                // That note applies to the PCM path; the join above is what makes it moot here.
                { audioPlayer?.releaseAndJoin() },
                // Last, not first: stopServer() shuts both EventLoopGroups down, and the workers
                // above may still be sending through a channel. A client arriving before this
                // runs is turned away by the `stopping` flag instead.
                { receiverServer?.stopServer() }
            )
        }
    }
```

#### B.3.8 审查要点（AudioReceiver）

1. **守卫位置**同 `AudioSender`：`require` 在 `stopping.set(true)` 与 `teardownGate.run` 之前。两个 netty 回调通过 `cleanupScope`，`AudioActivity.onStop()` 通过 Activity 的 `ioScope`，`startServer()` 内的补偿 `server.stopServer()` 是 `BaseNettyServer.stopServer()`（不是本类的），不触发守卫。
2. **5 步顺序**：① `stopRecordAndJoin()` 停新帧；② `audioPlayer?.stop()` 独立一步（防御性，理由同 sender）；③ `cancelAndJoin()` 等两个 `runInterruptible { take() }` worker 退出；④ `releaseAndJoin()`；⑤ `receiverServer?.stopServer()` 最后（关闭 EventLoopGroup 前让 worker 先退出；提前到的客户端由 `stopping` 挡在 `onClientConnected()` 门口）。**没有与 sender 第 ③④ 步对等的 close 步骤是有意的**：`ArrayBlockingQueue` 没有 `close()`，两个写入点都是非阻塞 `offer()`、队列有界，消费者退出后迟到帧只被丢弃（spec §4.7.4）。
3. **`runInterruptible { take() }` 的取消退出**：`cancelAndJoin()` 触发 `runInterruptible` 中断阻塞线程，`take()` 抛 `InterruptedException` 被转成 CE；`take()` 在 `runCatching` 外，所以 CE 不被吞。`ensureActive()` 在循环头兜住"取消时恰好不在 take() 中"的情况。
4. **保留 `ArrayBlockingQueue` 的理由**是 `io.netty.channel.Channel` 同名冲突，不是功能差异；KDoc 已删去"与 AudioSender 用同样的队列"这句过期表述（§20.3）。
5. **`startServer()` 的补偿路径与 `lifecycleMutex`**：`BaseNettyServer.startServer()`/`stopServer()` 都在 `lifecycleMutex.withLock` 内（`BaseNettyServer.kt:177-178`、`:289`）。门闩第 ⑤ 步的 `stopServer()` 若与 `server.startServer()` 并发，会在 mutex 上等待直到 `startServer()` 返回；随后 `startServer()` 的补偿 `teardownGate.awaitCompletion()` 等门闩，门闩等 mutex（已释放）——无环，不死锁。补偿的 `server.stopServer()` 对已 STOPPED 的 server 返回 `false`（`:292-294`），对已 bind 但门闩读到 null 的 server 真正释放端口 10020。
6. **`onClientConnected()` 内 `nettyServer`/`clientChannel` 在 `stopping` 首检之后赋值、补偿分支中已赋值**——无害（worker 未启动）。
7. `startMicRecording()` 改为纯构造+返回，发布与再检查上移到调用方：这样 `micRecorder` 的 volatile 写与 `stopping` 的读在同一处相邻，论证同 B.2.8 第 3 条。
8. `@Volatile` 没有加在 `receiverHandler`/`senderHandler` 上：teardown 不读它们（只被 worker 读），与注释"Volatile where stopServer() reads them"一致。

---

### B.4 `AudioActivity.kt`

**做了什么 / 为什么**：仅两处。① `stopPcmPlayback()` 按 `stop()` → `join()` → `releaseAndJoin()` 顺序（这里的 player 是真正的 `AudioType.PCM`，裸线程在 `AudioTrack.write()` 上阻塞，`interrupt()` 唤不醒它，能唤醒的 `AudioTrack.stop()` 原本藏在 join 之后的 `release()` 里——spec §4.7.5 / §15.1）；`release()` → `releaseAndJoin()`。② `onStop()` 先把 `audioReceiver`/`audioSender` 置 `null` 再调度关闭，与其余四个清理一致（门闩一次性，第二次 `onStop()` 只会重报已存结果，§20.3）。

#### B.4.1 `stopPcmPlayback()`

**改动前**（HEAD `:343-356`）：

```kotlin
    private fun stopPcmPlayback() {
        val playbackThread = playPcmThread
        val player = audioPlayer
        playPcmThread = null
        audioPlayer = null
        if (playbackThread == null && player == null) return
        runCatching { pcmInputStream.getAndSet(null)?.close() }
            .onFailure { LogContext.log.e(TAG, "Close PCM input failed", it) }
        playbackThread?.interrupt()
        launchCleanup("Stop PCM playback") {
            playbackThread?.join()
            player?.release()
        }
    }
```

**改动后**（工作树 `:341-358`）：

```kotlin
    private fun stopPcmPlayback() {
        val playbackThread = playPcmThread
        val player = audioPlayer
        playPcmThread = null
        audioPlayer = null
        if (playbackThread == null && player == null) return
        runCatching { pcmInputStream.getAndSet(null)?.close() }
            .onFailure { LogContext.log.e(TAG, "Close PCM input failed", it) }
        playbackThread?.interrupt()
        launchCleanup("Stop PCM playback") {
            // Stop before joining: interrupt() cannot wake a blocking native AudioTrack.write(),
            // and the AudioTrack.stop() that can is inside releaseAndJoin(), after this join.
            // Same ordering as StreamPlayerStopper.prepareStop() and AacFilePlayer.
            player?.stop()
            playbackThread?.join()
            player?.releaseAndJoin()
        }
    }
```

#### B.4.2 `onStop()`

**改动前**（HEAD `:370-378`）：

```kotlin
    override fun onStop() {
        launchCleanup("Stop audio receiver") { audioReceiver?.stopServer() }
        launchCleanup("Stop audio sender") { audioSender?.stop() }
        stopRecording()
        stopPcmPlayback()
        stopAacPlayback()
        stopOpusPlayback()
        super.onStop()
    }
```

**改动后**（工作树 `:372-386`）：

```kotlin
    override fun onStop() {
        // Detach first, like the four cleanups below: the gates are one-shot, so a second
        // onStop() would only re-report the stored result.
        val receiver = audioReceiver
        val sender = audioSender
        audioReceiver = null
        audioSender = null
        launchCleanup("Stop audio receiver") { receiver?.stopServer() }
        launchCleanup("Stop audio sender") { sender?.stop() }
        stopRecording()
        stopPcmPlayback()
        stopAacPlayback()
        stopOpusPlayback()
        super.onStop()
    }
```

未改动但相关的上下文（工作树）：`launchCleanup()` `:397-407`（`catch (e: CancellationException) { throw e }` 两 catch 子句形状，CE 不记日志——这正是门闩不把 CE 广播给 follower 的原因之一）；`onDestroy()` `:390-395` 用 `ioScopeJob.complete()` 让已调度的清理子协程跑完；`onAudioSenderClick()` `:409-414` / `onAudioReceiverClick()` `:416-419` 仍在 Activity `ioScope.launch` 中调 `start()`/`startServer()`（后者已是 suspend，launch 形状不变）。

#### B.4.3 审查要点（AudioActivity）

1. **`stop()` → `join()` → `releaseAndJoin()`**：`AudioPlayer.stop()` 委托 `AudioTrackPlayer.stop()`，后者先 `pause()`（含 `flush()`）再 `audioTrack.stop()`（spec §4.7.5 M3：不是一个"干净的唤醒原语"，但窗口极短，与 `StreamPlayerStopper.prepareStop()`/`AacFilePlayer` 一致，顺序不改）。真机验证时要看 logcat 是否有 `AudioTrack.write failed`/`write error=`（Task 5，本次未执行）。
2. 线程的 `play()` 调用在 `audioPlayer?.play(pcmData)`（`:165`）读的是**字段**，而 `stopPcmPlayback()` 已把字段置 null，因此 join 期间线程后续迭代会跳过 `play()`；已进入的那次 `write()` 由 `stop()` 唤醒。`releaseAndJoin()` 的 PCM 路径安全性论证见 `AudioPlayer.kt:175-178` KDoc（callers join their writer first——本处正是）。
3. **`onStop()` 置 null 的时序**：`onStop()` 在主线程，`onAudioSenderClick()` 也在主线程，字段读写同线程；但 `audioSender?.start(...)` 在 `ioScope.launch` 里读字段，`onStop()` 置 null 后那次 `start()` 可能读到 null 直接跳过，或读到旧实例后与 `stop()` 并发——后者正是 `stopping` 双检查覆盖的场景。
4. **门闩一次性与 `onStop()` 多次**：Activity 回前台再退后台时 `audioReceiver`/`audioSender` 已为 null，不会再次调用；重新点击会新建 owner 与新门闩，契约成立。
5. `launchCleanup` 的 `receiver?.stopServer()` 跑在 Activity `ioScope`（`SupervisorJob + Dispatchers.IO`）上，不是 `AudioReceiver.ioScope` 的后代，守卫通过。`onDestroy()` 的 `ioScopeJob.complete()` 不取消这些子协程；门闩体本身也在 `NonCancellable` 内。

---

### B.5 `ADPCMActivity.kt`

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.11）。

**做了什么 / 为什么**：删除共享可变 `player` 字段（4 处：字段 `:38`、Job 外赋值 `:88`、`.also { release() }` `:115`、`onDestroy()` `:121`），改为 `playbackJob: Job?`。播放 Job 自己构造、自己在 `finally` 中 `NonCancellable` 释放 player；新 Job 在构造 player 前 `NonCancellable` 地 join 上一个 Job，再 `ensureActive()`（§20.3：三连击时第二个 Job 被取消会让普通 `join()` 以 CE 立即退出，第三个 Job 随即构造 player、与第一个 Job 仍在跑的 `finally` 短暂共存）。`onDestroy()` 只 `cancel()`。编码路径的 `thread { }`（`:54`）不在本次范围，`import kotlin.concurrent.thread`（`:24`）**保留**。

#### B.5.1 import 与字段

**改动后新增 import**（工作树 `:7`、`:25-31`）：

```kotlin
import androidx.lifecycle.lifecycleScope
...
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

**字段 改动前**（HEAD `:38`）：

```kotlin
    private var player: AudioPlayer? = null
```

**改动后**（工作树 `:46`）：

```kotlin
    private var playbackJob: Job? = null
```

#### B.5.2 `onPlayADPCMClick()` 播放 Job

**改动前**（HEAD `:88-118`）：

```kotlin
        player = AudioPlayer(this, decoderInfo, AudioType.PCM)

        thread {
            runCatching {
                AdpcmImaQtDecoder(decoderInfo.sampleRate, decoderInfo.channelCount).use { decoder ->
                    // val inputStream = resources.openRawResource(R.raw.out_adpcm_44100_2ch_64kbps)
                    val inFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
                    val musicBytes = FileInputStream(inFile).use { it.readBytes() }
                    val chunkSize = decoder.chunkSize()
                    require(musicBytes.size % chunkSize == 0) {
                        "ADPCM file contains an incomplete trailing chunk."
                    }
                    for (i in musicBytes.indices step chunkSize) {
                        val chunk = musicBytes.copyOfRange(i, i + chunkSize)
                        val st = SystemClock.elapsedRealtimeNanos()
                        val pcmBytes = decoder.decode(chunk)
                        LogContext.log.i(
                            ITAG,
                            "PCM[${pcmBytes.size}] " +
                                "cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us"
                        )
                        player?.play(pcmBytes)
                    }
                }
            }.onFailure {
                LogContext.log.e(ITAG, "Unable to decode ADPCM audio.", it)
            }.also {
                player?.release()
            }
        }
    }
```

**改动后**（工作树 `:97-146`；解码块的双 `catch` 为第六轮 J.11）：

```kotlin
        // cancel() does not wait, so capture the old job and join it inside the new one.
        val previousJob = playbackJob?.also { it.cancel() }
        playbackJob = lifecycleScope.launch(Dispatchers.IO) {
            // Join the previous job before building a new player: the old job's finally still has
            // a NonCancellable releaseAndJoin() to run, and without this wait rapid clicks stack
            // several live AudioTracks. Joining here, not on the click thread, keeps the UI
            // thread free. The join itself is NonCancellable so that a third click, which cancels
            // this job while it waits, cannot let the chain skip a link: this job still outlives
            // the previous one, and the next job waits on this one. ensureActive() then honours
            // the cancellation before anything is built.
            withContext(NonCancellable) { previousJob?.join() }
            ensureActive()
            // Inside the job body, never outside it: a job cancelled before its first dispatch
            // never reaches this finally, so a player built on the click thread would leak.
            val currentPlayer = AudioPlayer(this@ADPCMActivity, decoderInfo, AudioType.PCM)
            try {
                try {
                    AdpcmImaQtDecoder(decoderInfo.sampleRate, decoderInfo.channelCount)
                        .use { decoder ->
                            // val inputStream =
                            //     resources.openRawResource(R.raw.out_adpcm_44100_2ch_64kbps)
                            val inFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
                            val musicBytes = FileInputStream(inFile).use { it.readBytes() }
                            val chunkSize = decoder.chunkSize()
                            require(musicBytes.size % chunkSize == 0) {
                                "ADPCM file contains an incomplete trailing chunk."
                            }
                            for (i in musicBytes.indices step chunkSize) {
                                ensureActive()
                                val chunk = musicBytes.copyOfRange(i, i + chunkSize)
                                val st = SystemClock.elapsedRealtimeNanos()
                                val pcmBytes = decoder.decode(chunk)
                                LogContext.log.i(
                                    ITAG,
                                    "PCM[${pcmBytes.size}] " +
                                        "cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us"
                                )
                                currentPlayer.play(pcmBytes)
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogContext.log.e(ITAG, "Unable to decode ADPCM audio.", e)
                }
            } finally {
                withContext(NonCancellable) { currentPlayer.releaseAndJoin() }
            }
        }
    }
```

#### B.5.3 `onDestroy()`

**改动前**（HEAD `:120-123`）：

```kotlin
    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
```

**改动后**（工作树 `:148-154`）：

```kotlin
    override fun onDestroy() {
        // Cancel only. The playback job owns its player and releases it in its own finally, so
        // releasing here would race the loop that may still be calling play().
        playbackJob?.cancel()
        playbackJob = null
        super.onDestroy()
    }
```

#### B.5.4 审查要点（ADPCMActivity）

1. **三连击行为**：点击 1 启动 J1；点击 2 取消 J1、启动 J2（J2 先 `NonCancellable` join J1——J1 的 `finally` 在 `NonCancellable` 中 `releaseAndJoin()`，所以 J1 一定会结束）；点击 3 取消 J2、启动 J3，J3 join J2。J2 此时可能仍在 join J1：因为 join 是 `NonCancellable`，J2 不会因被取消而立刻退出，J3 等 J2、J2 等 J1，链不断，任何时刻最多一个活 AudioTrack。J2 join 完成后 `ensureActive()` 抛 CE，J2 不构造 player 即结束。
2. **唯一接受的残留窗口**：`ensureActive()` 通过之后、`AudioPlayer(...)` 构造期间或 `try` 进入之前到来的取消不会被立即观察到；player 会被构造，然后在循环首个 `ensureActive()` 处抛出，由 `finally` 释放。后果是"构造了一个立刻释放的 AudioTrack"，没有泄漏、没有与下一 Job 的 player 重叠（下一 Job 仍在 join 本 Job）。spec/plan 中未对此窗口单独命名，这是本人依据代码的推断，标 **未核实（文档层面）**。
3. **`AudioPlayer` 必须在 Job 体内构造**：Job 在首次调度前被取消（例如 `lifecycleScope` 已取消时的 `launch`）时 body 不执行、`finally` 不存在，在 Job 外构造的 player 会泄漏（plan Task 3 Step 7 明令禁止）。`AudioType.PCM` 的 `AudioPlayer` 构造即 `audioTrackPlayer.play()`，确实持有活 track。
4. **`runCatching` 吞 CE 的回传**：`if (it is CancellationException) throw it` 位于 `onFailure` lambda 内，不是 `catch` 块，不触发 detekt `InstanceOfCheckForException`；与 plan 模板一致。
5. **`onDestroy()` 只 cancel 不 release**：直接 release 会与循环内的 `currentPlayer.play()` 并发（历史 SIGABRT 场景）。`lifecycleScope` 在 destroy 时取消，但 `finally` 的 `withContext(NonCancellable)` 仍会执行 `releaseAndJoin()`。
6. `ensureActive()` 每帧一次，`decoder.decode()`/`play()` 之间无挂起点，取消延迟最多一帧。
7. `thread` import 保留且仍被 `onEncodeToADPCMClick()` 使用（见 B.z）。

---

### B.z 本区自查结果

全部为只读命令（grep/awk/git diff），**未运行任何 Gradle 任务**。

| 项 | 命令 | 结果 |
|---|---|---|
| plan 附录 19：门闩不用 catch 内 `is` 判型 | `grep -n 'is CancellationException' .../SuspendTeardownGate.kt` | **空**（退出码 1）✅ |
| plan 附录 18：`audioPlayer?.stop()` 与 `cancelAndJoin()` 为两个独立 step | `grep -rn -A2 'audioPlayer?.stop()' .../audio` | `AudioSender.kt:261 { audioPlayer?.stop() },` 下一行 `:262 { ownerJob?.cancelAndJoin() },`；`AudioReceiver.kt:309` 下一行 `:310 { ownerJob?.cancelAndJoin() },`。**按字面"下一行不出现 cancelAndJoin"该检查不成立**，但 `:261`/`:309` 以 `},` 结束，`cancelAndJoin` 在**下一个独立 lambda** 里，plan 要防的"两动作共用一个 lambda"形状不存在。意图满足 ✅，字面检查需按本说明解读。 |
| plan 附录 16：无 `Socket(...).also` 形状 | `grep -rn 'Socket(.*)\.also' .../audio` | **空** ✅ |
| plan 附录 11：守卫是 `containsJob` 而非身份比较 | `grep -rn 'currentCoroutineContext()\[Job\] !==' demo/src/main` | **空** ✅（`MicRecorder.kt:614` 的 `job !== currentCoroutineContext()[Job]` 在 audio 模块，不在本区范围，且是 spec 允许的直接自调用检查） |
| plan 附录 12：`ADPCMActivity` 保留 `kotlin.concurrent.thread` 且仍使用 | `grep -n 'kotlin.concurrent.thread\|^\s*thread {' ADPCMActivity.kt` | `:24 import kotlin.concurrent.thread`、`:54 thread {`（`onEncodeToADPCMClick()` 内）✅ |
| 8 个文件无 >100 字符的非 import 行 | `awk 'length($0)>100 && $0 !~ /^import /'` 逐文件 | **无输出** ✅（import 行按 ktlint 惯例排除，未单独统计） |
| 未新增调用处尾随逗号 | `git diff -U0` 中"新增行以 `,` 结尾且下一新增行以 `)` 开头" | **无命中** ✅。两个 `teardownSteps(` 的 vararg 末项 `{ senderClient?.release() }`（`AudioSender.kt:267`）、`{ receiverServer?.stopServer() }`（`AudioReceiver.kt:322`）均无尾随逗号；`AudioPlayer`/`MicRecorder` 构造调用的实参列表末项（`contentType = ...`、`enableAdvancedFeatures = true`）亦无 |
| 新增文件均未跟踪 | `git status --short demo/` | `?? SuspendTeardownGate.kt`、`?? TeardownSupport.kt`、`?? SuspendTeardownGateTest.kt`、`?? TeardownSupportTest.kt` ✅ |

**未核实项（需 Codex 本机或真机确认）**：

1. `:demo:testDevDebugUnitTest` / `:demo:compileDevDebugKotlin` / `:demo:detekt` / `:demo:ktlintCheck` 是否全绿（本次未运行 Gradle）。
2. `SuspendTeardownGateTest` 第 5 个用例触发的 `LogContext.log.e(...)` 在 JVM 单测中 `LogContext` 未初始化时是否抛出。
3. B.2.8 第 7 条：`senderClient` 没有"发布后再读 `stopping`"的补偿；本人推断已被 step ⑥→⑧ 顺序覆盖，但 spec §20.1 未列入，建议复核。
4. B.5.4 第 2 条：ADPCM "构造后立即释放"的残留窗口在 spec/plan 中未明文命名。
5. Task 5 全部真机项（PCM 播放中退出、连接进行中退出、teardown 期间重连/迟到接入、"点完发送立刻退出"与"receiver bind 完成前退出"两个补偿路径场景）均**未执行**。

## C. basenetty 关闭路径

> 范围：`basenetty/` 与三个 socket demo 调用方。基线 HEAD = `d3058e2bc`，以下"改前"均指该提交，"改后"均指工作树（未提交）。
> Netty 版本：`gradle/libs.versions.toml:109` `netty = "4.1.108.Final"`。
>
> 背景（摘自 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md`，此处只概述）：
> - §2 诊断：改前 `BaseNettyServer.stopServer()` 串行执行 `close().syncUninterruptibly()`、`bossGroup.shutdownGracefully().syncUninterruptibly()`、`workerGroup.shutdownGracefully().syncUninterruptibly()`。无参 `shutdownGracefully()` 的 quiet period 为 2 s，两个独立 group 串行等待 ⇒ 下限约 4 s；`syncUninterruptibly()` 让协程取消（线程中断）完全不起作用。
> - §4 修法：新增 `awaitCompletion()` listener 桥接；两端改为 suspend、先发后等、quiet period 0、资源层超时 3 s。
> - §10 第一次复审 R1–R6：`stopServer()` 加入 `lifecycleMutex`（R2）、客户端 `release()` 补资源层超时（R3）、两端 inbound handler `release()` 包 try/catch（R4）、`awaitCompletion` 收窄 `internal`（R5）、`cause()` 空值兜底（R6）。
> - §11 第二次复审 R7–R19：`isDone` 快速路径（R7）、取消 future 映射（R8）、`STOPPED` 守卫（R9）、取消 start 时关 channel（R10）、过时 KDoc（R11）、listener 在 mutex 内执行的说明（R12）、文档订正（R13）、删除空操作 `removeAll`（R14）、stop 先于 start 走 `releaseUnstartedServer()` + `startServer()` 按状态拒绝且状态保持 `STOPPED`（R15）、group 关闭请求放进 `finally`（R16）、共用 `releaseInboundHandler()`（R17）、`awaitCompletion` 测试（R18）、关闭体包 `NonCancellable`（R19）、客户端 `releaseNetty()` 对齐 R16——关 channel 放进 `try`，`finally` 无条件 `shutdownGracefully`（R20）。

### C.1 NettyFutureExt.kt（新增）

`basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/NettyFutureExt.kt`（未跟踪文件，全文 63 行）：

```kotlin
package com.leovp.basenetty.framework.base

import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Awaits this Netty future without blocking the calling thread.
 *
 * Netty's own `sync()` / `syncUninterruptibly()` park the caller until the future completes. On a
 * coroutine dispatcher that costs a pool thread for the whole wait, and `syncUninterruptibly()`
 * additionally ignores interruption, so cancelling the caller does not shorten it by one
 * millisecond. Registering a listener leaves the thread free and makes the wait cancellable.
 *
 * Cancelling the caller only detaches the listener. Whatever is being awaited - a channel close or
 * an event loop shutdown - is already in flight and must be allowed to finish; abandoning it half
 * way would strand exactly the resources the caller is trying to release.
 *
 * A future that is already done is handled before suspending, and that is not an optimisation.
 * `addListener()` on a done future hands the notification to the future's executor, and when that
 * executor is a terminated event loop Netty only logs "Failed to submit a listener notification
 * task" and never calls the listener. A bind on a released server and a close on a channel whose
 * loop is already gone both return exactly such a future; awaited through a listener, either
 * would hang forever - `sync()` never had this problem because it checks `isDone()` first.
 *
 * A Netty future that was cancelled fails with `java.util.concurrent.CancellationException`, the
 * same class the coroutine machinery uses on the JVM. It is reported as [IllegalStateException]
 * instead so a caller cannot mistake the future's cancellation for its own.
 *
 * Internal on purpose: `basenetty` is published, and both callers live in this module. Making it
 * part of the public surface would commit the library to this signature for downstream users.
 */
internal suspend fun <V> Future<V>.awaitCompletion() {
    if (isDone) {
        failureOrNull()?.let { throw it }
        return
    }
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        val listener = GenericFutureListener<Future<V>> { future ->
            val failure = future.failureOrNull()
            if (failure == null) cont.resume(Unit) else cont.resumeWithException(failure)
        }
        addListener(listener)
        cont.invokeOnCancellation { removeListener(listener) }
    }
}

/**
 * The failure of this done future, or null when it succeeded.
 *
 * `cause()` is a platform type. A DefaultPromise that is done and not successful holds one, but a
 * custom Future implementation need not: passing null to `resumeWithException` would throw inside
 * the netty callback, where nothing resumes the continuation and the caller would wait out its
 * whole timeout - or forever, where there is none.
 */
private fun Future<*>.failureOrNull(): Throwable? = when {
    isSuccess -> null
    isCancelled -> IllegalStateException("Netty future was cancelled")
    else -> cause() ?: IllegalStateException("Netty future failed without a cause")
}
```

说明：

1. **listener 桥接**。`suspendCancellableCoroutine` 注册一个 `GenericFutureListener`，future 完成时在 Netty 线程上 `resume` / `resumeWithException`。调用线程不被 park，这是替代 `sync()` / `syncUninterruptibly()` 的核心。
2. **取消只摘 listener**。`invokeOnCancellation { removeListener(listener) }`：调用方被取消时，仅解除对 future 的观察，不调用 `future.cancel()`。被等待的 close / shutdown 已在飞行中，中止它只会让资源半释放。
3. **`isDone` 快速路径是必需的，不是优化**（R7）。Netty 机制：`DefaultPromise.addListener()` 在 future 已完成时直接调用 `notifyListeners()`；`notifyListeners()` 在非 event loop 线程上通过 `safeExecute(executor, task)` 把通知投递到该 future 绑定的 executor；若该 executor 是已终止的 event loop，`execute()` 抛 `RejectedExecutionException`，`safeExecute` 捕获后仅以 `rejectedExecutionLogger.error("Failed to submit a listener notification task. Event loop shut down?", t)` 记日志，listener 永不被调用。两条本模块可达路径都会产出"已完成且挂在死 loop 上"的 future：(a) 在已释放的 server 上 `bootstrap.bind()`——`AbstractBootstrap.initAndRegister()` 中 `group().register(channel)` 在终止的 loop 上 `execute()` 失败，`AbstractUnsafe.register()` 以 `safeSetFailure(promise, t)` 返回一个已失败、绑定到该死 loop 的 promise，`doBind()` 看到 `regFuture.cause() != null` 直接把它返回；(b) 对 loop 已终止的 channel 调 `close()`。`sync()` 从未有这个问题，因为 `DefaultPromise.await()` 第一步就检查 `isDone()`。测试 `awaitCompletion does not hang on a failed future whose executor is terminated`（C.4）直接构造了情形 (a)/(b) 的本质：终止 loop 上的 `DefaultPromise.setFailure()`。
4. **`failureOrNull()` 把 `isCancelled` 映射为 `IllegalStateException`**（R8）。被取消的 Netty future 的 `cause()` 是 `java.util.concurrent.CancellationException`，与 `kotlinx.coroutines.CancellationException` 是同一个 JVM 类。若原样 `resumeWithException`，`awaitOrLog()` / `releaseNetty()` 中的 `catch (e: CancellationException) { throw e }` 会把"future 被取消"误判成"调用方自己被取消"并上抛，导致本该继续的关闭序列中断。`cause() ?: IllegalStateException(...)` 是 R6 的平台类型兜底：若在 Netty 回调里对 `null` 调 `resumeWithException`，异常抛在 Netty 线程，continuation 永不恢复。
5. `internal` 可见性（R5）：`basenetty` 经 JitPack 发布，唯一的两个调用方（`BaseNettyServer`、`BaseNettyClient`）都在模块内；测试通过同模块 `internal` 访问。

### C.2 BaseNettyServer.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.1 / J.4）。

`basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt`（改前 365 行 → 改后 558 行；`git diff --numstat` +258 / −65）。`initHandler()`、`executeCommand()` 等其它部分未改动。

#### C.2.1 imports

仅新增，无删除（改后 L1–42）：

```diff
+import com.leovp.basenetty.framework.base.awaitCompletion
+import io.netty.channel.ChannelFuture
+import io.netty.util.concurrent.Future
+import java.util.concurrent.TimeUnit
+import kotlinx.coroutines.CancellationException
+import kotlinx.coroutines.NonCancellable
+import kotlinx.coroutines.sync.Mutex
+import kotlinx.coroutines.sync.withLock
+import kotlinx.coroutines.withContext
+import kotlinx.coroutines.withTimeoutOrNull
```

#### C.2.2 companion 常量

改前（HEAD L46–48）：

```kotlin
    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
    }
```

改后（L56–84；`NETTY_BIND_TIMEOUT_IN_MILLS` 为第五轮 I.1 新增）：

```kotlin
    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000

        /**
         * Hard limit each event loop group gets to drain. The quiet period is passed as 0, so
         * this is the only wait that remains.
         */
        private const val SHUTDOWN_TIMEOUT_IN_SECONDS = 2L

        /**
         * Upper bound for the whole netty shutdown. See [stopServer].
         *
         * Budgeted above [SHUTDOWN_TIMEOUT_IN_SECONDS] on purpose: the two groups overlap, so the
         * shutdown itself costs at most one of them, and the remainder is headroom for closing
         * the channel first.
         */
        private const val NETTY_SHUTDOWN_TIMEOUT_IN_MILLS = 3_000L

        /**
         * Upper bound for the bind in [startServer].
         *
         * A bind is a local socket operation handed to the boss loop, so this is not a budget
         * for a slow network. It is what keeps a wedged boss loop from parking the caller here
         * for good while it holds [lifecycleMutex] - the same mutex [stopServer] needs before it
         * can release anything, so an unbounded wait here would stall every teardown queued
         * behind it as well.
         */
        private const val NETTY_BIND_TIMEOUT_IN_MILLS = 3_000L
    }
```

#### C.2.3 `lifecycleMutex`（新增字段，L105–115）

改前只有 `private lateinit var serverChannel: Channel`（HEAD L67）紧接 `channelInitializer`。改后在两者之间插入：

```kotlin
    /**
     * Serializes [startServer] against [stopServer].
     *
     * It replaces the `@Synchronized` that used to guard start alone - Kotlin does not allow that
     * annotation on a suspend function, and a monitor would block a dispatcher thread here anyway.
     *
     * Stop has to share it. Now that start returns as soon as the port is bound, a stop that
     * arrives while the bind is still in flight would find [serverChannel] uninitialized, return
     * false, and leave the server it was asked to stop listening on the port for good.
     */
    private val lifecycleMutex = Mutex()
```

#### C.2.4 `startServer()`

改前（HEAD L123–165，`@Synchronized` 普通函数，阻塞到服务器关闭为止）：

```kotlin
    /**
     * If server has already been released, call this method will
     * throw [java.util.concurrent.RejectedExecutionException]: event executor terminated
     */
    //    @Throws(RejectedExecutionException::class)
    @Synchronized
    fun startServer() {
        LogContext.log.i(tag, "===== connect() current state=${connectState.get().name} =====")
        if (connectState.get() == ServerConnectStatus.STARTED) {
            LogContext.log.w(tag, "===== Already started or not initialized =====")
            return
        }
        try {
            serverChannel = bootstrap.bind(port).sync().channel()
            connectState.set(ServerConnectStatus.STARTED)
            LogContext.log.i(tag, "===== Start successfully =====")
            connectionListener.onStarted(this)
            // After running the line below, the process will get stuck there and wait for client
            // connection
            serverChannel.closeFuture().sync()
            // When serverChannel.close is executed, the process will continue to run.
            LogContext.log.i(tag, "===== Stopping server... =====")
        } catch (e: RejectedExecutionException) {
            LogContext.log.e(tag, "===== RejectedExecutionException: ${e.message} =====", e)
            LogContext.log.e(
                tag,
                "Netty server had already been released. You must re-initialize it again."
            )
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onStartFailed(
                this,
                ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED,
                e.message
            )
        } catch (e: Exception) {
            connectState.set(ServerConnectStatus.FAILED)
            connectionListener.onStartFailed(
                this,
                ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR,
                e.message
            )
        }
    }
```

改后（L171–293，suspend，绑定后立即返回；bind 的 3 秒上界 `withTimeoutOrNull(NETTY_BIND_TIMEOUT_IN_MILLS)` 为第五轮 I.1 新增）：

```kotlin
    /**
     * Binds the server port and returns. It does **not** block for the server's lifetime.
     *
     * The previous implementation parked the caller in `serverChannel.closeFuture().sync()` until
     * the server stopped, which had two consequences that could not be worked around by callers:
     * a caller writing `server = Server(...).also { it.startServer() }` never reached the
     * assignment while the server was up, and the `@Synchronized` monitor was held for the whole
     * session. To learn when the server is gone, use [ServerConnectListener.onStopped] or the
     * completion of [stopServer].
     *
     * A server that [stopServer] has already released - including one that was stopped before it
     * was ever started - is refused up front and reported through
     * [ServerConnectListener.onStartFailed] with
     * [ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED]. A bind that fails with
     * [java.util.concurrent.RejectedExecutionException] (event executor terminated) is reported
     * the same way rather than thrown.
     *
     * The bind itself is bounded by [NETTY_BIND_TIMEOUT_IN_MILLS] and reported through
     * [ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR] when it runs out. That bound
     * belongs here because this wait holds [lifecycleMutex].
     */
    suspend fun startServer() {
        lifecycleMutex.withLock {
            LogContext.log.i(
                tag,
                "===== startServer() current state=${connectState.get().name} ====="
            )
            if (connectState.get() == ServerConnectStatus.STARTED) {
                LogContext.log.w(tag, "===== Already started or not initialized =====")
                return@withLock
            }
            if (connectState.get() == ServerConnectStatus.STOPPED) {
                // Decided by state, not by netty thread timing: a stop that got the mutex first
                // has released the groups (see stopServer), and binding on them would only fail
                // the same way - or, if their shutdown ran out of budget, hang here holding the
                // mutex. The ALREADY_RELEASED branch below stays for a group terminated by
                // other means.
                //
                // The state stays STOPPED. Moving it to FAILED would let a later stopServer()
                // past its guard - serverChannel may well be initialised from an earlier start -
                // and run the whole teardown a second time, onStopped() included.
                LogContext.log.e(tag, "Netty server had already been released. Re-create it.")
                connectionListener.onStartFailed(
                    this,
                    ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED,
                    "Server already released"
                )
                return@withLock
            }
            var bindFuture: ChannelFuture? = null
            try {
                val future = bootstrap.bind(port)
                bindFuture = future
                // Bounded like the shutdown is, and for a stronger reason: this wait holds
                // lifecycleMutex, so a bind that never completes takes stopServer() down with
                // it. Held in a local val rather than read back through the nullable var, which
                // a lambda cannot smart-cast.
                val bound = withTimeoutOrNull(NETTY_BIND_TIMEOUT_IN_MILLS) {
                    future.awaitCompletion()
                }
                if (bound == null) {
                    LogContext.log.e(
                        tag,
                        "===== Bind did not finish within " +
                            "${NETTY_BIND_TIMEOUT_IN_MILLS}ms ====="
                    )
                    // Close it: the bind may still land inside the boss loop, and serverChannel
                    // is never assigned on this path, so stopServer() would find nothing to
                    // close and the port would stay held for good.
                    future.channel().close()
                    // FAILED, not STOPPED: the two event loop groups are still alive, and a
                    // later stopServer() must stay free to release them.
                    connectState.set(ServerConnectStatus.FAILED)
                    connectionListener.onStartFailed(
                        this,
                        ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR,
                        "Bind timed out after ${NETTY_BIND_TIMEOUT_IN_MILLS}ms"
                    )
                    return@withLock
                }
                serverChannel = future.channel()
                connectState.set(ServerConnectStatus.STARTED)
                LogContext.log.i(tag, "===== Start successfully =====")
                // Observe the close instead of blocking on it. The listener costs nothing and
                // keeps the log line the old closeFuture().sync() produced when it returned.
                serverChannel.closeFuture().addListener {
                    LogContext.log.i(tag, "===== Server channel closed =====")
                }
                connectionListener.onStarted(this)
            } catch (e: RejectedExecutionException) {
                LogContext.log.e(tag, "===== RejectedExecutionException: ${e.message} =====", e)
                LogContext.log.e(
                    tag,
                    "Netty server had already been released. You must re-initialize it again."
                )
                connectState.set(ServerConnectStatus.FAILED)
                connectionListener.onStartFailed(
                    this,
                    ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED,
                    e.message
                )
            } catch (e: CancellationException) {
                // Cancelling the caller is not a start failure. Letting it fall through to the
                // Exception branch below would hand the listener an onStartFailed() nobody asked
                // for - CancellationException is an ordinary Exception subclass on the JVM.
                //
                // The bind itself carries on inside the boss loop and may well succeed. Close the
                // channel, or a cancelled start leaves a bound server nothing can ever stop:
                // serverChannel is never assigned, so stopServer() would return false for good.
                // Only the port is freed here; the two event loop groups outlive a cancelled start
                // just as they outlive a failed one, and need a new instance to be left behind.
                bindFuture?.channel()?.close()
                throw e
            } catch (e: Exception) {
                connectState.set(ServerConnectStatus.FAILED)
                connectionListener.onStartFailed(
                    this,
                    ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR,
                    e.message
                )
            }
        }
    }
```

#### C.2.5 `stopServer()` 及其辅助函数

改前（HEAD L167–213，普通函数，全程阻塞）：

```kotlin
    /**
     * Stop and release server using **syncUninterruptibly** method.(Full release will cost almost
     * 4s.) So you'd better NOT call this method in main thread.
     *
     * Once you call this method, you can not start server again simply by calling [startServer]
     * because of the Server Netty object will be released.
     * If you want to start server again, you must recreate the Server Netty object.
     */
    fun stopServer(): Boolean {
        LogContext.log.w(tag, "===== stopServer() current state=${connectState.get().name} =====")
        if (!::serverChannel.isInitialized ||
            ServerConnectStatus.UNINITIALIZED == connectState.get()
        ) {
            LogContext.log.w(tag, "Already released or not initialized")
            return false
        }
        connectState.set(ServerConnectStatus.UNINITIALIZED)

        LogContext.log.w(tag, "Releasing default server inbound handler first...")
        defaultServerInboundHandler?.release()
        defaultServerInboundHandler = null
        channelInitializer = null

        serverChannel.run {
            LogContext.log.w(tag, "Closing channel...")
            runCatching {
                pipeline().removeAll { true }
                closeFuture()
                close().syncUninterruptibly()
            }.onFailure { LogContext.log.e(tag, "Close channel error.", it) }
        }

        runCatching {
            LogContext.log.w(tag, "Releasing bossGroup...")
            bossGroup.shutdownGracefully().syncUninterruptibly() // Will not get stuck here.
        }.onFailure { LogContext.log.e(tag, "Shutdown bossGroup error.", it) }

        runCatching {
            LogContext.log.w(tag, "Releasing workerGroup...")
            workerGroup.shutdownGracefully().syncUninterruptibly() // Will not get stuck here.
        }.onFailure { LogContext.log.e(tag, "Shutdown workerGroup error.", it) }

        LogContext.log.w(tag, "=====> Server released <=====")
        connectState.set(ServerConnectStatus.STOPPED)
        connectionListener.onStopped()
        return true
    }
```

改后 `stopServer()`（L295–377；状态迁移与 `onStopped()` 入 `finally` 为第五轮 I.2，`releaseInboundHandler()` 挪进 `try` 与 "Defence in depth" 注释为第六轮 J.1 / J.4）：

```kotlin
    /**
     * Stops and releases the server, suspending instead of blocking the caller's thread.
     *
     * Once you call this method, you can not start server again simply by calling [startServer]
     * because of the Server Netty object will be released.
     * If you want to start server again, you must recreate the Server Netty object.
     *
     * The netty part is bounded by [NETTY_SHUTDOWN_TIMEOUT_IN_MILLS]. That bound belongs here, at
     * the resource boundary, rather than in the caller: a shutdown that cannot finish must not
     * hold a teardown sequence forever, yet the state transition and [ServerConnectListener]
     * notification after it still have to run.
     *
     * Once past the guard, the teardown runs under [NonCancellable]: cancelling the caller cannot
     * shorten a resource release, it can only abandon it half way with the state left on
     * "stopping", after which every later call returns false and the event loops live for good.
     * The timeout above is the only thing that cuts it short, and it still works inside
     * [NonCancellable]. A caller cancelled while still waiting for [lifecycleMutex] returns at
     * once: it has touched nothing yet.
     *
     * Serialized against [startServer] through [lifecycleMutex], so a stop issued while the port
     * is still being bound waits for that bind rather than returning false and orphaning it.
     *
     * The state guard rejects a call that overlaps another one and a call on a server that is
     * already stopped, so a repeated stop returns false without touching netty again and
     * [ServerConnectListener.onStopped] fires once per server.
     *
     * A stop that arrives before the server was ever started also returns false - nothing was
     * running, so [ServerConnectListener.onStopped] is not fired - but it is not a no-op: it
     * releases both event loop groups, which otherwise outlive a never-started server for good,
     * and leaves the state STOPPED. That settles the race with a [startServer] still waiting for
     * [lifecycleMutex]: it sees STOPPED and reports
     * [ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED] through
     * [ServerConnectListener.onStartFailed] instead of bringing up a server nothing will stop.
     * The mutex alone only serializes the two; it does not make a stop that got there first win.
     *
     * [ServerConnectListener] callbacks run while [lifecycleMutex] is held. They are not suspend
     * and the mutex is not reentrant, so a listener that wants to call [startServer] or
     * [stopServer] in response must launch that call on another coroutine.
     */
    suspend fun stopServer(): Boolean = lifecycleMutex.withLock {
        LogContext.log.w(tag, "===== stopServer() current state=${connectState.get().name} =====")
        val state = connectState.get()
        if (ServerConnectStatus.STOPPED == state) {
            LogContext.log.w(tag, "Already released")
            return@withLock false
        }
        if (!::serverChannel.isInitialized) {
            releaseUnstartedServer()
            return@withLock false
        }
        if (ServerConnectStatus.UNINITIALIZED == state) {
            // Defence in depth. The mutex serialises stops and the finally below always lands on
            // STOPPED, so no caller should observe UNINITIALIZED with serverChannel initialised.
            LogContext.log.w(tag, "Already stopping")
            return@withLock false
        }
        connectState.set(ServerConnectStatus.UNINITIALIZED)
        withContext(NonCancellable) {
            try {
                // Inside the try as well: releaseInboundHandler() only catches Exception, and an
                // Error out of subclass code must not leave the state on UNINITIALIZED either.
                releaseInboundHandler()
                shutdownBounded { shutdownNetty() }
            } finally {
                // In a finally, because the KDoc above promises these two run regardless.
                // awaitOrLog() only guards the awaits; a throw from close() or
                // shutdownGracefully() itself would escape withTimeoutOrNull and this
                // withContext, and the state would stay UNINITIALIZED - where every later
                // stopServer() returns false at the "Already stopping" guard and onStopped()
                // never arrives at all.
                LogContext.log.w(tag, "=====> Server released <=====")
                connectState.set(ServerConnectStatus.STOPPED)
                // Guarded like releaseInboundHandler(): subclass code, and a throw from this
                // finally would also discard whatever the teardown above was already raising.
                try {
                    connectionListener.onStopped()
                } catch (e: Exception) {
                    LogContext.log.e(tag, "onStopped() error.", e)
                }
            }
        }
        return@withLock true
    }
```

`releaseUnstartedServer()`（新增，L379–398；`try`/`finally` 结构同上，第六轮 J.1）：

```kotlin
    /**
     * Stop before start. There is no channel to close, but the two event loop groups were built
     * with this object and would otherwise live for good. Terminating them here, inside the
     * mutex, is also what decides the race this call just won: see [stopServer].
     */
    private suspend fun releaseUnstartedServer() = withContext(NonCancellable) {
        LogContext.log.w(tag, "Not started; releasing the event loops so a later start is refused")
        try {
            releaseInboundHandler()
            shutdownBounded {
                requestEventLoopShutdown()
                awaitEventLoopShutdown()
            }
        } finally {
            // In a finally for the same reason as in stopServer(): this state is what refuses a
            // later startServer(), and leaving it unset would bring up a server that the race
            // this call just won was meant to prevent.
            connectState.set(ServerConnectStatus.STOPPED)
        }
    }
```

`releaseInboundHandler()`（新增，L400–411）：

```kotlin
    private fun releaseInboundHandler() {
        // Guarded like every other step: this hands control to subclass code, and letting it
        // throw here would skip the channel and both event loop groups entirely.
        LogContext.log.w(tag, "Releasing default server inbound handler first...")
        try {
            defaultServerInboundHandler?.release()
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release inbound handler error.", e)
        }
        defaultServerInboundHandler = null
        channelInitializer = null
    }
```

`shutdownBounded()`（新增，L413–421）：

```kotlin
    private suspend fun shutdownBounded(block: suspend () -> Unit) {
        if (withTimeoutOrNull(NETTY_SHUTDOWN_TIMEOUT_IN_MILLS) { block() } == null) {
            LogContext.log.e(
                tag,
                "Netty shutdown did not finish within ${NETTY_SHUTDOWN_TIMEOUT_IN_MILLS}ms. " +
                    "Check whether closing the channel, not the event loops, used up the budget."
            )
        }
    }
```

`shutdownNetty()`（新增，L423–435）：

```kotlin
    private suspend fun shutdownNetty() {
        LogContext.log.w(tag, "Closing channel...")
        try {
            serverChannel.close().awaitOrLog("Close channel")
        } finally {
            // Reached on the timeout too, as the cancellation it carries passes through here. The
            // request does not suspend, so a close that used up the whole budget still leaves both
            // groups shutting down instead of alive for good - which is the case the budget exists
            // for: a boss loop wedged in a handler is exactly what makes the close hang.
            requestEventLoopShutdown()
        }
        awaitEventLoopShutdown()
    }
```

`requestEventLoopShutdown()`（新增，L437–452）：

```kotlin
    /**
     * Asks both groups to shut down without waiting. Idempotent: a group already shutting down
     * just returns its termination future again.
     *
     * Both groups are asked BEFORE either one is awaited. They are independent, so awaiting them
     * one after the other only adds their quiet periods together instead of letting them
     * overlap - that serial wait is where the old "almost 4s" came from.
     *
     * Quiet period 0: it exists to let in-flight work settle before the loop dies, which buys
     * nothing for a server that is being torn down and whose channel is already closed.
     */
    private fun requestEventLoopShutdown() {
        LogContext.log.w(tag, "Releasing bossGroup and workerGroup...")
        bossGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    }
```

`awaitEventLoopShutdown()`（新增，L454–457）：

```kotlin
    private suspend fun awaitEventLoopShutdown() {
        bossGroup.terminationFuture().awaitOrLog("Shutdown bossGroup")
        workerGroup.terminationFuture().awaitOrLog("Shutdown workerGroup")
    }
```

`awaitOrLog()`（新增，L459–472）：

```kotlin
    /**
     * Awaits this future and logs an ordinary failure instead of aborting the rest of the
     * teardown. Cancellation is rethrown: it carries the timeout from [stopServer] and must not
     * be downgraded into a logged failure.
     */
    private suspend fun <V> Future<V>.awaitOrLog(what: String) {
        try {
            awaitCompletion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogContext.log.e(tag, "$what error.", e)
        }
    }
```

#### C.2.6 状态机

`stopServer()` 入口（已持有 `lifecycleMutex`；`state` 为入口处一次读取的快照）：

| `::serverChannel.isInitialized` | `connectState` | 动作 | 返回 | `onStopped()` |
|---|---|---|---|---|
| 任意 | `STOPPED` | 记 "Already released"，不触碰 netty | `false` | 否 |
| 否 | `UNINITIALIZED`（从未 start，或 start 被取消） | `releaseUnstartedServer()`：`try { releaseInboundHandler() → 3 s 预算内请求并等待两个 group 终止 } finally { 置 STOPPED }` | `false` | 否 |
| 否 | `FAILED`（bind 失败，或被 `STOPPED` 守卫拒绝过的 start） | 同上 | `false` | 否 |
| 否 | `STARTED` | 不可达：`STARTED` 只在 `serverChannel` 赋值后设置 | — | — |
| 是 | `UNINITIALIZED` | 记 "Already stopping"。静态分析认为仅防御性：另一个 stop 在 mutex 外排队，拿到锁时状态已是 `STOPPED`，走第一行（第六轮 J.4 已在代码中加注为 defence in depth） | `false` | 否 |
| 是 | `STARTED` | 完整路径：置 `UNINITIALIZED` → `NonCancellable { try { releaseInboundHandler(); shutdownBounded { shutdownNetty() } } finally { 置 STOPPED; onStopped() } }` | `true` | 是（一次） |
| 是 | `FAILED` | 不可达：`FAILED` 只在 bind 失败的两个 catch 分支设置，而那时 `serverChannel` 未被该次赋值；一个成功 start 过的实例之后只能经 stop 进入 `STOPPED`，而 `STOPPED` 守卫拒绝 start 时**不再改写状态**（见审查要点第 8 条） | — | — |

`startServer()` 入口（已持有 `lifecycleMutex`）：

| `connectState` | 动作 |
|---|---|
| `UNINITIALIZED` | `bootstrap.bind(port)` → `awaitCompletion()` → 赋值 `serverChannel`、置 `STARTED`、`onStarted()` |
| `STARTED` | 记 "Already started or not initialized"，返回，无回调 |
| `STOPPED` | 不触碰 netty、**不改写状态**（保持 `STOPPED`）：仅回调 `onStartFailed(CONNECTION_ERROR_ALREADY_RELEASED, "Server already released")` |
| `FAILED` | 与 `UNINITIALIZED` 相同，再次尝试 bind（允许 "Address already in use" 后重试；若 group 已终止，`bind()` 返回已失败 future，`awaitCompletion()` 快速路径抛 `RejectedExecutionException`，进入 `ALREADY_RELEASED` 分支） |
| bind 期间调用方被取消 | `bindFuture?.channel()?.close()` 后重抛 `CancellationException`；状态保持 `UNINITIALIZED`，无回调 |
| 排队等待 mutex 时被取消 | `withLock` 抛 `CancellationException`，未触碰任何东西 |

#### C.2.7 审查要点

1. **mutex 只串行化，状态决定胜负**（R15）。demo 在两个独立协程上分别 `launch { startServer() }` 与 `launch { stopServer() }`。若 stop 先拿锁，改前会因 `::serverChannel.isInitialized == false` 返回 `false` 而什么都不做，随后 start 正常 bind，端口被占且两个 group 永不关闭。改后 stop 先拿锁走 `releaseUnstartedServer()`：在锁内终止两个 group、置 `STOPPED`；随后 start 看到 `STOPPED` 按状态拒绝，不依赖 Netty 线程时序（若 group 的关闭耗尽了 3 s 预算仍停在 SHUTTING_DOWN，直接 bind 可能被接受后再失败，甚至 `awaitCompletion()` 持锁挂起）。
2. **`NonCancellable` 的边界**（R19）。守卫与 `lifecycleMutex.withLock` 的等待在 `NonCancellable` 之外：排队中被取消立即退出，未触碰资源。只有状态已置为 `UNINITIALIZED`（"stopping"）之后的关闭体在 `NonCancellable` 内，保证状态迁移到 `STOPPED` 与 `onStopped()` 一定执行，不会把状态留在"进行中"态导致之后所有 `stopServer()` 返回 `false`。
3. **`withTimeoutOrNull` 在 `NonCancellable` 内仍然有效**。它取消的是自己创建的 `TimeoutCoroutine`，不受父上下文 `NonCancellable` 影响；因此 3 s 上界不被 R19 削弱（doc §10.1 已确认）。
4. **group 关闭请求放在 `finally`**（R16）。超时时 `withTimeoutOrNull` 以 `TimeoutCancellationException` 取消 `block()`，该取消穿过 `awaitOrLog()`（重抛）后仍会经过 `shutdownNetty()` 的 `finally`。`requestEventLoopShutdown()` 不挂起，所以即便 `serverChannel.close()` 吃光全部预算，两个 group 也已被请求关闭，而不是活到进程结束。改前（9/20 批次）这一病态场景下 `shutdownGracefully` 根本不会被调用。
5. **listener 回调在 mutex 内执行、不可重入**（R12）。`onStarted()` / `onStartFailed()` / `onStopped()` 都在 `lifecycleMutex.withLock` 内调用；`Mutex` 不可重入，若监听器在回调中同步等待 `startServer()` / `stopServer()` 会死锁。当前 demo 监听器全部只 `launch`。`onStopped()` 还在 `NonCancellable` 内。
6. **并行关闭 + quiet period 0**。改前 `bossGroup.shutdownGracefully().syncUninterruptibly()` 等满 2 s quiet period 之后才对 `workerGroup` 发出请求，再等 2 s ⇒ ≈ 4 s。改后两个 `shutdownGracefully(0, 2, SECONDS)` 先后立即发出，再依次等 `terminationFuture()`，两段等待重叠，理论上界为单个 group 的 2 s hard timeout；`NETTY_SHUTDOWN_TIMEOUT_IN_MILLS = 3_000` 的余量留给前面的 `close()`。
7. **`RejectedExecutionException` 分支不再是死代码**（R7）。有了 `isDone` 快速路径，`bind()` 返回的已失败 future 会把 cause（`RejectedExecutionException`）同步抛给 `catch`；改前（9/20 批次的 listener 版本）这一分支永远不会命中，而是持锁永久挂起。
8. **已关闭的问题：start → stop → start → stop 不再二次触发 `onStopped()`**。上一版 `STOPPED` 守卫拒绝 start 时把状态改写为 `FAILED`；在该序列下第四步 `stopServer()` 读到 `FAILED`，且 `serverChannel` 由第一步初始化，三道守卫全部通过，整套 teardown 连同 `onStopped()` 会再跑一遍（close 在已终止的 loop 上要么快速失败要么耗尽 3 s 预算）。现在 L209–211 的注释写明状态**保持 `STOPPED`**、分支内不再有 `connectState.set(...)`：第四步 `stopServer()` 在第一道守卫 `ServerConnectStatus.STOPPED == state` 处即返回 `false`，不触碰 netty、不回调。doc §11 R15 行已同步为"状态保持 `STOPPED`"，§8.3 第 14 项（R21 插入 bind 超时项后顺延）的真机验收写明"状态仍为 `STOPPED`"。测试覆盖见 C.4：`stop before start ...` 现断言被拒绝的 start 之后状态为 `STOPPED`、再次 `stopServer()` 返回 `false`、`stoppedCount == 0`（`serverChannel` 未初始化的变体）；`serverChannel` 已初始化的变体（start → stop → start → stop）没有直接用例，但命中的是同一道守卫。
9. **观察**：`stopServer()` KDoc 开头仍保留旧句 "Once you call this method, you can not start server again simply by calling [startServer] because of the Server Netty object will be released."（语法沿袭改前），与新 KDoc 并存，不影响行为。
10. **观察**：`startServer()` 内 `serverChannel.closeFuture().addListener { ... }` 用于保留旧的日志行；该 listener 在 boss loop 上执行，只记日志，无状态副作用。
11. **观察**：`connectState` 字段声明为 `@Volatile internal var connectState: AtomicReference<...>`（改前即如此，未改动）；`@Volatile` 修饰的是引用而非内容，冗余但无害。

### C.3 BaseNettyClient.kt

`basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt`（`git diff --numstat` +108 / −35）。`connect()`、`closeChannel()`、重试逻辑等未改动。

#### C.3.1 imports

仅新增：

```diff
+import com.leovp.basenetty.framework.base.awaitCompletion
+import java.util.concurrent.TimeUnit
+import kotlinx.coroutines.CancellationException
+import kotlinx.coroutines.NonCancellable
+import kotlinx.coroutines.withContext
```

`suspendCancellableCoroutine` 仍被 `connect()` 与 `disconnectNow()` 使用，保留。

#### C.3.2 companion 常量

改前（HEAD L98–102）：

```kotlin
    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
        private const val DISCONNECT_TIMEOUT_IN_MILLS = 5_000L
        private const val WEBSOCKET_HANDSHAKE_TIMEOUT_IN_MILLS = 30_000L
    }
```

改后（L103–121）：

```kotlin
    companion object {
        private const val CONNECTION_TIMEOUT_IN_MILLS = 30_000
        private const val DISCONNECT_TIMEOUT_IN_MILLS = 5_000L
        private const val WEBSOCKET_HANDSHAKE_TIMEOUT_IN_MILLS = 30_000L

        /**
         * Hard limit the event loop group gets to drain. The quiet period is passed as 0, so this
         * is the only wait that remains.
         */
        private const val SHUTDOWN_TIMEOUT_IN_SECONDS = 2L

        /**
         * Upper bound for the whole netty release. See [release].
         *
         * Budgeted above [SHUTDOWN_TIMEOUT_IN_SECONDS] on purpose: the remainder is headroom for
         * closing the channel first.
         */
        private const val NETTY_RELEASE_TIMEOUT_IN_MILLS = 3_000L
    }
```

#### C.3.3 `disconnectManually()` 拆分

改前（HEAD L597–628，守卫之后直接继续执行关闭体）：

```kotlin
    suspend fun disconnectManually(): ClientConnectStatus {
        LogContext.log.w(
            tag,
            "===== disconnectManually() current state=${connectStatus.get().name} ====="
        )
        val wasConnecting: Boolean
        synchronized(this) {
            val connStatus = connectStatus.get()
            wasConnecting = ClientConnectStatus.CONNECTING == connStatus
            if (ClientConnectStatus.DISCONNECTED == connStatus ||
                ClientConnectStatus.UNINITIALIZED == connStatus
            ) {
                LogContext.log.w(
                    tag,
                    "Socket is not connected or already disconnected or not initialized."
                )
                return connectStatus.get()
            } else if (ClientConnectStatus.DISCONNECTING == connectStatus.get()) {
                LogContext.log.w(tag, "Socket is disconnecting. Stop processing.")
                return connectStatus.get()
            } else if (ClientConnectStatus.CONNECTING == connectStatus.get()) {
                LogContext.log.w(
                    tag,
                    "Socket is connecting. Cancel current connection attempt."
                )
            }
            connectStatus.set(ClientConnectStatus.DISCONNECTING)
            disconnectManually = true
        }

        stopRetryHandler()
        defaultInboundHandler?.release()

        if (wasConnecting) {
```

改后：KDoc 追加一段（L616–618），守卫（L620–650）结尾改为进入 `NonCancellable`，关闭体抽成 `disconnectNow()`（L652 起）：

```kotlin
     * Past the state guard this runs under [NonCancellable]: a caller cancelled half way would
     * otherwise leave the state on DISCONNECTING, where [release] refuses to run and the event
     * loop group is never shut down. The disconnect itself stays bounded by its own timeout.
     */
    suspend fun disconnectManually(): ClientConnectStatus {
        LogContext.log.w(
            tag,
            "===== disconnectManually() current state=${connectStatus.get().name} ====="
        )
        val wasConnecting: Boolean
        synchronized(this) {
            val connStatus = connectStatus.get()
            wasConnecting = ClientConnectStatus.CONNECTING == connStatus
            if (ClientConnectStatus.DISCONNECTED == connStatus ||
                ClientConnectStatus.UNINITIALIZED == connStatus
            ) {
                LogContext.log.w(
                    tag,
                    "Socket is not connected or already disconnected or not initialized."
                )
                return connectStatus.get()
            } else if (ClientConnectStatus.DISCONNECTING == connectStatus.get()) {
                LogContext.log.w(tag, "Socket is disconnecting. Stop processing.")
                return connectStatus.get()
            } else if (ClientConnectStatus.CONNECTING == connectStatus.get()) {
                LogContext.log.w(
                    tag,
                    "Socket is connecting. Cancel current connection attempt."
                )
            }
            connectStatus.set(ClientConnectStatus.DISCONNECTING)
            disconnectManually = true
        }
        return withContext(NonCancellable) { disconnectNow(wasConnecting) }
    }

    private suspend fun disconnectNow(wasConnecting: Boolean): ClientConnectStatus {
        stopRetryHandler()
        // Guarded like in releaseNow(): subclass code throwing here would strand the state on
        // DISCONNECTING, the very thing the NonCancellable wrapper above exists to rule out.
        try {
            defaultInboundHandler?.release()
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release inbound handler error.", e)
        }

        if (wasConnecting) {
            val targetChannel = if (::channel.isInitialized) {
                channel
            } else {
                runCatching { connectFuture?.channel() }.getOrNull()
            }
```

`disconnectNow()` 其余部分（`wasConnecting` 分支、`!::channel.isInitialized` 分支、`withTimeoutOrNull(disconnectTimeoutInMillis) { suspendCancellableCoroutine { ... channel.disconnect() ... } }`）为改前 L630 以下代码**原样搬入**，diff 中仅因缩进/位置呈现为上下文行，无逻辑改动。

#### C.3.4 `release()` 拆分

改前（HEAD L802–877，整个函数是一个 `suspendCancellableCoroutine`，守卫与关闭体同在 lambda 内）：

```kotlin
    /**
     * Release netty client using **syncUninterruptibly** method.(Full release will cost almost
     * 2200ms.)
     * So you'd better NOT call this method in main thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect] simply,
     * you must recreate netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     *
     * If current connect state is [ClientConnectStatus.FAILED], this method will also be run and
     * any exception will be ignored.
     */
    suspend fun release(): Boolean = suspendCancellableCoroutine { cont ->
        LogContext.log.w(
            tag,
            "===== release() current state=${connectStatus.get().name} ====="
        )
        synchronized(this) {
            if (ClientConnectStatus.UNINITIALIZED == connectStatus.get() ||
                ClientConnectStatus.RELEASING == connectStatus.get() ||
                ClientConnectStatus.DISCONNECTING == connectStatus.get()
            ) {
                LogContext.log.w(
                    tag,
                    "Releasing now or already released or disconnecting or not initialized"
                )
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            connectStatus.set(ClientConnectStatus.RELEASING)
        }
        released = true
        disconnectManually = true
        LogContext.log.w(tag, "Releasing retry handler...")
        resumeConnectContinuation(connectStatus.get())
        stopRetryHandler()
        retryScope.cancel()
        //        retryThread.quitSafely()

        LogContext.log.w(tag, "Releasing default socket handler first...")
        defaultInboundHandler?.release()
        defaultInboundHandler = null
        channelInitializer = null

        if (::channel.isInitialized) {
            channel.run {
                LogContext.log.w(tag, "Closing channel...")
                runCatching {
                    pipeline().removeAll { true }
                    // closeFuture().syncUninterruptibly()
                    // syncUninterruptibly() will get stuck here.
                    // Why???
                    //         closeFuture()
                    close().syncUninterruptibly()
                }.onFailure { LogContext.log.e(tag, "Close channel error.", it) }
            }
        }

        runCatching {
            LogContext.log.w(tag, "Releasing socket...")
            workerGroup.shutdownGracefully() // syncUninterruptibly() will not get stuck here.
                .addListener { f ->
                    if (f.isSuccess) {
                        connectStatus.set(ClientConnectStatus.UNINITIALIZED)
                        LogContext.log.w(tag, "=====> Socket released <=====")
                        cont.resume(true)
                    } else {
                        LogContext.log.w(tag, "Release socket failed!!!")
                        cont.resume(false)
                    }
                }
        }.onFailure {
            LogContext.log.e(tag, "Release socket error.", it)
            cont.resume(false)
        }
    }
```

改后 `release()`（L834–872，KDoc 重写 + 守卫）：

```kotlin
    /**
     * Releases the netty client: closes the channel and shuts the event loop group down,
     * suspending instead of blocking the caller's thread.
     *
     * Once you call [release], you can not reconnect it again by calling [connect] simply,
     * you must recreate netty client again.
     * If you want to reconnect it again, do not call this method, just call [disconnectManually].
     *
     * If current connect state is [ClientConnectStatus.FAILED], this method will also be run and
     * any exception will be ignored.
     *
     * The netty part is bounded by [NETTY_RELEASE_TIMEOUT_IN_MILLS] and returns false if that
     * bound is hit. It suspends rather than blocking, so it must be called from a coroutine -
     * it already had to be, but it used to park a dispatcher thread while it waited.
     *
     * Past the state guard this runs under [NonCancellable], for the same reason as
     * [BaseNettyServer.stopServer]: cancelling the caller cannot shorten a release, only strand
     * it on RELEASING with the event loop group alive for good. The timeout still applies.
     */
    suspend fun release(): Boolean {
        LogContext.log.w(
            tag,
            "===== release() current state=${connectStatus.get().name} ====="
        )
        synchronized(this) {
            if (ClientConnectStatus.UNINITIALIZED == connectStatus.get() ||
                ClientConnectStatus.RELEASING == connectStatus.get() ||
                ClientConnectStatus.DISCONNECTING == connectStatus.get()
            ) {
                LogContext.log.w(
                    tag,
                    "Releasing now or already released or disconnecting or not initialized"
                )
                return false
            }
            connectStatus.set(ClientConnectStatus.RELEASING)
        }
        return withContext(NonCancellable) { releaseNow() }
    }
```

改后 `releaseNow()`（新增，L874–907）：

```kotlin
    private suspend fun releaseNow(): Boolean {
        released = true
        disconnectManually = true
        LogContext.log.w(tag, "Releasing retry handler...")
        resumeConnectContinuation(connectStatus.get())
        stopRetryHandler()
        retryScope.cancel()
        //        retryThread.quitSafely()

        // Guarded like every other step: this hands control to subclass code, and letting it
        // throw here would skip the channel and the event loop group entirely.
        LogContext.log.w(tag, "Releasing default socket handler first...")
        try {
            defaultInboundHandler?.release()
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release inbound handler error.", e)
        }
        defaultInboundHandler = null
        channelInitializer = null

        // Bounded here, at the resource boundary, for the same reason BaseNettyServer.stopServer()
        // is: callers chain this into a teardown sequence that deliberately carries no timeout of
        // its own, so a release that cannot finish must not hold that sequence forever.
        val nettyReleased = withTimeoutOrNull(NETTY_RELEASE_TIMEOUT_IN_MILLS) { releaseNetty() }
        if (nettyReleased == null) {
            LogContext.log.e(
                tag,
                "Netty release did not finish within ${NETTY_RELEASE_TIMEOUT_IN_MILLS}ms. " +
                    "Check whether closing the channel, not the event loop, used up the budget."
            )
            return false
        }
        return nettyReleased
    }
```

改后 `releaseNetty()`（新增，L909–934）：

```kotlin
    private suspend fun releaseNetty(): Boolean {
        try {
            closeChannelAndAwait()
        } finally {
            // Reached on the timeout too, as the cancellation it carries passes through here. The
            // request does not suspend, so a close that used up the whole budget still leaves the
            // group shutting down instead of alive for good - same shape as
            // BaseNettyServer.shutdownNetty().
            //
            // Quiet period 0: it exists to let in-flight work settle before the loop dies, which
            // buys nothing for a client whose channel is already closed.
            LogContext.log.w(tag, "Releasing socket...")
            workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        }
        return try {
            workerGroup.terminationFuture().awaitCompletion()
            connectStatus.set(ClientConnectStatus.UNINITIALIZED)
            LogContext.log.w(tag, "=====> Socket released <=====")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release socket error.", e)
            false
        }
    }
```

改后 `closeChannelAndAwait()`（新增，L936–950；承接旧的关 channel 逻辑）：

```kotlin
    private suspend fun closeChannelAndAwait() {
        if (!::channel.isInitialized) return
        LogContext.log.w(tag, "Closing channel...")
        // close(), not closeFuture(): closeFuture() completes only once the channel is actually
        // closed, so awaiting it before anyone has asked for a close waits forever. That is what
        // the old "Why??? syncUninterruptibly() will get stuck here" note was looking at. The
        // future close() returns is the one that tracks this close.
        try {
            channel.close().awaitCompletion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogContext.log.e(tag, "Close channel error.", e)
        }
    }
```

#### C.3.5 审查要点

1. **`nettyReleased` 命名**。`BaseNettyClient` 有字段 `private var released = false`（L239），`releaseNow()` 第一行就 `released = true`。`withTimeoutOrNull` 的结果若也叫 `released` 会遮蔽该字段；现命名为 `nettyReleased`。
2. **超时 → 返回 `false`**（R3）。`withTimeoutOrNull(NETTY_RELEASE_TIMEOUT_IN_MILLS)` 返回 `null` 时记错误日志并 `return false`；此时 `connectStatus` 停在 `RELEASING`（`UNINITIALIZED` 只在 group 终止成功后设置），之后 `release()` 再调会被守卫拒绝、返回 `false`。
2a. **已关闭的问题：客户端现与服务端 R16 对称**（doc §11 R20）。上一版若 `channel.close()` 吃光 3 s 预算，`TimeoutCancellationException` 从 `awaitCompletion()` 上抛，`workerGroup.shutdownGracefully()` 根本不会被调用。现在关 channel 抽成 `closeChannelAndAwait()` 放进 `try`，`finally` 里无条件、不挂起地发出 `shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)`，之后再 `terminationFuture().awaitCompletion()`——与 `BaseNettyServer.shutdownNetty()` 同一形状。超时穿过 `finally` 时 group 已在关闭中，不再永久泄漏。
3. **`CancellationException` 在 `closeChannelAndAwait()` / `releaseNetty()` 内重抛**。两处 `catch (e: CancellationException) { throw e }` 排在 `catch (e: Exception)` 之前，保证 `withTimeoutOrNull` 的 `TimeoutCancellationException` 不被降级为"Close channel error." / "Release socket error." 日志 + `false`，而是让 `withTimeoutOrNull` 看到取消并返回 `null`，走超时分支。配合 `failureOrNull()` 把 Netty 自身的 cancel 映射为 `IllegalStateException`，这两个 catch 只会命中协程取消。
4. **`removeAll { true }` 删除**（R14）。`ChannelPipeline` 实现 `Iterable<Map.Entry<String, ChannelHandler>>`，Kotlin 把 `pipeline().removeAll { true }` 解析为标准库 `MutableIterable<T>.removeAll(predicate)`，它对 `iterator()` 逐项 `remove()`；而 `DefaultChannelPipeline.iterator()` 返回 `toMap().entrySet().iterator()`，`toMap()` 新建 `LinkedHashMap` 快照（维护者已用 javap 对 netty-transport 4.1.93 / 4.1.110 核实）。删的是快照，pipeline 一个 handler 都没少，纯空操作；两端连同其 `runCatching` 一并删除。
5. **`basenetty/src/main` 内不再有 `syncUninterruptibly()` 调用**。grep 结果（见 C.z）：仅剩 3 处命中，全部在注释 / KDoc 里（`NettyFutureExt.kt:13-14` 的说明，`BaseNettyClient.kt:941` 引用旧的 "Why???" 注释），无代码调用。
6. **唯一保留的 `.sync()` 在 `connect()`**（L440 `f.sync()`）。doc §6.1 的理由：`connect()` 是一整套状态机（早退分支、`connectContinuation` / `connectResume` 重试挂钩、WebSocket 握手二次挂起、`invokeOnCancellation` 中的 `cancelConnectFuture()`），改成全异步等于重写，风险与本次"去阻塞化"收益不成比例；且已有缓解——L418–421 `cont.invokeOnCancellation { clearConnectResume(cont); cancelConnectFuture() }` 在取消时取消 `ChannelFuture`，使阻塞的 `sync()` 尽快抛出。doc 同时注明该缓解**未实测**，已列入 audio 真机验证项。`sync()` 本身对已完成 future 有 `isDone()` 检查，不存在 C.1 第 3 条的挂起问题。
7. **`disconnectNow()` 的 `defaultInboundHandler?.release()` 现有 try/catch**（R4 延伸）；改前 `disconnectManually()` 中该调用裸露，子类 `release()` 抛异常会让状态停在 `DISCONNECTING`，而 `release()` 的守卫恰好拒绝 `DISCONNECTING`，event loop 永不关闭。
8. **观察**：`release()` 的守卫 `synchronized(this)` 与 `disconnectManually()` 共用 `this` 监视器（改前即如此）；两者的关闭体在监视器之外、`NonCancellable` 之内，互不阻塞，但 `release()` 对 `DISCONNECTING` 的拒绝意味着 `disconnectManually()` 进行中调用 `release()` 会直接返回 `false`，调用方需自行重试或等待。这是既有语义，本次未改。

### C.4 BaseNettyLifecycleTest.kt

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.5）。

`basenetty/src/test/kotlin/com/leovp/basenetty/BaseNettyLifecycleTest.kt`。类级 `@Timeout(value = 60, unit = TimeUnit.SECONDS)`（L48，既有）。`TestServer`（L577–585）默认以 `port = 0` 构造，即真实 `NioEventLoopGroup` + 随机端口的真实 NIO bind，不是 `EmbeddedChannel`；第六轮（§J.5）给它加了 `port: Int = 0` 参数，供「端口已被占用、启动失败」的用例传入一个已在 LISTEN 的端口：

```kotlin
    private class TestServer(
        listener: ServerConnectListener<BaseNettyServer> = RecordingServerListener(),
        port: Int = 0,
    ) : BaseNettyServer(
        port = port,
        connectionListener = listener
    ) {
        override fun getTagName(): String = "BaseNettyServerLifecycleTest"
    }
```

#### C.4.1 imports 变更

`CoroutineStart` / `launch` 两行为第六轮 J.5 新增（供 `launch(start = CoroutineStart.UNDISPATCHED)` 使用）；`java.net.ServerSocket` HEAD 已有。

```diff
 import com.leovp.basenetty.framework.base.ClientConnectStatus
+import com.leovp.basenetty.framework.base.ServerConnectStatus
+import com.leovp.basenetty.framework.base.awaitCompletion
 ...
 import io.netty.channel.embedded.EmbeddedChannel
+import io.netty.channel.nio.NioEventLoopGroup
 import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
+import io.netty.util.concurrent.DefaultPromise
+import io.netty.util.concurrent.GlobalEventExecutor
 import kotlinx.coroutines.CoroutineDispatcher
+import kotlinx.coroutines.CoroutineStart
 import kotlinx.coroutines.ExperimentalCoroutinesApi
 import kotlinx.coroutines.Job
+import kotlinx.coroutines.launch
 import kotlinx.coroutines.runBlocking
 ...
 import org.junit.jupiter.api.Assertions.assertEquals
+import org.junit.jupiter.api.Assertions.assertFalse
```

#### C.4.2 六个新测试与一处既有用例的新增断言（L451–575、L68–71）

```kotlin
    @Test
    fun `stop before start releases the event loops and refuses a later start`(): Unit =
        runBlocking {
            val listener = RecordingServerListener()
            val server = TestServer(listener)
            server.initHandler(TestServerHandler(server))

            assertFalse(server.stopServer())
            assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
            assertEquals(0, listener.stoppedCount.get())

            // Refused by state before any bind, so it can neither bring up a server nothing will
            // stop nor hang on a listener a dead event loop will never notify. The state stays
            // STOPPED: a later stop must not run the teardown again.
            server.startServer()

            assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
            assertFalse(server.stopServer())
            assertEquals(0, listener.stoppedCount.get())
            assertEquals(
                listOf(ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED),
                listener.startFailedCodes.toList()
            )
        }

    @Test
    fun `awaitCompletion does not hang on a failed future whose executor is terminated`(): Unit =
        runBlocking {
            val loop = NioEventLoopGroup(1)
            loop.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitCompletion()
            // A done promise on a dead executor: addListener() would hand the notification to
            // that executor, which only logs the rejection, so without the isDone fast path this
            // await never returns.
            val promise = DefaultPromise<Unit>(loop.next())
            promise.setFailure(IllegalStateException("boom"))

            val thrown = runCatching { promise.awaitCompletion() }.exceptionOrNull()

            assertEquals("boom", thrown?.message)
        }

    @Test
    fun `awaitCompletion reports a cancelled netty future as a plain failure`(): Unit =
        runBlocking {
            val promise = DefaultPromise<Unit>(GlobalEventExecutor.INSTANCE)
            promise.cancel(false)

            val thrown = runCatching { promise.awaitCompletion() }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException)
            assertEquals("Netty future was cancelled", thrown?.message)
        }

    @Test
    fun `second stop on a stopped server is a no-op and onStopped fires once`(): Unit =
        runBlocking {
            val listener = RecordingServerListener()
            val server = TestServer(listener)
            server.initHandler(TestServerHandler(server))
            server.startServer()
            assertEquals(ServerConnectStatus.STARTED, server.connectState.get())

            assertTrue(server.stopServer())
            assertFalse(server.stopServer())

            assertEquals(1, listener.stoppedCount.get())
            assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
        }

    @Test
    fun `cancelling the stopServer caller still lands on STOPPED with one onStopped`(): Unit =
        runBlocking {
            val listener = RecordingServerListener()
            val server = TestServer(listener)
            server.initHandler(TestServerHandler(server))
            server.startServer()
            assertEquals(ServerConnectStatus.STARTED, server.connectState.get())

            // UNDISPATCHED runs the body up to its first suspension, which is inside the
            // NonCancellable block (awaiting the channel close or the loop termination). The
            // cancel that follows therefore hits a teardown already past the state guard.
            val stop = launch(start = CoroutineStart.UNDISPATCHED) { server.stopServer() }
            stop.cancel()
            stop.join()

            assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
            assertEquals(1, listener.stoppedCount.get())
            assertFalse(server.stopServer())
            assertEquals(1, listener.stoppedCount.get())
        }

    @Test
    fun `stop after a failed start releases the event loops and refuses a later start`(): Unit =
        runBlocking {
            ServerSocket(0).use { occupied ->
                val listener = RecordingServerListener()
                val server = TestServer(listener, port = occupied.localPort)
                server.initHandler(TestServerHandler(server))

                server.startServer()

                assertEquals(ServerConnectStatus.FAILED, server.connectState.get())
                assertEquals(
                    listOf(ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR),
                    listener.startFailedCodes.toList()
                )

                // serverChannel was never assigned, so this takes the unstarted-release path:
                // both groups are shut down, no onStopped(), and the state refuses a restart.
                assertFalse(server.stopServer())
                assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
                assertEquals(0, listener.stoppedCount.get())

                server.startServer()

                assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
                assertEquals(
                    listOf(
                        ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR,
                        ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED
                    ),
                    listener.startFailedCodes.toList()
                )
            }
        }
```

逐条：

| 测试 | 钉住的行为 | 覆盖项 |
|---|---|---|
| `stop before start releases the event loops and refuses a later start` | 从未 start 的实例 `stopServer()` 返回 `false`、状态 `STOPPED`、`onStopped()` 不触发；随后 `startServer()` 回调 `onStartFailed(CONNECTION_ERROR_ALREADY_RELEASED)` 而不是挂起，且状态**仍为 `STOPPED`**；再调 `stopServer()` 返回 `false`、`stoppedCount` 仍为 0 | R15（`releaseUnstartedServer()` + 按状态拒绝 + 状态保持 `STOPPED`），兼覆盖 C.2.7 第 8 条已关闭的二次 `onStopped()` 问题（`serverChannel` 未初始化的变体） |
| `awaitCompletion does not hang on a failed future whose executor is terminated` | 终止的 `NioEventLoopGroup` 上的已失败 `DefaultPromise`，`awaitCompletion()` 同步抛出 cause（"boom"），不依赖 listener 投递 | R7 / R18（`isDone` 快速路径）。若去掉快速路径，该测试会撞上类级 60 s `@Timeout` |
| `awaitCompletion reports a cancelled netty future as a plain failure` | 被 `cancel(false)` 的 promise 以 `IllegalStateException("Netty future was cancelled")` 报告，而不是 `CancellationException` | R8 / R18 |
| `second stop on a stopped server is a no-op and onStopped fires once` | 真实 bind（`port = 0`）后第一次 `stopServer()` 返回 `true`，第二次返回 `false`，`stoppedCount == 1`，状态 `STOPPED` | R9（`STOPPED` 守卫） |
| `cancelling the stopServer caller still lands on STOPPED with one onStopped`（第六轮 J.5(a)） | 取消 `stopServer()` 的调用方后状态仍为 `STOPPED`、`onStopped()` 恰好一次；再调 `stopServer()` 返回 `false` 且计数不变 | R19（守卫之后 `NonCancellable`）+ I.2（`finally` 必达状态迁移与回调） |
| `stop after a failed start releases the event loops and refuses a later start`（第六轮 J.5(b)） | 端口被占导致 bind 失败 → `FAILED` + `onStartFailed(SERVER_START_ERROR)`；随后 `stopServer()` 返回 `false`、无 `onStopped()`、状态 `STOPPED`；再 `startServer()` 追加 `ALREADY_RELEASED` | R15 的「含启动失败」分支（CHANGELOG 明文承诺） |

后两个用例的确定性：

- 取消用例：`launch(start = CoroutineStart.UNDISPATCHED)` 让 body 在 `runBlocking` 的调用线程上同步跑到**第一个真实挂起点**。`lifecycleMutex.withLock` 无竞争不挂起，`withContext(NonCancellable)` 与 `withTimeoutOrNull` 同 dispatcher、均不派发，所以第一个挂起点落在 `serverChannel.close().awaitOrLog()`（或 close 已瞬时完成时的 `terminationFuture().awaitOrLog()`）——两者都在 `NonCancellable` 块内。也就是说 `launch()` 返回时状态守卫已经通过、`connectState` 已置 `UNINITIALIZED`，随后的 `cancel()` 只能落在一个 `NonCancellable` 的关闭体上；`finally` 仍迁移到 `STOPPED` 并恰好回调一次 `onStopped()`，再调 `stopServer()` 在第一道守卫返回 `false`。
- 启动失败用例：`ServerSocket(0)` 先占住一个端口并处于 LISTEN；`ServerBootstrap` 没有设置 `SO_REUSEPORT`（`SO_REUSEADDR` 不放行第二个 LISTEN），Linux 拒绝第二次 bind，`bootstrap.bind(port)` 的 future 以 `BindException` 失败 → `awaitCompletion()` 抛出 → `catch (e: Exception)` → `FAILED` + `onStartFailed(CONNECTION_ERROR_SERVER_START_ERROR)`。此时 `serverChannel` 从未赋值，所以 `stopServer()` 走 `!::serverChannel.isInitialized` 分支即 `releaseUnstartedServer()`：返回 `false`、`stoppedCount == 0`、状态 `STOPPED`；再 `startServer()` 被 `STOPPED` 守卫拒绝，`startFailedCodes` 追加 `CONNECTION_ERROR_ALREADY_RELEASED`。

既有用例 `connect failure updates stored state to failed` 的末尾（L68–71，第六轮 J.5(c)）：`client.release()` 改为断言返回值与终态——

```kotlin
        // The rewritten release path end to end: no channel to close, so it goes straight to
        // shutting down the worker group and must land on UNINITIALIZED within its own budget.
        assertTrue(client.release())
        assertEquals(ClientConnectStatus.UNINITIALIZED, client.connectStatus.get())
```

连接失败后 `channel` 未初始化，`closeChannelAndAwait()` 直接返回，路径只剩 `shutdownGracefully` + `terminationFuture().awaitCompletion()`，必须在 3 秒预算内落到 `UNINITIALIZED`。

#### C.4.3 `RecordingServerListener` 的 `stoppedCount`（L608–616）

```diff
     private class RecordingServerListener : ServerConnectListener<BaseNettyServer> {
         val startFailedCodes = CopyOnWriteArrayList<Int>()
         val disconnectedCount = AtomicInteger(0)
+        val stoppedCount = AtomicInteger(0)

         override fun onStarted(netty: BaseNettyServer) = Unit
-        override fun onStopped() = Unit
+        override fun onStopped() {
+            stoppedCount.incrementAndGet()
+        }
```

说明：改前 `BaseNettyLifecycleTest` 完全不触碰 `startServer()` / `stopServer()`（doc §10.1）；这六个用例是服务端生命周期的首批直接测试。start → stop → start → stop（`serverChannel` 已初始化）的变体没有直接用例，见 C.2.7 第 8 条。

### C.5 socket demo 调用方

> 2026-10-16：本节代码块已同步到第六轮修正后的工作树（差异说明见 §J.2）。

#### C.5.1 `WebSocketServerActivity.kt`（仅注释）

```diff
@@ -165,8 +165,8 @@ class WebSocketServerActivity :
         webSocketServer = server
         webSocketServerHandler = handler
         binding.btnStartServer.isEnabled = false
-        // startServer() blocks at closeFuture().sync() for the server's whole lifetime,
-        // so it must run off the main thread.
+        // startServer() is suspend: it binds the port and returns. It no longer parks here for the
+        // server's whole lifetime, but it still has to be called from a coroutine.
         cs.launch { server.startServer() }
     }
```

调用形式 `cs.launch { server.startServer() }` 不变（`cs = CoroutineScope(Dispatchers.IO)`，L41），只订正了过时的理由。

#### C.5.2 `WebSocketClientActivity.kt`

```diff
@@ -107,8 +107,24 @@ class WebSocketClientActivity :
     }
 
     override fun onDestroy() {
-        cs.launch { webSocketClient?.release() }
-        cs.cancel()
+        // The scope is cancelled from inside its last coroutine, once release() has returned. A
+        // cancel() issued right here instead could land before this coroutine is first
+        // dispatched, which skips the whole body and release() never runs. Cancelling afterwards
+        // still cancels a connect() that is in flight - its invokeOnCancellation cleans up - and
+        // release() itself is bounded by its own timeout, so the cancel cannot cut it short.
+        //
+        // If onConnectClick's coroutine has been dispatched but has not assigned webSocketClient
+        // yet, release() no-ops on the null, and the connect it then runs builds a client nothing
+        // will release. Narrow, and no cancel timing closes it: a cancel that lands before the
+        // first dispatch skips that coroutine entirely, one that lands after it is already past
+        // the assignment.
+        cs.launch {
+            try {
+                webSocketClient?.release()
+            } finally {
+                cs.cancel()
+            }
+        }
         super.onDestroy()
     }
 
```

#### C.5.3 `EventBusBridgeClientActivity.kt`

```diff
@@ -86,8 +86,17 @@ class EventBusBridgeClientActivity :
     }
 
     override fun onDestroy() {
-        ioScope.launch { webSocket?.release() }
-        ioScope.cancel()
+        // The scope is cancelled from inside its last coroutine, once release() has returned. A
+        // cancel() issued right here instead could land before this coroutine is first dispatched,
+        // which skips the whole body and release() never runs. Cancelling afterwards still cancels
+        // a connect() that is in flight, and release() is bounded by its own timeout.
+        ioScope.launch {
+            try {
+                webSocket?.release()
+            } finally {
+                ioScope.cancel()
+            }
+        }
         super.onDestroy()
     }
 
```

说明（两处相同）：`launch` 默认 `CoroutineStart.DEFAULT`，在 `Dispatchers.IO` 上是异步派发；HEAD 里紧随其后的 `scope.cancel()` 几乎必然在该协程首次被调度前落地，而一个尚未开始执行就被取消的协程**整个 body 都不会运行**——`release()` 根本不会被调用，event loop group 泄漏。`NonCancellable` 只保护"守卫之后"的关闭体，救不了从未进入 `release()` 的情形。现在的写法（第六轮 J.2）把 `cancel()` 挪进同一协程的 `finally`：`release()`（自身有 3 s 资源层超时）返回之后再取消 scope，此时落点必然在 `onConnectClick` 协程派发之后，所以它还能顺带取消一个仍在进行中的 `connect()`（其 `invokeOnCancellation` 会清理）；从 scope 自己的最后一个协程里取消它是合法的，Job 随之完成，不再有"scope 永不取消"这个弱化的生命周期约定。`kotlinx.coroutines.cancel` 的 import 因此保留，diff 不再触及 import 段。残余的窄窗口只剩一种：`onConnectClick` 的协程已被派发、但尚未把 client 赋给字段，此时 `release()` 对 null 空转，随后建出的 client 无人释放；任何 `cancel()` 时机都关不上它——落在首次派发前会整个跳过该协程（不会建 client），落在派发后则已越过赋值（见 `WebSocketClientActivity` 注释第二段、§J.2 与 §J.13）。

### C.z 本区自查结果

以下命令均在工作树上执行（未运行 Gradle）。

**1. `grep -rn 'syncUninterruptibly\|removeAll' basenetty/src/main`** — 预期：无代码调用。

```
basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt:941:        // the old "Why??? syncUninterruptibly() will get stuck here" note was looking at. The
basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/NettyFutureExt.kt:13: * Netty's own `sync()` / `syncUninterruptibly()` park the caller until the future completes. On a
basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/NettyFutureExt.kt:14: * coroutine dispatcher that costs a pool thread for the whole wait, and `syncUninterruptibly()`
```

结论：3 处命中全部为注释 / KDoc 文本；`removeAll` 零命中；代码中无 `syncUninterruptibly()` 调用。符合预期。

**2. `grep -rn '\.sync()' basenetty/src/main`** — 预期：仅 `connect()` 一处代码调用。

```
basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt:423:            // bootstrap.connect(host, port).sync()
basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt:440:            f.sync()
basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt:163:     * The previous implementation parked the caller in `serverChannel.closeFuture().sync()` until
basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt:213:                // keeps the log line the old closeFuture().sync() produced when it returned.
```

结论：唯一代码调用是 `BaseNettyClient.kt:440 f.sync()`（在 `connect()` 内，刻意保留，见 C.3.5 第 6 条）；其余 3 处为注释。符合预期。

**3. demo 中 `BaseNettyServer.startServer(` / `stopServer(` 调用点及其协程上下文** — 预期：4 start / 7 stop，全部在协程内。

排除项：`adb/base/LocalServerSocket.kt`、`adb/base/LocalServer.kt`、`adb/AdbCommunication.kt` 的同名方法属于 `LocalServerSocket`，与 netty 无关；`AudioActivity.kt:379/416` 调用的是 `AudioReceiver` 自己的 suspend 包装方法（`launchCleanup` → `ioScope.launch`；`ioScope.launch`），不直接调 `BaseNettyServer`；`ScreenShareMasterActivity.kt:151/205/284/497` 调用的是 Activity 自己的私有包装 `startServer()` / `stopServer()`（L501–514），内部才 `cs.launch`。

| # | 调用点 | 方法 | 协程上下文 |
|---|---|---|---|
| S1 | `sharescreen/master/ScreenShareMasterActivity.kt:507` | `webSocketServer.startServer()` | `cs.launch { ... }`（`cs = CoroutineScope(Dispatchers.IO)`，L90） |
| S2 | `socket/websocket/WebSocketServerActivity.kt:170` | `server.startServer()` | `cs.launch { ... }`（L41） |
| S3 | `socket/SocketServerActivity.kt:136` | `socketServer.startServer()` | `cs.launch { repeat(1) { ... } }`（L44） |
| S4 | `audio/receiver/AudioReceiver.kt:268` | `server.startServer()` | 在 `suspend fun startServer(ctx)` 的 `try` 内，由 `AudioActivity.kt:416` `ioScope.launch` 调用 |
| T1 | `sharescreen/master/ScreenShareMasterActivity.kt:513` | `webSocketServer.stopServer()` | `cs.launch { if (::webSocketServer.isInitialized) ... }` |
| T2 | `socket/websocket/WebSocketServerActivity.kt:150` | `server.stopServer()` | `cs.launch { ... }`（Stop 按钮） |
| T3 | `socket/websocket/WebSocketServerActivity.kt:174` | `server.stopServer()` | `cs.launch { ... }`（`onDestroy`） |
| T4 | `socket/SocketServerActivity.kt:125` | `socketServer.stopServer()` | `cs.launch { ... }`（Stop 按钮） |
| T5 | `socket/SocketServerActivity.kt:143` | `socketServer.stopServer()` | `cs.launch { ... }`（`onDestroy`） |
| T6 | `audio/receiver/AudioReceiver.kt:291` | `server.stopServer()` | 在 `suspend fun startServer(ctx)` 的 `finally` 内（`stopping` 重检查补偿路径），经 `cleanupScope.launch { teardownGate.awaitCompletion(); teardownSteps(...) }` 执行（第六轮 J.8） |
| T7 | `audio/receiver/AudioReceiver.kt:343` | `receiverServer?.stopServer()` | 在 `suspend fun stopServer()` 的 `teardownGate.run { teardownSteps(...) }` 内；调用方为 `cleanupScope.launch`（L155/161）与 `AudioActivity.launchCleanup` → `ioScope.launch`（L379） |

结论：4 start / 7 stop，全部在协程内。符合预期。

**4. 改动文件中 >100 字符的非 import 行** — 预期：仅两处既有 ASCII 图。

```
basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt:90: 205 chars:  *        └───────────────────────────────────────────────────────────────┘
demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/socket/websocket/WebSocketClientActivity.kt:78: 213 chars:         //        └───────────────────────────────────────────────────────────────┘
```

两处均为既有行：HEAD 中分别位于 `BaseNettyClient.kt:85`（因新增 5 个 import 下移到 90）与 `WebSocketClientActivity.kt:78`（第六轮恢复了 `kotlinx.coroutines.cancel` 的 import 后，import 段与 HEAD 相同，行号未变），内容未改。`NettyFutureExt.kt`（63 行）、`BaseNettyServer.kt`（624 行）、`BaseNettyLifecycleTest.kt`（692 行）、`WebSocketServerActivity.kt`（209 行）、`EventBusBridgeClientActivity.kt`（209 行）无命中。符合预期。（长度按 `awk length()` 的字符计数，框线字符按单字符计；以上为第六轮代码更新后重跑的结果。）

**4a. 括号配对（粗略计数，剥离块注释 / 行注释 / 字符串后统计）**：`{` / `}` 在全部 7 个文件中逐一相等——`NettyFutureExt.kt` 7/7、`BaseNettyServer.kt` 71/71、`BaseNettyClient.kt` 163/163、`BaseNettyLifecycleTest.kt` 74/74、`WebSocketServerActivity.kt` 30/30、`WebSocketClientActivity.kt` 31/31、`EventBusBridgeClientActivity.kt` 25/25。`(` / `)` 同样全部相等——`NettyFutureExt.kt` 13/13、`BaseNettyServer.kt` 167/167、`BaseNettyClient.kt` 423/423、`BaseNettyLifecycleTest.kt` 410/410、`WebSocketServerActivity.kt` 64/64、`WebSocketClientActivity.kt` 68/68、`EventBusBridgeClientActivity.kt` 73/73。（第六轮后重算；`BaseNettyClient.kt` 等未触及文件的计数与上一轮一致，可作为计数方法未变的旁证。）（注意剥离顺序必须先字符串后行注释：测试文件里 `URI("ws://127.0.0.1:…")` 的 `//` 若先被当成注释会吃掉右括号，造成 363/360 的假性失配。）这只是文本级配对，不能替代编译。

**5. 未核实项汇总**
- Gradle / detekt / ktlint / 单元测试均未运行（任务约束）；C.4 六个用例是否通过未核实。
- C.2.7 第 8 条与 C.3.5 第 2a 条两处"已关闭"均为对照工作树的静态核对，未运行验证。
- C.1 第 3 条引用的 Netty 内部行为（`DefaultPromise.notifyListeners` → `safeExecute` 仅记日志；`AbstractBootstrap.initAndRegister` 对终止 group 返回已失败 promise）基于对 Netty 4.1 源码的记忆，未对本机 4.1.108 字节码逐行核对；测试 `awaitCompletion does not hang on a failed future whose executor is terminated` 覆盖的是前者。


## E. 有意偏离与已接受的残余风险

| # | 事项 | 处理 |
|---|---|---|
| E.1 | 计划模板里 `containsJob` 的 KDoc 写「is this job or any job below it」，实现是 `children.any { ... }`，不含自身 | 改 KDoc 不改行为：实现必须与 `StreamPlayerStopper` 私有版一致 |
| E.2 | 计划模板把两个 `Channel.close()` 写在同一个 `teardownSteps` lambda 里 | 拆为两个 step，遵守计划自己的「每个 step 只做一件事」（第十次复审 H1）；`close()` 不抛，行为零差别 |
| E.3 | 计划模板测试 `assertSame(expected, first)` | 改为断言类型 + message，保留 `assertSame(first, second)`；原因见 B.1 |
| E.4 | `ADPCMActivity` 三连击：第三击若落在第二个 Job 首次派发之前，第二个 Job 跳过 join，第三个 player 可能与第一个的 release 短暂并存 | **接受**：亚毫秒窗口，两个独立 `AudioTrack`，各自由自己的 Job 释放，无崩溃无泄漏 |
| E.5 | `AudioSender.stop()` 把关闭 Channel 排在 `cancelAndJoin()` 之前，worker 可能对已断开的客户端排空至多 64 帧 | **接受**：`sendAudioToServer()` 内部 `runCatching`，只多几条日志 |
| E.6 | `disconnectNow()` 的 `complete()` 中 `cont.isActive` 检查与 `cont.resume` 非原子，超时恰好插入可能双通知 | **接受**（既有，几条指令的窗口） |
| E.7 | stream player 的 init 失败回滚协程不被 `stopPlayingAndJoin()` 等待 | **设计如此**（spec §3.4/§4.6），已写进 `AudioPlayer.releaseAndJoin()` KDoc |
| E.8 | `AudioSender.senderClient` 没有像其余四处那样做"赋值后 re-check `stopping`" | **无需**：赋值发生在 `start()` 起在 `ioScope` 上的协程体内，`stop()` 第 6 步 `ownerJob.cancelAndJoin()` 要么在该协程首次派发前取消它（client 根本未创建），要么 join 到它结束（赋值对第 8 步 `senderClient?.release()` 可见，字段也是 `@Volatile`）。**第五轮补全（原文只论证了第 8 步）**：同一窗口里第 1 步 `senderClient?.disconnectManually()` 会被整个跳过，而且即便 client 可见、状态还没到 `CONNECTING` 时 `disconnectManually()` 也在 `UNINITIALIZED` 守卫处直接返回、什么都不设。所以终止重试链靠的不是第 1 步，而是 `stopping`（挡回 `onConnected()`）加第 8 步 `release()` → `stopRetryHandler()`。结论不变，推理已补齐（§I.9） |
| E.9 | 计划附录第 18 项的字面自查「`grep -A2 'audioPlayer?.stop()'` 下一行不应出现 `cancelAndJoin`」在现代码上**不**字面成立 | 下一行是独立的 `{ ownerJob?.cancelAndJoin() }` lambda，正是该自查要保证的形态；自查文字写得过严，代码正确 |

## F. 验证状态

### F.1 未执行（需本地）

```bash
./gradlew --continue --rerun-tasks \
  :audio:compileDebugKotlin :audio:testDebugUnitTest \
  :basenetty:compileDebugKotlin :basenetty:testDebugUnitTest \
  :demo:compileDevDebugKotlin :demo:testDevDebugUnitTest \
  detekt ktlintCheck
```

预期：编译 0 条 deprecation warning（原 8 条）；detekt / ktlint 零问题；新增与迁移的单测全部通过。
重点盯：`SuspendTeardownGateTest`（coroutines DEBUG 模式下的异常副本）、`BaseNettyLifecycleTest` 的
4 个新用例（真实 NIO loop，`port = 0`）、`TeardownSupportTest` 的 `containsJob` 用例与第五轮新增的
`two steps reporting one instance do not abort the run`。

第五轮改动引入的编译面：`BaseNettyServer` 新增常量 `NETTY_BIND_TIMEOUT_IN_MILLS` 与一处
`withTimeoutOrNull`（import 已有）；`AudioActivity` 删掉 4 个字段、新增 `recordCallbackFor()`
（`BufferedOutputStream` / `FileOutputStream` / `AudioType` 的 import 仍被 `record()` 使用，未失效）。

第六轮新增 3 个 JVM 用例（§J.5）同样未执行：`cancelling the stopServer caller still lands on STOPPED
with one onStopped`、`stop after a failed start releases the event loops and refuses a later start`，以及
`connect failure updates stored state to failed` 末尾新增的 `release()` 返回值与终态断言。

### F.2 真机（全部未执行）

- audio 计划 Task 5 的 8 步：AAC/OPUS 启停循环、PCM 路径（`ADPCMActivity` + `AudioActivity` PCM 播放中
  退出，盯 `AudioTrack.write failed` / SIGABRT）、录放组合退出、init 失败回滚、录音启动失败回滚、
  netty 回调触发的关闭（含「server 确实停止」/ 10020 端口释放、「重试链确实终止」、连接进行中退出的
  teardown 耗时、并发交错、worker 退出、teardown 期间重连 / 迟到接入、**点完发送立刻退出**、
  **receiver 启动中退出**、ADPCM 所有权）、结果记入 spec §21。
第五轮新增的真机项：

- **receiver 启动中退出**（对应 §I.3）：点开 receiver 后立刻退出 Activity。期望端口 10020 释放、
  AudioTrack 释放，两条 release 日志都出现；补偿现在跑在 `cleanupScope` 上，Activity 销毁不应再
  影响它。
- **录音启动失败后再次录音**（对应 §I.4）：先启动 sender/receiver 占住麦克风，再点 PCM 录音
  （应失败），然后立刻再点一次录音。期望第二次录出的文件**非空**；改动前第一次的异步回滚会把
  第二次刚打开的流关掉，且界面无任何提示。

- netty 文档 §8.3 的 14 项：关闭耗时（应从约 4 秒降到数百毫秒）、端口释放、**bind 超时分支不出现
  （R21 新增）**、关闭超时分支不出现、
  客户端释放恰一次、netty 回调关闭、`startServer()` 确实返回、`onStarted` 时序、
  `Server channel closed` 日志、启动中立即退出、sender 关闭真正发生、sender 正常退出、
  连接进行中退出、**stop 先于 start**。

## G. 给 reviewer 的核对清单

按优先级排列；每项都给出了判定方法。

1. **并发正确性（最高）**
   - `AudioSender.stop()` / `AudioReceiver.stopServer()`：`require(containsJob)` 是否在任何副作用之前、
     且在进入 `withContext(NonCancellable)` 之前（进入后 `currentCoroutineContext()[Job]` 变成
     `NonCancellable`，守卫会失效）。
   - 四处「赋值后 re-check `stopping`」+ `@Volatile`：按 Dekker 推理，发布侧「写字段 → 读标志」、
     teardown 侧「写标志 → 读字段」，是否保证资源要么被门闩释放、要么被补偿释放，不会两边都漏。
   - 补偿路径先 `teardownGate.awaitCompletion()` 再释放：若 `stopping == true` 但 `run()` 永不被调用，
     是否会永久等待？（代码中 `stopping.set(true)` 与 `teardownGate.run` 之间无挂起点，请确认。）
   - `MicRecorder`：`releaseCompleted` 在四条进入路径上是否都必然完成；`onStop` 是否恰好一次。
   - 三处 `ioScope.launch(NonCancellable)` 回滚是否真正脱离随后被 `cancel()` 的 scope
     （`NonCancellable` 是 `Job`，替换了父 Job）。
2. **basenetty 状态机**
   - C.2 的状态表是否穷尽；`onStopped()` 是否恰好一次；stop 先于 start 时 `startServer()` 是否由
     **状态**而非 Netty 线程时序拒绝。
   - `awaitCompletion()` 的 `isDone` 快速路径：reviewer 若能核对 Netty 4.1.108 的
     `DefaultPromise.addListener` → `notifyListeners` → `safeExecute` 路径更好（维护者只核对了缓存中的
     4.1.93 / 4.1.110）。
   - ~~`withTimeoutOrNull` 在 `NonCancellable` 内是否仍生效~~ —— **第五轮已确认生效**：`withTimeout`
     建的 `TimeoutCoroutine` 成为 body 的 Job，`NonCancellable` 只挡外层取消**传入**，超时是这个
     `TimeoutCoroutine` 取消自己；`awaitCompletion()` 用 `suspendCancellableCoroutine`，有可取消
     挂起点。两路审查独立得出同一结论。`shutdownNetty()` 的 `finally` 在超时穿过时是否一定发出两个
     group 的关闭请求 —— 仍请复核。
   - **新增**：`startServer()` 的 bind 现在也有上界（§I.1）。请复核超时分支里 `close()` → `FAILED`
     → `onStartFailed` 的次序，以及「置 FAILED 而非 STOPPED」是否确实让后续 `stopServer()` 仍能走
     `releaseUnstartedServer()` 释放两个 group。
3. **编译与静态检查（无法本地跑时的替代）**
   - 每个改动文件的 import 是否全部仍被使用；删除的方法是否还有 KDoc `[链接]` 指向。
   - ~~`BaseNettyServer.startServer()` 里 `var bindFuture: ChannelFuture?` 的智能转型是否成立~~
     —— **第五轮已消除**：改用局部 `val future`，`bindFuture` 只保留给 `catch` 分支，lambda 不再
     捕获可空 `var`。
   - `releaseUnstartedServer() = withContext(NonCancellable) { ... }` 推断为 `Unit`。
   - 测试里 `runBlocking { subject.releaseAndJoin() }` 在 executor 线程上是否可能自等待。
4. **测试是否测到真东西**
   - `SuspendTeardownGateTest` 第 3 例若去掉 `NonCancellable` 是否会红（应会）。
   - `TeardownSupportTest` 的 `containsJob` 用例若换成身份比较是否会红（应会）。
   - `BaseNettyLifecycleTest` 的 `awaitCompletion does not hang...` 若去掉快速路径是否会撞 60 秒超时（应会）。
5. **文档与代码一致性**：CHANGELOG 两个条目、spec §20、netty 文档 §11 是否与最终代码一致。

## H. 建议的提交切分（尚未提交，等所有者授权）

| 序 | type | 内容 |
|---|---|---|
| 1 | `refactor(basenetty)!` | C 区全部 + `WebSocketServerActivity.kt` + netty 文档 + CHANGELOG 的 basenetty 条目 |
| 2 | `refactor(audio)` | Task 1：两个接口 + 两个 CompressedPcm wrapper |
| 3 | `refactor(audio)` | Task 2：两个 StreamPlayer、`MicRecorder`、`AudioPlayer`（含日志门控） |
| 4 | `refactor(demo)` | Task 3：B 区全部 + 两个 socket client demo 的 `cancel()` 删除 |
| 5 | `refactor(audio)!` | Task 4：删除弃用 API、`AacEncoderWrapper` / `OpusEncoderWrapper`、三个测试、CHANGELOG 的 audio 条目 |
| 6 | `docs(audio)` | spec + plan |

`AacEncoderWrapper.kt` / `OpusEncoderWrapper.kt`、两个 StreamPlayer、`AudioReceiver.kt` 各横跨两个
提交，需 `git add -p` 拆块；若不拆，可合并为「netty 一个 + audio 一个 + docs 一个」。

## I. 第五轮审查（2026-10-15，外部）与修正

两路独立审查：主审查者逐路径复核 + 一个通用审查代理全量扫 diff。两路在「bind 等待无上界」这一处
独立汇合，其余互补。以下 9 处全部已修，均为静态推断，未经编译与真机验证。

### I.1 `BaseNettyServer.startServer()` 的 bind 等待加 3 秒上界（MEDIUM）

`bindFuture.awaitCompletion()` 是本次改动里**唯一没有上界的等待**，而它在 `lifecycleMutex` 之内。
关闭侧每一处等待都套了 `shutdownBounded`（3 秒），启动侧没有。后果链：

```
boss loop 被卡住 → bind future 不完成 → startServer() 持 lifecycleMutex 不放
  → AudioReceiver.stopServer() 最后一步 receiverServer?.stopServer() 抢不到锁
  → teardownSteps 永久挂起 → 门闩永不完成（按设计无超时）
  → 所有 teardownGate.awaitCompletion() 的补偿协程永久等待
```

这违反了 `SuspendTeardownGate` 自己 KDoc 写下的契约：「Every operation inside the block must
therefore be bounded itself」。另一路审查从 `awaitCompletion()` 的 `isDone` 快速路径只收窄、未关闭
check-then-`addListener` 竞态的角度得出同一结论。

```kotlin
val future = bootstrap.bind(port)
bindFuture = future
val bound = withTimeoutOrNull(NETTY_BIND_TIMEOUT_IN_MILLS) { future.awaitCompletion() }
if (bound == null) {
    future.channel().close()                        // bind 可能仍在 boss loop 里落地
    connectState.set(ServerConnectStatus.FAILED)    // 不是 STOPPED：两个 group 还活着
    connectionListener.onStartFailed(
        this, ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR, "Bind timed out ..."
    )
    return@withLock
}
serverChannel = future.channel()
```

| 决定 | 原因 |
|---|---|
| 新增 `NETTY_BIND_TIMEOUT_IN_MILLS = 3_000L` 而不复用 `NETTY_SHUTDOWN_TIMEOUT_IN_MILLS` | 语义不同。bind 是交给 boss loop 的本地 socket 操作，3 秒不是给慢网络的预算，是给「boss loop 卡死」的上界 |
| 超时后 `close()` 掉 channel | `serverChannel` 在这条路径上从未被赋值，`stopServer()` 找不到任何东西可关，端口会一直被占 |
| 状态置 `FAILED` 而非 `STOPPED` | 两个 EventLoopGroup 还活着，后续 `stopServer()` 必须仍能释放它们（走 `releaseUnstartedServer()`）|
| 用局部 `val future` 而不是回读可空 `var bindFuture` | lambda 捕获可空 `var` 无法智能转型。顺带关掉了 §G.3 第 2 条那个开放问题 |

### I.2 `stopServer()` 的状态迁移与 `onStopped()` 移入 `finally`（LOW）

`releaseInboundHandler()` 刻意 try/catch 了（注释写着 "Guarded like every other step"），紧挨着的
`shutdownBounded { shutdownNetty() }` 却没有。`awaitOrLog()` 只兜住两处 *await*；`serverChannel.close()`
本身或 `shutdownGracefully()` 直接抛的话，会穿过 `withTimeoutOrNull` 和 `withContext(NonCancellable)`，
于是 `connectState.set(STOPPED)` 与 `onStopped()` 都不执行 → 实例永久停在 `UNINITIALIZED` → 后续每次
`stopServer()` 都在「Already stopping」返回 `false`，`onStopped()` 永不送达。这与该函数 KDoc 自己写的
「the state transition and `ServerConnectListener` notification after it still have to run」直接矛盾。

改为 `try { shutdownBounded { ... } } finally { 状态迁移 + onStopped() }`，并把 `onStopped()` 单独
try/catch（它是子类代码，而且 finally 里抛出会顶掉上面正在抛的那个）。`releaseUnstartedServer()`
按同样形状处理，使「停在启动之前也一定落到 STOPPED」这条论断成立。

**没有改成吞掉异常**：异常仍然向调用方传播，只是两个承诺过的副作用不再被跳过。

### I.3 `AudioReceiver.startServer()` 的补偿路径改走 `cleanupScope` + `teardownSteps`（MEDIUM）

```kotlin
// 改动前
if (stopping.get()) {
    teardownGate.awaitCompletion()
    server.stopServer()
    player.releaseAndJoin()
}
```

两个问题：

1. **内联跑在调用方协程上**（`AudioActivity.kt` 的 `ioScope.launch`），而 `AudioSender` 的两处同类补偿
   都丢进了永不取消的 `cleanupScope`，同一文件里 `onClientConnected()` 的补偿也用了 `cleanupScope`
   —— 只有这一处没用。今天不出事，只因为 `AudioActivity.onDestroy()` 用的是 `ioScopeJob.complete()`
   而不是 `cancel()`（已核：全文件无任何 `cancel()`）。这是一条**没写下来的隐式依赖**：换成
   `lifecycleScope` 之类可取消的 scope，`awaitCompletion()` 一被取消，10020 端口和 AudioTrack 就
   一起留到进程死。
2. **三步裸跑**：`server.stopServer()` 抛了就跳过 `player.releaseAndJoin()` → AudioTrack 泄漏。
   整个改动的立论就是「一步失败不能跳过后面的步骤」，这里恰好没遵守。

两处都改掉。注意这让 `startServer()` 不再等待补偿完成 —— 调用方不需要这个等待，而换来的是补偿
不可能被调用方的取消掀掉。

### I.4 `AudioActivity` 的录音回调改为每次录音一个，各自持有自己的流（MEDIUM）

由审查代理发现，复核成立。`AudioActivity` 把**同一个 `recordCallback` 实例**传给每一个 `MicRecorder`，
而它的 `onStop` 无条件 close `pcmOs`/`aacOs`/`opusOs`，没有任何「哪个 recorder 停了」的判别；
`MicRecorder.onStopNotified` 是**实例级** `AtomicBoolean`，跨实例不设防。

路径：sender/receiver 已占麦 → `record(PCM)` 的 `startRecord()` 失败 → `failRecordStart()` 把释放丢到
`ioScope.launch(NonCancellable)` → 用户再点一次录音 → `record()` 重开流、建新 recorder → 旧回滚这时
才落到 `Dispatchers.IO`，把**刚开好的流**关了 → `onRecording` 的写入被 `runCatching{}.onFailure` 吞掉
→ 录出空文件，界面上没有任何错误。

值得写明的是：同步版其实也会关流，只是关的是自己这次（失败的那次）的。**把回滚异步化，把「毁掉
自己」变成了「毁掉下一次」** —— 这是 §1.1 第 3 条那个改动的连带后果，CHANGELOG 已经为下游写了
提醒，但仓库内唯一的调用方当时没跟上。

修法不是加身份判别，而是消掉共享状态：`pcmOs` / `aacOs` / `opusOs` / `recordType` 四个字段全部删除，
改为 `recordCallbackFor(type, out)` —— 每次 `record()` 打开一个流，交给这次录音自己的回调，
`onStop` 只关自己那一个。顺带修掉了同一个共享字段在 `onRecording` 侧的别名问题（一次 PCM 停止
原先会把上一轮残留的 aac 流也关掉）。

### I.5 `teardownSteps()` 的自抑制守卫（LOW）+ 回归测试

`failure?.addSuppressed(e)` 在两步抛出**同一个实例**时抛 `IllegalArgumentException: Self-suppression
not permitted`，这个异常从 catch 里逃出去、跳过剩余所有步骤 —— 正是这个 helper 存在的理由被它自己
违反。今天不可达（已核：`MicRecorder.releaseCompleted` 是 `CompletableDeferred<Unit>`，不向多个
awaiter 转发同一个 Throwable 实例；`AudioPlayer.releaseAndJoin()` 也不缓存异常），按防御性修复处理。

```kotlin
val first = failure
if (first == null) {
    failure = e
} else if (first !== e) {
    first.addSuppressed(e)
}
```

用 `if/else` 而不是无主体 `when`，是为了让 `first` 的非空智能转型不依赖对无主体 `when` 分支顺序的
推断（本地无法编译验证）。新增用例 `two steps reporting one instance do not abort the run`：去掉
身份判别就会红。

### I.6 `SuspendTeardownGate` 的 KDoc 自相矛盾（LOW）

KDoc 原文说 cancellation "belongs to the leader's own coroutine and is never republished"；catch 块的
注释正好反驳它："it is not a cancelled caller here either: the body runs under NonCancellable, so this
is a real teardown failure"。两个理由只能有一个成立，而**代码注释那个才是对的**。

行为本身刻意如此，`SuspendTeardownGateTest` 第 5 例锁定了它，所以只改 KDoc 不改行为。同时补上两边
都没写的实际后果：`finally { completed.complete(failure) }` 里 `failure` 在 cancellation 分支仍为
`null`，所以**teardown 真失败时 follower 观察到的是成功** —— `AudioActivity.launchCleanup("Stop audio
sender")` 会记一条成功日志，leader 那条 error 日志是唯一的记录。

### I.7–I.9 三处注释理由失准（只改注释，不改行为）

| # | 位置 | 原注释的问题 |
|---|---|---|
| I.7 | `AudioReceiver.sendRecAudioThread()` KDoc | 说保留 `ArrayBlockingQueue` 是因为文件已 import `io.netty.channel.Channel`、第二个 `Channel` 得全限定。import 别名就能解决，站不住。改为承认这是迁移顺序差异而非设计决定，并指出真正让两者在关闭语义上等价的是 `runInterruptible` |
| I.8 | `WebSocketClientActivity.onDestroy()` | 说 "release() is the only thing left for this scope to do"，但该 scope 还带着 `onConnectClick` 的协程。补上这半边：若它尚未首次派发，`webSocketClient` 还是 null、`release()` 空转，随后的 connect 会建出一个没人释放的 client。窗口很窄，而且**取消也救不了**（取消会落在同一个未派发的协程上），所以不改行为 |
| I.9 | `AudioSender.stop()` 第 1 步 | 说 `disconnectManually()` "sets the flag doRetry() bails out on"。在状态尚未到 `CONNECTING` 时它在 `UNINITIALIZED` 守卫处直接返回、什么都不设 —— 而这恰好就是「teardown 撞上正在 start」的那个窗口。改为写明：真正终止重试链的是 `stopping` 加第 8 步 `release()` → `stopRetryHandler()`，这一步只是让常见情形便宜 |

顺带核清一条怀疑：`connect()` 的状态检查与 `disconnectManually()` 用的是**同一个
`synchronized(this)`**，所以「`connect()` 的 `disconnectManually = false` 会覆盖掉刚设上的标志」这条
竞态**不存在**。

### I.10 本文的两处失准

| 位置 | 问题 | 处理 |
|---|---|---|
| §0 改动规模 | 写 `+2947 / −611`、`6 个新文件`；审查当时实测 `+2965 / −612`、`7 个新文件`（§0.1 自己列的就是 7 个，与 §0 汇总自相矛盾）。已核 mtime：发现时代码自本文生成后未变动过，是 §0 没刷新 | 改为只记**代码**行数（`25 个 .kt`，`+1160 / −510`，5 个新 `.kt`），文档行数另列。原写法把本文自己算进了总数，每次文档订正都会让它再次失准；代码行数不受文档编辑影响。并补注 `CLAUDE.md` / `.claude/memory/` 两处修改不属于本改动集 |
| E.8 | 只论证了第 8 步 `release()` 能看到赋值，没提同一窗口里第 1 步会被跳过 | 推理补齐，结论不变 |

### I.11 未采纳与降级

| 意见 | 处理 |
|---|---|
| `awaitCompletion()` 的 `isDone` 与 `addListener` 之间仍有竞态，建议加 post-add `isDone` 重检 | **未改**。所有调用点现已全部有上界（I.1 补上了最后一处），竞态的后果从「永久挂起」降为「最多等一个超时」。再加重检是正确的加固，但它要动的是本次改动里唯一一处纯新增的公共桥接，留给下一轮 |
| `WebSocketClientActivity` 泄漏 `NioEventLoopGroup` 长达约 2.7 分钟，建议改行为 | **降级为注释**（I.8）。要同时满足「`onDestroy` 抢在 connect 协程首次派发之前」且「用户点过 Connect」，而取消并不能修它 |
| §E.9 关于计划附录第 18 项自查文字过严 | 保持原判，未改 |

### I.12 本轮新增的未验证项

- `I.1` 的 bind 超时分支（`close()` + `FAILED` + `onStartFailed`）**无自动化覆盖**：要构造它需要把
  boss loop 卡住，`BaseNettyLifecycleTest` 里没有合适的手段。只能靠评审。
- `I.2` 的 `finally` 路径同样无覆盖：要触发需要 `close()` 或 `shutdownGracefully()` 直接抛，而 netty
  正常是 fail promise 而不是抛。
- `I.3`、`I.4` 属于设备路径（端口占用、麦克风占用），归入 §F.2 真机清单。
- `I.5` 有新单测；`I.6`–`I.10` 只动注释与文档。


## J. 第六轮审查（2026-10-16，两路并行）与修正

两路独立静态审查：一路看 basenetty + 3 个 socket demo 调用方，一路看 audio 模块 + demo 音频侧。
两路均未发现并发缺陷（双回调、状态滞留、lost wakeup、无界挂起、删除 API 的残留调用）。basenetty
一路给出 5 条 Minor，audio 一路给出 2 条 Important + 5 条 Minor。以下除 J.12 外全部已修；均为静态
推断，未经编译与真机验证。

### J.1 `stopServer()` / `releaseUnstartedServer()`：`releaseInboundHandler()` 挪进 `try`（Minor）

`releaseInboundHandler()` 只 `catch (e: Exception)`；子类 `release()` 抛 `Error`（`-ea` 下的
`AssertionError`、OOM）时它会在 `try` **之前**逃逸，状态留在 `UNINITIALIZED`、两个 group 存活——
正是 I.2 的 `finally` 注释声称已排除的那种情形。改动只是把它移到 `try` 的第一句：

```kotlin
        connectState.set(ServerConnectStatus.UNINITIALIZED)
        withContext(NonCancellable) {
            try {
                // Inside the try as well: releaseInboundHandler() only catches Exception, and an
                // Error out of subclass code must not leave the state on UNINITIALIZED either.
                releaseInboundHandler()
                shutdownBounded { shutdownNetty() }
            } finally {
                // ...（原注释与状态迁移 + onStopped() 不变）
```

`releaseUnstartedServer()` 同形：`try { releaseInboundHandler(); shutdownBounded { ... } } finally
{ connectState.set(STOPPED) }`。

### J.2 socket demo：scope 改为在 `release()` 返回后、同一协程的 `finally` 中取消（Minor）

C.5 删掉紧随 `launch` 的 `cancel()` 诊断正确，但矫枉过正：scope 从此永不取消，而 C.5 末尾那句
"取消并不能帮上忙"只对**立刻**取消成立；在 `release()` 返回之后（至多 3 秒后）再取消，落点已经在
`onConnectClick` 协程派发之后，能取消一个仍在连接中的 `connect()`（其 `invokeOnCancellation` 会清理）。
从 scope 自己的最后一个协程里取消它是合法的。两个 Activity 相同，`import kotlinx.coroutines.cancel`
随之恢复：

```kotlin
    override fun onDestroy() {
        // The scope is cancelled from inside its last coroutine, once release() has returned. A
        // cancel() issued right here instead could land before this coroutine is first
        // dispatched, which skips the whole body and release() never runs. Cancelling afterwards
        // still cancels a connect() that is in flight - its invokeOnCancellation cleans up - and
        // release() itself is bounded by its own timeout, so the cancel cannot cut it short.
        //
        // If onConnectClick's coroutine has been dispatched but has not assigned webSocketClient
        // yet, release() no-ops on the null, and the connect it then runs builds a client nothing
        // will release. Narrow, and no cancel timing closes it: a cancel that lands before the
        // first dispatch skips that coroutine entirely, one that lands after it is already past
        // the assignment.
        cs.launch {
            try {
                webSocketClient?.release()
            } finally {
                cs.cancel()
            }
        }
        super.onDestroy()
    }
```

`EventBusBridgeClientActivity` 为 `ioScope.launch { try { webSocket?.release() } finally
{ ioScope.cancel() } }`，注释为四行精简版。I.11 里"降级为注释"的那条 2.7 分钟泄漏在这个改法下不再
成立于 connect 已派发的情形，仍成立于"已派发、尚未赋值 `webSocketClient`"的窄窗口（注释第二段；
第七轮复核纠正：尚未派发的协程被取消时 body 根本不跑，不会建出 client，所以窗口不是"尚未派发"）。

### J.3 CHANGELOG 两处措辞（Minor）

- 标题 `basenetty 服务端生命周期改为挂起、非阻塞` → `basenetty 生命周期改为挂起、非阻塞`（条目同时
  覆盖客户端）。
- `BaseNettyClient.release()` 一条：旧实现除状态守卫外，`shutdownGracefully` 的 listener 报
  `!isSuccess` 和 `runCatching.onFailure` 分支也返回 `false`，原文"此前只在状态守卫处返回 `false`"
  过窄，改为"此前只在状态守卫、`shutdownGracefully` 失败或抛异常时返回 `false`"。
- demo 两个 Activity 那条补上 J.2 的"改为在 `release()` 返回后、同一协程的 `finally` 中取消"。

整个 CHANGELOG 条目相对 HEAD 都是新增行，所以 `git diff HEAD -- CHANGELOG.md` 只能看到定稿文本，看不到"改前 → 改后"；三处改动后的现行文本如下（摘自该 diff）：

```diff
+- **basenetty 生命周期改为挂起、非阻塞**：`BaseNettyServer.startServer()` / `stopServer()`
+  由阻塞方法改为 `suspend fun`；`BaseNettyClient.release()` 签名不变，内部不再阻塞线程。
…
+  - `BaseNettyClient.release()` 新增 3 秒资源层超时，超时返回 `false`（此前只在状态守卫、
+    `shutdownGracefully` 失败或抛异常时返回 `false`）。
…
+  - demo 的 `WebSocketClientActivity` / `EventBusBridgeClientActivity` 不再在 `onDestroy()` 中
+    `launch { release() }` 后立刻取消同一 scope（取消若先于首次调度，`release()` 根本不会执行），
+    改为在 `release()` 返回后、同一协程的 `finally` 中取消。
```

同一 diff 里 audio 条目下本轮补录的两个子项（J.12 第二条所指）：

```diff
+  - **行为变更**：`AudioPlayer.releaseAndJoin()` 把自身 AudioTrack 的释放挪到最后、放在 `finally`
+    中（此前 `release()` 最先释放它）：stream player 或 decoder wrapper 关闭抛异常时，每个会话都
+    存在的那个 AudioTrack 不再泄漏。
+  - demo 音频侧配套重构：`AudioSender` / `AudioReceiver` 的关闭改为一次性 `SuspendTeardownGate`
+    串行化、`teardownSteps()` 扁平步骤、`stopping` 标志加 `@Volatile` 字段与发布后 re-check，
+    在"点完开始立刻退出"时由补偿协程在 `cleanupScope` 上等待 teardown 结束后释放竞态期间建出的
+    AudioTrack / AudioRecord / 端口；`AudioSender` 的录音与接收缓冲由阻塞队列改为协程 `Channel`
+    （`AudioReceiver` 因与 `io.netty.channel.Channel` 同名冲突保留阻塞队列）。
+    `ADPCMActivity` 的三连点改为 Job 链：新播放先 `NonCancellable` 地 join 上一个 Job 再建播放器。
```

### J.4 "Already stopping" 守卫加防御性说明（Minor）

mutex 串行化 + `finally` 必落 `STOPPED` 之后，没有调用方能在 `serverChannel` 已初始化时观察到
`UNINITIALIZED`。守卫保留，注释写明是 defence in depth，免得读者去找到达它的路径。现行代码（`BaseNettyServer.kt` L345–350）：

```kotlin
        if (ServerConnectStatus.UNINITIALIZED == state) {
            // Defence in depth. The mutex serialises stops and the finally below always lands on
            // STOPPED, so no caller should observe UNINITIALIZED with serverChannel initialised.
            LogContext.log.w(tag, "Already stopping")
            return@withLock false
        }
```

### J.5 补三个测试（Minor，但对应 CHANGELOG 明文承诺的路径）

`BaseNettyLifecycleTest.kt` 新增 `import kotlinx.coroutines.CoroutineStart` /
`import kotlinx.coroutines.launch`，`TestServer` 增加 `port: Int = 0` 参数。

**(a) 取消 `stopServer()` 的调用方，仍落 `STOPPED` 且 `onStopped()` 一次**——本次改动的核心保证，此前
无覆盖：

```kotlin
    @Test
    fun `cancelling the stopServer caller still lands on STOPPED with one onStopped`(): Unit =
        runBlocking {
            val listener = RecordingServerListener()
            val server = TestServer(listener)
            server.initHandler(TestServerHandler(server))
            server.startServer()
            assertEquals(ServerConnectStatus.STARTED, server.connectState.get())

            // UNDISPATCHED runs the body up to its first suspension, which is inside the
            // NonCancellable block (awaiting the channel close or the loop termination). The
            // cancel that follows therefore hits a teardown already past the state guard.
            val stop = launch(start = CoroutineStart.UNDISPATCHED) { server.stopServer() }
            stop.cancel()
            stop.join()

            assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
            assertEquals(1, listener.stoppedCount.get())
            assertFalse(server.stopServer())
            assertEquals(1, listener.stoppedCount.get())
        }
```

确定性论证：`runBlocking` 内 `UNDISPATCHED` 的协程在调用线程上一直跑到第一个真实挂起点。
`lifecycleMutex.withLock` 无竞争不挂起；`withContext(NonCancellable)` 同 dispatcher，走
`UndispatchedCoroutine`；`withTimeoutOrNull` 同样 undispatched；第一个挂起点要么是
`serverChannel.close().awaitOrLog()`（NIO close 由 event loop 异步完成），要么（若 close 已瞬时完成）
是 `terminationFuture().awaitOrLog()`。两者都在 `NonCancellable` 之内，其后的 `cancel()` 无效。

**(b) 启动失败后 stop 释放 group 并拒绝再次启动**（CHANGELOG"含启动失败"）：

```kotlin
    @Test
    fun `stop after a failed start releases the event loops and refuses a later start`(): Unit =
        runBlocking {
            ServerSocket(0).use { occupied ->
                val listener = RecordingServerListener()
                val server = TestServer(listener, port = occupied.localPort)
                server.initHandler(TestServerHandler(server))

                server.startServer()

                assertEquals(ServerConnectStatus.FAILED, server.connectState.get())
                assertEquals(
                    listOf(ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR),
                    listener.startFailedCodes.toList()
                )

                // serverChannel was never assigned, so this takes the unstarted-release path:
                // both groups are shut down, no onStopped(), and the state refuses a restart.
                assertFalse(server.stopServer())
                assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
                assertEquals(0, listener.stoppedCount.get())

                server.startServer()

                assertEquals(ServerConnectStatus.STOPPED, server.connectState.get())
                assertEquals(
                    listOf(
                        ServerConnectListener.CONNECTION_ERROR_SERVER_START_ERROR,
                        ServerConnectListener.CONNECTION_ERROR_ALREADY_RELEASED
                    ),
                    listener.startFailedCodes.toList()
                )
            }
        }
```

Linux 上两个 socket 不能同时 LISTEN 同一端口（`SO_REUSEADDR` 不放行，需 `SO_REUSEPORT`），所以 bind
以 `BindException` 失败 → `awaitCompletion()` 抛出 → `catch (e: Exception)` → `FAILED` +
`SERVER_START_ERROR`。

**(c) `BaseNettyClient.release()` 的返回值与终态**：既有用例 `connect failure updates stored state to
failed` 末尾的 `client.release()` 改为

```kotlin
        assertTrue(client.release())
        assertEquals(ClientConnectStatus.UNINITIALIZED, client.connectStatus.get())
```

连接失败后 `channel` 未初始化，`closeChannelAndAwait()` 直接返回，路径只剩 `shutdownGracefully` +
`terminationFuture().awaitCompletion()`，必须在 3 秒内落到 `UNINITIALIZED`。

### J.6（预留，无内容）

### J.7 替代函数的 KDoc 补迁移说明（Important，CLAUDE.md「API 演进约定」）

CHANGELOG 写了迁移方式，但替代函数自己的 KDoc 一句没提。七处各追加一段，措辞统一为
"Replaces the removed non-suspend X(). Migration: ..."：

| 文件 | 函数 | 追加的要点 |
|---|---|---|
| `AudioPlayer.kt` | `releaseAndJoin()` | 替代 `release()`；wrapper 与 stream player 改为等待而非仅发信号；自身 track 改为最后在 `finally` 释放（`release()` 以前最先放它） |
| `MicRecorder.kt` | `stopRecordAndJoin()` | 替代 `stopRecord()`；返回时 record job 与 encoder worker 已退出、`AudioRecord` 已释放（`stopRecord()` 同步释放了 `AudioRecord`，但不等两个 worker 退出） |
| `AacStreamPlayer.kt` / `OpusStreamPlayer.kt` | `stopPlayingAndJoin()` | 替代 `stopPlaying()`；返回时 decoder worker 已退出、track 已释放（`stopPlaying()` 同步释放了 track，但不等 decoder worker 退出） |
| `BaseMediaCodec.kt` | `releaseAndJoin()` | 替代 `release()`；原来覆写 `release()` 的子类把工作放进 `onCodecReleased`，本方法非 `open` |
| `AudioEncoderWrapper.kt` / `AudioDecoderWrapper.kt` | `releaseAndJoin()` | 替代非挂起 `release()`；实现把整个释放放进本方法，调用方在自己的协程里等待 |

七处现行 KDoc 原文（工作树行号；A.1 / A.2 / A.8–A.12 的代码块已同步）：

`AudioPlayer.kt` `releaseAndJoin()`（L163–189，KDoc 全文与签名；迁移段为末段）：

```kotlin
    /**
     * Releases every resource this player owns and waits for the workers to exit.
     *
     * The three AudioTrackPlayer instances involved are independent: this player builds its own,
     * and each stream player builds another. AudioPlayer's constructor also gives a session
     * either a decoder wrapper or a stream player, never both, so these calls cannot contend for
     * the same track and their relative order is free.
     *
     * The track this player always builds is released in a finally: StreamPlayerStopper rethrows
     * CancellationException and stopAndJoin() can also fail its own require(), so an exception
     * from the calls above would otherwise leak the one AudioTrack that exists in every session.
     *
     * Releasing that track last is also safe on the PCM path, where it is the one being written:
     * with AudioType.PCM there is no wrapper and no stream player, so nothing before the finally
     * touches it, and callers join their writer before calling this (AudioActivity's PCM
     * playback does exactly that). With COMPRESSED_PCM the wrapper's releaseAndJoin() is a no-op.
     *
     * One worker is deliberately not awaited: a stream player's init-failure rollback is
     * fire-and-forget by design, see AacStreamPlayer.releaseDecoderAsync().
     *
     * Replaces the removed non-suspend release(). Migration: await this from a coroutine the
     * owner controls instead of calling release() synchronously. Two things changed on the way:
     * the decoder wrapper and the stream players are now awaited rather than signalled, and this
     * player's own track is released last, in the finally, where release() used to release it
     * first.
     */
    suspend fun releaseAndJoin() {
```

`MicRecorder.kt` `stopRecordAndJoin()`（L392–401，KDoc 全文与签名；迁移段为末段）：

```kotlin
    /**
     * Deterministic teardown: stop the AudioRecord first (so the native read returns), then join
     * the record job and release resources. This API must be called by an external owner.
     *
     * Replaces the removed non-suspend stopRecord(). Migration: await this from a coroutine the
     * owner controls; it returns only once the record job and the encoder worker have exited and
     * the AudioRecord is released, where stopRecord() released the AudioRecord synchronously but
     * returned without waiting for either worker to exit.
     */
    suspend fun stopRecordAndJoin() {
```

`AacStreamPlayer.kt` `stopPlayingAndJoin()`（L239–248，KDoc 全文与签名；迁移段为末段）：

```kotlin
    /**
     * Deterministic teardown. Detaches decoder/CSD and advances the generation under the lock,
     * then awaits the old decoder OUTSIDE the lock to avoid suspension or callback re-entry.
     *
     * Replaces the removed non-suspend stopPlaying(). Migration: await this from a coroutine the
     * owner controls; it returns only once the decoder worker has exited and the track is
     * released, where stopPlaying() released the track synchronously but returned without
     * waiting for the decoder worker to exit.
     */
    suspend fun stopPlayingAndJoin() {
```

`OpusStreamPlayer.kt` `stopPlayingAndJoin()`（L233–242，KDoc 全文与签名；迁移段为末段）：

```kotlin
    /**
     * Deterministic teardown. Detaches decoder/CSD and advances the generation under the lock,
     * then awaits the old decoder OUTSIDE the lock to avoid suspension or callback re-entry.
     *
     * Replaces the removed non-suspend stopPlaying(). Migration: await this from a coroutine the
     * owner controls; it returns only once the decoder worker has exited and the track is
     * released, where stopPlaying() released the track synchronously but returned without
     * waiting for the decoder worker to exit.
     */
    suspend fun stopPlayingAndJoin() {
```

`BaseMediaCodec.kt` `releaseAndJoin()`（L152–164，KDoc 全文与签名；迁移段为末段）：

```kotlin
    /**
     * Deterministic lifecycle teardown: cancel and join the codec worker, cancel [ioScope], then
     * release the codec exactly once.
     *
     * This MUST be called by an external owner, never by the codec worker itself, otherwise the
     * worker would join on itself and deadlock. If a worker needs to request teardown, it should
     * expose a separate `requestRelease()` returning a [Job] instead of calling this.
     *
     * Replaces the removed non-suspend release(). Migration: await this from a coroutine the
     * owner controls; subclasses that overrode release() put that work in [onCodecReleased]
     * instead, as this method is not open.
     */
    suspend fun releaseAndJoin() {
```

`AudioEncoderWrapper.kt` `releaseAndJoin()`（L13–22，KDoc 全文与签名；迁移段为末段）：

```kotlin
    /**
     * Waits until any asynchronous encoder worker has stopped and its resources are released.
     *
     * Deliberately abstract: a default delegating to a non-suspend release would make this
     * method's own promise false for every implementation that did not override it.
     *
     * Replaces the removed non-suspend release(). Migration: implementations move the whole
     * release into this method and callers await it from a coroutine they own.
     */
    suspend fun releaseAndJoin()
```

`AudioDecoderWrapper.kt` `releaseAndJoin()`（L10–16，KDoc 全文与签名；迁移段为末段）：

```kotlin
    /**
     * Waits until any asynchronous decoder worker has stopped and its resources are released.
     *
     * Replaces the removed non-suspend release(). Migration: implementations move the whole
     * release into this method and callers await it from a coroutine they own.
     */
    suspend fun releaseAndJoin()
```

### J.8 `AudioReceiver.startServer()` 的发布后 re-check 移入 `finally`（Important，spec §21.2 的遗漏）

`BaseNettyServer.startServer()` 唯一会逃出的异常是调用方的 `CancellationException`（C.2 的 CE 分支
显式 `throw e`，其余都转成 `onStartFailed`）。调用方协程被取消时 `audioPlayer` / `receiverServer`
已发布、门闩若已被消费，则 10020 端口与 AudioTrack 无人释放。今天不可达只因为
`AudioActivity.onDestroy()` 用 `ioScopeJob.complete()` 而非 `cancel()`——这正是 §21.2 想消掉的
隐式依赖，但 I.3 只把补偿挪到了 `cleanupScope`，没放到异常路径上。改为：

```kotlin
        server.initHandler(receiverHandler)
        try {
            server.startServer()
        } finally {
            // Re-check after publishing, not only before building: ...（原注释）
            //
            // In a finally: the only exception startServer() lets out is the caller's
            // CancellationException, and a cancelled caller has published both fields just the
            // same. Without this, the compensation would hang on the caller's scope staying
            // alive - exactly the dependency this re-check exists to remove.
            if (stopping.get()) {
                cleanupScope.launch {
                    teardownGate.awaitCompletion()
                    teardownSteps(
                        { server.stopServer() },
                        { player.releaseAndJoin() }
                    )
                }
            }
        }
```

`finally` 里只做 `stopping.get()` 与 `cleanupScope.launch`，二者都不挂起，所以已取消的调用方协程
也能把补偿交出去。CE 路径上 `server.stopServer()` 走 `serverChannel` 未初始化分支
（`releaseUnstartedServer()`），与 C.2 的 CE 分支 `bindFuture?.channel()?.close()` 互补。

### J.9 `AudioSender.stop()` 第 1 步注释补一环（Minor）

原注释说关闭重试链靠"最后一步 `release()` 调 `stopRetryHandler()`"，但 `release()` 在
`UNINITIALIZED` 时直接 `return false`，根本不进 `releaseNow()`。结论仍成立，原因是
`senderClient = client` 到 `connect()` 内置 `CONNECTING` 之间没有挂起点，而 start() 的 job 在最后
一步之前已被 cancel-and-join，所以届时非 null 的 `senderClient` 不可能是 `UNINITIALIZED`。注释补上
这段依赖，并警告在那一段加挂起点会重新打开 `NioEventLoopGroup`（构造时创建）泄漏。只改注释。现行注释（`AudioSender.kt` L239–257，第三段 L251–256 为本轮新增）：

```kotlin
                // Disconnect first, before anything slow. handleConnectFailure() calls onFailed
                // synchronously and then doRetry(), so the client can reconnect ~2s later and
                // hand onConnected() a fresh AudioPlayer that this one-shot gate would refuse to
                // release.
                //
                // Not what ends the retry chain on its own: on a client that has not reached
                // CONNECTING - the window where start()'s coroutine is still on its way to
                // connect() - disconnectManually() returns at its UNINITIALIZED guard and sets
                // nothing. What closes that window is `stopping`, which turns onConnected()
                // away, and the last step's release() calling stopRetryHandler(). This step is
                // what makes the common case cheap, not what makes the race safe.
                //
                // release() only reaches stopRetryHandler() past its own UNINITIALIZED guard,
                // and it does: there is no suspension point between `senderClient = client` and
                // the CONNECTING transition inside connect(), and start()'s job is
                // cancel-and-joined before the last step runs, so a non-null senderClient is
                // never UNINITIALIZED by then. A suspension point added to that stretch would
                // reopen a NioEventLoopGroup leak - the group is built in the constructor.
                { senderClient?.disconnectManually() },
```

### J.10 `SuspendTeardownGate` KDoc 写明不可重入（Minor）

`block` 若在同一协程内同步再调 `run()` / `awaitCompletion()`，`started` 已为 true，等的是自己。今天
所有回调都 `cleanupScope.launch`，不可达；KDoc 追加一段"Not re-entrant: ..."。只改文档。追加的原文（`SuspendTeardownGate.kt` L34–37）：

```kotlin
 * Not re-entrant: the block must not call [run] or [awaitCompletion] synchronously on its own
 * coroutine - `started` is already set, so it would await its own completion for good. A callback
 * fired from inside the teardown that wants a teardown launches it on another scope, which is
 * what every current call site does.
```

### J.11 `ADPCMActivity` 解码块改为双 `catch`（Minor，仅一致性）

`runCatching { }.onFailure { if (it is CancellationException) throw it; log }` 改为仓库其余位置
（`AudioActivity.launchCleanup`、`StreamPlayerStopper`）的写法：

```kotlin
                try {
                    AdpcmImaQtDecoder(decoderInfo.sampleRate, decoderInfo.channelCount)
                        .use { decoder -> /* 不变 */ }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogContext.log.e(ITAG, "Unable to decode ADPCM audio.", e)
                }
```

捕获范围由 `Throwable` 收窄为 `Exception`：`Error` 不再被记日志后吞掉，而是穿出 job 体（外层
`finally` 仍 `NonCancellable` 地释放 player）。

### J.12 仅记录、未改

- `stopping` re-check 与三条补偿路径没有 JVM 单测，只有 Task 5 的真机项（§F.2）。要在 JVM 上构造
  "发布后、re-check 前恰好有 stop 消费门闩"需要向 `AudioSender` / `AudioReceiver` 注入挂起点，本轮
  不做。
- CHANGELOG audio 条目补录了 `AudioPlayer.releaseAndJoin()` 的释放顺序变更与 demo 音频侧重构
  （`AudioSender` / `AudioReceiver` / `ADPCMActivity`），属文档，列在此处备查。
- audio 一路确认：删除 API 的调用方全部迁移完毕（全仓 grep `.stopRecord(`、`.stopPlaying(`、
  `fun release()` 覆写、两个 wrapper 接口的实现者）；`AudioCipherActivity.kt` 的 `player?.release()`
  是 `MediaPlayer`。

### J.13 第七轮复核（2026-10-16）对 §J 的订正

复核者逐项核对 J.1–J.11 的代码与声明，全部成立；对三个测试给出了确定性论证（(a) 状态守卫在
`launch()` 返回前已同步通过，`cancel()` 不可能抢在前面；(b) bootstrap 无 `SO_REUSEPORT`，Linux 不允许
两个 socket 同时 LISTEN；(c) 连接失败后 `channel` 未初始化，`release()` 必在预算内落 `UNINITIALIZED`）。
需要改的只有措辞：

- `MicRecorder.stopRecordAndJoin()` KDoc：HEAD 的 `stopRecord()` 经 `finishRecorderRelease()` **同步**
  `releaseAudioRecord()`，并非"发出请求即返回"；缺的是 join record job 与 encoder worker。已改。
- `AacStreamPlayer` / `OpusStreamPlayer.stopPlayingAndJoin()` KDoc：HEAD 的 `StreamPlayerStopper.stop()`
  在 finally 里**同步** `releaseAudioTrack()`；缺的是 join decoder worker。已改。
- `WebSocketClientActivity.onDestroy()` 注释：泄漏窗口的前提是"已派发、尚未赋值"，不是"尚未派发"
  （尚未派发的协程被取消时 body 不跑）。已改，J.2 的引用块同步。

### J.14 提出但未实施：客户端 `release()` 终态不对称（另文）

第六轮之后由本文作者提出、所有者要求单独成文：`BaseNettyClient.release()` 超时或失败时状态停在私有态
`RELEASING`，与服务端"三条路径都落 `STOPPED`"不对称；连带发现"从未连接的客户端调 `release()` 被
`UNINITIALIZED` 守卫拒绝、`NioEventLoopGroup` 泄漏"。分析、原因与两套方案（均**未改代码**）见
`00-documents/2026-10-16-basenetty-client-release-terminal-state_cc.md`。

