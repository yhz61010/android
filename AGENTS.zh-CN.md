# 仓库指南

## 项目结构与模块组织
这是一个使用 Gradle Kotlin DSL 构建的多模块 Android 项目。共享构建逻辑和版本约束位于 [build.gradle.kts](/home/yhz61010/StudioProjects/android/build.gradle.kts)、[settings.gradle.kts](/home/yhz61010/StudioProjects/android/settings.gradle.kts) 和 [gradle/libs.versions.toml](/home/yhz61010/StudioProjects/android/gradle/libs.versions.toml)。

已纳入 Gradle 构建的模块以 `settings.gradle.kts` 为准。当前模块结构更适合按类别理解：

- Demo 应用：`demo`、`demo-dex`
- 核心与共享库：`androidbase`、`log`、`pref`、`http`、`lib-common-android`、`lib-common-kotlin`、`lib-bytes`、`lib-json`、`lib-compress`、`lib-network`、`lib-reflection`、`lib-image`、`lib-exif`、`lib-mvvm`、`lib-compose`
- 媒体与编解码模块：`audio`、`ffmpeg-javacpp`、`adpcm-ima-qt-codec`、`h264-hevc-decoder`、`adpcm-ima-qt-codec-h264-hevc-decoder`、`yuv`、`jpeg`
- 设备、图形与功能模块：`camerax`、`camera2live`、`screencapture`、`draw-on-screen`、`floatview`、`opengl`、`nfc`、`basenetty`、`aidl-client`、`dex`、`circle-progressbar`

仓库根目录下还有一些原生源码、构建辅助目录或历史实验目录，但除非它们被写入 `settings.gradle.kts`，否则都不是当前激活的 Gradle 模块。例如 `ffmpeg-sdk`、`webrtc`、`x264`、`libjpeg-turbo` 和 `libyuv`。

Android 资源通常位于 `src/main/res`。Native 构建入口可能位于 `src/main/cpp`，也可能是模块根目录下的 `CMakeLists.txt`，取决于具体模块。

## 构建、测试与开发命令
使用 JDK 17 和仓库内置的 Gradle Wrapper。当前版本目录配置的目标环境为 `compileSdk`/`targetSdk` 36、`minSdk` 21、NDK `29.0.14206865`、CMake `3.22.1`。

- `./gradlew assemble`：构建所有已配置模块。
- `./gradlew :demo:assembleDevDebug`：构建 demo 应用的主要调试变体。
- `./gradlew testDebugUnitTest`：运行 Android 模块的 debug 单元测试。
- `./gradlew :androidbase:testDebugUnitTest`：运行单个库模块的单元测试；可按需替换为其他模块名。
- `./gradlew :demo:testDevDebugUnitTest`：运行 demo 应用 `devDebug` 变体的单元测试。
- `./gradlew :demo:connectedDevDebugAndroidTest`：在已连接设备或模拟器上运行仪器测试。
- `./gradlew ktlintCheck detekt`：运行格式检查与静态分析。
- `./gradlew clean`：清理 Gradle 构建产物。

构建前请将 `gradle.properties.template` 复制为 `gradle.properties`。按照 `README.md` 和 `00-documents/git-lfs-guide.md` 中的说明安装 Git LFS。

## 代码风格与命名规范
遵循 Kotlin 优先约定，使用 4 空格缩进，Gradle 配置使用 Kotlin DSL。包名保持在 `com.leovp.*` 之下。命名方式与现有代码保持一致：类和对象使用 `UpperCamelCase`，函数和属性使用 `lowerCamelCase`，常量使用 `UPPER_SNAKE_CASE`。测试类通常以 `Test` 或 `UnitTest` 结尾。提交前运行 `ktlintCheck` 和 `detekt`；根级配置使用 `10-configs/detekt.yml`。Detekt 与 ktlint 都由根项目统一应用到所有模块。

## 测试指南
所有 Gradle `Test` 任务都通过 `useJUnitPlatform()` 启用 JUnit 5。Android 单元测试启用了 `isReturnDefaultValues = true` 和 `isIncludeAndroidResources = true`。`demo` 应用的仪器测试使用 `AndroidJUnitRunner`，并通过 `de.mannodermaus.junit5.AndroidJUnit5Builder` 接入 JUnit 5。JVM 测试放在 `src/test/kotlin` 或 `src/test/java`；设备测试放在 `src/androidTest`。优先将测试放在受影响模块附近，例如 `androidbase/src/test/.../RSAUtilTest.kt`。

## Commit 与 Pull Request 指南
最近的提交历史同时包含普通祈使句标题和带 Conventional Commit 风格前缀的标题，例如 `docs(readme): ...`。优先使用简短的祈使句提交标题；当作用域能提升可读性时，可加作用域前缀，例如 `fix(lib-network): handle empty response`。Pull Request 应尽量聚焦，说明受影响模块，列出验证命令；涉及 UI 或 demo 应用变更时附上截图。对于签名、native 库或 Gradle 配置变更，需要明确标注。

## 面向代理的说明
在本仓库中与贡献者沟通时使用中文。文档、Markdown 内容和代码注释使用英文。

## Claude 与 CodeX 互操作
保留所有现有 Claude Code 文件，包括 [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) 以及 `.claude/` 下的全部内容，除非用户明确要求修改。

对于本仓库中的 CodeX：

- 除本文件外，还应将 [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) 视为补充项目说明。
- 优先按需读取现有 Claude 材料，而不是盲目重复其内容。
- 当任务涉及个人或仓库工作风格时，读取 `.claude/rules/personal-style.md` 和 `.claude/memory/MEMORY.md`。
- 当任务涉及 Android UI 或 UX 设计时，读取 `.claude/skills/mobile-android-design/SKILL.md`。
- 当用户询问如何查找、创建或安装 skills 时，读取 `.claude/skills/find-skills/SKILL.md`。
- 保留 `.claude/commands`、`.claude/memory`、`.claude/rules`、`.claude/skills` 以及 Claude 生成的文件，除非用户明确要求修改它们。

以下规则足够重要，CodeX 应直接执行，而不依赖额外文件：

- 与贡献者沟通时使用中文。
- 代码注释、文档、Markdown 内容和 git commit message 使用英文。
- 创建文档文件时，同时生成英文版和中文版；除中文配套文档外，Markdown 与代码注释仍以英文为主。
- 文档保存到 `00-documents`，不要放到 `docs`。
- commit message 中不要添加 `Co-Authored-By` trailer。

如果 Claude 相关说明与 CodeX 相关说明存在重叠，遵循更严格的规则；如果存在冲突，优先遵循直接的 system、developer 和 [AGENTS.md](/home/yhz61010/StudioProjects/android/AGENTS.md) 指令，再将 [CLAUDE.md](/home/yhz61010/StudioProjects/android/CLAUDE.md) 与 `.claude/**` 作为补充说明使用。
