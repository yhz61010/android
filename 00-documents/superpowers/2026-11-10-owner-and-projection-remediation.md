# 生命周期与投影复审修复记录（2026-11-10）

## 范围与授权

本轮承接 2026-11-09 相对本地 `master@97c84d242` 的只读复审，当前提交仍为 `739928440`。
用户确认四项 P2 与两项 master 遗留问题全部修复，并明确截图时持续录制，不采用暂时切换投影输出的方案。
本轮保留 2026-11-06 的未提交修复；不提交、不推送，不恢复版本升级 stash，不修改预编译 Native 产物。

计划见 [修复计划](plans/2026-11-10-owner-and-projection-remediation.md)。原六项与相邻生命周期修复已通过完整矩阵；
2026-11-11 已完成收尾追加的前台服务启动与通知导航修复及 Demo 完整复验。
用户明确通知点击返回当前录屏页。下文分别列出全仓历史矩阵与最终 Demo 验证，不混用覆盖范围。

## 1. 同步 codec 失败通知

`BaseMediaCodecSynchronous.process()` 改为私有 suspend，捕获 hook 的 CancellationException 时先检查
worker 是否真正取消。worker 仍 active 的异常在离开 codec 操作锁后进入 FAILED 并通知 owner；
真正取消与释放路径不误报。真实 AacDecoder 的输出回调和 input/output/format hook 均有回归，
覆盖 worker 结束后仍接受输入、却不执行 errorCallback 的旧行为。

## 2. WebSocket owner

Activity 的 Disconnect 与 Release 使用同一 clientMutex 包住整个异步生命周期。按钮点击捕获目标
client，实际执行时再比对身份；完成 release 后才清 owner，销毁先等待 connect 退出再复用释放入口。
测试使用真实 Activity/client 与受控的外部 ChannelPromise，没有 mock release 的返回值。
Netty terminationFuture 完成和 listener 派发存在次序差异，测试显式等待派发，再推进测试调度器。

## 3. Wi-Fi 失败注销

`NetworkUtil` 在注销时立即删除业务 lambda key、detach listener，失败只把 framework callback 与
application manager 移交后台 owner。任务按 1 秒起、最大 30 秒指数退避，集合空时关闭线程。
新注册在 pending 期间被拒绝，既有注册继续运行；NetworkMonitor 的 ping/traffic 仍工作，之后重新
start 才重建 Wi-Fi 订阅。Activity 不需要保留已销毁 monitor，也无需再调用注销来驱动重试。

后台 worker 只在锁内取快照与收尾，framework Binder 调用在锁外。复审先用 latch 阻塞注销复现了
全局锁阻塞其它生命周期入口，再修正该窗口。AOSP 对已注销 callback 正常返回，代码没有依赖 OEM
异常字符串，或把所有 IllegalArgumentException 猜成成功。

公开迁移：`unregisterWifiNetworkStatsAboveAndroidS(callback)` 删除 Context 参数；返回 false 只表示
即时注销失败，清理所有权已经移交。所有 Kotlin 调用、KDoc 与 CHANGELOG 同步更新。

## 4. GL 文件播放逐帧消费

`AwaitedFrameRenderer` 用 producer Mutex 和一个 pending request 对接 GL 线程。准备尺寸/像素与
实际 draw 共用 renderer monitor，通知在该锁外完成；普通预览上传不能在 prepare 与 draw 之间插入。
等待可取消，取消后未消费的闭包被移除；GL 已取走的请求允许完成绘制，其数据是独立 JVM ByteArray，
并不借用 decoder native 内存。普通异常交还 producer，fatal error 继续在 GL 线程抛出。

两个 FFmpeg 播放器对 decodeFrames 和 EOF drain 的每个输出调用公开 renderAndAwait，再按输出帧
限速；不再对整个输入 packet 只 sleep 一次。close 取消并等待 decode job 退出后才释放 native decoder。
该 API 只确认 GL 消费，不宣称 GPU 已完成或帧已被物理显示。

