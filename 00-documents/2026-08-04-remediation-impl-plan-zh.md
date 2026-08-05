# 八模块整改实现计划（2026-08-04）

本文件是《[八模块代码审查复核报告](./2026-08-04-eight-module-code-review-zh.md)》（Codex 复核版）的**实现级整改方案**：给出每条确认问题的 `文件:行`、根因、现状→目标代码、minSdk 21 兼容边界、测试用例与回归风险。

- **详细条目**见配套文档：[整改实现细节](./2026-08-04-remediation-impl-details-zh.md)。
- **基线**：分支 `master`，commit `d24931c9828f54803becf78f2aa9cb5b6d5bf733`。
- **范围**：8 个模块共 **72 条已确认整改项 + 2 条待决策项**（CX-5、LB-3），涉及 `android-restricted / audio / androidbase / camera2live / camerax / circle-progressbar / http / lib-bytes`。
- **约束**：不改任何生产代码（本次仅产出方案文档）；编译与 `staticCheck` 由作者本机执行；`lib-bytes` 等无 `log` 依赖模块**不得新增 log 依赖**；`lib-json` 已按最新约定例外（不在本次 8 模块内）。

---

## 1. 严重级与 P 层定义

| 级别 | 含义 |
|---|---|
| **CRITICAL** | 任意文件写入 / 稳定复现的核心功能崩溃或永久阻塞 / 高影响安全事件 |
| **HIGH** | 主路径资源泄漏、竞态、生命周期错误、敏感信息暴露或明显功能失效 |
| **MEDIUM** | 需特定输入或时序触发、可恢复，或应修复的 API/性能/健壮性问题 |
| **LOW** | 维护性、日志规范、死代码、测试缺口、低概率风险 |

| P 层 | 含义 |
|---|---|
| **P0** | 本轮必修（合入前） |
| **P1** | 高优先（资源/竞态/生命周期/泄漏） |
| **P2** | 次高（功能正确性、并发、输入校验、性能） |
| **P3** | 低风险清理项（`!!`、日志规范、死代码及非阻塞性测试补齐） |

---

## 2. P0→P3 整改路线图

> 战略顺序沿用复核报告 §7；下表给出全部条目的逐条 P 层归类与索引（ID 前缀：CAM2=camera2live、CIP=androidbase-cipher、ABN=androidbase-非加密、AR=android-restricted、AUD=audio、CX=camerax、CPB=circle-progressbar、HTTP=http、LB=lib-bytes）。

### P0 — 本轮必修
| ID | 模块 | 级别 | 摘要 | 位置 |
|---|---|---|---|---|
| CIP-1 | androidbase | CRITICAL | ZipUtil.unzip Zip Slip 路径穿越 + 无解压上限 | `cipher/ZipUtil.kt:172-198` |
| CAM2-1 | camera2live | CRITICAL | takePhoto Image 泄漏 + continuation 恢复后不退出循环 | `Camera2ComponentHelper.kt:870-1007` |
| HTTP-1 | http | HIGH | 默认硬编码 BODY 日志、敏感 Header 无脱敏、宿主无法单独关闭 | `BaseHttpRequest.kt:43-83`、`HttpLoggingInterceptor.kt:143-227` |
| HTTP-3 | http | HIGH | `source.request(Long.MAX_VALUE)` 整体读入内存，大响应 OOM | `HttpLoggingInterceptor.kt:235-238` |

