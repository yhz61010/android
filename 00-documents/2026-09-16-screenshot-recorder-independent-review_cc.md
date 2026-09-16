# Screenshot 录制器与 Falcon 截图独立复审（2026-09-16）

## 1. 本文定位

`2026-09-02-audio-media-teardown-followup-fixes_cc.md` 的 **§12** 已经记录了第五至第七轮的修改与真机整改，那是**实施方的自述**。本文是对同一批提交的**独立复审**，目的有三：

1. 找出 §12 未覆盖的缺陷
2. 指出 §12 中与当前源码不符的声明
3. 对 §12 已列为"已知未处理"的问题重新评估严重度

与 §12 冲突时，以本文列出的 `file:line` 证据和当前源码为准。

## 2. 审查范围与方法

- **提交范围**：`e3a3911d~1..08b122e6f`，共 8 个提交

| 提交 | 标题 | 是否在代理审查范围内 |
|------|------|--------------------|
| `e3a3911d9` | fix: correct OPUS EOS timing and release init-only recorder resources | 是 |
| `4bf5f4cf3` | fix(screencapture): open release barrier only after teardown finishes | 是 |
| `f86b2682a` | fix(screencapture): fold recorder init into the lifecycle protocol | 是 |
| `ba3424a98` | fix(demo): allow a new recording after the screenshot recorder is released | 是 |
| `a1d04f27f` | fix(screencapture): skip detached windows when taking a screenshot | 是 |
| `878ed3b4a` | fix(demo): stop truncating the recording that just finished | 是 |
| `6d2088879` | fix(screencapture): correct timeout duration usage and logging level | **否** |
| `08b122e6f` | docs: record fifth to seventh review rounds and device findings | **否**（纯文档，即 §12） |

- **方法**：4 个并行分域代理独立审查（录制器生命周期状态机 / EGL 与线程亲和性 / Demo 调用侧 / Falcon + OPUS），主审人逐条回读源码复核并二次裁定
- **范围偏差说明**：代理启动时 HEAD 为 `878ed3b4a`，审查期间仓库前进到 `08b122e6f`。主审人的全部复核读的是最新源码。`6d2088879` 只改了 `withTimeoutOrNull` 的时长单位写法（`RELEASE_TIMEOUT_MS` → `RELEASE_TIMEOUT_MS.milliseconds`，语义等价）和 `Falcon.kt:238` 一行日志级别（ERROR → INFO），**不影响本文任何结论**
- **约束**：按协作约定未在审查环境运行任何 Gradle 任务，全部为静态源码分析

## 3. 结论摘要

**总体结论：BLOCK。** 6 个 HIGH、8 个 MEDIUM、3 条项目规则违反、2 处 §12 声明与代码不符。

| 域 | 结论 | HIGH | MEDIUM | §12 是否已覆盖 |
|----|------|------|--------|---------------|
| Demo 调用侧 | **BLOCK** | 2（A1、A2） | 0 | 否 |
| Falcon 截图 | **BLOCK** | 1（A3） | 2（M3、M7） | A3 已列为"已知未处理" |
| 录制器生命周期 / EGL | **BLOCK** | 3（B1、B2、B3） | 5 | 否，且 B3 与 §12 声明矛盾 |
| OPUS 文件播放器 | WARN | 0 | 1（M1） | 部分，§12.1 的推理有缺口 |

### 3.1 §12 中与当前源码不符的两处声明

这两处最值得优先处理，因为它们会让后续维护者以为问题已经关闭。

| §12 原文 | 实际代码 | 对应缺陷 |
|---------|---------|---------|
| §12.3：「`onInit()` 的 `finally` 保证中途失败也会释放已创建部分」；§12.1 P2：「释放流程对 null 与未初始化状态全程防护，因此 `onInit()` 中途抛异常的部分初始化场景一并覆盖」 | `Screenshot2H26xStrategy.kt:411-421` 的 `finally` 是 `if (endInit()) completeInlineRelease(...)`，而 `endInit()`（`:439-444`）返回的是 `teardownDeferredToInit`——**只有并发释放被转交时才为 true**。无并发释放时初始化失败**什么都不释放** | **B3** |
| §12.4 Bug 2：采集阶段跳过未 attach 的 root「顺带避免其失效边界影响位图尺寸计算」 | 该场景下 `mAttachInfo == null` 使 `getLocationOnScreen()` 返回 (0,0) 且 `width`/`height` 为 0，本就贡献 `(0,0,0,0)` 的 Rect，对尺寸零影响；采集阶段过滤反而引入新的尺寸风险 | **M7** |

### 3.2 本轮做对的部分