复审补出的旋转比例窗口也已修正：当前 Surface 尺寸优先，setVideoDimension 的传入渲染尺寸仅是
初始化回退，不会把 onSurfaceChanged 算好的比例覆盖成缓存的旧屏幕尺寸。回归使用 4×2 视频、
400×800 → 800×400 Surface，旧代码实际得到 -0.25 而非应有的 -1，已确认有效红绿。

真实 Renderer 接线测试需要 JVM parent loader 能加载 GL10；仅为测试运行时增加 Khronos OpenGL
API 接口依赖。Robolectric 的 javax 包委派不能通过 instrumentedPackages 改写，不用残缺接口替身。
此依赖不进入生产发布；没有恢复工具链或依赖批量升级。

## 5. 单 VirtualDisplay 与持续截图

MediaProjection 输入由稳定的 SurfaceTexture/OES 纹理承接，专属 EGL 线程持有 context、pbuffer、
投影输入 Surface 和编码器 EGL 输出。每帧更新纹理、转换矩阵与时间戳，再绘制到 encoder Surface。
截图在相同纹理上使用独立 FBO/readPixels，显式转换 RGBA→ARGB 和纵向翻转，不切换投影 Surface。

旋转只 resize 同一 VirtualDisplay、调整输入缓冲尺寸并替换 encoder/EGL 输出，不再次使用同一授权
createVirtualDisplay。每个 encoder 有独立 session/操作锁，native callback 与 retirement 互斥；
发送队列真正交付前复查 session，未开始交付的旧帧被丢弃，旧 native 操作/CSD 更新不能跨越 retirement。
业务 listener 不在资源锁内执行；已经选中并开始执行的 listener 可在 stop/rotation 后尾随完成，
调用方必须按自身生命周期隔离副作用，不能把停止视为在飞业务回调的完成屏障。

stop 等价 one-shot release，下一轮需新策略及新授权。EGL retirement 最多等待两秒；超时或中断后，
清理闭包仍由 EGL 线程持有，不能由调用线程提前释放借用 codec/Surface。实际清理先处理 GL，再释放
借用资源；晚到失败仍记录日志，不因调用者已经超时而无人观察。上游 producer 可以被系统/Service
提前停止，这不等于释放应用拥有的 EGL/codec/Surface。

截图维度、队列过载同步拒绝，普通单次截图或 callback 异常不停止录制；未交付 Bitmap 在取消时回收。
截图失败产生的普通 GL error 不能污染下一次 encoder draw，context-lost 仍交终态清理。
活动 codec/输出/GL 失败会立即关闭门禁，释放实际完成后才在主线程单次通知 onError；释放等待超时
不能提前通知，错误回调内可重入 onRelease。系统撤销投影作为正常终止处理。

## 6. Service 与相邻退出边界

stop/release/unbind/destroy 共用最终 cleanup FIFO，保留已排队工作。清理路径在 lifecycleLock 内只完成门禁、
owner 快照、发布与排队；线程在该锁外执行 processor stop/release、流 flush/close、projection stop 兜底，各步失败仍继续，
最后清 owner，再 quitSafely。销毁不在主线程 join native 清理。

截图保存也进入该 FIFO：执行前复查 generation；已经开始的保存先于清理完成，不会跨入下一会话。
stale 或 post 失败时回收 Bitmap。Service 通过带 generation/投影身份的弱引用 callback 响应系统撤销，
注销失败保留原句柄，下次启动或清理时重试，不能让新会话覆盖。活动会话的 onError 在同一生命周期
锁内复查代际并排队最终清理，旧错误不能停止替换后的会话。

Activity 区分服务已启动、绑定请求已接受与实际连接三个状态，销毁时撤销尚未完成的绑定请求并
停止已启动服务；迟到连接不再安装业务 listener。Timer/task 在销毁时取消并清空，排入主线程的
旋转与延迟截图在实际执行时复查销毁代际，数据 UI 更新复查销毁状态与服务身份；替换服务时解除旧 listener。

