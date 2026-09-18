# audio 模块弃用关闭 API 移除 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 `audio` 模块全部非挂起的弃用关闭 API，调用方一律改用确定性的 suspend 版本，消除 8 条弃用警告且不使用任何 `@Suppress`。

**Architecture:** 分四批落地，**每一批都可编译和审查**：先补齐 suspend 契约，再迁移 `audio` 内部调用，然后以可测试的一次性关闭门闩迁移 demo，最后删除旧 API。关闭路径全程确定性可等待；启动失败回滚在 `NonCancellable` 上异步执行。`AudioSender` 的阻塞队列改为可取消 Channel，两个网络 demo 类的 Activity/netty 并发关闭共享同一 teardown 结果，`ADPCMActivity` 则由播放 Job 拥有和释放 player。

**Tech Stack:** Kotlin 2.3.10、kotlinx-coroutines、Android SDK 36 / minSdk 21、JUnit 5 + Mockk + Kluent、detekt（`maxIssues = 0`）、ktlint（行长 100）

**Spec:** `00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md`

## Global Constraints

- **不新增任何 `@Suppress("DEPRECATION")`**，并删除测试里为调用本次弃用 API 而加的 20 处
  （spec §1、§9）。`audio/src/main/.../exts/AudioExt.kt` 与 demo 各 Activity 里那 13 处针对
  Android 平台 API 的抑制与本次无关，保持不动。
- Codex 在本机实施时必须自行执行本计划列出的窄范围 Gradle 验证。Claude Code 位于远端服务器，无法编译或运行 Gradle；它只能完成源码静态分析，不得把该结论表述为构建或测试通过。
- detekt `maxIssues = 0`：删除代码后必须一并清理随之失效的 import 与私有成员，否则直接失败。
- ktlint 行长上限 **100** 字符。
- 代码注释与 Git commit message 一律用**英文**；commit 不加 `Co-Authored-By` 或任何 AI 署名。
- 所有 Gradle 验证命令必须带 `--rerun-tasks`：刚修改过的路径不得只依赖 `UP-TO-DATE`。
- 未经用户明确授权，不得执行本计划中的 `git commit`、`git push`、发布或合并命令；提交步骤只是获权后的建议。
- 回滚协程的 body **必须整体包 `runCatchingPreservingCancellation`**：三个 `ioScope` 均无 `CoroutineExceptionHandler`，而 `NonCancellable` 换掉了父 Job，逃逸异常直达平台默认处理器 → 进程崩溃（spec §4.6、§8）。

### 实施批次与 spec 一致

按 spec §7 执行“先加新 API → 迁移 `audio` → 迁移 demo 与并发关闭 → 删旧 API”，
使每个 Task 结束时都可编译、可测试。提交拆分仅是获权后的建议，不代表实施者
自动获得提交或推送权限。

---

## File Structure

| 文件 | 本计划中的职责 |
|------|---------------|
| `audio/.../base/iters/AudioEncoderWrapper.kt` | 编码器包装接口。加 `suspend releaseAndJoin()` 抽象声明，最终删 `release()` 与退化默认实现 |
| `audio/.../base/iters/AudioDecoderWrapper.kt` | 解码器包装接口。新增 `suspend releaseAndJoin()`，最终删 `release()` |
| `audio/.../base/encoderWrapper/AacEncoderWrapper.kt` | 已有 `releaseAndJoin()`，最终删 `release()` 覆写 |
| `audio/.../base/encoderWrapper/OpusEncoderWrapper.kt` | 同上 |
| `audio/.../base/encoderWrapper/CompressedPcmEncoderWrapper.kt` | 纯内存 codec，加空体 `releaseAndJoin()`，最终删 `release()` |
| `audio/.../base/decoderWrapper/CompressedPcmDecoderWrapper.kt` | 同上（`AudioDecoderWrapper` 的唯一实现） |
| `audio/.../mediacodec/BaseMediaCodec.kt` | 最终删弃用的 `open fun release()` |
| `audio/.../aac/AacStreamPlayer.kt` | 回滚路径改异步；最终删 `stopPlaying()` |
| `audio/.../opus/OpusStreamPlayer.kt` | 同上 |
| `audio/.../base/StreamPlayerStopper.kt` | 最终删非挂起的 `stop()` |
| `audio/.../AudioPlayer.kt` | 加 `suspend releaseAndJoin()`；最终删 `release()` |
| `audio/.../MicRecorder.kt` | `failRecordStart()` 回滚改异步；最终删 `stopRecord()` 与 `finishRecorderRelease()` |
| `audio/src/test/.../mediacodec/BaseMediaCodecAsynchronousTest.kt` | 18 处 `subject.release()` 随 API 删除而迁移/重写 |
| `audio/src/test/.../mediacodec/BaseMediaCodecSynchronousTest.kt` | `:38` `subject.release()` 迁移 |
| `audio/src/test/.../base/StreamPlayerStopperTest.kt` | 删除只测 `stop()` 的那个用例 |
| `demo/.../audio/SuspendTeardownGate.kt` | 为 Activity/netty 并发关闭提供一次执行、共享结果的纯 Kotlin 门闩 |
| `demo/src/test/.../audio/SuspendTeardownGateTest.kt` | 覆盖并发等待、异常传播和调用者取消 |
| `demo/.../audio/sender/AudioSender.kt` | 队列改 Channel；加 `cleanupScope` 和关闭门闩；`stop()` 改 suspend 并等待 worker 退出 |
| `demo/.../audio/receiver/AudioReceiver.kt` | 加 `cleanupScope` 和关闭门闩；`stopServer()` 改 suspend 并在释放前等待 worker 退出 |
| `demo/.../audio/AudioActivity.kt` | 仅换方法名，沿用已有的 `launchCleanup` |
| `demo/.../audio/ADPCMActivity.kt` | 用 lifecycle 播放 Job 替代裸 thread，由 Job 拥有并释放局部 player |
| `CHANGELOG.md` | 记录破坏性变更 |

## 关于测试

wrapper 空实现和接口契约无需为了形式覆盖而新增测试；但 demo 新增的
`SuspendTeardownGate` 是可独立测试的纯 Kotlin 并发协议，Task 3 必须使用 RED/GREEN 覆盖它。
现有 codec 测试的 20 处旧 API 调用仍在 Task 4 同步迁移。最终结论必须分开报告 Claude Code
静态分析、Codex 本机单测/构建、真机验证与未验证项。

