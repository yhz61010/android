# audio 模块弃用关闭 API 移除 实施计划

> **实施状态（2026-09-18 第五次复核）：本计划尚未执行。**
> 历史提交 `f27449db0` 与 `484d2c3d9` 虽然使用了像代码已实施的 message，
> 实际 diff 只包含这两份 Markdown 文档。实施者必须从 Task 1 开始，不得把该历史
> 当成源码、测试或真机验证证据；未经明确授权不重写已在远端的历史。
>
> **另有一处前提在本计划之外被修掉了**：提交 `5672ecbbd` 已用
> `runInterruptible { queue.take() }` 让 `AudioSender` / `AudioReceiver` 的 worker 可被取消，
> 并把 `AudioReceiver` 的轮询循环也改成了阻塞取数。因此 Task 3 的 Channel 改造是**简化，
> 不是缺陷修复**，相关说明、验证项与提交信息均已据此改写，详见 spec §14。
>
> 行号以 `9eec9807f` 为准，仅作辅助定位；**函数名与调用结构才是权威锚点**，实施前请以
> 当时的 checkout 复核。
>
> **第十次复审（2026-09-20）是最新一轮，结论见 spec §19。** 它复核了第九次复审的全部结论
> （全部成立，引用逐行精确），并新发现一个 HIGH 与两个 MEDIUM：
>
> - **HIGH：`audioPlayer?.stop()` 与 `ownerJob?.cancelAndJoin()` 被写进同一个 `teardownSteps`
>   step。** 两者共用一个 `try`，前者抛出即跳过 join，而下一步照常 `releaseAndJoin()`——worker
>   仍在 `play()` 的同时 release 掉 player，正是本方案要堵的那个竞态。这当场取消了
>   `teardownSteps()` 被引入的唯一理由（"某一步失败，后续步骤仍然执行"）。
>   **Task 3 Step 4 与 Step 5 已拆成两个 step。**
> - **MEDIUM：门闩在 `catch (Throwable)` 内用 `!is CancellationException` 判型**，与仓库既有的
>   双 catch 子句写法（`StreamPlayerStopper.kt:44-45`、`AudioActivity.kt:391-393`）相反，且正落在
>   detekt `InstanceOfCheckForException`（`10-configs/detekt.yml:242`）的形状上（当前 `detekt`
>   未启用 type resolution 故不报）。**Task 3 Step 2 已改为两个 catch 子句。**
> - **MEDIUM：门闩的取消失败在 leader 侧同样无人记录。** `launchCleanup` 对取消 rethrow 不记日志，
>   `cleanupScope` 的 handler 也不接收取消；而关闭体跑在 `NonCancellable` 下，此处的取消是一次
>   **真实的关闭失败**。**Task 3 Step 2 的取消分支已加日志**（`SuspendTeardownGate` 因此新增
>   `LogContext` 依赖与 `TAG`）。
> - 另有三条 LOW：spec §4.7.3 有一行 Kotlin 长 101 字符（已折行）；Task 3 Step 7 的删除清单漏了
>   `ADPCMActivity.kt:88` 的 `player = AudioPlayer(...)`（已补）；spec §4.7.4 未说明为何没有
>   "关闭队列"这一步（已补）。
>
> **第九次复审（2026-09-20）的结论见 spec §18。** 它复核了第八次复审的全部结论
> （全部成立），并新发现两个 HIGH，两者出自同一个写法：
>
> ```kotlin
> field = Ctor(...).also { ...; 阻塞调用() }   // 赋值发生在块执行完之后
> ```
>
> - **`AudioReceiver.receiverServer` 在服务器正常运行时恒为 `null`**（`AudioReceiver.kt:191-195`，
>   阻塞调用是永不返回的 `BaseNettyServer.startServer()`）。`stopServer()` 的最后一步是死代码，
>   netty server 从不停止、端口不释放。**Task 3 Step 5 已补赋值提前的改法。**
> - **`AudioSender.senderClient` 在首次连接窗口内同样为 `null`**（`AudioSender.kt:85-92`，
>   阻塞调用是最长 30 s 的 `connect()`），恰好使第八次复审刚定下的"`disconnectManually()`
>   提为第一步"在它要修的场景里空转，第 7 步 `release()` 一并落空。
>   **Task 3 Step 4 已补赋值提前的改法；该改法与 `stop()` 改 suspend 均已于 2026-09-20
>   实施**（见该 Step 内的 ✅ 横幅）。本 Step 剩余：门闩、`teardownSteps()`、`stopping`、
>   自调用守卫、队列改 Channel。
>
> Task 5 Step 7 新增两条**改前必然失败、改后才能通过**的真机项，用来确认修法生效。
>
> **第八次复审（2026-09-18）的结论见 spec §17。** 它改动了本计划的三处代码模板，
> 实施前必须先读：
> - **Task 3 Step 4 / Step 5 的自调用 `require()` 写错了**：身份比较版本
>   （`caller !== ioScope 的 Job`）**永远不会触发**，因为 `ioScope.launch` 的子 Job 永远
>   `!==` 根 Job。改用 `StreamPlayerStopper` 真正使用的**递归后代检查**（spec §4.7.3 更正框）。
> - **`AudioSender` 的关闭顺序变了**：`senderClient?.disconnectManually()` 从最后一步提到
>   **第一步**，并给两个类各加一个 `stopping: AtomicBoolean`——否则 netty 重连会在 teardown
>   期间复活会话，新建一个门闩已拒绝释放的 `AudioPlayer`（spec §4.7.3 H2、§4.7.1）。
> - **Task 3 Step 7 不得删除 `kotlin.concurrent.thread` import**：`onEncodeToADPCMClick()`
>   （`ADPCMActivity.kt:46`）仍在用它。
>
> **第七次复审（2026-09-18）的结论见 spec §16**，其中三处同样改变了代码模板：
> - **spec §15.1 的死等前提被推翻**：`AudioSender` / `AudioReceiver` 恒为 OPUS，走不到 PCM
>   写入路径；`audioPlayer?.stop()` 保留但降级为防御性写法，真正需要"stop 在 join 之前"的是
>   `AudioActivity.stopPcmPlayback()`（Task 3 Step 6 已据此扩写）。
> - **关闭序列不再用嵌套 `try/finally`**，改用平铺的 `teardownSteps()`（Task 3 Step 4 / Step 5）。
> - **`SuspendTeardownGate` 不再把 `CancellationException` 广播给等待者**（Task 3 Step 1/Step 2）。
>
> 但**第七次复审"全部命中、可以直接信任"的自评不成立**（spec §17.2）：它只核了文档对源码的
> *引用*，没核文档自己新写的*方案代码*，上面那条 H1 正是那一轮写进去的。
>
> **行号引用约定**：本计划引 `@Test` 所在行，spec §6.2 引 `fun` 所在行，因此同一用例在两份
> 文档里会差 1 行（如 `:53` / `:54`）。两者都对，对照时注意。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 `audio` 模块全部非挂起的弃用关闭 API，调用方一律改用确定性的 suspend 版本，消除 8 条弃用警告且不使用任何 `@Suppress`。

**Architecture:** 分四批落地，**每一批都可编译和审查**：先补齐 suspend 契约，再迁移 `audio` 内部调用，然后以可测试的一次性关闭门闩迁移 demo，最后删除旧 API。关闭路径全程确定性可等待；启动失败回滚在 `NonCancellable` 上异步执行。`AudioSender` 的阻塞队列改为 Channel（简化而非缺陷修复，见 spec §14），两个网络 demo 类的 Activity/netty 并发关闭共享同一 teardown 结果，`ADPCMActivity` 则由播放 Job 拥有和释放 player。

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
- **关闭序列不得用嵌套 `try/finally` 串联**（第七次复审）：裸 `try/finally` 不记录 suppressed，
  后一步的异常会顶掉前一步的根因；且本仓库 `.claude/rules/common/coding-style.md` 明确要求
  "No deep nesting (>4 levels)"（detekt `NestedBlockDepth` 阈值为 8，工具会放行但规则不放行）。
  一律改用 Task 3 Step 4 给出的 `teardownSteps()` 平铺串联。
- **对自己 `ioScope` 做 `cancelAndJoin()` 的关闭入口必须带自调用 `require()` 守卫**
  （第七次复审），否则自调用即静默死锁。**守卫必须是递归后代检查，不是身份比较**（第八次复审
  更正）：`ioScope.launch` 的子 Job 永远 `!==` 根 Job，身份比较版本是一行死代码。
  照搬 `StreamPlayerStopper.kt:34-38` + `:55-56` 的 `containsJob` 写法，见 spec §4.7.3 的更正框。
