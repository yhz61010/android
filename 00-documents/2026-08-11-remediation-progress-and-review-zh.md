# 八模块整改 —— 进度与代码审查记录（2026-08-11，更新至 2026-08-12）

> 本文记录 `LeoAndroidBaseUtil` 八模块整改任务截至 2026-08-12 的落地进度，以及针对
> Codex 两轮审查修复所做的正式 code-review 结论（首次复审确认 9 项代码问题 +
> 1 项维护性建议、驳回 2 项；当前状态见 §1 与 §3）。
> 关联文档：`2026-08-04-remediation-impl-plan-zh.md`（P0→P3 路线图）、
> `2026-08-04-remediation-impl-details-zh.md`（各条目目标代码）、
> `2026-08-04-eight-module-code-review-zh.md`（原始 72 项审查）。

分支：`fix/eight-module-remediation`。

---

## 1. 进度总览

| 阶段 | 项数 | 状态 | 说明 |
|------|------|------|------|
| **P0** | 4 | ✅ 已完成 | CIP-1、CAM2-1、HTTP-1、HTTP-3 |
| **P1** | 26 | ✅ 已完成 | 资源/竞态/生命周期/泄漏 |
| **P2** | 26 | ✅ 已完成（Codex `7da7c444c`，待我方独立复审） | 功能正确性/并发/输入校验/性能 |
| **P3** | 16 | ✅ 已完成 | 清理/规范/测试补齐（2026-08-12） |
| **本轮剩余问题** | R-5 深改 | 🟡 暂缓 | 见 §3（R-1~R-4、R-6~R-10 已修复；R-5 部分修复、深改暂缓） |

累计：72 项确认整改已完成 **72 项**（P0+P1+P2+P3）；本轮审查
**R-1/R-2/R-3、R-4/R-6/R-7/R-8/R-10 已修复**，R-5 部分修复（深改暂缓，决策 B），
R-9 维护性重构已完成；仅剩不计入 72 项的 R-5 深改以及 `CX-5`/`LB-3` 决策项。

> 说明：P2 由 Codex 于 2026-08-06 一次性完成（`7da7c444c`），按改动文件覆盖核实对应全部 26 项
> 计划位置；**尚未**做我方独立逐项复审。本文 §3 的 R-1~R-10 是对 Codex 前两轮修复
> （`b67c47606..14d47c758`）的审查结论，P2 提交并未修复其中任何一条。

### 提交历史（本分支，自 master 起）

```
1fcd4b104 docs: add remediation progress and post-Codex review record  # 本文
7da7c444c Fix P2 issues across reviewed modules                 # Codex P2（26 项）
14d47c758 fix(camera2live): synchronize opened camera state      # Codex 第 2 轮
ba713af3c fix: address eight-module review findings              # Codex 第 1 轮
b67c47606 fix(camerax): remediate CX-1/2/3/4 (P1)
c31f1b65d fix(camera2live): remediate CAM2-2/3/6/7/8 (P1)
d4a71714f fix(lifecycle/security): remediate ABN-1, AR-1, AR-7, CPB-1, CPB-2 (P1)
376e05cb3 fix(audio): remediate lifecycle/race/cancellation issues AUD-1~8 (P1)
44156330a fix: remediate P1 batch 1 (LB-1, CIP-3, HTTP-2, HTTP-4)
ea55a2bf4 fix: remediate P0 issues from eight-module review (CIP-1, CAM2-1, HTTP-1, HTTP-3)
```

### 已知对外 API 变更（已记入 CHANGELOG）

- `Camera2ComponentHelper.switchCamera` 同步 → 异步（签名不变，完成时机延迟）
- `BaseCameraXFragment.binding` 恢复为 `var`（Codex 回补二进制兼容），新增只读 `viewBinding`
- `SoundManager` 移除公开 Activity 引用，`ctx` 保留但返回 applicationContext
- 新增 `Camera2ComponentHelper.CameraErrorListener`、audio 各 `*AndJoin()` suspend 变体
- `SingletonHolder.getInstance` 改 `open`（供 `DisplayCutoutManager` override，二进制兼容需要）

### ⚠️ 未验证事项

- P3 完成后运行 `./gradlew staticCheck --continue --rerun-tasks` **已通过**（2026-08-12，
  1421 个任务全部执行，最终复跑耗时 1m31s）。目标模块 XML 报告合计 84 个 JVM 测试，
  0 failure / 0 error。
  注意：staticCheck 只覆盖编译/detekt/ktlint/单测，
  **不**替代 R-3、R-4、R-7 的真机运行时验证；修复结论还依赖代码复审和对应回归测试。