**但「不用新写」不等于「现有测试不受影响」。** 三个测试文件共 20 处调用了本次要删的 API，不处理会让 `:audio:testDebugUnitTest` **编译失败**：

- `BaseMediaCodecAsynchronousTest.kt` — 18 处 `subject.release()`（`:35 :62 :91 :108 :124 :153 :182 :200 :234 :255 :284 :303 :304 :387 :405 :419 :448 :500`），其中 `:500` 在被 3 个用例共用的私有辅助函数 `assertReleaseWaitsForCallback()` 内
- `BaseMediaCodecSynchronousTest.kt:38` — `subject.release()`
- `StreamPlayerStopperTest.kt:34` — `subject.stop { }`

这批迁移放在 **Task 4 Step 8**，与删除 API 同一个提交（分开会留下一个测试编译不过的中间提交）。逐用例的处理方式见该 Step 与 spec §6.2。

---

## Task 1：为接口与四个 wrapper 补齐 suspend 变体

旧 API 全部保留，因此本 Task 结束时编译通过、行为完全不变。

**Files:**
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/iters/AudioEncoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/iters/AudioDecoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/decoderWrapper/CompressedPcmDecoderWrapper.kt`
- 无需修改：`AacEncoderWrapper.kt` / `OpusEncoderWrapper.kt`（已实现 `releaseAndJoin()`）

**Interfaces:**
- Consumes: 无
- Produces: `AudioDecoderWrapper.releaseAndJoin(): Unit`（suspend，抽象）；
  `AudioEncoderWrapper.releaseAndJoin(): Unit`（suspend，改为抽象，签名不变）

- [ ] **Step 1: 把 `AudioEncoderWrapper.releaseAndJoin()` 的退化默认实现改为抽象**

文件 `audio/src/main/kotlin/com/leovp/audio/base/iters/AudioEncoderWrapper.kt`，把

```kotlin
    /** Waits until any asynchronous encoder worker has stopped and its resources are released. */
    suspend fun releaseAndJoin() = release()
```

改为

```kotlin
    /**
     * Waits until any asynchronous encoder worker has stopped and its resources are released.
     *
     * Deliberately abstract: a default delegating to a non-suspend release would make this
     * method's own promise false for every implementation that did not override it.
     */
    suspend fun releaseAndJoin()
```

`fun release()` 本步**保留不动**。

- [ ] **Step 2: 给 `AudioDecoderWrapper` 新增挂起变体**

文件 `audio/src/main/kotlin/com/leovp/audio/base/iters/AudioDecoderWrapper.kt`，完整改为：

```kotlin
package com.leovp.audio.base.iters

/**
 * Author: Michael Leo
 * Date: 20-11-14 上午11:27
 */
interface AudioDecoderWrapper {
    fun decode(input: ByteArray)
    fun release()

    /** Waits until any asynchronous decoder worker has stopped and its resources are released. */
    suspend fun releaseAndJoin()
}
```

- [ ] **Step 3: 给 `CompressedPcmEncoderWrapper` 实现挂起变体**

文件 `audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt`，
在现有 `override fun release() { }` **之后**追加：

```kotlin
    override suspend fun releaseAndJoin() {
        // Nothing to release: compression runs synchronously on the caller's thread, so there is
        // no worker to wait for.
    }
```

- [ ] **Step 4: 给 `CompressedPcmDecoderWrapper` 实现挂起变体**

文件 `audio/src/main/kotlin/com/leovp/audio/base/decoderWrapper/CompressedPcmDecoderWrapper.kt`，
在现有 `override fun release() { }` **之后**追加：

```kotlin
    override suspend fun releaseAndJoin() {
        // Nothing to release: decompression runs synchronously on the caller's thread, so there
        // is no worker to wait for.
    }
```

- [ ] **Step 5: 验证**

```bash
./gradlew --continue --rerun-tasks :audio:compileDebugKotlin :audio:detekt :audio:ktlintCheck
```

预期：`BUILD SUCCESSFUL`。弃用警告数量**不变**（仍为 8 条）——本 Task 只做加法。

- [ ] **Step 6: 提交（仅用户明确授权时）**

```bash
git add audio/src/main/kotlin/com/leovp/audio/base/iters/AudioEncoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/iters/AudioDecoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/decoderWrapper/CompressedPcmDecoderWrapper.kt
git commit -m "refactor(audio): give both wrapper interfaces a real suspend teardown

AudioEncoderWrapper.releaseAndJoin() defaulted to the non-suspend release(),
so its own promise of deterministic teardown was false for anyone who did not
override it. AudioDecoderWrapper never had a suspend variant at all, which is
why callers had nothing correct to migrate to.

The non-suspend release() stays for now; callers move over next."
```

---

## Task 2：把 `audio` 模块内部调用迁到新 API

本 Task 结束时 `audio` 模块内除**弃用方法自身的实现体**外，不再有弃用调用。

**Files:**
- Modify: `audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt`（回滚路径）
- Modify: `audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt`（回滚路径）
- Modify: `audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt`（`failRecordStart`）
- Modify: `audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt`（新增 `releaseAndJoin()`）

**Interfaces:**
- Consumes: `AudioDecoderWrapper.releaseAndJoin()`（Task 1）
- Produces: `AudioPlayer.releaseAndJoin(): Unit`（suspend）——Task 3 的 demo 调用方依赖它

- [ ] **Step 1: 改造 `AacStreamPlayer` 的初始化失败回滚**

文件 `audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt`，`initDecoderLocked()` 末尾的
`.onFailure { }` 块（约 `:153-158`），把

```kotlin
        }.onFailure {
            runCatchingPreservingCancellation { audioDecoder?.release() }
            audioDecoder = null
            csd0 = null
            LogContext.log.e(TAG, "init failed, rolled back. msg=${it.message}", it)
        }
```

改为

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
```

- [ ] **Step 2: 在 `AacStreamPlayer` 中新增 `releaseDecoderAsync()` 私有辅助函数**

放在 `initDecoderLocked()` 之后、`decodeOrDropLocked()` 之前：