- **门闩保证不了"关闭之后没人再建资源"**（第八次复审）：`AudioSender` / `AudioReceiver` 各加一个
  `stopping: AtomicBoolean`，在 `teardownGate.run { }` 之前置位，并在 `start*()` 与 netty 接入回调
  （`onConnected` / `onClientConnected`）开头自查。见 spec §4.7.1。

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
| `audio/.../MicRecorder.kt` | `rollbackFailedInit()` 和 `failRecordStart()` 分别迁移异步回滚；最终删 `stopRecord()` 与 `finishRecorderRelease()` |
| `audio/src/test/.../mediacodec/BaseMediaCodecAsynchronousTest.kt` | 18 处 `subject.release()` 随 API 删除而迁移/重写 |
| `audio/src/test/.../mediacodec/BaseMediaCodecSynchronousTest.kt` | `:38` `subject.release()` 迁移 |
| `audio/src/test/.../base/StreamPlayerStopperTest.kt` | 删除只测 `stop()` 的那个用例 |
| `demo/.../audio/SuspendTeardownGate.kt` | 为 Activity/netty 并发关闭提供一次执行、共享结果的纯 Kotlin 门闩 |
| `demo/.../audio/TeardownSupport.kt` | 同包的两个 internal 顶层工具：`teardownSteps()` 平铺串联并保留根因异常（第七次复审）、`Job.containsJob()` 供自调用守卫做后代检查（第八次复审）。两个关闭入口共用，不留私有副本 |
| `demo/src/test/.../audio/SuspendTeardownGateTest.kt` | 覆盖并发等待、异常传播、调用者取消、关闭体取消不广播（4 个用例） |
| `demo/src/test/.../audio/TeardownSupportTest.kt` | 覆盖 `teardownSteps()` 的失败聚合与 `containsJob()` 的后代判定（第八次复审新增） |
| `demo/.../audio/sender/AudioSender.kt` | 队列改 Channel；加 `cleanupScope`、关闭门闩与 `stopping` 标志；`stop()` 改 suspend，先断连再等 worker 退出；`start()` 在 `connect()` **之前**赋值 `senderClient`（第九次复审 H2） |
| `demo/.../audio/receiver/AudioReceiver.kt` | 加 `cleanupScope`、关闭门闩与 `stopping` 标志；`stopServer()` 改 suspend 并在释放前等待 worker 退出；`startServer()` 在阻塞的 `startServer()` **之前**赋值 `receiverServer`（第九次复审 H1，否则最后一步是死代码） |
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

- [x] **Step 1: 把 `AudioEncoderWrapper.releaseAndJoin()` 的退化默认实现改为抽象**

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

- [x] **Step 2: 给 `AudioDecoderWrapper` 新增挂起变体**

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

- [x] **Step 3: 给 `CompressedPcmEncoderWrapper` 实现挂起变体**

文件 `audio/src/main/kotlin/com/leovp/audio/base/encoderWrapper/CompressedPcmEncoderWrapper.kt`，
在现有 `override fun release() { }` **之后**追加：

```kotlin
    override suspend fun releaseAndJoin() {
        // Nothing to release: compression runs synchronously on the caller's thread, so there is
        // no worker to wait for.
    }
```

- [x] **Step 4: 给 `CompressedPcmDecoderWrapper` 实现挂起变体**

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
- Modify: `audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt`（`rollbackFailedInit`、`failRecordStart`）
- Modify: `audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt`（新增 `releaseAndJoin()`）

**Interfaces:**
- Consumes: `AudioDecoderWrapper.releaseAndJoin()`（Task 1）
- Produces: `AudioPlayer.releaseAndJoin(): Unit`（suspend）——Task 3 的 demo 调用方依赖它

- [x] **Step 1: 改造 `AacStreamPlayer` 的初始化失败回滚**

文件 `audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt`，`initDecoderLocked()` 末尾的
`.onFailure { }` 块（当前 `:154-159`），把

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

- [x] **Step 2: 在 `AacStreamPlayer` 中新增 `releaseDecoderAsync()` 私有辅助函数**

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

- [x] **Step 3: 对 `OpusStreamPlayer` 做同样改造**

文件 `audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt`，`.onFailure { }` 块
（当前 `:147-154`）改为：

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

- [x] **Step 4: 单独改造 `MicRecorder.rollbackFailedInit()`**

文件 `audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt`，当前 `:209-223`。这是构造失败
路径，不能照搬 Step 5：函数已经用 `released.set(true)` 烧掉 one-shot guard，再调
`finishRecorderReleaseAndJoin()` 会在 `releaseCompleted.await()` 上自锁。

将完整函数改为：

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

不得把 `releaseCompleted.complete(Unit)` 留在 `launch` 之后；它是
`finishRecorderReleaseAndJoin()` 中并发等待者观察“释放已真正结束”的屏障，必须位于
encoder 挂起释放后的 `finally`。协程只访问捕获的局部 `failedEncoder` 和已完成
初始化的 `releaseCompleted`；构造器会在 `MicRecorder.kt:184` / `:193` 重抛，因此不得
在协程内访问可能尚未赋值的 `audioRecord`。闭包会保持部分构造的对象直到回滚
完成，随后可回收。

本步即新增（若尚未存在）`kotlinx.coroutines.NonCancellable` 和
`kotlinx.coroutines.launch` import。

- [x] **Step 5: 改造 `MicRecorder.failRecordStart()`**

文件 `audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt`，当前 `:289-295`，把

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

复用 Step 4 已新增的 `kotlinx.coroutines.NonCancellable` 与
`kotlinx.coroutines.launch` import。
`startRecord()` 维持非挂起，公开面不变。

**但这一步改变了 `RecordCallback.onStop` 的交付时机**（第七次复审，spec §4.6/§5）。现状
`finishRecorderRelease()` 同步跑完 `completeRecorderRelease()` → `callback.onStop(false)`，
因此"`startRecord()` 返回 ⇒ 要么在录音、要么已报过停止"这个不变量成立；改成异步之后
`onStop` 可能在 `startRecord()` 返回之后、从 `Dispatchers.IO` 线程到达。签名不变意味着下游
**看不出**这个变化，所以必须在 Task 4 Step 9 的 CHANGELOG 里显式记录。仓库内两个实现
（`AudioSender.kt:107` 与 `AudioReceiver.kt:125` 的 `onStop`）都是空体，不受影响。

- [x] **Step 6: 给 `AudioPlayer` 新增 `releaseAndJoin()`**

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
     *
     * The track this player always builds is released in a finally: StreamPlayerStopper rethrows
     * CancellationException and stopAndJoin() can also fail its own require(), so an exception
     * from the calls above would otherwise leak the one AudioTrack that exists in every session.
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

**`finally` 不是可选的**（第六次复审新增）。`audioTrackPlayer` 在 `AudioPlayer.kt:43` 无条件创建，
而 `StreamPlayerStopper.stopAndJoin()`（`:33`）会重抛 `CancellationException`（`:44-45`），
它的 `require()`（`:36-38`）也会抛 `IllegalArgumentException`。任一前置释放抛出，这个每个会话
都存在的 AudioTrack 就不会被释放，与本方法 KDoc 承诺的"释放它拥有的全部资源"矛盾。
§4.5 关于"三个 track 独立、顺序自由"的论证只说明**顺序**无所谓，不代表**异常路径**安全。

- [ ] **Step 7: 验证**

```bash
./gradlew --continue --rerun-tasks :audio:compileDebugKotlin :audio:detekt :audio:ktlintCheck
```

预期：`BUILD SUCCESSFUL`。弃用警告应从 8 条降到 **6 条**：

| 位置 | 为什么还在 |
|------|-----------|
| `AudioPlayer.kt:168` `aacStreamPlayer?.stopPlaying()` | 本 Task 只**新增** `releaseAndJoin()`，`fun release()` 留到 Task 4 才删，它的函数体仍在调弃用 API |
| `AudioPlayer.kt:169` `opusStreamPlayer?.stopPlaying()` | 同上 |
| `AacStreamPlayer.kt:224` `it.release()` | 弃用函数 `stopPlaying()` 自身的实现体 |
| `OpusStreamPlayer.kt:218` `it.release()` | 同上 |
| `AacEncoderWrapper.kt:45` `encoder.release()` | 非挂起覆写 `release()` 的实现体 |
| `OpusEncoderWrapper.kt:45` `encoder.release()` | 同上 |

这六处在 Task 4 随函数一起删除。**若警告数不是 6，停下来查清原因再继续。**
（早期版本此处写的是 4，漏算了 `AudioPlayer.release()` 里那两行——它要到 Task 4 才消失。）

额外静态检查：

```bash
rg -n 'encodeWrapper\?\.release\(\)' \
  audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt
```

预期只剩 `finishRecorderRelease()` 内的旧调用（当前 `:443`）；
`rollbackFailedInit()` 中不得再有非挂起 encoder 释放。

- [ ] **Step 8: 提交（仅用户明确授权时）**

```bash
git add audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt \
        audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt \
        audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt \
        audio/src/main/kotlin/com/leovp/audio/AudioPlayer.kt
git commit -m "refactor(audio): move internal teardown onto the suspend API

Four rollback paths cannot await a deterministic release: two stream-player
failures run under synchronized decoder state, while MicRecorder can fail in
its constructor or behind the non-suspend startRecord() entry point. They now
launch cleanup on NonCancellable so it survives the ioScope.cancel() that
follows. Each body contains ordinary failures because none of these scopes has
an exception handler."
```

---

## Task 3：迁移 demo 调用方

**Files:**
- Create: `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt`
- Create: `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupport.kt`
- Create: `demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt`
- Create: `demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupportTest.kt`
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
> | `AudioSender.kt:73` | `onDisconnected()` | `stop()` |
> | `AudioSender.kt:79` | `onFailed()` | `stop()` |
> | `AudioReceiver.kt:97` | `onClientDisconnected()` | `stopServer()` |
> | `AudioReceiver.kt:103` | `onStartFailed()` | `stopServer()` |
>
> 不先处理它们，本 Task 结束时会报四条
> `Suspend function 'stop' should be called only from a coroutine or another suspend function`。
> Step 3 的 `cleanupScope` 负责从非挂起回调发起关闭，Step 1–2 的门闩负责保证
> 它与 Activity 路径并发时只执行一次。

- [x] **Step 1: 先写 `SuspendTeardownGate` 失败测试**

