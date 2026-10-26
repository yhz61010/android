# `review/native-modules` 第二轮详审 · 第 2/3 部分：audio + basenetty + demo 音频/Socket（2026-10-26）

> 本文是 `00-documents/2026-10-20-branch-review-vs-master_cc.md` 第二轮详细审查的**第 2/3 部分**。
> 该文档第 141 行定义了三份切片的范围，第 1/3（录屏与共享基础库）已完成并记录在其 §9–§20；
> 本文承接第 2/3，§编号本地从 §1 重新开始，不接续前文的 §21。第 3/3（原生模块 + demo 其余 +
> 构建脚本）尚未开始。

## 1. 范围与方法

| 项目 | 值 |
|------|----|
| 审查分支 | `review/native-modules`，HEAD `ef51ac0ad` |
| 对比基线 | 与 `origin/master` 的合并基 `97c84d242`（`docs: update Claude repository guidance`） |
| 本分支代码差异 | 249 个文件，+17116 / −4953（已排除 `00-documents/` 与 `.claude/`） |
| 本部分范围 | `audio`（主源码 + 测试）、`basenetty`、demo 音频示例与 Socket 调用方，共 **43 个文件，+4644 / −960**，占分支代码新增的约 27% |
| 方法 | 3 个细审代理按切片通读改动文件全文（不只看 hunk），复核既有文档结论在当前 HEAD 是否仍成立，裁决第一轮遗留的悬置发现，并对未被任何文档覆盖的文件做补审；随后由独立复核代理对每条发现做对抗性核验（缺陷是否真实、行号是否准确、修法能否编译并通过 detekt/ktlint、是否兼容 minSdk 21、是否破坏调用方） |
| 执行方式 | **全部为源码静态分析，未执行 Gradle、未做真机验证。** 文中「确定」指依据公开 Android API 文档或仓库代码可直接证实；「推断」单独标注置信度。所有验证项一律标注「未验证」 |
| 代码改动 | 本轮审查阶段源码无改动 |

### 1.1 与既有文档的覆盖关系

本部分 43 个文件中，**30 个**已被既有文档逐行详审，因此本轮对这部分的动作是「复核旧结论是否仍成立」，而非重新发现；**13 个**不在那份清单内，其中 12 个被另一份文档覆盖，1 个从未被任何文档提到。

| 覆盖来源 | 文件数 | 本轮动作 |
|---|---|---|
| `2026-10-15-audio-netty-teardown-review-package_cc.md`（5342 行，§A audio / §B demo 音频 / §C basenetty 关闭路径） | 30 | 结论复核 |
| `2026-09-02-audio-media-teardown-followup-fixes_cc.md`（2178 行，H1–H5、M10–M17、L11–L17） | 12（与上表无重叠的部分） | 结论复核 + 补审 |
| 无任何文档覆盖 | 1（`demo/src/test/.../audio/PcmFramePaddingTest.kt`） | 完整补审 |

未被 `2026-10-15` 清单列入的 13 个文件及其规模：

```
audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt                    +462/-145
audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt                      +223/-27
audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronous.kt   +145/-100
audio/src/main/kotlin/com/leovp/audio/opus/OpusFramedFileReaderTest.kt          +127/-0
audio/src/main/kotlin/com/leovp/audio/AudioTrackPlayer.kt                       +94/-17
audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronous.kt  +92/-43
audio/src/main/kotlin/com/leovp/audio/opus/OpusFramedFileReader.kt              +84/-0
audio/src/main/kotlin/com/leovp/audio/opus/OpusDecoder.kt                       +53/-13
demo/src/test/kotlin/.../examples/audio/PcmFramePaddingTest.kt                  +32/-0
audio/src/main/kotlin/com/leovp/audio/opus/OpusEncoder.kt                       +25/-26
audio/src/main/kotlin/com/leovp/audio/mediacodec/iter/IAudioMediaCodec.kt       +19/-0
audio/src/main/kotlin/com/leovp/audio/aac/AacDecoder.kt                         +17/-15
audio/src/main/kotlin/com/leovp/audio/aac/AacEncoder.kt                         +10/-3
```

### 1.2 第一轮快审在本范围留下的悬置发现

以下 6 条来自 `2026-10-20-branch-review-vs-master_cc.md` 第一轮快审，从未进入详审，本轮逐条裁决（见 §4）：

| 编号 | 原文位置 | 一句话 |
|---|---|---|
| A1 | 该文档第 70 行 | `AacStreamPlayer` / `OpusStreamPlayer` 的 `stopped.set(true)` 早于 `stopAndJoin()` 自作用域守卫 |
| A2 | 该文档第 72 行 | `MicRecorder.startRecord()` 无重入守卫，`stopRecordAndJoin()` 只 join 最新 job |
| A3 | 该文档第 73 行 | 4 处 `assertFailsWith<TimeoutException>` 无法区分「被锁阻塞」与「尚未调度」 |
| B4 | 该文档第 74 行 | `AudioSender.start()` 在主线程打开 `AudioRecord` 并启动编码器 |
| C5 | 该文档第 71 行 | `BaseNettyClient.releaseNetty()` 超时后 `connectStatus` 永久停在 `RELEASING`，无补偿路径 |
| C6 | 该文档第 102 行 | `closeChannelAndAwait()` 超时后 `terminationFuture` 永不被观察 |

---

## 2. 既有结论复核

既有文档的结论在当前 HEAD `ef51ac0ad` 的状态。**所有「已修复」均为源码结构确认，未经编译或运行验证。**

### 2.1 `2026-09-02-audio-media-teardown-followup-fixes_cc.md` 的阻塞级条目

| 条目 | 裁决 | 证据 |
|---|---|---|
| **H2** OPUS 主线程挂死 | **已修复** | `OpusFilePlayer.kt:195` 以 `queue.offer(pcmData)` 取代阻塞 `put()`，满队列走 `:199-200` 丢弃并报错；`:194` 另加 `terminalStarted` 快路径。持 `codecOperationLock` 可达的每个回调体都已有界（`onDecoded→offer`、`OpusDecoder.kt:75 onInputData→queue.poll()`、`:203 endCallback→codecEos.complete()`、`:204 errorCallback→terminalScope.launch{}`）。`queue.clear()` 移到 `:491`（join 之后的 finally）。死锁环已断 |
| **H3** 停止竞态崩溃 | **已修复** | `OpusFilePlayer.kt:480-486` 严格按「`playbackScopeJob.cancel()` → `closeInputFile()` 唤醒阻塞文件读 → `audioTrackPlayer.stop()` 唤醒阻塞 `AudioTrack.write` → `cancelAndJoin()` → `decoder.releaseAndJoin()`」执行，整体在 `:473 withContext(NonCancellable)` 内，`audioTrackPlayer.release()` 在 `:492` 的 join 之后。`stop()` 已是 `suspend` 且自带 `withContext(Dispatchers.IO)`（`:163-165`） |
| **H4** 第二次 `playAac()` 把 H2/H3 带回 | **已修复** | `AacFilePlayer.kt:161` 的 `check(started.compareAndSet(false, true))` 是 `playAac` 字面第一条语句，位于 `cb=`（`:162`）、`MediaExtractor()`（`:168`）、`audioTrackPlayer.play()`（`:183`）、`start()`（`:184`）之前且在 `try` 之外 → 重复调用不触发任何清理，现有会话逐字节不受影响。`OpusFilePlayer.kt:125` 有同款闸。旧的 `releaseAfterStartFailure()` 已不存在 |
| **H5 / M18** AAC 自然 EOF 泄漏 fd 与 AudioTrack | **已修复** | `AacFilePlayer.kt:134-137` 的 `onEndOfStream()` 改为 `terminalScope.launch { finishPlayback(NaturalEnd) }`；该路径 `:230` 先排空 AudioTrack、`:233-234` `stop()`+`releaseAndJoin()`、`:239 releaseExternalResources()`（`:299-308` 释放 `MediaExtractor` 与 `AudioTrack`），全部完成后才在 `:266` 调完成回调 |
| **M10** EOF 回调无合法停止途径 | **形态已变，风险闭环** | `endCallback` 仍是 `() -> Unit`（`:158`），但 `:253 terminalCompletion.complete(Unit)` 发生在 `releaseAndJoin()` 返回之后、客户端回调之前，所以从回调里 `runBlocking { stop() }` 会命中 `:217-221` 的重复分支立即返回，不再 join 自身；且回调运行在 `terminalScope`（IO）而非 codec worker。**残留**：公开 KDoc 未写明回调所在线程，见 §3 L-7 |
| **M13** 同步尾音丢失 / `onEndOfStream()` 双触发 | **已修复** | `BaseMediaCodecSynchronous.kt:113` 返回 `!outputEosReceived`；`:51-73` 输入 EOS 后进入带 3 秒上界的排空循环；`:76` 是全文件唯一的 `onEndOfStream()` 调用点且由 `outputEosReceived` 守卫，结构上不可能双触发 |
| **M14** 两条公开 API 破坏未记入 CHANGELOG | **已修复** | `CHANGELOG.md:203-205` 已写明 `AacDecoder.decode()` 的 JVM 描述符由 `([B)V` 变为 `([B)Z`、预编译调用方不重编译会得到运行期 `NoSuchMethodError`，以及非 `RUNNING` 状态调用 `flush()` 现在抛 `IllegalStateException` |
| **M15 / M32** 钩子锁契约未文档化 | **已修复** | `BaseMediaCodec.kt:79-82`、`:85-88`、`:136-141`、`:144-150`、`:205-212`、`:262-265`、`:277-280` 全部补了锁契约；`IAudioMediaCodec.kt:24-26`、`:32-35`、`:43-46`、`:87-90`、`:94-97` 即本切片 `+19` 行的内容。**但该 KDoc 对同步路径不成立**，见 §3 L-1 |
| **M16** `codecJob` 非 `@Volatile` | **已修复** | `BaseMediaCodec.kt:55-56` 已标 `@Volatile`，`:174-176` 补了重读排序理由 |
| **M17** 一次性会话测试缺口 | **已修复** | `BaseMediaCodecAsynchronousTest.kt:254`（并发 start）、`:285`（重复 release 恰好一次）、`:183`/`:210`（迟到 output 回调） |
| **M19 / L16** 重复 AAC CSD 覆盖并泄漏 decoder | **已修复（owner 侧）** | `AacStreamPlayer.kt:97-120` 短包先比对 `activeCsd`，活动会话中重复或变化的 CSD 一律拒绝；`OpusStreamPlayer.kt:105` 为 `csd0 == null && size == 83` 守卫。`AacDecoder`/`OpusDecoder` 自身无对应守卫，但 `BaseMediaCodec.kt:99` 的一次性 `start()` 使二次启动必抛，所有权只由 owner 持有，故无需 decoder 侧守卫 |
| **M24** 输入 EOS 后无墙钟上界 | **worker 侧已修复，join 侧仍成立** | `BaseMediaCodecSynchronous.kt:44`、`:56` 加了 `eosDrainTimeoutMs`（3 s）；但 `BaseMediaCodec.kt:172`、`:177` 的两次 `cancelAndJoin()` 在 `NonCancellable` 下仍无上界（属基类，既有文档 §10.2 已记录为有意选择） |
| **M30** 异步输出行为变更未记录 | **已修复** | `CHANGELOG.md:466-467` 三条（空非 config 输出不再回调、带 payload 的 EOS 先 `onOutputData` 再 `onEndOfStream`、`onOutputFormatChanged` 现在真正触发）均已记录 |
| **M31** 三个数据回调的锁契约 | **形态已变：已文档化，未结构性消除** | KDoc 已补，但 `OpusDecoder.kt:111-113` 仍在锁内调用调用方提供的 `endCallback` |
| **L11** `mime!!` | **已消除** | `AacFilePlayer.kt:91-92` 改用 `requireNotNull` |
| **L12** `stop()` 体内阻塞调用无 dispatcher | **已消除** | `AacFilePlayer.kt:213`、`OpusFilePlayer.kt:164` 都加了 `withContext(Dispatchers.IO)` |
| **L18** `pause()` 内 `flush()` 紧随 `pause()` | **仍成立** | `AudioTrackPlayer.kt:251-256` 的 `audioTrack.pause()` 后立刻 `audioTrack.flush()`；`stop()`（`:274`）仍先调 `pause()`，消费者此时未必已 join |
| **L26** `write()` 失败与「什么都没写」不可区分 | **仍成立且本切片放大** | `AudioTrackPlayer.kt:209-212` 新增 `getOrElse { ... 0 }`，异常路径也返回 0，见 §3 L-4 |
| **L28** 补偿用裸 `runCatching` / output index 不归还 | **已修复** | `BaseMediaCodecSynchronous.kt:153`、`BaseMediaCodecAsynchronous.kt:72` 改用 `runCatchingPreservingCancellation`；`BaseMediaCodecSynchronous.kt:191-193`、`BaseMediaCodecAsynchronous.kt:110-112` 的 `finally` 保证归还 index |
| **H8** codec 失败绕过终态 | **仅文件播放器闭环，流播放器仍成立** | `AacFilePlayer.kt:138-142` 有 `notifyCodecFailure` 覆写；`AacDecoder` 无覆写，`AacStreamPlayer.kt:65-90` 与 `OpusStreamPlayer.kt:77-97` 均未接错误回调，见 §3 M-3 |

