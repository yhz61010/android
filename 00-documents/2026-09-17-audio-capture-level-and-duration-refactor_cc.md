# 音频采集电平修复与 Duration 常量整理（2026-09-17）

本文记录两件事：真机验证音频模块时发现的「播放声音明显偏小」问题的完整排查与修复，以及顺带完成的
`kotlin.time.Duration` 常量一致性整理。

基线提交：`d4b3cbcce`。§1–§6 所述改动对应提交范围 `d4b3cbcce..63217585f`；此后针对本文的
代码评审又带出四个提交，记在 §7。

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

## 3. 根因分析

### 3.1 实测数据（小米 10 / Android 13，单机）

把 demo 录制的 `audio.pcm`（48 kHz / 16 bit / 双声道）拉出来做 `ffmpeg astats`。下面这组数字只来自这一台设备：

| 指标 | 实测 | 正常参考 |
|------|------|----------|
| Peak level | **−18.88 dBFS** | 音乐 ≈ −1，人声 ≈ −6～−12 |
| RMS level | **−38.09 dBFS** | 音乐 ≈ −12～−16，人声 ≈ −20～−25 |
| Bit depth | 12/16/16/16 | 16 位仅用到 12 位 |
| Channel 1 vs 2 | 每一项统计逐位相同 | — |
| Noise floor | `-inf`，count=313 | 存在被压成数字绝对零的片段 |

RMS 比正常媒体低约 **22 dB**，线性幅度上差十几倍；峰值还空着 18.9 dB 余量完全没用上。

### 3.2 成因推断

`MicRecorder` 的采集源默认是 `MediaRecorder.AudioSource.VOICE_COMMUNICATION`，并无条件挂载
`AcousticEchoCanceler` + `AutomaticGainControl` + `NoiseSuppressor`。**当前推断**是这条平台 VoIP
采集链造成了 §3.1 的两项观测：

- 把电平归一到**通话语音**档位，远低于媒体内容——推断为响度缺口的来源；
- 只提供**单声道**，请求 `CHANNEL_IN_STEREO` 时把同一份内容复制到两个声道——这解释了两个声道
  逐位相同，也说明 OPUS 在用 128 kbps 编两条完全一样的声道。

**这只是推断，现有证据不足以把它写成平台级行为。** 局限如实记录如下：

- 数据只来自小米 10（Android 13）**一台**设备。`VOICE_COMMUNICATION` 的具体处理链由厂商实现，
  换一台设备未必是同样的表现。
- 改前 / 改后不是受控实验：采集源、三个音效、声道配置、录音内容是**同时**变的（§6 的两点说明
  记录了同一件事）。因此无法把响度差异归到其中任何单一变量头上。两项观测里，声道复制与配置的
  对应关系最直接；电平归一的因果链最弱，它同样可以由录音内容或嘴离麦距离解释。

要坐实因果，需要的是受控 A/B：固定声源与录音内容，先只切采集源（音效全关），再只切音效（采集源
固定），并在两台以上不同厂商的设备上复现。**这组实验尚未进行。**

因果是否坐实不影响改动方向：录制回放场景本就不该走 VoIP 采集链（理由见 `MicRecorder` 的 KDoc，
与本机数据无关），并且改后的电平与听感确有改善（§6）。

排查过程中一度把 §3.1 的 `Noise floor = -inf` 当作降噪器把背景压成数字绝对零的证据。**该归因已被
后续实测推翻**：换用 `MIC`、三个音效全部关闭后重录，`Noise floor` 仍为 `-inf`。这个指标随录音内容
变化，两种配置下都会出现，不能用来判断音效链是否生效。真正与配置强相关、且与内容无关的是声道数。

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

`d4b3cbcce` 已把调用点从 `delay(Long)` 迁到 `delay(Duration)`，但常量仍是裸 `Long` 且保留 `_MS`
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

顺带修掉一处遗漏：`OpusFilePlayer` 的 `CODEC_EOS_TIMEOUT_MS` 在 `d4b3cbcce` 中**未被迁移**，仍在调用
`withTimeoutOrNull` 的 `Long` 重载，与同文件其它调用点不一致。本次一并转为 `Duration`。

另外把散落的 `delay(20.milliseconds)` 收敛为具名的 `DRAIN_POLL_INTERVAL`（`OpusFilePlayer` 两处、
`AacFilePlayer` 一处）。

均为 `private` 常量，**对外 API 无影响**。

### 4.4 诊断日志转为常备 DEBUG 工具

`AudioTrackPlayer.logRouting()` 原注释写的是「查清后删除」。本次排查已结束，但这段日志是唯一能把
「设备播听筒」「被别的 App 留在 `MODE_IN_COMMUNICATION`」「媒体音量只有 20%」和「真正的路由 bug」
区分开的手段，故保留，注释改为说明其长期价值与调用时机（`routedDevice` 在数据真正流动前返回 null，
因此 `play()` 后与首次 `write()` 后各打一次）。

