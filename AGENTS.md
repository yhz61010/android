# 仓库指南

## 项目结构与模块组织
这是一个使用 Gradle Kotlin DSL 构建、通过 JitPack 发布的多模块 Android 库项目。共享构建逻辑和版本约束位于 [build.gradle.kts](/home/yhz61010/StudioProjects/android/build.gradle.kts)、[settings.gradle.kts](/home/yhz61010/StudioProjects/android/settings.gradle.kts) 和 [gradle/libs.versions.toml](/home/yhz61010/StudioProjects/android/gradle/libs.versions.toml)。

已纳入 Gradle 构建的模块以 `settings.gradle.kts` 为准。当前模块结构更适合按类别理解：

- Demo 应用：`demo`、`demo-dex`
- 核心与共享库：`androidbase`、`android-restricted`、`log`、`pref`、`http`、`lib-common-android`、`lib-common-kotlin`、`lib-bytes`、`lib-json`、`lib-compress`、`lib-network`、`lib-reflection`、`lib-image`、`lib-exif`、`lib-mvvm`、`lib-compose`
- 媒体与编解码模块：`audio`、`ffmpeg-javacpp`、`adpcm-ima-qt-codec`、`h264-hevc-decoder`、`adpcm-ima-qt-codec-h264-hevc-decoder`、`yuv`、`jpeg`
- 设备、图形与功能模块：`camerax`、`camera2live`、`screencapture`、`draw-on-screen`、`floatview`、`opengl`、`nfc`、`basenetty`、`aidl-client`、`dex`、`circle-progressbar`

仓库根目录下还有一些原生源码、构建辅助目录或历史实验目录，但除非它们被写入 `settings.gradle.kts`，否则都不是当前激活的 Gradle 模块。例如当前未启用的 `ffmpeg-sdk`、`webrtc`、`x264`、`libjpeg-turbo` 和 `libyuv`。

Android 资源通常位于 `src/main/res`。Native 构建入口可能位于 `src/main/cpp`，也可能是模块根目录下的 `CMakeLists.txt`，取决于具体模块。

## 模块依赖与职责边界
本仓库是横向 Android 工具库集合，不是单一业务应用，也不采用统一的 Clean Architecture 分层。理解和新增代码时，优先遵循现有的依赖方向：纯 Kotlin 基础库（如 `lib-common-kotlin`、`lib-bytes`、`lib-compress`）→ Android 基础与横切能力（如 `lib-common-android`、`log`、`lib-image`、`lib-network`）→ `androidbase` 综合工具层 → Camera、音频、录屏、OpenGL 等功能模块 → `demo`/`demo-dex` 集成演示应用。下层模块不应反向依赖上层功能模块或 Demo，也不要为了复用少量代码制造循环依赖。

`androidbase` 是常用 Android 能力的综合入口，但不是所有工具代码的默认归宿。与 Android 无关的通用逻辑优先放入对应的纯 Kotlin `lib-*` 模块；Android 通用扩展优先放入 `lib-common-android`；图片、网络、JSON 等逻辑放入各自的专用模块；需要敏感权限的能力放入 `android-restricted`。新增跨模块依赖前先检查是否能保持这一职责边界。

Gradle 依赖默认优先使用 `implementation`。只有当依赖类型确实出现在模块公开 API 中，或明确希望调用方获得该传递依赖时才使用 `api`。本仓库现有多个模块通过 `api` 暴露基础库，因此修改公开类、函数、默认参数、返回类型或依赖可见性时，要评估下游模块的源码兼容和 API/ABI 兼容，不能只验证当前模块可以编译。

`demo` 和 `demo-dex` 不只是示例代码，也是公开 API 和跨模块集成验证入口。修改库的公开用法时，应同步更新对应 Demo 调用点；至少编译受影响库及其直接下游，涉及多模块公开契约时再运行 `:demo:assembleDevDebug`。

## 构建、测试与开发命令
使用 JDK 17 和仓库内置的 Gradle Wrapper。当前 Wrapper 为 Gradle `9.4.0`，版本目录配置为 AGP `9.0.1`、Kotlin `2.3.10`、库发布版本 `5.15.8`、`compileSdk`/`targetSdk` 36、`minSdk` 21、NDK `29.0.14206865`、CMake `3.22.1`。

