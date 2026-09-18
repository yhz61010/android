thread_id: 01a05b85-fe62-7933-9d3f-4c127d5eebb9
updated_at: 2026-09-01T06:01:24+00:00
git_branch: review/native-modules

# 修复 DeviceUtil 多行字符串缩进异常

Rollout context: 用户发现 `lib-common-android/src/main/kotlin/com/leovp/android/utils/DeviceUtil.kt` 中 `trimIndent()` 未去除 `Device basic information:` 前的空格，并要求按分析结果优化。

## Task 1: 修复 getDeviceInfo() 的多行字符串缩进

Outcome: success

Preference signals:

- 用户明确指出实际返回结果中连 `Device basic information:` 前仍有空格，并要求“按你说的优化下” -> 类似问题应继续定位运行时插值内容，而不是停留在 Kotlin 语法解释，并直接做最小范围修复。
- 用户偏好中文沟通；修改应避免触碰其他未提交改动。

Key steps:

- 检查 `DeviceUtil.kt`、调用点、Git 历史及编译产物，确认 `trimIndent()` 确实在插值完成后执行。
- 定位根因：`externalStorageBytesInReadable` 会生成多行内容；当存在第二个存储设备时，插入后的 `[1]` 从第 0 列开始，使整段字符串的最小公共缩进变为 0，导致普通行的缩进也无法被 `trimIndent()` 删除。
- 将模板改为每行显式添加 `|`，并使用 `trimMargin()`；同时把网络类型/代际值先保存到局部变量，保持模板结构清晰。
- 保留 IMEI 和 `Device Features` 的层级缩进。

Failures and how to do differently:

- 首次直接运行 Gradle 因受限文件系统无法写入 `~/.gradle` 的 Gradle wrapper lock 文件失败；授权后重试成功启动。
- `:lib-common-android:ktlintCheck` 未通过，但失败来自既有的 `FileDocumentUtilTest.kt` 测试格式问题；单独 main 源集检查也被既有的 `FileDocumentUtil.kt`、`NetworkUtil.kt`、`ShellUtil.kt` 问题阻塞，均未报告 `DeviceUtil.kt` 问题。不要将这些无关 ktlint 失败归因于本次修改。

Reusable knowledge:

- Kotlin `trimIndent()` 会在字符串插值完成后计算所有非空行的最小公共缩进；插值变量中的零缩进行会让公共缩进变成 0。
- 对包含不可控多行插值的模板，使用显式 `|` 边界和 `trimMargin()`，可避免插值内容影响模板自身的缩进处理。

References:

- 文件：`lib-common-android/src/main/kotlin/com/leovp/android/utils/DeviceUtil.kt:222-262`
- 关键插值：`External Storage : $externalStorageBytesInReadable`
- 修复形态：每行以 `|` 开始，末尾使用 `""".trimMargin()`
- 验证：`./gradlew :lib-common-android:ktlintCheck :lib-common-android:compileDebugKotlin --rerun-tasks` 中 `compileDebugKotlin` 通过；`git diff --check -- lib-common-android/src/main/kotlin/com/leovp/android/utils/DeviceUtil.kt` 通过。