- **`e3a3911d9` 的 OPUS EOS 计时锚点修正是真 bug 的正确修复**。原来 3 秒从播放 job 启动即计时，任何超过 3 秒的音频都会在正常解码途中被误判失败
- **`878ed3b4a` 把输出流打开时机挪到开始录制**是对的，打开即截断，放在武装阶段会清零刚录完的文件
- **`a1d04f27f` 的绘制阶段过滤与单窗口异常隔离是对的**：绘制跑在主线程，`isAttachedToWindow` 在那里判定可靠
- **§11.4 把 M39 下调为「部分缓解 / 未闭环」、§12.7 主动记录「已知未处理」**，这种诚实标注比假装闭环有价值，建议保持

### 3.3 两个提交的目标未达成

| 提交 | 声称 | 实际 |
|------|------|------|
| `878ed3b4a` | "every finished recording ended up zero bytes" 已修 | 仅覆盖「停止 → 重新武装」路径；配置变更（旋转 / 语言切换 `recreate()`）路径上截断依旧，并叠加跨实例交叉写入（**A1**） |
| `a1d04f27f` | 窗口增删时整帧丢失已修 | 修的是 `AttachInfo` 为 null 导致绘制抛异常这一条；**丢帧主因 `mRoots`/`mParams` 双快照错位未触及**（**A3**，§12.7 自己也承认），且采集阶段的过滤带来新的尺寸风险（**M7**） |

## 4. HIGH 级缺陷

### 4.1 A 类：当前代码即可触发

#### A1（HIGH，§12 未覆盖）配置变更会自动开始录制并损坏输出文件

**位置**：`demo/.../RecordSingleAppScreenActivity.kt:111-117`、`:149-160`

**前提（均已核实）**：

- `demo/src/main/AndroidManifest.xml:266` 该 Activity **未声明 `android:configChanges`**（对比 `:270-272` 的 `ScreenShareClientActivity` 声明了 `keyboardHidden|orientation|screenSize`），旋转会真的走 destroy/recreate
- `activity_screenshot_record_h264.xml:10` 的 `toggleBtn` 有 `android:id` 且未设 `saveEnabled="false"`，`CompoundButton` 的 checked 状态会被保存并恢复
- 监听器在 `onCreate:111` 注册，早于视图状态恢复
- `BaseDemonstrationActivity.kt:60` 的语言切换 `recreate()` 是同一条路径

**失效场景**：

1. 点击开始，录制若干秒（`isChecked == true`）
2. 旋转屏幕。旧实例 `onDestroy()` → `releaseRecorder(restartable = false)`，`withContext(IO)` 挂起后 `onDestroy` 立即返回，`closeVideoOutput()` 的 flush/close 仍在 IO 线程排队
3. 新实例 `onCreate` 注册监听器 → 视图状态恢复把 `isChecked` 置回 true → **回调触发 `startRecording()`** → `openVideoOutput()` 打开同一个 `screen.h265`

**结果**：用户从未点击开始却自动开始了第二次录制；刚录完的文件被截断到 0；旧实例的 `outputLock` 是实例字段，与新实例不是同一把锁，旧实例仍在 flush 的缓冲会被写进新文件的任意偏移，产出两路编码器码流交错的不可解码文件。

**建议修法**：注册监听器前 `binding.toggleBtn.isSaveEnabled = false`（或布局里 `android:saveEnabled="false"`）；输出文件名带时间戳，从根上消除两个实例争抢同一路径。

#### A2（HIGH，§12 未覆盖）释放超时后仍重新武装，被放弃的 recorder 写入下一次录制的流

**位置**：`RecordSingleAppScreenActivity.kt:56`、`:140`、`:190`、`:212-220`

`awaitRecorderRelease()` 拿到 `released` 后**只记日志，返回类型是 `Unit`**；`releaseRecorder():190` 的 `if (restartable) armNextRecording()` 因此无条件执行，超时与否一视同仁。而 `screenDataListener` 是 `:56` 的**单个实例字段**，`:140` 传给每一个 `createRecorder()`。

**失效场景**：点击停止 → 录制线程卡在原生调用（`MediaCodec.stop()` / EGL 释放 / `HandlerThread.join()`，即 §11.4 与 §12.1 P2 标注「未闭环」的场景）→ `withTimeoutOrNull` 10 秒后放弃 → 关流、重新武装、toggle 恢复可用 → 用户开始第二次录制 → 卡住的 recorder 此刻返回，其 `onDataUpdate` 把残余帧写进第二次录制的流。次生影响：旧 recorder 最终的 `onError` 会执行 `:88` 的 `isChecked = false`，把**正在进行的**第二次录制误停。

§12.1 P2 已经识别到"卡住的线程仍能通过 `screenDataListener` 到达 Activity"，并采取了弱引用等收窄措施，但**没有意识到这条引用还会造成跨会话数据串流**。代码自己的 KDoc（`:206-210`）复述了同一个事实，紧接着仍无条件重新武装。

