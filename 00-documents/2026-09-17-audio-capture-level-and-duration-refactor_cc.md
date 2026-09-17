# 音频采集电平修复与 Duration 常量整理（2026-09-17）

本文记录两件事：真机验证音频模块时发现的「播放声音明显偏小」问题的完整排查与修复，以及顺带完成的
`kotlin.time.Duration` 常量一致性整理。

基线提交：`b5c9d614b`。本文所述改动尚未提交。

## 1. 问题现象

在按 `00-documents/2026-09-02-audio-media-teardown-followup-fixes_cc.md` §14.6 / §15.2 做真机验证时，
在小米 10（Android 13）上观察到：

- 录音本身正常，录下来的内容是对的。
- 回放时听筒和外放同时出声，听筒一侧明显更响。
- **即使把系统音量调到最大，听感仍然明显偏小。**

最初的描述是「声音只从听筒出来」，后经确认实为「两个器件都响、听筒更响、整体偏小」。这个澄清很关键，
它把问题从「路由跑偏」改成了「响度不足」。

## 2. 排查过程

排查按「先拿证据、再下结论」推进。以下四个假设**全部被证据推翻**，记录在此以免日后重复走一遍。

| # | 假设 | 推翻依据 |
|---|------|----------|
| 1 | 录音的 `VOICE_COMMUNICATION` 把设备切进通信用例，污染了播放路由 | 冷启动、全程不录音的场景（A）与先录后放（B）表现完全一致 |
| 2 | 未调用 `setVolumeControlStream()`，音量键调的不是媒体流 | 播放时音量条标题显示「媒体音量」 |
| 3 | 路由真的选了听筒 | 诊断日志 `device=2`（`TYPE_BUILTIN_SPEAKER`），选的是外放 |
| 4 | 某个声道静音，导致一侧响一侧不响 | `astats` 显示两个声道每一项统计逐位相同 |

排查中一度无法推进，原因是**App 从不打印任何路由信息**——路由由 audio policy 在 native 侧决定，
不主动查就看不见。为此在 `AudioTrackPlayer` 加了诊断日志（见 §3.4），一次就拿到了决定性数据：

```
ROUTE[play]        device=2 name=Mi 10 mode=0 musicVol=150/150 outputs=[1,2,18]
ROUTE[first-write] device=2 name=Mi 10 mode=0 musicVol=150/150 outputs=[1,2,18]
```

`device=2` 外放、`mode=0` 正常、`musicVol=150/150` 顶格——播放侧全部正常，问题只能在内容本身。

## 3. 根因

### 3.1 实测数据

把 demo 录制的 `audio.pcm`（48 kHz / 16 bit / 双声道）拉出来做 `ffmpeg astats`：

| 指标 | 实测 | 正常参考 |
|------|------|----------|
| Peak level | **−18.88 dBFS** | 音乐 ≈ −1，人声 ≈ −6～−12 |
| RMS level | **−38.09 dBFS** | 音乐 ≈ −12～−16，人声 ≈ −20～−25 |
| Bit depth | 12/16/16/16 | 16 位仅用到 12 位 |
| Channel 1 vs 2 | 每一项统计逐位相同 | — |
| Noise floor | `-inf`，count=313 | 存在被压成数字绝对零的片段 |

RMS 比正常媒体低约 **22 dB**，线性幅度上差十几倍；峰值还空着 18.9 dB 余量完全没用上。

### 3.2 成因

`MicRecorder` 的采集源默认是 `MediaRecorder.AudioSource.VOICE_COMMUNICATION`，并无条件挂载
`AcousticEchoCanceler` + `AutomaticGainControl` + `NoiseSuppressor`。这条平台 VoIP 采集链：

- 把电平归一到**通话语音**档位，远低于媒体内容——这是 22 dB 缺口的来源；
- 只提供**单声道**，请求 `CHANNEL_IN_STEREO` 时把同一份内容复制到两个声道——这解释了两个声道
  逐位相同，也说明 OPUS 在用 128 kbps 编两条完全一样的声道；
- 降噪器把背景压成数字绝对零，即日志中 `Noise floor = -inf` 的来历。

**「听筒比外放响」这一条没有找到 App 侧的成因。** 既然两个声道内容完全相同，送给上下两个扬声器的
就是同一个信号，差异只能来自设备本身（小米 10 上下扬声器的调音差异，或手掌挡住底部出声孔）。
不做进一步推测。

## 4. 本次改动

### 4.1 `MicRecorder`：改默认值并让音效可关（破坏性）

```kotlin
class MicRecorder(
    encoderInfo: AudioEncoderInfo,
    val callback: RecordCallback,
    type: AudioType = AudioType.PCM,
    audioSource: Int = MediaRecorder.AudioSource.MIC,   // 原 VOICE_COMMUNICATION
    enableAdvancedFeatures: Boolean = false,            // 新增
    recordMinBufferRatio: Int = 1,
)
```

- `initAdvancedFeatures()` 改为 `if (enableAdvancedFeatures) initAdvancedFeatures()`。
- 构造函数 KDoc 写明两个参数的取舍：什么时候该切回 VoIP 链、切回去的代价是什么。

参数顺序上 `enableAdvancedFeatures` 插在 `audioSource` 与 `recordMinBufferRatio` 之间。仓库内没有
任何调用方按位置传 `recordMinBufferRatio`，下游若有则会**编译失败**（`Int` 对 `Boolean` 类型不匹配），
不会静默出错。

### 4.2 双向语音调用方显式回切

改默认值会让实时语音通路静默失去回声消除，对端将听到自己的回声。因此 `AudioSender` 与
`AudioReceiver` 两处调用点显式传入原配置，行为保持不变：