### 2.2 `2026-10-15-audio-netty-teardown-review-package_cc.md` 覆盖的 30 个文件

该文档 §A/§B/§C 的结论本轮未发现失效项。`BaseNettyClient.kt` 自该批提交（`0f84de546`，与文档提交 `2a259b5b3` 同一时间戳）以来未再改动，故其行号引用仍逐条对齐（抽查 8 处，见 §4 C5）。

---

## 3. 新发现

严重度按 `.claude/rules/common/code-review.md`：CRITICAL 阻断合并、HIGH 应在合并前修、MEDIUM 建议修、LOW 可选。**本节全部修复方案均未编译、未运行、未真机验证。**

统计：CRITICAL 0 ／ HIGH 1 ／ MEDIUM 9 ／ LOW 12。

### H-1（HIGH）异步 `flush()` 后未 `start()`，OPUS 流播放首次丢帧即永久静音

- 文件：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt:230-238`（基类实现）、`BaseMediaCodecAsynchronous.kt`（**缺少 `flush()` 覆写**）、调用点 `audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt:213`
- 事实（整条链已逐环核对源码）：
  1. `BaseMediaCodec.flush()` 的全部动作只有 `codec.flush()`，无 `start()`。
  2. `BaseMediaCodecAsynchronous` 没有覆写 `flush()`，而它在 `:28` 调 `codec.setCallback(mediaCodecCallback)` → 其所有子类都运行在 MediaCodec **异步模式**。
  3. Android 对 `MediaCodec.flush()` 的公开契约：*"If the codec is configured in asynchronous mode, call `start` after `flush` has returned to resume codec operations. The codec will not request input buffers until this has happened."*
  4. `OpusDecoder : BaseMediaCodecAsynchronous`（`OpusDecoder.kt:35`），而 `OpusStreamPlayer.kt:213` 在丢帧分支调 `decoder.flush()`，前后只有 `decodedPcmQueue.clear()` 与 `audioTrackPlayer.pause()/play()`（`:214-216`），**无任何 `start()`**。
  5. `BaseMediaCodec.start()` 是一次性的（`:99` 的 `NEW -> STARTING` CAS），无法再调用来恢复。
  6. `BaseMediaCodec.kt:193` 另一处 `codec.flush()` 在 `releaseCodecOnce()` 内紧跟 `codec.release()`，不需要 start，不属本缺陷。
- 触发条件：**常规事件，非异常路径** —— `OpusStreamPlayer.decodeOrDropLocked()` 的丢帧分支，`decoder.queueSize >= 64` 或 `abs(latencyInMs) > audioLatencyThresholdInMs`（初值 `AUDIO_ALLOW_LATENCY_LIMIT_IN_MS = 500`，`:42`、`:145`）。
- 后果：flush 后编解码器不再请求输入缓冲 → `onInputBufferAvailable` 不再回调 → 队列填满 64 槽后 `decode()` 持续返回 false → **本次会话 OPUS 流永久静音**，而 `isRunning` 仍为 true、无任何错误上报（叠加 M-3）。只能整体 stop 并新建实例恢复。
- 范围：`AacStreamPlayer.kt:220` 的 `flush()` 作用在 `AacDecoder : BaseMediaCodecSynchronous` 上，同步模式无此契约，**不受影响**。
- 确定性：**确定**（Android 公开文档的显式契约 + 仓库内可达调用路径 + 代码中确无 `start()`）。
- 附带（**推断，置信度 0.6**）：flush 与在飞异步回调之间无世代校验，`onOutputBufferAvailable` 的 `finally`（`BaseMediaCodecAsynchronous.kt:110-112`）可能用失效 index 调 `releaseOutputBuffer()` 而抛出，被 `:114-119` 升级成一次伪 codec 失败。
- 修复方案（放在 `BaseMediaCodecAsynchronous`，与同步路径对称）：

```kotlin
/**
 * Asynchronous codecs stop requesting buffers after a flush. MediaCodec.flush() requires
 * start() to resume codec operations, so do both under one lock acquisition to keep stale
 * callbacks out of the gap.
 */
override fun flush() {
    withCodecOperationLock {
        super.flush()
        runCatchingPreservingCancellation { codec.start() }
            .onFailure { LogContext.log.e(TAG, "restart after flush error", it) }
    }
}
```

可行性已核：`codecOperationLock` 是 `ReentrantLock`（`BaseMediaCodec.kt:62`），同线程嵌套取锁安全；`codec` 是 `internal lateinit var`（`:47`）、`withCodecOperationLock` 是 `protected`（`:259`），子类均可访问；`super.flush()` 自带 `check(lifecycleState == RUNNING)`，非运行态会在 `start()` 之前抛出，顺序正确。

### M-1（MEDIUM）AAC 把「AudioTrack 一个字节都没收下」当成功，整段播放可无声地「自然结束」

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt:124-128`
- 事实：`val writtenBytes = audioTrackPlayer.write(chunkPCM)` 的返回值只用于计帧，`if (writtenBytes > 0) { ... }`，`<= 0` 既不记日志也不报错。而 `AudioTrackPlayer.kt:190` 的 `if (PLAYSTATE_PLAYING == audioTrack.playState)` 不成立时 `wroteSize` 保持 0 并在 `:208` 返回 0，**全程无日志**；`:195-198` 的负错误码（如 `ERROR_DEAD_OBJECT = -6`）原样返回，同样落进 false 分支被丢弃。随后 worker 读完文件 → `BaseMediaCodecSynchronous.kt:76 onEndOfStream()` → `AacFilePlayer.kt:136` → `NaturalEnd` → `:266` 调 `endCallback`。
- **同族已把该危害写在代码里**：`OpusFilePlayer.kt:310-317` 对同一情形有两道 `check` 并配四行英文注释，明确「把它算作已消费会以成功回调结束一次什么都没播的会话，比报错更难诊断」。两个播放器对同一风险处理相反。
- 触发条件：`play()` 成功后 AudioTrack 离开 `PLAYSTATE_PLAYING`（被其他组件 pause、音频焦点/路由异常），或 audioserver 重启导致 `ERROR_DEAD_OBJECT`。
- 后果：整首 AAC 被解码并静默丢弃，`endCallback` 照常触发，Demo 把按钮恢复成「播完了」，调用方拿不到任何线索；`ERROR_DEAD_OBJECT` 这类硬错误被完全吞掉，违反 `coding-style.md` 的「Never silently swallow errors」。
- 确定性：**确定**（代码路径与返回值语义可直接读出）；哪些设备状态会让 `playState` 掉出 PLAYING 属运行期因素，未验证。
- 修复方案（与 OPUS 对齐，经已有的 `notifyCodecFailure` 终态通道）：

```kotlin
val writtenBytes = audioTrackPlayer.write(chunkPCM)
// A non-positive write means the track is no longer playing or the audio server is gone.
// Reporting it beats ending the session through endCallback with nothing played, which is
// far harder to diagnose. Teardown already ignores this once terminal cleanup has begun.
if (writtenBytes <= 0) {
    if (!terminalStarted.get()) {
        notifyCodecFailure(
            IllegalStateException("AudioTrack accepted no data (code=$writtenBytes)")
        )
    }
    return
}
val pcmFrameBytes = Short.SIZE_BYTES * audioDecodeInfo.channelCount
writtenAudioFrames.addAndGet((writtenBytes / pcmFrameBytes).toLong())
```

`notifyCodecFailure` 已是 `:139-143` 的 `terminalScope.launch`，非阻塞，可安全在持 `codecOperationLock` 的 worker 回调内调用。