**建议修法**：`awaitRecorderRelease()` 返回释放结果，超时时**不要** `armNextRecording()`，保持 toggle 禁用并提示；`screenDataListener` 改为每会话独立实例，或携带 session 序号，回调先比对当前会话号再处理。

#### A3（HIGH，§12.7 已列为「已知未处理」，建议提级）`Falcon` 的 `mRoots`/`mParams` 双快照错位

**位置**：`Falcon.kt:209-216`、`:255`

§12.7 已准确描述了这个问题及其两种表现（下标越界导致偶发丢帧、LayoutParams 错配影响 dim 与偏移），此处不重复推导，只补充两点评估：

1. **它是 `a1d04f27f` 想解决的那个症状的主因**，而该提交没有触及它。把它留在"已知未处理"意味着提交的目标其实没有达成
2. 补充一条 §12.7 未提及的同源失效：`:214` 的 `toTypedArray()` 在读取一个**活的** `ArrayList`，主线程同时增删会让快照与 `mParams` 错位，同样被 `CaptureUtil.kt:82` 吞成 `null` 帧

   > **勘误（见主文档 §14.4）**：本条原写作"会抛 `ConcurrentModificationException`"，不成立。`ArrayList.toArray()` 走 `Arrays.copyOf`，不经迭代器，不抛 CME。真实风险是 size 在两次读取之间缩小，导致数组尾部出现 null 或两份快照下标错位。结论与修复方向不变，仅失效机制的描述有误。

录制循环跑在 `recordingDispatcher` 专用线程（`Screenshot2H26xStrategy.kt:549` 调 `CaptureUtil.takeScreenshot`），不是主线程，因此这条竞态在录制期间持续存在。

**建议修法**：把整个 `getRootViews()` 挪进 `drawRootsToBitmapOtherThread()` 的 `runOnUiThread` 块内，采集与绘制在同一次主线程消息内完成——这同时让 `:247` 的判定变可靠，并使 M7 的问题一并消失。若不改结构，至少反射取出 `mLock` 后在同一临界区拷贝两个列表，并在 `:255` 用 `params.getOrNull(i) ?: continue` 兜底。

### 4.2 B 类：仓库内不可达，影响下游库消费者

全仓库核查：唯一使用 `BY_IMAGE_2_H26X` 的是 demo，它只调 `startRecord()`，从不单独调 `onInit()`；`MediaProjectionService` 用的是 `BY_MEDIA_CODEC` 的另一个策略。因此以下三条对本仓库是潜在风险，对 JitPack 下游消费者才是现实风险——而 `e3a3911d9` / `f86b2682a` 新增 init-only 支持的意图，恰恰就是服务这类消费者。

#### B1（HIGH，§12 未覆盖）`onInit()` 可重入，双初始化泄漏整套原生资源

**位置**：`Screenshot2H26xStrategy.kt:428-436`、`:535`

`beginInit()` 只检查 `releaseRequested || teardownClaimed || releaseCompleted.isCompleted`，**没有 initialized 守卫**（对比 `startRecord():526` 就有 `recordingStarted` 闩）；而 `startRecord():535` 内部又会调用 `onInit()`，其 KDoc（`:518-523`）没有说明这一点。

**失效场景**：调用方按 `ScreenProcessor` 接口契约在主线程 `onInit()`，随后调用本类特有的 `startRecord(act)`：

- `:434` 覆盖 `eglOwner`，主线程上那份仍 current 的 EGLContext/EGLSurface/EGLDisplay 从此无任何释放路径
- `:491` 覆盖 `h26xEncoder`，第一份 codec 永不 `stop()`/`release()`
- `:291` 覆盖 `surface`，第一份 input Surface 泄漏
- `:631` 覆盖 `screenshotThread`，第一条 `scr-rec-send` 线程永不 `quitSafely()`

而 `releaseAndJoin()` 仍会报告"全部释放完成"。

**建议修法**：`beginInit()` 内在 `lifecycleLock` 下加 `initStarted` 闩；`startRecord()` 在已初始化时跳过 `onInit()`。

#### B2（HIGH，§12 未覆盖；由 §12.2/§12.3 的修复方案引入）inline teardown 在主线程执行无界阻塞拆除

**位置**：`Screenshot2H26xStrategy.kt:434`、`:682-687`、`:639`