新建 `demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt`，
使用 `runTest`、`async`、`CompletableDeferred` 覆盖：

1. 两个并发 `run` 只执行一次 block，第二个在首个完成前不返回。
2. 首个 block 抛出的异常会传播给后续调用者。
3. 取消首个调用者后，已开始的 block 仍能完成，等待者在完成后返回。
4. **首个 block 重抛 `CancellationException` 时，等待者正常返回而不是收到该异常**
   （第七次复审新增，对应 Step 2 的取消处理）。

测试文件完整内容：

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

（`assertIs` / `assertNull` 是第 4 个用例专用；第八次复审把它们并进了上面的 import 块——
此前只在正文里提了一句，照抄代码块的实施者会直接编译失败。）

另需在同一目录新增第二个测试文件
`demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupportTest.kt`，
覆盖 Step 4 抽出的两个 internal 顶层工具（第八次复审新增，spec §6.2 第 5/6 条）：

1. `teardownSteps()`：某一步抛出后**后续步骤仍执行**；最终抛出的是**第一个**失败，
   后续失败以 `suppressed` 附加。
2. `containsJob()`：对**直接子 Job** 与**孙 Job** 都返回 `true`，对无关 Job 返回 `false`。
   这是 H1 更正的回归锁——身份比较版本会让前两个断言失败。
   （`AudioSender` / `AudioReceiver` 需要 Android `Context`，其 `require` 守卫无法在 JVM 单测里
   构造验证；把判定逻辑提成顶层函数正是为了让这条能被覆盖。）

先运行：

```bash
./gradlew --rerun-tasks :demo:testDevDebugUnitTest \
  --tests '*SuspendTeardownGateTest'
```

预期：因 `SuspendTeardownGate` 尚未存在而编译失败。

- [x] **Step 2: 实现一次性关闭门闩并让测试转绿**

新建 `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt`：

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
 * Followers await the same completion and receive the same Throwable instance, except for
 * cancellation: that belongs to the leader's own coroutine and is never republished. There is no
 * gate timeout: timing out here would let a caller proceed while cleanup was still mutating
 * resources. Every operation inside the block must therefore be bounded itself; a stuck operation
 * stalls all followers and must be diagnosed at that resource boundary.
 */
internal class SuspendTeardownGate {
    companion object {
        private const val TAG = "TeardownGate"
    }

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

**两个 catch 子句，不是一个 catch 加 `is` 判型**（第十次复审 M1/M2，spec §4.7.1 的更正框）。
此前写的是 `catch (error: Throwable) { if (error !is CancellationException) failure = error; ... }`，
有两处不妥：

1. catch 块内判型与仓库既有写法相反——`StreamPlayerStopper.kt:44-45` 与
   `AudioActivity.kt:391-393` 对同一问题用的都是两个 catch 子句；而 detekt 的
   `InstanceOfCheckForException`（`10-configs/detekt.yml:242`，`active: true`，只 exclude
   测试目录）正是针对那个形状。当前 `./gradlew detekt` 未配置 `classpath`，跑在无 type
   resolution 模式，该规则不会报——**不要把"今天不报"当成可以这么写的理由**。
2. 关闭体跑在 `NonCancellable` 下，这里收到的 `CancellationException` 不是"调用方被取消"，
   而是一次**真实的关闭失败**，而 leader 侧和 follower 侧一样没人会记录它。所以取消分支
   必须自己打这条日志。

这个门闩不能重置。实施前再确认 `AudioActivity` 每次启动都新建
`AudioSender` / `AudioReceiver`；不得在同一 owner 上执行第二轮 start/stop。
已核实：`AudioActivity.kt:402` / `:407` 每次点击都新建实例，前提成立。后续
调用者会抛出同一个 `Throwable` 实例（**`CancellationException` 除外，见上面的
`catch` 注释**），且门闩层没有超时：这避免了关闭仍在
修改资源时调用方已经继续执行，但也意味着任一底层操作卡住都会使 netty 与
Activity 的所有等待者一起卡住。需要超时时必须在具体资源操作层实现，不得给
`completed.await()` 加超时后把未完成的关闭留在后台。

重跑 Step 1 的定向测试，预期门闩的 4 个用例全部通过（`TeardownSupportTest` 要等 Step 4
抽出 `TeardownSupport.kt` 之后才能转绿）。

- [x] **Step 3: 给 `AudioSender` 与 `AudioReceiver` 各加关闭门闩与 `cleanupScope`**

> **✅ 本 Step 的 `cleanupScope` 部分已于 2026-09-20 在两个类上全部实施，请勿重复添加。**
> 记录见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.4（`AudioReceiver`）
> 与 §4.8（`AudioSender`）。四处 netty 回调也都已改为 `cleanupScope.launch { }`。
> **本 Step 剩余待办**：`teardownGate` 字段本身（`SuspendTeardownGate` 类要先由 Step 1/2 建出来）。

两个文件都在 `ioScope` 声明之后追加（当前 `AudioSender.kt:37` /
`AudioReceiver.kt:48` 附近）：

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
// AudioSender.kt:73 / :79
cleanupScope.launch { stop() }

// AudioReceiver.kt:97 / :103
cleanupScope.launch { stopServer() }
```

两个文件都新增 import：`kotlinx.coroutines.CoroutineExceptionHandler`、
`kotlinx.coroutines.SupervisorJob`。
同时两个类都新增：

```kotlin
private val teardownGate = SuspendTeardownGate()
```

并 import `com.leovp.demo.basiccomponents.examples.audio.SuspendTeardownGate`。

- [x] **Step 4: `AudioSender` 队列改为 Channel，`stop()` 改为可等待的一次关闭**

文件 `AudioSender.kt`：

1. 删除 `java.util.concurrent.ArrayBlockingQueue` import（当前 `:19`），新增
   `kotlinx.coroutines.channels.Channel`、`kotlinx.coroutines.Job` 和
   `kotlinx.coroutines.cancelAndJoin`。`AudioSender` 没有 import `io.netty.channel.Channel`，
   不存在同名冲突；`AudioReceiver` 有，所以它的队列本次不改（spec §4.7.4）。
2. 两个队列改为 `Channel<ByteArray>(64)`。`onRecording()` / `onReceivedData()` 的
   `offer(data)` 改为 `trySend(data)`，保留队列满时丢当前帧的非阻塞语义。原有
   `recAudioQueue.size` 日志不能原样保留（Channel 无对等公开属性）；改为记录
   `trySend` 的 `isSuccess` / `isFailure`，不为日志引入额外计数器。
3. `sendRecAudioThread()` 和 `startPlayThread()` 不只是把 `take()` 机械换成 `for`。
   **先对齐现状**：`5672ecbbd` 之后这两个函数是 `while (true) { ensureActive();
   val d = runInterruptible { queue.take() }; ... }`，`take()` 已被 `runInterruptible`
   包住因而可取消（见 spec §14.1）。改用 Channel 后，`ensureActive()` 与
   `runInterruptible` 都不再需要，`for` 的 `receive` 挂起点本身就是取消点。
   必须完整改成下面的函数体，保留发送路径原有的逐帧异常隔离，避免一次瞬时网络
   失败杀死整个 worker：

```kotlin
private fun sendRecAudioThread() {
    ioScope.launch {
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

`for` 的 `receive` 挂起点位于 `runCatching` 之外，所以 scope 取消仍能退出 worker。
`startPlayThread()` 现状本就没有外层逐帧 `runCatching`，且 `AudioPlayer.play()` 内部已记录
普通 `Exception`，不在这里新增重复包裹。
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
        // stop() cancel-and-joins ioScope, so it can never run on ioScope or any of its children.
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
                // release. disconnectManually() sets the flag doRetry() bails out on.
                { senderClient?.disconnectManually() },
                // Stop producing new frames.
                { micRecorder?.stopRecordAndJoin() },
                // Close the channels so a late netty callback's trySend() fails instead of
                // parking frames behind a consumer that is about to exit.
                {
                    recAudioQueue.close()
                    receiveAudioQueue.close()
                },
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
                { senderClient?.release() },
            )
        }
    }
```

**第 4 步与第 5 步必须是两个独立的 step**（第十次复审 H1，spec §4.7.3 的更正框）。此前的模板
把 `audioPlayer?.stop()` 与 `ownerJob?.cancelAndJoin()` 写进了同一个 lambda，于是两者共用一个
`try`：`stop()` 一旦抛出，`cancelAndJoin()` 被跳过，而紧接着的 `audioPlayer?.releaseAndJoin()`
照常执行——worker 仍在 `play()` 的同时 release 掉 player，**正是本方案花最大篇幅要堵的那个竞态**。
合并写法等于当场取消了 `teardownSteps()` 被引入的唯一理由。

今天不会触发：`AudioPlayer.stop()`（`AudioPlayer.kt:158`）只委托 `AudioTrackPlayer.stop()`
（`:270-283`），后者全程被 `runCatchingPreservingCancellation` 包住并以 `.onFailure { }` 记日志
（`:276-282`），普通异常出不来。但这一步存在的理由本身就是"`defaultAudioType` 改成 PCM 那天"
——届时它会真正做事（`pause()` + `flush()` + `stop()`），而那也恰恰是 worker 真会卡在
`AudioTrack.write()` 上、join 最不能被跳过的场景。

**`stopping` 标志**（第八次复审新增，spec §4.7.1/§4.7.3 H2）。在 `ioScope` 声明附近追加：

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

两处自查（import `java.util.concurrent.atomic.AtomicBoolean`）：

```kotlin
// onConnected(), 当前 :49 —— 放在方法第一行
if (stopping.get()) return