### P1 — 资源 / 竞态 / 生命周期 / 泄漏
| ID | 模块 | 级别 | 摘要 | 位置 |
|---|---|---|---|---|
| CAM2-2 | camera2live | HIGH | onDisconnected 不 close 设备 / 打开阶段不 resume | `Camera2ComponentHelper.kt:584-626` |
| CAM2-3 | camera2live | HIGH | switchCamera 不等 onClosed 即开新相机 → ERROR_CAMERA_IN_USE | `:1099-1110` |
| CAM2-6 | camera2live | HIGH | Fragment 视图重建致 HandlerThread/singleExecutor 泄漏 | `BaseCamera2Fragment.kt` + helper `:93,419-447` |
| CAM2-7 | camera2live | HIGH | onStop 主线程 sleep(100)+关相机/编码器 → ANR | `BaseCamera2Fragment.kt:203-211` |
| CAM2-8 | camera2live | HIGH | takePhoto/createCaptureSession 用不可取消 suspendCoroutine | `:632-658,870` |
| AUD-1 | audio | HIGH | BaseMediaCodec.release 先释放 codec 后取消 scope | `mediacodec/BaseMediaCodec.kt:51-59` |
| AUD-2 | audio | HIGH | AacDecoder.onInputData 阻塞 take() 取消唤不醒 | `aac/AacDecoder.kt:83-86` |
| AUD-3 | audio | HIGH | MicRecorder.stopRecord 取消后不等录音任务结束 | `MicRecorder.kt:94-175` |
| AUD-4 | audio | HIGH | Stream Player audioDecoder/csd 并发无锁 TOCTOU | `aac/AacStreamPlayer.kt`、`opus/OpusStreamPlayer.kt` |
| AUD-5 | audio | HIGH | 解码器初始化失败 csd 不回滚 → 下一帧 `!!` 崩溃 | 同上 |
| AUD-6 | audio | HIGH | MicRecorder 录音缓冲区逐帧永久收缩 | `MicRecorder.kt:96-105` |
| AUD-7 | audio | HIGH | AudioRecord.read 负错误码 → copyOfRange 崩溃被吞、静默停止 | `MicRecorder.kt:94-127` |
| AUD-8 | audio | HIGH | process() 捕获异常后仍 return true → CPU 忙循环 | `mediacodec/BaseMediaCodecSynchronous.kt:37-99` |
| CX-1 | camerax | HIGH | SoundManager 单例持 Fragment Context | `utils/SoundManager.kt:17` |
| CX-2 | camerax | HIGH | captureForBytes 未在 finally 关闭 ImageProxy | `base/BaseCameraXFragment.kt:226-297` |
| CX-3 | camerax | HIGH | 依赖 ViewBinding 的任务用 Fragment lifecycleScope | `CameraFragment.kt:185/609/624/682`、`base:249/363` |
| CX-4 | camerax | HIGH | binding 永久 lateinit，onDestroyView 未清理 | `base/BaseCameraXFragment.kt:112` |
| CPB-1 | circle-progressbar | HIGH | 无限 ValueAnimator 未随 detach/状态切换取消 | `CircleProgressbar.kt:76,273,525,542-554` |
| CPB-2 | circle-progressbar | HIGH | 未覆写 onMeasure → wrap_content 退化 0×0 | `CircleProgressbar.kt` |
| ABN-1 | androidbase | HIGH | KeepAliveReceiver.onReceive 用 GlobalScope | `utils/system/KeepAlive.kt:108-116` |
| LB-1 | lib-bytes | HIGH | ByteBuffer.copy/copyAll 未继承 order() → 小端静默损坏 | `ByteBufferExt.kt:41-87` |
| HTTP-2 | http | HIGH | HttpRequest 共享可变 Retrofit.Builder 竞态 | `retrofit/HttpRequest.kt:16-25` |
| HTTP-4 | http | HIGH | BaseProgressObserver Disposable 不暴露取消入口 | `observers/base/BaseProgressObserver.kt:19-22` |
| AR-1 | android-restricted | HIGH | DisplayCutoutManager 单例持首个 Activity | `notch/DisplayCutoutManager.kt:34-35` |
| CIP-3 | androidbase | MEDIUM | GZipUtil.decompress 无输出上限（GZIP bomb） | `cipher/GZipUtil.kt:22-27` |
| AR-7 | android-restricted | MEDIUM | getSystemPropertyByShell 参数直拼命令（注入） | `utils/DeviceProp.kt:46-49` |

