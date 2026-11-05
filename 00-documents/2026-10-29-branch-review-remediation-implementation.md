# 分支全面审查问题修复实施记录

## 1. 范围与结论

本轮以 `review/native-modules` 相对 `master` 的审查结论为输入，落实
`2026-10-26-branch-review-part2-audio-netty_cc.md` 中已经确认、方案明确的 audio、MediaCodec、
basenetty、网络监听及 Demo 生命周期问题，并补充 2026-10-29 复核发现的两个独立竞态。

修复后的共同约束是：资源拥有者必须有单一终态入口；并发关闭只能有一个 owner；失败不得跳过后续
释放；迟到回调必须通过代际或 token 失效；异步失败必须能抵达拥有者，而不能只写日志。

## 2. Netty 与网络回调

### 2.1 `Future.awaitCompletion()` 注册竞态

原逻辑先读 `isDone`，再注册 listener。future 恰好在两步之间完成、且其 executor 已终止时，Netty 可能
拒绝投递 listener，continuation 永远不恢复。

修改后：

1. 保留完成态快速路径；
2. 注册 listener 后再次读取 `isDone`；
3. listener、二次检查和协程取消共享 `AtomicBoolean` 单次恢复门闩；
4. 协程取消时先烧掉门闩，再移除 listener。

回归测试使用 mock future 构造 `false -> true` 的 `isDone` 序列，验证不依赖 listener 真正回调也能完成，
且 listener 只注册一次。

### 2.2 client/server teardown

`BaseNettyClient.release()` 原来把 `UNINITIALIZED` 当成“已释放”，导致从未 connect 的实例无法释放构造时
创建的 worker group；handler 抛 `Error` 还会跳过 channel/group 清理，超时路径则可能把状态永久留在
`RELEASING`。

修改后：

- 生命周期守卫以 `released` 为一次性所有权依据，未连接实例也能进入关闭；
- owner 在锁内同时写入 `released = true` 与 `RELEASING`，并发调用只有一个成功；
- handler release 捕获 `Throwable` 并保存原始失败，但无条件清空 handler/initializer；
- channel/group 关闭仍执行，清理失败以 suppressed 聚合；
- `finally` 无条件把 client 状态置为 `UNINITIALIZED`，最后再传播原始失败；
- server 的已启动和未启动关闭路径采用同样协议，`onStopped()` 的失败也参与聚合。

测试覆盖未连接释放、并发单 owner、client handler `Error`、server handler `Error` 和底层 group 实际进入
shutting-down 状态。

### 2.3 `NetworkUtil` 注销可重试

原实现先从 map 删除 callback，再调用 framework 注销。framework 抛异常时，本地句柄永久丢失，系统却
仍持有 callback/context。

修改后先读取映射并把业务 listener 脱钩，只有 `unregisterNetworkCallback()` 成功才用身份匹配删除；
失败返回 `false` 并保留同一个 callback，下一次注销可重试。替换注册旧 callback 时也遵循相同顺序。

## 3. MediaCodec 终态协议

### 3.1 `FAILED` 与一次性上报

`BaseMediaCodec` 生命周期加入 `FAILED`。`transitionToCodecFailure()` 只允许
`RUNNING -> FAILED` 的 CAS；`reportCodecFailure()` 只有赢得转换的线程才通知 owner，因此同一故障不会
被多个 callback 重复上报。owner 通知在 codec 操作锁外执行，避免回调重入死锁。

`notifyCodecFailure()` 由 public `open` 收窄为 `protected open`：它是子类内部失败钩子，不再允许外部
调用者伪造 codec 失败。迁移影响已写入 `CHANGELOG.md`。

### 3.2 异步 flush、代际与 EOS

最初 `BaseMediaCodecAsynchronous.flush()` 在一次操作锁持有期间执行：

```text
检查 RUNNING -> codec.flush() -> generation++ / 清 EOS -> codec.start()
```

这版只能隔离 generation 递增前已进入的 callback，无法识别随后才分发的旧 callback，已由 §9 的底层
会话替换方案取代。现在 callback 在注册时绑定代号，失败状态转换也会在锁内核对该代号。