- audio / camera 真机回归未做（录制停止后重预览、返回栈、旋屏、前后台快切、进相机即返回；
  Camera2 还需连续快速切换前后镜头并检查黑屏、UI 状态和 `ERROR_CAMERA_IN_USE`）。
- P3 `CX-9c` 仍需真机验证两条路径：首次进入时已授权，以及权限弹窗授权后返回；每条路径都应只导航
  一次，前后台切换或 STARTED 状态恢复不应再次导航，授权回调后立即离页不应崩溃。
- 版本号（`leo-version`）未 bump。

---

## 2. 代码审查方法

对 **`b67c47606..14d47c758`**（即 Codex 前两轮修复 `ba713af3c` + `14d47c758` 的合并 diff；
审查执行时 `HEAD` 恰为 `14d47c758`，故当时命令写作 `b67c47606..HEAD`）执行 high-effort 审查：
8 个 finder 角度（3 正确性 + 3 清理 + 1 altitude + 1 conventions），
每个候选经 1 票对抗式 verify。12 候选 → 驳回 2、存活 10。
本审查范围**不含**其后的 P2 提交 `7da7c444c` 与本文档提交。

- ✅ = CONFIRMED（从代码可构造）
- 🟡 = PLAUSIBLE（现实条件下可达，但非确定性）

---

## 3. 审查问题状态（仅剩 R-5 深改暂缓，按严重度）

### 3.1 ✅ P1 返工 —— **R-1 / R-2 / R-3 已于 2026-08-11 修复**（见 CHANGELOG「修复」段）

#### R-1 ✅ `BaseMediaCodecSynchronous.kt:96` 输出循环死循环 → ANR
- **现象**：`while (outputIndex > -1)` 内 `if (buffer == null) continue` 用**同一个** outputIndex
  无限重试（`outputIndex` 在循环体末尾才重新赋值），`ensureActive()` 只在外层 do/while。
- **触发**：旧版 `release()` 在 worker 持有已 dequeue 的 outputIndex 时并发 `flush()`，
  按官方文档 `getOutputBuffer(失效index)` 返回 `null`（而非抛异常）→ 内层 while 永久自旋（100% CPU）。
- **影响（口径修正，采纳 Codex）**：确定后果是 **worker 自旋 + CPU 占用**，且并发释放
  （`cancelAndJoin`）时**可能**被阻塞；原文「后续调用必然 ANR」表述偏绝对，实际是「可能阻塞」。
- **建议**：`null` 时 `break`（或重新 `dequeueOutputBuffer` 并在内层加 `ensureActive()`）。

#### R-2 ✅ `Camera2ComponentHelper.kt:122` cameraScope 泄漏（Activity 型消费者）
- **现象**：`cameraScope` 取代 `context.lifecycleScope` 后丢失「Activity 销毁自动取消」；
  `cameraScope` 仅在 `release()` 中取消。
- **触发**：demo `Camera2WithoutPreviewActivity` 的 `onDestroy` 只调 `stopCameraThread()`，
  从不调 `release()`；更糟的是其 `onStop → stopRecord()` 会再调 `initializeCamera()`，
  在 `cameraScope` 上启动的协程跨 destroy 存活并重新打开相机，钉住已销毁的 `FragmentActivity`
  → 相机被占用 + Activity 泄漏。
- **范围**：库内 `BaseCamera2Fragment` 已正确在 `onDestroyView` 调 `release()`；问题限于
  **Activity 型 / 外部消费者**（从旧 lifecycleScope 行为升级者）。
- **建议（采纳 Codex）**：**必须**修改 demo 调用 `release()`（补文档无法修复已存在的泄漏），
  **并**补充 helper 公开生命周期契约（宿主须显式 `release()`）——两者是「与」不是「或」。

#### R-3 ✅ `Camera2ComponentHelper.kt:1225` 双击切换镜头永久黑屏
- **现象**：连续两次 `switchCamera`，第二次 `switchJob?.cancel()` 取消的首次切换**已 `closeCamera()`
  但尚未重开**，第二次 `checkNotNull(openedCamera.get())` 抛
  `IllegalStateException("No opened camera to switch from.")`。
- **触发**：job1 在 `switchMutex.withLock` 内同步执行 `closeCamera()`（`openedCamera` 置 null）后
  挂起于 `closed.await()`，被 job2 的 `cancel()` 取消、释放锁；job2 `checkNotNull` 抛异常 →
  `reportCameraError`（demo 未设 listener）→ 无相机打开、无重试路径 → 预览永久黑屏。