### P2 — 功能正确性 / 并发 / 输入校验 / 性能
| ID | 模块 | 级别 | 摘要 | 位置 |
|---|---|---|---|---|
| CAM2-4 | camera2live | HIGH | openCamera/switchCamera 未捕获 Security/CameraAccessException | `Camera2ComponentHelper.kt:554-626,1099` |
| CAM2-5 | camera2live | HIGH | getJpegOrientation display=-1 → getValue(-1) 抛异常 | `:850-863` |
| AR-2 | android-restricted | HIGH | vivo 误路由到 HuaweiDisplayCutout（Vivo 实现死代码） | `notch/DisplayCutoutManager.kt:78` |
| AR-3 | android-restricted | HIGH | ApplicationManager.application 急切求值使 init(context) 失效 | `utils/ApplicationManager.kt:14` |
| AR-4 | android-restricted | HIGH | getApplicationByReflect 在静态初始化链且无失败隔离 | `:31-39` |
| AR-5 | android-restricted | HIGH | VivoDisplayCutout 用 densityDpi 直乘算 dp（放大 ~160×） | `notch/impl/VivoDisplayCutout.kt:41,44` |
| AR-8 | android-restricted | MEDIUM | DeviceUtil 单例持 Context | `utils/DeviceUtil.kt:16-17` |
| HTTP-5 | http | MEDIUM | onError 丢堆栈 / 依赖 internal promisesBody / charset `!!` | `BaseProgressObserver.kt`、`HttpLoggingInterceptor.kt:11,170,228,242` |
| CPB-3 | circle-progressbar | MEDIUM | maxProgress 允许 0/负 → getDegrees 除零 NaN | `CircleProgressbar.kt:149,193-198,556-557` |
| CX-6 | camerax | MEDIUM | PhotoFragment/VideoFragment `!!` NPE | `PhotoFragment.kt:41`、`VideoFragment.kt:488` |
| CX-7 | camerax | MEDIUM | ExtensionsManager 每次 bind 重复初始化 | `base/BaseCameraXFragment.kt:661-738` |
| ABN-3 | androidbase | MEDIUM | Watermark 空文本/零间距 step=0 抛异常 | `utils/Watermark.kt:148-159` |
| ABN-4 | androidbase | MEDIUM | WifiUtil 单例持 Activity Context | `utils/device/WifiUtil.kt:25-26` |
| ABN-2 | androidbase | MEDIUM | LeoTextureView setCallback 应在 configure 前 | `ui/LeoTextureView.kt:167-172` |
| CAM2-9 | camera2live | MEDIUM | CameraAvcEncoder.setCallback 应在 configure 前 | `codec/CameraAvcEncoder.kt:193-199` |
| CAM2-10 | camera2live | MEDIUM | CameraAvcEncoder.queue 无界 → 背压/OOM | `codec/CameraAvcEncoder.kt:26` |
| LB-2 | lib-bytes | MEDIUM | toShortArray/LE 奇数长度静默丢字节 | `ByteArrayExt.kt:171-182` |
| CIP-2 | androidbase | MEDIUM | AES/PBKDF2 密钥材料未彻底清零 | `cipher/AESUtil.kt`、`PBKDF2Util.kt` |
| AR-6 | android-restricted | MEDIUM | DeviceProp FileInputStream 未关闭 | `utils/DeviceProp.kt:37-44` |
| AUD-9 | audio | MEDIUM | PCM16 奇数分片契约 + write 负码未校验 | `AudioTrackPlayer.kt:103-122` |
| AUD-10 | audio | MEDIUM | getMinBufferSize 返回值未校验 | `MicRecorder.kt`、`AudioTrackPlayer.kt` |
| AUD-11 | audio | MEDIUM | MicRecorder 未检查初始化/录音状态 | `MicRecorder.kt:34,80-93` |
| AUD-12 | audio | MEDIUM | AacEncoder csd0 缺失时下发脏 ADTS | `aac/AacEncoder.kt` |
| CPB-4 | circle-progressbar | MEDIUM | 每帧 Drawable.setTint() | `CircleProgressbar.kt:418,440` |
| CPB-5 | circle-progressbar | MEDIUM | 进度、动画、恢复和无障碍状态不完整 | `CircleProgressbar.kt`、`State.kt` |
| LB-4 | lib-bytes | MEDIUM | ByteBufferExt 缺少直接测试 | `ByteBufferExt.kt` |