`ScreenProcessor.kt:36-38` 明确要求 `onInit()` 在主线程调用，`beginInit():434` 于是把主线程记为 `eglOwner`。§12.2 为修正 EGL 线程归属而引入的"不在 owner 线程则 post 回去"方案，在 owner 恰好是主线程时，会把整套拆除搬到主线程：`MediaCodec.stop()`（厂商实现上常见 100ms~数秒阻塞）、`MediaCodec.release()`、`releaseEgl()`，最后 `releaseHandlerAndJoin():635-644` 里是**无超时的 `screenshotThread.join()`**。而 `quitSafely()` 语义要求先排干队列中全部积压消息，这些消息执行的是调用方的 `onDataUpdate`（在 `MediaProjectionService` 里是网络发送）。对端卡住即主线程无限阻塞 → ANR。

若调用方本身就在主线程调用 `onRelease()`（`Activity.onDestroy()` 里直调，完全合法），`:682-683` 分支会同步在主线程做同样的事。

**建议修法**：把 owner 语义收敛为"EGL 必须建在 `recordingDispatcher` 上"——`onInit()` 若发现当前不在 `recordingExecutor` 线程，则把 `initResources()` 整体提交到该线程执行并等待。这样 `eglOwner` 恒为 `screenshot-h26x-egl`，`dispatchInlineRelease` 直接 `recordingExecutor.execute{}` 即可，主线程只 await 屏障。至少也要给 `join()` 加超时，并禁止在主 Looper 上执行 `completeInlineRelease`。

#### B3（HIGH，**与 §12.3 声明矛盾**）`onInit()` 初始化失败不释放已创建的资源

**位置**：`Screenshot2H26xStrategy.kt:411-421`、`:439-444`、`:491`、`:495`

```kotlin
override fun onInit() {
    beginInit()
    try {
        initResources()
    } finally {
        if (endInit()) completeInlineRelease(stopEncoder = encoderStarted.get())
    }
}
```

`endInit()`（`:439-444`）返回的是 `teardownDeferredToInit`，**只有并发释放被转交给本线程时才为 true**。外部直调 `onInit()` 且无并发释放时，若 `encoder.configure()`（`:492`）或 `eglCreateWindowSurface()`（`:358`）抛出，`endInit()` 返回 false → 异常直接上抛，`h26xEncoder`、`surface`、已 `eglInitialize` 的 display 与已创建的 context 全部保持存活。调用方按 `build().apply { onInit(); onStart() }` 写法此时连引用都没拿到，**不可能再调用 `onRelease()`** → native codec 与 EGLDisplay 永久泄漏，重试几次即撞上 codec 实例上限。

协程路径由 `:569-570` 的 `finally` 兜住，所以这个洞只在公开 API 路径上——而这正是 §12.1 P2 当初要修的那条路径。

**建议修法**：

```kotlin
try { initResources() }
catch (t: Throwable) { runCatching { releaseOwnedResources(stopEncoder = false) }; throw t }
finally { if (endInit()) completeInlineRelease(...) }
```

`releaseOwnedResources` 全程空值安全且幂等，与协程 `finally` 的二次调用不冲突。同时应修正 §12.1 P2 与 §12.3 中"中途失败一并覆盖"的表述。

## 5. MEDIUM 级缺陷