- **初始建议（后续复审已否定）**：`openedCamera` 为 null 时直接打开新设备。该方案忽略了
  `closeCamera()` 会在 `CameraDevice.StateCallback.onClosed` 前清空 `openedCamera`；此时 null
  仅表示没有登记的已打开设备，不表示旧设备已物理关闭，直接打开仍可能触发
  `ERROR_CAMERA_IN_USE`。
- **最终修复**：不再用新请求取消正在执行的关闭/打开任务；所有切换请求经 `Mutex` 串行处理，
  打开新设备前重新读取最新目标镜头，并继续等待旧设备的 `onClosed`。Fragment 侧根据
  ToggleButton 的 `isChecked` 显式请求前置或后置镜头，避免从尚未提交的 `lensFacing` 推导目标。

### 3.2 🟡 audio / camera 竞态 —— **R-4 / R-7 / R-8 已于 2026-08-11 修复；R-5 部分修复**（见 CHANGELOG）

#### R-4 ✅ `BaseMediaCodec.kt:122` 同步 release() 与 worker 并发操作 codec
- **现象**：旧版 `release()` `cancel()` 不 `join` 即在调用线程 `flush()/release()` codec，
  与仍在 `dequeueInput/OutputBuffer` 的 worker 并发操作非线程安全的 `MediaCodec`。
- **触发**：`stopRecord()`/`stopPlaying()`/`AudioPlayer.release()` 走此路径。多数抛
  `IllegalStateException` 被吞（`codecFailed` 置位），但赶上 mid-flush 抛 `CodecException` →
  正常停止却触发 `notifyCodecFailure()`。**间歇性、设备相关**。
- **口径修正（采纳 Codex）**：核心竞态成立；但仓库内**并不存在**原文所说的「重建型
  `notifyCodecFailure` override」，「关停期间复活 codec」仅为潜在风险而非现存路径。
- **建议（采纳 Codex）**：**不**在同步 API 内引入 `runBlocking`；保留 `@Deprecated` 同步入口，
  将仓库内调用逐步迁移到 `releaseAndJoin()`。
- **最终修复**：同步 worker 的完整 `process()` 迭代与 `stop()`/`flush()`/`release()` 共用一把
  可重入锁。旧同步 `release()` 取消 worker 后会等待当前迭代退出再释放 codec，消除并发访问；
  `releasing` 标志仅负责抑制主动关停的伪失败/EOS。新增并发测试验证释放等待行为。

#### R-5 🟡 `BaseMediaCodecSynchronous.kt:84` 空输入无条件 queueInputBuffer(size=0)
- **现象**：`onInputData` 返回 ≤0 时无条件 `queueInputBuffer(inputIndex, 0, 0, pts, 0)`。
- **触发**：`AacDecoder` 空闲时每次 `poll(50ms)` 超时都提交 0 字节非 EOS buffer +
  **相同 pts**（`computePresentationTimeUs` 依赖不前进的 `frameCount`）+ 每次一条 `Decode cost` 日志
  → ~20 次/秒 codec 空提交（确定存在）。**厂商解码器因重复/非单调 pts 抛 `CodecException` 杀会话
  属推测**（未在真机复现），非确定后果。（"busy-loop 秒杀"半条不成立：同步基类现有子类均为阻塞/超时 poll。）
- **附带**：`AacDecoder.kt:86` 注释 "process() then skips queueing" 已过时。
- **建议**：跨迭代**持有**已 dequeue 的 inputIndex，仅在有真实数据或 EOS 时才归还（既修原
  "输入槽耗尽" 又消除空闲 churn）；顺带更正注释。
- **风险提示（采纳 Codex）**：长期持有 input index 会增加 flush/release/取消时处理**失效 index**
  的复杂度。更稳妥方向：**先等输入数据到达再 dequeue**，或明确设计 pending-index 状态机并补
  取消 / flush / EOS 测试。
- **决策（2026-08-11，B）：结构性改动暂缓。** 该改动触及 live-audio 的 MediaCodec 同步解码循环
  （`process()` 改 suspend + `hasInputData()` 门控 + `delay()` 节流）。audio 模块现已补充 R-4 的
  codec 互斥单测，但仍无法在无真机时验证「循环重构不破坏真实解码播放」。空闲 churn 为约 20 次/秒
  空提交、非致命，推迟成本低。待有真机回归条件时再落地（连同取消 / flush / EOS 测试）。安全子集
  （空闲日志守卫 + 过时注释）已随 `cb355582f` 上线。