API21/22 的 codec 初始化同样补齐 worker 所有权：创建到 setCallback 结束前 codec 仅由创建线程拥有，
通过 SynchronousQueue 成功交接后 caller 才接管。timeout/interrupt 不强制释放正在 JNI 初始化的 codec；
未交接实例在 worker finally 释放。策略、LeoTextureView、CameraAvcEncoder 的相同模式一并核对修正。

## 7. 分阶段证据与最终验收

- 同步 codec：6 条新增回归先确认 4 项失败、两项取消对照通过；修复后 audio 全套 97 条通过，质量检查通过。
- WebSocket：两条 owner 红测；修复后转绿。
- Wi-Fi：业务 key 保留、后台 IPC 持锁先红后绿；两库全套与 Demo 定向合计 114 条通过，662 个任务实际执行。
- GL：三条批量/取消/错误红测；修复后 8 条初轮回归通过，旋转比例补修后 9 条通过，109 个任务实际执行。
- Projection：旋转重复 VD/截图/one-shot 先红；随后补 EGL 边界、像素、迟到 callback、超时 retirement、
  截图 GL error 污染与 API22 handoff。各次日志单独保留，不用一次历史绿覆盖之后的修改。
- Service：线程未退出、stop 未 release、截图保存线程与 FIFO 次序先红；58 条 Demo 全套及质量检查曾通过，
  系统撤销投影/Timer/绑定与错误通知、迟到 UI 的后续定向 24 条通过，并由下述完整矩阵复验覆盖。
  Timer 回归使用真实 Timer/task 与 UI 队列，物理 rotation getter 使用边界替身，不能证明真实 Display 关联。

同一 Throwable 被多个清理步骤复用的边界测试在产品不变时已通过；本机编译字节码与 Kotlin stdlib
确认 addSuppressed 扩展本身跳过同一异常对象，未将 Java 成员语义的疑点误报为产品缺陷。

最终首轮完整矩阵实际执行 2019 个任务，102 个测试套件共 532 条测试通过，无失败、错误或跳过，
Demo assemble 通过；该轮整体 exit 1，原因是新增 Timer 测试三处换行格式、Wi-Fi 后台私有辅助函数的
API31 契约标注、相机私有 enqueueCameraSwitch 的 CAMERA 契约标注。格式已修正；Wi-Fi 三个辅助
入口均只由 SDK31+ 注册/注销分支或其 worker 到达，相机两个调用入口原已要求 CAMERA，因此补齐
内部注解即可，不提高最低版本、不扩大公开权限要求，不使用 SuppressLint 掩盖。
修正后的同一完整矩阵 exit 0，`BUILD SUCCESSFUL in 6m 20s`，2019 个任务全部实际执行。
该轮 XML 交叉核对为 102 个测试套件、532 条测试，零失败、零错误、零跳过；36 个子项目的
ktlintCheck 和 detekt 均实际执行，九个库的 lintDebug 与 Demo lintDevDebug、assembleDevDebug 通过。
日志：本机 `/tmp/fix-six-final-matrix2-nov10.log`，计数快照 `/tmp/fix-six-final-test-counts2-nov10.json`。

