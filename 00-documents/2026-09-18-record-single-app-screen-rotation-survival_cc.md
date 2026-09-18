# 录屏 Demo 旋转中断修复（2026-09-18）

本文记录 `RecordSingleAppScreenActivity` 在录制过程中旋转屏幕导致录制中断的排查与修复。

基线提交：`46c3220f5`。本文所述改动对应 `aa7577f38`、`611205937`、`48af308b3` 三个提交，
其中后两个分别来自自审与独立评审。

相关文档：`2026-09-16-screenshot-recorder-independent-review_cc.md` §4.1 A1 处理的是同一个
Activity 在配置变更路径上的**输出文件损坏**问题；本文处理的是那一轮修复之后剩下的**录制中断**
问题。两者根因不同，不要混为一谈。

## 1. 问题现象

手机已开启允许旋转，进入 `RecordSingleAppScreenActivity` 开始录制，**旋转屏幕时录制中断**。

从用户提供的 logcat 可以确认这次中断是「干净」的：

- EOS 正常抵达监听器
- 输出文件被 flush 并 close，可完整播放
- 新实例**没有**自动开始录制

也就是说，`2026-09-16` §9.5 真机清单里的两条验收标准（「不应自动开始录制」「上一段文件应完整
可播放」）**都已满足**——那一轮的 `isSaveEnabled = false` 与时间戳文件名确实生效了。剩下的问题
纯粹是：用户没有按停止，录制却结束了。

## 2. 排查过程

### 2.1 证据

logcat 中旋转时刻出现完整的 `onDestroy` → `onCreate` 序列。这直接排除了「录制器自身出错」这一类
假设——不需要再去 `Screenshot2H26xStrategy` 里找。

### 2.2 前提核实

`demo/src/main/AndroidManifest.xml:266` 该 Activity **完全没有声明 `android:configChanges`**：

```xml
<activity android:name=".basiccomponents.examples.RecordSingleAppScreenActivity" />
```

对比同文件 `:270-272` 的 `ScreenShareClientActivity`，后者声明了 `keyboardHidden|orientation|screenSize`。

于是旋转真的走 destroy/recreate，`onDestroy()` 调 `releaseRecorder(restartable = false)` 释放录制器。
**中断不是 bug，是必然结果。**

## 3. 根因

Activity 未声明 `configChanges`，旋转时被销毁重建，`onDestroy` 主动释放了正在录制的录制器。

## 4. 方案选择

| 方案 | 做法 | 否决理由 |
|------|------|----------|
| A | 不修，文档记为已知限制 | 用户明确要求修 |
| B | 只加 `configChanges`，Activity 存活即可 | **比 bug 更糟**，见下 |
| C | 加 `configChanges` 并重建编码器 | 采用 |

**方案 B 为什么更糟**：编码器在 `build()` 时按固定宽高一次性 configure，每一帧都按
`builder.width`/`builder.height` 绘制（`Screenshot2H26xStrategy.kt:333`、`:686-692`），而 MediaCodec
无法中途重新配置。Activity 存活之后旋转，采集到的横屏画面会被拉伸塞进竖屏尺寸的帧里；更糟的是
H.26x 通过参考帧把这一帧一路带下去，**畸变会一直留在后续画面里**。这正是 `2026-09-16` 评审 M7
指出的风险。

「录制中断」至少还留下一个能看的文件；方案 B 会留下一个从旋转那一刻起全程变形的文件。

**方案 C 的一个意外收获**：`openVideoOutput()` 已经按时间戳命名文件（`2026-09-16` A1 的修复），
所以一次旋转自然产出两个独立的、各自单分辨率的 `.h265` 文件，各自可独立播放。不需要在码流中途
插入新的 VPS/SPS/PPS，也不需要一个能跟随分辨率变化的解码器。

## 5. 本次改动

### 5.1 声明 `configChanges`（`aa7577f38`，`48af308b3` 扩展）

最终形态（`AndroidManifest.xml:266-268`）：

