thread_id: 019f6f1d-f8bc-7b33-bbbb-7f75b8107691
updated_at: 2026-08-17T08:54:30+00:00
git_branch: master

# Android 仓库记忆唤醒、AGENTS.md 更新及 Camera2Live 整改收口

Rollout context: 工作目录为 `当前仓库`。用户要求先唤醒记忆，随后更新仓库指南、核对 androidbase 审查报告与方案，完成 Camera2Live 方向/镜像/颜色修复，记录真机验证结果，并提交推送；后续根据 Claude Code 审查补充取消异常处理。

## Task 1: 唤醒仓库记忆

Outcome: success

Preference signals:
- 用户要求“唤醒记忆”后继续工作，说明类似任务应先读取仓库 `AGENTS.md`、`CLAUDE.md`、`.claude/memory/MEMORY.md` 及相关主记忆，再基于当前状态行动，而不是直接套用旧印象。
- 仓库规则要求中文沟通，代码注释和 commit 使用英文，文档放在 `00-documents`。

Reusable knowledge:
- 当前仓库远端主线经实查为 `origin/master`，没有 `origin/main`。
- fresh clone 的共享记忆入口是 `00-documents/codex-memory/README.md`，随后按需读取 `memory_summary.md`、`MEMORY.md` 和相关 rollout summaries；本机主 Codex 记忆优先于共享快照。
- 当前仓库既有记忆覆盖 GitHub Release、JitPack/Gradle 发布和 `android-restricted` consumer ProGuard 问题。

References:
- 验证命令：`git symbolic-ref refs/remotes/origin/HEAD` -> `refs/remotes/origin/master`
- 初始状态：`git status --short --branch` -> `## master...origin/master`

## Task 2: 更新 `AGENTS.md`

Outcome: success

Preference signals:
- 用户直接说“更新 AGENTS.md”，代理应先把它作为“根据当前代码和配置同步仓库事实”的文档任务处理，而非泛化润色；本轮先核对 `settings.gradle.kts`、`libs.versions.toml`、根 `build.gradle.kts`、README 和目录结构。
- 用户偏好中文仓库指南；不要修改 Claude 文件或共享记忆，除非明确要求。

Key steps:
- 发现旧指南漏列已激活的 `android-restricted`，并未体现当前 Gradle/AGP/Kotlin/JitPack 事实。
- 更新 `AGENTS.md`：补齐 `android-restricted`；记录 Gradle 9.4.0、AGP 9.0.1、Kotlin 2.3.10、发布版本 5.15.8；增加 `publishToMavenLocal` 和 `:android-restricted:mergeReleaseConsumerProguardFiles --rerun-tasks`；说明敏感权限工具应放入 `android-restricted`；说明 library 模块必须有根目录 `consumer-rules.pro`；补充 Git LFS 大文件注意事项。
- 复核时第一次 `rg` 命令因 shell 解释反引号产生 `command not found`，但未影响文件；随后使用单引号重跑。

Reusable knowledge:
- 根构建逻辑对所有 Android library 配置 `consumerProguardFiles("consumer-rules.pro")`；新增 library 即使没有规则也应提供空的模块根 `consumer-rules.pro`，否则 release consumer ProGuard/JitPack 可能失败。
- 仅文档变更不必跑 Gradle；本轮以 `git diff`、当前配置交叉核对和 `git diff --check` 验证。

References:
- 文件：`AGENTS.md`
- 事实源：`settings.gradle.kts`、`gradle/libs.versions.toml`、`build.gradle.kts`、`gradle/wrapper/gradle-wrapper.properties`
- 验证：`git diff --check -- AGENTS.md` 无输出

## Task 3: 核对 androidbase 代码审查报告与修改方案

Outcome: partial

Key steps:
- 找到 `00-documents/2026-07-17-androidbase-code-review-zh.md` 和 `00-documents/2026-07-17-androidbase-fix-plan-zh.md`，先只读审查，再回到源码核对。
- 代码证实报告中的 P0 加密问题：AES 零 IV、4 字节盐、PBKDF2 1000 次迭代、基于 `elapsedRealtimeNanos()` 的密钥派生、裸 `RSA` 变换以及将私钥 RSA 加密误作签名。
- 代码证实多个 P1/P2 问题：H264/H265 起始码判断的 `&&` 优先级、`findStartCode`/`getVps` 越界风险、YUV 输入缺少统一边界保护、`NetworkMonitor.interrupt()`、Surface 和文件流生命周期问题、`ConnectionLiveData` 在线状态硬编码为 `TYPE_OFFLINE`、Base64 UTF-8/US-ASCII 不对称、Bluetooth 反射未统一捕获异常等。
- 没有在该阶段直接修改 androidbase；报告明确要求加密修复必须版本化格式并经 security-reviewer 复核，当前 rollout 未完成这些修复。

Failures and how to do differently:
- 报告中的短文件路径不能直接假设目录；首次按猜测的 `media/codec` 路径读取失败，之后用 `rg --files androidbase/src/main` 精确定位到 `utils/media`、`utils/network` 等真实路径。
- AES/RSA 公共 API 修改必须考虑旧密文和旧 `sign/verify` 调用方；方案提出新格式前缀和 legacy 解密路径，但未实施。