```kotlin
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

新增 import（若尚未存在）：`kotlinx.coroutines.NonCancellable`、`kotlinx.coroutines.launch`。

- [ ] **Step 3: 对 `OpusStreamPlayer` 做同样改造**

文件 `audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt`，`.onFailure { }` 块
（约 `:146-152`）改为：

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
```

并新增私有辅助函数（注意解码器类型是 `OpusDecoder`）：

```kotlin
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

- [ ] **Step 4: 改造 `MicRecorder.failRecordStart()`**

文件 `audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt`，约 `:187-194`，把

```kotlin
    private fun failRecordStart(message: String) {
        LogContext.log.e(TAG, message)
        stopped.set(true)
        ioScope.cancel()
        stopAudioRecord()
        finishRecorderRelease(stopSucceeded = false)
    }
```

改为

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

新增 import（若尚未存在）：`kotlinx.coroutines.NonCancellable`、`kotlinx.coroutines.launch`。
`startRecord()` 维持非挂起，公开面不变。

- [ ] **Step 5: 给 `AudioPlayer` 新增 `releaseAndJoin()`**

文件 `audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt`，在现有 `fun release()`（`:163`）
**之后**追加新方法（本步**不删** `release()`）：

```kotlin
    /**
     * Releases every resource this player owns and waits for the workers to exit.
     *
     * The three AudioTrackPlayer instances involved are independent: this player builds its own,
     * and each stream player builds another. AudioPlayer's constructor also gives a session
     * either a decoder wrapper or a stream player, never both, so these calls cannot contend for
     * the same track and their relative order is free.
     */
    suspend fun releaseAndJoin() {
        LogContext.log.w(TAG, "releaseAndJoin()")
        decoderWrapper?.releaseAndJoin()
        aacStreamPlayer?.stopPlayingAndJoin()
        opusStreamPlayer?.stopPlayingAndJoin()
        audioTrackPlayer.release()
    }
```

- [ ] **Step 6: 验证**

```bash
./gradlew --continue --rerun-tasks :audio:compileDebugKotlin :audio:detekt :audio:ktlintCheck
```

预期：`BUILD SUCCESSFUL`。弃用警告应从 8 条降到 **6 条**：

| 位置 | 为什么还在 |
|------|-----------|
| `AudioPlayer.kt:168` `aacStreamPlayer?.stopPlaying()` | 本 Task 只**新增** `releaseAndJoin()`，`fun release()` 留到 Task 4 才删，它的函数体仍在调弃用 API |
| `AudioPlayer.kt:169` `opusStreamPlayer?.stopPlaying()` | 同上 |
| `AacStreamPlayer.kt:223` `it.release()` | 弃用函数 `stopPlaying()` 自身的实现体 |
| `OpusStreamPlayer.kt:217` `it.release()` | 同上 |
| `AacEncoderWrapper.kt:45` `encoder.release()` | 非挂起覆写 `release()` 的实现体 |
| `OpusEncoderWrapper.kt:45` `encoder.release()` | 同上 |

这六处在 Task 4 随函数一起删除。**若警告数不是 6，停下来查清原因再继续。**
（早期版本此处写的是 4，漏算了 `AudioPlayer.release()` 里那两行——它要到 Task 4 才消失。）

- [ ] **Step 7: 提交（仅用户明确授权时）**

```bash
git add audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt \
        audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt \
        audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt \
        audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt
git commit -m "refactor(audio): move internal teardown onto the suspend API

Two rollback paths cannot await a deterministic release: the stream players'
runs inside the synchronized block that guards their decoder state, and
MicRecorder's sits behind the non-suspend startRecord() entry point. Both now
launch it on NonCancellable instead, which survives the ioScope.cancel() that
follows, and both wrap the body because neither scope has an exception handler
to catch what NonCancellable's missing parent would otherwise let escape."
```

---

## Task 3：迁移 demo 调用方

**Files:**
- Create: `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt`
- Create: `demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt`
- Modify: `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/sender/AudioSender.kt`
- Modify: `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/receiver/AudioReceiver.kt`
- Modify: `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt`
- Modify: `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/ADPCMActivity.kt`

**Interfaces:**
- Consumes: `AudioPlayer.releaseAndJoin()`（Task 2）、`MicRecorder.stopRecordAndJoin()`（已存在）
- Produces: `SuspendTeardownGate.run(block: suspend () -> Unit)`；`AudioSender.stop()` 改为
  suspend；`AudioReceiver.stopServer()` 改为 suspend

> **先读这一段。** `stop()` / `stopServer()` 各有**两个非挂起调用方**在同一个文件里，是 netty
> 监听器的接口实现，签名改不了：
>
> | 文件 | 回调 | 调用 |
> |------|------|------|
> | `AudioSender.kt:71` | `onDisconnected()` | `stop()` |
> | `AudioSender.kt:77` | `onFailed()` | `stop()` |
> | `AudioReceiver.kt:96` | `onClientDisconnected()` | `stopServer()` |
> | `AudioReceiver.kt:102` | `onStartFailed()` | `stopServer()` |
>
> 不先处理它们，本 Task 结束时会报四条
> `Suspend function 'stop' should be called only from a coroutine or another suspend function`。
> Step 3 的 `cleanupScope` 负责从非挂起回调发起关闭，Step 1–2 的门闩负责保证
> 它与 Activity 路径并发时只执行一次。

- [ ] **Step 1: 先写 `SuspendTeardownGate` 失败测试**

新建 `demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt`，
使用 `runTest`、`async`、`CompletableDeferred` 覆盖：

1. 两个并发 `run` 只执行一次 block，第二个在首个完成前不返回。
2. 首个 block 抛出的异常会传播给后续调用者。
3. 取消首个调用者后，已开始的 block 仍能完成，等待者在完成后返回。

测试文件完整内容：

```kotlin
package com.leovp.demo.basiccomponents.examples.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
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

        assertSame(expected, first)
        assertSame(expected, second)
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
}
```

先运行：

```bash
./gradlew --rerun-tasks :demo:testDevDebugUnitTest \
  --tests '*SuspendTeardownGateTest'
```

预期：因 `SuspendTeardownGate` 尚未存在而编译失败。

- [ ] **Step 2: 实现一次性关闭门闩并让测试转绿**

新建 `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt`：

```kotlin
package com.leovp.demo.basiccomponents.examples.audio

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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