```xml
<activity
    android:name=".basiccomponents.examples.RecordSingleAppScreenActivity"
    android:configChanges="density|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize" />
```

`aa7577f38` 初版只写了 `keyboardHidden|orientation|screenSize`，只覆盖普通旋转；折叠屏展开、
分屏拉伸仍会重建 Activity，而这恰恰是本次改动要解决的场景。`48af308b3` 补上
`smallestScreenSize|screenLayout|density`。

已核实 `demo/src/main/res` 下**没有任何限定符 layout 目录**（只有 `layout/`，values 侧只有
`-ja` / `-zh-rCN` / `-zh-rTW`），所以不重建不会卡住错误的布局变体。

### 5.2 几何来源改为 Activity 上下文（`aa7577f38`）

`buildRecorderSetting()` 原先通过 Application 上下文读 `screenAvailableResolution`，而该扩展走
`WindowManager.currentWindowMetrics`（`lib-common-android/.../DeviceExt.kt:84`），**只有 UI 上下文
才跟随当前旋转**。Application 上下文会一直报竖屏边界——横屏下开始的录制会按竖屏宽高编码，每一帧
都被拉伸。

这是仓库内唯一一处这样用的调用点，其余都已经传 UI 上下文。

### 5.3 旋转时重建（`aa7577f38`）

`onConfigurationChanged` 中走既有的 `releaseRecorder(restartable = true)` 路径：释放当前录制器 →
`armNextRecording()` 按新几何重建 → 若用户仍在录制则开新文件继续。

几何未变的配置变更（键盘、语言）直接返回，不为此切一个文件。

### 5.4 判据改为 `Configuration`（`611205937`）

`aa7577f38` 在 `onConfigurationChanged` 里向 WindowManager 要新边界。`currentWindowMetrics`
**不保证**在应用新边界的 layout pass 之前就已更新，而这里读到旧值的失败方式最糟糕：判据认为
「窗口没动」，**整个重建被静默跳过**，录制器留在旧几何上，日志里没有任何异常。

改为从回调参数 `newConfig` 判断——它携带的就是新值，且不经过 WindowManager。几何本身仍在
`armNextRecording()` 中测量。

## 6. 自审与评审带出的修正（`48af308b3`）

`aa7577f38` + `611205937` 之后做了一轮独立评审，带出三个真缺陷和一处理由错误。

### 6.1 恢复录制读的是记忆标志而非开关本身

`resumeAfterRebuild` 在几何变更开始拆除时置位，只由 `armNextRecording()` 或释放失败分支清除。
**开关在拆除开始之后被关掉时，没有任何地方清它**——而这是会发生的：`releaseRecorder()` 设
`isEnabled = false` 只挡触摸，`onError` 里的 `binding.toggleBtn.isChecked = false` 是程序赋值，
照样触发 `setOnCheckedChangeListener`；该监听器进去发现 `cleanupStarted` 已置位便直接返回，标志
留在 true。

结果：录制器在旋转中途失败，Toast 已告诉用户「无法录制」、开关显示 off，屏幕却立刻重新开始录制。
此时用户再点一下开关，会对一个**已经启动过的一次性录制器**调 `startRecord()`，在主线程触发它的
`check()`，而 `openVideoOutput()` 已经把输出流从运行中的录制器底下换掉了。

改法：删掉标志，在 `armNextRecording()` 中读 `binding.toggleBtn.isChecked`——开关本身就是用户意图，
在恢复的那一刻读它。

### 6.2 应用内语言未重新应用

语言依附于 Activity 自己的 resources，由 `BaseDemonstrationActivity.attachBaseContext:24` 在创建时
应用一次。旋转原本会重建 Activity、顺带重跑这一步；现在 Activity 存活，就没有任何地方重新应用了。

现于 `onConfigurationChanged` 中补 `LangUtil.getInstance(this).setAppLanguage(this)`。