#### R-7 ✅ `Camera2ComponentHelper.kt:603` 部分初始化失败后重试 CAS 冲突黑屏
- **现象**：`initializeCamera` 的 catch 只 `reportCameraError`，`open` 成功但后续步骤
  （`setImageReaderForPhoto`/`setPreviewRepeatingRequest`）抛异常时**不清理 `openedCamera`**。
- **触发**：重试 `initializeCamera` 打开同 cameraId 时，框架驱逐旧设备的回调与新 `onOpened` 的
  顺序无保证；不利顺序下 `compareAndSet(null, ·)` 失败 → 新设备被关、异常报给空 listener → 黑屏。
  （旧设备回调最终清槽后再重试可恢复，故非永久 → PLAUSIBLE。）
- **建议**：初始化失败路径显式 `closeCamera()` / 清 `openedCamera`。
- **最终修复**：保存本次打开设备对应的关闭信号；后续 setup 失败时在 `NonCancellable` 清理区
  调用 `closeCamera()` 并等待该设备 `onClosed`（最长 3 秒）后再上报原异常，避免立即重试窗口。

#### R-8 ✅ `MicRecorder.kt:212` 自调用 require 守卫位于副作用之后
- **现象**：`stopRecordAndJoin` 的 `require(job !== current)` 守卫在 `stopped` CAS 与
  `audioRecord.stop()` **之后**，违约调用会在**半停止**后才抛异常。
- **触发**：从录音 job 上下文（如 `runBlocking` 包装）调用：守卫抛
  `IllegalArgumentException`（由录音循环 catch 兜底 → `onStop(false)`，用户主动停止却收到失败）；
  或 `runBlocking` 下守卫通过但 `cancelAndJoin` 自死锁。
- **建议**：把 `require` **移到 CAS/stop 之前**（廉价加固）。**注意（采纳 Codex）**：移动守卫只能
  修「直接自调用」，**无法**解决「`runBlocking` 嵌套调用」下守卫通过后 `cancelAndJoin` 自死锁的情形。

### 3.3 清理 / 低优（可延后）

#### R-6 ✅ `ZipUtil.kt:300` 忽略 backupFile.delete() 返回值 → 备份残留
- **现象**：成功路径 `backupFile?.delete()` 返回值被忽略；条目路径被**非空目录**占用时，
  `renameTo(backup)` 对目录成功、`delete()` 对非空目录失败且被忽略 → 含旧（可能敏感）内容的
  `.unzip-backup-*.tmp` 目录静默残留在解压目录（旧代码 `require(entryFile.delete())` 会响亮失败）。
- **建议**：检查 `delete()` 返回值并记日志 / 递归删除；或入口拒绝「文件条目撞已有目录」。

#### R-9 ✅ [清理] audio 拆解逻辑 4-6 处近似复制
- **位置**：`MicRecorder.finishRecorderRelease`(232) vs `finishRecorderReleaseAndJoin`(258)
  （~20 行仅差 encoder release 一步 + NonCancellable）；`AacStreamPlayer` 与 `OpusStreamPlayer`
  的 `stopPlaying/detachDecoderForStop/releaseAudioTrack` 三件套（`releaseAudioTrack` 逐字节相同）；
  录音循环失败序列 `stopped.set(true); stopAudioRecord(); finishRecorderReleaseAndJoin(false)`
  贴了两份（:136 / :147）。
- **成本**：未来拆解顺序修复须同步落在 4-6 个副本，漏一处即造成 legacy 与 suspend 路径语义分叉。
- **建议**：抽共享 release core（以 encoder-release 为 lambda 参数）+ 播放器公共 `stopCommon()`。
- **定性（采纳 Codex）**：这是**维护性重构建议**，不应计入「确定性缺陷」；优先级低于 R-1~R-8。
- **最终处理**：`MicRecorder` 抽取统一的录音失败清理、`AudioRecord` 释放和完成通知步骤；新增模块
  内部 `StreamPlayerStopper`，由 AAC/Opus 流播放器共用 decoder 摘除、scope 取消、`AudioTrack`
  释放及同步/挂起 decoder 释放流程。公开入口、资源释放顺序与取消传播语义不变，并补充停止顺序和
  `CancellationException` 传播回归测试。