重跑 Step 1 的定向测试，预期三个用例全部通过。

- [ ] **Step 3: 给 `AudioSender` 与 `AudioReceiver` 各加关闭门闩与 `cleanupScope`**

两个文件都在 `ioScope` 声明之后追加（`AudioSender.kt:35` / `AudioReceiver.kt:47` 附近）：

```kotlin
    /**
     * Teardown runs here rather than on [ioScope]: stop() terminates ioScope itself, and the
     * non-suspend netty listener callbacks need a scope that outlives it. The handler is what
     * keeps a failed teardown off the platform default handler.
     *
     * Deliberately never cancelled. Cancelling it from stop() would cancel the very coroutine
     * running stop(), which is the situation this separate scope exists to avoid. Nothing else
     * holds a reference to it, so it becomes collectable together with its owner once the
     * teardown it is running has finished.
     */
    private val cleanupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                LogContext.log.e(TAG, "Audio sender teardown failed", error)
            }
    )
```

`AudioReceiver` 版本的日志文案改为 `"Audio receiver teardown failed"`，其余相同。

四处回调随之改写：

```kotlin
// AudioSender.kt:71 / :77
cleanupScope.launch { stop() }

// AudioReceiver.kt:96 / :102
cleanupScope.launch { stopServer() }
```

两个文件都新增 import：`kotlinx.coroutines.CoroutineExceptionHandler`、
`kotlinx.coroutines.SupervisorJob`。
同时两个类都新增：

```kotlin
private val teardownGate = SuspendTeardownGate()
```

并 import `com.leovp.demo.basiccomponents.examples.audio.SuspendTeardownGate`。

- [ ] **Step 4: `AudioSender` 队列改为 Channel，`stop()` 改为可等待的一次关闭**

文件 `AudioSender.kt`：

1. 删除 `java.util.concurrent.ArrayBlockingQueue` import，新增
   `kotlinx.coroutines.channels.Channel`、`kotlinx.coroutines.Job` 和
   `kotlinx.coroutines.cancelAndJoin`。
2. 两个队列改为 `Channel<ByteArray>(64)`。`onRecording()` / `onReceivedData()` 的
   `offer(data)` 改为 `trySend(data)`，保留队列满时丢当前帧的非阻塞语义。原有
   `recAudioQueue.size` 日志不能原样保留（Channel 无对等公开属性）；改为记录
   `trySend` 的 `isSuccess` / `isFailure`，不为日志引入额外计数器。
3. `sendRecAudioThread()` 和 `startPlayThread()` 分别用 `for (data in recAudioQueue)` /
   `for (data in receiveAudioQueue)` 消费；不再调用不可取消的 `take()`。
4. 把旧 `stop()`：

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

改为：

```kotlin
    suspend fun stop() {
        teardownGate.run {
            try {
                micRecorder?.stopRecordAndJoin()
            } finally {
                try {
                    recAudioQueue.close()
                    receiveAudioQueue.close()
                } finally {
                    try {
                        // Both workers receive from coroutine Channels, so cancellation wakes them.
                        ioScope.coroutineContext[Job]?.cancelAndJoin()
                    } finally {
                        try {
                            audioPlayer?.releaseAndJoin()
                        } finally {
                            try {
                                senderClient?.disconnectManually()
                            } finally {
                                senderClient?.release()
                            }
                        }
                    }
                }
            }
        }
    }
```

保留 `kotlinx.coroutines.launch`；删除已无调用的 `kotlinx.coroutines.cancel` 和
`kotlinx.coroutines.ensureActive`。关闭时先停录音并关闭两个 Channel，使迟到回调的
`trySend()` 失败，再等 worker 退出；因此 `AudioTrack.write()` 不会与 release 并发，
也不会在无消费者时滞留音频帧或留下阻塞的 IO 线程。

- [ ] **Step 5: `AudioReceiver.stopServer()` 改为可等待的一次关闭**

文件 `AudioReceiver.kt`，`fun stopServer()`（`:179`）改为：

```kotlin
    suspend fun stopServer() {
        teardownGate.run {
            try {
                micRecorder?.stopRecordAndJoin()
            } finally {
                try {
                    // Both loops poll and delay, so this cancellation and join is bounded.
                    ioScope.coroutineContext[Job]?.cancelAndJoin()
                } finally {
                    try {
                        // The historical SIGABRT note applies to the PCM path: no writer survives.
                        audioPlayer?.releaseAndJoin()
                    } finally {
                        receiverServer?.stopServer()
                    }
                }
            }
        }
    }
```

**两个关键点：**

1. 不能只取消 scope 而不等待；那样 `startPlayThread()` 的 `audioPlayer?.play(it)` 可能仍在
   整个 `releaseAndJoin()` 期间继续跑，PCM 路径下 `write()` 会撞上 `AudioTrack.release()`——正是
   历史 SIGABRT 注释描述的场景。
2. 先停录音再 `cancelAndJoin()`，保证没有新帧进入；关闭跑在 `teardownGate` 的
   `NonCancellable` 上，不会被调用方中途取消。
3. 两个关闭体都用嵌套 `try/finally` 保证前一项失败时仍尝试后续资源；最终异常
   由 `SuspendTeardownGate` 共享给并发等待者，并由调用方或 `cleanupScope` handler 记录。

import 变化：新增 `kotlinx.coroutines.Job`、`kotlinx.coroutines.cancelAndJoin`；
**删除 `kotlinx.coroutines.cancel`**（`:19`）——`:182` 是全文唯一的 `cancel()` 调用点，替换后
该 import 失效，detekt 零容忍会直接失败。

- [ ] **Step 6: `AudioActivity` 只换方法名**

文件 `AudioActivity.kt`。该文件已有 `launchCleanup(name) { block }`（`:378`，`block` 是
`suspend () -> Unit`），跑在带 `SupervisorJob` + `CoroutineExceptionHandler` 的 `ioScope`（`:76`）
上，`onDestroy()` 用 `ioScopeJob.complete()`（`:374`）而非 `cancel()`，正是为了让清理跑完。
**不要引入 `lifecycleScope.launch(NonCancellable)`**——那会绕开这套机制。