| # | 位置 | 问题 | §12 |
|---|------|------|-----|
| M1 | `OpusFilePlayer.kt:253`、`:256-260`、`:329` | **input EOS 之前完全没有看门狗**。`inputEosSubmitted.await()` 无上限；`:253` 只在成功路径 complete，catch 分支不 complete。§12.1 P1 称"终态清理会取消 `playbackScopeJob`，喂帧提前失败时该 `await()` 不会悬挂"——这只对生产者**抛异常**成立，对生产者**活着但卡住**无效：若 `consumeDecodedPcm` 阻塞在 `audioTrackPlayer.write()`（音频路由切换 / HAL offload 卡顿）→ PCM 队列不降 → 生产者永远卡在 `awaitDecodedQueueCapacity()` → `:253` 永不执行 → MediaCodec、AudioTrack、`RandomAccessFile` 全部无限期持有，两个回调都不触发，也无日志。修法**不能退回时长型超时**（会重新引入原 bug），应加停滞检测：在重试循环里记录上次有进展的时刻，连续无进展超过阈值即 `requestFailure` | 推理有缺口 |
| M2 | `Screenshot2H26xStrategy.kt:149-193` | **MediaCodec 回调无 try/catch**。整段 `synchronized` 内无异常处理，`codec.getOutputBuffer()` 在 codec 进入 Error 态时抛出会直接逃逸到回调线程 → 进程崩溃；`releaseOutputBuffer` 也不在 `finally`，中途抛异常会泄漏 buffer index。§10.3 的同类加固只做在了 audio 的 codec 基类上。**本批之前就存在**（自 `93c1c3c0f`） | 未覆盖 |
| M3 | `Falcon.kt:129-138` | **`latch.await()` 无超时**，且 `CountDownLatch` 不是协程可取消点。录制协程卡在 `Screenshot2H26xStrategy.kt:549` 内等主线程绘制，`releaseAndJoin()` 的 10 秒超时只能结束等待、无法中断它。与 B2 组合会形成"主线程等 join / 录制线程等主线程"的双向阻塞。该 Runnable 还通过 `viewRoots` 强引用一批 View → Activity。**属既有问题**，但 `a1d04f27f` 的全部前提就是"绘制在主线程更可靠"，需一并处理 | 未覆盖 |
| M4 | `Screenshot2H26xStrategy.kt:635-644`、`:690-695` | **自我 join 死锁**。若 `onDataUpdate`（运行在 `scr-rec-send`）内调用 `onStop()`，降级分支会让 `completeInlineRelease` 跑在该线程自己身上 → `:639` 等待自身退出，永久阻塞，`releaseCompleted` 永不完成 | 未覆盖 |
| M5 | `Screenshot2H26xStrategy.kt:687` | **`handler.post` 成功但目标 Looper 随后 quit** → `completeInlineRelease` 永不执行 → 屏障永不完成 → `releaseAndJoin()` 永久挂起。代码只处理了 `post` 返回 false 的情形，没处理"入队成功但不会被执行"。Demo 靠 10 秒超时兜底，库调用方没有 | 未覆盖 |
| M6 | `Screenshot2H26xStrategy.kt:719-725` | **停止 encoder 前没有 `signalEndOfInputStream()`**。pipeline 中尚未输出的帧被直接丢弃，`ScreenDataListener` 永远收不到 `BUFFER_FLAG_END_OF_STREAM`，下游只能靠连接断开推断流结束。grafika / bigflake 的 surface-input 编码器在 `stop()` 前都会先 signal 并排干最后一次 drain | 未覆盖 |
| M7 | `Falcon.kt:244-249` | **采集阶段的新过滤只有坏处**（与 §12.4 Bug 2 的收益声明矛盾，见 §3.1）。反向风险：跨线程读到 `mAttachInfo` 已置空而 `mView` 尚未置空的中间态时，Activity 根窗口被剔除，只剩同进程悬浮窗 → 位图变成悬浮窗尺寸 → `encodeImages()` 固定按 `builder.width/height` 绘制，这张图被**拉伸铺满整帧**编码进去。改动前坏帧表现为丢一帧（肉眼无感），改动后变成一帧全屏失真画面，在视频流里因参考帧会持续可见。**建议删掉 `:244-249`，只保留 `:161` 的绘制阶段过滤**；若按 A3 的建议把采集挪进主线程，此问题自动消失 | 声明矛盾 |
| M8 | `Screenshot2H26xStrategy.kt:201-208`、`:571-578` | **init-only 路径上 codec 异步失败被静默丢弃**。`mediaCodecCallback.onError` 写入 `recordingFailure` 并把 `isRecording` 置 false，但唯一读取点在协程体 `finally` 内。`recordingJob == null` 时无人读取、不触发拆除、不回调 `onError`，与 `ScreenDataListener.onError` 的 KDoc "Called after a screen processor fails asynchronously and releases its owned resources" 直接矛盾 | 未覆盖 |

## 6. 项目规则违反

零容忍策略（detekt `maxIssues = 0`）下单列。

| 规则 | 违反点 | 说明 |
|------|--------|------|
| `.claude/rules/kotlin/coding-style.md:77`<br>"Never catch `CancellationException` — always rethrow it" | `Screenshot2H26xStrategy.kt:564-565` | 捕获后未重抛会隐藏主动抛出的取消信号；若 Job 已被外部 `cancel()`，吞掉异常不会将其 `isCancelled` 恢复为 false。应继续重抛以保留取消语义，不能用 `join()` 返回来证明执行成功；`:565` 写入的 `CancellationException` 又在 `:572` 被过滤掉，是纯死存储。**本批之前就存在** |
| `.claude/rules/kotlin/coding-style.md:31`<br>"Never use `!!`" | `Falcon.kt:207` | `getFieldValue("mGlobal", ...)!!`。`targetSdk = 36` 下 `WindowManagerImpl.mGlobal` / `WindowManagerGlobal.mRoots` / `mParams` 均受非 SDK 接口限制约束，一旦被拦截，`getFieldValue()` 的 `runCatching` 把失败吞成 null → `!!` 直接 NPE → 每帧都丢，而日志里只有一句含糊的 "Unable to take screenshot to bitmap"，没有任何线索指向反射被拦截 |
| 接口契约自相矛盾（§11.2 M38 仍未闭环） | `ScreenProcessor.kt:36-38`、`:42-44` vs `Screenshot2H26xStrategy.kt:404-406`、`:535`、`:609-620` | 接口要求 `onInit()` 在主线程，而本类 `:535` 自己在 `screenshot-h26x-egl` 线程调用它，`:404-406` 的新 KDoc 又称"调用线程成为 EGL owner"（暗示任意线程均可）——owner 线程的选择正是 B2 的成因。接口 `:42-44` 仍写着 "once do `onRelease()` ... Please call `onInit()` then `onStart()`"，而新代码对这种调用恰恰抛异常 |

