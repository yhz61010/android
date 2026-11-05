# 分支全面审查问题修复实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 在当前 `review/native-modules` 分支逐项实施；每个行为修复遵循测试先失败、实现后通过的 TDD 顺序。

**目标：** 修复 2026-10-29 相对 `master` 全面审查中确认的代码问题，补齐自动化回归、文档和验证证据。

**架构：** 资源所有者负责完整生命周期，异步状态机使用单次终态和可等待屏障；Netty/MediaCodec 的回调竞态通过原子单次恢复、代际栅栏和锁内状态转换消除；Demo 只负责串行化用户意图，不绕过库层资源协议。

**技术栈：** Kotlin、Android MediaCodec/AudioRecord/AudioTrack、Kotlin Coroutines、Netty、JUnit 5、Robolectric、MockK、Gradle Kotlin DSL。

**依据：** `00-documents/2026-10-26-branch-review-part2-audio-netty_cc.md`、`00-documents/2026-10-20-branch-review-vs-master_cc.md` 及 2026-10-29 当前分支复核结果。

## 全局约束

- 最低支持 Android API 21，不引入更高 API 的无保护调用。
- `MicRecorder.startRecord()` 重复调用或停止后重启直接抛 `IllegalStateException`。
- 不保留已发布版本的旧公开入口；公开行为变化更新 KDoc 和 `CHANGELOG.md`。
- 资源释放必须覆盖正常结束、初始化失败、取消、重复关闭、回调异常和迟到回调。
- 不修改 `CLAUDE.md`、`.claude/**`、`gradle.properties` 或无关用户文件。

## 重点复核

- Future 在完成与 listener 注册之间竞态，executor 同时终止。
- MediaCodec flush 前已进入但尚未取得锁的旧回调。
- stop 调用者取消、重复 stop、worker 自身触发停止三种交错。
- framework 注销失败后再次注销或重新注册。
- Demo 录音权限回调晚到、连续切换格式、Activity 销毁。

---

### Task 1：清除基础门禁和两个独立竞态

**文件：**
- 修改：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/receiver/AudioReceiver.kt`
- 修改：`basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/NettyFutureExt.kt`
- 修改：`basenetty/src/test/kotlin/com/leovp/basenetty/BaseNettyLifecycleTest.kt`
- 修改：`lib-common-android/src/main/kotlin/com/leovp/android/utils/NetworkUtil.kt`
- 修改：`lib-common-android/src/test/java/com/leovp/android/utils/NetworkStatsCallbackTest.kt`

- [x] 先增加 Future 注册竞态和注销失败重试测试，并确认旧实现失败。
- [x] 用 continuation 单次恢复与注册后二次检查消除 Future 竞态。
- [x] framework 注销成功后才移除 callback 映射，失败时保留已 detach 句柄供重试。
- [x] 修正 `AudioReceiver` 表达式函数体并运行三个模块的目标测试和质量检查。

### Task 2：Netty teardown 与 Demo Socket 生命周期

**文件：**
- 修改：`basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/BaseNettyClient.kt`
- 修改：`basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/BaseNettyServer.kt`
- 修改：`basenetty/src/test/kotlin/com/leovp/basenetty/BaseNettyLifecycleTest.kt`
- 修改：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/socket/websocket/WebSocketClientActivity.kt`
- 修改：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/socket/websocket/WebSocketServerActivity.kt`

- [x] 先补失败测试：从未连接也释放、并发 release 单 owner、超时进入终态、handler 抛 `Error` 仍关闭底层资源。
- [x] 让 client/server 在 handler 的任意 `Throwable` 后仍完成 channel/EventLoop 清理，再传播原异常。
- [x] 保存 Demo 创建/连接 Job，销毁时先取消并等待；局部未发布客户端由创建协程释放。
- [x] Server 启动失败先释放实例再清空引用。

### Task 3：MediaCodec 失败终态、异步 flush 和 EOS

**文件：**
- 修改：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt`
- 修改：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecAsynchronous.kt`
- 修改：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodecSynchronous.kt`
- 修改：`audio/src/main/kotlin/com/leovp/audio/aac/AacStreamPlayer.kt`
- 修改：`audio/src/main/kotlin/com/leovp/audio/opus/OpusStreamPlayer.kt`
- 修改：对应 `audio/src/test` 测试。

- [x] 先补 FAILED 状态、flush/restart、旧代回调、EOS 单次和 callback 异常边界测试并确认失败。
- [x] 锁内执行状态转换和 flush 代际递增，锁外只通知一次 owner。
- [x] 异步 flush 后重新 start；旧代 buffer index 不再归还。
- [x] 流播放器接收 decoder 失败并进入现有单次 teardown。

### Task 4：OPUS 文件边界和分配上限

**文件：**
- 修改：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFramedFileReader.kt`
- 修改：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt`
- 修改：`audio/src/test/kotlin/com/leovp/audio/opus/OpusFramedFileReaderTest.kt`
- 增补：播放器级 JVM 回归测试及内部接缝。