`:343-345` 的 `launchCleanup("Stop PCM playback") { ... }` 块内，把 `player?.release()` 改为
`player?.releaseAndJoin()`。

`:362` 与 `:363` 两行**不动**：

```kotlin
        launchCleanup("Stop audio receiver") { audioReceiver?.stopServer() }
        launchCleanup("Stop audio sender") { audioSender?.stop() }
```

两者在 Step 4/5 变成 suspend 后，`launchCleanup` 的 `suspend () -> Unit` 直接兼容。

- [ ] **Step 7: 让 `ADPCMActivity` 的播放 Job 拥有 player**

文件 `ADPCMActivity.kt`。删除 `private var player: AudioPlayer?`、`kotlin.concurrent.thread`
和两处分散的 release，新增：

```kotlin
private var playbackJob: Job? = null
```

点击播放时先 `playbackJob?.cancel()`，再把原 `thread { ... }` 改为：

```kotlin
playbackJob = lifecycleScope.launch(Dispatchers.IO) {
    val currentPlayer = AudioPlayer(this@ADPCMActivity, decoderInfo, AudioType.PCM)
    try {
        runCatching {
            AdpcmImaQtDecoder(decoderInfo.sampleRate, decoderInfo.channelCount).use { decoder ->
                val inFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
                val musicBytes = FileInputStream(inFile).use { it.readBytes() }
                val chunkSize = decoder.chunkSize()
                require(musicBytes.size % chunkSize == 0) {
                    "ADPCM file contains an incomplete trailing chunk."
                }
                for (i in musicBytes.indices step chunkSize) {
                    ensureActive()
                    val chunk = musicBytes.copyOfRange(i, i + chunkSize)
                    currentPlayer.play(decoder.decode(chunk))
                }
            }
        }.onFailure {
            if (it is CancellationException) throw it
            LogContext.log.e(ITAG, "Unable to decode ADPCM audio.", it)
        }
    } finally {
        withContext(NonCancellable) { currentPlayer.releaseAndJoin() }
    }
}
```

保留原有的计时日志时，将其放回循环内对应位置。`AudioPlayer` 必须在 Job 体内构造；
否则 Job 在首次调度前被取消时，`finally` 不一定开始，已构造的 player 会泄漏。
新增 `lifecycleScope`、`CancellationException`、`Dispatchers`、`Job`、`NonCancellable`、
`ensureActive`、`launch`、`withContext` import。

`onDestroy()` 只做 `playbackJob?.cancel()` 并置 null，不直接 release。如此播放循环与释放位于同一
Job 的 `try/finally`，不会出现 `write()` 与 `release()` 并发；连续点击时旧 Job 也只释放自己
捕获的 player。

- [ ] **Step 8: 验证**

```bash
./gradlew --continue --rerun-tasks :demo:testDevDebugUnitTest \
  :demo:compileDevDebugKotlin :demo:detekt :demo:ktlintCheck
```

本地 Codex 实施时由 Codex 执行；Claude Code 只能静态核对命令、路径和预期结果，必须将
未执行的 Gradle 项标记为“未验证”。预期：`BUILD SUCCESSFUL`，门闩的 3 个新用例通过，
demo 侧无本次 API 的弃用警告，且无 suspend 函数调用位置错误。

- [ ] **Step 9: 提交（仅用户明确授权时）**

```bash
git add demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt \
        demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/sender/AudioSender.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/receiver/AudioReceiver.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/ADPCMActivity.kt
git commit -m "refactor(demo): await audio teardown instead of firing it off

AudioReceiver cancelled its scope before releasing the player, which would have
cancelled the suspend teardown before it finished. Cancelling last instead would
have let the playback loop keep calling AudioPlayer.play() throughout the
release, so the scope is joined before the release rather than after it.

AudioSender used blocking queues that ignored coroutine cancellation. Both
workers now consume Channels, so teardown can cancel and join them before
releasing the player.

Both classes share concurrent Activity and netty shutdown through a one-shot
gate. ADPCM playback now owns its player inside one lifecycle job, so playback
and release cannot race."
```

---

## Task 4：删除弃用 API 并记录破坏性变更

**Files:**
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/iters/AudioEncoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/iters/AudioDecoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/AacEncoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/OpusEncoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/decoderWrapper/CompressedPcmDecoderWrapper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/base/StreamPlayerStopper.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt`
- Modify: `audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt`
- Modify: `audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronousTest.kt`
- Modify: `audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronousTest.kt`
- Modify: `audio/src/test/kotlin/com/leovp/audio/base/StreamPlayerStopperTest.kt`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: Task 1–3 的全部成果
- Produces: 公开面只剩 suspend 关闭 API

> **本 Task 必须连测试一起改。** 三个测试文件共 20 处调用了这里要删的 API，只改 `src/main`
> 会让 `:audio:testDebugUnitTest` 编译失败。Step 8 专门处理。

- [ ] **Step 1: 从两个接口删除 `release()`**

`AudioEncoderWrapper.kt` 删除 `fun release()` 一行；`AudioDecoderWrapper.kt` 删除 `fun release()` 一行。
删除后 `AudioEncoderWrapper` 应只剩 `encode()` 与 `suspend releaseAndJoin()`，
`AudioDecoderWrapper` 只剩 `decode()` 与 `suspend releaseAndJoin()`。

- [ ] **Step 2: 从四个 wrapper 实现删除 `release()` 覆写**

`AacEncoderWrapper.kt` 与 `OpusEncoderWrapper.kt` 删除整个：

```kotlin
    override fun release() {
        runCatchingPreservingCancellation { encoder.release() }
            .onFailure { LogContext.log.e(TAG, "AAC encoder release failed", it) }
    }
```

（OPUS 版的日志文案是 "OPUS encoder release failed"。）

`CompressedPcmEncoderWrapper.kt` 与 `CompressedPcmDecoderWrapper.kt` 删除 `override fun release() { }`。

**删除后逐文件检查** `runCatchingPreservingCancellation`、`LogContext`、`TAG` 是否还有其他使用点，
失效的 import 与私有成员必须一并删除（detekt `maxIssues = 0`）。

- [ ] **Step 3: 删除 `BaseMediaCodec` 的弃用 `release()`**

文件 `BaseMediaCodec.kt`，删除整个（约 `:229-245`）：

```kotlin
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