### 6.1 `onInit()` 抛 `CancellationException` 是危险的类型选择

**位置**：`Screenshot2H26xStrategy.kt:408`（KDoc 明示）、`:411`、`:430-431`；§12.3 与 §12.6 把它记为有意的行为变更。

`kotlinx.coroutines.CancellationException` 在 JVM 上就是 `java.util.concurrent.CancellationException`。
这里的风险是把普通生命周期错误编码成协程取消，而不是方法是否挂起、是否运行在主线程：

1. 子协程中未捕获的 `CancellationException` 通常只取消该子协程及其子任务，**不会自动取消父协程或兄弟协程**。普通非取消异常的传播规则不同，还要区分普通 Job 与 SupervisorJob。
2. `runCatching` 会捕获 `CancellationException`；需要保留结构化取消的调用链应重抛。项目的 `runCatchingPreservingCancellation` 用于这一目的，但不能据此断言所有同步 `runCatching { onInit() }` 用法都必然错误。
3. 主线程可以运行协程（例如 `Dispatchers.Main`、`lifecycleScope`），因此“主线程上没有协程可取消”不成立。接口应以错误含义选择异常类型：已释放等生命周期误用适合 `IllegalStateException`，内部录制任务收到释放请求则可以使用取消信号。

依据：[Kotlin 协程异常与取消说明](https://kotlinlang.org/docs/exception-handling.html#cancellation-and-exceptions)。

**建议**：对外改抛 `IllegalStateException`，或定义一个**不继承 `CancellationException`** 的 `RecorderReleasedException`。内部协程路径若需把释放请求按取消处理，另用私有哨兵类型，不要暴露到公开接口上。

## 7. 修复优先级

1. **A1、A2** —— Demo 文件损坏，当前即可触发
   - `toggleBtn.isSaveEnabled = false`
   - `awaitRecorderRelease()` 返回释放结果，超时则不重新武装
   - `screenDataListener` 改为每会话独立实例或带 session 序号
   - 输出文件名带时间戳
2. **A3 + M7** —— Falcon，`a1d04f27f` 的目标未达成
   - `getRootViews()` 整体挪进主线程的 `runOnUiThread`，采集与绘制在同一消息内完成
   - 删掉采集阶段的 `isAttachedToWindow` 过滤
3. **B3 + 修正 §12 表述** —— `onInit()` 的 catch 里补 `releaseOwnedResources`，并订正 §12.1 P2 / §12.3 中"中途失败一并覆盖"的说法
4. **B1** —— 一行守卫：`beginInit()` 加 `initStarted` 闩，`startRecord()` 已初始化时跳过 `onInit()`
5. **B2 + M4 + M5** —— EGL owner 收敛为恒定的 `recordingDispatcher`；`join()` 加超时；禁止在主 Looper 上执行拆除
6. **M1** —— OPUS 加停滞检测，恢复看门狗（不要退回时长型超时）
7. **M2** —— MediaCodec 回调包 `runCatching`，`releaseOutputBuffer` 移入 `finally`
8. **M3、M6、M8 与第 6 节的规则违反** —— 随上述批次顺带处理

## 8. 未验证项

- **本轮 8 个提交中的代码改动未在审查环境编译或运行测试**。§12.7 记录用户本地已跑通 65 条单测与 `ktlintCheck`/`detekt`，但那是本文所列缺陷修复**之前**的状态
- `screencapture` 模块**没有任何针对 `Screenshot2H26xStrategy` 的单元测试**（`screencapture/src/test` 下无对应用例）。B1、B2、B3 需要真机配合 init-only 调用序列才能验证
- A1 可在真机上直接复现：录制中旋转屏幕，观察是否自动重新开始录制、文件是否被清零
- A3 需要在录制过程中反复增删窗口（Dialog、悬浮窗）才能复现，属时序竞态，建议用高频增删的压力脚本触发
- M1 需要在长音频自然播放过程中切换音频路由（插拔耳机、切蓝牙）来构造 `AudioTrack.write()` 阻塞
- §12.7 列出的五项待真机验证仍未消化；§8.6 与 §11.6 的既有欠账同样未动

---

# 9. 修复记录（2026-09-16）

第 4～6 节列出的全部缺陷已在本分支修复，改动涉及 5 个文件、+399/-142。修复后又经一轮独立验证代理复核（同样为静态分析，未编译），其发现的 4 个 MEDIUM、6 个 LOW 中，成本低且有实际价值的已一并处理。

## 9.1 缺陷关闭一览

| 缺陷 | 修法 | 位置 |
|------|------|------|
| **A1** | `toggleBtn.isSaveEnabled = false`，录制状态不再跨 `recreate()` 自动复活；调试文件名加时间戳，消除两个实例争抢同一路径 | `RecordSingleAppScreenActivity.kt` |
| **A2** | `awaitRecorderRelease()` 改为返回 `Boolean`，超时不再 `armNextRecording()`；`screenDataListener` 改为按 session 号生成，`onDataUpdate` 与 `onError` 都带会话守卫；超时时 `activeSession` 自增，令被放弃的 recorder 立即失效 | 同上 |
| **A3** | 采集、测量、绘制合并进**同一次主线程消息**（新增 `captureOnMainThread`），跨线程读 `mRoots`/`mParams` 的窗口消失；另在 `viewRootData` 加 `params.getOrNull(i) ?: continue` 兜底，保护仍公开的 `getRootViews()` | `Falcon.kt` |
| **B1** | 新增 `initStarted` 闩与 `InitOutcome` 三态；`startRecord()` 与公开 `onInit()` 共用 `allocateResourcesOnce()`，第二次初始化返回 `ALREADY_INITIALIZED` 而不再重建资源 | `Screenshot2H26xStrategy.kt` |
| **B2** | 删除 `EglOwner` 与 `Handler.post` 机制，EGL owner **恒为** `recordingExecutor` 线程；新增 `runOnEglThread` 作为唯一入口，拆除改由 executor 派发 | 同上 |
| **B3** | `allocateOnEglThread()` 的 `catch` 中调用 `releaseOwnedResources(stopEncoder = false)` 并以 `addSuppressed` 保留原异常后重抛 | 同上 |
| **M1** | 新增 `awaitInputEosWithStallDetection()`，以 `queuedPcmCount + consumedPcmCount` 为进度指标做停滞检测（5 秒无进展判失败），而非时长超时；生产者异常时 `completeExceptionally` 并在等待结束后立即抛出 | `OpusFilePlayer.kt` |
| **M2** | 拆出 `deliverOutput()` 并包 `try/catch`，`releaseOutputBuffer` 移入 `finally` 且再包 `runCatching`；顺带把原先 `when (flags)` 的全等比较改为位掩码判断 | `Screenshot2H26xStrategy.kt` |
| **M3** | `latch.await(1000ms)`；位图改由主线程那一次 turn 创建，超时后调用方与主线程完全脱钩 | `Falcon.kt` |
| **M4** | `releaseHandlerAndJoin()` 检测自我 join，命中时改 `quit()` 直接返回；`join` 加 2 秒上限 | `Screenshot2H26xStrategy.kt` |
| **M5** | 改用 `recordingExecutor.execute`（shutdown 后已入队任务仍会执行），并处理 `RejectedExecutionException` | 同上 |
| **M6** | 新增 `drainEncoder()`：在 detach **之前** `signalEndOfInputStream()` 并等待 `encoderEos` 最多 500ms | 同上 |
| **M7** | 删除采集阶段的 `isAttachedToWindow` 过滤，只保留绘制阶段检查 | `Falcon.kt` |
| **M8** | `mediaCodecCallback.onError` 在无 recording job 时触发 `requestRelease()`，由 `completeInlineRelease` 统一经 `reportFailureIfAny()` 上报 | `Screenshot2H26xStrategy.kt` |
| **规则 1** | 补 `throw e`；并删除对 `recordingFailure` 写入 `CancellationException` 的死存储（它会挡住后续真实失败的上报） | 同上 |
| **规则 2** | `!!` 改 `checkNotNull`，错误消息指明可能是非 SDK 接口限制所致 | `Falcon.kt` |
| **规则 3** | 接口 KDoc 改为"实现方可收紧契约；一次性实现的 `onStop()` 等同 `onRelease()`" | `ScreenProcessor.kt` |
| **§6.1** | 公开 `onInit()` 改抛 `IllegalStateException`；`ReleaseRequestedException` 退回为仅内部协程路径使用 | `Screenshot2H26xStrategy.kt` |

顺带修复（报告中提及但未单列）：`frameCount` 改 `AtomicLong`；`screenshotHandler` 由 `lateinit` 改为可空 `@Volatile`；`startEncoder()` 加 `checkNotNull`，不再在未初始化时静默成功。

## 9.2 验证轮补充修复

| 发现 | 处理 |
|------|------|
| `runOnEglThread` 的 `task.get()` 无超时，与 Falcon 的主线程等待可构成互等 | 改为 `get(5s)`，超时 `cancel` 并抛 `IllegalStateException` |
| demo 的 `onError` 缺会话守卫，过期 recorder 能停掉进行中的录制 | 补守卫 |
| `RejectedExecutionException` 兜底分支可能落在主线程并执行 `drainEncoder` + `join` | 该分支改为 `stopEncoder = false`，跳过所有有界等待 |
| 删除 `EglOwner` 后遗留的孤儿 KDoc | 删除 |

## 9.3 行为变更

| 位置 | 变更 |
|------|------|
| `Screenshot2H26xStrategy.onInit()` | 对已释放实例抛 `IllegalStateException`（此前为 `CancellationException`）；重复调用变为无操作而非重建资源 |
| `Screenshot2H26xStrategy.onInit()` | **EGL 初始化不再发生在调用线程**：提交到内部 EGL 线程执行并阻塞等待（最长 5 秒）。调用方不得与 `startRecord()` 并发调用 |
| `Screenshot2H26xStrategy` 停止路径 | 停止前会发送 `signalEndOfInputStream()` 并等待 EOS 最多 500ms，`ScreenDataListener` 现在能收到 `BUFFER_FLAG_END_OF_STREAM` |
| `Falcon` | 截图的全部工作（含位图分配）改在主线程完成，超时 1 秒后放弃该帧 |
| demo 录屏页 | 调试文件名带时间戳，不再覆盖上一段录制；释放超时后禁用开关而非武装新会话 |

## 9.4 已知取舍

**`Falcon` 把位图分配移到主线程**：这是关闭 A3 必须付出的代价。录制循环约 30fps，意味着主线程每秒新增约 30 次全屏位图分配（1080p RGB_565 = 1920×1080×2 ≈ **3.96 MiB/帧**，约 119 MB/s）。若真机验证发现主线程抖动，可在尺寸不变时复用位图（`eraseColor()` 而非每帧新建），但这会引入位图所有权问题，本轮未做。

> **勘误（见主文档 §14.4）**：本段原写作"约 1.6 MB/帧"，算错了（少算一半还多）。真实值为 3.96 MiB/帧。另外本段**遗漏了同批搬上主线程的另一项开销**：`findField()` 每帧都调 `Class.getDeclaredFields()`，其代价数量级高于位图分配，已在 §14.1 修复。

## 9.5 验证状态

**本轮修复未编译、未跑测试、未真机验证。** 验证代理的静态核查未发现编译阻断项，但以下两处构造建议本地首先确认：

- `runOnEglThread` 中 `FutureTask<T> { action() }` 的 SAM 转换
- `allocateOnEglThread` 中 `.onFailure(t::addSuppressed)` 的扩展函数绑定引用（若报错，改为 `.onFailure { t.addSuppressed(it) }`）

需要本地执行：

```bash
./gradlew --continue :screencapture:compileDebugKotlin :audio:testDebugUnitTest \
  :screencapture:detekt :screencapture:ktlintCheck \
  :audio:detekt :audio:ktlintCheck \
  :demo:ktlintCheck :demo:compileDevDebugKotlin
```

真机验证项在第 8 节基础上新增：

- `onInit()` 在主线程调用时不再卡顿，且 EGL 资源确实在内部线程创建与销毁
- 停止录制时下游能收到 `BUFFER_FLAG_END_OF_STREAM`
- 录制中旋转屏幕：不应自动开始录制，上一段文件应完整可播放
- 录制中反复增删窗口（Dialog、悬浮窗），确认丢帧率下降且无画面错位

## 9.6 后续

本文 §9 记录的修复随后又经过两轮复审，结果统一记录在
`2026-09-02-audio-media-teardown-followup-fixes_cc.md` 的 §13：§13.4 为对 §9 修复的复审修正，
§13.8 为对整改的第二次复审，其中 B4（`onInit()` 失败后 EGL 执行器线程钉住录制器）由 §9 的 B2
修复引入并已修正。两份文档如有出入，以主文档 §13 为准。


## 9.7 初始化中断与输出失败补充整改（2026-09-16）

后续 Codex 审查在 `6f6313f5d` 上确认两项遗漏：等待初始化的线程中断后，EGL 任务仍可继续分配
资源；输出回调的 catch 没有处理“未创建录制协程”的释放与错误通知。现已分别补上中断后的释放
责任移交，以及退出回调锁后的统一释放请求。三个回归用例先在修复前失败，再用于验证修复。
详细改动、验证命令与真机边界见主文档 §15。

同时更正本篇 §6 与 §6.1 的取消语义：外部取消后的 Job 不会因吞异常而自动恢复；子协程的
`CancellationException` 通常不取消父协程或兄弟协程；主线程同样可以运行协程。
