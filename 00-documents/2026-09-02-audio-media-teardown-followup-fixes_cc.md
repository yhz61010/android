# Audio / Media Teardown 后续修复清单（Claude Code 审查产出）

- 审查范围：`eb5ab94eb..dce403f17`，共 4 个提交
  - `87fd1c7ac` fix(audio): handle PCM and OPUS file boundaries
  - `df7825309` fix(media): harden codec teardown and API 21 EGL
  - `b9fa704f8` chore(lib-common-android): fix static analysis violations
  - `dce403f17` chore(demo): log audio cipher completion
- 审查方式：三个并行专项代理独立审查 + 主审人逐条回读源码复核。H2 由两个代理**独立复现**。
- 交叉引用：`00-documents/2026-08-31-native-modules-implementation-review_cc.md` §4.12、§4.13
- 本文档为待修问题清单，**不含真机验证结论**

## 0. 结论摘要

| 提交 | 结论 | CRITICAL | HIGH | MEDIUM | LOW |
|------|------|---------|------|--------|-----|
| `87fd1c7ac` | **BLOCK** | 0 | 2（H2、H3） | 5 | 5 |
| `df7825309` | **BLOCK** | 0 | 1（H1） | 3 | 3 |
| `b9fa704f8` | APPROVE | 0 | 0 | 0 | 1 |
| `dce403f17` | APPROVE | 0 | 0 | 0 | 0 |

**审查时要求合入前修复：H1、H2、H3。**截至 2026-09-03，H1 已按“一次性 codec 会话”方案修复；
H2、H3 及第二轮 H4 已在后续整改代码中闭环，详见 §8。真机发布验证仍待完成。

> **2026-09-16 第五至第七轮复审与真机整改补充**：`e9983b248..6d2088879` 已审查完毕。
> Codex 连续三轮各发现 1～3 个问题（含 1 个 P1：OPUS 长文件被提前判失败），全部成立并修复；
> 用户真机实测另外发现 3 个缺陷，其中 1 个是本轮自引入的回归。详见 §12。

> **2026-09-15 第四轮复审补充**：`93c1c3c0f` 已审查完毕。§10.2 的修复声明绝大部分成立；
> 新发现 **3 个 HIGH（H10～H12）**、6 个 MEDIUM（M34～M39，其中 M37 复核后驳回）和 4 个 LOW，
> 全部已在同一分支修复并补充回归测试，详见 §11。

> **2026-09-03 第三轮复审补充**：`d1c87e65e` 已审查完毕。H2、H3、H4、M13 等确认真实闭环。
> Codex 逐条复核后确认 H6、H8、H9 为 HIGH；H7 的通用异常边界问题成立，但原文给出的
> `MediaCodec.stop()` 可达链不成立，降为 MEDIUM。后续修复与剩余验证见 §10。

> **2026-09-03 第二轮复审补充**：`eb8bf5f5f` + `6bb73dfd6` 已审查完毕。Codex 对新增结论
> 逐条复核后，确认 1 个 HIGH（H4）、10 个 MEDIUM（M10～M19）和 6 个 LOW。当前阻塞项为
> **H2、H3、H4**，详见本文 §7。

代理实跑结果：`:audio:testDebugUnitTest --tests "com.leovp.audio.opus.*"` 2/2 通过；
`:audio:detekt`、`:audio:ktlintCheck` 均干净 —— 因此**本轮无静态检查类问题**，下列全部是逻辑/并发缺陷。

---

## 1. 必修项（HIGH）

### H1（HIGH，已修复）`BaseMediaCodec` 生命周期语义不完整

原实现同时暴露 `start()` 与 `stop()`，但没有定义同一实例能否再次启动。`stop()` 把 teardown 标志永久
置位，第二次 `start()` 又会新建 codec，因此会出现新 codec 已启动、回调或同步 worker 却被 teardown
标志静默拦截的问题。与此同时，第一次创建的 codec 只执行 `stop()`，字段随后被新实例覆盖，旧 codec
无法再被显式释放。

Codex 复核同时更正原审查中的一个推导：`stop()` 并不会把 `codecReleased` 置为 `true`，所以第二个 codec
并非因为该标志而无法释放；真正泄漏的是被第二次 `createCodec()` 覆盖的第一个 codec。仅在 `start()` 中
执行 `releasing.set(false)` / `codecReleased.set(false)` 既不能释放旧实例，也不能防止旧同步 worker 或迟到
回调进入新会话，因此不采用原建议的两行复位方案。

#### 最终决策：一个包装器实例只承载一个 codec 会话

本轮明确不保留 `stop() -> start()` 复用兼容性，生命周期收敛为：

```text
NEW -> STARTING -> RUNNING -> RELEASING -> RELEASED
```

选择一次性会话而不是复用的理由：

1. 一轮编解码包含输入/输出队列、PTS、帧计数、CSD、EOS、回调、worker 和错误状态。新建包装器能天然
   隔离这些状态；复用则必须为每项定义重置规则，并处理上一轮迟到回调和 worker 跨代访问。
2. `ioScope` 在释放时会永久取消，复活同一对象与现有资源所有权模型冲突。
3. 继续当前会话应使用明确的输入暂停或 `flush()`；结束后开始新会话应创建新的 encoder/decoder，功能
   能力不减少，但生命周期契约更容易验证。
4. 若未来实测 MediaCodec 创建延迟成为瓶颈，应设计独立的资源池或显式 `resetAndReconfigure()`，不让普通
   `start()` 同时承担首次启动和跨会话复活两种语义。

#### 代码修改

- `BaseMediaCodec.start()` 不再可覆写，并通过原子生命周期状态保证每个实例只能调用一次；运行中重复启动、
  释放后启动、启动失败后重试都会明确抛出 `IllegalStateException`。
- 删除基类公开 `stop()`。AAC/OPUS 子类原先在 `stop()` 中执行的队列清理迁入 `onCodecReleased()`；
  每轮帧计数和 CSD 初始化迁入 `onBeforeCodecStart()`。
- 启动任一步骤失败时进入终态，并释放已经部分创建的 MediaCodec，不留下半初始化实例。
- `release()`/`releaseAndJoin()` 可从未启动、启动中或运行中进入终态；释放和子类清理继续只执行一次。
- AAC/OPUS encoder 的输入队列改为私有，通过 `encode()` 接收数据；encoder/decoder 只有在 `RUNNING`
  状态才接受输入，teardown 后的新数据会返回失败。
- `releaseAndJoin()` 在不可取消清理区等待同步 worker，并在等待 codec 锁后再次读取 worker 引用，覆盖
  `start()` 与释放并发时才刚安装 worker 的窗口。

#### 回归测试

新增或调整测试覆盖：第二次 `start()` 被拒绝；启动失败释放部分初始化 codec 且实例保持终态；未启动就
释放后不能启动；`start()` 与 `release()` 并发时 release 等待初始化临界区、底层 codec 只释放一次；
终态后的迟到 input/format/error 回调全部忽略。H1 不再以“恢复 restart”为验收标准，而以“任何实例只
能启动一轮，所有结束路径都确定进入终态”为验收标准。

---

### H2（HIGH，`87fd1c7ac` 引入的回归）`OpusFilePlayer.stop()` 顺序变更导致主线程永久死锁

> 两个独立代理均定位到此问题。