**准确的影响范围**：`LangUtil.updateResources()` 在 API ≤ 24 走
`Resources.updateConfiguration()`，这条会被框架下发新 configuration 时覆盖，**确实需要补**；
API ≥ 25 走 `createConfigurationContext()` 返回新上下文，此处返回值无处可用只能丢弃，实际生效的
只有 `Locale.setDefault()`。但 API ≥ 25 的 locale 本来就由 `attachBaseContext` 传入的 override
config 保住，所以在高版本上这是一道无害的保险。

成本上没有变化：旋转以前本来就会因重建走一遍同样的调用，且 `saveLanguageToPref` 用的是
androidx ktx `edit {}`（默认 `apply()`），不阻塞主线程。

### 6.3 密度必须进入几何比较

见 §5.1。`configChanges` 既然纳入了 `density`，判据就不能只看 `screenWidthDp`/`screenHeightDp`
——这两个值是密度无关的，密度变化时它们不变，而编码器所配置的**像素**尺寸已经变了，判据会错报
「什么都没动」。

现比较三元组 `(screenWidthDp, screenHeightDp, densityDpi)`。

### 6.4 一处正确代码配了错误的理由

`armNextRecording()` 原注释称「`releaseRecorder` 先 UNDISPATCHED 启动再挂进 `Dispatchers.IO`，
所以主线程必然已跑完排队的 traversal」。**这个论证不成立**：协程 resume 与 Choreographer
traversal 都是 main looper 消息，顺序没有保证。

真正的理由是：`onConfigurationChanged` 被派发之前，Activity 的 resources configuration
（以及 `currentWindowMetrics`）就已经更新了。代码未改，注释换成成立的理由。

## 7. 已知取舍与边界

- `uiMode` **未**纳入 `configChanges`，深色模式切换仍会重建 Activity 并结束录制且不恢复。
  这是有意的：主题切换重建是期望行为。
- 一次旋转产生两个文件，不做拼接。跨分辨率拼接需要解码器跟随中途几何变化，代价远大于收益。
- 释放失败（`released == false`）时不再武装下一次，录制功能在本次 Activity 生命周期内禁用，
  并 Toast 告知。该分支同时意味着不会恢复录制。

## 8. 验证状态

### 已完成

- 代码自审一轮（发现并修正 §5.4）
- 独立评审一轮（发现并修正 §6.1–§6.4）
- 静态核查（人工）：行宽无超 100 字符项、无失效引用残留、文件 390 行（限 800）、
  类内函数 19 个（`TooManyFunctions` 阈值 33）
- **静态检查已覆盖到 `48af308b3`**（2026-09-18 在含该提交的分支上以 `--rerun-tasks` 重跑）：

  ```bash
  ./gradlew --continue --rerun-tasks :demo:ktlintCheck :demo:detekt :demo:compileDevDebugKotlin
  ```

  309 个任务全部实际执行，BUILD SUCCESSFUL。输出中的 deprecation 警告共 18 条，全部来自
  `audio` / `lib-common-android` / `demo` 的既有过渡期 API 与既有平台 API 弃用
  （`stopPlaying()`、`release()`、`stopRecord()`、`statusBarColor`、`Locale` 构造器、
  `Resources.updateConfiguration()`），**没有一条指向 `RecordSingleAppScreenActivity.kt`**。
  其中 `LangUtil.kt:167/169` 与 `LocaleUtil.kt:39` 位于 §6.2 调用目标的内部，属该模块既有问题，
  非本次新增调用点引入。

### 待真机验证（尚未进行）

- 录制中旋转屏幕：**不应**出现 `onDestroy` / `onCreate`；应看到 EOS、新的
  `dstFile=screen-<时间戳>.h265`，以及 `LEO-ScrCap encodeType=H265 width=... height=...` 中
  **宽高互换**；两个文件均可播放且方向正确
- **先转到横屏再按开始录制**：这是 §5.2 那个 Application 上下文缺陷的直接复现路径
- 旋转中途录制器失败：确认不会在 Toast 提示「无法录制」之后自行重新开始（§6.1）
- 切换应用内语言后旋转：确认界面语言未回落到设备语言（§6.2）
- 连续快速旋转两次：应只重建一次，且按最终方向编码