首次提交输入 EOS 后设置 `inputEosQueued`；同代后续 input callback 不再读取或提交普通输入。input、
output、format-changed、onError 四条 callback 都有统一的普通异常边界。同步 worker 的 `onEndOfStream()`
也使用相同失败通道；真正的协程取消继续传播，不伪装成 codec 故障。

## 4. 音频文件和流播放器

### 4.1 AAC 文件播放器所有权

`AacFilePlayer` 不再继承 `BaseMediaCodecSynchronous`，而是通过私有 `DecoderSession` 组合 codec。这样公开
API 只保留播放器级 `playAac()` / `stop()`，调用方无法再直接调用继承的 `releaseAndJoin()`，造成 codec
释放但 extractor、AudioTrack、terminal scope 仍存活。

所有自然结束、主动停止、取消和失败仍汇聚到 `finishPlayback()` 的一次性屏障。AudioTrack 写入结果通过
`requireSuccessfulAacAudioWrite()` 检查，0 或负错误码立即抛出并进入失败终态；不再增加播放帧数，也不再
触发自然结束回调。测试分别覆盖 0 和负错误码。

### 4.2 OPUS 文件边界

`OpusFramedFileReader` 在分配 `ByteArray` 之前检查 payload 不超过 codec 的 8 KiB 输入上限，损坏文件
不能再依据文件长度尝试接近 2 GiB 的单次分配。

`shouldSubmitOpusPayload()` 把三个情况明确分开：

- 非空 payload：提交 decoder；
- 空 payload 且没有下一个 start code：这是文件尾的边界标记，自然结束；
- 空 payload 但后面还有 start code：这是中间空帧，抛 `IllegalArgumentException`。

测试覆盖超限分配前拒绝和上述三类播放器决策。

### 4.3 stream decoder 异步失败

`AacDecoder` 增加与 OPUS 对称的必填错误回调；`AudioPlayer` 同步增加必填 `onFailure`，把 AAC/OPUS
decoder 失败传递给公开 owner。两个 stream player 以独立 `terminalScope` 执行失败清理，并用终态
所有权 CAS 区分失败与显式停止：失败先赢时，完整释放后才调用 owner；显式停止先赢时，迟到错误不再
启动失败任务。

`StreamPlayerStopper` 现在用 `stopStarted` 与 `stopCompleted` 协调并发 teardown。首个合法调用者负责
detach decoder、取消并等待 worker、释放 decoder 与 AudioTrack；后到调用者只等待并复用同一结果。
caller-job 守卫仍位于 once CAS 之前，非法自作用域调用不会消耗终态所有权。此前在 stopper 守卫前预写
`stopped = true` 的路径已删除。

## 5. `MicRecorder` 单会话

新增 `MicRecorderLifecycleGate`，同一监视器内完成：

- 一次性 `claimStart()`；重复启动或停止后启动抛 `IllegalStateException`；
- `AudioRecord.startRecording()`、lazy record Job 创建、字段发布和 `job.start()`；
- stop owner 认领及当前 Job 快照。

赢得 stop 的外部调用者进入 `NonCancellable` 后依次停止 AudioRecord、`cancelAndJoin` worker、取消 scope、
释放 encoder/AudioRecord/effect 并完成 `releaseCompleted`。后续 stop 只等待同一屏障。worker 自身故障也先
认领 stop，再走同一释放函数。

构造失败回滚的 `releaseCompleted.complete(Unit)` 与 encoder 的 `releaseAndJoin()` 位于同一个
`NonCancellable` 协程 `finally`，不会在 encoder 真正释放前放行等待者；该协程只捕获构造期间已经复制到
局部变量的 encoder，不依赖尚未成功构造的对象被外部持有。

## 6. Demo 生命周期

### 6.1 录音意图

`RecordingIntentCoordinator` 使用递增 generation 和目标 `AudioType` 表达用户意图；`AudioActivity` 再用
`recordingMutex` 串行化真实资源切换。新的 start 先让其它格式按钮退出，旧权限结果、旧 off 事件和旧
失败回调都必须通过 token 检查，不能发布或关闭新资源。

