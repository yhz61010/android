# Changelog

本文件记录 `LeoAndroidBaseUtil` 的显著变更,尤其是**破坏性 / 行为变更**。
格式参考 [Keep a Changelog](https://keepachangelog.com/)，遵循语义化版本。

## [Unreleased]

### 性能 (Performance)

- **camerax 编码器能力查询不再泄漏 + 按 mime 缓存(H1)**:`CodecExt` 的
  `getSupported{ColorFormat,ProfileLevels}For{Encoder,Decoder}` 原 `createEncoderByType()` 后只读
  能力、从不 `release()`(靠 GC finalizer 回收原生编码器);改为 `try/finally` 立即释放,编码器能力
  再按 mime 全局缓存,`outputCameraParameters()` 每次相机 bind 不再在主线程创建 4 个 MediaCodec。
- **camerax 每次相机 bind 复用相机静态数据(M4)**:`BaseCameraXFragment` 新增按 `cameraId` 记忆化的
  `CameraCharacteristics` 与 supported-size 访问器;`showAvailableRatio`/`getMaxPreviewSize`/
  `outputCameraParameters`/`CameraFragment.bindCameraUseCases` 复用之,消除同一次 bind 内重复的
  CameraService binder 查询与 supported-size 重算。行为不变。
- **camerax 亮度分析器默认停用 + 内部优化(H2/M1/M2/M3)**:`CameraFragment` 的 `LuminosityAnalyzer`
  每帧仅把亮度打一条 verbose 日志、无其它消费。亮度分析开关默认关闭,关闭时不创建或绑定
  `ImageAnalysis` use case,从而消除默认情况下的分析流和每帧处理开销;维护者可显式打开开关启用。
  分析器内部同时优化以便 opt-in 时高效:`averageLuma` 直接遍历
  `ByteBuffer` 并按 stride 采样(消除每帧整帧 `ByteArray` 分配与全像素求和),FPS 统计改用原始
  `LongArray` 环形缓冲(消除每帧 `Long` 装箱);非正 stride 会被显式拒绝,避免无效步长导致循环异常。
- **camera2live 相机方向不再每帧查询(M7)**:`Camera2ComponentHelper` 缓存 `SENSOR_ORIENTATION`
  (相机静态,仅切镜头时随 `characteristics` 重赋值变化),录制的 image-available 回调不再每帧读取
  `CameraCharacteristics`。行为不变。

### 安全 (Security)

- **CIP-1 `ZipUtil.unzip` 加固**:解压时对每个条目做规范化路径校验,拒绝 Zip Slip 路径穿越
  (`../`、绝对路径、软链父目录),并对单条目与归档总解压体积设上限(默认 200 MiB / 1 GiB)以缓解
  ZIP bomb。写入改为「临时文件 + 同目录备份 + rename」,替换失败会恢复原文件,且不遗留半文件。
  - **行为变更**:遇到路径穿越或超限的归档,现在抛出 `IllegalArgumentException`(此前会静默写出)。
  - **兼容**:新增可选参数 `limits: ZipUtil.UnzipLimits`,通过 `@JvmOverloads` 保留原两参数入口,
    既有 Kotlin/Java 调用无需改动。
- **HTTP-1 默认日志级别改为 `NONE`**:`BaseHttpRequest` 不再默认以 `BODY` 级别记录完整请求/响应。
  - **破坏性变更**:此前默认 `BODY`(会打印完整报文体和所有 Header)。现新增公开属性
    `BaseHttpRequest.logLevel`,默认 `HttpLoggingInterceptor.Level.NONE`;宿主需(建议仅 debug 构建)
    显式设置 `logLevel = BODY/HEADERS` 才会输出。
  - Header 日志按名脱敏(`Authorization`、`Cookie`、`Set-Cookie`、`Proxy-Authorization` 的值以 `██` 代替);
    请求头注入日志只记 Header 名,不再记录其值。
- **HTTP-3 日志体积双向有界**:请求与响应体日志各自最多缓冲/输出 256 KiB。请求日志使用
  有界 sink,即使自定义 `RequestBody.contentLength()` 虚报较小值也只保留 256 KiB + 1 个探测字节;
  不再将整个响应(`source.request(Long.MAX_VALUE)`)或请求体读入内存。duplex/one-shot/未知长度/
  超限的请求体一律省略。
  修复大响应/大请求下的 OOM 风险;业务读取报文体不受影响。
- **CIP-3 `GZipUtil.decompress` 加解压上限**:新增可选参数 `maxOutputBytes`(默认 64 MiB),
  超限返回 `null`,缓解 GZIP bomb。`@JvmOverloads` 保留原有入口,既有调用无需改动。
- **AR-7 `DeviceProp.getSystemPropertyByShell` 命令注入加固**:shell `getprop` 前对属性名做正则
  白名单校验(`^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$`),非法名记日志并返回 `""`,防止参数直拼命令注入。
  合法属性名(`ro.*`/`ril.*`/`gsm.*`)全部匹配,行为不变。
- **CIP-2 AES/PBKDF2 密钥副本清理**:`AESUtil` 不再通过不可清除的临时 hex `String` 派生密钥,
  改用与原大写 hex 编码完全一致的 `CharArray`,并在加解密或旧格式派生结束后清零;PBKDF2 provider
  路径在 `finally` 调用 `PBEKeySpec.clearPassword()`;API 21–25 的 HMAC-SHA256 回退路径会在
  `finally` 清零密码字节、每轮 PRF 中间值和派生输出缓冲,构造 `SecretKeySpec` 后也立即清零临时
  key 副本。密文格式和既有固定向量不变。

### 变更 (Changed)

- **P3 低风险清理与规范化完成**:`KeepAlive` 使用 `SupervisorJob` 并隔离 callback 异常，释放后清空
  `MediaPlayer`;`android-restricted` 和 `audio` 的目标路径不再调用 `printStackTrace()`，统一通过
  `LogContext` 保留异常上下文。audio 模块的 `runCatching` 路径统一通过内部 helper 保证
  `CancellationException` 继续向上抛出；`AacEncoder` 删除未使用且标有 FIXME 的 CSD 构造代码。
- **CX-9c 权限页改为一次性导航**:`PermissionsFragment` 在 `onViewCreated` 发起权限流程，通过
  `viewLifecycleOwner.lifecycle.withStarted` 等待可导航状态，并用一次性门控阻止同一 View 生命周期内
  重复导航；等待会随 View 销毁自动取消并重置门控，导航失败时也允许后续重试。
- **CPB-6 监听器分发采用快照语义**:`CircleProgressbar` 在状态和点击回调前复制监听器列表，允许
  回调安全地增加或移除监听器，不再触发 `ConcurrentModificationException`;本轮新增的监听器从下一轮
  分发开始生效。
- **LB-5 字节读取表达式等价清理**:`readByte()` 直接返回原字节，`readInt()`/`readIntLE()` 对每个
  无符号字节掩码和位移增加显式括号，并删除注释中的旧 hex 实现。返回值与既有契约不变。
- **R-9 audio teardown 维护性重构**:`MicRecorder` 统一录音循环失败清理、`AudioRecord` 释放与
  完成通知步骤；`AacStreamPlayer`/`OpusStreamPlayer` 共用模块内部停止协调器，集中维护 decoder
  摘除、scope 取消、`AudioTrack` 释放及 decoder 释放顺序。公开 API、同步/挂起语义及执行顺序不变。
- **CX-4 `BaseCameraXFragment.binding` 绑定 View 生命周期**:改用可空 backing `_binding` 并在
  `onDestroyView` 置空,修复视图重建后 binding 泄漏与释放后误用。新增只读访问器 `viewBinding`;
  公开 `binding` 继续保留 getter/setter,避免破坏既有子类的源码和 JVM 调用入口。
  `onDestroyView` 之后读取会抛 `IllegalStateException`。
- **CX-1 `SoundManager` 不再持有传入 `Context`**:构造参数改为私有存 `applicationContext`,移除公开
  Activity 引用;历史公开 `val ctx/getCtx()` 继续保留,但返回 application context,避免 API/ABI 破坏。
  `soundPool` 改可空、支持 `release()` 后重建,
  未加载时 `play*` 变 no-op(不再崩溃)。
- **CAM2-3 `Camera2ComponentHelper.switchCamera` 由同步改为异步**:切换现在会先关闭旧设备并等待其
  `onClosed`(经 `Mutex` 串行化、关闭信号绑定到具体 `CameraDevice`),关闭超时(默认 3s)则中止本次切换
  并通过新增的 `cameraErrorListener` 上报,不再直接打开新设备;`lensSwitchListener` 仅在切换成功后回调。
  方法签名不变(仍返回 `Unit`),但完成时机变为延迟;连续请求不会取消已开始的切换,而是串行处理并在
  打开设备前读取最新目标镜头。
  `switchToFrontCamera()`/`switchToBackCamera()` 同步语义随之变化。
- **AUD-9 PCM16 输入改为完整帧契约**:`ByteArray.toShortArray()`、`toShortArrayLE()` 以及
  `AudioTrackPlayer.write()` 现在拒绝奇数字节长度并抛出 `IllegalArgumentException`,不再静默丢弃末尾
  字节。`AudioTrack.write()` 的负错误码保持原值返回,不再错误地乘以 2。
- **AUD-10 音频最小缓冲参数校验**:`AudioTrackPlayer` 和 `MicRecorder` 要求缓冲倍率为正数,并在
  `getMinBufferSize()` 返回错误码或计算溢出时立即抛出 `IllegalArgumentException`,不再用无效大小构造
  音频对象。
- **CPB-3/5 进度范围收敛**:`CircleProgressbar.maxProgress` 最小为 1,`currentProgress` 被限制在
  `0..maxProgress`;XML 初始化和状态恢复同样执行约束。动画时长会同步到已有 animator,状态恢复缺值
  回退到 idle,并新增状态描述供无障碍服务读取。

### 修复 (Fixed)

- **CAM2-10 编码队列丢帧可观测**:`CameraAvcEncoder` 的有界队列丢弃最旧帧时会通知编码器;
  首个丢帧立即记录 warning,持续背压时每累计 30 个丢帧再记录一次,避免静默丢帧和日志洪泛。
  队列容量、非阻塞写入和保留最新帧的策略不变。
- **P3 健壮性边界补齐**:`GZipUtil.isGzip()` 对 0/1 字节输入返回 `false`;相机特征缺值采用明确异常或
  安全默认值，native bitmap 处理返回空时回退 Android 镜像/旋转；亮度计算改用无装箱累加并始终关闭 `ImageProxy`;
  `CircleProgressbar` 回调期间可安全修改监听器列表。
- **P3 日志与错误处理补齐**:`AppExt`、刘海屏适配、动态 Activity 启动、KeepAlive 和 audio 资源操作
  记录完整 throwable;动态 Activity 类不存在时记日志并安全返回。挂起音频释放继续重抛
  `CancellationException`，普通释放异常保持记录后兼容返回。
- **HTTP-6/7 测试与日志收尾**:补充明文/二进制判断、Content-Encoding、observer 错误分类和二进制
  响应日志测试；二进制响应不再提前返回，仍会输出统一末尾分隔线，并移除无效 suppress。
- **R-1 `BaseMediaCodecSynchronous.process()` 输出 buffer 为空不再死循环**:`getOutputBuffer()`
  返回 `null`(index 被并发 flush/release 失效)时改为 `break` 退出本轮 drain,不再用同一 index
  无限自旋;外层 worker 循环下一轮经 `ensureActive()` 正常响应取消。修复潜在 CPU 自旋与释放期阻塞。
- **R-2 `Camera2WithoutPreviewActivity.onDestroy` 调用 `release()`**:不再只 `stopCameraThread()`;
  改调 `Camera2ComponentHelper.release()`,取消 helper 自有 `cameraScope` 并关闭设备,修复
  `onStop` 重新初始化后协程与 Activity 泄漏。同时在 `release()` 补充公开生命周期契约(宿主须在
  `onDestroy`/`onDestroyView` 调用)。
- **R-3 `Camera2ComponentHelper.switchCamera` 连续切换保持关闭/打开流程完整**:新请求不再取消
  已进入关闭/打开流程的任务;切换请求通过 `Mutex` 串行处理,并在打开设备前读取最新目标镜头,
  确保旧设备 `onClosed` 完成后才打开新设备。`BaseCamera2Fragment` 同时按 ToggleButton 的
  `isChecked` 明确请求前置/后置镜头,避免连续点击丢失最后一次选择。
- **R-4 codec worker 与生命周期操作串行化**:`BaseMediaCodec` 使用共享可重入锁串行执行同步 worker
  的完整 `process()` 迭代以及 `stop()`/`flush()`/`release()`;旧同步 `release()` 在取消 worker 后会
  等待当前 codec 迭代退出再释放底层对象,不再与 `dequeue/queue/releaseOutputBuffer` 并发。`releasing`
  标志继续避免主动关停被误报为 codec 失败或正常 EOS,并新增并发回归测试。
- **R-5 空闲解码不再刷屏(部分修复)**:`process()` 仅在真正 drain 到输出时才打印 "Decode cost"
  日志,消除流静默时每秒约 20 条日志;更正 `AacDecoder.onInputData` 过时注释。**说明**:空输入仍
  提交 0 字节 buffer 归还输入槽——按 Codex 建议,消除该 churn 的结构性改动(先等数据再 dequeue /
  pending-index 状态机 + 取消/flush/EOS 测试)因风险较高**暂缓**,待补测试后再做。
- **R-7 相机初始化失败清理**:`initializeCameraAndAwait` 在 `openCamera` 成功但后续 setup
  (`setImageReaderForPhoto`/`setPreviewRepeatingRequest`)抛异常时关闭对应设备,并在 `NonCancellable`
  清理区等待其 `onClosed`(最长 3s)后再重抛,避免残留 CAS 登记或立即重试撞上仍在关闭的设备。
- **R-8 `MicRecorder.stopRecordAndJoin` 守卫前置**:自调用守卫 `require(job !== 当前 Job)` 移到
  `stopped` CAS 与 `audioRecord.stop()` **之前**,自调用 fail-fast 不再半停止录音器。**注意**:仅拦截
  直接自调用;`runBlocking` 嵌套的间接自调用仍无法在此安全处理。
- **R-6 `ZipUtil.unzip` 不再静默残留备份**:当条目路径被已有**目录**占用时,前置 `require(!isDirectory)`
  响亮拒绝(不再把非空目录改名为备份后 `delete()` 静默失败残留 `.unzip-backup-*.tmp`);成功路径的
  备份删除失败也改为记日志而非忽略返回值;新增目录冲突回归测试。
- **R-10 `HttpLoggingInterceptor` 请求体日志截断保持内存与处理量有界**:
  `captureRequestBodyForLogging` 在写入 256 KiB+1 探测字节时先置 `truncated`,再抛出无堆栈哨兵以
  提前终止 body 生成;即使 `writeTo()` 吞掉该异常,截断状态也不会丢失。补充精确边界、吞异常及
  提前终止生产的回归测试。
- **CAM2-2 相机打开阶段 `onDisconnected` 关闭设备并抛异常**:打开期间断连现在 `device.close()` 并以
  `IllegalStateException` resume(带 `isActive` 守卫防二次 resume),不再仅打日志(TODO)。
- **CAM2-6 相机线程/执行器随视图释放**:`stopCameraThread()` 追加 `singleExecutor.shutdown()`;
  helper 使用可在 `release()` 中取消的自有 scope,不再把 Camera 操作挂到 Activity lifecycle;
  `BaseCamera2Fragment.onDestroyView()` 释放 helper,且每次 `onViewCreated` 重置幂等状态,支持同一
  Fragment 实例的多次 View 重建。
- **CAM2-7 移除 `onStop` 主线程 `sleep(100)`**:`stopRepeating()` 改用 `stopRepeating()+abortCaptures()`
  串行关闭,不再固定睡眠阻塞 UI 线程,消除前后台快速切换时的 ANR 风险。
- **CAM2-8 相机挂起操作可取消**:`createCaptureSession` 改为 `suspendCancellableCoroutine`,取消后
  配置完成的 session 会关闭;`openCamera` 同步捕获权限/CameraAccess 异常,并在取消后关闭迟到的
  `onOpened` 设备。打开设备状态改由 `AtomicReference` 管理,登记及按设备身份清理均使用 CAS,
  防止 Camera HandlerThread 的迟到回调覆盖或清除主线程上的新设备状态。
- **CX-2 `captureForBytes` 始终关闭 `ImageProxy`**:`onCaptureSuccess` 用 `image.use{}` 保证关闭;
  UI 操作改走 `viewLifecycleOwner.lifecycleScope`,错误路径不再用 `requireActivity()`(Fragment 可能已
  detach),按视图生命周期守卫后再回调 `onImageSaved`,修复 ImageProxy 泄漏与 detach 后崩溃。
- **CX-3 触碰 View/binding 的协程改用 `viewLifecycleOwner.lifecycleScope`**:相机回调(可能离页后触发)
  保留 `lifecycleScope` 但在 `withContext(Main)` 内 `if (!isAdded || view == null) return`,修复
  「进入相机→立即返回」时向已销毁视图回调导致的崩溃。
- **CX-5 `CameraFragment` 相机绑定改用 `viewLifecycleOwner`**:`bindCameraUseCases()` 原将 use-case
  绑定到 Fragment(`this`),View 销毁但 Fragment 保留时相机仍绑定、持有旧预览 Surface;改为绑定
  `viewLifecycleOwner`(与 `VideoFragment` 对齐)并在函数入口加 `if (!isAdded || view == null) return`
  守卫;相机切换动画结束后的延迟 UI 恢复改由当前 View 的 `lifecycleScope` 执行,修复动画中途离页后
  访问已清空 binding 的崩溃,以及跨 View 重建的 Surface 泄漏/相机占用。
- **LB-1 `ByteBuffer.copy()` / `copyAll()` 保留字节序**:复制时对目标 buffer 调用 `order(order())`,
  修复小端 buffer 复制后字节序被重置为大端导致的静默数据损坏。
- **LB-3 `ByteArray.toAsciiString()` 明确严格 ASCII 契约**:原实现 `it.toInt().toChar()` 对 ≥`0x80`
  的字节符号扩展成 U+FF80..U+FFFF 乱码;改为逐字节 `and 0xFF` 后要求 `0..127`,非 ASCII 字节
  fail-fast 抛 `IllegalArgumentException`(含索引与十六进制值),并补 KDoc 与单元测试。对 0..127 输入
  行为不变。**行为变更**:含高字节的输入由静默乱码变为抛异常。
- **HTTP-2 `HttpRequest` 并发安全**:不再共享并复用同一个可变 `Retrofit.Builder`;改为保存不可变
  header 快照,每次 `getRetrofit(baseUrl)` 新建 builder,避免并发不同 `baseUrl` 时构建出错误 Host。
- **AUD-1 `BaseMediaCodec` 确定性释放(T4)**:新增 `suspend fun releaseAndJoin()`,先
  `cancelAndJoin` codec worker 再取消 `ioScope` 并幂等释放 codec(`releaseCodecOnce()` 原子只执行一次);
  含自 join 守卫。旧 `release()` 保留同步释放语义和 JVM 签名,但先取消 worker/scope 再释放 codec;
  需要“返回即 worker 已退出”的调用方迁移到 suspend 入口。
- **AUD-2 `AacDecoder.onInputData` 可取消(T4)**:阻塞 `queue.take()` 改为
  `queue.poll(50ms)`,协程取消可及时唤醒退出;无数据时仍提交空 input buffer,确保每个从 MediaCodec
  dequeue 的输入槽都被归还,避免输入槽耗尽后永久停解。
- **AUD-3 `MicRecorder` 确定性停止(T4)**:新增 `suspend fun stopRecordAndJoin()`,先 `stop()` 让
  native `read()` 返回,再 join 录音任务并通过 encoder wrapper 的 `releaseAndJoin()` 等待编码器释放;
  `onStop` 幂等且只在资源释放完成后回调。旧 `stopRecord()` 保留同步释放兼容语义。
- **AUD-4 Stream Player 并发串行化**:`AacStreamPlayer`/`OpusStreamPlayer` 用私有锁 + generation
  串行化 init/decode/flush/stop,`dropFrameCallback` 移出锁;修复 `audioDecoder`/`csd` 无锁 TOCTOU。
- **AUD-5 解码器初始化失败回滚**:init/`play()` 成功才提交 `csd0`(OPUS 一并 csd1/csd2);失败回滚并释放,
  维持不变量「`csd0 != null` ⇔ decoder 就绪」,消除下一帧 `!!` 崩溃路径。
- **AUD-6 `MicRecorder` 读缓冲复用**:改为固定容量 `ShortArray(bufferSize/2)`,每帧仅切片;
  修复原逐帧 `copyOfRange` 覆写导致缓冲永久收缩。
- **AUD-7 `AudioRecord.read` 负错误码处理(T8)**:`>0` 编码/回调、`==0` 继续、`<0` 记日志并停止;
  录音循环 rethrow `CancellationException`,不再吞异常静默停止。
- **AUD-8 `process()` 异常不再伪装正常 EOS(T8)**:catch 顺序 `CancellationException`(rethrow)→
  `MediaCodec.CodecException`(先于 `IllegalStateException`)→ `IllegalStateException` → 泛型
  `Exception`,均 `return false` 终止;错误经独立 `notifyCodecFailure()` 上报,不再 `return true` 忙循环。
- **ABN-1 `KeepAliveReceiver.onReceive` 不再用 `GlobalScope`**:改用 `goAsync()` + 局部
  `CoroutineScope(SupervisorJob())`,`finish()` 置于 `finally`(约 10s 窗口),rethrow
  `CancellationException`;移除 `@OptIn(DelicateCoroutinesApi)`。修复广播提前结束/异步任务泄漏。
- **AR-1 `DisplayCutoutManager` 不再进程级单例**:保留 `SingletonHolder` 继承和既有 JVM bridge,
  但覆写 `getInstance(activity)` 为每次返回独立实例,不再固定持有首个 Activity 导致泄漏
  (本类无可变共享状态)。公开 API/ABI 不变。
- **CPB-1 `CircleProgressbar` 无限动画随生命周期取消**:`onDetachedFromWindow` 取消动画,
  `onAttachedToWindow` 在仍为 indeterminate 时恢复,`setIdle/setFinish/setError` 首行取消动画,
  `setIndeterminate` 仅在 View 已 attach 时启动,修复构造后未 attach 或 detach 后切状态仍持续运行的耗电。
- **CPB-2 `CircleProgressbar` 支持 `wrap_content`**:新增 `onMeasure`,按图标 + 进度环 + padding
  计算期望尺寸,修复 `wrap_content` 退化为 0×0 不可见。
- **CAM2-4/5 相机错误与方向回退**:`openCamera`/`switchCamera` 捕获并上报权限及 CameraAccess 错误;
  JPEG 方向在 display 或 sensor orientation 缺失时使用安全默认值,不再因 `-1`/null 崩溃。
- **CAM2-9/10 编码回调与背压**:`CameraAvcEncoder` 在 configure 前注册 callback,API 23+ 使用专用
  HandlerThread 并在释放时退出;输入队列最多保留 5 帧,拥塞时丢弃最旧帧。公开 queue getter 类型不变。
- **ABN-2/3/4 androidbase 健壮性**:`LeoTextureView` 的 MediaCodec callback 在 configure 前注册并在
  API 23+ 使用可释放的专用线程;Watermark 对空文本、非正字号和零间距安全返回/钳位;`WifiUtil` 仅
  持有 application context。
- **AR-2/3/4/5/6/8 android-restricted 修复**:vivo 刘海设备使用正确实现并按 density 计算 dp;
  `ApplicationManager` 延迟获取 Application,反射失败保留 cause 并给出初始化提示;读取 build.prop
  自动关闭流;`DeviceUtil` 仅持有 application context。
- **AUD-11/12 录音与 AAC 输出校验**:`MicRecorder` 在录音前检查初始化及实际 recording state,权限或
  状态失败时只释放和回调一次;`AacEncoder` 在 csd0 缺失或不足 2 字节时丢弃输出,不再生成全零 ADTS。
- **CX-6/7 CameraX 空值与扩展缓存**:媒体预览和 URI 实际路径缺失时记录并安全返回;
  `ExtensionsManager` future 按 `ProcessCameraProvider` 缓存复用,失败后允许重试,视图销毁后不再回调 UI。
- **CPB-4 Drawable tint 移出绘制热路径**:图标在赋值或 tint 变化时 `mutate()` 并更新颜色,不再每帧
  `setTint()`。
- **HTTP-5 HTTP 诊断兼容性**:`BaseProgressObserver.onError` 记录完整 throwable;响应体判断改用公开
  API 复刻 HEAD、204、304、Content-Length 和 chunked 语义,不再依赖 OkHttp internal API。
- **LB-4 ByteBuffer 扩展测试**:覆盖 remaining 读取、copy/copyAll 内容与源状态恢复、大小端继承和空
  buffer。

### 新增 (Added)

- **HTTP-4 `BaseProgressObserver.cancel()` / `isDisposed`**:暴露取消入口,调用方可在页面销毁时
  终止订阅,避免向已销毁界面回调。