### P3 — 清理 / 规范 / 测试补齐
| ID | 模块 | 级别 | 摘要 |
|---|---|---|---|
| CIP-4 | androidbase | LOW | GZipUtil.isGzip <2 字节无长度检查 |
| ABN-5 | androidbase | LOW | KeepAlive.start 裸 CoroutineScope 无异常处理 |
| ABN-6 | androidbase | LOW | KeepAlive.release 未置空 mediaPlayer |
| ABN-7 | androidbase | LOW | AppExt.installApk/exitApp 吞异常未记录 throwable |
| AR-9 | android-restricted | MEDIUM | Huawei/Oppo/XiaoMi DisplayCutout 用 printStackTrace |
| AR-10 | android-restricted | LOW | ActivityExt.startActivity(clsStr) Class.forName 无异常处理 |
| AUD-13 | audio | MEDIUM | 全模块 printStackTrace 违反日志约定 |
| AUD-14 | audio | LOW | runCatching 吞 CancellationException + AacEncoder FIXME 死代码 |
| CX-8 | camerax | MEDIUM | adjustBitmapRotation getBitmapAndFree() `!!` |
| CX-9a | camerax | LOW | CameraExt CameraCharacteristics.get(...) `!!` |
| CX-9b | camerax | LOW | LuminosityAnalyzer 每帧装箱 |
| CX-9c | camerax | LOW | PermissionsFragment repeatOnLifecycle 误用于一次性导航 |
| CPB-6 | circle-progressbar | LOW | 监听器列表遍历期被回调修改 → CME |
| HTTP-6 | http | MEDIUM | 测试覆盖不足（isPlaintext/bodyEncoded/onError 分类可纯单测） |
| HTTP-7 | http | LOW | 无效 @Suppress("unchecked") + 二进制分支提前 return |
| LB-5 | lib-bytes | LOW | readByte 掩码 no-op / readInt 缺括号 / 注释死代码 |

### 实施前决策门槛（不计入 72 条确认项）

| ID | 模块 | 待确认内容 | 未确认前动作 |
|---|---|---|---|
| CX-5 | camerax | `bindToLifecycle(this)` 是否有意让 use case 跨 View 重建存活 | 保持现状，只补生命周期真机测试；确认应绑定 View 后再改为 `viewLifecycleOwner` |
| LB-3 | lib-bytes | `toAsciiString()` 是严格 ASCII、ISO-8859-1，还是其它逐字节映射 | 不改生产实现；先确认调用方契约并补对应测试 |

---

## 3. 跨模块统一整改（先立标准，再逐条落地）

以下 8 类模式在多个模块重复，建议统一约定后成批整改（对应条目在括号内）。

### T1. `SingletonHolder` 持有 Activity/Context → 泄漏
`SingletonHolder` 进程级只创建一次并永久持有首参。**统一修复**：构造内部转存 `context.applicationContext`。
- 涉及：ABN-4 WifiUtil、CX-1 SoundManager、AR-8 DeviceUtil。
- **例外**：AR-1 DisplayCutoutManager 需真实 Activity `window`，**不能**降级为 applicationContext，应**改为每 Activity 各持一实例**（去掉全局单例）。

### T2. `MediaCodec.setCallback()` 顺序与线程
异步回调须在 `configure()` **之前**注册；用专用 `HandlerThread` 明确回调线程（`setCallback(cb, handler)` 需 API 23+，21/22 回退无 Handler 重载）。
- 涉及：CAM2-9 CameraAvcEncoder、ABN-2 LeoTextureView。