文件创建、`MicRecorder` 构造和启动移至 IO scope；切换格式时先完整等待旧 recorder 停止，再创建新
实例。启动异常会清空相同实例、等待其释放并关闭输出流。`RecordCallback.onRecording()` 不再吞文件
写入异常：异常回到 recorder worker，触发失败 teardown；`onStop(false)` 只在 token 仍是当前意图时
复位按钮，并用 recorder 身份比较避免迟到清理误停随后创建的同格式实例。

### 6.2 WebSocket Demo

client Activity 保存连接 Job，并用 mutex 保护客户端发布；销毁时先取消并等待创建 Job，再摘除并释放
已经发布的实例，最后取消 scope。连接协程在发布前发现 Activity 已销毁时会自行释放局部 client。
server 的 `onStartFailed` 先等待 `stopServer()`，成功完成释放后才清空字段。

## 7. 验证边界

本机 JVM/Gradle 验证只能证明状态机、回归测试、源码质量门禁和集成编译；不能替代真实 AudioRecord、
AudioTrack、MediaCodec、音频焦点与 Netty socket 的设备行为。真机仍需覆盖：OPUS flush 后恢复、AAC
自然结束/写失败、三个录音格式快速切换、重复 start、录音中退后台、WebSocket 连接中销毁及重复进入退出。

最终命令和实际结果记录在
`2026-10-26-branch-review-part2-audio-netty_cc.md` 的“2026-10-29 实施复核”章节。

## 8. 2026-11-03～04 独立复核补充

再次按审查文档逐条反查源码与测试后，发现原实施虽然已覆盖 OPUS 帧读取器和 AAC 写入结果的纯逻辑，
但没有真正满足 M-7 所要求的播放器层资源顺序、一次性终态、阻塞写入、队列溢出及尾随 start code
回归；同时发现三个异常清理缺口。

### 8.1 MediaCodec 启动时的 `Error`

`BaseMediaCodec.start()` 原先只捕获 `Exception`。当 `createCodec()`、`setCallback()`、
`codec.start()` 或 `onCodecStarted()` 抛出 `OutOfMemoryError`、`LinkageError` 等 `Error` 时，已经创建或
启动的 codec 不会进入既有回滚路径。

修改后启动回滚捕获 `Throwable`，仍按原顺序停止并释放 codec、清空 worker/state，再重新抛出原始
失败；清理失败作为 suppressed 附加。新增测试从 `onCodecStarted()` 注入 `AssertionError`，先证明旧实现
未调用 `MediaCodec.release()`，再验证修复后原始错误继续外传且 native 资源已释放。

### 8.2 AAC/OPUS 文件播放器测试接缝

新增内部 `FileAudioOutput` 边界，把 Android `AudioTrackPlayer` 封装在 `AndroidFileAudioOutput`；AAC
再拆出 `AacSampleSource` 与 `AacDecoderSession`，OPUS 拆出 `OpusDecoderSession`。这些类型均为
`internal`，不扩大公开 API，只允许 JVM 测试用可控 fake 驱动完整播放器终态。

播放器级测试现在覆盖：

- AAC 第二次启动不改动当前会话，自然结束后按 `AudioTrack.stop -> decoder release -> source release ->
  AudioTrack.release -> callback` 排序，重复 stop 不重复释放；
- OPUS 尾随 start code 不提交空帧，输入 EOS 后自然结束并只释放一次；
- OPUS `AudioTrack.write()` 阻塞时，stop 先调用 `AudioTrack.stop()` 唤醒写入，再等待 worker，最后释放
  decoder 与 AudioTrack；
- OPUS decoded PCM 队列溢出进入失败终态，且错误回调只在 decoder/AudioTrack 完整释放后触发；
- AAC/OPUS 初始化抛 `Error` 时仍完整释放已经发布的 source、decoder 与 AudioTrack。

### 8.3 清理步骤不能因前一步失败而短路

