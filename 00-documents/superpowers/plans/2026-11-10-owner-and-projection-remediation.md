# 生命周期与投影复审问题修复计划

**目标：** 修复 2026-11-09 相对 `master@97c84d242` 审查确认的四项 P2，以及同时列出的两项 master 遗留风险。

**授权：** 用户要求六项一起修复，截图必须保持持续录制并同时截图；不采用临时切换投影输出 Surface 的停顿方案。用户只要求不明确时先询问，已明确的修复直接实现，不另设计划审批。不提交、不推送。

**架构：** 保留会话单 owner；框架注销重试与业务监听者解耦；文件播放逐帧等待 GL 消费；MediaProjection 仅创建一次 VirtualDisplay，稳定 SurfaceTexture 输入经 EGL 分发到编码器和截图 FBO；Service 排空清理队列后退出线程。

**技术栈：** Kotlin、协程、Android MediaCodec/MediaProjection、EGL/OpenGL ES 2.0、JUnit/Robolectric/MockK。

## 约束与交错

- 保留当前所有未提交及未跟踪文件；不恢复工具链升级 stash，不修改 Native 二进制或 Claude 配置，不暂存、提交或推送。
- 最低 API 21；公开 API 改动以最佳方案为准，KDoc 与 CHANGELOG 记录迁移，不保留兼容壳。
- Gradle 统一经 `flock /tmp/android-review-gradle.lock` 串行，日志写 `/tmp`；高风险回归使用 `--rerun-tasks --max-workers=1`。
- 测试覆盖 worker 自行抛取消与真正取消、断连尚未结束就释放、销毁后无人再调用注销、GL 未绘制时连续提交与取消、旋转时编码器替换、截图排队时关闭。
- Mock/Robolectric 不证明真实 Codec、EGL、厂商网络行为或音画结果；交付单列真机缺口。

## 1. 同步 codec 取消失败通道

文件：`audio/.../BaseMediaCodecSynchronous.kt` 与 `BaseMediaCodecSynchronousCancellationTest.kt`。

- [x] 迁入 input/output/format 主动取消及真实 AacDecoder 输出回调四例红测，另加真实 worker 取消、release 取消对照。
- [x] `process()` 改为私有 suspend：catch CE 内先 `currentCoroutineContext().ensureActive()`，活动 worker 的 CE 在 codec 锁外记录 FAILED 并通知 owner，release 中不误报。
- [x] 全 audio 单测 97 条、ktlint/detekt 通过；仍待最终组合复验。

## 2. WebSocket owner

文件：`demo/.../websocket/WebSocketClientActivity.kt` 与 `WebSocketClientActivityLifecycleTest.kt`。

- [x] 用真实 Activity/client 与可控外部 disconnect future 先确认 Release/Destroy 提前丢 owner 的两例失败。
- [x] Disconnect/Release 使用同一 `clientMutex` 包住完整异步生命周期；点击捕获目标身份，锁内复查，释放结束后清字段；销毁取消并等待 connect 后复用同一释放入口。
- [x] 两例回归、Demo ktlint/detekt 通过；测试显式等待 Netty future 的 listener 派发后再推进 coroutine scheduler。

## 3. Wi-Fi 失败注销独立 owner

文件：`lib-common-android/.../NetworkUtil.kt`、NetworkMonitor 与相关测试。

- [x] 真实 NetworkUtil + framework ConnectivityManager 边界回归：注销失败立即解除业务 map key/listener，销毁 Activity 后无调用方介入仍自动注销。
- [x] pending 只持有 application manager 与 detached framework callback；单后台任务按 1 秒起、最大 30 秒退避，集合空时关闭调度线程。
- [x] pending 期间拒绝新增 Wi-Fi 订阅以避免累积，已注册订阅继续；ping/traffic 不受影响。
- [x] 删除注销函数无用 ctx 参数，KDoc 记录迁移，更新全部调用；不靠异常字符串推断 OEM 已注销状态。
- [x] 全 lib-common-android/androidbase 单测和质量检查，Demo 集成编译。

## 4. 批量帧 GL 消费确认

文件：`opengl/.../AwaitedFrameRenderer.kt`、`GLRenderer.kt`、`ui/LeoGLSurfaceView.kt`；两个 FFmpeg Demo 播放器与测试。