### M-2（MEDIUM）`AacFilePlayer` 暴露继承来的 `releaseAndJoin()`，而它只释放 MediaCodec

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt:49-53`（`: BaseMediaCodecSynchronous(...)`）、`BaseMediaCodec.kt:98`/`:164`/`:230`
- 事实：`BaseMediaCodec.start()`、`suspend fun releaseAndJoin()`、`open fun flush()` 均为 **public**，`AacFilePlayer` 是 public 类并直接继承，故三者是其公开 API 的一部分。`releaseAndJoin()` 的 KDoc（`:152-163`）自称 deterministic lifecycle teardown，但它只做 `ioScope.cancel()` + join worker + `releaseCodecOnce()`；`MediaExtractor` 与 `AudioTrack` 的释放只存在于 `AacFilePlayer.kt:299-308 releaseExternalResources()`，而该函数**仅**被 `finishPlayback()`（`:239`）调用。`releaseAndJoin()` 不是 `open`，无法覆写收口。
- 对照：`OpusFilePlayer` 用组合（`:97 private var decoder: OpusDecoder? = null`）而非继承，不存在该缺口。
- 触发条件：调用方按 CLAUDE.md「释放类 API 统一为挂起函数（`releaseAndJoin()` …）」的约定，对 `AacFilePlayer` 调 `releaseAndJoin()` 而非 `stop()`。
- 后果：codec 释放、worker 退出，但 `MediaExtractor`（持文件 fd）与 `AudioTrack`（native 混音器资源）泄漏；`terminalStarted` 仍为 false，`endCallback`/`errorCb` 引用不被清空（延长对 Activity lambda 的持有）。后续再调 `stop()` 可补救，但语义上调用方已认为释放完成。
- 确定性：**确定**（可见性与调用关系可直接读出）。Demo 走的是 `AudioActivity.kt:365-367` 的 `player.stop()`，故为 API 契约缺口而非活跃 bug。
- 修复方案：**本项需作者决策**，两个方向代价差异大：
  - **方案 A（彻底，推荐但工作量大）**：把 `AacFilePlayer` 从 `BaseMediaCodecSynchronous` 的子类改为持有者，与 `OpusFilePlayer` 对齐 —— 新增私有 `AacFileDecoder : BaseMediaCodecSynchronous`，`AacFilePlayer` 只暴露 `playAac()`/`stop()`。这是结构性重构，会改变公开继承关系，须记入 CHANGELOG「变更」。
  - **方案 B（最低限度）**：在 `AacFilePlayer` 类级 KDoc 写明「继承而来的 `start()`/`flush()`/`releaseAndJoin()` 不是本类的生命周期入口，唯一释放入口是 `stop()`」。只是缓解，无法阻止误用。

### M-3（MEDIUM）codec 失败不改生命周期状态，`isRunning` 仍为 true；两个流播放器没有失败通道

- 文件：`audio/.../aac/AacDecoder.kt:106-109`、`opus/OpusDecoder.kt:100-101`、`aac/AacEncoder.kt:236`、`opus/OpusEncoder.kt:235`
- 事实：投递入口统一是 `isRunning && queue.offer(...)`。`isRunning`（`BaseMediaCodec.kt:77`）只看 `lifecycleState == RUNNING`；同步失败路径（`BaseMediaCodecSynchronous.kt:88-104`）只做 `codecFailed.set(true)` + `reportCodecFailure(e)`，异步失败路径（`BaseMediaCodecAsynchronous.kt:79-84`、`:114-119`、`:142`）同样只上报，**两者都不改 `lifecycleState`**。`AacDecoder` 没有覆写 `notifyCodecFailure`（全模块仅 `OpusDecoder.kt:115` 与 `AacFilePlayer.kt:138` 有），失败只落到 `BaseMediaCodec.kt:220-222` 的一条 `log.e`。`AacStreamPlayer.kt:65-90` 与 `OpusStreamPlayer.kt:77-97` 构造 decoder 时都只传 `IDecodeCallback`，`OpusStreamPlayer` 走位置参数，`endCallback`/`errorCallback` 取默认 `{}`（`OpusDecoder.kt:33-34`）。
- 触发条件：任何 `CodecException`/`IllegalStateException`/子类回调异常（如损坏 AAC 帧，或帧超过 `KEY_MAX_INPUT_SIZE = 8 * 1024`（`BaseMediaCodec.kt:272`）时 `inBuf.put(input)` 抛 `BufferOverflowException`）。
- 后果：worker 退出或 codec 进入错误态后 `isRunning` 仍为 true → 流播放器继续入队 → 队列填满 → `decodeOrDropLocked` 进入「丢帧 + flush + resync」死循环（在 OPUS 上叠加 H-1）→ **永久静音且调用方永不知情**，`MediaCodec`/`AudioTrack` 一直被持有。文件播放器不受影响（已接错误回调）。
- 确定性：机制**确定**；真机发生频率取决于码流质量（推断）。
- 修复方案（两步，任一步单独生效）：

```kotlin
// 1) AacDecoder: give it the same failure channel OpusDecoder already has.
class AacDecoder(
    // ...
    private val callback: IDecodeCallback,
    private val errorCallback: (Throwable) -> Unit = {},
) : BaseMediaCodecSynchronous(/* ... */) {

    override fun notifyCodecFailure(error: Throwable) {
        LogContext.log.e(TAG, "AAC decoder failed", error)
        errorCallback.invoke(error)
    }
}
// 2) Both stream players must wire errorCallback to their one-shot stop path, so a dead
//    codec tears the session down instead of silently dropping every frame.
```

### M-4（MEDIUM）异步路径缺输入 EOS 守卫，EOS 之后仍会 `queueInputBuffer()`

- 文件：`audio/.../mediacodec/BaseMediaCodecAsynchronous.kt:32-85`（对比 `BaseMediaCodecSynchronous.kt:117-118`）
- 事实：
  - 同步路径有显式状态位：`queueInputIfNeeded()` 第一行 `if (inputEosQueued) return`（`:117-118`），`inputEosQueued` 在提交 EOS 后立即置位（`:141`）。
  - 异步路径**完全没有等价状态**：`onInputBufferAvailable` 只检查 `isReleasing`（`:33`、`:36`），EOS 提交（`:54-60`）后不记录任何标志。
  - `OpusDecoder` 的 EOS 标记是一次性队列元素，`onInputData`（`:74-81`）为 `currentInputIsEos = input === eosMarker`。**队列为空时 `input == null`，该赋值使标志回落为 `false`**，于是 `computePresentationTimeUs()`（`:94-98`）返回非负的 `currentInputPtsUs`，`BaseMediaCodecAsynchronous.kt:67` 会提交一个 0 字节的**普通**输入缓冲 —— 在输入 EOS 之后。（此处的回落机制已逐行核对源码确认。）
  - 该窗口不是理论窗口：MediaCodec 输入回调在单个 handler 上串行投递，EOS 被处理时通常已有若干 `onInputBufferAvailable` 消息排在队列里。
- 触发条件：`OpusFilePlayer` 的每一次自然结束（`OpusFilePlayer.kt:262` → `signalEndOfStreamWithBackpressure`）。
- 后果：违反 MediaCodec「提交 EOS 后不得再提交输入」的文档契约。具体表现取决于设备实现（**推断，置信度 0.6**）：抛 `IllegalStateException` → 被 `:114-119` 转成 codec 失败 → `OpusFilePlayer.kt:204` 的 `errorCallback = { requestFailure(it) }` 把一次正常播完的文件报成失败；或输出 EOS 永不到达，落到 codec EOS 的 3 秒超时。
- 确定性：结构性缺失与可达路径**确定**；设备侧后果**推断**。
- 修复方案（一次性会话，无需重置）：

```kotlin
/** Set once the input EOS buffer has been queued; a one-shot session never reuses it. */
private val inputEosQueued = AtomicBoolean(false)

override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
    if (isReleasing || inputEosQueued.get()) return
    // ... existing body; right after the EOS queueInputBuffer() succeeds:
    //     inputEosQueued.set(true)
}
```

### M-5（MEDIUM）异步 `onOutputFormatChanged` 无异常边界，子类抛出即杀 MediaCodec 回调线程

- 文件：`audio/.../mediacodec/BaseMediaCodecAsynchronous.kt:122-131`
- 事实：同一个 `MediaCodec.Callback` 的其余三个回调都被 `runCatchingPreservingCancellation { ... }.onFailure { ... reportCodecFailure(it) }` 包住（`:34`/`:79`、`:93`/`:114`、`:135`/`:140`），只有 `onOutputFormatChanged` 是裸的。同步路径相反：`drainOutput()` 的 `onOutputFormatChanged(codec, outputFormat)`（`BaseMediaCodecSynchronous.kt:172`）在 `process()` 的 try 内，异常被转成 `reportCodecFailure`（`:88-104`）。
- 触发条件：任何覆写 `IAudioMediaCodec.onOutputFormatChanged` 的子类抛出异常。该钩子**本切片刚从死 API 变为真正触发**（`CHANGELOG.md:467`），仓库内尚无覆写，风险全在下游。
- 后果：异常逃到 MediaCodec 回调 handler 线程 → 未捕获异常 → 进程崩溃；而同一异常在同步路径上只是一次 codec 失败上报。
- 确定性：**确定**（代码结构可证）；仓库内无可达触发点。
- 修复方案：

```kotlin
override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
    if (isReleasing) return
    runCatchingPreservingCancellation {
        withCodecOperationLock {
            if (isReleasing) return@withCodecOperationLock
            this@BaseMediaCodecAsynchronous.format = format
            this@BaseMediaCodecAsynchronous.onOutputFormatChanged(codec, format)
        }
    }.onFailure {
        if (!isReleasing) {
            LogContext.log.e(TAG, "Format changed callback failed", it)
            reportCodecFailure(it)
        }
    }
}
```

### M-6（MEDIUM）同步 `onEndOfStream()` 无异常边界，抛出即逃到无兜底的 `ioScope`

- 文件：`audio/.../mediacodec/BaseMediaCodecSynchronous.kt:76`、`BaseMediaCodec.kt:49`
- 事实：`:76` 是 `ioScope.launch { ... }` 协程体的最后一条语句，裸调用 `if (!codecFailed.get() && !isReleasing && outputEosReceived) onEndOfStream()`；协程体内其余异常都已被 `process()`（`:86-104`）或 `reportCodecFailure()`（`BaseMediaCodec.kt:225-228`）吸收。而 `ioScope = CoroutineScope(Dispatchers.IO + CoroutineName("base-mediacodec"))`（`BaseMediaCodec.kt:49`）**既无 `SupervisorJob` 也无 `CoroutineExceptionHandler`**，根 `launch` 抛出即走默认未捕获处理器。异步路径相反：`onEndOfStream()`（`BaseMediaCodecAsynchronous.kt:109`）在 `runCatchingPreservingCancellation` 内。
- 触发条件：子类或 owner 的完成回调抛出。仓库内 `AacFilePlayer.kt:134-136` 只做 `terminalScope.launch{}`，不抛；风险在下游实现与 owner lambda。
- 后果：进程崩溃，且 codec 与 `AudioTrack` 的确定性释放被跳过。
- 确定性：**确定**（机制）；仓库内无可达触发点。
- 修复方案：

```kotlin
// Owner completion callbacks must not kill the worker coroutine: ioScope carries no
// CoroutineExceptionHandler, so an escaping throwable would reach the platform handler.
if (!codecFailed.get() && !isReleasing && outputEosReceived) {
    runCatchingPreservingCancellation { onEndOfStream() }
        .onFailure { LogContext.log.e(TAG, "End of stream callback failed", it) }
}
```

### M-7（MEDIUM）H2/H3/H4/H5 的修复仍然零回归测试

- 文件：`audio/src/test/` 下无 `OpusFilePlayerTest` / `AacFilePlayerTest`；本切片唯一新增测试是 `OpusFramedFileReaderTest.kt`，只覆盖被抽出的扫描器
- 事实：`audio/src/test` 共 6 个测试文件，播放器层一个也没有。`OpusFilePlayer.kt:480-486` 的释放顺序（H3）、`:195` 的非阻塞 offer（H2）、`AacFilePlayer.kt:161` 的一次性闸（H4）、`:134-137` 的 EOF 自动释放（H5）全部可以被改回旧写法而不会有任何测试变红。既有文档 `2026-09-02-...md:1141` 已记录同一结论，说明该缺口跨轮未闭合。
- 后果：本切片四个阻塞级修复没有任何自动化保护；§4 M4 那种「reader 测试祝福、player 实现拒绝」的契约撕裂正是缺少播放器层测试的直接产物。
- 确定性：**确定**。
- 修复方案：`BaseMediaCodecAsynchronousTest` / `BaseMediaCodecSynchronousTest` 已证明这类生命周期时序在 JVM + mockk 上可测。建议至少补 4 个用例（可用 Robolectric 提供 `AudioTrack`/`MediaCodec` 替身，或先把 `AudioTrackPlayer` 抽成接口以便注入）：
  1. `playAac()`/`playOpus()` 第二次调用抛异常且**不改动**任何现有会话状态（H4）；
  2. 自然 EOF 后 `endCallback` 触发前 `MediaExtractor.release()`/`AudioTrack.release()` 已各调恰好一次（H5）；
  3. 消费协程阻塞在 `write` 时 `stop()` 能在界限内返回，且 `release()` 严格晚于 join（H3）；
  4. 以 start code 结尾的 .opus 文件走 `endCallback`（§4 M4 的回归锁）。

### M-8（MEDIUM）demo 三个录音开关互不排斥，`micRecorder` 被覆盖即泄漏

- 文件：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt:120-130`、`:321`；布局 `demo/src/main/res/layout/activity_audio.xml`
- 事实（已核实）：布局是六个独立 `ToggleButton`（`:9/:19/:30/:40/:49/:105`），**没有 `RadioGroup`**；三个录音开关各自 `if (isChecked) record(AudioType.X) else stopRecording()`。先开 PCM 再开 AAC，`:321` 的 `micRecorder = MicRecorder(...)` 直接覆盖上一个引用。
- 后果：第一个 `MicRecorder` 的 `AudioRecord`、编码器、record job 全部失去引用，永不 `stopRecordAndJoin()` → 泄漏；两个 `AudioRecord` 会话并发录同一麦克风。
- 关系：这是 §4 A2 的 demo 侧对偶 —— 库侧缺 `startRecord()` 的一次性闸门，Activity 侧缺「新建前先停旧的」。
- 是否本切片引入：**不是**，merge base（`97c84d242:demo/.../AudioActivity.kt:247`）形状相同，**预存**。
- 确定性：**确定**。
- 修复方案：`record()` 开头先 `stopRecording()`，或把三个 ToggleButton 改成互斥（RadioGroup / 单选逻辑）。前者改动小，后者更符合交互语义。