// start(), 当前 :94 的 micRecorder 赋值之前
if (stopping.get()) return
```

`start()` 这一处必须在**构造 `MicRecorder` 之前**返回：构造即开麦，构造完再补一次
`stopRecordAndJoin()` 等于把关闭体再跑一遍，与门闩冲突。

**`senderClient` 必须在 `connect()` 之前赋值**（第九次复审新增，spec §4.7.3 的更正框、§18.2 H2）。

> **✅ 本项已于 2026-09-20 实施，实施者请跳过，不要重复改。**
> 记录见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.7 与 §4.8。已落地内容：
>
> 1. `AudioSender.start()` 里 `senderClient` 的赋值已提到 `connect()` 之前，`also { }` 出口
>    赋值已去掉，`senderClient` 不再有 null 窗口（§18 H2 的根因）。
> 2. **`stop()` 已改为 `suspend fun`**，并按本节的顺序重排：`disconnectManually()` →
>    `stopRecordAndJoin()` → `player.stop()` → `cancelAndJoin()` → `player.release()` →
>    `client.release()`。旧实现的 `ioScope.launch { }` + 紧随其后的 `ioScope.cancel()` 已去掉
>    ——那对组合使两个 suspend 释放调用**一次都没执行过**。
> 3. 入口处先把资源取到局部变量再清空字段，挡住**顺序**重入的二次 release
>    （**不等于门闩**，并发重入仍可能发生）。
> 4. `cleanupScope` 与两处 netty 回调改写（本 Task Step 3 的 `AudioSender` 一半）一并落地。
> 5. `micRecorder?.stopRecord()` 已换成 `stopRecordAndJoin()`，少一条弃用警告。
>
> **本 Step 剩余待办**：`teardownGate`、`teardownSteps()` 平铺串联、`stopping` 标志、
> `containsJob()` 自调用守卫、两个队列改 `Channel`、把 `player.release()` 换成 Task 2 的
> `releaseAndJoin()`。下面保留原分析作为依据。

不做这一步，上面把 `disconnectManually()` 提到第一步的改动**在它要修的场景里是空转的**：
现状 `start()`（`:85-92`）是

```kotlin
        senderClient =
            AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000)).also {
                senderHandler = AudioSenderWebSocketHandler(it)
                it.initHandler(senderHandler)
                it.connect()        // suspend；内部 f.sync() 最长 30 s
            }
```

`also` 的赋值发生在块**返回之后**，而 H2 场景正是"`connect()` 内部同步回调 `onFailed`"——
此刻 `connect()` 尚未返回，`senderClient` 仍是 `null`，于是第 1 步的
`senderClient?.disconnectManually()` 与第 7 步的 `senderClient?.release()` 双双空转：重试链不终止，
两个 EventLoopGroup 不 shutdown。若第 5 步的 `ownerJob?.cancelAndJoin()` 在 `connect()` 挂起期间
取消了这个协程，赋值更是**永远不会发生**。

改为：

```kotlin
    fun start(ctx: Context, uri: URI) {
        this.ctx = ctx
        ioScope.launch {
            // Assign before connect(), not after. connect() parks for up to
            // CONNECTION_TIMEOUT_IN_MILLS and calls onFailed() from inside itself, so an
            // `also { }` that assigns on the way out leaves senderClient null for exactly the
            // window in which stop() needs it to end the retry chain and release the client.
            val client =
                AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000))
            senderClient = client
            senderHandler = AudioSenderWebSocketHandler(client)
            client.initHandler(senderHandler)
            client.connect()
        }
        if (stopping.get()) return
        micRecorder = MicRecorder(/* unchanged */).apply { startRecord() }
    }
```

`stopping` 标志仍然必要——它挡住 `onConnected()` 新建 `AudioPlayer`；但"终止重试链 + 释放
netty client"这两件事只能靠 `senderClient` 非空。两者都要做，不可互相替代。

**两个共用工具提成 internal 顶层函数**（第八次复审明确，不再给"各留一份私有副本"的选项）。
新建 `demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupport.kt`，
与 `SuspendTeardownGate` 同包：

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
 */
internal suspend fun teardownSteps(vararg steps: suspend () -> Unit) {
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

/**
 * True when [target] is this job or any job below it.
 *
 * A teardown entry point that cancel-and-joins its own scope must reject callers running
 * anywhere inside that scope, not just callers holding the scope's own Job - launch() always
 * hands a child Job, so an identity check would never fire. Mirrors the private helper in
 * StreamPlayerStopper.
 */
internal fun Job.containsJob(target: Job): Boolean =
    children.any { child -> child === target || child.containsJob(target) }
```

`AudioSender`（`...audio.sender` 包）与 `AudioReceiver`（`...audio.receiver` 包）都要
`import com.leovp.demo.basiccomponents.examples.audio.teardownSteps` 与
`...audio.containsJob`（`internal` 是模块可见，跨子包没问题，但 import 不能漏）。

**为什么是平铺的 step 列表而不是嵌套 `try/finally`**（第七次复审）：裸 `try/finally` 不记录
suppressed，若 `stopRecordAndJoin()` 抛出、随后 `senderClient.release()` 也抛出，根因会被静默
丢弃，门闩广播给所有等待者的反而是最后那个不相干的异常；而且 6 层嵌套违反本仓库
`.claude/rules/common/coding-style.md` 的 "No deep nesting (>4 levels)"。
`AudioReceiver`（Step 5）复用同一写法，所以 `teardownSteps()` 与 `containsJob()` 都放在上面那个
`TeardownSupport.kt` 里（第八次复审已把"或各留一份私有副本"这个选项去掉——DRY 违规，且两份副本
会各自漂移）。

**`cancelAndJoin()` 还会等待连接协程**（第七次复审）：`start()` 在同一个 `ioScope` 上跑
`senderClient.connect()`（当前 `:85-92`），而 `BaseNettyClient.connect()`（`:342`）内部执行
`f.sync()`（`:421`），其自身注释写明最长阻塞 `CONNECTION_TIMEOUT_IN_MILLS` = `30_000`（`:99`）。
旧实现 `ioScope.cancel()` 后立即返回，新实现会等它。预期
`cont.invokeOnCancellation { cancelConnectFuture() }`（`:399-402`）会让它很快抛出，但这是
**必须实测的假设**——Task 5 Step 7 已新增"连接进行中退出"一项并要求记录耗时。

新增 import 还需 `kotlinx.coroutines.currentCoroutineContext`（自调用守卫）、
`java.util.concurrent.atomic.AtomicBoolean`（`stopping`），以及上面两个共用工具
（`...examples.audio.teardownSteps` / `...examples.audio.containsJob`）。

保留 `kotlinx.coroutines.launch`；删除已无调用的 `kotlinx.coroutines.cancel`（当前 `:22`）、
`kotlinx.coroutines.ensureActive`（`:23`）和 **`kotlinx.coroutines.runInterruptible`（`:25`）**。
最后一个容易漏：它是 `5672ecbbd` 为包住 `take()` 而加的，换成 Channel 后失效，detekt
零容忍会直接失败。关闭时先停录音并关闭两个 Channel，使迟到回调的
`trySend()` 失败，再等 worker 退出；因此 `AudioTrack.write()` 不会与 release 并发，
也不会在无消费者时滞留音频帧或留下阻塞的 IO 线程。

- [x] **Step 5: `AudioReceiver.stopServer()` 改为可等待的一次关闭**

文件 `AudioReceiver.kt`，`fun stopServer()`（当前 `:198`）改为：

```kotlin
    suspend fun stopServer() {
        // stopServer() cancel-and-joins ioScope, so it can never run on ioScope or any of its
        // children. Descendant check, not identity - see AudioSender.stop() for why.
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
                // The historical SIGABRT note applies to the PCM path: no writer survives.
                { audioPlayer?.releaseAndJoin() },
                // Last, not first: stopServer() shuts both EventLoopGroups down, and the workers
                // above may still be sending through a channel. A client arriving before this
                // runs is turned away by the `stopping` flag instead.
                { receiverServer?.stopServer() },
            )
        }
    }
```

`teardownSteps()` 与 `containsJob()` 复用 Step 4 建的 `TeardownSupport.kt`（不再各留私有副本）。
`stopping` 字段与 `AudioSender` 的同名字段一致（KDoc 里把 "start() assigns micRecorder / doRetry()"
那两句换成 "startServer() builds the player before the server is up, and onClientConnected() builds
the recorder and both workers"）。两处自查：

```kotlin
// onClientConnected(), 当前 :70 —— 放在方法第一行
if (stopping.get()) return

// startServer(), 当前 :181 的 audioPlayer 赋值之前
if (stopping.get()) return
```

新增 import：`kotlinx.coroutines.currentCoroutineContext`、
`java.util.concurrent.atomic.AtomicBoolean`，以及
`...examples.audio.teardownSteps` / `...examples.audio.containsJob`。

**`receiverServer` 必须在 `startServer()` 之前赋值，否则关闭序列的最后一步是死代码**
（第九次复审新增，spec §4.7.4、§18.2 H1）。