- [x] 红测确认三个行为失败：批量输出不得覆盖未绘制帧、取消未绘制请求不上传、绘制错误交还 producer。
- [x] `AwaitedFrameRenderer.render(prepare, requestDraw)` 用 Mutex 串行 producer，单 pending 请求的 CompletableDeferred 由 `drawFrame(draw)` 完成；准备与绘制共用 renderer monitor，通知在锁外。
- [x] 取消移除 pending 并释放 producer 锁；GL 已取走的帧可完成绘制，不能再触碰 decoder 资源；普通重绘仍绘制当前帧。
- [x] 新增公开 `LeoGLSurfaceView.renderAndAwait(...)`，把尺寸与像素在 GL 线程同时应用，surface 暂停时可取消等待；它确认 GL 已消费，不宣称物理显示或 GPU 完成。
- [x] 两个文件播放器的普通输出和 EOF drain 都逐帧 await，30 fps 限速按输出帧执行，移除按输入 packet 的 sleep；close 取消 producer 后再释放 decoder。
- [x] opengl 单测/质量检查和 Demo 编译；就近补消费/错误/取消对照。

## 5. 单 VirtualDisplay 与连续截图

文件：`ScreenRecordMediaCodecStrategy.kt`、新增内部 `ProjectionEglSession.kt`、策略及 EGL 边界测试。

- [x] 先确认旋转再次 createVD 与截图 stop/切换投影输出的失败用例。
- [x] 专属线程拥有 EGL context/pbuffer、OES 纹理、SurfaceTexture 与投影输入 Surface；onInit 只创建一次 VD，编码器输出 Surface 仍由策略拥有。
- [x] frame callback 同线程 updateTexImage/变换/时间戳→encoder EGLSurface/swap；截图使用同纹理的独立 FBO/readPixels/行翻转，按请求尺寸生成 Bitmap，不中断录制。
- [x] 旋转保留 VD 与投影输入，只 resize/setDefaultBufferSize；有序替换 codec 与其 EGL 输出，旧 callback 不触碰新 codec。
- [x] 策略明确 one-shot；原 onStop 已终止投影，现把该约束显式化。初始化失败、截图排队、释放重入、迟到帧均单 owner 清理。
- [x] 终态立即关闭工作门禁；EGL 线程先清理 GL 输入/输出，再释放借用的 encoder Surface/codec/VD。超时后继续持有资源，不由调用线程提前释放；截图结果交发送 handler，销毁时回收未交付 Bitmap。

## 6. Service 最终清理

文件：`MediaProjectionService.kt`、必要的 Activity 旋转接线与 `MediaProjectionServiceTest.kt`。

- [x] stop/release/unbind/destroy 共用一轮最终 session 清理，清理路径只在 lifecycleLock 内完成门禁、owner 快照、发布和排队。
- [x] 销毁立即拒绝迟到 start/截图/旋转；已排队清理保留，最后一个 drain runnable 完成资源清理后 `quitSafely()`，不 removeCallbacks 丢失 owner。
- [x] 各清理步骤独立执行：processor stop/release、输出流关闭、projection stop 兜底；finally 清 owner，失败不跳过后续释放。
- [x] Activity 的 Timer 通过 Service 门禁发起旋转；不在主线程等待耗时 native cleanup。
- [x] 真实 Service 生命周期红绿回归、重复清理与迟到任务对照。

## 7. 收尾复核：前台服务启动与通知

- [x] Activity 创建时只发起 BIND_AUTO_CREATE；确认授权成功、Activity 仍有效且服务已连接后，再启动服务并立即显示前台通知，不把提升期限跨越用户授权等待。
- [x] Service 不以 START_STICKY 重建一个无法恢复投影授权的空会话；启动未取得 owner 时不继续采集，通知失败撤销已取得的 started owner，销毁仍处理未完成绑定。
- [x] 补充未授权不启动 FGS、授权后的启动/提升/投影顺序、未绑定/销毁拒绝和失败回滚回归，重跑 Demo 完整验证。
- [x] 通知 PendingIntent 改为 ScreenShareMasterActivity，并通过 NEW_TASK | CLEAR_TOP | SINGLE_TOP 复用现有录屏页；用户已明确选择返回录屏页，补目标组件与 flags 回归。

## 最终验收

- [x] 独立复核六项修复与所有公开 API 迁移、Demo 接线、失败/取消/迟到回调。
- [x] 运行受影响模块完整单测、ktlint/detekt、标准 lint 和 `:demo:assembleDevDebug`；必要时显式全仓 `testDebugUnitTest :demo:testDevDebugUnitTest ktlintCheck detekt`。
- [x] 更新 CHANGELOG 和独立实施记录，记录实际任务/测试数；检查 `git diff --check` 与 Git 边界。