References:
- `00-documents/2026-07-17-androidbase-code-review-zh.md`
- `00-documents/2026-07-17-androidbase-fix-plan-zh.md`
- 关键源码：`androidbase/.../utils/cipher/AESUtil.kt`、`PBKDF2Util.kt`、`RSAUtil.kt`

## Task 4: Camera2Live 方向、镜像和颜色整改

Outcome: success

Key steps:
- 根据真机反馈发现：后置横屏裸 H.264 原本 SPS 为 `1080x1920`；前置横屏仍竖屏且 YUV420SP 偏色。
- 根因：录像固定旋转 90°/270°、编码器尺寸取自显示方向推导的 `previewSize`；前置从 `getYuvDataFromImage(..., COLOR_FORMAT_I420)` 得到 I420，却调用 `mirrorNv21`、`rotateYUV420Degree270` 等按 NV21 色度解释的函数。
- 实现录像开始时锁定 `relativeOrientation`，统一按 0/90/180/270 旋转 I420；旋转后前置调用 native `mirrorI420`；YUV420P 输出 I420，YUV420SP 最后显式转换为 NV12；编码器 SPS 尺寸与变换后尺寸一致。
- 保留无参策略的历史默认值：后置 90°、前置 270°；保留 `IDataProcessStrategy.doProcess(...)` 签名，新增内部旋转参数路径。
- 添加 `RecordingOrientation.kt` 和测试，覆盖镜头默认角、四种旋转尺寸、非法角度和非正尺寸。
- 更新总整改文档、3b spec 和 `CHANGELOG.md`，记录问题闭环、真机通过状态和跨设备发布回归要求。
- 用户确认 Camera2Live 照片、预览、录像真机测试均通过；文档记录前后摄像头竖屏/横屏照片方向与镜像、裸 H.264 方向/颜色、镜头切换回归均正常。

Reusable knowledge:
- 裸 H.264 没有 MP4/MOV 容器旋转矩阵；要得到横屏播放效果，必须直接编码横屏像素和正确 SPS，黑边属于播放器/容器范围，不应写入裸码流。
- I420 数据绝不能送入 NV21 专用函数；SP 输出必须明确转换为 NV12。
- 方向应在录像开始时锁定，录像中旋转只影响下一段录像，避免同一裸码流中途改变 SPS 宽高。
- 文档应区分“当前设备真机已通过”和“不同 sensorOrientation/YUV 格式的跨设备发布回归仍待覆盖”，不要夸大验证范围。

References:
- 新增：`camera2live/src/main/kotlin/com/leovp/camera2live/utils/RecordingOrientation.kt`
- 修改：`Camera2ComponentHelper.kt`、`DataProcessFactory.kt`、`EncoderStrategyYuv420P.kt`、`EncoderStrategyYuv420Sp.kt`、`OrientationLiveData.kt`、`BaseCamera2Fragment.kt`
- 测试：`RecordingOrientationTest.kt`、`OrientationLiveDataTest.kt`
- 文档：`00-documents/2026-08-13-camera-performance-remediation-zh.md`、`00-documents/superpowers/specs/2026-08-13-cam2-front-yuv-native-design.md`、`CHANGELOG.md`
- 首次功能提交：`117e26213 fix(camera2live): correct capture and recording orientation`

## Task 5: 修复 `CancellationException` 处理并提交推送

Outcome: success

Preference signals:
- 用户转发 Claude Code 的具体审查意见后，期望先核对当前代码和对应模块实现，再做最小修复并提交推送，而不是泛泛接受结论。
- 用户说“commit and push”时，默认只提交当前明确修复，先查看差异和 `git diff --check`，完成后报告 commit、分支、远端和工作树状态。

Key steps:
- 核对 `Camera2ComponentHelper.saveTransformedJpeg()` 与 camerax 对应实现，确认通用 `catch (Exception)` 会捕获 `CancellationException` 并误记 error 日志；虽然原代码重新抛出，结构化并发未被吞掉。
- 增加专用 `catch (CancellationException)`：删除未完成输出文件后立即重抛，不记录错误；普通异常仍删除文件、记录日志并重抛。
- `git diff --check` 通过；提交并推送成功。

References:
- 文件：`camera2live/src/main/kotlin/com/leovp/camera2live/Camera2ComponentHelper.kt`
- 修改形态：`catch (exc: CancellationException) { output.delete(); throw exc }`
- 提交：`8afc8a845 fix(camera2live): preserve JPEG save cancellation`
- 分支/远端：`fix/eight-module-remediation` / `origin/fix/eight-module-remediation`
- 最终状态：`git status --short --branch` -> `## fix/eight-module-remediation...origin/fix/eight-module-remediation`，工作树干净、已同步。

Failures and how to do differently:
- 在协程代码中不要把 `CancellationException` 留给宽泛 `catch (Exception)`；即使重新抛出，也会产生错误日志和不必要的失败清理语义。应在通用 catch 前单独处理。