### M-9（MEDIUM）本切片内一条既有 ktlint 违规使整个 `staticCheck` 无法通过

- 文件：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/receiver/AudioReceiver.kt:164-166`
- 事实：

```kotlin
/** Builds and starts the recorder; the caller publishes it and re-checks `stopping`. */
private fun startMicRecording(): MicRecorder {
    return MicRecorder(
```

  单 return 表达式体，ktlint 标准规则 `function-expression-body` 要求写成 `= MicRecorder(...)`。
  `.editorconfig` 未关闭该规则（只设了 `max_line_length = 100`），而项目 ktlint 为
  `ignoreFailures = false`。
- 后果：`demo:ktlintCheck` 失败 → **`./gradlew staticCheck` 在本分支红**。这不是本切片新引入的，
  但它使 §5.1 的验证命令无法整体通过，也挡住本文档所有修复方案的验证路径。
- 确定性：**确定**（已读源码确认规则违反）。Codex 在
  `2026-10-20-branch-review-vs-master_cc.md` §21.2 记录其实际运行 `demo:ktlintCheck` 时仍被这一条
  且仅这一条阻断。
- 为何本轮细审未发现：`AudioReceiver.kt` 属 §1.1 中「已被 `2026-10-15` 审查包覆盖的 30 个文件」，
  本轮对该桶只做结论复核，未重新过一遍 lint。**这是本轮方法上的缺口**：既有覆盖只保证逻辑结论
  被复核过，不保证静态检查仍然通过。
- 修复方案：

```kotlin
/** Builds and starts the recorder; the caller publishes it and re-checks `stopping`. */
private fun startMicRecording(): MicRecorder = MicRecorder(
    // ... existing arguments unchanged ...
)
```

  注意随之而来的缩进变化须满足 `max_line_length = 100`；改完应以
  `./gradlew :demo:ktlintCheck --rerun-tasks` 单独确认，再跑整套 `staticCheck`。

### LOW 条目

| 编号 | 文件 | 一句话 | 确定性 |
|---|---|---|---|
| **L-1** | `IAudioMediaCodec.kt:94-98` | 新增 KDoc 写「Called while the codec operation lock is held」，**对同步路径不成立** —— `BaseMediaCodecSynchronous.kt:76` 在 worker 协程里、锁外、且在该 worker 自身的 Job 上调用。实现若在同步路径从该回调调 `releaseAndJoin()`，会命中 `BaseMediaCodec.kt:165` 的 `require(codecJob !== currentCoroutineContext()[Job])` 而抛出，KDoc 未提示。19 行新 KDoc 也都没写「实现抛出时会发生什么」，而三条路径处理各不相同（异步转 codec 失败、同步 `onInputData`/`onOutputData` 转 codec 失败、同步 `onEndOfStream` 杀进程）。另 `AacDecoder.kt:90` 的 `queue.poll(50, MILLISECONDS)` 持锁阻塞最多 50 ms，与同份 KDoc 的「must return promptly」字面冲突（有界，契约应写「有界」而非 promptly） | 确定 |
| **L-2** | `BaseMediaCodecSynchronous.kt:88-93` | 同步路径**从不调用** `onError()`（全文件 `grep` 零命中），只调 `reportCodecFailure(e)`；异步路径（`BaseMediaCodecAsynchronous.kt:138`）先调子类 `onError` 再上报。覆写 `onError` 的同步子类拿不到 `CodecException`，而 KDoc（`IAudioMediaCodec.kt:87-91`）描述得像两条路径一致。建议改 KDoc 写明「只有异步路径触发 `onError`，同步路径统一走 `notifyCodecFailure`」 | 确定 |
| **L-3** | `AacEncoder.kt:80-81` | `csd0` 仍是非 `@Volatile` 的公开读取面；本切片已把 `OpusEncoder` 的三个 CSD 字段合并为单个 `@Volatile`（`:179-184`），两者不对称。`csd0` 写入点分属三个线程（`:91`/`:109`/`:240`，都在锁内），而公开 getter 供外部任意线程不持锁读取 → 无 happens-before，外部消费方可能长期读到 `null`。仓库内无读取方 | 确定（JMM）；下游影响推断 |
| **L-4** | `AudioTrackPlayer.kt:187-212`、`:199-202` | `write()` 现在整体包进 `runCatchingPreservingCancellation { ... }.getOrElse { ...; 0 }`，异常**不再外抛**（合并基无 try/catch，`IllegalStateException` 会传给调用方）。这是公开 API 行为变更，`CHANGELOG.md:259-261` 只记了奇数字节与负错误码，**此条遗漏**。另 `logRouting("first-write")`（`:199-202`）位于已成功写入之后、返回值之前且在 `runCatching` 内：若 `routedDevice`/`getDevices()` 抛出，**已真实写入的字节会被报成 0** → `AacFilePlayer.kt:120-126` 的 `writtenAudioFrames` 少计 → `awaitAudioTrackDrain()` 提前退出剪掉尾音（L26 的延伸）。仅 debug 构建。次要：该调用点在 `AacStreamPlayer` 路径上处于 codec 操作锁内，debug 构建会在锁内多做 4 次 AudioManager binder 调用 | 确定 |
| **L-5** | `BaseMediaCodecSynchronous.kt:46` | worker 的唯一安装点 `override fun onCodecStarted()` 未标 `final`，而基类该方法是 `protected open`（`BaseMediaCodec.kt:150`）。同步子类覆写而不调 `super` 会让 `codecJob` 永不赋值：`start()` 成功、`isRunning` 为 true、`decode()` 返回 true，但没有任何东西排空队列。仓库内两个同步子类都未覆写。建议 `final override` 并把子类启动点收敛到 `onBeforeCodecStart()` | 确定 |
| **L-6** | `OpusFramedFileReaderTest.kt:85-99` | `` `overlapping start code prefix remains payload data` `` 是**半表演用例**：代理用脚本实证，现实现与「失配即重置且不回看当前字节」的错误实现对该输入给出相同结果（`payload = "\|le\|lex"` 的第二个前缀被 `x` 打断，不会长成真正匹配）。且对 `"\|leo\|"` 这个 start code，`prefixTable` 在循环中实际用到的 `prefix[0..3]` 全为 0，KMP 与「重置+回看」完全等价，`buildPrefixTable`（`:72-83`）当前**没有任何用例能证伪**。其余 6 个用例逐条判为真实断言（含 `:101` 跨 8KB 缓冲边界那条，能证伪 `:57` 的绝对位置算式）。建议补两条：前缀后紧跟真 start code、自重叠 start code（如 `"aab"`，`prefixTable = [0,1,0]`） | 确定（已实证） |
| **L-7** | `OpusFilePlayer.kt:130-136`、`BaseMediaCodecAsynchronous.kt:27-29` | `setCallback(cb)` 单参版使用 MediaCodec 构造时确定的 handler，而 `MediaCodec` 构造取 `Looper.myLooper() ?: Looper.getMainLooper()`；`createCodec()` 经 `OpusDecoder.start()` 在 `Dispatchers.IO`（无 Looper）执行 → **回调全部投递到主 Looper**。因此 `onDecoded`（`:193`）、`endCallback`（`:203`）、`errorCallback`（`:204`）及每帧的 `withCodecOperationLock` 获取都在主线程。当前这些回调体都有界非阻塞（即 H2 的修复内容），不是活跃 bug，但这正是 H2 的回归面且未写进任何 KDoc。`minSdk = 21` 不能无条件用 API 23 的 `setCallback(cb, handler)`，建议先把该约束写进 KDoc（含 `playOpus`/`playAac` 的「回调在播放器自己的 IO 作用域上，不是调用方线程」—— Demo 的 `runOnUiThread` 包裹即对此的隐式依赖，`AudioActivity.kt:190`、`:229`），彻底方案是用专用 `HandlerThread` 创建 codec | 推断（高置信度，机制来自 AOSP，需真机打印线程名确证） |
| **L-8** | `OpusFramedFileReader.kt:28-36`、`OpusFilePlayer.kt:82-84` | `require(payloadSize in 0..Int.MAX_VALUE.toLong())` 之后直接 `ByteArray(payloadSize.toInt())`，上界 2 GiB —— 只在开头含一次 start code 的损坏文件会按「剩余文件长度」一次性分配。OPUS 单包最大约 1275 字节/通道流，兆字节级 payload 必然是损坏数据。后续 `OpusDecoder.kt:78 inBuf.put(input)` 会因超 `KEY_MAX_INPUT_SIZE`（8 KiB）抛 `BufferOverflowException` 并干净失败，故**不会崩溃**，但错误点从「reader 输入校验」漂移到「codec 缓冲溢出」，诊断成本高。建议加 `maxPayloadSize`（默认 8 KiB）。另 `ioScope`（`:82-84`）只有 `SupervisorJob + Dispatchers.IO + CoroutineName`，不像 `terminalScope`（`:88-91`）挂了 `CoroutineExceptionHandler`，而三个播放协程的 `catch (e: Exception)` 不覆盖 `Error`（如 OOM），逃逸后直达默认未捕获处理器（资源不泄漏，`:419` 的停滞检测 5 秒会拉起终态） | 确定 |
| **L-9** | `OpusFilePlayer.kt:470-471`、`AacFilePlayer.kt:223-224` | 终态读取客户端回调字段（`completionCallback`/`failureCallback`，AAC 为 `cb`/`errorCb`）发生在 `lifecycleMutex.withLock`（`:475`/`:228`）**之前**，而写入发生在 `withLock` 之前（`:126-127`/`:162-163`）。**当前良性**：happens-before 由「赋值 → 协程启动 → 回调 → `terminalScope.launch`」这条链保证，`ExplicitStop` 路径不使用这两个字段。但良性结论依赖调用图，若将来新增一条不经协程启动的终态入口即失效。建议标 `@Volatile` 或把读取移进 `withLock` | 确定 |
| **L-10** | `StreamPlayerStopper.kt:31` | `ownerJob?.cancelAndJoin()` 在 `NonCancellable` 内且**无自身超时**，而 CLAUDE.md「生命周期与释放约定」要求「每一步内部自带超时」。实际风险低：`prepareStop()`（`:47-59`）已先 `ioScope.cancel()` 并 `audioTrackPlayer.stop()` 唤醒阻塞写，唯一长时子协程是 `AacStreamPlayer.kt:150-161` 的 `delay(5s)` 看门狗（可取消），回滚协程 `:188` 用 `launch(NonCancellable)` 故不是 ioScope 子协程、不会被 join。当前有界，但有界性依赖「ioScope 里没有不可取消的阻塞」这条不成文约束。建议只在 KDoc 写明该约束，**不加超时**（加了会在 `NonCancellable` 外套 `withTimeout`，违反同一条约定） | 确定 |
| **L-11** | `BaseMediaCodecAsynchronousTest.kt:483` | `assertReleaseWaitsForCallback`（`:464-492`）在 `:483` 只断言 `releaseFuture` 超时，没有像 `:65` 和 `:433` 那样补 `verify(exactly = 0) { subject.mediaCodec.release() }`。补上才与另两处对称；若按 §4 A3 的方案改造，顺手一起加 | 确定 |
| **L-12** | `AudioSender.kt:86` | `senderClient`、`micRecorder`、`audioPlayer` 都加了 `@Volatile` 并在 `:80-83` 写了详细的 happens-before 论证，但 `senderHandler` 没加。它由 `start()` 在 IO 线程写（`:156`）、由 `sendRecAudioThread()` 的协程读（`:203`）。读到过期 `null` 只丢几帧（`:203` 是 `senderHandler?.`），不崩，但打破了 `:80-83` 注释所声明的规律，易致后续误判。要么加 `@Volatile`，要么在注释里说明它为何不需要 | 确定 |

### 3.1 未发现问题的区域

明确记录通读后认为无需整改的部分，避免后续误判为漏审。

**文件播放器（`OpusFilePlayer` / `AacFilePlayer`）**
- 资源「恰好释放一次」：5 条退出路径（正常 EOF、`stop()`、启动失败、异步失败、协程取消）都收敛到同一个 `finishPlayback`，由 `terminalStarted` 的 CAS（`OpusFilePlayer.kt:464` / `AacFilePlayer.kt:217`）保证单次执行，`terminalCompletion` 作为后到者屏障。`closeInputFile()` 用 `AtomicReference.getAndSet` 实现幂等（`OpusFilePlayer.kt:532-539`）。
- **无孤儿实例**：`:168-169`（文件先 CAS 登记）、`:135`（`decoder` 先赋值后 `start()`）、`AacFilePlayer.kt:168-170`（extractor 先赋值后 `setDataSource`）都是先登记后使用；旧版 `MediaExtractor().apply { setDataSource(...) }` 这一经典陷阱已消除。
- `cancelAndJoin()` 可终止性：每个阻塞点都有唤醒手段（文件读由 `closeInputFile()`、`AudioTrack.write` 由 `audioTrackPlayer.stop()`、`queue.poll(100ms)` 自带上界、其余是可取消 `delay`），且 `:480-486` 的顺序与之一一对应。
- `NonCancellable` 边界：释放体在内（`OpusFilePlayer.kt:473`/`AacFilePlayer.kt:226`），状态守卫与屏障等待在外，每步超时都是内层 `withTimeoutOrNull`，没有在 `NonCancellable` 外再套 `withTimeout`。符合约定。
- AAC 同步 worker 的 EOS/PTS：`onInputData` 现在在 `readSampleData` **之前**快照 `sampleTime`（`:100-101`）并在 EOF 分支显式置 -1（`:105`），修掉了旧版读 `advance()` 之后的 `sampleTime` 导致 PTS 偏一帧的问题。
- 回调引用清理：`OpusFilePlayer.kt:503-504`、`AacFilePlayer.kt:306-307` 在终态置空客户端 lambda，无 Context 泄漏。

**MediaCodec 基类与四个 codec**
- 同步 EOS 排空与 `onEndOfStream` 恰好一次：`BaseMediaCodecSynchronous.kt:51-76` 的三段式逻辑自洽；失败已上报时不叠加超时失败（`:66`），`isReleasing` 时不伪装自然结束（`:66`、`:76`）。
- input/output index 所有权：同步 `queueInputIfNeeded()` 的补偿（`:149-157`）与 `drainOutput()` 的 `finally`（`:191-193`）、异步 `:70-77` 与 `:110-112`，四条退出路径恰好归还一次，补偿本身用 `runCatchingPreservingCancellation` 保留取消语义。`audio` 模块无 `createInputSurface()` 使用，无 Surface 所有权问题。
- 持锁阻塞的上界：持 `codecOperationLock` 期间可达的阻塞调用都有界（`AacDecoder.onInputData` 的 50 ms `poll`、`dequeueOutputBuffer(10_000us)`）；`releaseAndJoin()` 在取锁前先 join worker，不存在持锁 join。
- 一次性会话语义：四个 codec 子类的 `onBeforeCodecStart()`/`onCodecReleased()` 都只做有界本地状态复位，无锁内等待。
- `OpusDecoder.signalEndOfStream()` 的 CAS 协议（`:104-109`）与 `OpusFilePlayer.kt:296-301` 的重试循环配合正确：`offer` 失败会回滚 `eosRequested` 以便重试，非运行态返回 false 后由 `check(isAcceptingInput)` 快速失败。单生产者模型下无竞态。（**注意**：这与 M-4 不矛盾 —— M-4 说的是消费侧 `onInputData` 在 EOS 之后的回落，不是生产侧的提交协议。）

**共同项**
- `CancellationException` 重抛：本切片全部合规。`BaseMediaCodecSynchronous.kt:86-87` 显式首位重抛且 `CodecException` 排在 `IllegalStateException` 之前；`runCatchingPreservingCancellation`（`RunCatchingExt.kt:8-9`）先 catch `CancellationException` 再 catch `Throwable`，顺序正确。
- 中断处理：本切片无 `InterruptedException` 捕获点、无 `Future.get()`/`Thread.join()`，不存在「捕获后未恢复中断标志」。（`FutureTask.awaitDone` 用静态 `Thread.interrupted()` 会清除标志的那个坑只在 `BaseMediaCodec*Test` 里。）
- 安全面：本切片不涉及导出组件、deep link、WebView、网络配置、密钥或凭据；新增日志（`AudioTrackPlayer.kt:144-157`）受 `BuildConfig.DEBUG` 门控，发布 AAR 中为 false，只输出设备类型/音量/AudioManager mode，无凭据或 PII。**无 CRITICAL 安全问题。**
- 格式与规则（静态判断）：本切片文件未见超过 100 列的行、未见 `!!`；`ktlint_standard_import-ordering`（`.editorconfig:17`）与 detekt `ImportOrdering`（`10-configs/detekt.yml:782-783`）在本仓库均为 disabled，故几处「`com.leovp.*` 先于 `android.*` 且夹空行」的 import 布局**不是**违规；两个 `finishPlayback` 的 `throw` 都在最外层 `withContext` 之后（`OpusFilePlayer.kt:524`、`AacFilePlayer.kt:272`），嵌套 `finally` 内只做 `addSuppressed`，不触发 detekt `ThrowingExceptionFromFinally`。

---
## 4. 第一轮悬置发现的裁决

§1.2 列出的 6 条，加上第一轮的 M4。每条的行号都已对当前 HEAD 重新定位。

### M4（确认，行号准确）`OpusFilePlayer` 把合法的文件结尾形态判成会话失败

- 原记录：`2026-10-20-branch-review-vs-master_cc.md` 第 61 行。
- 当前定位：`OpusFilePlayer.kt:231` 的 `require(payload.data.isNotEmpty()) { "Empty OPUS audio frame" }` —— 行号一致。对照 `OpusFramedFileReaderTest.kt:57` 的 `` `trailing start code produces an empty final payload` ``，`:65` `assertContentEquals(byteArrayOf(), last.data)`、`:66` `assertNull(last.nextStartCodePosition)` —— 该形态被测试明确固化为**合法读取结果**，行号也一致。
- 机制链（全部可验证）：
  1. 循环在 `:260` 以 `payload.nextStartCodePosition ?: break` 推进，所以末尾那个 start code 一定会被当成新一帧的起点进入 `readPayload`；
  2. `OpusFramedFileReader.kt:26-30` 对「payloadStart == file.length()」返回 `payloadSize = 0` 并**正常返回空 `data`**；
  3. `:231` 的 `require` 抛 `IllegalArgumentException` → `:269-274 catch (e: Exception)` → `requestFailure(e)` → `finishPlayback(Failure)` → `:514-516` 调 `errorCallback`，本该自然结束的会话被报成失败；
  4. 触发源确有其事：`AudioActivity.kt:291-292` 录制时是 `write(START_CODE)` 紧跟 `write(data)` 两次独立写入 `BufferedOutputStream`，进程在两者之间被杀或掉电即产出该形态文件；
  5. 与 `CHANGELOG.md:576` 自称的「`OpusFilePlayer` 将文件自然结尾识别为最后一个 payload 的合法边界」也不一致。
- 裁决：**确认**。
- **关键修正：该 `require` 不能简单删除。** 已核实 `OpusDecoder.kt:100-101` 的 `decode()` 首个条件就是 `rawData.isNotEmpty()`，空数组**永远返回 false**；而 `submitWithBackpressure`（`OpusFilePlayer.kt:287-294`）是 `while (!decoder.decode(data))`，唯一出口是 `check(decoder.isAcceptingInput)`。所以提交空帧会变成 5 ms 间隔的无限自旋，直到 `:419` 的停滞检测 5 秒后把会话判死 —— 比现状更糟。
- 修复方案：按边界处理，而不是提交空帧。

```kotlin
val payload = input.reader.readPayload(startCodeBeginPos)
if (payload.data.isEmpty()) {
    // A recording truncated right after a start code (power loss or crash) leaves no frame
    // here. OpusFramedFileReader reports this as a valid boundary, so skip it instead of
    // failing the whole session.
    LogContext.log.w(TAG, "Skip empty OPUS frame at $startCodeBeginPos")
    startCodeBeginPos = payload.nextStartCodePosition ?: break
    continue
}
submitWithBackpressure(playbackDecoder, payload.data)
```

- 注意事项：该 while 循环届时含 `break`、`continue`、`break` 共 3 处跳转，`10-configs/detekt.yml:560-562` 的 `LoopWithTooManyJumpStatements maxJumpCount = 3` 为「超过才报」，**恰好不触发**；若想留余量，可把 `submitWithBackpressure` 之后的配速块放进 `else` 分支，只保留末尾单个 `break`。
- 需同步补测试：`OpusFramedFileReaderTest` 保持现状即可（它已是正确预期）；另需一个播放器级用例证明「以 start code 结尾的文件走 `endCallback` 而非 `errorCallback`」（见 §3 M-7 第 4 条）。

### A1（确认机制 + 触发条件形态修正）流播放器 `stopped.set(true)` 先于守卫

- 原记录：第 70 行，行号 `:249`（Aac）/`:243`（Opus）—— **均准确**。
- 当前定位：`AacStreamPlayer.kt:248-251`、`OpusStreamPlayer.kt:242-245`；守卫在 `StreamPlayerStopper.kt:22-28`。
- 事实（已核实）：`stopPlayingAndJoin()` 先 `stopped.set(true)` 再 `streamPlayerStopper.stopAndJoin{}`；而 `require` 位于 `withContext` **之前**，抛出即整个释放体不执行。`stopped` 置位后 `startPlayingStream()` 在 `:95` 与 `:98` 两处直接 `return`，播放器永久拒收帧，而 decoder 与 `audioTrackPlayer` 仍被持有。
- **反向证据**：`StreamPlayerStopperTest.kt:90-107` 这条用例的名字就是 `` `suspending stop rejects calls from its owned scope before side effects` ``，并断言 `verify(exactly = 0) { audioTrackPlayer.stop() }`。stopper 本身刻意做到了「守卫先于副作用」，两个调用方却在它外面又加了一个守卫前的副作用，正好破坏这个被测试固化的契约。对比 `BaseMediaCodec.kt:164-168`，那里 `require` 在 `markReleasing()` 之前，顺序是对的。
- **触发条件修正（原判不完整，两个方向）**：
  1. **原判方向（漏报 → 泄漏）**：`containsJob`（`StreamPlayerStopper.kt:44-45`）只递归遍历 `children`，守卫只在 callerJob 是 ioScope 后代时触发。穷举调用链已核实：`stopPlayingAndJoin` 的唯一调用方是 `AudioPlayer.kt:193-194`；`AudioPlayer.releaseAndJoin()` 的全部调用点是 `AudioSender.kt:114/:277`、`AudioReceiver.kt:292/:339`、`AudioActivity.kt:356`、`ADPCMActivity.kt:143`，**无一处位于流播放器私有 `ioScope` 内**。故当前代码路径下守卫**不可达** —— 是面向未来调用方的陷阱，不是现网缺陷。
  2. **原判遗漏方向（误报 → 死锁）**：守卫有假阴性。`runBlocking` 建的 `BlockingCoroutine` 父上下文是 `EmptyCoroutineContext`，**不继承调用方 Job**。故在 ioScope 子协程里写 `runBlocking { player.stopPlayingAndJoin() }`，callerJob 不是 ownerJob 后代 → 守卫放行 → `StreamPlayerStopper.kt:31` 的 `ownerJob.cancelAndJoin()` 去 join 一个正卡在 `runBlocking` 里等自己子协程的线程 → **死锁**。`MicRecorder.kt:402-405` 的 KDoc 明确承认了同一个洞，`StreamPlayerStopper` 的 KDoc 没有。
- 后果：IAE 抛出后 decoder 与该流播放器自己的 AudioTrack 永久泄漏。理论上再从正确作用域调一次能完成释放，但 demo 侧的 `SuspendTeardownGate`（`AudioSender.kt:63/:237`）与 `AudioActivity.launchCleanup` 的 detach-then-null 都是一次性闸门，不会重试 → 实际不可恢复。`AudioPlayer.kt:170-172` 的 KDoc 已写明「stopAndJoin() can also fail its own require()」，但它的 `finally` 只补救 AudioPlayer 自己那个 AudioTrack。
- 确定性：机制**确定**；「当前不可达」**确定**（已穷举调用点）；runBlocking 死锁路径**确定**（基于 `runBlocking` 的 Job 语义）。
- 修复方案（最小改动：删掉那一行）。安全性已核实：`detachDecoderForStop()`（`AacStreamPlayer.kt:253-255`、`OpusStreamPlayer.kt:247-248`）已在 `lock` 内做 `stopped.set(true)`，由 `prepareStop()` 在守卫**之后**调用；`startPlayingStream` 在 `lock` 内的第二次检查（`:98`）保证并发帧无论先后进锁都安全。提前置位只是省一次锁竞争的微优化。

```kotlin
/**
 * ... (existing KDoc unchanged) ...
 *
 * Does not mark the player stopped before delegating: StreamPlayerStopper validates the
 * caller's scope first and throws without any side effect (StreamPlayerStopperTest covers
 * that). Setting the flag here would leave a rejected player that no longer accepts frames
 * while its decoder and AudioTrack are still open. detachDecoderForStop() sets it under the
 * lock, past the guard, which is where the invariant actually needs it.
 */
suspend fun stopPlayingAndJoin() {
    streamPlayerStopper.stopAndJoin { it.releaseAndJoin() }
}
```

两个文件各删一行，无 import 变化，不触发 detekt。**同时建议**（针对第 2 个方向）：在 `StreamPlayerStopper.kt:22` 的 `require` 上方补一条与 `MicRecorder.kt:402-405` 对齐的注释，说明该守卫只拦直接的后代调用，`runBlocking` 包装过的自调用拦不住且会死锁 —— 这是契约文档缺口，不改代码。

### A2（确认 + 后果升级）`MicRecorder.startRecord()` 无重入守卫

- 原记录：第 72 行，行号 `:231` —— **准确**。
- 事实（已核实）：`:231-252` 的三道检查**全部是状态查询**，没有一道是一次性闸门 —— 第二次调用时 `audioRecord.state` 仍为 `INITIALIZED`、`startRecording()` 对已在录音的实例重复调用不抛、`recordingState` 仍为 `RECORDING`，于是 `:252 recordJob = ioScope.launch { ... }` 覆盖旧 job 引用。`recordJob` 在 `:117` 声明，**不是** `@Volatile`。
- `stopRecordAndJoin()`（`:401-421`）只取到最后一个：`val job = recordJob`（`:406`）→ `job?.cancelAndJoin()`（`:417`）→ `ioScope.cancel()`（`:419`）→ `finishRecorderReleaseAndJoin(ok)`（`:420` → `:470 audioRecord.release()`）。`ioScope.cancel()` 确实会取消 job1，但**取消不等于退出**：job1 可能正卡在 `audioRecord.read(...)`（`:258`，不可取消的 native 阻塞调用）或 `encodeWrapper?.encode(frame)`（`:263`）里。
- 后果：
  1. 双 job 并发 `read()` 同一个 `AudioRecord`：PCM 帧交错，编码流损坏（原记录的后果）。
  2. **`release()` 与未 join 的 job1 的 `read()` 竞争 → native use-after-release 风险**（原记录未提）。窗口窄（`:428` 的 `audioRecord.stop()` 会先唤醒阻塞的 read），但不为零。
  3. `callback.onStop` 由 `onStopNotified`（`:126/:488`）保证只投一次，故 job1 的异常路径 `handleRecordingFailure()`（`:289-293`）不会二次通知 —— 这部分无问题。
- 是否本切片引入：**不是**。`97c84d242:audio/.../MicRecorder.kt` 的 `startRecord()` 形状完全一致，本切片只在 `:273` 加了 `if (stopped.get()) break`。**预存，未加重。** `audio/src/test/` 下无 `MicRecorderTest`，该路径零单测。
- 确定性：缺守卫与覆盖**确定**；native 竞争窗口**推断（置信度 ~85%）**，依据是「cancel 不唤醒 native read」+「`stop()` 只在 `:428` 之后才调用」。
- 修复方案：

```kotlin
/** Guards [startRecord] so one recorder can only open one capture session. */
private val started = AtomicBoolean(false)

@Volatile
private var recordJob: Job? = null      // :117 currently non-volatile
```

```kotlin
/**
 * Starts this one-shot capture session.
 *
 * A recorder cannot be restarted, and a second call is refused rather than silently opening a
 * second reader over the same AudioRecord: the two jobs would interleave frames, and only the
 * last one is reachable from [stopRecordAndJoin], so release() could run while the orphan is
 * still inside the native read(). Create a new MicRecorder per session.
 *
 * Migration: a second startRecord() used to start another reader; it now reports the refusal
 * through [RecordCallback.onStop] and returns.
 */
fun startRecord() {
    LogContext.log.w(TAG, "Do startRecord()")
    if (stopped.get() || !started.compareAndSet(false, true)) {
        LogContext.log.e(TAG, "startRecord() refused: already started or stopped")
        return
    }
    // ... existing body unchanged ...
}
```

- 说明：`started` 用独立 `AtomicBoolean` 而非复用 `stopped`，因为 `stopped` 的语义是「拒收/已停」，两者在 `failRecordStart()`（`:295-311`）里会打架。`recordJob` 加 `@Volatile` 的理由：`startRecord()` 在主线程写、`stopRecordAndJoin()` 在 IO 协程读，成功路径上两者之间没有共享同步点（`stopped.compareAndSet` 在 `:410`，而 `startRecord()` 成功路径不碰 `stopped`）。典型用法靠 `launch` 的任务提交建立 happens-before，实践中安全；但若 stop 协程早已挂在 `releaseCompleted.await()` 上则无此保证，读到过期 `null` 会直接跳过 join。参照 `BaseMediaCodec.kt:55-56` 的 `codecJob` 正是 `@Volatile`。确定性：数据竞争**确定**（JMM）；实际显形**推断（低概率）**。
- 建议补测试：`MicRecorderTest`（Robolectric + mockk 影子化 `AudioRecord`），两次 `startRecord()` 后断言 `ioScope` 只有一个子协程。另见 §3 M-8（同一问题的 demo 侧对偶）。

### A3（形态修正）超时断言的证明力

- 原记录：第 73 行，4 处行号 —— **全部准确**。
- **原记录范围偏大一半**：worker 侧的歧义已被闭锁消掉。四处断言之前都有一条 `await(2s)` 确认被测协程/回调已进入临界区（已核实）：`BaseMediaCodecSynchronousTest.kt:36`、`BaseMediaCodecAsynchronousTest.kt:59`、`:429`、`:480`。
- **残留歧义只在 release 侧**：`releaseExecutor.submit { runBlocking { subject.releaseAndJoin() } }` 之后立刻 `get(100ms)`。TimeoutException 可能来自 (a) 真的被 `codecOperationLock`（`BaseMediaCodec.kt:62/:259`）挡住，也可能来自 (b) 单线程 executor 还没把任务调度起来。
- **关键修正：失效方向是假绿，不是假红。** 在负载高的 CI 上 (b) 完全可能，此时该用例**静默通过**（`verify(exactly = 0)` 同样被平凡满足），不会变红。所以这是覆盖度缺口，不是 flaky 失败源 —— 原记录没有指出方向，容易被误判成「会随机挂」而去调大超时，那只会让假绿更稳固。
- 确定性：**确定**（闭锁与断言顺序都在源码里）。
- 修复方案：闭锁解决不了这个方向（没法让生产代码在「即将争锁」处 countDown 而不污染 API）。可行办法是先用闭锁确认 release 任务已进入 lambda，再轮询该线程栈，确认它确实停在争锁上。在两个测试类里各加一个私有辅助：

```kotlin
/**
 * Submits a deterministic release and returns once the submitted thread is genuinely parked
 * inside the codec operation lock.
 *
 * A bare get(100ms) timeout cannot tell "blocked on the lock" from "not scheduled yet": on a
 * loaded CI the second one passes the assertion without ever exercising the lock, so a
 * regression that let release() walk straight past an active callback would still be green.
 *
 * Couples to withCodecOperationLock() not being inline; if that changes, match releaseCodecOnce.
 */
private fun submitBlockedRelease(
    executor: ExecutorService,
    subject: BaseMediaCodec,
): Future<*> {
    val entered = CountDownLatch(1)
    val thread = AtomicReference<Thread>()
    val future = executor.submit {
        thread.set(Thread.currentThread())
        entered.countDown()
        runBlocking { subject.releaseAndJoin() }
    }
    assertTrue(entered.await(2, TimeUnit.SECONDS), "Release task was never scheduled")
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
    while (System.nanoTime() < deadline) {
        val frames = thread.get()?.stackTrace.orEmpty()
        val onLock = frames.any { it.className.startsWith("java.util.concurrent.locks") } &&
            frames.any { it.methodName == "withCodecOperationLock" }
        if (onLock) return future
        Thread.sleep(1)
    }
    fail("releaseAndJoin() never reached the codec operation lock")
}
```

调用点替换（以 `BaseMediaCodecSynchronousTest.kt:38-46` 为例）：

```kotlin
val releaseFuture = submitBlockedRelease(releaseExecutor, subject)

assertFalse(
    releaseFuture.isDone,
    "releaseAndJoin() must wait for the active codec iteration"
)
verify(exactly = 0) { mediaCodec.release() }
```

效果：去掉 `get(100ms)` 的时间依赖；栈检查通过之后 `isDone == false` 与 `verify(exactly = 0)` 才是有意义的断言。若实现回退成不加锁释放，`submitBlockedRelease` 会在 2 秒后 `fail`，**真正变红**。顺带补 `:483` 缺失的 `verify`（见 §3 L-11）。

### B4（拆成两半：一半推翻）demo 侧在主线程构造 `MicRecorder`

- 原记录：第 74 行，行号 `:164` —— 准确，但结论需拆分。

**B4-a `AudioSender.start()`：推翻。** 已核实 `start()` 的唯一调用点是 `AudioActivity.kt:411` 的 `ioScope.launch { audioSender?.start(this@AudioActivity, uri) }`（`AudioActivity` 的 `Dispatchers.IO` 作用域），**不在主线程**。`AudioSender.kt:164-183` 的 `MicRecorder` 构造与 `startRecord()` 因此跑在 IO 线程上。原记录「在主线程构造 MicRecorder」对 `AudioSender` 不成立。
残留的真问题（降为 LOW，API 契约而非缺陷）：`start()`（`:146`）不是 `suspend`、KDoc 也没写线程约束，API 形状允许主线程调用，当前靠约定守着。建议改成 `suspend fun start(...)`（调用点已在协程里，零成本），或至少补一行 KDoc 写明「必须在非主线程调用」。

**B4-b `AudioActivity.record()`：确认。** `AudioActivity.kt:308-323` 的权限回调由主 Looper 投递，以下三段全在主线程：
1. `FileOutputStream(...)`（`:317`）：主线程磁盘 I/O；
2. `MicRecorder` 构造（`MicRecorder.kt:142-197`）：`AudioRecord.getMinBufferSize()`（`:144`）、`AudioRecord(...)`（`:176`）、`AudioEncoderManager.getWrapper(...)`（`:161`）—— 最后一个对 AAC/OPUS 会走到 `OpusEncoderWrapper.kt:18-31` 的 `.apply { start() }` → `BaseMediaCodec.start()` → `MediaCodec.createEncoderByType()` + `configure()` + `start()`；
3. `startRecord()` → `audioRecord.startRecording()`（`MicRecorder.kt:238`）。

- 是否本切片加重：**不是**。`97c84d242:demo/.../AudioActivity.kt:233-258` 的 `record()` 结构完全相同，MicRecorder 构造侧的重量在本切片也没新增。**预存，未加重** —— 需修正原记录「本分支加重了」的提法。
- 确定性：主线程执行**确定**（权限回调在主 Looper）；`createEncoderByType` 的耗时量级**推断**（高置信度，未实测）。
- 修复方案：把三段搬进 `ioScope.launch{}`，失败经 `withContext(Dispatchers.Main)` 报 toast。注意 `micRecorder` 届时从 IO 线程写、主线程（`stopRecording()`，`:335-338`）读，需把 `AudioActivity.kt:87` 的 `private var micRecorder` 改成 `@Volatile`，并补一次 publish-then-recheck（否则 `onStop()` 可能先跑、读到 null）—— 这与 `AudioSender.kt:78-92/:184-192` 已有的做法同形，可直接照抄那套注释论证。本项应与 §3 M-8 一并处理。

### C5（确认 + 一处修正）`releaseNetty()` 超时后 `connectStatus` 永久停在 `RELEASING`

- 原记录：第 71、102 行，行号 `:904` → 现为 **`:906`**（偏移 2 行），`:867` 准确。
- 事实（已核实）：`releaseNow()` 的 `withTimeoutOrNull(NETTY_RELEASE_TIMEOUT_IN_MILLS) { releaseNetty() }` 在 `null` 时 `return false`（`:906` 之前）而**不动状态**；`connectStatus` 只在 `:925` 的成功分支（`terminationFuture().awaitCompletion()` 之后）写 `UNINITIALIZED`。随后 `release()` 的守卫（`:858-868`）把 `RELEASING` 当「正在释放」返回 false。
- **修正：「worker group 无补偿路径」这句要改。** `releaseNetty()` 的 `finally`（`:912-922`）在超时路径上**一定会执行** `workerGroup.shutdownGracefully(0, 2, SECONDS)`（`:921`，非挂起，在被取消的协程里照样跑完）。group 的关闭请求已发出，netty 会自己走完终止流程 —— **不是资源泄漏，是状态与契约不一致**。准确说法是「没有终态、没有观察点」。这与 `2026-10-16-...` §3 的结论一致（该文档本就写明「不是资源泄漏」）。
- 后果（照该提案 §3 的四条，逐条在当前 HEAD 核实，全部仍成立）：
  1. 后续 `release()` 在守卫处返回 false，调用方（如 `AudioSender.kt:280`）无法区分「正在释放」与「上次释放超时」；
  2. 私有状态 `RELEASING` 被永久留在公开可读的 `connectStatus`（`:263-265`，`private set` 但 getter 公开）；
  3. 与 `BaseNettyServer.stopServer()` 的 KDoc 承诺不对称（`BaseNettyServer.kt:358-366` 的 `finally` 必落 `STOPPED` 并回调 `onStopped()`）；
  4. `releaseNow()` 没有 `finally`：`shutdownGracefully()` 自身抛（极罕见）会穿出两层 finally，状态同样停在 `RELEASING`。
- 确定性：**确定**。
- **修复方案：沿用 `00-documents/2026-10-16-basenetty-client-release-terminal-state_cc.md` 的 §4（主项），无需修订。** 该提案在当前 HEAD 仍完全适用，8 处行号引用已逐条抽查对齐：

| 提案引用 | 当前 HEAD |
|---|---|
| §2.1 `release()` L853–872 | `:853` 起一致 |
| §2.2 `releaseNow()` L874–907 | `:874` 起一致 |
| §2.3 `releaseNetty()` L909–934 | `:909` 起一致 |
| §4.3 `connect()` 守卫 L383–409 从不按 `UNINITIALIZED` 拒绝 | `:383-409` 的 `when` 只列 `CONNECTING`/`CONNECTED`/`RELEASING`/`DISCONNECTING`，`else` 放行 |
| §4.3 `doRetry()` L783–788 按 `released` 短路 | `:783` 首行 `if (released) return` |
| §4.3 `disconnectManually()` 守卫 L629–631 按 `UNINITIALIZED` 拒绝 | `:629-631` 为 `DISCONNECTED \|\| UNINITIALIZED` |
| §5.1 `workerGroup` 由属性初始化器建（L247） | `:247 private val workerGroup = NioEventLoopGroup()` |
| §5.3 `released` 为 `@Volatile`（L238–239） | `:238-239` 一致 |

行号之所以逐条对齐：`BaseNettyClient.kt` 自 `0f84de546` 以来未再改动，而该提交与提案文档提交 `2a259b5b3` 的时间戳完全相同（同一批推送），即提案就是针对当前这个状态写的。

- 落地建议：
  - **实施提案 §4**（`releaseNow()` 把 netty 释放段包进 `try`，`finally` 里 `connectStatus.set(UNINITIALIZED)`；`releaseNetty()` 删掉 `:925-926` 的状态写入与那行日志）。不改返回值语义、不吞异常、不改超时值、不改守卫，与 `BaseNettyServer.kt:358-366` 同形。提案 §4.2 的代码片段可直接采用。
  - **提案 §5（方案 B，守卫改用 `released`，让从未连接的客户端也能释放 group）是独立且更严重的问题**（真实的 `NioEventLoopGroup` 泄漏），同样仍适用，但按提案 §7 应作为**独立提交**，不与 §4 合并。
  - 补充一点（提案未写）：§4 落地后 `release()` 的 `false` 有两种含义 —— 「守卫拒绝」与「netty 超时但状态已终结」。若调用方需区分，建议在 KDoc 写明「`false` 不保证资源未释放」，而不是改返回类型（改 sealed result 会波及 `AudioSender.kt:280`、demo WebSocket 示例等多处，收益不值）。

### C6（确认机制，维持「可接受的设计取舍」）超时取消后 `terminationFuture` 永不被观察

- 原记录：第 71 行，行号 `:928`/`:945` —— **仍准确**（`:928` 是 `releaseNetty()` 的 `catch (e: CancellationException)`，`:945` 是 `closeChannelAndAwait()` 的）。
- 控制流：`withTimeoutOrNull`（`:897`）超时 → 取消打到 `channel.close().awaitCompletion()`（`:944`）→ `closeChannelAndAwait` 的 catch 重抛（`:945-946`）→ `releaseNetty` 的 `finally` 跑 `shutdownGracefully`（`:921`，请求已发出）→ `CancellationException` 继续向上 → `:923-933` 的 `return try { terminationFuture().awaitCompletion() ... }` 整段被跳过 → **永不观察**。`:913-916` 的注释本身就写明了这个取舍。
- **对称性核实**：服务端 `BaseNettyServer.kt:423-435` 的 `shutdownNetty()` 是**完全相同的形状** —— `serverChannel.close().awaitOrLog(...)`（`:426`）被取消后 `finally` 的 `requestEventLoopShutdown()`（`:432`）照样跑，但 `:434` 的 `awaitEventLoopShutdown()` 同样被跳过。所以这**不是客户端独有的不对称**（与 C5 不同），而是两端一致的有意设计。
- 裁决：**可接受**。理由三条：(1) group 关闭请求已发出，`shutdownGracefully` 幂等，等不等它终止都不改变 group 的命运；(2) 超时预算存在的理由恰恰是「关闭卡住时不能把调用方的 teardown 链拖住」（`:894-896` 的注释），再去等 2 秒与该目标直接冲突；(3) 真正丢掉的只是**可观测性**。
- 确定性：机制**确定**；「可接受」**推断（高置信度）**。
- 若要修的最小改法（只补观察，不加等待）：在 `:912-922` 的 `finally` 里给 `shutdownGracefully()` 返回的 future 挂 listener，不挂起、不阻塞。

```kotlin
} finally {
    // ... (existing comment unchanged) ...
    LogContext.log.w(tag, "Releasing socket...")
    val shutdownFuture =
        workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    // The release budget may already be spent on the channel close, in which case the await
    // below is never reached and a loop that never terminates leaves no trace at all. A
    // listener observes it without suspending: the termination future is owned by
    // GlobalEventExecutor, not by the group being shut down, so it is still notified after
    // the group's own threads are gone.
    if (!shutdownFuture.isDone) {
        shutdownFuture.addListener { future ->
            if (future.isSuccess) {
                LogContext.log.w(tag, "=====> Worker group terminated <=====")
            } else {
                LogContext.log.e(tag, "Worker group did not terminate.", future.cause())
            }
        }
    }
}
```

- 两点说明：
  - `isDone` 预检是必须的 —— `NettyFutureExt.kt:22-27` 的 KDoc 已记录「已完成 future 上 `addListener()` 若 executor 是已终止的 event loop 则永不回调」。此处 future 的 executor 是 `GlobalEventExecutor.INSTANCE`（netty `MultithreadEventExecutorGroup` 构造时 `terminationFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE)`），不会终止，故 listener 一定被通知 —— 这是本改法可行的前提。**确定性：推断（置信度 ~90%，依据 netty 实现，未在本环境核对版本源码）。实施前应核对 `gradle/libs.versions.toml` 里 netty 版本的 `MultithreadEventExecutorGroup`。**
  - 不依赖 netty 内部细节的保守替代：在 `releaseNow()` 的 `finally`（C5 §4 会新增的那个）里加一次快照，信息量小但零假设：

```kotlin
if (!workerGroup.isTerminated) {
    LogContext.log.e(
        tag,
        "Worker group not terminated when release() returned. " +
            "isShuttingDown=${workerGroup.isShuttingDown}"
    )
}
```

  - 若实施 listener 版，建议**同时**给 `BaseNettyServer.kt:423-435` 做对称改动（两个 group 各一个 listener），否则会制造新的客户端/服务端不对称。

### 4.1 裁决汇总

| 原记录 | 裁决 | 行号准确性 | 严重度 |
|---|---|---|---|
| M4 `OpusFilePlayer` 空帧判失败 | **确认**，但修法必须是「跳过边界」而非删 `require` | `:231` 准确 | MEDIUM |
| A1 流播放器 `stopped` 先于守卫 | **确认 + 触发条件形态修正**（当前不可达；另发现 runBlocking 死锁假阴性） | `:249`/`:243` 准确 | 契约 HIGH ／ 现网 LOW |
| A2 `startRecord()` 无重入守卫 | **确认 + 后果升级**（native use-after-release）；预存未加重 | `:231` 准确 | HIGH |
| A3 超时断言证明力 | **形态修正**：worker 侧已闭锁，残留只在 release 侧；失效方向是假绿 | 4 处全准确 | MEDIUM |
| B4 demo 主线程构造 MicRecorder | **AudioSender 推翻**；**`AudioActivity.record()` 确认**，预存未加重 | `:164` 准确但结论需拆分 | MEDIUM |
| C5 `connectStatus` 停在 `RELEASING` | **确认**，「group 无补偿路径」修正为「关闭已请求，缺终态与观察点」；沿用 2026-10-16 §4 无需修订 | `:867` 准确，`:904`→`:906` | MEDIUM |
| C6 `terminationFuture` 永不被观察 | **确认机制，维持可接受**；服务端同形；建议只补 listener | `:928`/`:945` 准确 | LOW |

---

## 5. 验证状态与后续命令

**本轮全部结论为源码静态分析。Claude Code 运行在远端服务器，无法执行任何 Gradle 命令，故下列各项一律标注「未验证」。**

| 验证项 | 状态 |
|---|---|
| 编译（`assembleDebug`） | **未验证** |
| detekt（`maxIssues = 0`） | **未验证** |
| ktlint（Android 模式，100 列） | **未验证** |
| 单元测试 | **未验证** |
| 真机验证（AudioRecord / AudioTrack / MediaCodec） | **未验证** |

本轮**未改动任何源码**，所以上述未验证项针对的是 §3/§4 给出的**修复方案**，而非当前树的状态。

### 5.1 若实施修复，需要本机执行

```bash
./gradlew --continue --rerun-tasks \
  :audio:testDebugUnitTest :basenetty:testDebugUnitTest \
  :demo:testDevDebugUnitTest detekt ktlintCheck