```kotlin
audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
enableAdvancedFeatures = true
```

`AudioActivity` 的「录制到文件再回放」路径不做改动，直接吃新默认值——它正是本次要修的场景。

### 4.3 超时与轮询常量改为 `Duration`

`b5c9d614b` 已把调用点从 `delay(Long)` 迁到 `delay(Duration)`，但常量仍是裸 `Long` 且保留 `_MS`
后缀，于是出现 `AUDIO_TRACK_DRAIN_TIMEOUT_MS.milliseconds` 这种单位编码两遍、而声明处没有获得任何
类型安全的写法。本次把常量本身改成 `Duration`：

| 文件 | 改动 |
|------|------|
| `OpusFilePlayer` | 6 个常量转 `Duration`；停滞检测的 `stalledForMs: Long` 改为 `stalledFor: Duration` |
| `AacFilePlayer` | `AUDIO_TRACK_DRAIN_TIMEOUT` 转 `Duration` |
| `AacStreamPlayer` / `OpusStreamPlayer` | `REASSIGN_LATENCY_TIME_THRESHOLD` 转 `Duration` |
| `BaseMediaCodecSynchronous` | `EOS_RETRY_DELAY` 转 `Duration` |
| `ScreenCountdownManager` | 新增 `TICK_INTERVAL = 1.seconds`，同时支撑 `delay()` 与毫秒递减 |
| `CoroutineActivity` | 去掉 `1300L.milliseconds` 等 3 处冗余 `L` 后缀 |

顺带修掉一处遗漏：`OpusFilePlayer` 的 `CODEC_EOS_TIMEOUT_MS` 在 `b5c9d614b` 中**未被迁移**，仍在调用
`withTimeoutOrNull` 的 `Long` 重载，与同文件其它调用点不一致。本次一并转为 `Duration`。

另外把散落的 `delay(20.milliseconds)` 收敛为具名的 `DRAIN_POLL_INTERVAL`（`OpusFilePlayer` 两处、
`AacFilePlayer` 一处）。

均为 `private` 常量，**对外 API 无影响**。

### 4.4 诊断日志转为常备 DEBUG 工具

`AudioTrackPlayer.logRouting()` 原注释写的是「查清后删除」。本次排查已结束，但这段日志是唯一能把
「设备播听筒」「被别的 App 留在 `MODE_IN_COMMUNICATION`」「媒体音量只有 20%」和「真正的路由 bug」
区分开的手段，故保留，注释改为说明其长期价值与调用时机（`routedDevice` 在数据真正流动前返回 null，
因此 `play()` 后与首次 `write()` 后各打一次）。

### 4.5 CHANGELOG

`CHANGELOG.md` 的 `### 变更 (Changed)` 新增两条：`MicRecorder` 默认值破坏性变更（含回切指引与参数
顺序说明）、audio 模块内部常量 Duration 化。

## 5. 未决事项

1. **`AudioActivity.kt:51` 的 `CHANNEL_IN_STEREO`**（以及 `:56` 的 `CHANNEL_OUT_STEREO`）未改动。
   VoIP 链下它确定是浪费（两声道逐位相同），但改用 `MIC` 后部分机型支持真立体声采集，是否仍为浪费
   需要**在真机上重新量一次两个声道**再决定：若仍逐位相同则应改为 `CHANNEL_IN_MONO` /
   `CHANNEL_OUT_MONO`，若出现差异则应保留立体声。
2. **`MicRecorder.initAdvancedFeatures()` 的三个 `AudioEffect` 未被持有、从不 `release()`**
   （`:205` 起）。`audioRecord.release()` 会带走 native 侧的 effect，功能上不出错，但这三个 Java
   对象要等 GC 终结器才回收，与仓库的确定性释放约定不符。本次未处理。
3. **公开 API 仍是 `Long` 毫秒**：`BaseMediaCodecSynchronous.eosDrainTimeoutMs`（`protected open val`，
   有子类覆写）、`DebounceExt` 的 `debounceTime`、`ScreenCountdownManager` 的
   `countdownDurationMillis` / `warningThresholdMillis` / `remainingTimeMillis`。这些是下游最容易
   写错单位的地方，但转 `Duration` 是源码与二进制破坏性变更，留待下次破坏性变更窗口统一处理。
4. **音频焦点**：全仓库没有任何 `requestAudioFocus()` / `abandonAudioFocus()` 调用。与本次响度问题
   无关（已排除），但意味着别的 App 放音乐时不会被暂停或压低，来电时这边也不会让路。未处理。

## 6. 验证状态

**本次改动未编译、未跑测试、未真机验证。** 已静态核对：全部改动文件字符长度均 ≤100（注意按字符而非
字节计数）；新增 import 均有使用；被替换的常量无残留引用；`MicRecorder` 的三个调用点参数位置正确。

需在本地执行：

```bash
./gradlew --continue --rerun-tasks :audio:testDebugUnitTest :audio:detekt :audio:ktlintCheck \
  :lib-mvvm:detekt :lib-mvvm:ktlintCheck :demo:ktlintCheck :demo:detekt :demo:compileDevDebugKotlin
```

待真机验证：

- 用 `AudioActivity` 重录一段并回放，确认响度恢复正常；再拉出 PCM 跑一次 `astats`，确认 Peak 与 RMS
  回到正常区间，并据此决定 §5.1 的立体声取舍。
- `AudioSender` / `AudioReceiver` 双机通话，确认回声消除仍然生效（对端听不到自己的回声）。
- `00-documents/2026-09-02-audio-media-teardown-followup-fixes_cc.md` §13.7 / §14.6 / §15.2 的真机清单
  **仍然全部未做**，不受本次改动影响。