> **✅ 本项已于 2026-09-20 实施，实施者请跳过，不要重复改。**
> 记录见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §4.5。已落地内容：
> `BaseNettyServer.startServer()` 与 `stopServer()` 均已改为 `suspend`，`startServer()` 不再
> 阻塞到服务器关闭；`AudioReceiver.startServer()` / `stopServer()` 随之改 `suspend`，
> `receiverServer` 赋值已提前；本 Task Step 3 中 `AudioReceiver` 侧的 `cleanupScope` 与两处
> netty 回调改写也一并落地（**`AudioSender` 侧仍待做**）。
> **本 Step 剩余待办**：`teardownSteps()` 关闭序列本体、`teardownGate`、`stopping` 标志。
> 下面保留原分析作为依据。

现状 `startServer()`（`:191-195`）是

```kotlin
        receiverServer = AudioReceiverWebSocket(10020, connectionListener).also {
            receiverHandler = AudioReceiverWebSocketHandler(it)
            it.initHandler(receiverHandler)
            it.startServer()          // 阻塞到服务器关闭为止
        }
```

`also` 的赋值发生在块**返回之后**，而块里的 `it.startServer()` 根本不会返回：
`AudioReceiverWebSocket`（整文件 21 行）没有覆写 `startServer()`，`BaseNettyServer` 里也没有任何
thread / coroutine，而 `BaseNettyServer.startServer()`（`:129`）在 `:136` bind 成功后于 `:142`
执行 `serverChannel.closeFuture().sync()`——源码注释自己写着
"the process will get stuck there and wait for client connection"。

⇒ **服务器成功启动 ⇒ `receiverServer` 永远是 `null`**，只有 bind 失败时才会被赋值。于是
`stopServer()` 的 `receiverServer?.stopServer()` 在正常会话里是一条空语句：netty server 从不停止、
10020 端口不释放，`AudioActivity.kt:408` 的 `ioScope.launch { audioReceiver?.startServer(...) }`
永久阻塞在 `sync()` 上（`onDestroy()` 用的是 `ioScopeJob.complete()` 而非 `cancel()`，`:383`），
连同它持有的 Activity 引用一起留存。

改为：

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

`stopping` 的自查位置随之上移到 `audioPlayer` 赋值之前（即上面那行），替换本步前面给出的
"`:181` 的 `audioPlayer` 赋值之前"这一处，不要写成两处。

**三个关键点：**

1. 不能只取消 scope 而不等待；那样 `startPlayThread()` 的 `audioPlayer?.play(it)` 可能仍在
   整个 `releaseAndJoin()` 期间继续跑，PCM 路径下 `write()` 会撞上 `AudioTrack.release()`——正是
   历史 SIGABRT 注释描述的场景。
   第六次复审曾据此断言"只等待也不够，`cancelAndJoin()` 本身可能永不返回"，理由是 worker 可能
   卡在 `AudioTrackPlayer.write()`（`:194`）的阻塞 `AudioTrack.write(short[], int, int)` 上。
   **该理由在本类中不成立**（第七次复审，spec §15.1）：`AudioReceiver` / `AudioSender` 的
   `AudioPlayer` 都以 `defaultAudioType`（`AudioReceiver.kt:32` = `AudioType.OPUS`）构造，
   `AudioPlayer.play()` 只走 `:99-108` 的 stream player 分支，`:88-98` 的 PCM 分支恒不命中，
   该 track 从不 `write()`。
   `audioPlayer?.stop()`（`AudioPlayer.kt:158`，仅委托 `audioTrackPlayer.stop()`，不碰 stream
   player）**仍然保留**，但理由改为：与 `StreamPlayerStopper.prepareStop()`（`:58-70`，注释写的
   就是 "Stop first to wake any blocking write"）保持同一顺序，代价为零（该 track 从未
   `play()` 过，`stop()` 在 `playState == PLAYSTATE_STOPPED` 守卫处直接返回），并在
   `defaultAudioType` 改成 PCM 时立刻变成必需。**不得据此撰写"修死锁"的提交信息或验证项。**
2. 先停录音再 `cancelAndJoin()`，保证没有新帧进入；关闭跑在 `teardownGate` 的
   `NonCancellable` 上，不会被调用方中途取消。
3. 两个关闭体都用 `teardownSteps()` 平铺串联（不是嵌套 `try/finally`），保证前一项失败时仍尝试
   后续资源**且根因不被后续异常顶掉**；最终异常由 `SuspendTeardownGate` 共享给并发等待者
   （取消除外），并由调用方或 `cleanupScope` handler 记录。**平铺的前提是每个 step 只做一件事**
   ——把两个动作塞进同一个 lambda 就退回了"一损俱损"，见上面 `audioPlayer?.stop()` /
   `ownerJob?.cancelAndJoin()` 的拆分说明（第十次复审 H1）。
4. **本类没有与 `AudioSender` 第 3 步（关闭两个 Channel）对等的一步，这是有意的**（第十次复审
   L3）。`AudioReceiver` 的两个队列本次不改 Channel（同名冲突，见 spec §4.7.4），而
   `ArrayBlockingQueue` 没有 `close()`。这不构成缺口：两个写入点
   （`onReceivedData()` 的 `receiveAudioQueue.offer()`、`MicRecorder.onRecording()` 的
   `recAudioQueue.offer()`）都用非阻塞的 `offer()`，队列有界，消费者退出后迟到的帧只会被丢弃，
   既不阻塞 netty 线程或录音回调，也不会滞留到对象回收之后。

import 变化：新增 `kotlinx.coroutines.Job`、`kotlinx.coroutines.cancelAndJoin`；
**删除 `kotlinx.coroutines.cancel`**（当前 `:20`）——`:201` 是全文唯一的 `cancel()` 调用点，替换后
该 import 失效，detekt 零容忍会直接失败。

- [x] **Step 6: `AudioActivity` 只换方法名**

文件 `AudioActivity.kt`。该文件已有 `launchCleanup(name) { block }`（当前 `:387`，`block` 是
`suspend () -> Unit`），跑在带 `SupervisorJob` + `CoroutineExceptionHandler` 的 `ioScope`（当前 `:79`）
上，`onDestroy()` 用 `ioScopeJob.complete()`（当前 `:383`）而非 `cancel()`，正是为了让清理跑完。
**不要引入 `lifecycleScope.launch(NonCancellable)`**——那会绕开这套机制。

`:352-355` 的 `launchCleanup("Stop PCM playback") { ... }` 块内，把 `player?.release()` 改为
`player?.releaseAndJoin()`，**并在 `join()` 之前补一次 `player?.stop()`**（第七次复审新增）：

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

**这里才是 spec §15.1 那个模式在 demo 中真正存在的位置**，而不是 `AudioSender` / `AudioReceiver`：

- 这个 player 确实是 `AudioType.PCM`（`AudioActivity.kt:144`），其裸线程 `playPcmThread`
  （`:148-176`）在循环里调 `audioPlayer?.play(pcmData)`，PCM 路径直通
  `AudioTrackPlayer.write()`（`:194`）的阻塞 `AudioTrack.write(short[], int, int)`。
- 现有 teardown 只 `playbackThread?.interrupt()`（`:351`）就 `join()`（`:353`），而
  `Thread.interrupt()` **唤不醒** native 写入；能唤醒它的 `AudioTrack.stop()` 又排在
  `release()` 里、在 join 之后。实际阻塞时长通常由缓冲区排空决定（有界），但 track 若在写入
  期间被 `pause()`，写入会一直挂住。
- 这一步不改变公开面，也不引入新 scope，仍在既有的 `launchCleanup` 内。

**但 `player?.stop()` 不是一个干净的解阻塞调用**（第八次复审，spec §4.7.5 M3）。它委托
`AudioTrackPlayer.stop()`（`:270-283`），而后者第一步是 `pause()`（`:274`）——
`AudioTrackPlayer.pause()`（`:246-257`）做的是 `audioTrack.pause()` **加 `audioTrack.flush()`**，
之后才 `audioTrack.stop()`（`:280`）。也就是"先加深阻塞并对进行中的写入 flush，再由 stop() 解除"。
窗口极短，**顺序不改**（与 `StreamPlayerStopper.prepareStop()` / `AacFilePlayer` 一致），
但 Task 5 Step 3 的 PCM 项必须同时确认 logcat 无 `AudioTrack.write failed` /
`AudioTrack.write error=`；若出现，修法是给 `AudioTrackPlayer` 加一个只做 `audioTrack.stop()`
的解阻塞入口，**不是**调整本步的调用顺序。

`:371` 与 `:372` 两行**不动**：

```kotlin
        launchCleanup("Stop audio receiver") { audioReceiver?.stopServer() }
        launchCleanup("Stop audio sender") { audioSender?.stop() }
```

两者在 Step 4/5 变成 suspend 后，`launchCleanup` 的 `suspend () -> Unit` 直接兼容。

- [x] **Step 7: 让 `ADPCMActivity` 的播放 Job 拥有 player**

文件 `ADPCMActivity.kt`。要删的共 **4 处**（第十次复审 L2 补齐，此前只列了 3 处）：

| 位置 | 内容 | 说明 |
|------|------|------|
| `:38` | `private var player: AudioPlayer? = null` | 字段本身 |
| `:88` | `player = AudioPlayer(this, decoderInfo, AudioType.PCM)` | **此前漏列**。它在 `thread { }` 块**之外**，由下面 Job 体内的 `currentPlayer` 取代 |
| `:115` | `player?.release()`（`.also { }` 内） | 由 Job 的 `finally` 取代 |
| `:121` | `player?.release()`（`onDestroy()` 内） | 改为只 `playbackJob?.cancel()` |