### T3. Image / ImageProxy.close() 必须放 `use{}`/`finally`
- 涉及：CAM2-1 takePhoto（flush 循环、时间戳不匹配 continue、队列满均需 close）、CX-2 captureForBytes、CX-9b LuminosityAnalyzer。

### T4. 阻塞调用 + 释放顺序竞态 → 可取消等待与明确的释放 API
统一顺序：先让 native 阻塞调用返回，再取消并等待后台退出，最后 release。禁止在 UI 线程使用无界 `runBlocking`，禁止 job self-join；阻塞 `take()` 改 `Channel.receive()` 或有界 `poll(timeout)`。公开的非 suspend 释放 API 若要调整，必须提供兼容迁移路径。
- 涉及：AUD-1/2/3、CAM2-8。

### T5. `Fragment.lifecycleScope` 承载依赖 View 的协程 → onDestroyView 后 NPE
触碰 View/binding 的协程改用 `viewLifecycleOwner.lifecycleScope`；后台回调切主线程前通过 `viewLifecycleOwnerLiveData.value` 判断 View 生命周期，避免 detach 后调用 `requireActivity()` 或重新访问不存在的 `viewLifecycleOwner`。
- 涉及：CX-3、CX-4、CX-5（评估）、CAM2-1 的 UI 回调。

### T6. `printStackTrace()` → `LogContext`（这些模块都有 `log` 依赖）
统一 `LogContext.log.e(TAG, "...", it)`。
- 涉及：AUD-13、AR-9、CAM2 若干。**注意**：`lib-bytes` 无 log 依赖，用 `require`/`IllegalArgumentException`，**不得**引入 log。

### T7. 裸 `!!` → `checkNotNull(x){"msg"}` 或 `?:` 早退
优先消除非法状态而非机械替换；CameraCharacteristics/getDrawable/反序列化结果尤需容错。
- 涉及：CX-6/8/9a、CPB-5、HTTP-5、CAM2-5、AR 若干。

### T8. `CancellationException` 必须 rethrow
凡 `runCatching`/`catch (Exception)` 覆盖含挂起点或 `ensureActive()` 的代码块，先 `catch (e: CancellationException) { throw e }` 再处理其它。
- 涉及：AUD-7/8/14、ABN-5、CAM2-4。

---

## 4. 落地与验证约定

1. 每条改动落地后，运行受影响模块的 `./gradlew :<module>:detekt :<module>:ktlintCheck :<module>:testDebugUnitTest`（零容忍：同步清理失效 import / 私有成员 / 死代码）。
2. **无 `src/test` 的模块**（camera2live、camerax、circle-progressbar、android-restricted、audio）需先新建测试源集（JUnit5 + Mockk + Kluent，必要时 Robolectric）；硬件/框架强耦合逻辑按细节文档建议**抽纯函数**后单测，其余标注需 instrumented/手工验证。
3. 安全项（CIP-1、HTTP-1、AR-7 命令注入、CIP-2 密钥清零）修复后再走一次 `security-reviewer`。
4. 相机/音频修复后，在 API 21 / 26 / 29 / 33+ 代表设备验证生命周期、权限撤销、后台恢复、并发操作。
5. 破坏性变更（HTTP-1 默认日志改 NONE、`toShortArray`/`toAsciiString` 契约、若干 API 由抛异常改安全早退）需在 CHANGELOG 记录。
6. 本仓库是已发布 library。任何公开属性可见性、函数签名、默认行为或同步/异步语义变化，先执行 API/ABI 对比；不能把 `public var` 直接改成 `protected val`，也不能直接把普通函数改成 suspend 函数而不给迁移入口。
7. Camera2 的 Image 关闭、超时/取消、单次完成和线程切换，以及 Audio 的停止/self-join/重复回调，必须有 Robolectric、instrumented 或可控 fake 并发测试；纯函数测试只能覆盖匹配和计算规则。

> 逐条现状→目标代码见：[整改实现细节](./2026-08-04-remediation-impl-details-zh.md)。