### 4.5 采集与播放改为单声道（2026-09-18 追加，`ad8f0256a`）

本文初稿把这一项列为未决事项，等待改用 `MIC` 后重新评估。结论是这个 demo 不需要立体声，
`AudioActivity` 的两个配置同步改为单声道，并顺带抽出重复的常量、改用具名参数：

```kotlin
private const val SAMPLE_RATE = 48000
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

val audioEncoderInfo = AudioEncoderInfo(
    sampleRate = SAMPLE_RATE,
    bitrate = 128000,
    channelConfig = AudioFormat.CHANNEL_IN_MONO,    // 原 CHANNEL_IN_STEREO
    audioFormat = AUDIO_FORMAT
)
val audioDecoderInfo = AudioDecoderInfo(
    sampleRate = SAMPLE_RATE,
    channelConfig = AudioFormat.CHANNEL_OUT_MONO,   // 原 CHANNEL_OUT_STEREO
    audioFormat = AUDIO_FORMAT
)
```

**两处必须成对改。** 两个 `channelConfig` 各自推导出自己的 `channelCount`
（`AudioEncoderInfo.kt:33-36`、`AudioDecoderInfo.kt:29-32`），编码器与 `AudioTrack` 分别取用。只改
编码侧会让 OPUS 按单声道编码、而播放侧仍按双声道配置 `AudioTrack` 与解码器，单声道 PCM 喂进立体声
track 的听感是音调升高、速度变快。

这两个 `info` 是 `AudioActivity` 的 companion 常量，`AudioSender`（`:53`、`:94`）与 `AudioReceiver`
（`:109`、`:171`）都直接引用，因此三条通路自动保持一致，无需改动别处。已确认 `audio` 与 `demo`
模块中不存在其它硬编码的双声道假设；`ADPCMActivity.kt:82` 的 `CHANNEL_OUT_STEREO` 属独立通路，
不受本次影响。OPUS 配置帧的 83 字节判定（`OpusStreamPlayer.kt:99`）由固定偏移构成，与声道数无关。

### 4.6 CHANGELOG

`CHANGELOG.md` 的 `### 变更 (Changed)` 新增两条：`MicRecorder` 默认值破坏性变更（含回切指引与参数
顺序说明）、audio 模块内部常量 Duration 化。

## 5. 未决事项

1. **`AudioActivity` 的 OPUS 码率仍是 128 kbps**，且是这一组配置里唯一没有抽成常量的字面量。
   改单声道前它名义上编两条声道（实际是同一份内容），现在编一条真正的单声道，128 kbps 对单声道
   OPUS 偏高（语音场景通常 24–64 kbps 即可）。未调整，因为这是 demo、码率不影响正确性。
2. ~~**`MicRecorder.initAdvancedFeatures()` 的三个 `AudioEffect` 未被持有、从不 `release()`**~~
   **已在本文写成后处理，见 §7。** 原文还称「`audioRecord.release()` 会带走 native 侧的 effect，
   功能上不出错」——这句话也是错的，实际风险比原文估计的严重，详见 §7.1。
3. **公开 API 仍是 `Long` 毫秒**：`BaseMediaCodecSynchronous.eosDrainTimeoutMs`（`protected open val`，
   有子类覆写）、`DebounceExt` 的 `debounceTime`、`ScreenCountdownManager` 的
   `countdownDurationMillis` / `warningThresholdMillis` / `remainingTimeMillis`。这些是下游最容易
   写错单位的地方，但转 `Duration` 是源码与二进制破坏性变更，留待下次破坏性变更窗口统一处理。
4. **音频焦点**：全仓库没有任何 `requestAudioFocus()` / `abandonAudioFocus()` 调用。与本次响度问题
   无关（已排除），但意味着别的 App 放音乐时不会被暂停或压低，来电时这边也不会让路。未处理。

## 6. 验证状态

### 已完成

- **静态核对**：全部改动文件字符长度均 ≤100（按字符而非字节计数）；新增 import 均有使用；被替换的
  常量无残留引用；`MicRecorder` 的三个调用点参数位置正确。
- **本地构建与静态检查通过**（用户于 2026-09-17 执行并反馈通过）：

  ```bash
  ./gradlew --continue --rerun-tasks :audio:testDebugUnitTest :audio:detekt :audio:ktlintCheck \
    :lib-mvvm:detekt :lib-mvvm:ktlintCheck :demo:ktlintCheck :demo:detekt \
    :demo:compileDevDebugKotlin
  ```

  §4.5 的单声道改动（`ad8f0256a`）是之后提交的，**未包含在这次运行中**，需再跑一次。

### 设备实测：录制与回放（2026-09-18，小米 10 / Android 13）

用 `AudioActivity` 重录后拉出 `audio.pcm` 跑 `astats`（单声道文件必须用 `-ac 1`，否则 ffmpeg 会把
它按左右交错拆成两路，得到两组「几乎相同但不全等」的假声道）：