收尾检查另确认前台服务启动时序的既有缺陷：Activity 创建即 startForegroundService，但只有用户
授权成功后才 startForegroundNotification。等待或拒绝授权会使服务无法及时提升；不同 Android
版本的异常类型不同，不能把 API26+ 统一写成同一个异常名。依据
[Android 官方前台服务故障排查](https://developer.android.com/develop/background-work/services/fgs/troubleshooting)，
已改为先绑定，授权成功且仍有有效连接 owner 后再启动并立即提升；通知失败会 stopService 撤销
started owner，并保留原始异常。Service 返回 START_NOT_STICKY，避免重建无法恢复授权的空会话。
此项及通知目标由下述最终 Demo 复验覆盖，不能视为已由 11 月 10 日矩阵覆盖。
通知 PendingIntent 同时误以 BasicFragment 为 Activity 目标。用户已明确点击返回当前录屏页，
目标已改为 ScreenShareMasterActivity，使用 NEW_TASK | CLEAR_TOP | SINGLE_TOP 复用已有页面，
没有 CLEAR_TASK；回归核查真实通知 PendingIntent 的目标、三个 flags 及 Manifest 中可解析的 Activity。

所有 Gradle 使用 JDK 17 与仓库 wrapper，经共享 flock 锁串行执行，日志保存在本机 `/tmp`。
完整矩阵命令如下（首轮与修正后的复跑使用同一命令）：

```bash
flock /tmp/android-review-gradle.lock ./gradlew \
  testDebugUnitTest :demo:testDevDebugUnitTest :demo:assembleDevDebug ktlintCheck detekt \
  :audio:lintDebug :androidbase:lintDebug :lib-common-android:lintDebug \
  :camera2live:lintDebug :opengl:lintDebug :screencapture:lintDebug \
  :lib-json:lintDebug :lib-compress:lintDebug :draw-on-screen:lintDebug :demo:lintDevDebug \
  --continue --rerun-tasks --max-workers=1 --console=plain
```

2026-11-11 最终 Demo 复验命令：

```bash
flock /tmp/android-review-gradle.lock ./gradlew \
  :demo:testDevDebugUnitTest :demo:ktlintCheck :demo:detekt \
  :demo:assembleDevDebug :demo:lintDevDebug \
  --continue --rerun-tasks --max-workers=1 --console=plain
```

结果 exit 0，`BUILD SUCCESSFUL in 3m 43s`，1174 个任务全部实际执行。XML 为 12 个测试套件、
75 条测试，零失败、零错误、零跳过；五个直接验证入口均执行通过。该次在原全仓矩阵之后只调整
Demo 及文档，库源代码未再改变；不将旧 Demo 的 69 条与本次 75 条重复相加。
日志：本机 `/tmp/projection-owner-final-nov11.log`；计数 `/tmp/projection-owner-final-counts-nov11.json`。

修正后的等待授权测试使用真实 Activity、绑定与权限结果派发。Robolectric 4.16.1 的成功 bind 也
写入 allStartedServices，已通过本机字节码核实，故授权前应只有 1 条绑定记录，提升通知时应为
绑定加 start 共 2 条。网络初始化用未推进的测试 coroutine dispatcher 隔离，未 mock Activity
或生产启动返回值。受控临时回插 eager start 后，唯一等待授权用例精确失败；finally 将源文件
逐字节恢复，日志为 `/tmp/projection-fgs-eager-red-nov11.log`。随后才运行上述完整绿色验证。
15 秒虚拟等待证明未提前调用启动入口，不模拟 Android 系统的前台服务看门狗或真实通知点击。

最终源代码与文档复核未发现其它明确问题；git diff --check、文档路径、代码围栏与新增文档空白
检查通过。保留全部已有修改与未跟踪文件，未暂存、提交或推送；未修改工具链升级 stash 或 Native 二进制。

## 8. 未验证项

2026-11-10 仅用 adb 只读核对到一台 P3H、API30 设备；整个修复过程未安装或运行 Demo，
未执行真机、仪器测试或真实媒体播放。JVM/Robolectric 的 framework、GLES/EGL 边界替身不能
证明真实 GPU 纹理方向/颜色、API21～26 与新设备的厂商 Codec、旋转录屏、系统撤销、长时性能或 Wi-Fi。
需在旧 API 与新设备验证连续录制中旋转/截图、后台/前台、反复进入退出、声音/画面、PTS/帧率/丢帧和
内存，并验证通知点击后复用原录屏页、录制不中断。现有 API30 设备不构成 API21～26 与 API34+
两类目标设备覆盖。Native 预编译库未变，本轮没有新增 ABI 加载、sanitizer 或 16 KB 页设备验证结果。