- `./gradlew assemble`：构建所有已配置模块。
- `./gradlew :demo:assembleDevDebug`：构建 demo 应用的主要调试变体。
- `./gradlew testDebugUnitTest`：运行 Android 模块的 debug 单元测试。
- `./gradlew :androidbase:testDebugUnitTest`：运行单个库模块的单元测试；可按需替换为其他模块名。
- `./gradlew :demo:testDevDebugUnitTest`：运行 demo 应用 `devDebug` 变体的单元测试。
- `./gradlew :demo:connectedDevDebugAndroidTest`：在已连接设备或模拟器上运行仪器测试。
- `./gradlew ktlintCheck detekt`：运行格式检查与静态分析。
- `./gradlew publishToMavenLocal`：本地验证 Maven/JitPack 发布链路。
- `./gradlew :android-restricted:mergeReleaseConsumerProguardFiles --rerun-tasks`：针对 `android-restricted` consumer ProGuard 问题的最窄验证任务。
- `./gradlew clean`：清理 Gradle 构建产物。

构建前请将 `gradle.properties.template` 复制为 `gradle.properties`。按照 `README.md` 和 `00-documents/git-lfs-guide.md` 中的说明安装 Git LFS。

## 代码风格、日志与命名规范
遵循 Kotlin 优先约定，使用 4 空格缩进，Gradle 配置使用 Kotlin DSL。包名保持在 `com.leovp.*` 之下，其中需要受限或敏感权限的工具应优先放在 `android-restricted`，避免重新把敏感权限依赖混入 `androidbase`。命名方式与现有代码保持一致：类和对象使用 `UpperCamelCase`，函数和属性使用 `lowerCamelCase`，常量使用 `UPPER_SNAKE_CASE`。测试类通常以 `Test` 或 `UnitTest` 结尾。提交前运行 `ktlintCheck` 和 `detekt`；根级配置使用 `10-configs/detekt.yml`。Detekt 与 ktlint 都由根项目统一应用到所有模块，且 detekt 使用零容忍策略；删除或重构代码后，要同步清理失效的 import、私有成员和死代码，避免未用符号导致检查失败。

仓库 `minSdk` 为 21。新增或替换 Android / Java API 时先确认 API level；需要高于 21 的 API 必须通过 `Build.VERSION.SDK_INT` 分支、AndroidX 兼容 API 或等价低版本写法处理。例如不要直接使用 `ThreadLocal.withInitial` 或 `ConcurrentHashMap.newKeySet()` 这类高版本入口。

日志处理按模块依赖决定，不要为了记日志随意新增依赖：

- 已依赖 `log` 的模块，例如 `androidbase`、`lib-compose`、`lib-json`、`lib-network`、`lib-mvvm`、`camerax`、`screencapture`、`nfc`、`opengl`、`http`、`audio`、`basenetty`、`aidl-client`、`android-restricted`、`demo`，优先使用 `com.leovp.log.base.d/e` 或 `LogContext`。
- 未依赖 `log` 的基础工具模块，例如 `lib-common-kotlin`、`lib-common-android`、`lib-compress`、`draw-on-screen`、`lib-bytes`、`lib-reflection`，不要为了日志新增 `log` 依赖；优先使用 `android.util.Log`、上抛异常，或通过调用方注入的错误处理回调暴露错误。
- `lib-json` 是当前例外：`Any?.toJsonString()` 与 `String?.toObject()` 系列只做 JSON 转换，失败时在函数内部通过 `LogContext` 记录异常并返回默认值（`""` / `null`），不向调用方暴露 `onError` 参数；`CancellationException` 应继续向上抛出。

## 测试指南
所有 Gradle `Test` 任务都通过 `useJUnitPlatform()` 启用 JUnit 5。Android 单元测试启用了 `isReturnDefaultValues = true` 和 `isIncludeAndroidResources = true`。`demo` 应用的仪器测试使用 `AndroidJUnitRunner`，并通过 `de.mannodermaus.junit5.AndroidJUnit5Builder` 接入 JUnit 5。JVM 测试放在 `src/test/kotlin` 或 `src/test/java`；设备测试放在 `src/androidTest`。优先将测试放在受影响模块附近，例如 `androidbase/src/test/.../RSAUtilTest.kt`。

测试范围按风险扩展：内部纯函数修改优先运行模块单元测试；公开 API 修改还要编译直接下游和对应 Demo；Camera、Camera2、音频、MediaCodec、MediaProjection、OpenGL、NFC、蓝牙以及 JNI/Native 相关修改必须补真机验证。真机验证至少覆盖一台 API 21～26 设备和一台较新 Android 设备，并检查旋转、前后台切换、重复进入退出、资源释放和长时间运行。涉及图像、音视频或 Codec 的改动还应记录分辨率、帧率、编码器名称、内存和丢帧情况，不能只以“不崩溃”作为通过标准。