| 指标 | 改前 | 改后 | 变化 |
|------|------|------|------|
| Peak level | −18.88 dBFS | **−14.57 dBFS** | +4.32 dB |
| RMS level | −38.09 dBFS | **−30.85 dBFS** | +7.24 dB |
| Max level | 3726 | 6125 | ×1.64 |
| Bit depth | 12/16 | 13/16 | +1 位 |
| Crest factor | 9.12 | 6.52 | 更平 |
| 声道 | 2（逐位相同） | **1** | 见 §4.5 |

**听感确认：系统媒体音量顶格时，响度较改前明显改善。** 这是本次修复的验收标准，数字本身无法替代
它——`MIC` 没有 AGC，电平完全由声源决定。

两点如实记录，避免日后误读这组数字：

- 改前改后是**两次不同的录音**，内容、音量、嘴离麦距离都不同。+7.24 dB 说明改后电平更高，
  **但不能当作本次修复的增益值**。§3.1 预估的约 22 dB 缺口未被这组数据直接证实。
- RMS −30.85 dBFS 仍比成品音乐低十几 dB。这是无 AGC 的原始采集的正常状态，不是缺陷；若某个场景
  需要更高响度，杠杆是软件增益或采集距离，不是采集源类型。

### 待真机验证（尚未执行）

- `AudioSender` / `AudioReceiver` 双机通话，确认回声消除仍然生效（对端听不到自己的回声）——这是
  §4.2 显式回切是否奏效的唯一验证手段。
- `00-documents/2026-09-02-audio-media-teardown-followup-fixes_cc.md` §13.7 / §14.6 / §15.2 的真机清单
  **仍然全部未做**，不受本次改动影响。

## 7. 评审带出的后续提交

`63217585f` 之后对本文与相关代码做了一轮评审，产生以下四个提交。它们不属于 §4 的原始改动，单列于此。

### 7.1 音效的持有与释放（`2c42c5af4`）

§5 第 2 项原写「三个 `AudioEffect` 未被持有、从不 `release()`，`audioRecord.release()` 会带走 native
侧的 effect，功能上不出错」。后半句是错的：`AudioEffect` 持有一个挂在 `AudioRecord` session 上的
原生音效，丢掉最后一个强引用后，GC 可以在**录音进行中**跑终结器把那个原生音效拆掉——双向语音会在
任意时刻静悄悄失去回声消除。这不是「等 GC 回收的整洁性问题」，是功能问题。

改法：三个音效存入字段、持有至会话结束，并在 `releaseAdvancedFeatures()` 中先于 `AudioRecord`
显式释放，复用既有的一次性 `released` 守卫。

### 7.2 音效启用结果校验与构造回滚（`5a4463096`）

`initAdvancedFeatures()` 用 `enabled = true` 开启音效，Kotlin 的属性语法会丢弃
`AudioEffect.setEnabled()` 的状态码。被平台拒绝开启的音效照样留在字段里、被当作已生效——双向语音
在没有回声消除的情况下继续跑，日志里没有任何线索。现改为显式判断 `== AudioEffect.SUCCESS`，未能
开启的立即 `release()`，字段保持 `null`。

同时补上构造失败的回滚：`AudioRecord` 构造或音效挂载抛异常时，调用方拿不到对象引用，也就无从调用
`stopRecordAndJoin()`，已创建的编码器、`AudioRecord` 与音效只能等 GC 终结器。现由
`rollbackFailedInit()` 先释放已建成的部分，再把原异常抛出。

### 7.3 结论范围收窄（`600b6e1bf` 与本次文档修订）

`MicRecorder` 的 KDoc 与本文 §3 原先把小米 10 的单机观测写成了平台级行为。已改为按「该设备上的
观测 + 当前推断」表述，并写明所缺的受控 A/B（见 §3.2 末尾）。

### 7.4 demo 录音文件改名（`00b8412a1`）

`AudioActivity` 改单声道后仍写 `audio.pcm` / `audio.aac` / `audio.opus`。旧版本留在
`externalFilesDir` 的双声道录音会按新的单声道配置打开，裸 PCM 大约以双倍速播放。改名为
`audio_mono.*`，旧文件自然不再被拾取；不做迁移或格式协商——这是 demo 自己的临时录音。

### 7.5 §7 各项的验证状态

- `:audio:testDebugUnitTest` 45 个用例通过；`audio` / `demo` 的 ktlint、detekt 与
  `:demo:compileDevDebugKotlin` 通过（评审在 `00b8412a1` 上执行，**不含 `5a4463096`**，后者需重跑）。
- **未做真机验证**：音效是否真的启用成功、`setEnabled` 失败时的新分支、构造回滚路径，以及 §6 仍挂着的
  双机回声消除，全部只能靠设备确认。
