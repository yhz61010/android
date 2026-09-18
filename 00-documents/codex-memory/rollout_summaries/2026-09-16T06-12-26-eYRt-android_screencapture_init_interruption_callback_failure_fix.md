thread_id: 01a0a8d8-597f-73e3-9c41-273269e6c2f3
updated_at: 2026-09-16T06:59:13+00:00
git_branch: review/native-modules

# 修复录屏初始化中断与 MediaCodec 回调失败清理问题，并完成验证

Rollout context: `当前仓库`，分支 `review/native-modules`。先 fetch 并审查上游 3 个提交，随后经用户明确授权执行 `git merge --ff-only origin/review/native-modules`。本轮保持代码审查与修复范围明确，未执行真机验证。

## Task 1: 拉取并独立审查上游代码与文档

Outcome: success

Preference signals:
- 用户要求“拉取最新的代码并对拉取的代码及文档进行审查” -> 类似任务应先检查工作树、分支、上游差异，再安全 fetch；未经授权不 pull/merge/rebase。
- 用户随后明确“允许仅快进合并并修复” -> 涉及整合远端提交时应先请求明确授权，不应默认合并。

Key steps:
- 初始分支 `review/native-modules`，工作树干净，fetch 前 `HEAD...@{upstream}` 为 `0 0`。
- `git fetch origin` 首次因受限文件系统报 `.git/FETCH_HEAD: Read-only file system`，获得授权后重试成功。
- 上游新增 3 个提交，分支落后 3 个提交；审查范围为 `6d2088879..6f6313f5d`，涉及录屏生命周期、Falcon、OPUS、文档和 CHANGELOG。
- 识别两项代码遗漏：`onInit()` 等待被中断后 EGL 任务仍可继续创建资源；无录制协程时 MediaCodec 输出回调异常不会触发释放/错误通知。
- 识别一项文档错误：独立审查文档对 `CancellationException` 的父/兄弟协程传播及 `isCancelled` 语义表述不准确。

Reusable knowledge:
- `Screenshot2H26xStrategy.runOnEglThread()` 使用 `FutureTask.get()`；调用线程中断不会自动停止已排队或正在执行的 EGL 任务，因此必须由 `onInit()` 捕获 `InterruptedException`，请求统一释放、恢复中断标记并重抛。
- `onOutputBufferAvailable()` 的异常必须在归还 output buffer、退出 `codecCallbackLock` 后进入 `requestRelease()`，否则仅 `onInit(); onStart()` 的公开 API 路径没有录制协程负责清理。
- `git fetch` 只更新远端引用；是否整合由 `git rev-list --left-right --count HEAD...@{upstream}` 决定。此次用户授权后使用 fast-forward merge。

References:
- 上游提交：`6f6313f5d fix(screencapture,audio,demo): close review findings from rounds eight and nine`
- 临时审查快照：`/tmp/android-review-6f6313f5d`
- 独立审查文档：`00-documents/2026-09-16-screenshot-recorder-independent-review_cc.md`

## Task 2: 修复代码、补测试并更新文档

Outcome: success

Key steps:
- 修改 `Screenshot2H26xStrategy.kt`：初始化等待中断时调用 `requestRelease()`，恢复当前线程 interrupt 状态并重抛；输出回调失败和 `onError()` 统一请求释放。
- 新增 `Screenshot2H26xStrategyTest.kt`，覆盖：初始化进行中中断、初始化排队中断、无录制协程时输出回调失败清理与单次错误通知。
- 为 `screencapture` 增加测试依赖配置。
- 更新两份审查文档和 `CHANGELOG.md`，记录修复、验证边界和真机待验证项，并纠正文档中的协程取消语义。
- 测试先在旧实现上失败，修复后通过。

Reusable knowledge:
- 回调失败路径应先在 `finally` 中调用 `releaseOutputBuffer`，离开 codec 锁后再触发释放，避免释放流程与当前回调死锁。
- 初始化任务不能使用 `cancel(true)` 强行中断 EGL/native 初始化；应由拥有 EGL context 的线程完成资源释放。
- 文档应明确：`CancellationException` 通常只结束当前子协程，不自动取消父协程或兄弟协程；主线程也可以运行协程。

References:
- 修复文件：`screencapture/src/main/kotlin/com/leovp/screencapture/screenrecord/base/strategies/Screenshot2H26xStrategy.kt`
- 测试文件：`screencapture/src/test/kotlin/com/leovp/screencapture/screenrecord/base/strategies/Screenshot2H26xStrategyTest.kt`
- 验证命令：`./gradlew --offline --continue :screencapture:testDebugUnitTest :screencapture:ktlintCheck :screencapture:detekt :demo:compileDevDebugKotlin --rerun-tasks`
- 验证结果：`BUILD SUCCESSFUL`；新增 3 个测试 `3 tests completed, 0 failures, 0 errors, 0 skipped`；`git diff --check` 通过；未进行真机验证。

## Task 3: 提交并推送

Outcome: partial

Key steps:
- 已暂存且核对仅包含本轮 6 个文件，暂存区 `git diff --cached --check` 通过，分支与远端在提交前为 `0 0`。
- 计划提交信息：`fix(screencapture): release resources after init interruption and callback failure`。

Failures and how to do differently:
- `git commit` 被执行环境自动审核拒绝，原因是 workspace credits 用尽；因此本轮没有生成 commit，也没有 push。后续需在 credits 恢复后重新执行提交，并验证 commit、远端同步和 clean worktree。

References:
- 暂存文件共 6 个：两份 `00-documents` 文档、`CHANGELOG.md`、`screencapture/build.gradle.kts`、生产 Kotlin 文件、测试文件。
- 当前最终状态：工作树有上述暂存修改，尚未提交或推送。