`markReleasing()`、`releaseCodecOnce()`、`codecJob`、`lifecycleState` 仍被 `releaseAndJoin()` 使用，
**不要一起删**。全仓已确认无子类覆写 `BaseMediaCodec.release()`。

- [ ] **Step 4: 删除两个 stream player 的 `stopPlaying()`**

`AacStreamPlayer.kt`（约 `:212-224`）与 `OpusStreamPlayer.kt`（约 `:206-218`）删除整个：

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

- [ ] **Step 5: 删除 `StreamPlayerStopper.stop()`**

文件 `StreamPlayerStopper.kt`，删除整个：

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

`prepareStop()` 与 `releaseAudioTrack()` 仍被 `stopAndJoin()` 使用，**保留**。

- [ ] **Step 6: 删除 `AudioPlayer.release()`**

文件 `AudioPlayer.kt`，删除整个（约 `:163-170`）：

```kotlin
    fun release() {
        LogContext.log.w(TAG, "release()")
        audioTrackPlayer.release()

        decoderWrapper?.release()
        aacStreamPlayer?.stopPlaying()
        opusStreamPlayer?.stopPlaying()
    }
```

Task 2 新增的 `suspend fun releaseAndJoin()` 保留。

- [ ] **Step 7: 删除 `MicRecorder` 的非挂起释放路径**

文件 `MicRecorder.kt`：

1. 删除 `fun stopRecord()`（约 `:251-258`）
2. 删除 `private fun finishRecorderRelease(stopSucceeded: Boolean)`（约 `:277-289`）
3. `:67` 的 KDoc 把 `Guards [finishRecorderRelease] so ...` 改为
   `Guards [finishRecorderReleaseAndJoin] so ...`，否则 KDoc 链接指向已删除的函数

保留 `suspend fun stopRecordAndJoin()` 与 `private suspend fun finishRecorderReleaseAndJoin()`。

- [ ] **Step 8: 迁移受影响的现有单元测试**

删掉上面那些 API 之后，三个测试文件会编译不过。逐条处理（依据 spec §6.2）：

**A. `BaseMediaCodecAsynchronousTest.kt`（18 处 `subject.release()`）**

1. **纯作收尾用的调用**（`:35 :91 :108 :124 :153 :182 :200 :234 :255 :284 :387 :405`）——非
   `runTest` 的用例改为 `runBlocking { subject.releaseAndJoin() }`，已经是 `runTest` 的直接改成
   `subject.releaseAndJoin()`。`releaseAndJoin()` 的
   `require(codecJob !== currentCoroutineContext()[Job])` 不会误伤：`runBlocking` 建的是另一个 Job。
2. **`assertReleaseWaitsForCallback()` 私有辅助函数**（`:500` 的
   `releaseExecutor.submit { subject.release() }`）——改为
   `releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }`。一处改动覆盖 3 个用例
   （`release waits for active format callback`、`release waits for active error callback`、
   `release waits for an active asynchronous input callback`）。断言依然成立：`releaseAndJoin()`
   同样要过 `releaseCodecOnce()` 的 `withCodecOperationLock`。
3. **`:62` / `:448`**（`release waits for an active asynchronous input callback`、
   `release racing start cleans partial codec and keeps session terminal`）——同 2 的写法。
4. **`:419`**（`release before start makes the one-shot session terminal`）——同 1 的写法。
5. **`:296` `successful release remains exactly once across both release APIs`**——"两个 API"的
   前提随删除消失。改为连续三次 `subject.releaseAndJoin()`，断言 `releaseHookCount == 1` 且
   `verify(exactly = 1) { mediaCodec.release() }`，用例更名为
   `` `successful release remains exactly once across repeated calls` ``。幂等性覆盖保留。

**B. `BaseMediaCodecSynchronousTest.kt`**

`:24` `` `legacy release waits for the active codec iteration` ``——`:38` 改为
`releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }`，`:41` 的断言消息里的
`release()` 改成 `releaseAndJoin()`，用例更名去掉 `legacy`。

**C. `StreamPlayerStopperTest.kt`**

`:21-51` `` `legacy stop releases resources in teardown order` ``——被测的
`StreamPlayerStopper.stop()` 整体删除，**此用例一并删除**。teardown 顺序的覆盖不丢：
`suspending stop propagates cancellation after common cleanup`（`:53`）与
`suspending stop logs ordinary decoder failures without throwing`（`:90`）断言的是同一个
`events` 序列。删除后检查 `io.mockk.verify` 之外的 import 是否还有使用点（`assertFalse`、
`CoroutineScope`、`Job` 等仍被其余用例使用，逐个确认再删）。

**D. 清掉随之失效的 `@Suppress("DEPRECATION")`**

`BaseMediaCodecAsynchronousTest.kt` 19 处（`:26 :42 :80 :97 :113 :130 :167 :188 :216 :241 :262
:294 :311 :359 :377 :394 :413 :428 :482`）与 `BaseMediaCodecSynchronousTest.kt:22` 1 处，全是
为调用这些弃用 API 而加的，随之失效，全部删除。

**注意**：`audio/src/main/kotlin/com/leovp/audio/exts/AudioExt.kt:42,56` 的两处
`@Suppress("DEPRECATION")` 针对的是 Android 平台 API 弃用，**与本次无关，不要动**。

- [ ] **Step 9: 写 CHANGELOG**

`CHANGELOG.md` 的 `### 变更 (Changed)` 节开头插入：

```markdown
- **audio 关闭 API 统一为挂起**：删除全部非挂起的关闭入口，调用方必须在协程中等待关闭完成。
  - **破坏性变更**：以下 API 已删除，无兼容垫片——`AudioEncoderWrapper.release()`、
    `AudioDecoderWrapper.release()`、`BaseMediaCodec.release()`、`AacStreamPlayer.stopPlaying()`、
    `OpusStreamPlayer.stopPlaying()`、`AudioPlayer.release()`、`MicRecorder.stopRecord()`。
    迁移方式：`X.release()` → `X.releaseAndJoin()`，`X.stopXxx()` → `X.stopXxxAndJoin()`。
    调用方必须在自己管理的协程中等待关闭完成；不要盲目使用
    `lifecycleScope.launch(NonCancellable)`，应复用已有 teardown scope，或使用带
    `SupervisorJob` 与 `CoroutineExceptionHandler` 的专用 scope。仅把必要的最终释放段
    放入 `withContext(NonCancellable)`。
  - **破坏性变更**：`AudioEncoderWrapper.releaseAndJoin()` 不再提供默认实现，
    `AudioDecoderWrapper` 新增同名抽象方法。自行实现这两个接口的下游必须补上该方法——此前的
    默认实现会静默退回非确定性关闭。
  - 初始化失败回滚（两个 stream player 与 `MicRecorder.startRecord()`）改为在
    `NonCancellable` 协程上异步释放，因其调用点位于锁内或非挂起公开入口，无法等待。
    关闭路径不受影响，仍然完全确定性。
```