```

**注意：这条命令当前无法整体通过** —— `demo:ktlintCheck` 被 §3 M-9 的既有违规阻断，须先修掉
`AudioReceiver.kt:164-166`，否则 ktlint 环节的失败会掩盖本文档修复方案自身的问题。

### 5.2 必须真机验证的项

按 CLAUDE.md「Camera、Camera2、音频、MediaCodec、MediaProjection、OpenGL、NFC、蓝牙和 JNI/Native 相关修改还必须补充真机验证」：

- **H-1**：OPUS 流播放触发丢帧（队列满或延迟 > 500 ms）后，确认 flush + start 之后音频恢复而非永久静音。这是本轮唯一的 HIGH，也是最需要真机确认的一项。
- **M-1**：构造 AudioTrack 非 PLAYING 的场景（播放中被其他应用抢占音频焦点），确认改后走失败回调而非 `endCallback`。
- **M-4**：OPUS 文件自然播完，确认不再出现「正常播完却报错」，且设备侧不抛 `IllegalStateException`。
- **A2 / B4-b / M-8**：连续两次开启录音、三个录音开关交替切换，确认无 PCM 帧交错、无 `AudioRecord` 泄漏、无 native 崩溃。
- **L-7**：打印 `OpusDecoder` 回调所在线程名，确证「回调落在主 Looper」这一推断。

### 5.3 本轮未解决、需作者决策的项

1. **M-2 的方案选择**：`AacFilePlayer` 从继承改为组合（方案 A，结构性重构 + CHANGELOG「变更」条目），还是只补 KDoc 警示（方案 B）。
2. **C5 的实施时机**：提案 §4（终态补齐）与 §5（守卫改用 `released`，修真实 group 泄漏）按提案 §7 应分两个提交，需确认是否本轮一并做。
3. **C6 是否动手**：当前判为可接受的设计取舍；若要补 listener 观察，需先核对 netty 版本的 `MultithreadEventExecutorGroup` 实现，并同步改服务端以保持对称。
4. **M-7 的测试投入**：四个阻塞级修复（H2–H5）至今零回归保护，补齐需要 Robolectric 替身或把 `AudioTrackPlayer` 抽成接口以便注入 —— 这是本切片最大的结构性缺口。

---

## 6. 关联文档

- `00-documents/2026-10-20-branch-review-vs-master_cc.md`：第一轮快审与第二轮第 1/3 部分（录屏与共享基础库）。
- `00-documents/2026-10-15-audio-netty-teardown-review-package_cc.md`：audio/basenetty teardown 的七轮审查记录，本部分 30 个文件的结论来源。
- `00-documents/2026-09-02-audio-media-teardown-followup-fixes_cc.md`：audio 播放器与 MediaCodec 基类的 H1–H5 / M10–M17 / L11–L17。
- `00-documents/2026-10-16-basenetty-client-release-terminal-state_cc.md`：C5 的方案提案（§4/§5）。
- `00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md`：basenetty 非阻塞关闭的原始改动说明。