## Commit 与 Pull Request 指南
最近的提交历史同时包含普通祈使句标题和带 Conventional Commit 风格前缀的标题，例如 `docs(readme): ...`。优先使用简短的祈使句提交标题；当作用域能提升可读性时，可加作用域前缀，例如 `fix(lib-network): handle empty response`。Pull Request 应尽量聚焦，说明受影响模块，列出验证命令；涉及 UI 或 demo 应用变更时附上截图。对于签名、native 库或 Gradle 配置变更，需要明确标注。

## 发布与二进制文件注意事项
所有 Android library 子项目默认通过根构建逻辑配置 `consumerProguardFiles("consumer-rules.pro")`。新增库模块时必须在模块根目录提供 `consumer-rules.pro`，即使当前没有保留规则也应保留空文件，否则 release consumer ProGuard 合并和 JitPack 发布会失败。

仓库使用 Git LFS 管理大型二进制文件。新增或替换 `.so`、`.a`、媒体样本、源码压缩包等大文件前，先检查 `.gitattributes` 和 `00-documents/git-lfs-guide.md`；不要提交 LFS 指针损坏或未拉取完整内容的构建结果。

修改 JNI 或 Native 模块时，同时核对 Kotlin/Java native 方法签名、C/C++ 导出符号、CMake 输入和支持 ABI。当前 Demo 主要打包 `armeabi-v7a` 与 `arm64-v8a`；相关变更至少验证这两个 ABI 的构建和真实加载，避免只验证 JVM 编译或单一架构。

项目 AI 生成文档统一放在 `00-documents/`。Superpowers 生成的 specs、plans 和 implementation notes 放在 `00-documents/superpowers/`，并按本仓库约定只维护中文内容。

## 面向代理的说明
在本仓库中与贡献者沟通时使用中文。代码注释与 commit 内容使用英文。

做分支或 PR 审查时，先确认当前仓库真实主线，不要假设一定是 `main`。截至当前仓库状态，远端默认主线是 `origin/master`；审查前仍应以 `git symbolic-ref refs/remotes/origin/HEAD`、`git branch -r --list 'origin/main' 'origin/master'` 和当前 refs 为准。

需要真实验证 Gradle 测试时，不要只依赖 `UP-TO-DATE` 结果；对风险较高或刚改过的路径，可加 `--rerun-tasks` 强制重跑目标任务。

## Claude 与 CodeX 互操作
保留所有现有 Claude Code 文件，包括 [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) 以及 `.claude/` 下的全部内容，除非用户明确要求修改。

对于本仓库中的 CodeX：

- 除本文件外，还应将 [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) 视为补充项目说明。
- 本机存在主 Codex 记忆时，优先使用当前主记忆；仓库内 `00-documents/codex-memory/` 是可共享快照，用于 fresh clone 或跨环境恢复。主记忆更新且需要共享给未来 clone 时，再刷新该目录。
- fresh clone 后读取共享 Codex 记忆时，先读 [00-documents/codex-memory/README.md](/home/yhz61010/StudioProjects/android/00-documents/codex-memory/README.md)，再加载 `00-documents/codex-memory/memory_summary.md`、`00-documents/codex-memory/MEMORY.md`、`00-documents/codex-memory/extensions/ad_hoc/notes/`，以及相关的 `00-documents/codex-memory/rollout_summaries/` 文件。
- 优先按需读取现有 Claude 材料，而不是盲目重复其内容。
- 当任务涉及个人或仓库工作风格时，读取 `.claude/memory/MEMORY.md`；如果 `.claude/rules/personal-style.md` 存在，再作为补充读取。
- 当任务涉及 Android UI 或 UX 设计时，读取 `.claude/skills/mobile-android-design/SKILL.md`。
- 当用户询问如何查找、创建或安装 skills 时，读取 `.claude/skills/find-skills/SKILL.md`。
- 保留 `.claude/commands`、`.claude/memory`、`.claude/rules`、`.claude/skills` 以及 Claude 生成的文件，除非用户明确要求修改它们。

以下规则足够重要，CodeX 应直接执行，而不依赖额外文件：

- 与贡献者沟通时使用中文。
- `AGENTS.md` 只使用中文；不再维护 `AGENTS.zh-CN.md`。
- 代码注释与 commit 内容使用英文。
- 创建或更新文档文件时，同时维护英文版和中文版；除中文配套文档外，Markdown 与代码注释仍以英文为主。
- 例外：Superpowers 生成的 specs、plans 和 implementation notes 只使用中文。
- 文档保存到 `00-documents`，不要放到 `docs`。

如果 Claude 相关说明与 CodeX 相关说明存在重叠，遵循更严格的规则；如果存在冲突，优先遵循直接的 system、developer 和 [AGENTS.md](/home/yhz61010/StudioProjects/android/AGENTS.md) 指令，再将 [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) 与 `.claude/**` 作为补充说明使用。