- 文件：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt`
- 位置：队列声明 `:49`；生产者 `onDecoded` `:93`；消费者 `:169`；`stop()` `:204-211`

**问题**：`onDecoded`（`:93`）在容量 64 的有界 `ArrayBlockingQueue` 上执行**阻塞** `queue.put()`，
而 `onDecoded` 运行于 `onOutputBufferAvailable` 内 —— 该回调在 `df7825309` 之后
**持有 `codecOperationLock`**（`BaseMediaCodecAsynchronous` 用 `withCodecOperationLock` 包裹
`onOutputData`）。本次提交同时改了 `stop()` 内的释放顺序：

| 步骤 | 旧（`eb5ab94eb`） | 新（`87fd1c7ac`，当前） |
|------|------------------|------------------------|
| 1 | `decoder?.release()` | **`ioScope.cancel()`** <- 先杀掉唯一消费者 |
| 2 | `ioScope.cancel()` | `closeInputFile()` |
| 3 | `queue.clear()` | `audioTrackPlayer.release()` |
| 4 | — | `decoder?.release()` |
| 5 | — | `queue.clear()` <- **不可达** |

旧顺序下执行 `decoder.release()` 时消费者仍在运行、仍在排空队列，被阻塞的 `put()` 能被唤醒；
新顺序**先取消了唯一消费者**，因此队列满时：

1. 回调线程卡死在 `queue.put()`，且**持有 `codecOperationLock`**；
2. `stop()` 继续执行到 `decoder?.release()` -> `releaseCodecOnce()` -> `withCodecOperationLock`
   -> **永久阻塞**；
3. `queue.clear()`（`:210`）是唯一会发出 `notFull` 信号唤醒 putter 的调用，却排在 release 之后，
   永远执行不到 -> 死锁无法自解，MediaCodec 与 AudioTrack 均不会被释放。

`isReleasing` 快路径救不了：回调**已经进入锁内**才阻塞。

**队列确实会满**：`:129-145` 的自适应 delay 逻辑本身就是为应对队列积压而存在的。

**且落在主线程**：`BaseMediaCodecAsynchronous.kt:28` 的 `codec.setCallback(mediaCodecCallback)`
未传入 `Handler`，MediaCodec 使用构造时的 `EventHandler`（`Looper.myLooper() ?: getMainLooper()`）；
`playOpus()` 由 `AudioActivity.kt:183` 的 `setOnCheckedChangeListener` 进入，即构造于主线程
-> 回调落在主线程 -> 结果是**硬 ANR + 永久挂死**。

**建议修法**（推荐两项同时做）：

1. 调整 `stop()` 顺序，让队列先排空：

```kotlin
fun stop() {
    if (!stopped.compareAndSet(false, true)) return
    ioScope.cancel()
    closeInputFile()
    queue.clear()          // 移到 release 之前，唤醒可能阻塞的 put()
    audioTrackPlayer.release()
    decoder?.release()
    queue.clear()          // 释放后再清一次，回收残留引用
}
```

   仅此一项仍有窗口：生产者可能在第一次 `clear()` 之后、`release()` 之前再次填满队列。
   因此第 2 项才是根治。

2. 把生产者改成**不可阻塞**：

```kotlin
override fun onDecoded(pcmData: ByteArray) {
    if (pcmData.isEmpty()) return
    if (!queue.offer(pcmData, DECODED_QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        // 队列持续满 = 消费端已停止或严重滞后：丢弃并计数，绝不阻塞回调线程
        droppedFrameCount.incrementAndGet()
        LogContext.log.w(TAG, "Drop decoded PCM, queue full")
    }
}
```

**不要这样修**：不要把队列改成无界（`LinkedBlockingQueue()`）来回避阻塞 —— 那会把死锁换成
解码快于播放时的无界内存增长；也不要在 `onDecoded` 里 catch `InterruptedException` 后吞掉。

**同型问题（同一轮一并处理）**：`audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt:81`
经 `AudioTrackPlayer.kt:125` 解析到阻塞的 `audioTrack.write(short[], ...)`，同样在持锁回调中执行、
同样落在主线程。

**说明**：把消费者从不可取消的 `queue.take()` 改为 `queue.poll(100, MILLISECONDS)`（`:169`）
本身是正确改进，但被上述释放顺序回归抵消，需一并修复才生效。

---

### H3（HIGH）`stop()` 在播放协程可能仍处于阻塞 `write()` 时释放 AudioTrack -> 崩溃

- 文件：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt:171`、`:206-208`
- 相关：`audio/src/main/kotlin/com/leovp/audio/AudioTrackPlayer.kt:112-135`

**问题**：`ioScope.cancel()` 是**协作式**取消。`:166-174` 的消费协程可能正阻塞在
`AudioTrackPlayer.write()` -> `AudioTrack.write()` —— 在 `MODE_STREAM` 下这是**阻塞的、
不可取消的 native 调用**。而 `stop()` 在 `audioTrackPlayer.release()`（`:208`）之前
**没有 join 该 job**。

`AudioTrackPlayer.write` 存在 TOCTOU：先检查 `audioTrack.state` / `audioTrack.playState`
（普通 Java 字段），再调用 native `write`。若 `release()` 落在这个窗口内，native 调用抛
`IllegalStateException` —— 该处**没有** `runCatchingPreservingCancellation` 包裹；
`ioScope` 又无 `CoroutineExceptionHandler` -> 异常直达默认未捕获处理器 -> **应用崩溃**。

**建议修法**（择一或并用）：

1. 把 `OpusFilePlayer.stop()` 改为 `suspend`，在释放前 `cancelAndJoin()` 播放 job；
   若必须保留非 suspend 签名，参照 `BaseMediaCodec.releaseAndJoin()` 提供一个确定性的
   `stopAndJoin()`，并让 Demo 侧调用它。
2. 在 `AudioTrackPlayer.write` 内用 `runCatchingPreservingCancellation` 包裹 native `write`，
   把释放竞态期的 `IllegalStateException` 降级为返回 0（`CancellationException` 仍须重抛）。

**Codex 复核修正**：释放与在途阻塞写并发的风险成立，但“必然由 native write 抛
`IllegalStateException`”缺少 API 契约支持。Android 的阻塞写文档说明，其他线程调用 `stop()`/`pause()`
可以中断写入并返回短写。因此最终修复顺序应是：停止生产、取消播放任务、调用 AudioTrack 的
`stop()`/`pause()` 唤醒阻塞写、等待任务退出、最后 `release()`。单独在 release 前直接
`cancelAndJoin()` 仍可能等待一个尚未被唤醒的 native write，异常捕获只能作为兜底，不能代替所有权排序。

---

## 2. 建议修复（MEDIUM）

### M1 `onError` 现在在持锁状态下回调，公开扩展点新增未文档化的约束

- 文件：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronous.kt:124`
- KDoc：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt:149-154`

`IAudioMediaCodec.onError` 默认空实现，仓库内无 override，**当前无实际缺陷**。但纳入
`withCodecOperationLock` 后，实现方若在 `onError` 中把 teardown 派发到别的线程并等待其完成，
就会死锁 —— 修改前不会。现有 KDoc 只说明「同步处理会在持锁状态下调用子类的 input/output 回调」，
未覆盖新纳入的两个回调。

**建议**：更新 `BaseMediaCodec.kt:149-154` KDoc 明确列出 `onOutputFormatChanged` 与 `onError`；
并在 `IAudioMediaCodec.onError` 上补充「实现必须有界、不得阻塞、不得同步等待其他线程完成 teardown」。

### M2 新增测试未覆盖本次真正要防的竞态

- 文件：`audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronousTest.kt:79-97`

`late callbacks are ignored after stop begins` 是**纯单线程**用例：`stop()` 已完全返回并释放锁
之后才调用回调，因此只验证了快路径 `if (isReleasing) return`。
**即使 `onError` / `onOutputFormatChanged` 完全不加 `withCodecOperationLock`，该用例也照样通过**
—— 它覆盖的是上一轮的 #2（`stop()` 标志），而非 #1（format/error 锁窗口）。

该文件 `:42-76` 已有正确范式（两个 executor + latch，证明 `release()` 会等待在途回调完成），
format/error 路径缺同款用例。

**建议补充三个用例**：

1. **format/error 在途竞态**：仿 `:42-76`，让 `onOutputFormatChanged` / `onError` 在回调中卡住，
   验证 `release()` 会等待其完成；该用例应在临时移除 `withCodecOperationLock` 后**失败**。
2. **一次性会话约束**（对应 H1）：第二次 `start()`、释放后 `start()`、启动失败后重试均应失败；
   `start()` 与 release 并发时部分初始化 codec 必须只释放一次。
3. **`onCodecReleased()` 恰好一次**：分别经 `release()` 与 `releaseAndJoin()` 验证。

**Codex 复核修正**：当前异步实现没有把 format 变化转发给
`IAudioMediaCodec.onOutputFormatChanged()`，因此 format 路径暂时没有可阻塞的子类测试点。应先决定删除
该死 API，或恢复转发后再按上述方式测试；error 路径可以直接使用阻塞 override 验证。

### M3 `initEgl()` 失败路径泄漏 encoder / input Surface / EGLDisplay，且直接崩溃进程

- 文件：`screencapture/src/main/kotlin/com/leovp/screencapture/screenrecord/base/strategies/Screenshot2H26xStrategy.kt`
- 新增抛点：`:265-269`（本次新增的 `eglChooseConfig()` 严格校验）；既有抛点：`:285`、`:296`
- 调用点：`:383-385`

新增校验的抛出位置在 `surface = h26xEncoder?.createInputSurface()`（`:221`）与 `eglInitialize()`
（`:231`）**之后**。而 `onInit()` 由裸 `CoroutineScope(Dispatchers.IO).launch` 调用（`:383-385`），
无 `CoroutineExceptionHandler`、外部无 try/catch -> 异常直达线程未捕获处理器 -> **进程崩溃**，
同时 MediaCodec encoder、其 input `Surface`、以及已 `eglInitialize()` 但从未 `eglTerminate()` 的
`EGLDisplay` 全部泄漏。既有 `:285` / `:296` 抛点是同样问题，且因新增校验而更易触发。

**建议**：把 `initEgl()` 包进 try/catch，失败时 `releaseEgl()` + 释放 encoder/Surface + 置空，
并通过既有的 `builder.screenDataListener` 上报错误，而不是让异常逃逸崩溃进程。
`CancellationException` 必须重抛 —— 使用仓库既有的 `runCatchingPreservingCancellation`，
不要用裸 `catch (e: Exception)`。

**Codex 复核修正**：`ScreenDataListener` 当前只有 `onDataUpdate()`，不存在错误回调，所以上述“通过既有
listener 上报错误”不能直接实施。修复时需新增带默认实现的错误入口或独立 error listener，或者在不扩展
公开 API 时至少完成确定性清理并记录错误。

### M4 CHANGELOG 缺少 Screenshot API 21 能力范围变更

- 文件：`CHANGELOG.md`

本轮只记录了 PCM/OPUS 播放边界修复，遗漏了 `df7825309` 的以下能力范围变更：

1. **`Screenshot2H26xStrategy` 移除 3 处 `@RequiresApi(26)`**，把该录屏策略可用范围从 API 26
   放宽至 minSdk 21。这是对外能力范围的**扩大**，且 API 21~25 真机尚未验证，
   应记录并附「API 21~25 未完成真机验证」限定说明。

原审查把 `OpusEncoder.csd0/csd1/csd2` 从 `var` 改成 `val` 判定为移除公开 setter，这是误判：旧属性均为
`private set`，外部从未获得 setter；新实现保留相同公开 JVM getter，因此不构成所述源码或二进制破坏。
该项无需作为 breaking change 记录。

补充确认：`OpusFramedFileReader` / `OpusFilePayload` 均为 `internal`，不构成新公开 API，无需记入 CHANGELOG。

### M5 排空判定拿 decoder **输入**数与**输出**数相比 -> 确定性 3 秒卡顿 + 同类伪错误

- 文件：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt:178-192`

`submittedFrameCount` 统计**送入 decoder 的 OPUS 帧数**（`:127`）；
`playedFrameCount` 统计**写入 AudioTrack 的 PCM buffer 数**（`:172`）。两者不是同一个量：

- 全程未发送 `BUFFER_FLAG_END_OF_STREAM`，输入 EOF 时 codec 内部仍持有的帧永远不会被吐出；
- `:93` 过滤掉空输出（`if (pcmData.isNotEmpty())`），会永久性地少计输出侧。

一旦两者发散，`playedFrameCount < submittedFrameCount` 恒不可满足 -> `withTimeoutOrNull` 耗满
3 秒 -> `endCallback` 延迟 3 秒，并记录一条伪 Error `Timed out draining OPUS output`
—— 正是本次提交刚刚消除的那类伪错误（`Can't find start code`）的同型症状。

> 注：`00-documents/2026-08-31-native-modules-implementation-review_cc.md` §5.1 记录的
> `submitted=194 played=194` / `submitted=192 played=192` 说明在 P3H 上两者恰好相等；
> 但这**不是格式或 API 保证**，换设备/换 codec 即可能发散。

**建议**：改用 codec 的排空信号（发送 EOS + `onEndOfStream`），或者干脆去掉输入/输出计数比较，
只等待 `queue.isEmpty()`。

### M6 瞬时解码背压现在会直接终止播放

- 文件：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt:122-126`

`OpusDecoder.decode()` 内部是对 64 槽 `ArrayBlockingQueue` 的 `offer()`，输入队列满时返回 `false`。
当前 `check(decoder?.decode(payload.data) == true) { "OPUS decoder input queue is full" }`
把这个**可恢复的瞬时状态**转成 `IllegalStateException`，被 `:158` 捕获、记为 Error，
**整个解码循环终止，播放在文件中途结束**。本次提交之前该帧只是被静默丢弃。

另外当 `decoder` 为 `null` 时 `null == true` 走同一分支，错误信息具有误导性。

**建议**：区分「decoder 为 null」（真错误）与「输入队列满」（重试/丢帧 + Warn），不要用 `check` 终止循环。

### M7 `playOpus()` 在调用线程做阻塞文件 I/O，而 Demo 在主线程调用

- 文件：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt:64-100`、
  `audio/src/main/kotlin/com/leovp/audio/opus/OpusFramedFileReader.kt:43-48`
- 调用点：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt:183`
  （`setOnCheckedChangeListener` 内 -> 主线程）

`findStartCode` 在**无缓冲** `RandomAccessFile` 上，每扫描 1 字节做一次 `seek` + `readFully`
系统调用对。文件正常时 CSD 扫描约 19 字节，开销可忽略；但若文件**没有第二个 start code**
（被截断、损坏、或选错文件），`readPayload(0)` 会在主线程上逐字节扫完整个文件，文件较大时可能造成
明显卡顿乃至 ANR。原审查给出的“约 1~3 秒/MB”没有基准测试证据，不作为量化结论保留。

另外：空的 `audio.opus`（从未录制就点 "Play OPUS"，`createFile()` 会创建 0 字节文件）会让
`require(hasStartCodeAt(0))` 抛 `IllegalArgumentException` 直接从 `playOpus` 逃出到 UI 线程；
`AudioActivity` 无 try/catch -> **崩溃**。（此前 `getCsd()!!` 同样会崩，属既有行为，
但「边界处理」这个提交正是关闭它的合适位置。）

**建议**：`playOpus()` 的同步初始化段整体移到 IO 线程；`OpusFramedFileReader` 改用带缓冲的批量
扫描而非逐字节 `seek`；并对空文件/无 start code 给出明确的失败回调而非抛到 UI 线程。

### M8 `AudioActivity.ioScope` 从不取消，生命周期超出 Activity

- 文件：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt:68`

`CoroutineScope(Dispatchers.IO)` 无 `onDestroy` 覆写；`BaseDemonstrationActivity.onDestroy`
只反注册 EventBus。`:312` / `:317` 的 job 捕获了 `this@AudioActivity`。
上一轮「把 `stop()` 移出主线程」的修复方向正确，但**残留了 scope 泄漏**。

**建议**：改用 `lifecycleScope`，或在 `onDestroy` 中取消该 scope。

### M9 最后一段 PCM 仍被丢弃

- 路径：`OpusFilePlayer.kt:200` -> `AudioActivity.kt:183` -> `stopOpusPlayback()`
  -> `AudioTrackPlayer.release()`

`endCallback` 在**软件队列**清空时即触发，但 `write()` 只是把数据**排入 AudioTrack**。
Demo 的回调随即取消按钮选中 -> `stop()` -> `AudioTrackPlayer.release()` -> `stop()` -> `pause()` + `flush()`，
**丢弃尚未播出的尾部音频**。最后一个 OPUS 帧现在能正确提交了（这是本次提交的正向改进），
但最后约 `minBufferSize` 长度的音频仍会被丢掉。

**建议**：release 前改用 `AudioTrack.stop()`（让已排入的数据播完）而非 `pause()` + `flush()`，
或依据 `playbackHeadPosition` 等待播放真正到达写入总量后再释放。

---

## 3. 可选项（LOW）

| 编号 | 文件:行 | 内容 |
|------|---------|------|
| L1 | `audio/.../mediacodec/iter/IAudioMediaCodec.kt:66` | `onOutputFormatChanged` 是**死 API**：异步回调只赋值 `this.format` 从不转发，同步路径也不调。本次恰好改到该方法却留着永不触发的钩子。 |
| L2 | `audio/.../mediacodec/BaseMediaCodec.kt:115` | `onCodecReleased()` 是 `releaseCodecOnce()` 中**唯一**未用 `runCatchingPreservingCancellation` 包裹的调用；子类钩子抛异常会在 codec 已释放后逃逸出 `release()` / `releaseAndJoin()`。 |
| L3 | `demo/.../screenrecord/RecordSingleAppScreenActivity.kt:112` | 残留 `// @RequiresApi(Build.VERSION_CODES.O)` 陈旧注释，与本次移除 API 26 约束不一致。 |
| L4 | `lib-common-android/.../utils/shell/ShellUtil.kt:143-150` | `var line: String`（非空）从 Java 平台类型 `readLine()` 赋值，EOF 时 `:150` 的 `line.split(...)` 解引用 null -> 更严格空检查代码生成下抛 NPE；`reader` 亦未 `use { }` 关闭。既有问题，本次未触及。修时注意不要引入 `!!`。 |
| L5 | `lib-common-android/.../utils/NetworkUtil.kt:463-473`、`:485`；显示于 `DeviceUtil.kt:222` | `1xRTT`/`CDMA`/`EHRPD`/`EVDO_*` 被注释后落入 `else -> null`，CDMA/EVDO 制式下设备信息可能渲染为 `(null)`。原归属判断错误：`origin/master` 仍保留这些映射，当前分支的 `6fac4bdd5` 才将其注释，因此这是当前分支回归，虽不在本文四提交审查范围内，仍应在合入前恢复并按需加 `@Suppress("DEPRECATION")`。 |
| L6 | `audio/.../opus/OpusFramedFileReader.kt:19-21` | 溢出 `require` 不可达：`:16` 的 `hasStartCodeAt` 已把 `startCodePosition` 限制在 `<= file.length() - startCode.size`。 |
| L7 | `audio/.../opus/OpusFramedFileReader.kt:5` | `data class` 含 `ByteArray` 属性 -> `equals`/`hashCode` 退化为基于身份比较。 |
| L8 | `audio/.../opus/OpusFilePlayer.kt:206` | `stop()` 永久取消 `ioScope`，实例单次可用；二次 `playOpus()` 会打开文件并启动 codec 却静默不启动任何协程。Demo 每次新建实例，故为潜在问题。 |
| L9 | `audio/.../opus/OpusFilePlayer.kt:84-98` | 若 `start()` 在 `.apply {}` 内抛出，`decoder` 仍为 `null`，`:101` 的 catch -> `stop()` -> `decoder?.release()` 空操作 -> **半启动的 MediaCodec 泄漏**。 |
| L10 | `audio/src/test/kotlin/com/leovp/audio/opus/OpusFramedFileReaderTest.kt` | 仅 2 个用例。缺：空文件；**末尾裸 start code**（实际可达 —— `AudioActivity.kt:217-218` 把 start code 与 payload 分两次 `write()`，中途被杀即产生零长末 payload，随后触发 `OpusFilePlayer.kt:123` 的 `require(payload.data.isNotEmpty())` 并记伪 Error）；EOF 处截断的 start code；payload 内部的 start code 别名。 |

---

## 4. 正面确认（无需改动，避免重复劳动）

### 4.1 teardown 不变量全部保持

- terminal teardown 先切换到 `RELEASING` 再等待 codec 锁；`release()` 与 `releaseAndJoin()` 都遵循该
  顺序，基类 `stop()` 已按一次性会话决策删除。
- 四个回调均为「快路径检查 + 锁内二次检查」。
- `onCodecReleased()` 位于 `codecReleased.compareAndSet` 保护块内（`:108-116`），
  对 `release()` 与 `releaseAndJoin()` 两条路径都恰好一次。
- `job?.cancelAndJoin()`（`:95`）先于取锁（`:98`），无锁序死锁。
- **死锁与重入两项假设证否**：`MediaCodec.stop()/flush()/release()` 只做 `native_*()` +
  `mCallbackHandler.removeMessages()`，从不 join Java 回调 Handler，且回调派发路径不持
  `mListenerLock`；回调恒经 `Handler` 派发，不会从 `stop()`/`release()` 内联重入。
  因此给 `onError` 加锁**不引入新的锁序或二次释放风险**。

### 4.2 上一轮 4 项 MEDIUM 的闭环情况

| 上轮编号 | 内容 | 状态 |
|---------|------|------|
| #1 | `onOutputFormatChanged`/`onError` 绕过共享锁 | **已闭环**（但缺回归测试，见 M2） |
| #2 | `stop()` 未置 teardown 标志 | 原补丁引入 H1；现已由一次性会话状态机替代，基类不再暴露 `stop()` |
| #3 | `OpusEncoder` csd 三字段竞态 | **已闭环**（在 `df7825309`）：单个 `@Volatile codecSpecificData: OpusCsd?` 原子换整体，`onOutputData` 在锁内写、`onCodecReleased()` 清空（由 `releaseCodecOnce()` 在 `withCodecOperationLock` 内调用）。无死锁（同线程 `ReentrantLock` 直调）、无 NPE（getter 保持可空，调用方用 `?.`），且保留 `getCsd0/1/2` JVM getter 名。 |
| #4 | `AudioActivity.onStop()` 主线程阻塞 | **已闭环**（在 `df7825309`）：`stopAacPlayback`/`stopOpusPlayback`（`AudioActivity.kt:286-296`）先摘除字段引用再 `ioScope.launch { player.stop() }`；`stop()` 不触碰 Activity 状态，无 use-after-destroy。残留 scope 泄漏见 M8。 |

### 4.3 `87fd1c7ac` 的核心修复是正确的

- **不存在 partial-read 缺陷**：`OpusFramedFileReader.kt:33/45/56` 全部使用 `readFully`。
- **边界数学正确**：`hasStartCodeAt:53` 对短于 start code 的文件不下溢；`:26` 的
  `payloadSize in 0..Int.MAX_VALUE` 是有效防护；负长度结构上不可能（`nextStartCodePosition >= payloadStart`）。
- **主修复本身正确**：`OpusFilePlayer.kt:154` 的
  `startCodeBeginPos = payload.nextStartCodePosition ?: break` 在 `break` **之前**已提交最后一帧，
  自然 EOF 不再丢帧、不再产生伪错误。
- **取消规则遵守**：`OpusFilePlayer.kt:156-157` 在通用 catch 之前重抛 `CancellationException`；
  `:160-163` 的 `finally` 关闭文件。新代码中**无 `!!`**，`:69`/`:72` 使用 `requireNotNull`。
- `stop()` 经 `stopped.compareAndSet` 幂等；`closeInputFile()` 有 `::rf.isInitialized` 保护。

### 4.4 API 21 EGL 兼容改动正确

- `0x3142` 是 `EGL_ANDROID_recordable` 的正确 token 值。
- `EGLExt.EGL_RECORDABLE_ANDROID` 确实自 API 26 才作为公开 Java 字段可用；而
  `EGLExt.eglPresentationTimeANDROID()`（`:210`）早于 API 21，保留该 import 正确。
- `EGL14`、`eglCreateWindowSurface(..., Object win, ...)`、`MediaCodec.setCallback`、
  `createInputSurface` 均为 API <= 21。
- 其余版本分支与真实 API 级别吻合：`:352` `KEY_LEVEL` = API 23、`:346` `KEY_PROFILE` = API 21、
  `:340` `KEY_MAX_FPS_TO_ENCODER` 为内联字符串常量。
- 因此三处 `@RequiresApi(26)` 的移除**在 API 链接层面成立**；`androidx.annotation.RequiresApi`
  已正确移除，`android.os.Build` 与 `EGLExt` 仍在使用，无 detekt 未用 import。
- 该结论**不代表**任意 API 21 设备一定提供满足录制要求的 EGL config，也不代表设备具备 H.265 硬件编码器。

### 4.5 `b9fa704f8` 确认为真正的 no-op

用 `git show -w --ignore-blank-lines` 隔离后，全部非空白改动为：注释重排 5 处、无用 import 1 个、
块体转表达式体 2 处、冗余字符串模板花括号 1 处、lambda 花括号合并 1 处。三项高危假设全部证否：

- **无任何 `catch` 子句被改动**（`-w` diff 中 0 行 `catch`/`try`/`runCatching`），
  不存在 `CancellationException` 被吞的规则违反。
- **`ShellUtil` 关键不变量完好**：`drainStreams(...)`（`:98-99`）仍在 `process.waitFor()` **之前**
  调用，未回归 stderr 未排空死锁；`:126-135` 仍起两个线程并 `join()`；`readStreamAsync` 仍用
  `bufferedReader(...).use { }` 且仍以 `.apply { start() }` 结尾（丢掉它才是隐形杀手，未发生）；
  `forceStop`（`:138`）仍经正则校验的 `requireValidPackage()`（`:42`），无新增注入面。
- **测试未被削弱**：`FileDocumentUtilTest.kt:40` 仅参数换行加尾逗号，`:63` 把字面量提取为
  `val documentRawPath`（值相同）；全部断言与路径穿越校验
  `canonicalPath.startsWith(base.canonicalPath + File.separator)` 一字未改。
- 移除的 `import android.database.Cursor` 之后 `Cursor` 仅出现在 KDoc/注释中，安全。
- `lib-common-android` 仍无 `com.leovp.log` 依赖，模块日志规则未破。

### 4.6 `dce403f17` 无敏感信息泄漏

`AudioCipherActivity.kt:74,90` 新增的两行日志只输出 `measureTimeMillis` 得到的 `Long` 耗时，
未插值 `secretKey`、IV 或明文/密文 `ByteArray`。

---

## 5. 修复后的验证要求

1. **静态检查与单测**：`audio`、`screencapture`、`demo`、`lib-common-android` 的
   `ktlintCheck`、`detekt`、`testDebugUnitTest` 与 `:demo:assembleDevDebug`，
   本轮改动路径必须加 `--rerun-tasks` 强制重跑，不接受 `UP-TO-DATE`。
2. **新增回归测试必须先红后绿**：M2 的「format/error 在途竞态」用例应在临时移除
   `withCodecOperationLock` 后失败、恢复后通过；H1 用例应证明同一实例第二次启动、释放后启动和启动
   失败后重试均被拒绝，并覆盖 `start()` 与 release 并发清理。
3. **真机验证（发布门槛，尚未满足）**：
   - **H2 重现验证**：OPUS 文件播放过程中，在解码快于播放、队列接近满时点击停止，
     确认无 ANR、无挂死，进程存活且播放按钮复位。
   - **H3 重现验证**：播放中途反复快速点击停止，确认不出现
     `IllegalStateException` 崩溃。
   - **M7 验证**：对空的 / 被截断的 `audio.opus` 点击播放，确认给出错误提示而非崩溃或 ANR。
   - PCM / AAC / OPUS 三种格式的播放中主动停止、自然结束、快速重复进入退出，
     确认无主线程卡顿与 teardown 错误，并完成人工听感确认（含 M9 的尾音是否完整）。
   - Screenshot 录屏：至少一台 API 21~25 真机，用 H.264 路径验证 EGL config 选择、首帧画面、
     停止释放与重复进入退出；同时验证 M3 修复后 `initEgl()` 失败不再崩溃进程。
4. 上述真机项完成前，本轮改动**不得标记为「发布验证完成」**。

---

## 6. 修复优先级建议

| 顺序 | 项 | 理由 |
|------|-----|------|
| 1 | H2 | 主线程永久挂死 + ANR，两个独立代理复现，最严重 |
| 2 | H3 | 停止竞态导致应用崩溃，用户可感知 |
| 已完成 | H1 | 已改为一次性 codec 会话，删除基类 `stop()` 并补生命周期状态与回归测试 |
| 4 | M3、M7 | 均会崩溃进程（EGL 初始化失败 / 空文件播放） |
| 5 | M5、M6 | 伪错误与中途停播，直接影响本次提交想解决的问题 |
| 6 | M2 | 缺回归测试意味着 H1 与 #1 可能再次回归 |
| 7 | M8、M9 | 泄漏与尾音丢失 |
| 8 | M1、M4 | 文档与 CHANGELOG，影响外部使用者但不影响运行 |
| 9 | L1~L10 | 可择机处理；L4、L5 属既有问题 |

---

# 7. 第二轮复审（2026-09-03）

- 审查范围：`dce403f17..6bb73dfd6`，共 2 个提交
  - `eb8bf5f5f` refactor(audio): make codec sessions one-shot
  - `6bb73dfd6` fix(audio): synchronize AAC file player teardown
- 审查方式：两个并行专项代理独立审查 + 主审人逐条回读源码复核（含实跑
  `:audio:compileDebugKotlin`、`:audio:testDebugUnitTest --rerun-tasks`）

## 7.0 结论摘要

| 提交 | 结论 | CRITICAL | HIGH | MEDIUM | LOW |
|------|------|---------|------|--------|-----|
| `eb8bf5f5f` | **APPROVE** | 0 | 0 | 5（M14～M17、M19） | 3 |
| `6bb73dfd6` | **BLOCK** | 0 | 1（H4） | 5（M10～M13、M18） | 3 |

**本次复审当时的阻塞项：H2、H3（OPUS）+ H4（AAC）。**这些问题已在后续整改代码中处理，
见 §8。M18（原 H5）是需要明确的资源所有权契约，但不再作为 HIGH 阻塞项。

---

## 7.1 H1 已关闭：一次性会话重构验证通过

`eb8bf5f5f` 对 H1 的处理**正确且完整**，且关键在于**响亮失败而非静默失效**：

| 路径 | teardown 后行为 |
|------|----------------|
| `start()`（`BaseMediaCodec.kt:89`） | `check(CAS(NEW, STARTING))` -> 立即抛 `IllegalStateException`，不静默 |
| `release()`（`:192`） | `markReleasing()` 在 `RELEASED` 返回 false -> no-op，幂等 |
| `releaseAndJoin()`（`:140`） | 同一守卫；codec 经 CAS 恰好释放一次 |
| `flush()`（`:206`） | `check(state == RUNNING)` -> 抛出，不静默 |
| `process()`（Sync `:56`） | `isReleasing` -> `false`，worker 退出，`onEndOfStream()` 正确抑制 |
| 四个异步回调 | 快路径 + 锁内 `isReleasing` 重检，未变 |

- **原子性无 TOCTOU**：`AtomicReference<LifecycleState>`（`:58`）；`start()` 闸是**单次
  `compareAndSet(NEW, STARTING)`**，两线程不可能同时通过；`markReleasing()`（`:216`）是 CAS 重试循环。
- **`release()` 与 `start()` 竞争已验证**：`start()` 全程持 `codecOperationLock`，并在 `createCodec()`
  与 `codec.start()` 之后各重检一次状态，catch 块拆除半成品 codec；两者最终都收敛到 `RELEASED`。
  `releaseAndJoin()` 的双 `cancelAndJoin()`（`:148`/`:153`）成立，因为 `codecJob` 的赋值在锁内，
  重读发生在 `releaseCodecOnce()` 取锁之后。
- **此前确认的 5 条 teardown 不变量全部存活**。`start()` 失败路径 `:115` 直接把状态设为
  `RELEASING`，再执行一次性资源清理；这里没有调用 `markReleasing()`，但终态语义成立。
- **仓库内无调用方被破坏**：逐个核查 `AacStreamPlayer.kt:61-79`、`OpusStreamPlayer.kt:66-86`、
  `OpusFilePlayer.kt:84-98`、`AacEncoderWrapper.kt:24-38`、`OpusEncoderWrapper.kt:24-38`、
  `MicRecorder.kt:97`、`AudioActivity.kt:159/176`，全部已是「每会话新建实例」，
  不存在 `onStop` 里 stop / `onStart` 里 start 的配对。
- **项目规则干净**：未新增 `!!`；`start()` 的 `catch (e: Exception)` 虽会捕获
  `CancellationException`（JVM 上是 `IllegalStateException` 子类），但在 `:122` 重抛，契约成立。

`6bb73dfd6` 对 AAC **正常停止路径**的修复同样正确：`stop()`（`AacFilePlayer.kt:120-130`）先
`audioTrackPlayer.stop()` 唤醒阻塞的 native write，再 `releaseAndJoin()`（不持锁 join），
最后在 `finally` 中 `releaseExternalResources()` 释放 extractor 与 AudioTrack。
AAC 此前的 extractor 提前释放竞态和 AudioTrack 释放竞态在该路径上均已消除；这不代表 OPUS 的
H2/H3 已修复。`stop()` 幂等，`CancellationException` 全程重抛。

---

## 7.2 必修项（HIGH）

### H4（HIGH，`6bb73dfd6`）第二次 `playAac()` 会覆盖输入资源并同步终止当前会话

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt:91-115`、`:132-137`
- 调用点：`demo/.../examples/audio/AudioActivity.kt:165`（主线程）

`playAac()` 只有 `check(!stopped.get())`（`:92`）这一道闸，**没有「是否已启动」闸**。同一个实例在
运行中被再次调用时，第二次调用不会在修改状态前响亮失败。
运行中再次调用时：

1. `:95` 直接覆盖 `mediaExtractor` —— 第一个 extractor 泄漏，且**存活中的 worker 的
   `onInputData`（`:60`）随即读到新字段**；
2. `:110` 的 `start()` 因一次性状态机抛出。该 `check` 在 `start()` 的 try/catch **之外**
   （`BaseMediaCodec.kt:90`），因此不触发自清理，`lifecycleState` 保持 `RUNNING`；
3. `.onFailure` -> `releaseAfterStartFailure()`（`:132`）-> `super.release()`，把当前运行会话切换到
   `RELEASING`，并使用已弃用的非 join 释放入口；
4. 这发生在**调用方线程**（`playAac` 非 suspend，Demo 从主线程调用）。该路径没有像 `stop()` 那样先
   `audioTrackPlayer.stop()`；如果 worker 正持锁执行阻塞式 `AudioTrack.write`，主线程会等待
   `codecOperationLock`，形成不可控的 UI 卡顿，极端设备状态下存在 ANR 风险；
5. `super.release()` 取得并释放 `codecOperationLock` 后，生命周期已经是 `RELEASING`，worker 即使尚未
   完成协程收尾，也不能再次进入 codec/extractor 访问区。因此随后释放外部资源**不会**重新产生
   `MediaExtractor.readSampleData()` 竞态；原复审所述“形态 B 原样回归”推导过强。

该问题成立的核心是：旧 extractor 确定泄漏、正在运行的会话被意外终止，以及调用线程可能同步等待 codec
锁；不是外部资源释放后 worker 必然继续访问。

**建议修法**：

1. 在赋值 `cb`、创建 extractor 或改变 AudioTrack 前，用 `started: AtomicBoolean` 执行一次性 CAS；重复调用
   应立即抛出，并保持现有会话完全不变；
2. 一次性闸建立后，`releaseAfterStartFailure()` 只处理首次初始化失败，不再承担“终止正在运行会话”的
   职责；
3. 同时按 M11 统一启动失败契约，避免在主线程隐式执行一套调用方不可见的同步清理。

---

## 7.3 建议修复（MEDIUM）

### M10 EOF 回调与挂起停止的组合容易诱发自等待死锁

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt:87-89`、`:120`

CHANGELOG 已声明的破坏性变更要求 `stop()` 从挂起上下文调用，但该类唯一的完成信号是
**运行在 codec worker 上的非挂起回调**。调用方若直接写成
`runBlocking { player.stop() }` 会**绕过自 join 防护**：
`require(codecJob !== currentCoroutineContext()[Job])`（`BaseMediaCodec.kt:141`）
因 `runBlocking` 装入新 `Job` 而通过，随后 `codecJob?.cancelAndJoin()`
**从 worker 线程 join worker 自身** -> 在 `NonCancellable` 下**永久死锁**。

原结论中“从 EOF 无合法停止途径”表述过强：调用方可以向独立的 owner scope 派发 `stop()`，Demo 当前
正是把按钮状态切回主线程，再由 Activity 的 IO scope 停止播放器。问题在于 API 没有说明线程/Job 约束，
很容易写出上述死锁用法。

单纯把 `endCallback` 改为 suspend 也不能解决问题：回调仍运行在 codec worker 中，从该回调直接调用
`stop()` 依然是在等待自身。应与 M13、M18 一并设计以下一种明确契约：

1. 播放器在 worker 结束后通过不需要 join 自身的内部终态路径自动清理，再通知调用方完成；或
2. 完成事件只通知独立 owner，公开文档明确禁止在回调线程阻塞等待 `stop()`，并提供可等待的共享终态结果。

### M11 `playAac()` 吞掉全部启动失败，调用方永不知情

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt:111-114`

新增的 `check(...)`（"AAC file does not contain a supported audio track"）以及
`setDataSource` 的 `IOException` 只被记为一行日志；`playAac` 正常返回，`cb` 在 `:145` 被置 null
且**永不触发** —— 调用方会一直以为在播放。而同一函数的 `:92` 又会把
`IllegalStateException` 抛出去：**一个 API 两套互相矛盾的错误契约**。
违反项目规则「不得静默吞错 / UI 侧需给出可理解的错误信息」。

**建议**：通过回调或返回值 / `Result` 上报失败，并统一该函数的错误契约。

### M12 `AacFilePlayer` 与 `OpusFilePlayer` 现在契约互斥

- 文件：`audio/.../aac/AacFilePlayer.kt:120`（`suspend fun stop()`）
  vs `audio/.../opus/OpusFilePlayer.kt:204`（`fun stop()`）
- 可见于：`demo/.../examples/audio/AudioActivity.kt:289`（AAC）与 `:295`（OPUS）

同族两个播放器的停止语义已经分叉：`AudioActivity` 里的 `ioScope.launch` 包装
**对 AAC 有意义（等待 worker 退出），对 OPUS 完全无效**。而 H2/H3 在 OPUS 侧确认仍然存在。

**建议**：让 `OpusFilePlayer` 采用与 AAC 相同的所有权原则（先停止生产并唤醒阻塞操作，再等待任务和
decoder 退出，最后释放文件与 AudioTrack），一次性收敛两个播放器的终态契约。OPUS 另有文件读取、解码、
PCM 消费和 drain 等多个任务，不能机械复制 AAC 的代码顺序；这仍是 H2、H3 的推荐落地方向。

### M13 同步路径尾音丢失，且 `onEndOfStream()` 可能触发两次（既有，基类）

- 文件：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronous.kt:76-91`、`:159`

`pts < 0` 时排入 `BUFFER_FLAG_END_OF_STREAM`、置 `isFinish = true`、**只再 drain 一次**，
随后 `return !isFinish` 结束 worker 循环。codec 内仍滞留的若干帧（codec 延迟通常数帧）
永远不会被排空，**文件最后几帧被静默丢弃**。因此 `:121` 的 EOS 分支实际不可达；
若在同一轮真的到达，`onEndOfStream()` 会在那里与 `:52` 各触发一次 -> **end callback 被调用两次**。

`OpusFilePlayer` 有显式的 `OUTPUT_DRAIN_TIMEOUT_MS` 排空（`opus/OpusFilePlayer.kt:176-201`），
同步路径没有等价物。非本轮引入，但它削弱了文档中「自然结束」已验证的结论。

### M14 CHANGELOG 对公开 API 破坏的二进制影响说明仍不完整（`eb8bf5f5f`）

- 文件：`CHANGELOG.md`

现有破坏性变更条目已覆盖 `stop()` 删除、`start()` 终态化、encoder `queue` 私有化、
「新会话需新建实例」，并且已经明确写出 `AacDecoder.decode()` 改为返回入队结果。原复审称
`decode()` 返回类型变化“完全遗漏”是误报，但说明仍缺两个层次：

1. **`AacDecoder.decode()` 返回类型 `Unit` -> `Boolean` 的二进制影响未说明**
   （`audio/.../aac/AacDecoder.kt`：`dce403f17` 的 `:105` 为 `fun decode(aacData: ByteArray)`，
   现为 `:104 fun decode(aacData: ByteArray): Boolean`）。
   JVM 描述符 `([B)V` -> `([B)Z`：外部**预编译**调用方得到的是运行期 `NoSuchMethodError`，
   而不是编译错误。CHANGELOG 已记录行为变化，但应把它明确列入破坏性变更并说明需要重新编译调用方。
   （`OpusDecoder.kt:80` 不受影响，它原本就推断为 `Boolean`。）
2. **`flush()` 非 `RUNNING` 即抛 `IllegalStateException` 的行为变化确实遗漏**
   （`BaseMediaCodec.kt:206-214`）。此前对已启动后停止的 codec 是成功或仅记日志。
   仓库内调用方有包装（`AacStreamPlayer.kt:160`、`OpusStreamPlayer.kt:171`），外部调用方没有。

完整破坏清单（供 CHANGELOG 参考）：`stop()` 删除；`start()` `open` -> `final`（`:89`）；
`AacEncoder.queue`（`AacEncoder.kt:75`）与 `OpusEncoder.queue`（`OpusEncoder.kt:174`）
`public` -> `private`；以上两项。新增 protected 面：`onBeforeCodecStart()`、`onCodecStarted()`、`isRunning`。

### M15 子类钩子现在在 `codecOperationLock` 内执行，但 KDoc 未说明

- 文件：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt:95-113`、KDoc `:126-130`

`onBeforeCodecStart()`、`createMediaFormat()`、`createCodec()`、`codec.start()` 与
`onCodecStarted()` 全部在 `withCodecOperationLock` 内执行。而钩子 KDoc 只写了
「重置每会话子类状态」/「启动子类工作」。

**失效场景**：外部子类若在 `onCodecStarted()` 中做一次有界但缓慢的等待，
会在该时长内**阻塞所有异步回调与全部 teardown** —— 这是此前不存在的挂起类别，且无任何文档警告。
（`BaseMediaCodecSynchronous.onCodecStarted()` `:44` 本身是正确的，只做 `ioScope.launch`。）

**建议**：在钩子 KDoc 上明确「在 codec 操作锁内调用，实现必须有界、不得阻塞、不得同步等待其他线程」。

### M16 `codecJob` 是非 `@Volatile` 的 `protected var`，跨线程读写

- 文件：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt:55`
  （已核实为 `protected var codecJob: Job? = null`，无 `@Volatile`）
- 写：`BaseMediaCodecSynchronous.kt:46`；读/写：`BaseMediaCodec.kt:148`、`:153`、`:154`、`:196`、`:197`

当前**仅**依靠一条不显眼的 happens-before 成立：`start()` 在 `codecOperationLock` 内写它，
而 `releaseAndJoin()` 的重读跟在 `releaseCodecOnce()` 取锁/释放锁之后。
但 `release()`（`:196`）**完全无锁**读它 —— 今天成立只是因为 `ioScope.cancel()` 使此后安装的
job 天生已取消。

因此当前代码没有可直接复现的 worker 泄漏：确定性路径会在取得 codec 锁后重读，兼容路径即使没看到
新 job，也会先永久取消其 scope。该项属于并发可维护性加固，不应描述成已经发生的数据竞争故障。

**建议**：标记 `@Volatile`（或改 `AtomicReference`），并把上述排序理由写入注释；
否则未来一次把重读移出锁的重构，会**静默丢掉 join**。

### M17 一次性会话的新测试有效，但仍缺三类用例

- 文件：`audio/src/test/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronousTest.kt`

以下 4 个用例在移除一次性守卫后**都会失败**（即它们是有效的）：
`:103` 二次 `start()` 被拒；`:120` 启动失败释放半初始化 codec 且进入终态；
`:139` 未启动即释放后不能启动；`:154` `start()`/`release()` 并发。

**仍缺**：

1. **双线程并发 `start()`** —— CAS 正是为它而设，却无任何用例覆盖；
2. 成功启动后调用两次 `release()`，或 `release()` 后再 `releaseAndJoin()`，验证底层 codec
   **恰好释放一次**；启动失败后再调用 `release()` 的现有测试已经覆盖了幂等释放的一种路径；
3. teardown 后 `onOutputBufferAvailable` 的惰性（`:81` 只覆盖了 input / formatChanged / onError）。

`6bb73dfd6` **未新增任何测试**；而 `BaseMediaCodecAsynchronousTest.kt:200` 已证明这类生命周期
时序在 JVM 上可 mock，因此同步路径的释放顺序测试是可行的。

### M18（原 H5，严重度修正为 MEDIUM）AAC 自然 EOF 的资源所有权契约不明确

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt:87-89`

```kotlin
override fun onEndOfStream() {
    cb?.invoke()
}
```

自然结束时，播放器自身不会释放 `MediaExtractor`、`AudioTrack` 或 `MediaCodec`，需要调用方在完成回调
之后再执行 `stop()`。Demo 会把按钮切回未选中状态，并由监听器在 Activity 的 IO scope 中调用 `stop()`，
所以仓库内已验证路径能够完整释放，并非必然泄漏。

问题在于公开 API 没有说明“完成回调只表示读取结束，调用方仍拥有停止责任”。外部调用方若把回调理解为
播放器已经结束并释放，每次自然播放都会保留一组 Native 资源。鉴于正确管理生命周期的调用方可以安全
释放，且当前 Demo 已遵守该流程，本项从 HIGH 调整为 MEDIUM，不再作为本轮阻塞项。

推荐的一次性播放器语义仍是让 EOF 与主动停止收敛到同一终态，但必须与 M10、M13 一起设计：先正确提交
并排空 EOS，随后通过不会 join 当前 worker 自身的内部终态路径释放资源，最后再通知调用方。若暂不实现
自动释放，则必须在公开 KDoc 中明确 owner 的 `stop()` 责任和禁止在完成回调线程中阻塞等待自身。

### M19（原 L16，严重度提升为 MEDIUM）重复 AAC CSD 会覆盖并泄漏现有 decoder

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt:87-88`

`startPlayingStream()` 把任何小于 10 字节的 payload 都交给 `initDecoderLocked()`，没有像
`OpusStreamPlayer` 那样先检查 `csd0 == null`。第二个短 CSD 到达时，`initAudioDecoder()` 会直接把
`audioDecoder` 改成新实例；旧 decoder 在没有 release 的情况下失去最后一个引用，其 MediaCodec 无法再
被确定性释放，只能依赖运行时后续清理，且一次性会话语义使它不可能再被恢复使用。

这是确定的 Native 资源所有权丢失，不应只列为 LOW。建议对重复且相同的 CSD 幂等忽略；若协议允许 CSD
变化，则在锁内摘除旧 decoder、提交新状态，再在锁外通过确定性挂起入口释放旧实例，禁止直接覆盖。

---

## 7.4 可选项（LOW，第二轮新增，共 6 项）

| 编号 | 文件:行 | 内容 |
|------|---------|------|
| L11 | `audio/.../aac/AacFilePlayer.kt:54`、`:97` | `createDecoderByType(mime!!)` 与 `mediaExtractor!!.trackCount` 仍使用 `!!`，违反项目硬性规则（本次删掉了旧 `:99` 的一处却留下这两处）。当前调用图上不会 NPE，但 `createCodec()` 是公开 override。改用 `requireNotNull(mime)` / 已非空的局部变量。**注意 detekt 通过并不代表合规 —— 当前配置不拦 `!!`。** |
| L12 | `audio/.../aac/AacFilePlayer.kt:120-130`、`:142-144` | `suspend fun stop()` 体内全是阻塞调用（`audioTrackPlayer.stop()`、`codecOperationLock.lock()`、`MediaExtractor.release()`、`AudioTrack.release()`）却无 `withContext(Dispatchers.IO)`，依赖调用方给对 dispatcher。Demo 恰好从 IO 调用（`AudioActivity.kt:289`），但库函数不应依赖这一点。 |
| L13 | `audio/.../aac/AacFilePlayer.kt:121` | `stop()` 幂等但**不是屏障**：并发第二次调用经 CAS 立即返回，而第一次仍在 `releaseAndJoin()` 内 —— 不兑现 KDoc 承诺的「已等待其 codec worker」。建议以共享 `Deferred`/`Job` latch，让所有调用方等待同一次 teardown。 |
| L14 | `audio/.../opus/OpusFilePlayer.kt:124` | `check(decoder?.decode(...) == true) { "OPUS decoder input queue is full" }` 的诊断信息现在会误导：`decode()` 已改为 `isRunning && queue.offer(...)`，并发 `stop()` 时会报「队列已满」。被 `:159` 的 `if (!stopped.get())` 抑制，无用户可见影响，但信息错误。（与 M6 同一处，修 M6 时一并处理。） |
| L15 | `audio/.../mediacodec/BaseMediaCodec.kt:176`、KDoc `:181` | `onCodecReleased()` 现在位于 `::codec.isInitialized` 守卫之外，codec 从未创建时也会触发（由 `:147` 的测试确认）。仓库内 4 个子类都安全，但 KDoc「在 codec 被释放之后」已不符实，需更新，并写明「实现必须能应对从未启动的 codec」。 |
| L17 | `BaseMediaCodecAsynchronousTest.kt:80`；`AacEncoderWrapper.kt:24` | 前者缺少四个同族用例都有的 `@Suppress("DEPRECATION")`，构建在 `:91` 产生警告；后者用 `var encoder` 而 `val` 即可（`OpusEncoderWrapper.kt:24` 已正确用 `val`）。 |

---

## 7.5 第二轮修复优先级

| 顺序 | 项 | 理由 |
|------|-----|------|
| 1 | **H2 + H3 + M12 合并处理** | 按 AAC 已验证的所有权原则重新设计 `OpusFilePlayer`，同时消除阻塞队列死锁、停止竞态和同族播放器契约分叉 |
| 2 | **H4** | 第二次 `playAac()` 会确定泄漏旧 extractor、终止现有会话，并可能在调用线程等待 codec 锁 |
| 3 | **M13 + M18 + M10 合并处理** | 正确 EOS 排空、自然结束资源所有权和完成回调线程语义相互依赖，拆开修容易固化尾音丢失或自等待死锁 |
| 4 | M19 | 重复 AAC CSD 会确定覆盖并泄漏 MediaCodec，严重度已由 LOW 提升为 MEDIUM |
| 5 | M11 | 启动失败只记日志，调用方无法统一处理失败或恢复 UI |
| 6 | M14 | `decode()` 已记录但缺二进制影响；`flush()` 行为变化确实遗漏 |
| 7 | M15、M16 | 契约文档与可见性加固；当前路径正确，但未来重构容易破坏隐含排序 |
| 8 | M17 | 补并发 start、成功启动后的幂等 release 和迟到 output callback 测试 |
| 9 | L11～L15、L17 | 可择机处理；L11 属项目硬性规则违反，建议优先 |

---

## 7.6 主审人自我更正记录

Codex 对本文档第一轮内容提出 5 处更正，经逐条回读源码核验，**全部成立**，现记录如下以免后续沿用错误结论：

1. **`OpusEncoder.csd0/1/2` 原本即为 `private set`**（`eb5ab94eb` 的 `:178-183`）。
   `var` -> `val` **不构成**公开 setter 移除，原 M4 第 1 项是误报。
   成因：初次核查用 `grep "csd"` 匹配，把紧随其后的 `private set` 行过滤掉了。
2. **`stop()` 不会把 `codecReleased` 置为 `true`**，原 H1 第 3 点的推导有误。
   泄漏结论仍成立，但机理是**第一个 codec 被第二次 `createCodec()` 覆盖**后无法再释放。
3. **`ScreenDataListener` 只有 `onDataUpdate()`**，不存在错误回调，
   原 M3 中「通过既有 listener 上报错误」的修法不可直接实施，需先扩展接口。
4. **「约 1~3 秒/MB」缺乏基准测试证据**，已按 Codex 修改删除该量化结论。
5. **H3 的机理表述过强**：「必然由 native write 抛 `IllegalStateException`」缺少 API 契约支持。
   正确修法是所有权排序（停生产 -> 取消播放任务 -> `AudioTrack.stop()/pause()` 唤醒阻塞写
   -> 等任务退出 -> 最后 `release()`），异常捕获只能作为兜底。
6. **H4 的第二种竞态推导过强**：重复 `playAac()` 确实泄漏旧 extractor、终止当前会话并可能阻塞
   调用线程；但 `super.release()` 在返回前已通过 codec 锁排除当前迭代，且 `RELEASING` 阻止 worker
   再次进入，所以之后释放外部资源不会重新造成 `readSampleData()` 访问已释放 extractor。
7. **原 H5 严重度过高**：自然 EOF 后资源需要调用方 `stop()` 的事实成立，但 Demo 已正确执行该责任，
   不是所有路径都会必然泄漏。该项改为 M18，作为公开所有权契约和一次性播放器终态设计问题处理。
8. **M10 的结论与建议过强**：独立 owner scope 可以合法调用挂起 `stop()`；真正危险的是在 codec worker
   回调中用 `runBlocking` 等待自身。把回调改成 suspend 并不能改变 worker 身份，必须设计独立终态路径。
9. **M14 部分误报**：CHANGELOG 已写明 `AacDecoder.decode()` 返回入队结果；遗漏的是其 JVM 描述符变化
   对预编译调用方的影响，以及非 `RUNNING` 状态调用 `flush()` 的新失败行为。
10. **原 L16 严重度过低**：重复 AAC CSD 会直接覆盖未释放的 decoder，属于确定的 MediaCodec 所有权
    丢失，已提升为 M19。

另有一处**主审人自身的反复**需记录：关于 L5（NetworkUtil）的归属，第一轮结论正确（本分支
`6fac4bdd5` 注释掉了这些常量），中途因过度采信子代理的 `git log -S` 结果而错误改判为
「2022 年既有」，随后经 `git show origin/master:...NetworkUtil.kt` 核实
**master 仍保留这些映射**，确认最初结论正确。`-S` 命中 `d775ff91a` 只是因为该提交**引入**了
这些常量，不能据此判断当前状态。**教训：子代理结论与自己已直接核验的观察冲突时，
应以重新直接核验为准，而不是采信任一方。**

---

# 8. 整改实现记录（2026-09-03）

本节记录在上述审查结论基础上实际落地的代码，不把静态验证等同于真机验证。

## 8.1 MediaCodec 基类

- `BaseMediaCodecSynchronous` 不再在提交输入 EOS 后立即退出。现在分别跟踪“输入 EOS 已提交”和
  “输出 EOS 已收到”，输入结束后使用有界等待继续排空，直到收到真实输出 EOS 才结束 worker。
- 带 EOS 标志且 `size > 0` 的输出先交给 `onOutputData()`，再报告完成；完成回调从输出 drain 分支移到
  worker 唯一出口，消除尾帧丢失和双重 `onEndOfStream()`。
- 同步、异步输入路径均保证已取得的 input buffer 在 `getInputBuffer()` 返回空、输入回调抛异常或尺寸
  校验失败时以 0 字节归还，避免 input buffer starvation。输出回调统一通过 `finally` 释放 output buffer；
  异步 EOS buffer 同样先交付有效数据。
- `onOutputFormatChanged()` 恢复向子类公开钩子转发；format/error/input/output 与启动、释放钩子均明确
  受同一 codec 操作锁保护。KDoc 已写明这些钩子必须有界、不可阻塞、不可同步等待 teardown。
- `codecJob` 增加 `@Volatile`，保留确定性释放路径中的锁后重读；`onCodecReleased()` 文档明确其在 codec
  从未创建时也会恰好调用一次。
- AAC/OPUS decoder 的 PTS 改为按成功接受的输入帧递增，不再用输出帧数推导下一输入时间戳。

对应闭环：M1、M2、M13、M15、M16、M17、L1、L11、L13、L15、L17，以及 input buffer 异常归还的
同型 starvation 风险。

## 8.2 AAC 文件与流播放

- `AacFilePlayer` 新增独立 `started` CAS。第二次 `playAac()` 会在写 callback、创建 extractor 或改变
  AudioTrack 前失败，现有播放会话不会被覆盖或终止，关闭 H4。
- `playAac()` 改为挂起入口并在 IO dispatcher 完成 extractor/codec 初始化；初始化失败先进入共享终态、
  释放资源，再把原异常重新抛给调用方。Demo 会记录错误、恢复按钮并给出提示，关闭 M11。
- 自然 EOF 由独立 terminal scope 执行 teardown，避免 codec worker join 自身；主动停止与自然结束共用
  `Mutex + CompletableDeferred` 终态屏障，并发 `stop()` 都会等待同一轮清理。资源全部释放、屏障完成后
  才调用完成 callback，因此 callback 内再次调用 `stop()` 不会自等待，关闭 M10、M18、L12、L13。
- `MediaExtractor.sampleTime` 改为在 `readSampleData()` 与 `advance()` 之前捕获。旧实现先前移 extractor
  再读取时间戳，会把当前 AAC access unit 错配为下一帧时间戳，并可能把最后一帧误判成 EOF；现在输入
  PTS 与实际送入 codec 的样本一一对应。自然结束还会依据 `AudioTrack.playbackHeadPosition` 等待已写入
  PCM 真正播放完成（最多 3 秒），再停止并释放 AudioTrack，避免 AAC 尾音被 flush。
- `AacStreamPlayer` 对相同重复 CSD 幂等忽略，对活动会话中的变化 CSD 明确拒绝并记录错误，不再覆盖旧
  decoder；小于 2 字节的非法配置也不会越界，关闭 M19。

## 8.3 OPUS 文件与流播放

- `OpusFilePlayer` 的 MediaCodec 输出 callback 从阻塞 `queue.put()` 改为有界队列 `offer()`；PCM 写入由
  IO 消费任务执行。队列持续满时按首帧及每 50 帧限频记录丢弃，不阻塞 callback 或主线程，关闭 H2。
- `stop()` 改为挂起并采用确定性所有权顺序：阻止新生产、取消任务、关闭输入文件、`AudioTrack.stop()`
  唤醒在途阻塞写、等待所有文件/播放任务退出、等待 decoder 释放，最后清队列并释放 AudioTrack。
  `StreamPlayerStopper` 也使用相同顺序；`AudioTrackPlayer.write()` 对释放竞态异常记录并安全返回，关闭 H3、M12。
- decoder 新增显式输入 EOS marker。文件生产者在最后一个完整帧后提交 EOS；输入队列瞬时满改为可取消
  重试，不再以 `IllegalStateException` 中止播放；自然完成依据真实 codec EOS 与同口径 PCM 入队/消费数，
  不再比较 decoder 输入数和输出数，关闭 M5、M6、L14。
- 自然结束先等待软件 PCM 队列耗尽，再依据 `AudioTrack.playbackHeadPosition` 等待硬件播放到已写入帧数，
  最后进入共享终态，避免完成 callback 立即触发 flush 丢尾音，关闭 M9。
- `playOpus()` 改为挂起并在 IO dispatcher 初始化；OPUS framing 扫描从逐字节 `seek + readFully` 改为
  8 KiB 分块 KMP 扫描。空文件、末尾裸 start code、EOF 截断 start code 均增加单测，初始化错误重新抛出，
  后续异步错误在清理完成后走 error callback，关闭 M7、L6～L10。
- `OpusStreamPlayer` 的 decoder callback 同样改为非阻塞入队，由 IO worker 写 AudioTrack；flush/stop 会
  清理等待 PCM，避免异步 codec callback 在主线程执行阻塞音频写。

## 8.4 Screenshot H.26x 初始化失败

- `ScreenDataListener` 新增带默认实现的 `onError(Throwable)`，现有实现无需强制修改。
- `Screenshot2H26xStrategy.startRecord()` 捕获并区分协程取消与普通初始化/录制异常。普通异常会记录完整
  throwable，按逆序释放已创建的 MediaCodec、input Surface、EGL surface/context/display，再调用错误回调；
  错误回调自身异常也会被隔离。
- MediaCodec 在创建后立即登记到策略字段，再执行 `configure()` 和 `setCallback()`；因此任一步骤抛错时，
  公共失败清理路径都能释放已经创建但尚未完整配置的 codec。删除了配置阶段读取 `outputFormat` 的无效操作，
  输出格式只由合法的 `onOutputFormatChanged()` 回调提供。
- `releaseEgl()` 能处理 null、`EGL_NO_DISPLAY`、`EGL_NO_CONTEXT` 和 `EGL_NO_SURFACE` 的部分初始化状态，
  关闭 M3。API 21～25 的真实 EGL/编码能力仍必须真机验证。

## 8.5 API 与变更记录

- `CHANGELOG.md` 已补充 `AacDecoder.decode()` JVM 描述符变化、`flush()` 非运行态抛错、AAC/OPUS 文件
  播放器挂起 API、OPUS decoder 构造器变化、EOS/teardown 修复以及 Screenshot 异步错误入口，关闭 M4、M14。
- `AudioActivity` 已迁移到挂起播放 API，错误时恢复 UI；Activity 的 IO scope 在 `onDestroy()` 后拒绝新任务，
  同时允许 `onStop()` 已提交的确定性清理完成，关闭 M8。
- 删除 `RecordSingleAppScreenActivity` 中两处过期的 API 26 注释；`ShellUtil.getProcessesList()` 改用
  可空局部行变量和 `use` 自动关闭 reader；恢复 CDMA/EVDO 网络代际映射并对平台弃用常量做局部 suppress；
  `AacEncoderWrapper.encoder` 收窄为 `val`，关闭 L3～L5、L17 的剩余项。

## 8.6 已完成验证与剩余真机项

已使用 JDK 17 和 `--rerun-tasks` 完成：

- `:audio:testDebugUnitTest`
- `:audio:ktlintCheck`
- `:audio:detekt`
- `:lib-common-android:testDebugUnitTest`
- `:lib-common-android:ktlintCheck`
- `:lib-common-android:detekt`
- `:screencapture:ktlintCheck`
- `:screencapture:detekt`
- `:demo:assembleDevDebug`

新增测试覆盖同步 EOS 延迟输出与单次完成、异步 EOS 尾数据、input buffer 异常归还、并发 `start()`、
两种 release API 幂等、format/error 在途回调锁、迟到 output callback，以及 OPUS 文件边界。

已在 SUNMI P3H（Android 11 / API 30）完成可自动观察的真机冒烟验证：

- 安装并启动 `demo-dev-debug.apk` 成功，进程保持存活。
- AAC 文件自然播放收到真实输出 EOS，尾部 PCM 在 EOS 前继续交付，按钮自动复位；主动停止路径也未出现
  `MediaExtractor`、MediaCodec 或 AudioTrack teardown 异常。该项只确认状态与日志，尾音听感仍需人工确认。
- OPUS 文件自然播放两次均正常结束，其中一次统计为 `queued=37`、`consumed=37`、`dropped=0`。
  另录制约 7 秒文件后，在播放队列达到 34 时主动停止，未出现 ANR、崩溃、MediaCodec 非法状态或
  AudioTrack 释放竞态；测试后已恢复设备原有 OPUS 文件。
- 将 OPUS 文件临时置空后点击播放，会在 IO 协程报告明确的 `No start code at position 0`，按钮恢复未选中，
  Activity 与进程保持存活；测试后已恢复原文件。
- 设备不支持 H.265 encoder 时，Screenshot 录制初始化失败会记录完整错误，Activity 与进程保持存活，
  不再发生未捕获异常闪退。该结果只验证失败清理，不代表 H.264 录制或 API 21～25 兼容性已经验证。

仍待执行或确认：

1. 人工确认 AAC/OPUS 自然结束和主动停止时的声音、尾音完整性，并继续覆盖快速重复操作及截断文件。
2. 用更长或更高负载的 OPUS 输入让 PCM 队列接近 64 槽上限，再停止并确认无 ANR、callback 卡死或
   AudioTrack 释放竞态。
3. AAC/OPUS 流播放的开始、丢帧重同步、停止与重新创建播放器。
4. API 21～25 与较新 Android 各至少一台设备验证 Screenshot H.264；H.265 仅在设备声明支持时验证。

---

# 9. 第三轮复审（2026-09-03，提交 `d1c87e65e`）

- 审查范围：`f917bbfdc..d1c87e65e`（`fix: harden media teardown and cleanup`，27 文件 +1478/-461）
- 审查方式：4 个并行专项代理分域独立审查（OPUS / AAC+同步基类 / codec 基类+异步 / screencapture+utils）
  + 主审人逐条回读源码复核
- 代理实跑：`:audio:testDebugUnitTest` 全绿（异步 14/14、同步 2/2、stopper 3/3、OPUS reader 5/5）；
  `:audio:detekt`、`:audio:ktlintCheck`、`:screencapture:detekt`、`:screencapture:ktlintCheck`、
  `:lib-common-android:detekt`、`:lib-common-android:ktlintCheck` 全部通过

## 9.0 结论摘要

| 域 | 结论 | CRITICAL | HIGH | MEDIUM | LOW |
|----|------|---------|------|--------|-----|
| OPUS（`OpusFilePlayer` 重写） | **BLOCK** | 0 | 1（H6） | 4 | 3 |
| AAC + 同步基类 | **BLOCK** | 0 | 1（H8） | 5 | 3 |
| codec 基类 + 异步 | **APPROVE** | 0 | 0 | 5 | 4 |
| screencapture + utils + demo | **BLOCK** | 0 | 1（H9） | 3（含原 H7） | 3 |

**对 `d1c87e65e` 的阻塞项：H6、H8、H9。**H7 降为 MEDIUM。H6 是本轮终态改造引入；
H8 是既有失败通知缺口在新终态模型中继续存在；H9 的 EGL 并发释放竞态在审查基线前已经存在，
并非四项都由本轮加固引入。

## 9.1 已确认真实闭环（无需重做）

以下均有可证伪证据，Codex 不必重复验证：

| 项 | 证据 |
|----|------|
| **H2** | `git grep "queue.put"` 在 `audio` 模块**零命中**（只剩 `ByteBuffer.put`）；`OpusFilePlayer.kt:162` 改为 `offer()`。持 `codecOperationLock` 时可达的每个调用都已有界非阻塞：`onDecoded -> offer`、`onInputData -> queue.poll()`、`onEndOfStream -> codecEos.complete()`（派发到 IO 而非内联）、`onError -> terminalScope.launch{}`。`queue.clear()` 已移到 join 之后（`:356`） |
| **H3** | `OpusFilePlayer.kt:345-357` 完全符合约定顺序，整体在 `withContext(NonCancellable)`（`:338`）内：`cancel()` -> `closeInputFile()` -> `audioTrackPlayer.stop()` 唤醒阻塞写 -> `cancelAndJoin()` -> `releaseAndJoin()` -> `clear()` + `release()`。唯一写入方与 `playbackHeadPosition` 读取均属 `playbackScopeJob`，release 时可证已死 |
| **H4** | `AacFilePlayer.kt:135` 的 `check(started.compareAndSet(false, true))` 是 `playAac` 的**字面第一条语句**，位于 `cb` 赋值（`:136`）、extractor 创建（`:141`）、`audioTrackPlayer.play()`（`:156`）与 `start()`（`:157`）之前；抛出发生在 `try` 之前，失败清理不会运行，**现有会话逐字节不受影响**。该文件已无任何废弃 `release()` 调用 |
| **M13** | `BaseMediaCodecSynchronous.kt:95` 返回 `!outputEosReceived`，输入 EOS 后继续排空；`onEndOfStream()` 移到循环之后（`:60`）恰好触发一次，**双触发结构上不可能** |
| M5 / M6 / M7 / M12 / L8 / L9 | 真 EOS 已发送；背压改为可取消重试；`playOpus` 挂起 + IO；扫描改 8KB 分块 KMP；一次性语义显式化 |
| **M9** | `AacFilePlayer.awaitAudioTrackDrain()`（`:221-235`）按 `playbackHeadPosition` 对齐 `writtenAudioFrames`，且**有 3 秒上界**并在超时告警；正确只对自然结束生效（`:190`），显式 stop 仍立即丢弃 —— 语义正确；32 位回绕已处理（`:237-238`） |
| M10 / M11 / M19 / L11 / L13 | 完成回调在 `terminalScope` 而非 codec worker；启动失败改为清理后重抛并被 Demo 处理；`AacStreamPlayer` 拒绝活动会话中的 CSD；`audio/src/main` 已无任何 `!!`；`terminalCompletion` 屏障成立 |
| L1 / L15 / L17 | `onOutputFormatChanged` 已在异步与同步两条路径转发（**签名未变，二进制兼容**）；`onCodecReleased` KDoc 已说明可能在 codec 从未创建时调用；测试 `@Suppress` 已补 |
| L3 / L4 / L5 | 两处过期 API 26 注释已删；`ShellUtil` 改可空局部 + `.use{}` 且**未引入 `!!`**；六个 `NETWORK_TYPE_*` 常量已恢复（`NETWORK_TYPE_IDEN` 本就在 master 上是注释状态，非残留） |
| M16 | `BaseMediaCodec.kt:55` 已加 `@Volatile` |
| **codec 基类 7 条不变量** | 全部存活，逐条有 `file:line` 证据；输出路径**改进**：空 buffer 分支现在也归还 index（此前泄漏） |

**本轮还顺手修好了一个此前无人发现的真实缺陷**：旧的输出 `when` 链中，带
`BUFFER_FLAG_KEY_FRAME | BUFFER_FLAG_END_OF_STREAM` 的输出 buffer（音频编码器常见）会命中 key-frame
分支，**`onEndOfStream()` 永不触发**。改为独立标志解码后修复。

`ScreenDataListener.onError(error: Throwable) {}` 有默认实现；经核实项目未设置任何
`-jvm-default` / `-Xjvm-default` 标志，Kotlin 2.3.10 默认 `-jvm-default=enable`，会同时生成真正的
JVM default 方法与 `DefaultImpls` 桥接，因此**对 Kotlin 与 Java 实现方均源码兼容、对预编译实现方
二进制兼容**（不会出现 `AbstractMethodError`）。

---

## 9.2 必修项（HIGH）

### H6（HIGH，本轮引入）`codecEos.await()` 无超时 —— 永久挂起换了一扇门

- 文件：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt:280`
- 使其可达的根因：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronous.kt:68-79`

`awaitNaturalCompletion()` 的**第一行就是裸 `codecEos.await()`，没有任何超时**。其后的每一步
（`:281` 的 `withTimeoutOrNull`、后续 playbackHead 等待）都有界，唯独这道闸没有。

可达路径（已核验）：`BaseMediaCodecAsynchronous.kt:51-58` 提交 EOS 的
`queueInputBuffer(..., BUFFER_FLAG_END_OF_STREAM)` 若抛出，`:68-74` 的补偿会 queue 一个
**`flags = 0` 的非 EOS 空 buffer —— EOS 标志就此永久丢失**；异常随后被 `:77-79` 的
`.onFailure { if (!isReleasing) log.e(...) }` **吞掉**，不触发 `onError`，`requestFailure` 永不调用。
异步路径没有同步路径 `notifyCodecFailure`（`BaseMediaCodecSynchronous.kt:44`）的等价物。

后果：`codecEos` 永不完成 -> `awaitNaturalCompletion()` 永久阻塞 -> `finishPlayback(NaturalEnd)`
永不执行 -> MediaCodec、AudioTrack、已打开的 `RandomAccessFile` 和三个协程全部不释放，
`endCallback` 永不触发（UI 按钮卡在选中态）。任何丢弃 EOS 标志的设备也会走到同一结局。

**这是相对旧行为的回归**：旧代码以 `isDecodeDone` 为闸并有 3 秒兜底，`endCallback` 总会触发。

**建议修法**：

1. `withTimeoutOrNull(EOS_TIMEOUT_MS) { codecEos.await() }`；超时必须按失败终态清理并走错误回调，
   不能伪装成自然完成；
2. 补偿路径必须**重新发送 EOS**，而不是降级成 `flags = 0`；
3. 异步输入回调不要只把失败记日志 —— 应路由到错误钩子（与同步路径的 `notifyCodecFailure` 对齐）。

### H7（复核后降为 MEDIUM）Demo 停止路径缺少异常边界

- 文件：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt:71`、
  `:307`、`:317`、`:326`、`:332`、`:336`、`:337`
- 触发源：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt:218`

`ioScope = CoroutineScope(ioScopeJob + Dispatchers.IO)`（`:71`）**没有 `CoroutineExceptionHandler`**。
播放路径（`:169`、`:199`）本轮已正确加了 try/catch 并重抛 `CancellationException`，但**停止路径
全部是裸 `launch`**：

```kotlin
private fun stopAacPlayback() {
    val player = aacFilePlayer ?: return
    aacFilePlayer = null
    ioScope.launch { player.stop() }   // :326 —— 无 catch
}
```

`SupervisorJob` **不会**吞掉根子协程的异常，它会走 `handleCoroutineException` -> 线程默认未捕获
处理器 -> **应用崩溃**。而本轮同一提交恰好把 `AacFilePlayer.finishPlayback()` 改成了显式重抛
（`:218` `releaseFailure?.let { throw it }`），因此这条路径**由本轮设计变成可达**。

原文举出的具体失效链不成立：`BaseMediaCodec.releaseAndJoin()` 不调用 `MediaCodec.stop()`，其
`flush()`、`release()` 与 subclass cleanup 异常也会在基类内部记录并吸收；当前 AAC/OPUS 停止路径
的大多数 native 异常同样已在封装层吸收。因此没有足够证据证明现有 Demo 可以沿该链稳定触发崩溃。
不过根 `launch` 没有 handler、停止任务也没有局部 catch 的设计风险真实存在，未来任何未吸收异常都会
交给线程默认未捕获异常处理器，故仍应在本轮修复，但严重度调整为 MEDIUM。

**建议修法**：给 `ioScope` 装 `CoroutineExceptionHandler`，或让每个停止 `launch` 体套用播放路径
已有的 `catch (CancellationException) { throw it } catch (Exception) { log }` 模式。

同族问题（严重度 MEDIUM，见 M21）：`OpusFilePlayer` 的 `terminalScope`（`:70-72`）同样只有
`SupervisorJob` 而无 handler，`:380` 也重抛。

### H8（HIGH）codec 失败路径完全绕开新的终态机制

- 文件：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt`（缺少 `notifyCodecFailure` 覆写）
- 基类：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronous.kt:77-93`、`:60`

M18 只对**自然 EOF**修好了。同步基类还有**第三条终止路径**：遇到 `CodecException`、
`IllegalStateException` 或其他异常时设置 `codecFailed` 并结束循环，且 `:60` 会**刻意抑制
`onEndOfStream()`**。其中只有 `CodecException` 分支调用 `notifyCodecFailure(e)`；另外两条分支
连失败通知都没有。`AacFilePlayer` 也没有覆写 `notifyCodecFailure`，所以三类错误都无法进入播放器终态。

失效场景：截断或损坏的 AAC 文件让 decoder 中途抛出 -> worker 退出 -> `finishPlayback` 永不到达 ->
`endCallback` 永不触发，`MediaExtractor` 的 fd、`AudioTrack` 和 `MediaCodec` 实例长期占用；
Demo 的开关卡在选中态且不给任何错误提示。

`OpusFilePlayer.kt:324-327` 的 `requestFailure` -> `TerminalReason.Failure` 加上其 `errorCallback`
构造参数已经证明了正确形状 —— **AAC 播放器是唯一的例外**。

**建议修法**：覆写 `notifyCodecFailure`，以失败原因走同一条 `finishPlayback` 终态路径；
并为 `playAac` 增加与 `playOpus` 对称的 `errorCallback` 参数。

### H9（HIGH）EGL/MediaCodec 拆除与仍在运行的录制协程无任何 rendezvous -> 原生 abort

- 文件：`screencapture/src/main/kotlin/com/leovp/screencapture/screenrecord/base/strategies/Screenshot2H26xStrategy.kt:52`、`:381`、`:387`、`:391`、`:422`、`:425-427`、`:445-458`
- 调用点：`demo/.../examples/RecordSingleAppScreenActivity.kt:113`（主线程）

`startRecord()` 在 `:387` 用 `CoroutineScope(Dispatchers.IO).launch { while (isRecording) ... }`
启动录制循环，**并丢弃了返回的 `Job`** —— 没有任何地方持有或 join 它。`onRelease()`（`:425`，
主线程）直接调用 `releaseResources(stopEncoder = true)`，后者在 `:446` 置 `isRecording = false`
之后**立即**执行 `encoder.release()` 与 `releaseEgl()`（`eglDestroySurface` / `eglDestroyContext` /
`eglTerminate`），**完全不等待录制协程观察到该标志**。

`isRecording` 在 `:51-52` 已有 `@Volatile`，而且审查基线 `f917bbfdc` 中也已经存在；原文关于
“没有 `@Volatile`”的判断错误。可见性并不能解决资源所有权问题：主线程仍然没有等待 IO 线程退出，
也没有保证 EGL 的销毁回到创建并持有 current context 的线程。

失效场景：用户在循环处于 `encodeImages()`（`:191-214`）内时取消勾选。主线程执行
`eglTerminate(display)`，同时 IO 线程正在对已销毁的 display 与已释放的编码器输入 Surface 执行
`renderer.draw(...)` / `EGL14.eglSwapBuffers(...)` -> **SIGSEGV 原生 abort**。
`:411` 的 `try/catch (Throwable)` **接不住原生 abort**。另外 `:312` 的 `EGL14.eglReleaseThread()`
释放的是**主线程**的绑定（主线程根本没有绑定），IO 线程仍持有已销毁的 context 为 current。

**建议修法**：保留 `startRecord()` 返回的 `Job`，把 `onRelease()` 改为挂起/阻塞的 `cancelAndJoin()`
（或把 `releaseResources` post 回持有 EGL context 的那个 IO 线程执行），确保**所有 EGL 调用都在
拥有该 context 的线程上完成**。`@Volatile` 已存在，无需重复添加。

---

## 9.3 建议修复（MEDIUM）

| 编号 | 文件:行 | 内容 |
|------|---------|------|
| M20 | `audio/.../opus/OpusFilePlayer.kt:119-126` | `playOpus` 失败路径在**调用方 dispatcher** 上做终态清理：`stop()` 正确用了 `withContext(Dispatchers.IO)`（`:131`），但 catch 路径直接调 `finishPlayback`，位于 `:108` 的 `withContext` **之外**。`playOpus` 是公开 suspend API，惯用调用是 `lifecycleScope.launch{}`（主线程）→ 空文件/不支持编码时，主线程执行 `codec.release()` 与阻塞 `ReentrantLock.lock()` → ANR 风险。修法：`catch { withContext(Dispatchers.IO + NonCancellable) { finishPlayback(...) }; throw e }` |
| M21 | `audio/.../opus/OpusFilePlayer.kt:380`（由 `:310`、`:326` 启动） | `terminalScope`（`:70-72`）有 `SupervisorJob` 但**无 `CoroutineExceptionHandler`**；`releaseFailure` 非空时 `:380` 抛出根 `launch` → 默认未捕获处理器 → 崩溃。对 `stop()`/`playOpus` 重抛是对的（有调用方），对两条 launch 路径是错的。与 H7 同族，一并处理 |
| M22 | `audio/.../opus/OpusFilePlayer.kt:161-169` | 背压变成**静默丢帧**：`offer`-or-drop 修好了 H2，却把挂起换成数据丢失，而生产侧没有真正节流（自适应 delay 每次仅 +1ms 且被 `maxFrameSizeMs = 20` 硬顶，`:203`、`:211-219`）。AudioTrack 一旦停滞（设备慢、失焦、`PLAYSTATE_PAUSED` 时 `write` 立即返回 0），64 槽填满后 PCM 被永久丢弃，仅 `w` 级日志 → **可听见的断音**。修法：在 `produceDecoderInput` 中按高水位真正挂起，而不是在 codec 回调里丢弃 |
| M23 | `audio/.../opus/OpusFilePlayer.kt:352`、`:358` | 两处 `catch (Throwable)` 存入 `releaseFailure` 而**不重抛 `CancellationException`**，违反项目硬规则；文件顶部 `:8` 已 import `runCatchingPreservingCancellation`，直接换用。且 `:380` 可能把 CE 抛出非取消协程，被 kotlinx 静默当作正常取消 |
| M24 | `BaseMediaCodecSynchronous.kt:52-61`；`BaseMediaCodec.kt:158-167` | 输入 EOS 后 worker 无限循环 `dequeueOutputBuffer(10ms) + delay(1)`，**若 codec 永不置 `BUFFER_FLAG_END_OF_STREAM`（真实的设备相关失败模式）则 `outputEosReceived` 永不翻转** → 与 H8 相同的终局，且不经由任何异常。对称地，`releaseAndJoin()` 的两次 `cancelAndJoin()` 在 `NonCancellable` 下**无超时**。修法：worker 循环加 EOS 墙钟预算；join 外加 `withTimeoutOrNull` 并明确记录"worker 未退出" |
| M25 | `audio/.../aac/AacFilePlayer.kt:160-168` | 与 M20 同型：catch 位于 `withContext(Dispatchers.IO)` **之外**，`finishPlayback` 只裹了 `NonCancellable` 而未切 IO → `lifecycleScope.launch { playAac(...) }` 会在**主线程**执行 `audioTrackPlayer.stop()`、无界 `cancelAndJoin()`、`MediaExtractor.release()`、`AudioTrack.release()`。另：`catch (e: Exception)` 会捕获 CE（`:167` 确实重抛，规则未违反）但把取消记成 `"AAC playback start failed"`，应拆出单独的 CE 分支 |
| M26 | `audio/.../aac/AacStreamPlayer.kt`、`audio/.../opus/OpusStreamPlayer.kt` | **两个 stream player 都没有终态闸**：stop 会清空 CSD、永久取消 `ioScope` 并释放 AudioTrack；stop 后到达的新 CSD 却能再次创建并启动 decoder，而消费任务已无法启动，造成无声播放和 MediaCodec 泄漏。修法：两者都增加一次性 `stopped` 闸，并在 monitor 内二次检查以关闭 start/stop 竞态 |
| M27 | `audio/.../base/StreamPlayerStopper.kt:31-46`；`AacStreamPlayer.kt:203-205` | 顺序本身正确（detach -> 取消 owner scope -> `audioTrackPlayer.stop()` 唤醒阻塞写 -> 释放 decoder -> 最后释放 AudioTrack），三个测试也钉住了它。两个隐患：(1) `:35` 的 `ioScope.coroutineContext[Job]?.cancelAndJoin()` **正是 `BaseMediaCodec.releaseAndJoin` 用 `require(codecJob !== currentCoroutineContext()[Job])` 防的那种自 join** —— 若将来任何调用方从该 `ioScope` 内启动的协程（如 `AacStreamPlayer.kt:136` 的延迟重置协程）调用 `stopPlayingAndJoin()`，父 job 会被自己的子协程 join，在 `NonCancellable` 下永久挂起且无超时。当前无此调用方，但该守卫是零成本的；(2) `stopAndJoin` 与 `stopPlayingAndJoin()` 都**未切到 `Dispatchers.IO`**，与 `AacFilePlayer.stop()` 不一致 |
| M28 | `screencapture/.../Screenshot2H26xStrategy.kt:132-134` | `ScreenDataListener.onError` **未在异步 MediaCodec 失败路径接线**：`override fun onError(codec, e)` 只用 **debug 级**记日志，既不调 `builder.screenDataListener.onError(e)` 也不释放任何资源。这是 `start()` 成功之后唯一的 codec 失败通道 → 编码器中途抛 `CodecException` 时 `isRecording` 仍为 true，循环持续向死 codec 灌帧，无帧产出且应用永不知情。与本轮 CHANGELOG 自称"通过 `ScreenDataListener.onError()` 上报"不符。另（提示性）：`:408-410` 的 `CancellationException` 分支有意不触发 `onError` 是对的，但应在 `ScreenDataListener.kt:16` 的 KDoc 中写明，否则只在 `onError` 中清理的客户端会在取消时泄漏 |
| M29 | `demo/.../audio/AudioActivity.kt:345-350` | `ioScopeJob.complete()` 使新 `launch` 被拒（好），但既有子协程**永不取消**且无上界，而它们都捕获了 `this@AudioActivity`。具体路径：`stopPcmPlayback()`（`:317-320`）在 scope 内 `playbackThread?.join()`，而 `playPcmThread` 阻塞在**不可中断**的 `FileInputStream.read()`（`:139`）—— 大文件 + 慢存储时 Activity 引用可无界地活过 `onDestroy()`。该 scope 内没有任何 `withTimeout`。首选修法仍是 `lifecycleScope` + 对确需完成的释放块加 `NonCancellable` |
| M30 | `CHANGELOG.md`；`BaseMediaCodecAsynchronous.kt:103-106`、`:123` | **三处未记录的公开行为变更**（新 CHANGELOG 条目只覆盖了 EOS 排空与 buffer 归还）：(a) `onOutputData` 不再对 `size == 0` 的非 config buffer 调用 —— 依赖回调计数或在空 buffer 上重置状态的子类行为改变；(b) 带 payload 的 EOS buffer 现在**同时**投递 `onOutputData` 与 `onEndOfStream`，而此前 `KEY_FRAME\|EOS` 只投递 `onOutputData`；(c) `onOutputFormatChanged` 从死 API 变为在异步与同步两条路径都会触发 —— 任何既有覆写会**开始执行，且在锁内**。应在 CHANGELOG 增加"行为变更"条目 |
| M31 | `IAudioMediaCodec.kt:87`、`:22-27`、`:29-34` | **锁契约文档只补了一半**：`onOutputFormatChanged`、`onError` 已加警告，但同样在锁内调用的 `onEndOfStream`、`onInputData`、`onOutputData` 仍无说明 —— 而 `onEndOfStream` 恰恰是实现方最自然用来触发 teardown 的钩子。`OpusDecoder.kt:109-111` 就在锁内调用了调用方提供的 `endCallback` lambda。失效场景：使用者写 `endCallback = { runBlocking { player.stopAndJoin() } }` → `releaseCodecOnce()` 阻塞在由同一 MediaCodec 回调线程持有的锁上 → **该回调线程永久死锁**。仓库内调用方恰好都是非阻塞的，故无现行 bug |
| M32 | `BaseMediaCodec.kt:79`、`:81`、`:256-259`、`:270-273`；`:51-54` | M15/M16 残留：`setFormatOptions()`、`setMediaCodecOptions()`、`createMediaFormat()`、`createCodec()` 全部在 `:100-113` 于锁内执行，且都是 public/abstract 可覆写，但四者的 KDoc 均未说明锁契约（`createCodec` 的 KDoc 甚至仍只写"多数情况下你不需要覆写"）。`setMediaCodecOptions` 已被 `BaseMediaCodecAsynchronous:27` 覆写，说明外部子类确实会覆写它。M16 的 `@Volatile` 已加，但**排序论证注释未补** |
| M33（原 L22） | `lib-common-android/.../shell/ShellUtil.kt:142-168` | P3H 的真实 `adb shell ps` 第一行是 `USER PID PPID VSZ RSS WCHAN ADDR S NAME`，旧解析会把 `"PID"` 交给 `Integer.valueOf()` 并抛出未捕获的 `NumberFormatException`。该问题会让公开函数在常见 toybox 输出上直接失败，故由 LOW 升为 MEDIUM |

---

## 9.4 可选项（LOW，第三轮新增）

| 编号 | 文件:行 | 内容 |
|------|---------|------|
| L18 | `audio/.../AudioTrackPlayer.kt:196-209`（由 `OpusFilePlayer.kt:347` 调用） | `stop()` 先调 `pause()`，而 `pause()` 内部是 `audioTrack.pause()` **紧接** `audioTrack.flush()` —— 此时消费者尚未 join。`pause()` 正是用来唤醒阻塞写的，紧随其后对另一线程仍在返回途中的 track 调 `flush()` 不是有文档保证的安全组合。建议 `prepareStop` 只做 `pause()`/`stop()`，`flush()` 挪到 join 之后 |
| L19 | `audio/.../opus/OpusFilePlayer.kt:374` vs KDoc | **`endCallback` 在显式 stop 时不再触发**（`ExplicitStop -> Unit`），而旧实现总会触发。这是**静默的契约变更**；Demo 恰好不依赖它（由勾选框监听器驱动停止），但外部 JitPack 使用者若依赖"结束回调必定触发"会静默失效。应在 `playOpus` 的 KDoc 写明 |
| L20 | `audio/.../opus/OpusFilePlayer.kt:331` vs KDoc `:129` | `stop()` 的"等待释放完成"承诺**只对首个调用者成立**：败者的 `terminalCompletion.await()` 在 `withContext(NonCancellable)` 之外，被取消的调用方 `stop()` 会抛 CE 并在 teardown 完成前返回 |
| L21 | `audio/src/test/.../OpusFramedFileReaderTest.kt` | L10 仅部分闭环：已补空文件、末尾裸 start code、EOF 截断 start code（3 例，全通过），但缺新 KMP 扫描器最需要的两例：(a) **payload 内 start code 别名**（如 `\|le\|leo\|`）—— 唯一能触发 `OpusFramedFileReader.kt:52-53` `prefixTable` 回退的输入（`\|leo\|` 的 prefixTable 为 `[0,0,0,0,1]`，非平凡）；(b) **跨 8 KiB 缓冲边界的 start code** —— 唯一能测 `matchedBytes` 跨块进位与 `:57` 负索引算术的输入 |
| L23 | `demo/.../RecordSingleAppScreenActivity.kt:41-66`、`:111-114` | Demo 未实现新的 `onError`，`releaseAfterFailure()` 之后 `videoH26xOsForDebug` 仍开着、`toggleBtn` 仍是选中态，UI 谎称仍在录制 —— 新钩子没有示范消费者。另（既有）：`:111-114` 在 `screenProcessor.onRelease()` **之前**关闭 `videoH26xOsForDebug`，而 `quitSafely()` 会排空已入队消息，待处理的 `onDataUpdate` 会向已关闭的流 `write()` → `scr-rec-send` 线程抛未捕获 `IOException` → 崩溃 |
| L24 | `Screenshot2H26xStrategy.kt:445-458` | `releaseHandler()` 现在跑在 `encoder.stop()` **之前**（旧 `onRelease()` 是 `onStop()` → `releaseHandler()`）。`quitSafely()` 仍会排空已入队帧故无数据丢失，`::screenshotHandler.isInitialized` 守卫（`:437-438`）也正确保护了 `initHandler()` 未执行的失败路径。仅因该顺序变更是静默且未文档化而记录 |
| L25 | `audio/.../aac/AacFilePlayer.kt:195-202`、`:243` | `releaseExternalResources()` 位于 `finally` 中：若 `awaitAudioTrackDrain()` / `audioTrackPlayer.stop()` / `releaseAndJoin()` 抛出，`MediaExtractor.release()` 会在 worker 可能仍处于同一 native 对象的 `readSampleData` 中时执行（worker 在 `:85` 捕获了局部引用，早于字段置空）→ 潜在 SIGSEGV。当前三者均有内部保护故实际不可达，但"先 join 再释放 native"这条不变量应显式化而非依赖巧合 |
| L26 | `audio/.../AudioTrackPlayer.kt:118-138` | `playState`/`state` 检查相对 native `write` 仍非原子（挂起风险已由"先唤醒再 join"消除，故降级而非关闭）。次生影响：新的 `getOrElse { ... 0 }` 使"写失败"与"什么都没写"不可区分，于是 `writtenAudioFrames`（`AacFilePlayer.kt:111-114`）少计，`awaitAudioTrackDrain` 可能**提前退出并剪掉尾音**。它有日志，不算静默吞错，但计数已经有损 |
| L27 | `audio/.../aac/AacStreamPlayer.kt`、`audio/.../opus/OpusStreamPlayer.kt` | 两个 stream player 的初始化回滚都使用废弃的非 join `release()`。由于入口是同步函数且失败的 `BaseMediaCodec.start()` 已自行完成部分初始化回滚，这一项不应通过阻塞调用强行 join；后续若把 stream 初始化改为挂起 API，再统一迁移到 `releaseAndJoin()` |
| L28 | `BaseMediaCodecAsynchronous.kt:70-72`、`:91`；`BaseMediaCodecSynchronous.kt:130-132` | (a) 补偿用的内层 `runCatching` 捕获 `Throwable`，若补偿调用抛 CE 会被降为 suppressed 而非传播 —— 实际不可达，但正是项目规则禁止的写法，同包内已有 `runCatchingPreservingCancellation`；(b) 输出路径不对称：若 `getOutputBuffer(index)` 自身抛出，index 永不归还，而输入路径现在已显式补偿 |
| L29 | `Screenshot2H26xStrategy.kt:451`、`:454`、`:456` | 使用裸 `runCatching`，会吞 `CancellationException`。当前非活 bug（被包裹的都是非挂起调用），但违反仓库约定。根因是 `runCatchingPreservingCancellation` 目前是 `audio` 模块的 `internal`（`audio/.../base/RunCatchingExt.kt:6`），`screencapture` 用不到 —— 建议提升到共享模块 |

---

## 9.5 测试缺口（本轮硬伤，单列）

1. **`audio/src/test` 中完全没有 `OpusFilePlayer`、`OpusDecoder`、`AacFilePlayer` 的任何测试。
   把 H2、H3、H4 的修复整个回退，零个测试会失败。** 本轮最核心的三个修复没有任何回归保护。
2. **两个新基类测试是"半表演"**：
   - `concurrent start admits only one caller`（`BaseMediaCodecAsynchronousTest.kt:151-179`）：
     线程 A 在 `enteredStart` 触发前就已发布 `STARTING`，主线程观察到的是 `STARTING` 而非 `NEW`，
     因此**一个非原子的 `check(state == NEW); state = STARTING` 闸也能同样通过** —— 从未进入 CAS
     竞争窗口。M17(a) 仅部分覆盖；真正的竞争需要两个线程从同一屏障释放并断言恰好一个成功。
   - `successful release remains exactly once`（`:183-196`）：第二次 `release()` 与 `releaseAndJoin()`
     都在 `markReleasing()`（`BaseMediaCodec.kt:237`，状态已是 `RELEASED`）就返回，根本到不了
     `releaseCodecOnce()`。**即使把 `codecReleased` CAS（`:179`）回退，该测试也不会失败** ——
     实际起作用的是生命周期状态。M17(b) 只在可观察层面覆盖，未钉住机制。
3. **过硬的三例**（可作为范式）：`release waits for active format callback` / `...error callback`
   （`:200-232`）—— 移除 `:118` 或 `:129` 的 `withCodecOperationLock` 即 `assertFailsWith<TimeoutException>`
   失败；以及 `worker drains delayed output EOS and reports completion once`
   （`BaseMediaCodecSynchronousTest.kt:53-85`）—— 回退后 `assertEquals(1, outputCount)` 得 0。
4. **M13 的"双触发"那一半仍未测**：现有 mock 让回退后的代码根本进不了循环内 EOS 分支，
   因此 `assertEquals(1, endCount)` 照样通过；需要一个**第一次 dequeue 就返回 EOS** 的 mock。
5. 其余缺口：`require(size <= capacity)` 守卫（`:60-64`）无测试；null-`getInputBuffer` 补偿
   （`:40-44`）无测试；codec 永不发 EOS 的超时行为无测试（对应 M24）；`notifyCodecFailure`
   终止路径无测试（对应 H8）。
6. **建议的最小补测**（OPUS/AAC 播放器层）：(a) 用 `write` 阻塞在 latch 上的假 `AudioTrackPlayer`，
   断言 `stop()` 能返回且 `release()` 严格发生在 write 返回之后；(b) 把 `queue` 填满，断言
   `onDecoded` 绝不阻塞；(c) N 路并发 `stop()`，断言只释放一次且不挂起。

---

## 9.6 第三轮修复优先级

| 顺序 | 项 | 理由 |
|------|-----|------|
| 1 | **H9** | 唯一会导致 **SIGSEGV 原生 abort** 的问题，且 `catch (Throwable)` 接不住 |
| 2 | **H6 + M24 合并处理** | 无界等待把"永久挂起"换扇门重新引入；异步与同步两条路径是同一模式，且都需要 EOS 丢失时的兜底 |
| 3 | **H8** | codec 失败路径绕开终态，资源长期占用且 UI 无反馈；OPUS 已有正确形状可对齐 |
| 4 | **H7 + M21 合并处理** | 作为异常边界加固处理；原文没有证明当前代码存在 HIGH 级稳定崩溃链 |
| 5 | M26、M28 | 均为确定的资源所有权丢失 / 失败完全不上报 |
| 6 | M20、M25 | 失败清理落在主线程 → ANR 风险；两处同型 |
| 7 | M22、M23、M27、M29 | 可听见断音、CE 规则违反、自 join 隐患、scope 无界 |
| 8 | M30、M31、M32 | CHANGELOG 行为变更与锁契约文档；不影响运行但影响外部使用者 |
| 9 | **§9.5 测试缺口** | 建议至少补 H2/H3/H4 的播放器层回归测试与两个"半表演"测试的强化，否则这三项随时可能静默回归 |
| 10 | L18～L29 | 可择机处理 |

---

# 10. Codex 复核修正与第四轮整改（2026-09-03）

本节在逐条回读 `d1c87e65e` 源码、对比审查基线并检查当前编译产物后，对 §9 的事实错误做出
修正，同时记录后续实际代码整改。§9 保留为 Claude Code 对指定提交的历史审查记录；若与本节冲突，
以本节及当前源码为准。

## 10.1 复核修正

1. **H6、H8、H9 保持 HIGH，H7 降为 MEDIUM。**H7 关于无 handler 根协程的通用风险成立，但
   `BaseMediaCodec.releaseAndJoin()` 不调用 `MediaCodec.stop()`，codec flush/release 异常也已在基类
   内部吸收，原文给出的现行崩溃链没有代码证据。
2. **H9 不是本轮新引入，且 `isRecording` 已经有 `@Volatile`。**真正问题是录制 Job 被丢弃、
   `Dispatchers.IO` 不能保证协程恢复到同一 EGL owner thread，以及主线程没有等待绘制退出就释放
   MediaCodec/EGL。原 `onStop()` 还先 stop encoder、后清 `isRecording`，存在同型竞态。
3. **H8 的原始异常分支描述不准确。**`d1c87e65e` 只有 `CodecException` 调用
   `notifyCodecFailure()`；`IllegalStateException` 与普通异常仅停止 worker，三条路径都无法让
   `AacFilePlayer` 进入失败终态。
4. **H6 超时不能伪装成自然结束。**codec EOS 超时代表流水线状态未知，必须进入失败终态、释放资源并
   调用错误回调，不能调用自然完成回调。
5. **M26 与 L27 同时影响 AAC 和 OPUS stream player。**两者都在 stop 时永久取消 owner scope、释放
   AudioTrack 并清空 CSD，因此都必须拒绝 stop 后迟到的配置帧。
6. **原 L22 升为 M33。**已在 SUNMI P3H 上直接确认 toybox `ps` 输出包含 `USER PID ...` 表头；旧代码
   会对 `"PID"` 执行整数转换，使公开 API 在常见设备上直接失败。
7. `successful release remains exactly once` 是有效的公开行为测试；内部 `codecReleased` CAS 即使与
   生命周期状态构成双重保护，也不要求删除任一保护后该测试必须失败。并发 start 测试确实没有形成两个
   调用者同时竞争 `NEW` 的窗口，需强化。
8. `ScreenDataListener.onError()` 的兼容性结论成立。当前 class 文件同时包含 JVM default method 与
   `DefaultImpls` 桥接。**归属修正（2026-09-15）**：该默认方法在 `d1c87e65e` 就已存在，
   `93c1c3c0f` 只更新了它的 KDoc；此条针对的是 `d1c87e65e` 引入的接口变更，不是本节所述提交。

## 10.2 已实施代码修复

### MediaCodec 与 AAC/OPUS

- 将 `notifyCodecFailure(Throwable)` 提升到 `BaseMediaCodec`，同步路径的 `CodecException`、
  `IllegalStateException` 和普通异常，以及异步 input/output callback 异常都会在 codec 锁外统一通知 owner。
  owner 的错误回调异常会被隔离，不会再次杀死 codec worker。
- 异步 input buffer 补偿保留原本要提交的 EOS flag；`getOutputBuffer()` 自身抛错时也会在 `finally`
  归还 output index。同步补偿改用保留取消语义的 helper，并且取消异常也会先归还已经 dequeue 的 input
  index 再继续传播。同步与异步路径都不再把“带 EOS flag 但 output buffer 为 null”误判成 EOS 丢失。
- 同步 codec 在 input EOS 后最多等待 3 秒取得 output EOS；超时调用失败钩子，不报告自然完成。
  `releaseAndJoin()` 没有添加强制 join 超时：如果 worker 仍在持锁执行 native/callback，超时后并发释放
  codec 会制造 use-after-release，安全性比等待更差。所有仓库内 callback 已通过 KDoc 要求有界。
- `AacFilePlayer` 新增异步 `errorCallback`，codec 失败、自然结束、主动 stop 和初始化失败共用同一个
  终态屏障；失败会在资源释放后上报。AAC/OPUS 初始化失败清理明确切到 IO dispatcher，取消异常单独
  处理并保持原始取消为主异常。终态清理中的取消或普通异常都会先完成资源屏障及错误回调，公开 `stop()`
  再向调用方传播；根终态协程不会因清理异常形成未捕获崩溃。
- `OpusFilePlayer` 的 codec EOS 等待增加 3 秒上限并在超时走失败终态。生产者在 PCM 队列达到 48 时
  暂停喂帧，降到 32 后恢复；callback 仍保持非阻塞，极端情况下队列满会明确终止为错误，不再静默断音。
- AAC 与 OPUS stream player 都增加一次性 stopped 闸，并在各自 monitor 内二次检查，阻止 stop 后的
  迟到 CSD 重建 decoder。`StreamPlayerStopper.stopAndJoin()` 会拒绝 owner scope 内的自等待，并统一切到 IO。
- `IAudioMediaCodec`、format/codec 创建钩子和配置钩子的 KDoc 补齐 codec operation lock 契约。

### Screenshot、Demo 与 ShellUtil

- `Screenshot2H26xStrategy` 使用专用单线程 dispatcher 完成 EGL 初始化、截图绘制、encoder 停止和
  EGL 销毁，不再让持有 current context 的协程在通用 IO 线程池迁移。录制 Job 被保存，`onStop()` /
  `onRelease()` 只发出停止请求；新增 `releaseAndJoin()` 等待录制循环、encoder、EGL 和 callback handler
  完全退出。销毁前先解除 current EGL context。
- MediaCodec 运行期 `onError()` 会停止生产，并在同线程完成资源清理后调用 `ScreenDataListener.onError()`；
  KDoc 明确主动停止与取消不触发错误回调。encoder 的 detach 与 callback 访问使用同一把锁，确保停止线程
  先等待在途 callback，后续 callback 也无法再碰已释放的 codec；异步投递前复制 flags 与 PTS，不捕获
  MediaCodec 可复用的 `BufferInfo` 对象。
- Screenshot Demo 实现 `onError()`、恢复 toggle 并提示用户；停止时先等待 strategy 与 handler 完全释放，
  再 flush/close 输出流。写入和关闭使用同一把锁，异常不会逃出 handler 线程。Activity 销毁时也会立即
  发出停止请求，并以不可取消的释放任务等待 native 资源完成清理，避免直接退出页面后录制继续持有 Activity。
- Audio Demo 的清理任务使用统一异常边界及兜底 `CoroutineExceptionHandler`。PCM 输入流保存为可关闭引用，
  stop 时先 close + interrupt 再 join，避免 Activity 因阻塞读取被长期持有。
- `ShellUtil` 将进程文本解析提取为可测试函数；PID/PPID 使用 `toIntOrNull()`，自动跳过 toybox 表头和
  非数据行，同时保留 reader 的 `use` 关闭语义。

## 10.3 新增或强化的回归测试

- 异步 codec：null input buffer、超大输入、EOS 补偿 flag、output buffer lookup 异常归还及失败通知。
- 同步 codec：首个 output 即 EOS 时只完成一次、永远没有 output EOS 时超时失败、非法 codec 状态上报。
- 同步与异步 codec：带 EOS flag 但 `getOutputBuffer()` 返回 null 时仍完成 EOS，并恰好归还一次 output index。
- 并发 start：两个线程从同一门闩同时释放，断言只有一个调用成功。
- `StreamPlayerStopper`：从 owner scope 内调用 `stopAndJoin()` 必须在任何副作用前失败。
- OPUS KMP reader：重叠 start-code 前缀回退与跨 8 KiB 扫描块边界。
- ShellUtil：真实 toybox 风格表头被跳过、后续进程行正常解析。

播放器与 EGL/MediaCodec 的 Android native 对象仍不适合只用 JVM mock 证明完整线程安全；真机发布验证
仍需覆盖 §8.6 的四项，并新增 Screenshot 录制过程中快速停止、Activity 退出以及 codec 运行期错误注入。

## 10.4 本轮本机验证（2026-09-07）

使用 JDK 17 和 `--rerun-tasks` 强制执行，结果如下：

- `:audio:testDebugUnitTest`：通过，共 42 条测试；
- `:audio:ktlintCheck`、`:audio:detekt`：通过；
- `:lib-common-android:testDebugUnitTest`、`:lib-common-android:ktlintCheck`、
  `:lib-common-android:detekt`：通过；
- `:screencapture:ktlintCheck`、`:screencapture:detekt`：通过；
- `:demo:ktlintCheck`、`:demo:compileDevDebugKotlin`：通过；
- `:demo:assembleDevDebug`：通过，包含 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 的现有 native
  构建链路。

以上结果证明 JVM 回归、静态规则、Kotlin 下游调用点及 APK 集成构建通过，不等同于 EGL、MediaCodec、
AudioTrack 的真机时序验证。当前连接的 SUNMI P3H（Android 11 / API 30）不具备 HEVC encoder，而 Demo
固定使用 H.265。本轮已在该设备点击“Start Recording Screen by Screenshot”：初始化按预期返回
`Failed to initialize video/hevc`，错误经 `ScreenDataListener.onError()` 上报；toggle 自动恢复为未选中并禁用，
随后退出页面，进程仍存活且日志中没有 `FATAL EXCEPTION`。这只证明不支持编码器的失败清理与页面退出不再
闪退。H.264 成功录制、录制过程中快速停止/直接退出，以及 API 21～25 的 EGL 兼容性仍须使用相应配置或
设备完成。

---

# 11. 第四轮复审与整改（2026-09-15，提交 `93c1c3c0f`）

- 审查范围：`d1c87e65e..93c1c3c0f`（`fix: complete media teardown and float window cleanup`，
  34 文件 +1887/-225）
- 审查方式：5 个并行分域代理（MediaCodec 基类 / AAC+OPUS 播放器 / floatview /
  screencapture+ShellUtil / Demo+CHANGELOG）独立审查，主审人逐条回读源码复核并对代理结论
  做二次裁定；按协作约定未在审查环境运行 gradle，全部为静态分析
- 与 §10 的关系：本节先核验 §10.2 的修复声明是否成立，再列出本轮新发现的缺陷与整改

## 11.0 结论摘要

| 域 | 结论 | CRITICAL | HIGH | MEDIUM | LOW |
|----|------|---------|------|--------|-----|
| MediaCodec 基类 | WARN | 0 | 0 | 2（M34、M35） | 0 |
| AAC / OPUS 播放器 | WARN | 0 | 0 | 1（M36） | 2（L28、L29） |
| floatview | WARN | 0 | 1（H12） | 0 | 1（L31） |
| screencapture + ShellUtil | **BLOCK** | 0 | 2（H10、H11） | 2（M38；M37 驳回） | 1（L30） |
| Demo + CHANGELOG | WARN | 0 | 0 | 1（M39） | 1（文档） |

**合入前修复项：H10、H11、H12。**三项均已在本轮整改（§11.4）。

## 11.1 §10.2 声明核验

成立且有 `file:line` 证据的：`notifyCodecFailure` 提升到基类并在同步路径锁外调用；异步 input
补偿保留 EOS flag、`getOutputBuffer()` 抛错时 `finally` 归还 index；"EOS flag + null buffer"不再误判；
同步 3 秒 drain 超时走失败钩子；`releaseAndJoin()` 刻意不设 join 超时；一次性语义与并发 start
测试；AAC `errorCallback` 与四类终态共用屏障；初始化失败清理切 IO 并保留原始取消；OPUS EOS 3 秒
上限与 PCM 48/32 水位；两个 stream player 的 `stopped` 闸；`StreamPlayerStopper` 拒绝自等待；
`Screenshot2H26xStrategy` 专用单线程 dispatcher、job 保存、`releaseAndJoin()`、EGL 销毁前解除
current；encoder detach 与 callback 同锁、投递前复制 flags/PTS；Demo `onError()`、输出流同锁、
`NonCancellable` 释放；Audio Demo 根 scope handler 与 PCM 流 close+interrupt+join；`ShellUtil`
`toIntOrNull()` 跳过表头。

**不成立或有偏差的**：

| 声明 | 实际 | 对应缺陷 |
|------|------|---------|
| "主动停止与取消不触发 `ScreenDataListener.onError()`" | `onInit()` 期间的主动停止经 `check()` 抛 `IllegalStateException`，被当作失败上报 | H11 |
| "异步 callback 异常统一在锁外通知 owner 并隔离" | `MediaCodec.Callback.onError` 分支无隔离，且 `OpusDecoder.onError` 在锁内直接调 `notifyCodecFailure` | M35 |
| "显式 stop 不触发任一回调"（`playAac`/`playOpus` KDoc） | 清理阶段异常时既 throw 给调用方又调 `errorCallback` | M36 |
| §10.1 第 8 条把 `ScreenDataListener.onError()` 归到本提交 | 该默认方法在 `d1c87e65e` 已存在，本提交只改 KDoc | 已在 §10.1 标注 |

## 11.2 缺陷清单

### H10（HIGH）`Screenshot2H26xStrategy` 注册 job 与完成释放屏障之间存在 TOCTOU 竞态

`requestRelease()` 在不持 `lifecycleLock` 的情况下读 `recordingJob`，而 `startRecord()` 在锁内写。
Demo 里两者确实跨线程：`startRecord()` 在主线程，`RecordSingleAppScreenActivity.releaseRecorder()`
的 `releaseAndJoin()` 跑在 `Dispatchers.IO`。lazy job 已创建但尚未注册时若收到释放请求，
`requestRelease()` 看到 `null` → 完成 `releaseCompleted` 并 `recordingDispatcher.close()`；随后主线程
`job.start()` 向已 shutdown 的 executor 派发 → `RejectedExecutionException`，且 `releaseAndJoin()`
的"全部释放"承诺失效。（`recordingJob` 本身已是 `@Volatile`，这是原子性问题而非可见性问题。）

**自审补充**：修复后的代码复审又发现同类残留窗口——`requestRelease()` 只置 `isRecording = false`
而不取消 job，若录制协程随后才执行 `onStart()`，会把 `isRecording` 改回 `true`，循环永不退出，
`releaseCompleted` 永不完成，`releaseAndJoin()` 永久挂起（该竞态在审查基线上就存在）。此外 LAZY
job 若在注册后、`start()` 前被取消，协程体的 `finally` 不会执行，屏障同样无法完成。

### H11（HIGH）初始化期间的主动停止被误报为失败

`onInit()` 之后 `check(!releaseRequested.get())` 抛出的是 `IllegalStateException`，不是
`CancellationException`，落入 `catch (e: Throwable)` → `recordingFailure` 非空 → `finally` 调
`screenDataListener.onError()`。这与本次给 `ScreenDataListener.onError()` 新写的 KDoc
"Intentional cancellation or an explicit release does not invoke this callback" 直接矛盾，
且恰是 §10.4 自陈未做真机验证的"录制过程中快速停止/直接退出"场景。

### H12（HIGH）`FloatViewOwner` 构造期 `check(isAlive)` 把公开 API 变成会崩溃

`FloatView.with(activity).build()` 对 finishing/destroyed Activity 现在抛未捕获的
`IllegalStateException`，`FloatViewManager.create()` 无任何捕获。同一模块的 `show()` 对相同情况是优雅
处理（`remove(true); return`，`addView` 包在 `runCatching`），`build()` 的 KDoc 也没有 `@throws`。
从异步回调（权限返回、网络响应、`postDelayed`）创建悬浮窗是常见模式，对 JitPack 发布的库这是
未文档化的新增崩溃路径。

### MEDIUM

| # | 位置 | 问题 |
|---|------|------|
| M34 | `BaseMediaCodecSynchronous` drain 阶段 | drain 循环内部真实失败已由 `process()` 上报一次，外层超时分支未复查 `codecFailed`，再合成一条 "Timed out after 3000ms" 二次上报；诊断被污染，owner 的 `terminalStarted` CAS 吸收了第二次，无功能破坏 |
| M35 | `BaseMediaCodecAsynchronous.mediaCodecCallback.onError`、`OpusDecoder.onError` | 异步 `onError` 是唯一没有 `runCatchingPreservingCancellation` 隔离的回调；`OpusDecoder.onError` 在锁内直接调 `notifyCodecFailure` → `errorCallback`，违反 `BaseMediaCodec.notifyCodecFailure` KDoc 的"锁外调用"契约。当前 `requestFailure` 只 `launch{}`，靠运气未出事 |
| M36 | `AacFilePlayer` / `OpusFilePlayer` `finishPlayback` | 显式 `stop()` 若清理阶段抛异常，`terminalFailure` 被设为 `cleanupError`，既调 `errorCallback` 又从 `stop()` 抛出；违反 KDoc。仓库内清理路径几乎都吞异常，可达性窄，故由代理的 HIGH 下调为 MEDIUM |
| M37 | `releaseResourcesOnRecordingThread` 释放顺序 | **驳回**。代理称"先 encoder 后 EGL 与官方样例相反"；实际 grafika `TextureMovieEncoder.releaseEncoder()` 与 bigflake `EncodeAndMuxTest.releaseEncoder()` 都是先 `stop()/release()` encoder 再释放 input surface / EGL，与当前代码一致。未改动 |
| M38 | `Screenshot2H26xStrategy.onStop()` vs `ScreenProcessor` KDoc | `onStop()` 现等同 `onRelease()`，但接口 KDoc 写 "After onStop(), you can do onStart() again"；按接口写的调用方会拿到 `IllegalStateException` |
| M39 | `RecordSingleAppScreenActivity.releaseRecorder()` | `NonCancellable` 内 `releaseAndJoin()` 无超时，上游 `releaseHandlerAndJoin()` 的 `join()` 也无超时；线程卡在原生调用时 Activity 无界不可回收，且无日志 |

### LOW

| # | 位置 | 问题 |
|---|------|------|
| L28 | `AacFilePlayer.terminalScope` | 缺 `CoroutineExceptionHandler`，`OpusFilePlayer` 本次加了，防御深度不对称 |
| L29 | `OpusFilePlayer.awaitNaturalCompletion()` | 缺顶层 `catch → requestFailure`，同文件另两个协程都有 |
| L30 | `Screenshot2H26xStrategy.startRecord()` | lazy job 在 `check` 之前创建，二次调用时 job 被静默丢弃且调用线程裸抛 |
| L31 | `FloatViewManager` / `FloatViewImpl` | 主线程检查重复实现 |

## 11.3 主审人对代理结论的裁定

- **驳回 M37**（理由见上表）。
- **驳回"`recordingJob` 未加 `@Volatile`"**：`Screenshot2H26xStrategy.kt:76` 已有 `@Volatile`；H10 保留，
  但定性为 TOCTOU 而非可见性。
- **M36 由 HIGH 下调为 MEDIUM**：`releaseExternalResources()` 只有 `audioTrackPlayer.release()` 未包
  `runCatching`，其余清理路径都吞异常，当前触发条件窄；文档与实现的矛盾是确定的。
- **H12 保留 HIGH**：虽然需要调用方在 Activity 结束后创建悬浮窗，但这是库的公开 API 上未文档化的
  新增崩溃路径，且同模块 `show()` 已示范了优雅路径。

## 11.4 已实施代码修复

### screencapture（H10、H11、M38、L30）

- `startRecord()` 先在 `lifecycleLock` 内以 `recordingStarted` 拒绝二次调用，再创建 lazy job；
  注册 job 与 `releaseCompleted.isCompleted` 判断在同一锁内原子完成，未被接受的 job 直接 `cancel()`。
- 屏障完成（`releaseCompleted.complete()` + `recordingDispatcher.close()`）从协程体 `finally`
  移到注册时挂的 `job.invokeOnCompletion {}`，从而覆盖"注册后、启动前被取消"的 LAZY job。
- `requestRelease()` 在 `lifecycleLock` 内决定"无 job 则自己完成屏障"，否则 `cancel()` 已注册的
  job；录制循环条件改为 `isRecording && !releaseRequested.get()`，`onStart()` 竞态无法再把
  循环拉活。
- `onInit()` 前后各做一次 `ensureNotReleased()`：已请求释放时抛出 `CancellationException` 子类
  `ReleaseRequestedException` 而非 `check()`，走取消分支，不再触发 `onError()`；协程体改调私有
  `startEncoder()`，公开 `onStart()` 的 `check()` 仅用于拦截外部误用。
- `startRecord()` / `onStop()` / `onRelease()` 的 KDoc 明确本策略是一次性的，`onStop` 与
  `onRelease` 等价；接口 `ScreenProcessor` 的通用契约未改。

### floatview（H12、L31）

- `FloatViewOwner` 构造不再 `check()`；新增 `FloatViewOwner.isContextAlive(context)`。
- `FloatViewManager.create()` 对 finishing/destroyed Activity 记 `Log.w` 后直接返回，不创建实例。
- 主线程检查抽为 `checkFloatViewMainThread()`，`FloatViewManager` 与 `FloatViewImpl` 共用。

### audio（M34、M35、M36、L28、L29）

- `BaseMediaCodecSynchronous`：超时分支增加 `!codecFailed.get()`，drain 内部失败只上报一次。
- `BaseMediaCodecAsynchronous.mediaCodecCallback.onError`：子类 `onError` 钩子包在
  `runCatchingPreservingCancellation` 内并在锁内执行；锁外再 `reportCodecFailure(e)`。
  `OpusDecoder` 删除 `onError` 覆写，仅保留 `notifyCodecFailure` 作为 owner 回调。
- `AacFilePlayer` / `OpusFilePlayer.finishPlayback`：`reason == ExplicitStop` 时不调用任一回调，
  清理失败只经 `stop()` 抛给调用方，与 KDoc 一致。
- `AacFilePlayer.terminalScope` 补 `CoroutineExceptionHandler`。
- `OpusFilePlayer.awaitNaturalCompletion()` 拆为外层 `try/catch → requestFailure` 与内层
  `awaitDrainedNaturalEnd()`。

### demo（M39，部分缓解）

- `RecordSingleAppScreenActivity.awaitRecorderRelease()`：`releaseAndJoin()` 以
  `withTimeoutOrNull(10 s)` 包裹，超时记 `LogContext.log.e` 后仍关闭输出流。
- **未闭环**：超时只结束等待，无法中断阻塞中的原生调用（协程取消是协作式的）。录制线程若卡在
  `MediaCodec.stop()`、EGL 释放或 `HandlerThread.join()`，仍会持续运行，并通过
  `builder.screenDataListener`（demo 中是 Activity 的匿名内部类）继续可达该 Activity。
- 已一并收窄引用：`Screenshot2H26xStrategy` 的录制协程改为弱引用持有 Activity，并在协程结束时
  清空 `recordingJob`，避免已完成的 lazy 协程 continuation 长期钉住捕获对象。彻底解除需让
  strategy 能释放 `screenDataListener`，属接口变更，另行评估。

## 11.5 新增回归测试

| 测试 | 证伪对象 |
|------|---------|
| `BaseMediaCodecSynchronousTest.failure inside the EOS drain loop is reported exactly once` | M34 |
| `BaseMediaCodecAsynchronousTest.error callback notifies owner once outside the codec lock` | M35（另起线程探测 `withCodecOperationLock` 可立即获取） |
| `BaseMediaCodecAsynchronousTest.error hook failure is isolated and still notifies owner` | M35 |
| `FloatViewLifecycleTest.build on a finishing activity is skipped without throwing` | H12 |
| `FloatViewLifecycleTest.window already removed by the framework is treated as detached` | §10 遗留：`IllegalArgumentException` 分支无用例 |
| `ShellUtilTest` 新增 4 条：前导空白、非数字行跳过后继续解析、列数不足、空/空白输入 | §10 遗留覆盖缺口 |

## 11.6 仍未覆盖的验证项

- `AacFilePlayer` / `OpusFilePlayer` 的终态屏障、`errorCallback` 语义、codec EOS 3 秒超时、PCM 水位
  与"队列满即失败"仍无单测（依赖 `MediaExtractor` / `AudioTrack` / 真实 codec，JVM mock 成本高、
  可信度低）。M36 的修复目前只靠人工阅读验证。
- `Screenshot2H26xStrategy` 的 H10 / H11 修复无自动化测试（依赖 EGL 与真实 encoder），需真机验证
  "录制中快速停止"、"初始化期间退出页面"两条路径，确认不再收到 `onError()` 且进程不崩溃。
- 本轮所有改动**未在审查环境编译**，`:audio` / `:floatview` / `:lib-common-android` /
  `:screencapture` / `:demo` 的 `testDebugUnitTest`、`detekt`、`ktlintCheck` 由用户本地执行。

# 12. 第五至第七轮复审与真机整改（2026-09-15 ~ 2026-09-16）

- 审查范围：`e9983b248..6d2088879`
- 审查方式：Codex 逐轮静态审查 + 主审人逐条回读源码复核；§12.4 三项来自用户真机实测
- 本轮首次出现**真机实测发现的缺陷**，其中一项是本轮自引入的回归

## 12.0 结论摘要

| 轮次 | 来源 | 提交 | 发现 | 结论 |
|------|------|------|------|------|
| 第五轮 | Codex 静态审查 | `e3a3911d9` | 1 × P1、2 × P2 | 全部成立并修复 |
| 第六轮 | Codex 静态审查 | `4bf5f4cf3` | 2 × P2 | 全部成立并修复 |
| 第七轮 | Codex 静态审查 | `f86b2682a` | 1 × P2 | 成立并修复 |
| 真机实测 | 用户设备 | `ba3424a98`、`a1d04f27f`、`878ed3b4a` | 3 个缺陷 | 全部修复 |

第六、七轮的全部问题均由上一轮的修复引入，`878ed3b4a` 修复的是 `ba3424a98` 引入的回归。
静态审查在这条链上持续有效，但连续三轮都要等下一轮才暴露，说明
`Screenshot2H26xStrategy` 的并发生命周期已接近人工逐条审查的上限。

## 12.1 第五轮：OPUS EOS 计时与 Screenshot 释放（`e3a3911d9`）

### P1 OPUS 长文件被提前判失败

`launchPlaybackJobs()` 与喂帧协程同时启动 `awaitNaturalCompletion()`，而
`awaitDrainedNaturalEnd()` 的第一条语句就是对 `codecEos` 起 `CODEC_EOS_TIMEOUT_MS`（3 秒）超时。
喂帧受 PCM 队列水位与逐帧 `delay` 约束，速率接近实时，因此**音频长度超过 3 秒的 OPUS 文件必然
在正常解码途中被 `requestFailure(TimeoutException)` 判失败并清理**。

修复：新增 `inputEosSubmitted: CompletableDeferred<Unit>`，`produceDecoderInput()` 在
`signalEndOfStreamWithBackpressure()` 返回后完成它；`awaitDrainedNaturalEnd()` 先 `await()` 再起
超时。终态清理会取消 `playbackScopeJob`，喂帧提前失败时该 `await()` 不会悬挂。
`AacFilePlayer` 无同构机制，不受影响。CHANGELOG 中"输入 EOS 后等待输出 EOS 最多 3 秒"的描述
此前与实现不符，已同步修正。

### P2 经公开接口初始化后释放会泄漏

`onInit()` 创建 encoder、EGL display/context/surface、`TextureRenderer` 与 HandlerThread。
若经公开 `ScreenProcessor` 接口 `onInit()` 后直接 `onRelease()`，`recordingJob` 为 null，
`requestRelease()` 只完成屏障并关闭 dispatcher，`releaseResourcesOnRecordingThread()` 不会执行，
上述资源全部泄漏。该路径是接口的常规用法，`MediaProjectionService` 即 `build().apply { onInit(); onStart() }`。

修复：该分支就地释放；函数更名为 `releaseOwnedResources()`（原名已不准确）；释放流程对 null 与
未初始化状态全程防护，因此 `onInit()` 中途抛异常的部分初始化场景一并覆盖。

### P2 超时不解除 Activity 引用

`RecordSingleAppScreenActivity.awaitRecorderRelease()` 的 10 秒超时只结束等待。协程取消是协作式的，
卡在 `MediaCodec.stop()`、EGL 释放或 `HandlerThread.join()` 的线程不会被中断，仍能通过
`builder.screenDataListener`（demo 中是 Activity 的匿名内部类）到达 Activity。

已收窄引用：录制协程改为弱引用持有 Activity，并在其被回收时退出循环；`job.invokeOnCompletion`
内清空 `recordingJob`，避免已完成的 lazy 协程 continuation 继续钉住捕获对象。
**未闭环**：彻底解除需让 strategy 能释放 `screenDataListener`，属接口变更，另行评估。
§11.4 的 M39 状态已改为"部分缓解"。

## 12.2 第六轮：释放屏障与 EGL 线程归属（`4bf5f4cf3`）

### P2 释放屏障早于实际清理完成

`requestRelease()` 用 `releaseCompleted.complete(Unit)` 的返回值同时表达"取得清理所有权"和
"清理已完成"，而实际清理在其之后执行。单线程调用序列看不出问题，并发下线程 A 正在清理时，
线程 B 的 `releaseAndJoin()` 立即返回，此时 codec、EGL 与回调线程仍在运行。

修复：拆成两个状态。新增 `inlineReleaseClaimed`（§12.3 后更名 `teardownClaimed`），由
`lifecycleLock` 保护，仅表示所有权；`releaseCompleted` 移到清理结束后的 `finally` 完成。
连带修正：`startRecord()` 的准入判断原先只看 `releaseCompleted.isCompleted`，屏障延后完成后
该判断失效，已扩展为同时检查 claim 标志，两者在同一把锁下读写。
协程路径本就在任务体 `finally` 跑完清理后才由 `invokeOnCompletion` 开屏障，不受影响。

### P2 直接释放路径未保证 EGL 线程归属

原注释断言"调用方线程即初始化线程"，这是假设而非保证。主线程 `onInit()`、IO 协程
`releaseAndJoin()` 时，`eglMakeCurrent` 解绑的是 IO 线程的绑定，主线程上的 context 仍 current，
`eglDestroyContext` 只标记删除，实际销毁被推迟。

修复：`onInit()` 记录 EGL owner 线程及其 `Handler`；释放时若不在 owner 线程则 post 回去，
屏障由 post 过去的那次执行来开，`releaseAndJoin()` 仍能等到真正结束；owner 无 Looper 或
Looper 已退出时就地释放，但记 error 说明可能不完整，不静默假装成功。

## 12.3 第七轮：初始化纳入生命周期协议（`f86b2682a`）

### P2 初始化线程信息未原子发布

owner 线程与 `Handler` 分两个 `@Volatile` 字段发布，存在"已写入 thread、尚未写入 handler"的窗口。
并发释放在该窗口读到 handler 为 null，走降级清理，此时尚无资源可释放，于是立即开屏障；
随后 `onInit()` 创建的全部资源被孤立，后续释放因屏障已完成而跳过。

复核发现根因比发布窗口更宽：**`onInit()` 完全不参与生命周期协议**。释放请求完全早于 `onInit()`
到达时，`eglOwner` 为 null，同样什么都没释放就开屏障，这条路径不涉及任何 volatile 读写。

修复：

- 新增不可变的 `EglOwner(thread, handler)`，在 `lifecycleLock` 内、任何资源创建之前一次发布，
  不存在只观察到一半的状态。
- `onInit()` 拆为 `beginInit()` / `initResources()` / `endInit()`。`beginInit()` 在锁内检查释放
  是否已请求或已被接管，是则抛 `ReleaseRequestedException`。该异常继承 `CancellationException`，
  录制协程按取消处理，不会误报 `onError()`。
- `requestRelease()` 在锁内发现 `initInProgress` 为真时只标记移交，由 `endInit()` 在初始化线程执行。
  清理因此既看到完整资源集合，又始终跑在持有 EGL context 的线程上。

**行为变更**：对已请求释放的实例调用 `onInit()` 会抛 `CancellationException`，不再静默完成
初始化并把资源孤立在无人释放的状态。

> **勘误（§13 复审修正）**：本节原先还写有"`onInit()` 的 `finally` 保证中途失败也会释放已创建
> 部分"，该说法对 `f86b2682a` 不成立。当时 `finally` 里只有 `if (endInit()) completeInlineRelease(...)`，
> 而 `endInit()` 返回的是 `teardownDeferredToInit`——仅在并发释放被移交时为真。没有并发释放的
> 普通初始化失败什么都不释放。该缺陷在 §13 中以 B3 记录并修复，原句已删除。
>
> **已被 §13 取代**：`EglOwner(thread, handler)` 类已删除，EGL 属主改为录制器自己的单线程
> executor；`onInit()` 对已释放实例改抛 `IllegalStateException` 而非 `CancellationException`。

## 12.4 真机实测发现

### Bug 1 录制按钮停止后永久禁用（`ba3424a98`）

`Screenshot2H26xStrategy` 是一次性策略，但 demo 只在 `onCreate()` 构建一次录制器和一次输出流。
`releaseRecorder()` 置位 `cleanupStarted` 并禁用开关，两者都没有恢复路径，因此走过一次清理后
页面报废。手动关闭开关，以及 `onError()` 把 `isChecked` 置 false 触发同一分支，都会命中。
该行为由 §11 的 `93c1c3c0f` 引入，此前停止录制走 `onRelease()`，按钮不会被禁用。

修复：清理完成后武装下一次会话——重建一次性录制器、复位 latch、恢复按钮可用；
`onDestroy()` 传 `restartable = false`，页面销毁时不重建。

### Bug 2 Falcon 绘制未 attach 的窗口抛 NPE（`a1d04f27f`）

`viewRootData()` 只过滤 null 与 `!isShown`，而 `isShown` 只看可见性和是否有 parent，
不保证已 attach。首次 traversal 之前或已 detach 的 root 其 `AttachInfo` 为 null，
`View.onDrawScrollIndicators()` 直接读 `mAttachInfo.mTmpInvalRect` 抛 `NullPointerException`。
`takeScreenshotBitmap()` 捕获后返回 null，**整帧丢失**。触发条件是录制期间有窗口正在增删。
该缺陷为既有问题，`Falcon.kt` 上次修改是 `c5218ffef`。

修复三层：采集时跳过未 attach 的 root；绘制前在主线程再确认一次，因为采集在录制线程、
绘制在主线程，中间状态会变；单个窗口绘制失败只跳过该窗口并记日志，不再放弃整帧。

> **勘误（§13 复审修正）**：本节原先在第一层后附有"顺带避免其失效边界影响位图尺寸计算"，
> 这条收益没有依据，且方向相反——跨线程读到 `mAttachInfo` 已置空而 `mView` 尚未置空的中间态时，
> 该过滤会剔除 Activity 根窗口、只剩悬浮窗，位图缩成悬浮窗尺寸后被 `encodeImages()` 拉伸铺满整帧，
> 比原来的丢一帧更糟。该判断在 §13 中以 M7 记录。
>
> **已被 §13 取代**：采集阶段的 attach 过滤已整体删除。A3 把窗口列表采集、位图尺寸计算与绘制
> 合并到主线程的同一个 turn，过滤因此不再需要，"三层"现为：整帧采集移入主线程 / 绘制前 attach
> 复查 / 单窗口失败只跳该窗口。

### Bug 3 停止录制后文件被清零（`878ed3b4a`，本轮自引入回归）

Bug 1 的修复在 `armNextRecording()` 中重新打开调试输出流，而 `FileOutputStream` 打开即截断。
顺序为：录制写入 → 停止时清理 flush 并 close（文件此时有内容）→ 武装下一次会话重开同一文件
→ **清成 0 字节**。

修复：输出流改为在开始录制时打开，武装下一次会话只重建录制器和恢复按钮，完全不碰文件。

> **已被 §13 取代**：文件名当时是固定的、每次新录制覆盖上一次；§13 的 A2 改为
> `screen-<时间戳>.h265`，每次会话一个新文件，避免释放超时后仍在运行的旧录制器写进新文件。

## 12.5 其他提交

- `c49d9cf0b` — 用户本地补齐第五轮修复的 ktlint 与 detekt 问题。
- `6d2088879` — 用户提交。Falcon 的 `null View stored as root...` 日志由 ERROR 降为 INFO；
  demo 的 `withTimeoutOrNull(RELEASE_TIMEOUT_MS)` 改为 `RELEASE_TIMEOUT_MS.milliseconds`。
  后者是可读性改进而非缺陷修复：`Long` 重载本就按毫秒解释，超时时长前后一致为 10 秒。

## 12.6 行为变更汇总

| 位置 | 变更 |
|------|------|
| `OpusFilePlayer` | codec EOS 超时从输入 EOS 提交后开始计时，不再从播放启动算起 |
| `Screenshot2H26xStrategy.onInit()` | 对已请求释放的实例抛 `CancellationException` |
| `Screenshot2H26xStrategy` 直接释放路径 | 真正释放资源，并调度回 EGL 初始化线程 |
| `Screenshot2H26xStrategy` 释放屏障 | 清理真正结束后才完成，并发 `releaseAndJoin()` 不再提前返回 |
| `Falcon` | 跳过未 attach 的 root；单窗口绘制失败不再放弃整帧 |
| demo 录屏页 | 停止后可再次录制；调试文件在开始录制时才打开 |

## 12.7 验证状态与仍未覆盖项

**已完成**（用户本地，`--rerun-tasks`）：65 条单测通过；`:audio`、`:lib-common-android`、
`:screencapture`、`:demo` 的 `ktlintCheck` 与 `detekt` 通过；`git diff --check` 通过。
审查环境全程未编译。

**无自动化测试**：第五至第七轮的三条竞态均依赖真实 EGL 与 encoder，JVM 侧无法构造。
§12.4 的三个缺陷同样只有真机路径。

**待真机验证**：

- OPUS 播放长于 3 秒的文件，确认不再被提前判失败。
- 只调用 `onInit()` 后直接 `onRelease()`，确认 encoder、EGL 与回调线程真正释放。
- 释放请求早于 `onInit()`、以及穿插于 `onInit()` 执行期间两条时序。
- `onInit()` 与 `releaseAndJoin()` 分处不同线程时，清理落在初始化线程上。
- demo 录屏页"录制、停止、再录制"可循环，且停止后文件非空可播放。

**已知未处理**：`Falcon.getRootViews()` 分两次反射读取 `mRoots` 与 `mParams`，未持有
`WindowManagerGlobal` 的锁。对话框等窗口增删时这两个列表在锁内一起变更，跨线程分两次读可能
得到不一致快照，随后按 root 下标取 `params[i]` 会错配 LayoutParams（影响 dim 与偏移）或下标越界。
越界会被 `takeScreenshotBitmap()` 捕获，表现为偶发丢帧。

> **已在 §13 处理**：该项在独立复审中提级为 A3，并已修复——整次采集移入主线程的同一个 turn，
> 两个列表的快照不再可能错位。

# 13. 第八轮：独立复审与整改（2026-09-16）

## 13.0 背景与产出

审查 AI 对 §12 的全部改动做了一次不依赖既往结论的独立复审，产出
`00-documents/2026-09-16-screenshot-recorder-independent-review_cc.md`，共 6 条 HIGH、
8 条 MEDIUM、3 条项目规则违反，另指出 §12 中两处与源码矛盾的声明。

本节记录这 17 项的整改、对整改本身再做一轮复审时新发现的 7 项（§13.4），以及对上述全部整改
的第二次复审与修正（§13.8）。

**影响面**：`Screenshot2H26xStrategy`、`Falcon`、`ScreenProcessor`、`OpusFilePlayer`、
demo 录屏页。其中 B2 与 §12.4 Bug 3 属于 §12 自身修复引入的回归。

## 13.1 HIGH 级缺陷

### A1 配置变更自动开始录制并损坏输出文件

`RecordSingleAppScreenActivity` 的 `toggleBtn` 有 id 且未禁用状态保存。旋转屏幕或切换语言触发
`recreate()` 时，`CompoundButton` 的 checked 状态被恢复，`setOnCheckedChangeListener` 在恢复过程中
被触发，启动一次用户从未请求的录制；而上一个实例此刻仍在 flush 同一个固定名输出文件。
manifest 中该 Activity 也没有 `android:configChanges`。

修复：`binding.toggleBtn.isSaveEnabled = false`。`View.dispatchSaveInstanceState()` 检查
`SAVE_DISABLED_MASK`，置位后 checked 状态不再参与保存/恢复，监听器不会在重建时被触发。

### A2 释放超时后仍重新武装，被放弃的 recorder 污染下一次录制

`awaitRecorderRelease()` 原先返回 `Unit`，超时只记一条日志；`releaseRecorder()` 无条件调用
`armNextRecording()`。超时意味着旧 recorder 仍持有 encoder 并可能继续出帧，而它与新会话共用
同一个 `screenDataListener` 实例，于是旧帧会写进新会话的输出流。

修复三处：

- `awaitRecorderRelease()` 返回 `Boolean`，只有真正释放完成才武装下一次会话；否则 toast
  提示并让开关保持禁用。
- 引入 `activeSession: AtomicInteger`，`screenDataListenerFor(session)` 为每个 recorder 生成
  带会话号的监听器，`onDataUpdate` 与 `onError` 都先比对会话号再处理。释放超时时
  `activeSession.incrementAndGet()` 直接让旧会话退休。
- 输出文件名改为 `screen-<时间戳>`，即使旧 recorder 还在写，也写不进新文件。

### A3 `Falcon` 的 `mRoots` / `mParams` 双快照错位

`getRootViews()` 分两次反射读取这两个列表，而 `WindowManagerGlobal` 在自己的锁内同时变更它们。
跨线程分两次读可能拿到不一致的快照，随后 `params[i]` 错配 LayoutParams 或下标越界；
`toTypedArray()` 本身也有 `ConcurrentModificationException` 风险。

修复：新增 `captureOnMainThread()`，把窗口列表采集、位图尺寸计算和绘制合并进主线程的同一个
turn；`captureFromOtherThread()` 只负责把整件事投递到主线程并有界等待。两个列表只在主线程读取，
与它们的变更方同线程，错位窗口消失。位图也改由主线程那一 turn 创建，等待超时的调用方不会
拿到一张主线程可能还在往里画的位图。

### B1 `onInit()` 可重入，双初始化泄漏整套原生资源

`beginInit()` 只检查释放状态，没有"是否已初始化"的闩。外部 `onInit()` 叠加 `startRecord()` 的
惰性初始化会创建两套 encoder / EGL / Surface / 回调线程，第一套被字段覆盖后无人可释放。

修复：新增 `initStarted` 闩与 `InitOutcome` 枚举，第二次初始化返回 `ALREADY_INITIALIZED`。
`allocateResourcesOnce()` 在 `lifecycleLock` 内先答复已定型的情况，避免把调用方派发到 EGL
线程上排队。

### B2 inline teardown 在主线程执行无界阻塞拆除（§12.2/§12.3 修复引入）

原 `dispatchInlineRelease()` 把清理 post 到 `eglOwner.handler`，而 `onInit()` 的文档线程就是主线程，
于是 `MediaCodec.stop()` 与无界的 `screenshotThread.join()` 落在主线程上。

修复：EGL 属主不再是"调用 `onInit()` 的那个线程"，而是录制器自己的单线程 executor
（`screenshot-h26x-egl`）。`EglOwner` 类删除，改为 `eglThread: AtomicReference<Thread?>` 在
ThreadFactory 中发布；所有 EGL 调用与拆除都经 `runOnEglThread()` / `recordingExecutor.execute()`
路由到该线程。`join()` 加 `CALLBACK_THREAD_JOIN_TIMEOUT_MS = 2s` 上限。

### B3 `onInit()` 初始化失败不释放已创建的资源

§12.3 曾声称 `finally` 覆盖了中途失败，实际不成立（见该节勘误）。`build().apply { onInit() }`
这类写法下调用方连引用都还没拿到，失败时没有任何人能释放已创建的部分。

修复：`allocateOnEglThread()` 新增 `catch (t: Throwable)`，先登记 `initFailure`，再
`releaseOwnedResources(stopEncoder = false)`，释放过程中的次生异常用 `addSuppressed` 附加到原异常
上后重新抛出。

> **§13.8 补充**：仅释放 encoder / EGL / 回调线程还不够，失败路径还必须关闭 EGL 执行器并打开
> 释放屏障，否则空闲的执行器线程会把整个录制器钉在内存里。见 §13.8 B4。

## 13.2 MEDIUM 级缺陷

| 编号 | 问题 | 修复 |
|------|------|------|
| M1 | input EOS 之前没有看门狗：生产者"活着但卡住"时 `inputEosSubmitted.await()` 无限期挂起，MediaCodec / AudioTrack / `RandomAccessFile` 全部不释放，且无日志 | 新增 `awaitInputEosWithStallDetection()`。按 `queuedPcmCount + consumedPcmCount` 判断进展而非按时长，长文件正常解码可以等任意久，连续 5 秒无进展才 `requestFailure`。不能退回时长型超时，那会重新引入 §12.1 的 P1 |
| M2 | MediaCodec 回调无 try/catch，codec 进入 Error 态时 `getOutputBuffer()` 抛出会逃逸到回调线程使进程崩溃；`releaseOutputBuffer` 不在 `finally`，中途抛异常泄漏 buffer index | 回调体拆出 `deliverOutput()`，外层 try/catch 记录失败并停录，`finally` 中 `releaseOutputBuffer`。顺带把录制器内部识别 `BUFFER_FLAG_CODEC_CONFIG` 的 `when (flags)` 精确匹配改为位测试——`flags` 是位掩码，原写法在复合标志下会漏记 `vpsSpsPpsBytes`。传给 listener 的 `flags` 一直是原样透传，对外无变化 |
| M3 | `latch.await()` 无超时，录制协程卡在等主线程绘制时 `releaseAndJoin()` 的超时无法中断它 | `latch.await(MAIN_THREAD_CAPTURE_TIMEOUT_MS = 1s)`。超时经 `takeScreenshotBitmap()` 的 catch 返回 null，录制循环跳帧继续，不中断录制 |
| M4 | 自我 join 死锁：在 `onDataUpdate`（运行于 `scr-rec-send`）内调用 `onStop()` 时，清理会在该线程上 join 它自己 | 双重覆盖——拆除统一路由到 EGL 线程，不再落在 `scr-rec-send`；`releaseHandlerAndJoin()` 另加 `Thread.currentThread() === screenshotThread` 判断，命中时 `quit()` 后直接返回 |
| M5 | `handler.post` 成功但目标 Looper 随后 quit，`completeInlineRelease` 永不执行，屏障永不完成 | 改用 `recordingExecutor.execute()`。`ExecutorCoroutineDispatcher.close()` 走 `shutdown()`，已入队任务仍会执行；执行器已关闭时抛 `RejectedExecutionException`，降级分支就地释放并跳过 encoder drain/stop 这两段长等待 |
| M6 | 停止 encoder 前没有 `signalEndOfInputStream()`，pipeline 中未输出的帧被丢弃，下游收不到 `BUFFER_FLAG_END_OF_STREAM` | 新增 `drainEncoder()`：detach 之前 signal，然后等 `encoderEos` 闩最多 500ms。顺序正确——EOS buffer 经 `screenshotHandler.post` 投递后才 `quitSafely()`，监听器能收到结束标志 |
| M7 | 采集阶段的 attach 过滤只有坏处（§12.4 Bug 2 的收益声明无依据） | 该过滤整体删除，由 A3 的主线程合并采集取代 |
| M8 | init-only 路径上 codec 异步失败被静默丢弃：`onError` 写入 `recordingFailure`，但唯一读取点在录制协程的 `finally` 内 | `mediaCodecCallback.onError` 在没有录制协程时直接 `requestRelease()`；`completeInlineRelease()` 末尾调用 `reportFailureIfAny()`，且放在屏障之后，避免回调方再次进入本录制器时与 `releaseAndJoin()` 死锁 |

## 13.3 项目规则违反

- `Falcon.kt` 的 `getFieldValue("mGlobal", ...)!!` → `checkNotNull` 并附带诊断消息（非 SDK 接口
  限制时能说清原因）。同文件另外两处 `!!` 一并清除：`writeBitmap` 的参数改为非空类型；
  `findField` 改为把 null 检查放进循环条件，不再靠 `NullPointerException` 做控制流。
- `Screenshot2H26xStrategy` 录制协程 `catch (e: CancellationException)` 补 `throw e`。
- `ScreenProcessor` 接口 KDoc 自相矛盾（`onStop()` 既说可再 `onStart()`，又说一次性策略等价于
  `onRelease()`）→ 重写为"默认契约 + 实现可收紧"，并说明一次性策略的具体行为。
- `onInit()` 原先抛 `CancellationException`（非挂起的公开方法用取消异常做信号是危险的类型选择）
  → 改抛 `IllegalStateException`；`ReleaseRequestedException` 退回为录制协程的内部信号。

## 13.4 对整改的复审修正

整改完成后又做了一轮复审，7 项已全部处理：

| 编号 | 问题 | 处理 |
|------|------|------|
| N1 | `inputEosSubmitted.completeExceptionally()` 形同死代码——等待循环用 `isCompleted` 判停，异常完成同样让它为真，生产者的失败从不抛出，反而落进 3 秒 codec EOS 等待后报一条张冠李戴的超时 | 循环后补 `inputEosSubmitted.await()`。循环已保证完成，因此它要么立即返回要么重新抛出。不用 `getCompletionExceptionOrNull()`：那是 `@ExperimentalCoroutinesApi` |
| N2 | demo 的 `onError` 未做会话门控，而 `completeInlineRelease()` 刻意把 `reportFailureIfAny()` 放在屏障之后，退休 recorder 的报错可能在下一会话武装之后到达，把健康录制关掉 | `onError` 加与 `onDataUpdate` 相同的会话号比对 |
| N3 | `EglOwner` 类删除后其 KDoc 遗留，与类注释连成两个相邻 KDoc 块，描述一个不存在的类 | 删除 |
| N4 | `runOnEglThread` 的 `task.get()` 无界且执行器 FIFO：`onInit()` 若输掉与 `startRecord()` 的竞态，会排在录制循环之后，把调用线程阻塞整场录制 | `task.get(EGL_DISPATCH_TIMEOUT_MS = 5s)` 兜底；另在 `onInit()` 入口加 `check(!recordingStarted)`，把"录制已启动后再调 `onInit()`"这条现实路径变成立即抛异常，超时退化为纯防御 |
| N5 | 初始化失败后 `initStarted` 仍为 true，后续 `startRecord()` 得到 `ALREADY_INITIALIZED`，最终在 `checkNotNull(h26xEncoder)` 报"onInit() must run before starting the encoder"，与事实相反 | 新增 `initFailure` 字段与 `InitOutcome.FAILED`。`onInit()` 与录制协程都改抛带原始 cause 的 `IllegalStateException` |
| N6 | `Falcon` 采集超时后主线程仍会产出一张全屏位图，无人 recycle | 超时分支向同一 looper 追加 `captured.getAndSet(null)?.recycle()`，排在采集 runnable 之后执行 |
| N7 | `Falcon.kt` `writeBitmap` 的 `bitmap!!` | 见 §13.3 |

## 13.5 行为变更汇总

| 位置 | 变更 |
|------|------|
| `ScreenProcessor.onInit()` | 契约放宽为"除非实现另行说明，否则在主线程调用"；一次性策略明确拒绝二次初始化和对已释放实例的初始化 |
| `Screenshot2H26xStrategy.onInit()` | 已释放 / 已录制 / 上次初始化失败三种情况改抛 `IllegalStateException`（原为 `CancellationException` 或无检查）；初始化在录制器自己的 EGL 线程上执行，调用方阻塞至完成，最长 5 秒 |
| `Screenshot2H26xStrategy` 拆除 | 停止前先 `signalEndOfInputStream()` 并最多等 500ms，监听器现在能收到 `BUFFER_FLAG_END_OF_STREAM`；回调线程 join 上限 2 秒 |
| `Screenshot2H26xStrategy.startRecord()` | 对 `onInit()` 已失败的实例改为立即抛带原始 cause 的 `IllegalStateException`，不再进入录制协程（§13.8 B4） |
| `Falcon` 截图 | 非主线程调用改为整次投递到主线程并最多等 1 秒；超时返回 null（表现为丢一帧），不再无限期阻塞 |
| demo 录屏页 | 输出文件名改为 `screen-<时间戳>`；释放超时后不再武装下一次会话，改为提示并保持开关禁用；旋转 / 切换语言不再自动开始录制 |

## 13.6 §12 勘误索引

- §12.3 —— "`onInit()` 的 `finally` 保证中途失败也会释放已创建部分"：原句错误，已删除，对应缺陷见 B3。
- §12.3 —— `EglOwner`、`onInit()` 抛 `CancellationException`：已被本节取代。
- §12.4 Bug 2 —— "顺带避免其失效边界影响位图尺寸计算"：收益无依据且方向相反，已删除，判断见 M7。
- §12.4 Bug 3 —— "文件名固定，每次新录制覆盖上一次"：已被 A2 取代。
- §12.7 "已知未处理"的 `mRoots`/`mParams` 双快照：已提级为 A3 并修复。

## 13.7 验证状态与待真机验证

**审查环境全程未编译**，本节所有结论来自源码静态核对。已人工核对：改动文件行长均 ≤100、
大括号配对、新增 import 均有使用、`Looper` import 随 `EglOwner` 一并移除且无残留引用、
两处 `when (InitOutcome)` 分支穷尽（`onInit()` 与录制协程）、触达文件中已无 `!!`。

**需用户本地执行**：`./gradlew staticCheck --rerun-tasks`。`runOnEglThread` 的
`catch (e: ExecutionException) { throw e.cause ?: e }` 是否触发 `SwallowedException`，
已在 §13.8 从规则实现层面确认不会。

**待真机验证**：

- OPUS：播放长于 3 秒的文件不被提前判失败；人为制造 `AudioTrack.write()` 阻塞，确认 5 秒停滞
  检测生效且日志给出的是停滞原因而非 codec EOS 超时。
- 录屏停止后，输出文件尾部确实带 `BUFFER_FLAG_END_OF_STREAM`（M6 的实际效果）。
- 旋转屏幕与切换系统语言，确认不会自动开始录制、上一段录制文件完整可播。
- "录制、停止、再录制"可循环，每次生成独立的 `screen-<时间戳>` 文件且非空可播放。
- 只调用 `onInit()` 后直接 `onRelease()`；以及释放请求早于 / 穿插于 `onInit()` 的两条时序。
- 录制期间反复弹出并关闭对话框，确认 A3 之后不再出现 `params[i]` 错配或偶发丢帧。
- 主线程人为制造 >1 秒卡顿，确认 Falcon 只丢帧、录制继续，且不出现位图堆积。

## 13.8 第二次复审修正（2026-09-16）

对 §13.1–§13.4 的整改再做一轮复审。§13.4 的 7 项逐条对照源码均成立：N1 的生产者与等待者同属
`playbackScopeJob`，循环退出即已完成，`await()` 只会立即返回或重抛；N4 超时后 `cancel(false)` 的
`FutureTask` 即使之后被执行器取到也不会运行；N6 的回收 runnable 与采集 runnable 同队列 FIFO，
`getAndSet(null)` 保证成功与超时两条路径不会都拿到位图。

§13.7 担心的 detekt `SwallowedException` 已从本地缓存的 detekt 1.23.8 规则实现确认：判定条件是
throw 表达式内对异常的**全部**引用都是 `e.xxx` 形式的成员访问；`throw e.cause ?: e` 中 `e` 本身
出现，不会命中。

新发现 8 项，7 项已修，1 项明确未处理。

### B4（HIGH，仓库内不可达，由 §13.1 B2 的修复引入）`onInit()` 失败后 EGL 执行器线程永不关闭，且钉住整个录制器

`recordingExecutor` 的 ThreadFactory lambda 引用了属性 `eglThread`，Kotlin 因此捕获了 `this`。
空闲 worker 线程持有 `ThreadPoolExecutor`，后者持有 factory，factory 持有录制器，录制器持有
listener，demo 里 listener 持有 Activity。`Executors.newSingleThreadExecutor` 返回的
`FinalizableDelegatedExecutorService` 本可在录制器被 GC 后靠终结器 `shutdown()` 兜底，但录制器
被线程钉住，永远不会被 GC。HEAD（`08b122e6f`）的 factory 不引用任何属性，没有这条链。

B3 的修复只释放了 encoder / EGL / 回调线程，没有关闭执行器，也没打开 `releaseCompleted` 屏障。
`build().apply { onInit() }` 抛异常后调用方没有引用可以再调 `onRelease()`——这正是 B3 要覆盖的
场景，仓库内 `MediaProjectionService` 对另一个策略就是这样写的。触发条件是初始化本身抛异常，
例如消费者选 H265 而设备没有 HEVC 编码器。`startRecord()` 路径不受影响：协程完成时会关闭执行器。

修复三处：

- factory 改为 `eglThread.let { holder -> Executors.newSingleThreadExecutor { ... holder.set(it) } }`，
  只捕获 `AtomicReference`，恢复终结器兜底。
- `endInit()` 增加 `failed` 参数：初始化失败且没有录制协程、拆除未被认领、屏障未打开时，由
  初始化线程认领拆除并执行 `completeInlineRelease()`，关闭执行器、打开屏障。判定条件与
  `requestRelease()` 的认领条件一致，因此与 `startRecord()` 竞态时已注册的协程仍然拥有拆除权，
  并通过自己的 `InitOutcome.FAILED` 分支上报。
- `allocateResourcesOnce()` 与 `beginInit()` 把 `FAILED` 判断提到 `RELEASED` 之前，避免失败实例
  被报成"已释放"而丢掉 cause；`startRecord()` 入口同步检查 `initFailure`，直接抛带 cause 的
  `IllegalStateException`，不再让一个注定被拒绝的协程去报"released before it could start"。

### B5（HIGH，既有缺陷，仓库内不可达）注册过的录制协程未运行就被取消时，没有人释放 `onInit()` 已分配的资源

拆除归属的前提是"已注册录制协程则由它的 `finally` 负责拆除"。这个前提对**协程体从未进入**的
情况不成立：

1. `onInit()` 成功，encoder / EGL / 输入 Surface / `scr-rec-send` 线程均已存在，尚无协程。
2. `startRecord()` 在锁内注册 `recordingJob = job`，随后 `job.start()`；首次派发进入 EGL 执行器队列。
3. 派发执行前（或 `onInit()` 仍在 EGL 线程上跑、协程排在它后面时）`onStop()` 到达：
   `requestRelease()` 看到 `job != null`，**因此不认领拆除**，只调 `job.cancel()`。
4. LAZY 协程被取消后不再进入 `try`，`finally { releaseOwnedResources(...) }` 从未执行。
5. `invokeOnCompletion` 完成屏障并关闭 dispatcher，`releaseAndJoin()` 报告"已释放"，而 encoder、
   EGL 对象和回调线程全部泄漏；EGL 线程已退出，再没有任何线程能 un-current 那个 context。

注册处原有注释称"在此完成屏障也覆盖了 LAZY 协程未运行就被取消"——它覆盖的只是**屏障**，
不包括**释放**，这正是缺口。仓库内不可达：demo 只调 `startRecord()` 不调 `onInit()`，协程未运行
即无资源可泄漏（`initStarted` 为 false）。影响使用 `onInit()` + `startRecord()` 文档协议的库消费者。

修复：协程体首句置 `bodyEntered`（`AtomicBoolean`）；`invokeOnCompletion` 在锁内判断
`!bodyEntered.get() && initStarted && !teardownClaimed`，命中则认领拆除并走
`dispatchInlineRelease()`，由 `completeInlineRelease()` 在 EGL 线程释放资源、关闭 dispatcher、
最后才打开屏障；未命中则维持原有的"完成屏障 + 关闭 dispatcher"。屏障和 dispatcher 都不能在
释放之前提前处理，否则重新引入本缺陷。此路径上 `encoderStarted` 为 false，因此跳过 drain 与
`stop()`——encoder 已 configure 但从未 start，`stop()` 本会抛异常。

### L4（LOW）并发释放会让 `onInit()` 抛出 `RejectedExecutionException` 而非文档承诺的 `IllegalStateException`

线程 A 通过 `allocateResourcesOnce()` 的已定型检查并释放锁，尚未 `execute`；线程 B 的
`requestRelease()` 此时 `initInProgress` 为 false，于是认领拆除并在 EGL 线程执行
`completeInlineRelease()`，其中 `recordingDispatcher.close()` 关闭了执行器；A 恢复后
`recordingExecutor.execute(task)` 抛 `RejectedExecutionException`，逃出 `onInit()`。
没有挂起也没有泄漏（当时尚未分配任何资源），只是异常类型与 `@throws` 契约不符。

修复：`execute` 包 try/catch，`RejectedExecutionException` 转为
`IllegalStateException("Screenshot recorder was already released", cause)`。`beginInit()` 本来
也会答复 `RELEASED`，语义一致。

### D1（MEDIUM，文档）"`onDataUpdate` 的 `flags` 现按位掩码传递"不是行为变更

HEAD 就是把 `info.flags` 原样传给 listener，这轮只改了录制器内部识别 `BUFFER_FLAG_CODEC_CONFIG`
的匹配方式。§13.5 与 `CHANGELOG.md` 原先要求消费者改为位测试，会让消费者误以为需要迁移；demo
自己的 listener 仍是精确匹配（`else` 分支照样写入数据，无害）。已改写 §13.2 M2、删除 §13.5 该行、
修正 `CHANGELOG.md`。

### D2（LOW，文档）§13.7 "四处 `when (InitOutcome)`"实为两处

另两处 `when` 匹配的是 `encodeType`。已修正。

### L1（LOW）`runOnEglThread` 的 5 秒上限混入了 action 自身的执行时间

`FutureTask.cancel(false)` 分不清"排队中"与"已开始"：对已开始的任务它同样返回 true 并丢弃结果，
action 照跑，资源建好却无人认领。改为用 `AtomicBoolean claimed` 做一次 CAS 认领：EGL 线程在执行
action 前认领，调用方超时后认领；谁赢谁决定该请求的命运。调用方赢则 `cancel` 并抛出，输则说明
action 已在跑，转为无界等待。5 秒因此只约束排队等待。

### L3（LOW）`Falcon.getRootViews()` 在 Activity 已回收时误报"非 SDK 接口限制"

主线程路径上 `weakAct.get()` 为 null 时，原 `checkNotNull` 的消息把原因说成反射被拦。改为先判
Activity 是否存活，为 null 时抛 `UnableToTakeScreenshotException("Activity is gone ...")`，与
`captureFromOtherThread()` 一致。

### L2（LOW，项目规则，未处理）`Screenshot2H26xStrategy.kt` 超过 800 行

HEAD 为 741 行，本轮修复后为 1011 行，超过 coding-style 的 800 行上限；detekt `LargeClass`
阈值为 1300，不会挂检查。EGL 状态被初始化、编码、释放三条路径共享，在无法编译的环境里拆分
风险高于收益，建议本地构建通过后单独做一次拆分（候选：把 `initEgl()` / `releaseEgl()` /
`encodeImages()` 与 EGL 字段抽成 `EglEncoderSurface`）。

### 未处理项（明确判定为无需改动）

`completeInlineRelease()` 末尾的 `reportFailureIfAny()` 只读 `recordingFailure`，因此纯
`onInit()` 失败路径不会回调 `screenDataListener.onError()`。这是刻意的：`onInit()` 的调用方已经
同步收到该异常，再回调一次等于重复上报。`recordingFailure` 承载的是 codec 异步失败，该注释描述
的仍然准确。

### 验证状态

**本节修复同样未编译、未跑测试、未真机验证。** 除 §13.7 已列的两处构造外，新增两处建议本地首先
确认：`eglThread.let { holder -> Executors.newSingleThreadExecutor { ... } }` 的类型推断，以及
`FutureTask<T> { check(...); action() }` 多语句 lambda 的 SAM 转换。

已静态核对：文件内无直接 `throw IllegalStateException(...)`（两处带 cause 的构造都经由返回异常的
辅助函数），因此不触发 detekt `UseCheckOrError`；`when (InitOutcome)` 两处分支穷尽；改动后
`RejectedExecutionException`、`AtomicBoolean` 两个 import 均已存在且有使用；行长均 ≤100。

待真机验证追加两条：

- 让初始化失败（例如在没有 HEVC 编码器的设备上选 H265）后只调 `onInit()` 不调 `onRelease()`，
  用 Android Studio Profiler 确认 `screenshot-h26x-egl` 线程退出、Activity 可被回收；随后调
  `startRecord()` 应立即抛带原始 cause 的 `IllegalStateException`。
- `onInit()` → `startRecord()` → 立刻 `onStop()`（B5 的时序）：确认 `releaseAndJoin()` 返回后
  `screenshot-h26x-egl` 与 `scr-rec-send` 两个线程都已退出、encoder 已释放，而不是屏障先开、
  资源仍在。

# 14. 第九轮：对 §13 整改的独立复核与修复（2026-09-16）

## 14.0 背景与方法

§13 的整改完成后，对 5 个改动文件做第四轮独立复核：4 个分域代理（录制器生命周期 /
`Falcon` / demo 调用侧 / OPUS）并行静态审查，主审人逐条回读源码复核并二次裁定。
**审查环境按协作约定未运行任何 Gradle 任务**，全部为静态源码分析。

## 14.1 裁定结果

§13 声称的修复共 28 项，复核结论：

| 域 | 声称项 | CONFIRMED | PARTIAL |
|----|--------|-----------|---------|
| `Screenshot2H26xStrategy`（B1–B5、M2/M4/M5/M6/M8、L1/L4、规则1、§6.1、N4/N5 等） | 15 | 15 | 0 |
| `Falcon`（A3、M3、M7、N6、L3、规则2、N7） | 7 | 6 | 1（M3） |
| demo + `ScreenProcessor`（A1、A2、N2、规则3） | 4 | 0 | 4 |
| `OpusFilePlayer`（M1、N1） | 2 | 1（N1） | 1（M1） |

**修得最扎实的是拆除认领权状态机**：`onInit` 失败路径、`requestRelease()`、
`invokeOnCompletion`、协程 `finally` 四方全部在 `lifecycleLock` 下以 `teardownClaimed`
为唯一 CAS 点，条件严格互补，逐个交错窗口走查未发现重复认领或都不认领的组合。
B4(a) 的 ThreadFactory lambda 经逐符号核对，确认只捕获 `AtomicReference`、不再捕获 `this`。
B5 的 `bodyEntered` 方案中屏障与 dispatcher 都在资源释放之后处理，没有重新引入缺陷。

## 14.2 本轮发现并修复的问题

| 编号 | 级别 | 问题 | 修法 |
|------|------|------|------|
| **P1** | 阻断 | `Screenshot2H26xStrategy.kt` 的 `allocateOnEglThread()` 在 `finally` 块内 `throw teardownFailure`（嵌在 `.onFailure {}` lambda 内）。detekt `ThrowingExceptionFromFinally` 为 active、`maxIssues: 0`、仓库无 baseline，该规则用 `forEachDescendantOfType<KtThrowExpression>` 全递归遍历 finally 子树，嵌套 lambda 照样命中 ⇒ `./gradlew staticCheck` 必然失败 | 改为：有初始化异常时 `addSuppressed`，无初始化异常时记 ERROR 日志，不再从 `finally` 抛出。该分支运行期本就几乎不可达（`completeInlineRelease` 内部吞掉全部异常），故无行为代价 |
| **P2** | HIGH | **A2 的核心前提是假的。** `releaseHandlerAndJoin()` 在回调线程 `join(2000ms)` 超时后只打日志就返回，`completeInlineRelease()` 的 `finally` 随后**无条件**完成 `releaseCompleted` ⇒ `releaseAndJoin()` 正常返回 ⇒ demo 认为 `released == true` 并武装下一次会话，而 `scr-rec-send` 线程可能仍活着、队列里还压着 `onDataUpdate`。A2 只挡住了 demo 侧 10 秒外层超时，挡不住策略内部 2 秒放弃回调线程这条更易发生的路径 | 新增 `callbackThreadAbandoned`，join 超时或被中断且线程仍存活时置位；**`releaseAndJoin()` 返回类型由 `Unit` 改为 `Boolean`**，false 表示"屏障已开但回调线程被放弃"。demo 的 `awaitRecorderRelease()` 消费该返回值，两种不干净情形分别记日志并退休会话 |
| **P3** | HIGH | **demo 会话守卫存在真实 TOCTOU。** `onDataUpdate` 在 `outputLock` **之外**比对会话号，中间隔着若干日志调用；被退休的回调线程可以通过守卫后被调度出去，恢复时新会话已开流，数据写进新文件 | 锁内增加权威性复检。退休会话（`activeSession.incrementAndGet()`）与换流（`openVideoOutput()`）都在主线程按序发生且换流持锁，因此持锁的陈旧写入方只可能看到自己那一代的流。`createRecorder()` 的 KDoc 记明了这条顺序依赖 |
| **P4** | HIGH | **`Falcon` 把未缓存的反射一起搬上了主线程。** `findField()` 每次调用都走 `Class.getDeclaredFields()`，ART 上每次返回全新分配的 `Field[]`。A3 的修复把 `getRootViews()` 搬到主线程，连带把这份反射也搬了上去：每帧 3 次全局字段查找 + 每个 root 一次 `mView` 查找，`ViewRootImpl` 字段数百量级，30fps 下主线程每秒新增约 2–3 万个 `Field` 对象。§9.4 的取舍评估完全没提这一项 | 新增 `fieldCache: ConcurrentHashMap<String, Field>`，键为 `类名#字段名`。这些是固定的框架字段，一次命中终生有效 |
| **P5** | HIGH | **`onInit()` 的两个公开 API 误用路径**（仓库内不可达，影响 JitPack 下游）：① `allocateResourcesOnce()` 在初始化**进行中**就返回 `ALREADY_INITIALIZED`，并发调用者的 `onInit()` 在资源尚不存在时正常返回，违反"blocks until it finishes"的承诺；② `startEncoder()` 无幂等闸，按接口文档的 `onInit(); onStart(); startRecord()` 会调两次，`MediaCodec.start()` 打在已 Executing 的 codec 上抛异常 | ① 就绪判断改为 `initStarted && !initInProgress`，让并发调用者排到在飞初始化之后，由 `beginInit()` 在资源就绪后答复；② 新增 `encoderStartRequested` 闩，在 `checkNotNull` **之后**做 CAS，使"未初始化就调 `onStart()`"不会消耗掉唯一的一次启动机会。与 `encoderStarted` 分开：后者必须在 `start()` 真正成功后才置位，拆除只能停真正进入 Executing 的 codec |
| **P6** | MEDIUM | **OPUS 假成功。** `AudioTrackPlayer.write()` 在 `playState != PLAYING` 时返回 0，消费者的 `check(writtenBytes >= 0)` 对 0 成立，`consumedPcmCount` 照常自增 ⇒ 全程静音、队列被全速抽干、停滞检测认为"一直有进展"，最后走自然结束触发**成功**回调。比挂起更难排查 | 消费循环增加 `check(writtenBytes > 0 || pcmData.isEmpty())`，零字节写入即判失败。拆除期间该异常被 `terminalStarted` 守卫吞掉，不影响正常停止 |
| **P7** | MEDIUM | **OPUS 停滞看门狗零日志。** M1 原文点名的症状之一就是"也无日志"，而看门狗触发时一行都不打；`requestFailure()` 在 `terminalStarted` 已置位时静默返回，故这可能是唯一一次不留痕迹的失败 | 触发点先记 ERROR，带上 `queued` / `consumed` / `pending` 三个计数 |
| **P8** | MEDIUM | **M38 的尾巴未收掉。** `ScreenProcessor.onInit()` 的新 KDoc 写"除非实现另行说明，否则在主线程调用"，而 `Screenshot2H26xStrategy.onInit()` 并未 document otherwise——它实际最长阻塞 5 秒，CAS 认领输掉时转无界等待。按接口文档调用会 ANR | `Screenshot2H26xStrategy.onInit()` 的 KDoc 增加"**不要在主线程调用**"及其理由。无界等待机制本身保留：action 已在跑时不能丢下半成品 EGL 上下文 |
| **P9** | MEDIUM | **§13「规则违反 2」的反射诊断只覆盖了一半。** `Falcon.getRootViews()` 给 `mGlobal` 加了 `checkNotNull` + "可能被非 SDK 接口限制拦截"的提示，但紧接着的 `rootObjects as List<Any>` / `paramsObject as List<LayoutParams>` 仍是裸转换。targetSdk 36 下真正受限的恰恰是 `mRoots` / `mParams`，被拦时抛 `null cannot be cast to non-null type` 这种毫无线索的 NPE，日志里仍只有一句含糊的"Unable to take screenshot"——正是该规则违反想消灭的症状。表现仍是丢一帧、非新增崩溃，故判 MEDIUM | 抽出 `reflectedList()` 辅助函数，用 `as?` + 带字段名和拦截提示的 `UnableToTakeScreenshotException` 取代裸转换，两个字段与 `mGlobal` 诊断口径一致 |
| **P10** | HIGH（既有缺陷，本轮之前就存在） | **`ScreenRecordMediaCodecStrategy.onRelease()` 的编码器与 VirtualDisplay 从不释放。** 该方法以 `if (!videoEncoderLoop.get()) return` 开头，而调用方（`MediaProjectionService.onReleaseScreenShare()`）先 `stopScreenShare()`（→`onStop()` 把该标志置 false）再 `onRelease()` ⇒ `h26xEncoder.release()` 与 `virtualDisplay.release()` 永远执行不到。与 §13 新改的 `ScreenProcessor.onRelease()` KDoc "Releases every owned resource" 直接矛盾 | 去掉该守卫（`onStop()` 保留自己的守卫），并在释放后把两个字段置空，使重复调用是 no-op 而非 double free。与同文件 `changeOrientation()` 既有的写法一致 |

## 14.3 复核中被推翻或下调的三条代理结论

保留在此，避免后续维护者据错误模型做决策。

1. **`Falcon` 的 `!rootView.isShown` 过滤**被代理算作"M7 未完全消除"——不成立。M7 针对的是
   §13 新加的 `isAttachedToWindow` 采集阶段过滤，那个确已删除，**M7 判 CONFIRMED**；
   `isShown` 是 `Falcon` 原有逻辑，属独立的既有风险，单独记账。
2. **demo TOCTOU 窗口"对 I 帧 hex dump 可达毫秒量级"**——不成立。`toHexString()` 只在
   `BUFFER_FLAG_CODEC_CONFIG` 分支（配置帧仅几十字节），关键帧分支只打 size。但 P3 的结论不受
   影响：线程可在守卫与写入之间的任意一点被抢占，窗口长度无需论证。
3. **`OpusFilePlayer.finishPlayback()` 的 `playbackScopeJob.cancelAndJoin()` 无界等待**——由
   HIGH 下调为 MEDIUM，**本轮未修**。该调用前刻意按 `cancel()` → `closeInputFile()` →
   `audioTrackPlayer.stop()` 的顺序唤醒阻塞的 `write()`，常见路由切换场景能正常返回；真正的无界
   挂起要求 HAL 彻底卡死、连 `pause()/stop()` 也进不去 AudioFlinger 锁。该给这个 join 加界并保证
   终局回调一定触发，列为后续项。

## 14.4 §9 / §13 勘误索引

- 独立复审文档 §9.4 —— "1080p RGB_565 约 1.6 MB/帧"：算错，实为 1920×1080×2 ≈ **3.96 MiB/帧**
  （30fps 约 119 MB/s，而非 48 MB/s）。该段还遗漏了同批搬上主线程的 `getDeclaredFields()` 开销，
  其数量级高于位图分配。取舍结论需按真实值重估，已在原文标注。
- 独立复审文档 §4.1 A3 第 2 点 —— "`toTypedArray()` 会抛 `ConcurrentModificationException`"：
  不成立。`ArrayList.toArray()` 走 `Arrays.copyOf`，不经迭代器。真实风险是两次读取之间 size
  缩小导致尾部 null 或下标错位。修复方向不变，失效机制描述有误，已在原文标注。
- §13.1 A2 —— "超时后不再武装下一次会话"：只覆盖了 demo 侧 10 秒外层超时，未覆盖策略内部 2 秒
  放弃回调线程的路径，见 P2。
- §13.5 —— `releaseAndJoin()` 现返回 `Boolean`（原 `Unit`），见 P2。

## 14.5 行为变更汇总

| 位置 | 变更 |
|------|------|
| `Screenshot2H26xStrategy.releaseAndJoin()` | **返回类型 `Unit` → `Boolean`**。false 表示屏障已开但回调线程被放弃（仍可能投递残帧），调用方应退休该录制器的会话而非视为干净释放 |
| `Screenshot2H26xStrategy.onInit()` | KDoc 明确**不得在主线程调用**（最长阻塞 5 秒，认领竞争失败时无界）。并发调用现在会阻塞到在飞的初始化真正完成，而不是提前返回 |
| `Screenshot2H26xStrategy.onStart()` | 重复启动编码器变为无操作，`onInit(); onStart(); startRecord()` 序列不再抛 `IllegalStateException` |
| `ScreenRecordMediaCodecStrategy.onRelease()` | 现在真正释放编码器与 VirtualDisplay（此前因守卫从不执行）；重复调用为 no-op |
| `OpusFilePlayer` | AudioTrack 接受 0 字节即判定失败并回调 `onFailure`，不再静音播完后报成功；停滞看门狗触发时记录 ERROR 日志 |
| demo 录屏页 | 释放被判定为不干净（超时或回调线程被放弃）时禁用开关并提示，两种情形日志可区分 |

## 14.6 验证状态

**本轮修复未编译、未跑测试、未真机验证。** 已静态核对：5 个改动文件行长均 ≤100；
`finally` 块内已无任何 `throw`（脚本扫描确认，P1 的 detekt 阻断已消除）；
`ConcurrentHashMap` 为 `Falcon.kt` 新增 import 且有使用；`initInProgress`、`encoderStartRequested`、
`callbackThreadAbandoned` 三个新成员均有读写点；项目未启用 `allWarningsAsErrors`，
`reflectedList()` 的 unchecked cast 已加 `@Suppress`。

**需用户本地执行**：

```bash
./gradlew --continue :screencapture:compileDebugKotlin :audio:testDebugUnitTest \
  :screencapture:detekt :screencapture:ktlintCheck \
  :audio:detekt :audio:ktlintCheck \
  :demo:ktlintCheck :demo:compileDevDebugKotlin
```

**待真机验证**（在 §13.7 基础上追加）：

- 主线程掉帧与 GC 抖动实测：录制期间用 Profiler 观察 `Field` 对象分配是否已随 P4 消失。
- targetSdk 36 上 `mRoots` / `mParams` 反射是否真被非 SDK 接口限制拦截；若被拦，确认日志给出的是
  P9 新增的字段名 + 拦截提示，而非裸 NPE。
- 人为让 `scr-rec-send` 阻塞以命中 2 秒放弃分支：确认 `releaseAndJoin()` 返回 false、开关保持禁用、
  被放弃的录制器写不进任何文件（P2 + P3）。
- 屏幕共享（`BY_MEDIA_CODEC`）停止后再启动：确认编码器与 VirtualDisplay 已真正释放，不再累积（P10）。
- OPUS：人为让 AudioTrack 脱离 PLAYING 状态，确认走失败回调而非静音"成功"（P6）。

## 14.7 已知未处理

- `Screenshot2H26xStrategy.kt` 本轮后约 1030 行，仍超 coding-style 的 800 行上限；类内方法数已达
  31，detekt `TooManyFunctions.thresholdInClasses` 为 33，**只剩 2 个名额**。下一轮建议以拆分为主、
  不再叠加逻辑（候选：把生命周期状态机与 `EglThreadDispatcher` 各抽一个类）。
- `OpusFilePlayer.finishPlayback()` 的 `cancelAndJoin()` 仍无界，见 §14.3 第 3 条。
- `Falcon` 的 `!rootView.isShown` 过滤仍可能让位图缩成悬浮窗尺寸，下游 `encodeImages()` 按固定尺寸
  拉伸，表现为一帧全屏失真。属既有风险，建议在 `encodeImages()` 入口加宽高比校验。
- `Falcon.getRootViews()` 仍是 public 且无线程契约，库使用者可在任意线程调用。


# 15. Codex 复审遗漏修复（2026-09-16）

## 15.1 范围与实现

基线为 `6f6313f5d`。本节只处理随后复审发现的两项代码遗漏及协程文档错误，不代表 §14.7 的
已知问题已经全部关闭。

- **初始化等待被中断**：`FutureTask.get()` 抛出 `InterruptedException` 不会停止后台任务。
  `onInit()` 现在捕获该异常，调用现有 `requestRelease()` 并恢复调用线程的中断标记后重抛。
  尚在排队的初始化由 `beginInit()` 拒绝；已经进入初始化的任务由 `endInit()` 在 EGL 原线程
  接管释放。调用方收到异常时清理可能仍在进行；若驱动调用永久卡住，仍不能保证释放完成。
  不对 EGL 工作线程执行 `cancel(true)`，避免中途打断原生资源初始化。
- **输出回调失败且没有录制协程**：`onOutputBufferAvailable()` 保留首个异常，先在 `finally`
  尝试归还 output buffer，再退出 `codecCallbackLock` 并请求释放。`onError()` 也直接使用同一
  释放协议；有录制任务时取消任务并由其 finally 清理，没有任务时由 EGL 执行器负责清理与错误
  通知。迟到回调仍由 codec 身份检查拦截。
- **文档勘误**：独立复审文档 §6 / §6.1 已修正 `CancellationException` 对父子与兄弟协程的
  传播说明、吞异常与 `isCancelled` 的关系，以及“主线程没有协程”的错误前提。

公开方法签名不变；`onInit()` 的 KDoc 补充中断后的行为。测试依赖沿用仓库已有 bundle，未新增
生产依赖。

## 15.2 回归测试与验证边界

新增 `screencapture/src/test/kotlin/com/leovp/screencapture/screenrecord/base/strategies/`
下的 `Screenshot2H26xStrategyTest.kt`：

1. 在 codec 初始化进行中中断调用线程，验证中断状态、EGL 执行器退出、编码器清空及在原线程释放。
2. 在初始化排队时中断调用线程，验证不创建 codec 且执行器退出。
3. 在 `onInit(); onStart()` 后注入输出 buffer 读取失败，验证自动清理、错误仅通知一次、迟到回调被忽略。

三个用例使用真实录制器生命周期代码、真实线程与 latch，仅模拟 MediaCodec/EGL 等 Android 边界；
均已在未修复版本上观察到目标断言失败。测试没有执行真实 EGL/Codec 驱动，不能替代真机验证。

本轮验证命令：

```bash
./gradlew --offline --continue :screencapture:testDebugUnitTest \
  :screencapture:ktlintCheck :screencapture:detekt \
  :demo:compileDevDebugKotlin --rerun-tasks
```

验证结果：Gradle `BUILD SUCCESSFUL`，348 个任务实际执行；新增 3 项单测通过，0 失败、0 跳过；
`screencapture` 的 ktlint、detekt 及 Demo 集成编译通过，`git diff --check` 通过。构建仍有弃用
API 与 Gradle 兼容性警告，本轮未扩大范围处理。

待真机验证：API 21～26 与较新设备的录制、停止、重复进入退出、旋转及前后台切换；驱动初始化
异常和输出异常时的实际资源释放。本轮未执行设备测试。

### 15.2.1 验证覆盖缺口（重要）

**上面的 `BUILD SUCCESSFUL` 只覆盖 `screencapture` 与 `demo` 的编译，不代表 §14 的改动已全部验证。**
§14.6 要求的任务集与 §15.2 实际执行的任务集存在差异，以下**至今没有编译或静态检查过**：

| 未执行的任务 | 涉及的未验证改动 |
|--------------|------------------|
| `:audio:testDebugUnitTest` | §14.2 P6（`OpusFilePlayer` 零字节写入判失败）、P7（停滞看门狗日志） |
| `:audio:detekt`、`:audio:ktlintCheck` | 同上；P7 新增的多行日志字符串未过 ktlint |
| `:demo:ktlintCheck`、`:demo:detekt` | §14.2 P2/P3 在 `RecordSingleAppScreenActivity` 的改动 |

补齐命令：

```bash
./gradlew --continue :audio:testDebugUnitTest :audio:detekt :audio:ktlintCheck \
  :demo:ktlintCheck :demo:detekt
```

在这三组任务跑通之前，**不应认为本分支已通过 `staticCheck`**。

## 15.3 第九轮复核对 §15 的确认与补充

对 `322cb6eed` 的独立复核结论：三处代码改动全部成立，未引入新缺陷。逐条核实要点：

- **`onError()` 改为无条件 `requestRelease()` 不会吞掉真实失败**。`recordingFailure` 在
  `requestRelease()` **之前**即已 `compareAndSet` 写入（`:208` / `:259`），协程 `finally` 的
  `reportFailureIfAny()` 无条件执行且只过滤 `CancellationException`；协程路径与
  `completeInlineRelease()` 两条上报路径互斥，不会重复回调。相比此前"等录制循环察觉
  `isRecording=false` 才收尾"是严格改进。
- **中断处理的两条时序成立**。排队中：`requestRelease()` 先置 `releaseRequested`，FIFO 保证 init
  任务先执行并由 `beginInit()` 直接返回 `RELEASED`，不分配任何资源；已在飞：`initInProgress`
  为 true 使其走 `teardownDeferredToInit`，由 `endInit()` 在 EGL 原线程接管释放。
- **测试的编译前提均成立**：`h26xEncoder` 为 public `var`；`Builder(width, height, dpi, listener)`
  签名匹配；`test` bundle 含 `kotlin-test-junit5`，故 `kotlin.test.Test` 映射到 Jupiter；
  `initEgl()` 用到的 8 个 `EGL14` 方法与测试桩精确对应；`TextureRenderer.loadShader()` 无编译
  状态校验，因此在 `unitTests.isReturnDefaultValues = true` 下 GLES20 桩不会抛异常。
- **§6.1 的三处协程语义订正正确**，前几轮评审的原文确有技术错误（未捕获的
  `CancellationException` 不会传播到父/兄弟协程；主线程可以运行协程）。以订正后的表述为准。

本轮追加一处修改：

| 编号 | 级别 | 问题 | 修法 |
|------|------|------|------|
| **P11** | LOW | `onInit()` 现在会抛 `InterruptedException`（Java 中为受检异常），但 Kotlin 默认不生成 `throws` 子句。Java 消费者写 `catch (InterruptedException e)` 会因"该异常不可能抛出"而**编译失败**，却仍可能在运行时收到它。对 JitPack 下游是现实问题 | `onInit()` 加 `@Throws(InterruptedException::class)`，使 JVM 签名如实声明。Kotlin 侧签名与行为不变 |

未处理（仅记录）：新增测试使用 `kotlin.test.assert*` 而非项目约定的 Kluent
（`.claude/rules/kotlin/testing.md`），不影响运行，留待后续统一。
