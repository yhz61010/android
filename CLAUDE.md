# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概述

**LeoAndroidBaseUtil** 是一个通过 JitPack 发布的多模块 Android 库项目。当前 Gradle 构建包含 36 个模块，提供字节操作、JSON、图像处理、网络、MVVM 基础组件、Jetpack Compose 组件、媒体编解码（H.264/HEVC、ADPCM）、相机、录屏、OpenGL 和 NFC 等 Android 开发能力。仓库根目录下还保留了一些未纳入当前 Gradle 构建的原生源码、构建辅助目录和历史实验目录。

## 构建与开发

- **主要使用 Kotlin**，同时包含 Java、AIDL、C/C++ 和 JNI 源码；Gradle 构建脚本使用 Kotlin DSL
- **环境要求**：JDK 17、Android SDK 36、NDK 29.0.14206865、CMake 3.22.1
- **当前构建版本**：Gradle 9.4.0、AGP 9.0.1、Kotlin 2.3.10、库发布版本 5.15.8
- **初始化**：将 `gradle.properties.template` 复制为 `gradle.properties`，并按本地环境调整配置；应用签名由各应用模块分别配置

### 常用命令

```bash
./gradlew assembleDebug                    # 构建所有激活模块的 debug 产物
./gradlew assembleRelease                  # 构建所有激活模块的 release 产物
./gradlew :demo:assembleDevDebug            # 构建 demo 应用的 devDebug APK
./gradlew testDebugUnitTest                # 运行所有单元测试
./gradlew :lib-bytes:testDebugUnitTest     # 运行单个模块的测试
./gradlew staticCheck                      # 运行所有质量检查（lint、detekt、ktlint、测试）
./gradlew detekt                           # 运行 detekt 代码分析
./gradlew ktlintCheck                      # 运行 ktlint 风格检查
./gradlew ktlintFormat                     # 自动修复 ktlint 问题
./gradlew dependencyUpdates                # 检查依赖更新
./gradlew clean                            # 清理构建产物
```

## 构建架构

共享构建逻辑和版本约束主要位于根目录 `build.gradle.kts`、`settings.gradle.kts` 和 `gradle/libs.versions.toml`；模块专属配置仍位于各模块自己的 `build.gradle.kts` 中：

- `configureApplication()` — 应用于包含 `android.application` 插件的模块（demo 应用）
- `configureLibrary()` — 自动应用于所有库模块
- `configureCompileTasks()` — Java/Kotlin 编译器设置，应用于所有项目
- Java 17 源码/目标兼容性，所有测试任务使用 JUnit Platform
- Detekt 配置位于 `10-configs/detekt.yml`，零容忍策略
- Ktlint 使用 Android 模式运行，带 checkstyle 报告

版本目录 `gradle/libs.versions.toml` 管理所有依赖版本、SDK 版本，并定义依赖 bundle（如 `androidx-full`、`lifecycle-full`、`test`、`android-test`）。

## 原生构建（CMake）

当前由 Gradle 调用 **CMake** 构建的模块包括：

- `lib-image` — Bitmap 旋转 JNI 库（`leo-bitmap`）
- `yuv` — libyuv 封装（`leo-yuv`）
- `jpeg` — libjpeg-turbo 封装（`leo-jpeg`）

这些模块的 CMake 配置包含 16KB 页面对齐选项，以兼容使用 16KB 内存页的 Android 设备。`CMakeLists.txt` 位于模块根目录或 `src/main/cpp/` 下。

当前激活的 `h264-hevc-decoder`、`adpcm-ima-qt-codec`、`adpcm-ima-qt-codec-h264-hevc-decoder` 和 `ffmpeg-javacpp` 模块直接打包预编译 `.so`，不在日常 Gradle 构建中重新编译对应原生源码。修改这些二进制文件或其生成脚本时，应同时核对 ABI、16KB 页面对齐和 Git LFS 状态。

**外部原生库源码**（非 Gradle 模块，用于离线编译）：

- `libjpeg-turbo/` — libjpeg-turbo 构建脚本和源码压缩包（为 `jpeg` 模块生成 `.so`）
- `ffmpeg-sdk/src/main/ffmpeg_build/` — FFmpeg 构建脚本和源码压缩包

`ffmpeg-sdk`、`webrtc`、`x264`、`libjpeg-turbo` 和 `libyuv` 当前都未写入 `settings.gradle.kts`，因此不是当前激活的 Gradle 模块。

**JNI nativeHandle 模式**：`h264-hevc-decoder` 和 `adpcm-ima-qt-codec` 中的 FFmpeg 封装采用基于实例的架构，每个 Kotlin 对象持有一个 `nativeHandle: Long` 字段存储 C++ 对象指针。该模式替代了之前的全局变量方式，支持多实例和线程安全。对应原生源码及说明保留在未激活的 `ffmpeg-sdk` 目录中，详见 `ffmpeg-sdk/docs/native-handle-pattern-en-zh.md`。

## 模块组织

当前激活模块以 `settings.gradle.kts` 为准，主要分类如下：