- [ ] **Step 10: 验证**

```bash
./gradlew --continue --rerun-tasks :audio:testDebugUnitTest :audio:detekt :audio:ktlintCheck \
  :demo:testDevDebugUnitTest :demo:compileDevDebugKotlin :demo:detekt :demo:ktlintCheck
```

预期：`BUILD SUCCESSFUL`，**弃用警告 0 条**。用以下命令自查：

```bash
# 1) 为调用本次弃用 API 而加的抑制必须清零（全部在测试里）
grep -rn '@Suppress("DEPRECATION")' audio/src/test ; echo "(空=符合要求)"

# 2) 本次没有新增任何抑制。audio/src/main 与 demo/src/main 里剩下的 13 处全部是 Android
#    平台 API 弃用（AudioExt 2、WifiActivity 4、BluetoothClientActivity 3、
#    BluetoothServerActivity 2、MainActivity 1、SimpleAdapter 1），与本次无关，不要动。
grep -rn '@Suppress("DEPRECATION")' audio/src/main demo/src/main | wc -l ; echo "(应为 13)"

# 3) 被删的符号确实删净了
grep -rn "fun stopPlaying()\|fun stopRecord()\|fun finishRecorderRelease(" audio/src/main \
  ; echo "(空=已删净)"
grep -n "fun stop(" audio/src/main/kotlin/com/leovp/audio/base/StreamPlayerStopper.kt \
  ; echo "(空=已删净，只应剩 stopAndJoin)"

# 4) `fun release()` 应只剩 AudioTrackPlayer 一处 —— spec §9 明确不动它（它直接包装
#    AudioTrack，没有 worker 需要 join）。出现第二处就是漏删。
grep -rn "fun release()" audio/src/main ; echo "(应只有 AudioTrackPlayer.kt 一行)"
```

**注意**：早期版本这里写的是 `grep -rn '@Suppress("DEPRECATION")' audio/src demo/src` 与
`grep -rn "fun release()..." audio/src/main`，两条都**永远不可能为空**——前者会扫到 13 处无关的
Android 平台 API 抑制，后者会扫到按设计保留的 `AudioTrackPlayer.release()`。

- [ ] **Step 11: 提交（仅用户明确授权时）**

```bash
# audio/src 而非 audio/src/main：Step 8 的测试迁移必须与删除 API 同一个提交，
# 否则这个提交单独 checkout 时测试编译不过。
git add audio/src/main/kotlin/com/leovp/audio/base/iters/AudioEncoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/iters/AudioDecoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/AacEncoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/OpusEncoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/base/decoderWrapper/CompressedPcmDecoderWrapper.kt \
        audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt \
        audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt \
        audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt \
        audio/src/main/kotlin/com/leovp/audio/base/StreamPlayerStopper.kt \
        audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt \
        audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt \
        audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronousTest.kt \
        audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronousTest.kt \
        audio/src/test/kotlin/com/leovp/audio/base/StreamPlayerStopperTest.kt \
        CHANGELOG.md
git commit -m "refactor(audio)!: drop the non-suspend teardown API

Deleting these rather than suppressing the warnings was the point: a caller
that cannot wait for a worker to exit cannot know the codec, the AudioTrack or
the file handle is actually gone, and every remaining internal caller has been
moved over in the previous commits.

BREAKING CHANGE: AudioEncoderWrapper.release(), AudioDecoderWrapper.release(),
BaseMediaCodec.release(), AacStreamPlayer.stopPlaying(),
OpusStreamPlayer.stopPlaying(), AudioPlayer.release() and
MicRecorder.stopRecord() are gone, and AudioEncoderWrapper.releaseAndJoin() no
longer has a default implementation. Implementors of either wrapper interface
must supply one."
```

---

## Task 5：真机验证（不可跳过）

Task 3 已用 JVM 单测覆盖一次性关闭门闩，Task 4 迁移现有 codec 生命周期用例。
但 Channel worker 退出、AudioTrack/AudioRecord/MediaCodec 真实驱动行为与 Activity 生命周期仍需本 Task
的真机验证。**单测 + 编译 + detekt 不等同于媒体行为验证完成。**

**Files:** 无代码改动。发现问题则回到对应 Task 修复。

- [ ] **Step 1: AAC 流播放启停循环**

`AudioActivity` 中启动 AAC 播放 → 停止 → 再启动，循环 5 次以上。
观察：无 native crash、无音频残留、每次都能正常出声。

- [ ] **Step 2: OPUS 流播放启停循环**

同上，走 OPUS 路径（`AudioReceiver.defaultAudioType` 即为 OPUS）。

- [ ] **Step 3: PCM 路径反复进出**

`ADPCMActivity`（`AudioType.PCM`）反复进入退出 Activity 5 次以上。
**重点观察 logcat** 是否出现：

```
releaseBuffer() track ... disabled due to previous underrun
Fatal signal 6 (SIGABRT)
```

这是 `AudioReceiver` 那段历史注释真正约束的路径——AAC/OPUS 路径不涉及 `AudioPlayer` 自己的 track。
若复现，说明 Task 2 Step 5 中 `audioTrackPlayer.release()` 的位置需要调整，回到该步处理。

- [ ] **Step 4: `AudioActivity` 录放组合后退出**

录制 + 播放组合操作后直接退出 Activity，确认 `launchCleanup` 的三处清理都跑完
（看 logcat 中 `launchCleanup` 的日志），无 ANR、无泄漏。

- [ ] **Step 5: 初始化失败回滚**

