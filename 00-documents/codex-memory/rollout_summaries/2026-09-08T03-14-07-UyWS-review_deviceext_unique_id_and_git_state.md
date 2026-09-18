thread_id: 01a07f02-3886-75c3-9bac-9500a74356da
updated_at: 2026-09-14T06:58:56+00:00
git_branch: review/native-modules

# 工作区状态核查、拉取远端与 DeviceExt 标识符审查

Rollout context: 工作目录为 `当前仓库`。用户先询问是否有待提交代码，随后要求拉取最新代码，最后要求只审查 `lib-common-android` 的 `DeviceExtKt.getUniqueID()` 和 `getUuid()`，不修改文件。

## Task 1: 检查未提交改动

Outcome: success

Preference signals:
- 用户用中文沟通；后续类似仓库操作应默认使用中文回复。

Key steps:
- 检查当前分支、工作区、暂存区和上下游关系。
- 确认 `review/native-modules` 有 22 个未提交文件，均为工作区修改，未暂存。
- `git diff --check` 通过；本地 HEAD 与当时记录的 upstream 一致。

Reusable knowledge:
- 这 22 个文件属于音频/MediaCodec teardown、录屏资源释放、Demo、ShellUtil 及相关文档改动；不要在后续操作中覆盖或丢弃。
- 先检查工作区再执行 pull/fetch，是该仓库的安全操作流程。

References:
- 分支：`review/native-modules`
- 上游：`origin/review/native-modules`
- 22 个未提交文件，未执行提交。

## Task 2: 拉取远端最新代码

Outcome: success

Key steps:
- 首次 `git fetch origin` 因受限环境无法写入 `.git/FETCH_HEAD` 失败。
- 使用提升权限重新执行 fetch 成功。
- `git rev-list --left-right --count HEAD...@{upstream}` 返回 `0 0`，无需合并；最新远端提交为 `d1c87e65e fix: harden media teardown and cleanup`。
- 本地 22 个未提交文件保持不变。

Failures and how to do differently:
- 受限沙箱中 fetch 可能报 `error: cannot open '.git/FETCH_HEAD': Read-only file system`；需要在用户授权后以提升权限执行 fetch，而不是直接修改或清理工作区。

References:
- `git fetch origin`
- 错误：`Read-only file system`
- 最新提交：`d1c87e65e fix: harden media teardown and cleanup`

## Task 3: 审查 DeviceExtKt.getUniqueID() 和 getUuid()

Outcome: success

Preference signals:
- 用户要求“审查”而非修改；助手明确只做静态审查，最终未修改文件。类似请求应先报告问题、证据和修复方向，不主动编辑。
- 用户关注 Android API 兼容性、稳定性、资源释放和异常契约；类似设备标识审查应覆盖生命周期、作用域、失败回退和版本差异。

Key steps:
- 定位 `DeviceExt.kt:344` 的 `getUuid()`、`DeviceExt.kt:376-388` 的 `Context.getUniqueID()` 及 Demo 调用点。
- 核对 Android 官方文档：`ANDROID_ID` 的作用域/变化条件、`Settings.Secure.getString()` 可返回 null、`MediaDrm.close()/release()` 生命周期、`UUID.randomUUID()` 的随机 UUID 语义。
- 未运行 Gradle 或真机测试，未发现专项单元测试。

Reusable knowledge:
- `getUuid()` 本身只是 `UUID.randomUUID().toString()`，是标准 UUID v4 随机生成器；每次调用都会产生新值，不应单独当作稳定设备 ID。
- Android 21-25 的 `getUniqueID()` 在 Widevine 获取失败时直接回退到 `getUuid()`，没有持久化，因此同一设备每次调用可能返回不同 ID。
- `getUniqueIdByMediaDrm()` 创建 `MediaDrm` 后没有释放；应保证成功和异常路径都清理，API 21-27 用 `release()`，API 28+ 用 `close()`。
- Android 8.0+ 分支直接把 `Settings.Secure.getString(...ANDROID_ID)` 作为非空 `String` 返回，但官方契约允许返回 null；需要明确空值/失败回退策略。
- 实现跨 Android 版本切换来源：低版本优先 DRM，高版本改用 `ANDROID_ID`；升级 Android 8 后可能发生标识变化，当前没有迁移或兼容处理。
- `ANDROID_ID` 在 Android 8+ 按签名密钥、用户和设备作用域，恢复出厂或更换签名密钥可能变化，不能承诺永久或跨应用稳定。

Failures and how to do differently:
- 不要把 `getUuid()` 作为未持久化的设备 ID 回退；若 API 目标是稳定标识，应持久化最终值并处理并发初始化及来源切换。
- 不要忽略 `MediaDrm` 资源释放；应使用 `try/finally` 或等价结构覆盖所有路径。
- 不要把可空的 `ANDROID_ID` 直接暴露为非空返回值；应定义显式失败契约或稳定回退策略。
- 这些问题均为静态审查发现，尚未实施修复或验证。

References:
- 文件：`lib-common-android/src/main/kotlin/com/leovp/android/exts/DeviceExt.kt`
- `getUuid()`：行 344
- `getUniqueIdByMediaDrm()`：行 356-368
- `getAndroidId()`：行 370-373
- `getUniqueID()`：行 376-388
- Demo 调用：`demo/src/main/kotlin/com/leovp/demo/MainActivity.kt:250`
- 主要审查结论：3 个 P2 问题——随机回退不稳定、MediaDrm 泄漏、ANDROID_ID null 违反非空契约。