AAC/OPUS 的 `finishPlayback()` 原先把 `AudioTrack.stop()`、worker join 和 decoder release 放在同一个
`try` 中。若 stop 自身抛异常，后续 codec worker/decoder 释放会被跳过。

修改后每个清理步骤独立尝试，首个失败作为主异常，后续失败使用 `addSuppressed()` 聚合；decoder 引用在
释放前先摘除，终态屏障仍只完成一次。新增测试让 `AudioTrack.stop()` 主动失败，验证 AAC 仍释放 decoder、
sample source 和输出，OPUS 仍等待 worker 并释放 decoder/输出，最终由显式 `stop()` 向调用方传播首个
失败。再次复核还发现 AAC source release 失败原先只记录日志，且 output release 失败会跳过回调字段
清空；现 source 与 output 也分别尝试并聚合失败，回调引用在 `finally` 中无条件清除。

### 8.4 回调异常边界补齐

补齐异步 MediaCodec input、output、format、error hook 和 owner failure hook 的
`CancellationException` 回归，以及同步 EOS callback 的取消回归，验证 callback 线程不会因业务取消异常
静默退出，codec 仍只进入一次 `FAILED`；error hook 的失败作为 suppressed 保留原始 CodecException。

### 8.5 最终验证

首次把所有受影响模块合并执行时，AAC 的“第二次启动”用例触发 `UncompletedCoroutinesError`。该用例直接
在 `runTest` 虚拟时钟中等待真实 `Dispatchers.IO` teardown，定向运行可能通过、全套运行可能等待到测试
框架的一分钟上限。测试改为与 OPUS 用例一致，在真实 `Dispatchers.Default.limitedParallelism(1)` 上用
两秒超时等待 `stop()`；它仍验证同一生产终态，只消除虚拟时钟与真实 dispatcher 混用造成的假挂起。

最终使用独立、单次 Gradle daemon 且全部带 `--rerun-tasks --max-workers=1` 执行：

- `:audio:testDebugUnitTest :audio:ktlintCheck :audio:detekt`：通过，110 个任务实际执行；
- `:basenetty:testDebugUnitTest :basenetty:ktlintCheck :basenetty:detekt
  :lib-common-android:testDebugUnitTest :lib-common-android:ktlintCheck :lib-common-android:detekt
  :demo:testDevDebugUnitTest :demo:ktlintCheck :demo:detekt :demo:assembleDevDebug`：通过，662 个任务实际执行；
- `staticCheck`：最后一次在 AAC source/output 清理修复后重新执行并通过，耗时 4 分 37 秒，1425 个
  任务实际执行。该任务构建 instrumentation APK，未在
  设备上运行，不属于真机验证。

输出中仍有仓库既有的 Android/Java 弃用 API、`ExperimentalCoroutinesApi` opt-in 和 SDK XML/CMake
版本警告；它们没有导致本轮任务失败，也不是本次 audio/Netty 修复引入的新诊断。

## 9. H-1 完整回调隔离修复

### 9.1 根因与方案

入口读取 `AtomicLong` 只能表达“回调何时进入应用”，不能表达“buffer 由哪个 native 会话发出”。旧回调
在 flush 后才分发时会读到新值；布尔标记也无法区分标记窗口内的新旧 buffer。

本轮采用替换整个底层 codec 的方式，保留 Kotlin wrapper 与其输入队列：

1. 在 `codecOperationLock` 内确认 `RUNNING`，递增会话代号并重置基类输入 EOS 门闩；
2. `detachCodec()` 把 native 所有权转移到局部变量，同时清空 active 引用；
3. 依次尝试旧 codec 的 `flush()` 和 `release()`；两个步骤任一失败都终止重建，第二个失败作为 suppressed；
4. 调用 `createMediaFormat()` 从参数和 `setFormatOptions()` 重建原始输入格式/CSD，避免误用曾被 output
   format callback 覆盖的 `format`；随后创建新 native codec；