> `:88` 漏删会因未解析引用直接编译失败，所以它不会流到运行期；列出来是因为真正的风险是
> 实施者顺手把它改成 Job **外面**的 `val player = ...`——那会重新引入"Job 在首次调度前被取消
> ⇒ `finally` 不执行 ⇒ 已构造的 player 泄漏"，也就是本步末尾明令禁止的写法。

把 `onPlayADPCMClick()` 的 `thread { }`（`:90`）换成 lifecycle Job，新增：

> **不要删 `import kotlin.concurrent.thread`（`:23`）**（第八次复审，M4）。本次只改*播放*路径；
> `onEncodeToADPCMClick()`（`:40-73`）里 `:46` 的 `thread { }` 属于编码路径，不在本次范围，
> 该 import 仍有使用点。把它当成"随播放路径一起失效的 import"删掉会直接编译失败。

```kotlin
private var playbackJob: Job? = null
```

点击播放时先取消并**捕获**上一个 Job，再把原 `thread { ... }` 改为：

```kotlin
val previousJob = playbackJob?.also { it.cancel() }
playbackJob = lifecycleScope.launch(Dispatchers.IO) {
    // Join the previous job before building a new player: cancel() does not wait, and the old
    // job's finally still has a NonCancellable releaseAndJoin() to run. Without this, rapid
    // clicks stack several live AudioTracks. Joining here, not on the click thread, keeps the
    // UI thread free.
    previousJob?.join()
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
未执行的 Gradle 项标记为“未验证”。预期：`BUILD SUCCESSFUL`，门闩的 4 个用例与
`TeardownSupportTest` 全部通过，
demo 侧无本次 API 的弃用警告，且无 suspend 函数调用位置错误。

- [ ] **Step 9: 提交（仅用户明确授权时）**

```bash
git add demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupport.kt \
        demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGateTest.kt \
        demo/src/test/kotlin/com/leovp/demo/basiccomponents/examples/audio/TeardownSupportTest.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/sender/AudioSender.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/receiver/AudioReceiver.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt \
        demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/ADPCMActivity.kt
git commit -m "refactor(demo): await audio teardown instead of firing it off

AudioReceiver cancelled its scope before releasing the player, which would have
cancelled the suspend teardown before it finished. Cancelling last instead would
have let the playback loop keep calling AudioPlayer.play() throughout the
release, so the scope is joined before the release rather than after it.

AudioSender's queue waits were already made cancellable by wrapping take() in
runInterruptible. Moving both workers onto Channels drops that dependency on
thread interruption and gives teardown a close() to stop accepting late frames
with, rather than fixing a leak that is still there.

AudioSender now disconnects before anything slow rather than last. Netty calls
onFailed() synchronously and then schedules a retry, so a reconnect could land
mid-teardown and hand onConnected() a fresh AudioPlayer that the one-shot gate
would refuse to release. A stopping flag covers the rest of that window on both
classes, since neither gate can promise that nobody builds a new resource after
teardown has run.

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

- [x] **Step 1: 从两个接口删除 `release()`**

`AudioEncoderWrapper.kt` 删除 `fun release()` 一行；`AudioDecoderWrapper.kt` 删除 `fun release()` 一行。
删除后 `AudioEncoderWrapper` 应只剩 `encode()` 与 `suspend releaseAndJoin()`，
`AudioDecoderWrapper` 只剩 `decode()` 与 `suspend releaseAndJoin()`。

- [x] **Step 2: 从四个 wrapper 实现删除 `release()` 覆写**

`AacEncoderWrapper.kt` 与 `OpusEncoderWrapper.kt` 删除整个：

```kotlin
    override fun release() {
        runCatchingPreservingCancellation { encoder.release() }
            .onFailure { LogContext.log.e(TAG, "AAC encoder release failed", it) }
    }
```

（OPUS 版的日志文案是 `"Opus encoder release failed"`——第七次复审更正，此前本行误记为
全大写的 "OPUS"，比对源码时会对不上，实际见 `OpusEncoderWrapper.kt:46`。）

`CompressedPcmEncoderWrapper.kt` 与 `CompressedPcmDecoderWrapper.kt` 删除 `override fun release() { }`。

**删除后这两个 encoder wrapper 会连带清空 `companion object`**（第七次复审核实）。在
`AacEncoderWrapper.kt` 与 `OpusEncoderWrapper.kt` 中，`runCatchingPreservingCancellation`
（`:3`）、`LogContext`（`:10`）和 `TAG`（`:21`）的**唯一使用点**就是被删的 `release()`
（`:44-47`）。因此必须一并删除：

1. 两条失效 import：`com.leovp.audio.base.runCatchingPreservingCancellation`（`:3`）、
   `com.leovp.log.LogContext`（`:10`）。**连同 `:3` 下面那个孤立空行（`:4`）一起删**
   （第八次复审，L3）——两个文件现在都是"`import runCatchingPreservingCancellation` + 空行 +
   其余 import"的写法，只删 import 会在 `package` 与首个 import 之间留下空行，ktlint 零容忍；
2. `private const val TAG`；
3. **以及包着它的整个 `companion object`**——只删 TAG 会留下 `companion object { }`，
   而 detekt 的 `EmptyClassBlock` 是开启的（`10-configs/detekt.yml:203`），`maxIssues = 0`
   下会直接失败。

两个 `CompressedPcm*Wrapper` 没有 `TAG`/`LogContext`，无此问题；它们保留 Task 1 新增的空体
`releaseAndJoin()`（`EmptyFunctionBlock` 的 `ignoreOverridden` 为 `true`，`10-configs/detekt.yml:215-217`，
覆写的空体不会被判违规）。

- [x] **Step 3: 删除 `BaseMediaCodec` 的弃用 `release()`**

文件 `BaseMediaCodec.kt`，删除整个（当前 `:226-246`，含上方 KDoc）：

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

`markReleasing()`、`releaseCodecOnce()`、`codecJob`、`lifecycleState` 仍被 `releaseAndJoin()` 使用，
**不要一起删**。全仓已确认无子类覆写 `BaseMediaCodec.release()`。

- [x] **Step 4: 删除两个 stream player 的 `stopPlaying()`**

`AacStreamPlayer.kt`（当前 `:213-225`）与 `OpusStreamPlayer.kt`（当前
`:207-219`）删除整个：

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

- [x] **Step 5: 删除 `StreamPlayerStopper.stop()`**

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

- [x] **Step 6: 删除 `AudioPlayer.release()`**

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

- [x] **Step 7: 删除 `MicRecorder` 的非挂起释放路径**

文件 `MicRecorder.kt`。前置检查：先确认 Task 2 已将 `rollbackFailedInit()` 中的
   `encodeWrapper?.release()` 迁移为局部 encoder 的 `releaseAndJoin()`，且
   `releaseCompleted.complete(Unit)` 位于同一协程的 `finally`。若仍有旧调用，本步删除
   `AudioEncoderWrapper.release()` 后会直接编译失败，必须回到 Task 2 Step 4，不得在这里
   临时抑制或改回非挂起释放。

1. 删除 `fun stopRecord()`（当前 `:411-418`）
2. 删除 `private fun finishRecorderRelease(stopSucceeded: Boolean)`（当前 `:437-450`）
3. 修正**两处** KDoc，不得只改第一处：
   - `released` 字段上方（当前 `:122`）把
     `Guards [finishRecorderRelease] so ...` 改为引用 `finishRecorderReleaseAndJoin`。
   - `releaseAdvancedFeatures()` 上方（当前 `:358-360`）删除
     `[finishRecorderRelease] /` 和“`both of which`”，只保留
     `[finishRecorderReleaseAndJoin]` 与 `[rollbackFailedInit]` 的入口说明。

保留 `suspend fun stopRecordAndJoin()` 与 `private suspend fun finishRecorderReleaseAndJoin()`。

- [x] **Step 8: 迁移受影响的现有单元测试**

删掉上面那些 API 之后，三个测试文件会编译不过。逐条处理（依据 spec §6.2）：

**A. `BaseMediaCodecAsynchronousTest.kt`（18 处 `subject.release()`）**

1. **纯作收尾用的调用**（`:35 :91 :108 :124 :153 :182 :200 :234 :255 :284 :387 :405`）——非
   `runTest` 的用例改为 `runBlocking { subject.releaseAndJoin() }`，已经是 `runTest` 的直接改成
   `subject.releaseAndJoin()`。`releaseAndJoin()` 的
   `require(codecJob !== currentCoroutineContext()[Job])` 不会误伤：`runBlocking` 建的是另一个 Job。
2. **`assertReleaseWaitsForCallback()` 私有辅助函数**（`:483`，其中 `:500` 的
   `releaseExecutor.submit { subject.release() }`）——改为
   `releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }`。一处改动覆盖 **2** 个
   用例：`release waits for active format callback`（调用点 `:316`）与
   `release waits for active error callback`（调用点 `:364`）。断言依然成立：`releaseAndJoin()`
   同样要过 `releaseCodecOnce()` 的 `withCodecOperationLock`（`BaseMediaCodec.kt:277-278`，
   阻塞 `ReentrantLock.withLock`）。

   > **第八次复审更正（M1）**：本条此前写的是"覆盖 3 个用例"，并把
   > `release waits for an active asynchronous input callback` 算了进来。实测
   > `assertReleaseWaitsForCallback(` 在全文件只出现在 `:316` 与 `:364` 两处；
   > 那个用例（`:44`）有**自己的内联** `subject.release()`（`:62`），由下面第 3 条处理。
   > 于是它此前被第 2、3 两条重复计数，spec §16.1 的"无重无漏"随之不成立。
   > **迁移动作不受影响**——18 个调用点仍被完整覆盖，只是这一处改动的收益是 2 个用例。
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