构造解码器初始化失败（例如向 `AacStreamPlayer.startPlayingStream()` 喂入损坏的 csd0）。
确认：日志出现 `init failed, rolled back`；**不出现** `rollback release failed`；随后正常调用
停止流程不受影响、不崩溃。

- [ ] **Step 6: 录音启动失败回滚**

构造 `MicRecorder.startRecord()` 失败（例如撤销录音权限后调用）。
确认：日志出现 `AudioRecord ...` 失败信息；**不出现** `start rollback release failed`；进程不崩溃。

- [ ] **Step 7: netty 回调触发的关闭路径**

本次新增的 `cleanupScope` 只在这条路径上生效，Activity 侧走不到它。

- `AudioActivity` 连上 sender/receiver 后，**从对端断开**（关掉另一端、或直接断网），让
  `onDisconnected()` / `onClientDisconnected()` 触发 `cleanupScope.launch { stop() }`。
- 确认：日志出现正常的停止序列；**不出现** `Audio sender teardown failed` /
  `Audio receiver teardown failed`；进程不崩；随后 Activity 退出时 `launchCleanup` 的第二次
  关闭等待同一 teardown，不再次调用 AudioTrack/codec/netty release。
- 再试一次连接失败的场景（填一个不可达地址），让 `onFailed()` / `onStartFailed()` 走到。
- **并发交错场景（重点）**：断开对端后**立刻**退出 Activity，让 netty 回调的
  `cleanupScope.launch { stop() }` 与 `launchCleanup { audioSender?.stop() }` 真正同时在飞。
  确认只出现一套实际 release 序列，后续调用等待 `SuspendTeardownGate` 的同一结果。
  下列是门闩必须消除、不得再依赖幂等推测规避的竞态：

  - `AudioTrackPlayer.release()` 开头 `if (audioTrack.state == STATE_UNINITIALIZED) return`，
    而 `audioTrack.release()` 后状态恰为 UNINITIALIZED，所以**顺序**重入在守卫处就返回了。
    但它是裸的 check-then-act，没有 CAS 的原子性：两条路径真正并发时可以双双通过这个 check，
    一起走到 `audioTrack.release()`。入口在 `StreamPlayerStopper.releaseAudioTrack()`——它自身
    也没有 once-guard，同一实例上并发 `stopAndJoin()` 就是两次 `audioTrackPlayer.release()`。
    要盯的就是本 Step 的交错场景，且落点是 **OPUS** 实例：两条路径打在同一个 `AudioReceiver`
    上，经同一个 `AudioPlayer`（`defaultAudioType = OPUS`）到同一个 `AudioTrackPlayer`。
    `ADPCMActivity` 不接 netty，其 player 所有权与取消竞态由后面的独立场景验证。
  - `BaseMediaCodec` 由 `releaseCodecOnce()` 里的 `codecReleased` CAS 把关。**不是
    `markReleasing()`**——它只在已 `RELEASED` 时短路，`RELEASING` 态下返回 `true`，并发重入
    挡不住；`mediaCodec.release()` 恰好一次是那个 CAS 给的保证，重构时不要把它当冗余删掉。
  - `MicRecorder` 由 `released` 的 CAS 加 `releaseCompleted.await()` 把关。
  - netty 侧：`BaseNettyClient.disconnectManually()` 与 `release()` 各有 `synchronized(this)`
    的状态机守卫，遇 DISCONNECTING / RELEASING / UNINITIALIZED 直接返回；
    `BaseNettyServer.stopServer()` 有 UNINITIALIZED 守卫，但同样是 check-then-set 非原子；
    其后的 channel close 与两个 EventLoopGroup shutdown 各有 `runCatching` 兜底，而紧跟守卫的
    `defaultServerInboundHandler?.release()` 并不在 `runCatching` 内，靠的是随后置 null 加安全
    调用。属既有行为，不在本次范围。
  - `AudioPlayer.releaseAndJoin()` **自身没有守卫**，靠的是它委托的每一步各自幂等。

  真机上要确认门闩确实让第二个调用等待而非再次进入，不能只信单测推导。
- **worker 退出**：连续进出 `AudioActivity` 5 次以上，确认 `AudioSender` 的两个 Channel
  worker 都在 stop 返回前退出，不累积阻塞的 `DefaultDispatcher-worker-*` 线程。
- **ADPCM 所有权**：播放中立即退出，再连续点击播放。确认旧 Job 只释放自己的 player，
  不释放新 Job 的 player，不出现 AudioTrack write/release 并发。

- [ ] **Step 8: 把验证结果记入文档**

在 spec `00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md`
末尾追加一节（接在 §11 之后），如实记录每项的通过/失败与设备型号、Android 版本。失败项必须
记录，不得省略。

```bash
# 仅在用户明确授权提交时执行：
git add 00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md
git commit -m "docs: record device verification for the audio teardown change"
```

---

## 附录：完成标准

全部满足才算完成：

1. `./gradlew --rerun-tasks :audio:testDebugUnitTest :audio:detekt :audio:ktlintCheck :demo:testDevDebugUnitTest :demo:compileDevDebugKotlin :demo:detekt :demo:ktlintCheck` 全绿
2. 弃用警告 **0 条**
3. `audio/src/test` 中 **无** `@Suppress("DEPRECATION")`；`audio/src/main` 与 `demo/src/main`
   中仍为原有的 13 处 Android 平台 API 抑制，**未新增、未误删**
4. `audio/src/main` 中 **无** `fun stopPlaying()` / `fun stopRecord()` /
   `fun finishRecorderRelease(` / `StreamPlayerStopper.stop(`；`fun release()` 只剩
   `AudioTrackPlayer.kt` 一处（spec §9 明确保留）
5. Task 5 的真机验证全部执行并记录（失败项也要记），包含 Channel worker
   退出、并发关闭门闩和 ADPCM Job 所有权
6. `CHANGELOG.md` 已记录破坏性变更与迁移方式
7. `:audio:testDebugUnitTest` 能编译并通过——Task 4 Step 8 的测试迁移已完成，且与删除 API
   在同一个提交里
8. `SuspendTeardownGateTest` 覆盖并发只执行一次、等待同一结果、异常传播与
   调用者取消
9. 交付报告分开列出 Claude Code 静态分析、Codex 本机单测/构建、真机结果与
   未验证项；未经明确授权没有提交或推送