#### R-10 ✅ [低] `HttpLoggingInterceptor.kt:306` 哨兵异常被吞时缺截断标记
- **现象**：限长用私有 `IOException` 子类**穿过第三方 `writeTo` 抛出**；若 `writeTo` 吞掉该异常
  且 okio 缓冲恰好排空，`truncated` 保持 `false` → 日志打出 cap 字节但无 `(truncated…)` 后缀。
- **范围**：纯外观、触发条件苛刻；"损坏有状态 body" 半条已被 `isOneShot/isDuplex` 前置过滤驳回。
- **初始建议（后续复审已调整）**：丢弃式计数 sink 虽可限制日志缓存，却仍会消费并生成完整请求体，
  无法限制处理时间；最终改为带截断状态的无栈哨兵异常，在达到上限后提前终止写入。
- **最终修复**：sink 在超过上限时先保存 `truncated = true`，再抛出复用的无堆栈哨兵以停止
  body 继续生成；即使第三方 `writeTo()` 吞掉异常，截断状态仍保留。测试覆盖 256 KiB+1 精确边界、
  吞异常和提前终止生产。

---

## 4. 驳回的候选（存档）

- **openCamera 的 `cont.resume(device)` 竞态泄漏设备** —— **驳回**。kotlinx 1.10.2 的
  prompt-cancellation 保证：cancellation 赢得 dispatch 竞争时，会对被丢弃的 resume 值调用
  cancel handler（`CompletedContinuation.invokeHandlers`）；且成功路径**故意保留 `pendingDevice`**，
  供 `invokeOnCancellation` 关闭设备并 `clearOpenedCamera`。设计正确。
- **`SingletonHolder.getInstance` 改 `open` 是设计倒退** —— **驳回**。已发布 tag（5.15.8/5.16.0）
  的二进制里 `DisplayCutoutManager` 就是**继承** `SingletonHolder` 的形状，外部二进制引用擦除后的
  `getInstance(Object)Object` 虚方法；改成 plain factory 会删掉该桥方法 → 调用方 `NoSuchMethodError`。
  Codex 的 `override` 才是二进制安全方案。（设计味道轻微存在，但结论上 Codex 正确。）

---

## 5. Codex 交叉复审结论（2026-08-11）

Codex 复审了本文档与 R-1~R-10，结论汇总：

| 项 | Codex 结论 | 处理 |
|----|-----------|------|
| R-1 | 成立；「必然 ANR」偏绝对 | 已改口径为「自旋+CPU，可能阻塞」 |
| R-2 | 成立 | 保留 |
| R-3 | 成立 | 保留 |
| R-4 | 核心竞态成立；无「重建型 override」；勿用 `runBlocking` | 已改口径 + 建议 |
| R-5 | 空 buffer 确定；厂商崩溃属推测 | 已标注推测 |
| R-6 | 成立 | 保留 |
| R-7 | 成立 | 保留 |
| R-8 | 成立但仅修直接自调用，解决不了嵌套 `runBlocking` | 已加注意 |
| R-9 | 维护性重构，不算确定性缺陷 | 已完成重构 |
| R-10 | 成立但极低概率 | 保留（低优） |

另 Codex 指出本文档两处需修正，均已采纳：进度过时（P2 已完成，应为 56/72）、
审查范围应精确写作 `b67c47606..14d47c758`。

**双方无分歧**：**高优先级**确认问题为 **R-1 / R-2 / R-3**（P1 返工，未被 P2 提交修复）。
（R-4 / R-6 / R-7 同为已确认问题，优先级次之；R-9 为维护性重构，不计入缺陷。）

## 6. 下一步

1. ✅ **R-1 / R-2 / R-3 已修复**（2026-08-11，见 CHANGELOG）。
2. ✅ **R-4 / R-7 / R-8 已修复,R-5 部分修复**（2026-08-11，见 CHANGELOG）。
   R-6 / R-10 已修复（`1f7b92455`）。R-5 结构性改动**暂缓**（决策 B，见 §3.2；待真机验证条件）。
   R-9 维护性重构已完成；仅剩 R-5 深改（待真机条件）。
3. **我方独立复审 P2 提交 `7da7c444c`**（Codex 已完成，尚未经本侧审查）。
4. ✅ P3 完成后 `./gradlew staticCheck --continue --rerun-tasks` 已通过（1421 任务全过；见 §1）。
5. ✅ **P3 16 项已完成**（2026-08-12）；重点回归测试覆盖 AUD-14 的 audio 全模块
   `runCatching` 取消重抛 helper、CX-9c 门控、CPB-6 快照和 LB-5 大小端固定向量。
6. 真机回归（包括 Camera2 连续快速切换）+ 版本号 bump。