5. `setMediaCodecOptions()` 每次创建 `SessionCallback`，把当前代号保存在不可变构造参数中；
6. 如果并发 release 已认领终态，新 codec 不再 start，由 release owner 在获锁后释放；否则 start 新 codec；
7. 任一普通失败在锁内转入 `FAILED`，锁外通知 owner；`Error` 也通知并继续外传。

旧 SessionCallback 无论在锁前等待、在重建期间进入，还是重建结束后才被投递，都保留旧代号。四种 callback
在取得锁后先核对代号，不匹配时不访问/提交/归还旧 index，也不写入 format 或触发旧 error hook。新 callback
代号从注册时固定，native start 期间出现也不会被误丢。

### 9.2 失败上报与资源所有权

`reportSessionFailure()` 在同一把 codec 锁内核对代号并执行 `RUNNING -> FAILED`，随后锁外通知 owner。
这防止旧 callback 在业务 hook 抛异常后、真正上报前被一次 reset 超越，从而错误地将新会话标为失败。

`BaseMediaCodec.codec` 改为 nullable 私有 backing field 上的非空访问器；`detachCodec()` 为受保护的
所有权移交 helper。正常 release 同样先摘除再释放，reset 中已退休的 codec 不会在最终 release 时被
二次释放。部分配置/启动失败的新 codec 仍由现有 owner 的 `releaseAndJoin()` 路径释放。

复核该基类路径时还发现，原 `runCatchingPreservingCancellation` 会让 native flush 抛出的
`CancellationException` 跳过 native release 和子类 hook。两个回归分别证明“未执行 release”和“清理
取消覆盖原始启动 Error”。现在 native flush、release 和 hook 都先尝试，普通失败继续按原契约记录日志；
清理阶段的取消延后到全部步骤完成后再抛出。启动回滚捕获这类清理失败并附加到原始启动异常，不再覆盖
根因。Samsung 所需的 flush 后 release 顺序保持不变。

### 9.3 迁移与验证边界

异步 `flush()` 从 native flush/start 改为重建 native 会话，增加 codec 重建延迟；编码器可能重新输出
codec config，decoder 重新输出格式事件。覆写 `createCodec()` 的外部子类必须每次创建新实例；复用旧
实例会被拒绝。`setFormatOptions()` 必须能恢复完整原始配置。该影响已写入 KDoc 和 CHANGELOG。

本轮不清空子类软件输入队列、不调用整个 wrapper 的 release hook；这保持原有队列/PTS 语义。仓库内
异步子类为 AAC encoder、OPUS encoder/decoder，均由基类创建 native codec，配置可从构造参数重建；
实际 reset 调用点位于 `OpusStreamPlayer.decodeOrDropLocked()`。

新增回归直接驱动旧/new SessionCallback，覆盖 reset 后晚到的四类旧 callback、新 callback 在 start 期间
正常处理、连续两次 reset、flush/release 失败聚合、replacement 创建/启动失败，以及并发 release 先认领
终态时不再 start replacement。首条回归在旧实现因未释放旧 codec 失败，替换实现后转绿。

真机仍须验证 API 21～26 和较新设备的 OPUS 过载恢复、连续重置、实际声音、重建延迟和资源数量。
JVM 回归不能证明 native codec 重建的设备性能。

### 9.4 最终验证结果

2026-11-04 完成当前代码的组合验证：

```bash
./gradlew :audio:testDebugUnitTest :audio:ktlintCheck :audio:detekt \
  :demo:assembleDevDebug staticCheck --rerun-tasks --max-workers=1 --no-daemon
```

结果：`BUILD SUCCESSFUL in 4m 45s`，1461 个任务实际执行。audio XML 报告合计 84 条测试通过，其中
`BaseMediaCodecAsynchronousTest` 40 条；Demo 单测和仪器测试 APK 构建也在组合任务中执行。没有运行
设备 instrumentation，不能据此认定声音恢复或 codec 重建性能通过。

重新核对注册时代号、锁内状态转换、native 所有权、终态清理和文档后，当前 H-1 修复范围内未发现剩余
明确源码问题。上述验证结果对应本节描述的最终实施版本。