- [x] 先补超大 payload 拒绝和尾随 start code 自然结束测试并确认失败。
- [x] 分配前按 OPUS codec 输入容量拒绝 payload。
- [x] 只把最终空 payload 当作合法边界，中间空帧仍失败。

### Task 5：AAC 文件播放器拥有完整资源生命周期

**文件：**
- 修改：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt`
- 新增或修改：AAC source、decoder session、audio output 内部接缝及测试。
- 修改：Demo 调用点、KDoc、`CHANGELOG.md`。

- [x] 先补非正 AudioTrack 写入的纯函数回归，确认旧实现没有失败判定接缝。
- [x] 将 codec decoder 改为播放器私有组合对象，不再公开继承的 `releaseAndJoin()`。
- [x] 保留原有单次终态屏障；非正写入改为异常并进入失败终态，不得走自然结束。

### Task 6：MicRecorder 单会话与不可取消释放

**文件：**
- 修改：`audio/src/main/kotlin/com/leovp/audio/MicRecorder.kt`
- 增补：`audio/src/test` 的生命周期回归测试。

- [x] 先补 lifecycle gate 的重复启动、停止后启动和单 stop owner 回归，并确认旧实现缺少接缝。
- [x] 使用共同生命周期锁原子发布唯一 record Job。
- [x] stop leader 在 `NonCancellable` 中完成停止、join、encoder/AudioRecord/effect 释放和完成屏障。

### Task 7：Demo 录音意图串行化

**文件：**
- 修改：`demo/src/main/kotlin/com/leovp/demo/basiccomponents/examples/audio/AudioActivity.kt`
- 增补：可 JVM 测试的录音意图协调器及测试。

- [x] 先补连续格式切换、权限结果晚到、销毁失效和同格式迟到失败测试并确认失败。
- [x] 通过 Mutex 和 generation 串行化录音意图；旧意图不得发布或清理新资源。
- [x] 文件打开、MicRecorder 构造和启动移到 IO；旧 recorder 完整停止后才创建新实例。

### Task 8：文档与全量复核

**文件：**
- 修改：`00-documents/2026-10-26-branch-review-part2-audio-netty_cc.md`
- 修改：`00-documents/2026-10-20-branch-review-vs-master_cc.md`
- 修改：`CHANGELOG.md`

- [x] 更新每条问题的实现状态、代码级修法、测试证据和剩余真机验证边界；定位使用符号名，避免再次固化易漂移行号。
- [x] 运行受影响模块测试、ktlint、detekt、Demo 集成编译和 `staticCheck --rerun-tasks`。
- [x] 重新审查完整 diff；发现明确问题继续按 RED→GREEN 修复，直至本轮无剩余明确源码问题。

### Task 9：独立复核播放器层证据与异常清理

**文件：**
- 修改：`audio/src/main/kotlin/com/leovp/audio/mediacodec/BaseMediaCodec.kt`
- 修改：`audio/src/main/kotlin/com/leovp/audio/aac/AacFilePlayer.kt`
- 修改：`audio/src/main/kotlin/com/leovp/audio/opus/OpusFilePlayer.kt`
- 新增：`audio/src/main/kotlin/com/leovp/audio/base/FileAudioOutput.kt`
- 修改：对应 `audio/src/test` 测试与实施文档、`CHANGELOG.md`。

- [x] 为异步四类 callback 和同步 EOS 补齐 `CancellationException` 失败终态回归。
- [x] 先用失败测试证明 `BaseMediaCodec.start()` 遇到 `Error` 会跳过 native release，再让任意
  `Throwable` 进入同一启动回滚并重新抛出。
- [x] 加入仅对模块内部可见的 source/decoder/audio-output 接缝，完成 AAC/OPUS 播放器层一次性终态、
  尾随 start code、阻塞写入和队列溢出测试。
- [x] 先用失败测试证明 AAC/OPUS 初始化 `Error` 会泄漏已发布资源，再统一清理并重新抛出。
- [x] 把 stop、worker join、decoder/source/output release 改为逐步执行并聚合失败，验证 stop 异常不会
  短路后续释放。
- [x] 重跑受影响模块测试、ktlint、detekt、Demo 集成编译和全仓 `staticCheck --rerun-tasks`，记录本次
  新代码的最终结果。

### Task 10：关闭 H-1 的完整会话隔离缺口

- [x] 用失败回归证明旧 flush/start 没有替换 codec，也无法按 native 会话拒绝迟到回调。
- [x] 异步 flush 退休旧 codec 后重建新 codec，callback 注册时绑定不可变会话代号。
- [x] 基类用 `detachCodec()` 转移所有权，确保退休 codec 不会在最终 teardown 重复释放。
- [x] callback 失败在同一把锁内核对代号并转换状态，在锁外通知 owner。
- [x] 增加连续 reset、晚到四类 callback、新 callback 正常处理、失败聚合和并发 release 回归。
- [x] 更新 KDoc、CHANGELOG、审查状态和代码级实施记录，说明重建成本及子类迁移。
- [x] 运行最终 audio 回归/质量检查、Demo 集成构建和全仓 staticCheck，记录当前代码结果。
