# Android 项目共享记忆

本文件只索引当前仓库相关的可复用经验。所有分支、提交、版本、设备、构建结果和文件行号均为历史快照，使用前必须按当前 checkout 重核。

## Task Group: 安全 Git 检查、同步与交付边界

scope: 检查待提交工作、刷新远端引用、保留本地修改，并准确区分未提交修改、未推送提交与上游差异。

### rollout_summary_files

- `rollout_summaries/2026-09-08T03-14-07-UyWS-review_deviceext_unique_id_and_git_state.md`
- `rollout_summaries/2026-09-08T03-07-01-fqjb-android_fetch_latest_code_preserve_local_changes.md`

### User preferences

- “有需要提交的代码吗”需要同时检查工作树、暂存区和上游差异。
- “拉取最新代码”只授权安全检查和刷新远端引用；未经明确授权不执行 pull、merge、rebase、stash、reset 或 clean。
- 提交、推送、发布和合并均需要当前明确授权。

### Reusable knowledge

- 先检查 `git status --short --branch`、普通/暂存 diff 和 `git diff --check`，再执行 `git fetch origin`。
- fetch 后使用 `git rev-list --left-right --count HEAD...@{upstream}` 判断 ahead/behind；`0 0` 时无需整合。
- `.git/FETCH_HEAD`、`.git/index.lock` 或 Gradle wrapper lock 的只读错误通常是环境写权限限制，不是仓库冲突或代码失败。
- 只报告本轮实际执行的检查；历史测试和构建不能充当当前验证。

## Task Group: 设备标识 API 与 Kotlin 原始字符串

scope: 审查 `DeviceExt.kt` 的设备标识语义、资源释放和兼容边界，以及多行运行时插值导致的缩进问题。

### rollout_summary_files

- `rollout_summaries/2026-09-08T03-14-07-UyWS-review_deviceext_unique_id_and_git_state.md`
- `rollout_summaries/2026-09-01T05-51-43-lxkB-fix_deviceutil_trimindent_multiline_interpolation.md`

### Reusable knowledge

- `getUuid()` 每次生成随机 UUID v4，不提供跨调用稳定性。
- MediaDrm 获取设备标识后需要在所有路径释放；不能只因长度或格式看似正常就宣称稳定或唯一。
- `ANDROID_ID`、MediaDrm 和持久化随机 UUID 的恢复出厂、备份、厂商差异与隐私边界不同，API 命名和文档必须准确。
- `trimIndent()` 只依据原始字符串源码行的共同缩进；插值表达式运行时展开的后续行不会自动重新缩进。
- 修复字符串格式时应对齐完整输出，而不只断言单个子串存在。

## Task Group: Camera、MediaCodec、音频与录屏生命周期

scope: Camera2Live/Camerax 图像路径、MediaCodec buffer ownership、EGL/codec teardown、取消语义、初始化中断和迟到回调。

### rollout_summary_files

- `rollout_summaries/2026-07-17T08-07-43-Zipz-android_agents_cameralive_review_fix_and_push.md`
- `rollout_summaries/2026-08-17T09-00-36-zQry-android_camera_performance_and_media_teardown_review.md`
- `rollout_summaries/2026-09-16T06-12-26-eYRt-android_screencapture_init_interruption_callback_failure_fix.md`

### Reusable knowledge

- 裸 H.264 没有容器旋转矩阵；编码前必须得到正确方向的像素与尺寸。I420 不能送入 NV21 专用路径，P/SP 格式转换需要显式区分。
- `CancellationException` 必须在宽泛异常前单独捕获并重抛，清理动作不能吞掉取消信号。
- MediaCodec 输入 buffer 的 ownership 要保持到 `queueInputBuffer()` 成功；容量、写入、queue 或取消失败时不能制造连续 zero-byte submission。
- EOS 即使 `getOutputBuffer()` 返回 null，也应从 `BufferInfo.flags` 判断；每个 output index 只能归还一次。
- EGL/MediaCodec 必须由 owning thread 释放；callback 需要串行化，并以 join/barrier 等待释放完成后再关闭依赖资源。
- 调用线程在 `FutureTask.get()` 被中断不会自动停止已经排队或执行的初始化。应请求统一释放、恢复 interrupt 标记并重抛。
- 输出回调异常应先在 `finally` 归还 buffer，退出 callback 锁后再触发释放，避免锁内 teardown。
- 性能结论需要真机 FPS、丢帧、时延、内存和 codec trace；没有崩溃不等于功能或性能通过。

### Verification boundary

- Robolectric/Mock 测试不能证明真实 Codec、EGL、Camera、MediaProjection 或厂商驱动行为。
- 历史摘要中记录的单测、质量检查和构建结果仅证明当时 checkout；当前变更必须重新运行受影响范围。
- 真机仍需覆盖适用的 API 21～26 与较新设备、旋转、前后台、重复进入退出、快速停止、异常回调和长时间运行。

## Task Group: JitPack、Git LFS、Native 与发布排障

scope: JitPack 依赖解析、Native 输入实化、publication 冲突、consumer ProGuard 和 Demo 增量编译问题。

### rollout_summary_files

- `rollout_summaries/2026-05-15T05-29-03-UN96-jitpack_devicecompat_repo_order_and_local_execution.md`
- `rollout_summaries/2026-05-15T08-25-40-OBgC-jitpack_lfs_fix_and_detekt_max_line_length_100.md`
- `rollout_summaries/2026-05-18T01-41-46-NYlI-jitpack_lfs_pointer_linker_failure_jpeg_module.md`
- `rollout_summaries/2026-05-18T02-06-40-jfve-jitpack_duplicate_publication_coordinates.md`
- `rollout_summaries/2026-05-18T06-53-15-Huu3-basenetty_review_fixes_and_demo_androidbase_recompile.md`

### Reusable knowledge

- `.so`、`.a` 或源码压缩包报异常格式时，先检查首行、blob 大小、`git lfs ls-files` 和 JitPack 的 LFS 拉取顺序，确认输入不是 pointer。
- POM/publication 报重复坐标时，检查 Android `singleVariant("release")` 与显式 `MavenPublication` 是否生成同名发布。
- 根构建若统一配置 `consumerProguardFiles("consumer-rules.pro")`，每个 Android library 模块根目录都必须存在该文件；`.keep` 文件不能替代它。
- 只验证 consumer ProGuard 原始失败时，优先运行目标模块的 `mergeReleaseConsumerProguardFiles --rerun-tasks`，再决定是否扩大到本地发布链路。
- Demo unresolved reference 可能来自 provider 模块的陈旧 Kotlin 增量产物；先核对源码和 intermediates，再用目标 provider 的 `compileDebugKotlin --rerun-tasks` 验证。
- Release 说明先确定已推送的 tag/branch 范围，并与本地 staged、unstaged、untracked 修改分开。

## 项目级长期边界

- `AGENTS.md` 是当前共享规则入口；本记忆不能覆盖较新的代码、配置、用户指令或 `AGENTS.md`。
- `00-documents/` 只服务当前 Android 仓库，不存放其它项目资料。
- Claude 文件只读。Claude Code 在远端且不能运行 Gradle，其结论只能标记为静态分析；Codex 本机验证需要单独提供证据。
- 本机主 Codex 记忆优先于项目快照；同步时只筛选当前仓库内容，不能整库复制。