> **该文件还有第 4 个用例，不要一起删**（第八次复审补登记）：
> `suspending stop rejects calls from its owned scope before side effects`（`:121-139`）。
> 它不调用被删的 API，与本次改动无关。它同时是 Task 3 Step 4/5 那条自调用守卫的现成模板——
> 用 `scope.launch(start = CoroutineStart.UNDISPATCHED)` 的**子协程**验证 `require` 会拒绝，
> 并且是"在任何副作用之前"拒绝（`verify(exactly = 0) { audioTrackPlayer.stop() }`）。
> 这正是身份比较版守卫过不了的那个断言。

**D. 清掉随之失效的 `@Suppress("DEPRECATION")`**

`BaseMediaCodecAsynchronousTest.kt` 19 处（`:26 :42 :80 :97 :113 :130 :167 :188 :216 :241 :262
:294 :311 :359 :377 :394 :413 :428 :482`）与 `BaseMediaCodecSynchronousTest.kt:22` 1 处，全是
为调用这些弃用 API 而加的，随之失效，全部删除。

**注意**：`audio/src/main/kotlin/com/leovp/audio/exts/AudioExt.kt:42,56` 的两处
`@Suppress("DEPRECATION")` 针对的是 Android 平台 API 弃用，**与本次无关，不要动**。

- [x] **Step 9: 写 CHANGELOG**

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
  - 初始化失败回滚（两个 stream player、`MicRecorder` 构造和
    `MicRecorder.startRecord()`）改为在
    `NonCancellable` 协程上异步释放，因其调用点位于锁内或非挂起公开入口，无法等待。
    关闭路径不受影响，仍然完全确定性。
  - **行为变更（签名不变）**：受上一条影响，`MicRecorder.startRecord()` 启动失败时
    `RecordCallback.onStop(false)` **不再保证在 `startRecord()` 返回之前送达**，而是稍后
    从 `Dispatchers.IO` 线程到达。`startRecord()` 本身仍是非挂起、签名不变，因此编译期
    不会提示；依赖"`startRecord()` 返回即已收到 onStop"的下游需要自行调整。
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

# 5) 构造失败回滚不得残留已删的 encoder API，KDoc 也不得引用已删函数。
rg -n 'encodeWrapper\?\.release\(\)|\[finishRecorderRelease\]' \
  audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt ; echo "(空=符合要求)"
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
若复现，说明 Task 2 Step 6 中 `audioTrackPlayer.release()` 的位置需要调整，回到该步处理。

**外加：`AudioActivity` 的 PCM 播放路径**（第七次复审新增）。勾上 PCM 播放开关、在**持续播放中**
退出 Activity（不是播完之后），重复 5 次以上。这是 spec §15.1 那个"阻塞写入挡住 join"模式在
demo 里真正存在的位置（`AudioActivity.kt:144` 的 `AudioType.PCM` + `:148-176` 的裸线程）。
确认 Task 3 Step 6 的 `player?.stop()` → `playbackThread?.join()` → `releaseAndJoin()` 序列在
有限时间内结束、`launchCleanup` 日志正常收尾、无 ANR、无 write/release 并发崩溃。
若 join 不返回，回到 Task 3 Step 6。
**注意**：这条不能用 `AudioSender` / `AudioReceiver` 代替——两者恒为 OPUS，走不到 PCM 写入路径。

**另外必须看 flush/write 并发**（第八次复审新增，M3）：`AudioPlayer.stop()` 经
`AudioTrackPlayer.stop()`（`:274`）会先 `pause()` + `flush()` 再 `stop()`，因此要确认 logcat 中
**不出现** `AudioTrack.write failed` 或 `AudioTrack.write error=`。若出现，按 Task 3 Step 6 的
说明处理（给 `AudioTrackPlayer` 加只做 `audioTrack.stop()` 的解阻塞入口），**不要**改调用顺序。

- [ ] **Step 4: `AudioActivity` 录放组合后退出**

录制 + 播放组合操作后直接退出 Activity，确认 `launchCleanup` 的三处清理都跑完
（看 logcat 中 `launchCleanup` 的日志），无 ANR、无泄漏。

- [ ] **Step 5: 初始化失败回滚**

构造解码器初始化失败（例如向 `AacStreamPlayer.startPlayingStream()` 喂入损坏的 csd0）。
确认：日志出现 `init failed, rolled back`；**不出现** `rollback release failed`；随后正常调用
停止流程不受影响、不崩溃。

另外在可控测试环境覆盖 `MicRecorder` 的两个构造失败入口（当前
`:184` 的 `AudioRecord` 构造失败和 `:193` 的高级音频效果初始化失败）。
确认原异常重抛，encoder 最终完成 `releaseAndJoin()`，且
`releaseCompleted` 不在 encoder 释放前提前完成。如果当前没有可注入的失败环境，
将这两个分支记为未验证，不得用普通启停成功代替。

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
- **连接进行中退出**（第七次复审新增，与上一条顺序相反）：填不可达地址后，在
  `ConstantRetry(10, 2000)` 的连接/重试窗口内**立刻**退出 Activity。新的 `stop()` 会
  `cancelAndJoin()` 等待 `start()` 起在同一 `ioScope` 上的连接协程，而
  `BaseNettyClient.connect()` 内部 `f.sync()` 最长阻塞 `CONNECTION_TIMEOUT_IN_MILLS` = 30 秒
  （`BaseNettyClient.kt:99`、`:421`）。**记录 teardown 实际耗时**：预期
  `cont.invokeOnCancellation { cancelConnectFuture() }`（`:399-402`）会让它很快返回；
  若观察到接近 30 秒的停顿，说明该缓解不生效，需回到 Task 3 Step 4 重新设计
  （例如把连接协程移出被 join 的 scope）。
- **server 确实停止**（第九次复审新增，spec §18.2 H1）：退出 `AudioActivity` 后确认 logcat 出现
  `BaseNettyServer` 的 `===== stopServer() ... =====` 与 `=====> Server released <=====`，
  并确认 10020 端口已释放（再次进入 Activity 能重新 bind，不报 `BindException`）。
  **这条在改 Task 3 Step 5 之前必然失败**（`receiverServer` 恒为 null），因此它正是用来
  确认那处赋值提前是否生效的。
- **重试链确实终止**（第九次复审新增，spec §18.2 H2）：sender 填不可达地址，在第一次连接失败后
  **立刻**退出 Activity。确认 logcat 不再出现 `Reconnect(n) in 2000ms`，teardown 之后不再出现
  `onConnected`，且 `disconnectManually() done` 与 client 的 `release()` 各出现一次。
  **这条在改 Task 3 Step 4 之前同样必然失败**（`senderClient` 在该时刻仍为 null）。
- 记录 teardown 耗时；四条路径都必须在有限时间内结束。门闩本身无超时，任一 netty
  或媒体操作卡住都会让 Activity 与回调路径一起卡住；如发生这种情况，应回到
  具体资源操作层定界，不在 `completed.await()` 外层粗暴超时。
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
  这是**回归确认**：`5672ecbbd` 的 `runInterruptible` 已消除该泄漏，本项确认 Channel
  改造没有把它带回来（spec §14.1）。
- **teardown 期间的重连与迟到接入**（第八次复审新增，对应 Task 3 Step 4/5 的 `stopping` 标志）：
  - sender 填不可达地址触发 `onFailed()` 后**不要立刻退出 Activity**，让 teardown 与
    `ConstantRetry(10, 2000)` 的重试窗口重叠。确认 logcat 中 `onConnected` **不再出现**，
    也不出现第二个 `AudioPlayer` 的 `OPUS Decoder` 构造日志。
    如果去掉 `disconnectManually()` 提前那一步就能复现出多余的 `onConnected`，说明这条验证有效；
    否则应缩短重试间隔或延长 `stopRecordAndJoin()` 以把窗口撑开，不要因为"没复现"就删掉守卫。
  - receiver 侧：在 `stopServer()` 进行中让对端接入，确认 `onClientConnected` 被 `stopping` 挡住、
    不新建 `MicRecorder`（logcat 无新的 `MicRec` 构造日志）。
  - 再测"点完发送立刻退出"：确认麦克风指示灯熄灭、没有持续的 `MicRec` 录音日志。这是
    `stop()` 跑赢 `start()` 的 `micRecorder` 赋值那条路径（spec §4.7.1 第 1 种情况）。
    若 `stop()` 恰好落在"检查已过、字段未赋"的窗口内，应看到补偿路径生效：`start()` 的
    re-check 在门闩完成后调用 `stopRecordAndJoin()`（spec §20.1），麦克风同样被释放。
  - **receiver 启动中退出**（spec §20.1 新增）：点 receiver 后在 bind 完成前（约 50 ms 内）
    退出 Activity。确认 `startServer()` 的 re-check 在门闩完成后调用了
    `BaseNettyServer.stopServer()` 与 `releaseAndJoin()`：logcat 出现 `=====> Server released <=====`，
    随后重进能再次 bind 10020，不报 `BindException`。
- **ADPCM 所有权**：播放中立即退出，再连续点击播放。确认旧 Job 只释放自己的 player，
  不释放新 Job 的 player，不出现 AudioTrack write/release 并发。

- [ ] **Step 8: 把验证结果记入文档**