- **Demo 应用** — `demo`、`demo-dex`
- **核心与共享库** — `androidbase`、`android-restricted`、`log`、`pref`、`http`、`lib-common-android`、`lib-common-kotlin`、`lib-bytes`、`lib-json`、`lib-compress`、`lib-network`、`lib-reflection`、`lib-image`、`lib-exif`、`lib-mvvm`、`lib-compose`
- **lib-mvvm** — MVVM 架构组件（BaseViewModel、BaseState、BaseAction、UiEventManager）
- **lib-compose** — Jetpack Compose 扩展和组合式组件
- **androidbase** — 核心 Android 工具，依赖多个 lib-* 模块
- **android-restricted** — 隔离需要**受限/敏感权限**的工具（命名空间 `com.leovp.android.restricted`）：刘海屏(notch) `DisplayCutout` 实现、`DeviceProp`、`ApplicationManager`、`DeviceUtil`（电池容量、序列号，需 `READ_PHONE_STATE`）、`ActivityExt`/`AppExt`。这些工具自 `androidbase` 迁出，使敏感权限依赖与核心库解耦
- **媒体与编解码模块** — `audio`、`ffmpeg-javacpp`、`adpcm-ima-qt-codec`、`h264-hevc-decoder`、`adpcm-ima-qt-codec-h264-hevc-decoder`、`yuv`、`jpeg`
- **设备、图形与功能模块** — `camerax`、`camera2live`、`screencapture`、`draw-on-screen`、`floatview`、`opengl`、`nfc`、`basenetty`、`aidl-client`、`dex`、`circle-progressbar`

## 架构模式

本仓库是横向 Android 工具库集合，不采用统一的 Clean Architecture 分层。新增依赖时应保持基础工具库到 Android 共享能力、综合工具层、功能模块，再到 Demo 应用的单向依赖，避免下层模块反向依赖功能模块或 Demo。

`lib-mvvm` 提供可选的 MVVM 基础组件：

- `BaseViewModel` 搭配 `BaseState` 和 `BaseAction` 接口
- `UiEventManager` 处理 UI 事件
- 通过 `lib-compose` 集成 Compose

Koin 当前主要用于 `demo` 中的依赖注入示例，不是所有模块必须遵循的统一依赖注入方案。

## 测试

- **JUnit 5**（Jupiter）作为主要测试框架
- **Mockk** 用于 Mock、**Kluent** 用于断言、**Robolectric** 用于 Android 单元测试
- 测试并行运行：`maxParallelForks = availableProcessors / 2`
- 全局设置 `unitTests.isReturnDefaultValues = true` 和 `isIncludeAndroidResources = true`

高风险或刚修改过的路径不要只依赖 `UP-TO-DATE`，应通过 `--rerun-tasks` 强制重跑对应任务。Camera、Camera2、音频、MediaCodec、MediaProjection、OpenGL、NFC、蓝牙和 JNI/Native 相关修改还必须补充真机验证；若当前没有可用设备，应明确记录未完成的验证项，不要将构建通过等同于真机通过。

## 代码质量

Detekt 和 Ktlint 应用于**所有项目**（包括根项目）。两者都是严格模式 — `ignoreFailures = false`，detekt 最大问题数 = 0。在本地运行 `./gradlew staticCheck` 执行完整的验证套件。

零容忍（`maxIssues=0`）意味着：删除/重构代码后，务必一并清理**随之失效的 import 与私有成员**（未用即 detekt 失败）。

## 签名

签名由各应用模块独立配置。当前 `demo` 的 release 签名配置已注释，以避免 JitPack 环境因缺少 keystore 而构建失败；V1–V4 签名当前启用于其 debug 签名。`aidl-client` 也只将现有签名配置绑定到 debug 构建。若重新启用 release 签名，应使用本地 `gradle.properties` 或 CI/CD 环境变量提供凭据，不要提交真实 keystore 或密码。

## SDK 约束

`minSdk` 为 21（Android 5.0），因外部项目约束限制。这会限制部分依赖的升级。

## 语言约定

- Git commit message 和代码注释统一使用**英文**。
- 在 `00-documents/` 下生成的所有文档只需要中文，无需创建或维护英文版。

## 日志约定

- **可使用 `log` 的模块**（androidbase、lib-compose、lib-json、lib-network、lib-mvvm、camerax、screencapture、nfc、opengl、http、audio、basenetty、aidl-client、android-restricted、demo）→ 用 `com.leovp.log.base.{d,e}` / `LogContext`。其中 `lib-network` 使用 `compileOnly(projects.log)`，运行时由调用方提供日志实现。
- **无 `log` 依赖**的纯工具模块（lib-common-kotlin、lib-common-android、lib-compress、draw-on-screen、lib-bytes、lib-reflection）→ **勿新增 `log` 依赖**；改用 `android.util.Log`、rethrow，或调用方注入的错误处理回调。
- **`lib-json` 例外**：`Any?.toJsonString()` 与 `String?.toObject()` 系列只做 JSON 转换，失败时在函数内部通过 `LogContext` 记录并返回默认值（`""` / `null`），**不暴露 `onError` 参数**；`CancellationException` 仍需向上抛出。

## 外部文档路径

本项目的 AI 生成文档统一管理在项目 `00-documents/` 目录下。该目录下的所有文档只维护中文内容，无需英文版：

| 路径 | 用途 |
|------|------|
| `00-documents/superpowers/` | superpowers 插件产物（specs/、plans/） |

## AI 交互规则

- 若有不明白或不明确的地方，一定要先问我。不要自己幻想或无中生有。
- 用户偏好使用中文对话。

## 项目记忆

项目级记忆（偏好、约定、反馈）存储在 `.claude/memory/` 目录中，包含跨会话的协作约定和工作流偏好。

新会话开始时，请先读取 `.claude/memory/MEMORY.md` 了解已有的记忆内容。