在 spec `00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md`
末尾追加一节（当前末节是 §20「代码审查修正」，因此新节编号为 §21；实施时以 spec 实际末节编号为准），
如实记录每项的通过/失败与设备型号、
Android 版本。失败项必须记录，不得省略。

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
   退出、并发关闭门闩、ADPCM Job 所有权、**`AudioActivity` PCM 播放中退出**、
   **连接进行中退出（记录 teardown 耗时）**，以及 **teardown 期间的重连与迟到接入**
   （第八次复审新增）
6. `CHANGELOG.md` 已记录破坏性变更与迁移方式
7. `:audio:testDebugUnitTest` 能编译并通过——Task 4 Step 8 的测试迁移已完成，且与删除 API
   在同一个提交里
8. `SuspendTeardownGateTest` 覆盖并发只执行一次、等待同一结果、异常传播、
   调用者取消，**以及关闭体重抛 `CancellationException` 时等待者不受牵连**（共 4 个用例）；
   KDoc 明确单 owner、单会话、不可重置、无门闩层超时和取消不广播的契约。
   另有 `TeardownSupportTest` 覆盖 `teardownSteps()` 的"后续步骤仍执行 + 首个失败 +
   suppressed"，以及 `containsJob()` 对子 Job / 孙 Job 返回 `true`（第八次复审新增）
9. `rollbackFailedInit()` 已无 `encodeWrapper?.release()`；其
   `releaseCompleted.complete(Unit)` 只在 encoder `releaseAndJoin()` 所在协程的
   `finally` 中执行；删除 `finishRecorderRelease()` 后无失效 KDoc 引用
10. 交付报告分开列出 Claude Code 静态分析、Codex 本机单测/构建、真机结果与
   未验证项；未经明确授权没有提交或推送
11. 两个 demo 关闭入口用 `teardownSteps()` 平铺串联，**无嵌套 `try/finally`**，
   且各带一条自调用 `require()` 守卫（第七次复审）。守卫必须是 **`containsJob()` 后代检查**，
   **不是**身份比较——`grep -n 'currentCoroutineContext()\[Job\] !==' demo/src/main` 应为空
   （第八次复审更正）
12. `AudioActivity.stopPcmPlayback()` 的顺序为 `stop()` → `join()` → `releaseAndJoin()`；
   `ADPCMActivity` 的新播放 Job 在构造 player 之前 `join` 掉上一个 Job（第七次复审）；
   `ADPCMActivity` 仍保留 `import kotlin.concurrent.thread`（编码路径在用，第八次复审）
13. `AacEncoderWrapper` / `OpusEncoderWrapper` 删除 `release()` 后不残留空的
   `companion object`、也不残留 `package` 与首个 import 之间的空行；CHANGELOG 已记录
   `RecordCallback.onStop` 的交付时机变更（第七次复审 + 第八次复审 L3）
14. `AudioSender.stop()` 的第一步是 `senderClient?.disconnectManually()`；两个类各有
   `stopping: AtomicBoolean`，并在 `start*()` 与 netty 接入回调（`onConnected` /
   `onClientConnected`）开头自查（第八次复审 H2）
15. `teardownSteps()` 与 `containsJob()` 只有一份，位于
   `demo/.../audio/TeardownSupport.kt`，两个类通过 import 复用，无私有副本（第八次复审 L2）
16. **`senderClient` 与 `receiverServer` 都在各自的阻塞调用之前赋值**（第九次复审 H1/H2）。
   自查：`grep -n 'Socket(.*)\.also' demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio`
   应为空 —— `AudioSender.start()` 与 `AudioReceiver.startServer()` 都不再用
   `field = Ctor(...).also { ...; 阻塞调用() }` 的形状。
   **状态：H1、H2 均✅ 已于 2026-09-20 实施**，上面那条 grep 现在应为空（已验证）。
   `AudioSender.stop()` 与 `AudioReceiver.stopServer()` 也已改为 `suspend`，
   sender 侧并已重排关闭顺序（见 Task 3 Step 4 的 ✅ 横幅）
17. Task 5 Step 7 的两条真机项（第九次复审）：
   - **"server 确实停止"—— 现在可以且应当通过**（H1 已实施，见第 16 项）
   - **"重试链确实终止"—— 现在第一次具备通过的条件，应当实测**（H2 与 `stop()` 重写均已
     实施）。门闩已随 Task 3 实施：若观察到 client `release()` 出现两次，就是
     `SuspendTeardownGate` 失效，**属回归**，回到 Task 3 排查，不得当作已知项记录了事
18. **`teardownSteps()` 的每个 step 只做一件事**（第十次复审 H1）。特别是
   `audioPlayer?.stop()` 与 `ownerJob?.cancelAndJoin()` 必须是**两个**独立 step，两个类都是。
   自查：`grep -n -A2 'audioPlayer?.stop()' demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio`
   的下一行不应出现 `cancelAndJoin`
19. `SuspendTeardownGate` 用**两个 catch 子句**而非 catch 内 `is` 判型，且取消分支自己记一条
   日志（第十次复审 M1/M2）。自查：
   `grep -n 'is CancellationException' demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/SuspendTeardownGate.kt`
   应为空
20. `ADPCMActivity` 的 4 处删除全部完成（`:38` 字段、`:88` 赋值、`:115`、`:121` 两处 release），
   且 `AudioPlayer` 只在播放 Job **体内**构造（第十次复审 L2）

---

## 实施后修正（2026-10-14 代码审查）

Task 1–4 实施后做了四路静态审查，发现的问题及修正记录在 spec §20。与本计划模板的偏离如下，
**以代码与 spec §20 为准**：

- Task 3 的 `SuspendTeardownGateTest` 第 2 个用例：模板的 `assertSame(expected, first)` 在 Gradle
  测试 JVM（`-ea` → coroutines DEBUG → stack-trace recovery）下必红，已改为断言类型与 message，
  并保留 `assertSame(first, second)`；新增 `awaitCompletion()` 用例。
- Task 3 的 `stopping` 标志：`AudioSender.start()` / `onConnected()`、`AudioReceiver.startServer()`
  / `onClientConnected()` 四处在赋值后**再读一次**，为真则 `teardownGate.awaitCompletion()` 后
  补偿释放；被 teardown 读取的字段加 `@Volatile`。`SuspendTeardownGate` 新增 `awaitCompletion()`。
- Task 3 的 `ADPCMActivity`：`previousJob?.join()` 包进 `withContext(NonCancellable)` 并后接
  `ensureActive()`。
- Task 3 的 `AudioActivity.onStop()`：`audioReceiver` / `audioSender` 先置 `null` 再调度关闭。
- Task 5 Step 7 新增"receiver 启动中退出"场景，并给"点完发送立刻退出"补上补偿路径的预期。
- basenetty 的 `awaitCompletion()` 增加 `isDone` 快速路径等修正，见
  `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §11。

**2026-10-15 第五轮（外部）审查后的再次偏离**，以代码与 spec §21 为准：

- Task 3 的 `AudioReceiver.startServer()` 补偿路径：模板与 §20.1 都是内联 `awaitCompletion()` 后
  顺序释放，现改为 `cleanupScope.launch { teardownGate.awaitCompletion(); teardownSteps(...) }`
  —— 内联会被调用方的取消掀掉，且两步裸跑违反本计划自己的"每个 step 只做一件事"（spec §21.2）。
- Task 3 的 `teardownSteps()`：`failure?.addSuppressed(e)` 加 `first !== e` 身份守卫，否则两步抛出
  同一实例时 `addSuppressed` 自抑制抛出、跳过剩余步骤（spec §21.3）；新增对应用例。
- Task 3 的 `AudioActivity`：删除 `pcmOs` / `aacOs` / `opusOs` / `recordType` 四个字段，录音回调改为
  每次录音一个的 `recordCallbackFor(type, out)` —— 这是 Task 2 把 `startRecord()` 失败回滚异步化的
  连带后果，当时没追到调用方（spec §21.1）。
- Task 5 Step 7 新增"录音启动失败后再次录音，第二次文件应非空"场景。
- basenetty 的 `startServer()` bind 加 3 秒上界、`stopServer()` 状态迁移与 `onStopped()` 移入
  `finally`，见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §12（R21 / R22）。

**2026-10-16 第六轮审查后的再次偏离**，以代码与审查包 §J 为准：

- Task 3 的 `AudioReceiver.startServer()`：发布后 re-check 放进 `try { server.startServer() } finally
  { ... }`——`startServer()` 会把调用方的 `CancellationException` 原样抛出，而被取消的调用方同样已发布
  字段（spec §21.2 补正）。
- Task 4 的替代函数 KDoc：`AudioPlayer.releaseAndJoin()`、`MicRecorder.stopRecordAndJoin()`、两个
  stream player 的 `stopPlayingAndJoin()`、`BaseMediaCodec.releaseAndJoin()`、两个 wrapper 接口的
  `releaseAndJoin()` 各追加一段 "Replaces the removed non-suspend X(). Migration: ..."——CLAUDE.md
  「API 演进约定」要求迁移说明写在函数 KDoc 里，之前只写了 CHANGELOG。
- Task 3 的 `ADPCMActivity`：解码块由 `runCatching {}.onFailure {}` 改为与仓库其余位置一致的双
  `catch`；`SuspendTeardownGate` KDoc 写明不可重入；`AudioSender.stop()` 第 1 步注释补上
  "`release()` 在 `UNINITIALIZED` 时不会进 `stopRetryHandler()`，靠的是 start() job 已被 join"。
- CHANGELOG audio 条目补录 `AudioPlayer.releaseAndJoin()` 的释放顺序变更与 demo 音频侧重构。
- basenetty 侧见 `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §13（R23–R26）。

