# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

**LeoAndroidBaseUtil** 是一个多模块 Android 库项目（`com.leovp.android`），通过 JitPack 发布。包含 40+ 个模块，提供 Android 开发常用工具：字节操作、JSON、图片处理、网络、MVVM 架构、Jetpack Compose 组件、媒体编解码（H.264/HEVC、ADPCM）、相机、NFC、WebRTC 等。

## 构建与开发

- **纯 Kotlin** 代码库，Gradle 使用 Kotlin DSL
- **环境要求**：JDK 17、Android SDK 36、NDK 25.2.9519653
- **初始设置**：将 `gradle.properties.template` 复制为 `gradle.properties` 并配置 keystore 属性

### 常用命令

```bash
./gradlew assembleDebug                    # 构建 debug APK（demo 应用）
./gradlew assembleRelease                  # 构建 release APK
./gradlew testDebugUnitTest                # 运行所有单元测试
./gradlew :lib-bytes:testDebugUnitTest     # 运行单个模块的测试
./gradlew staticCheck                      # 运行全部质量检查（lint、detekt、ktlint、测试）
./gradlew detekt                           # 运行 detekt 代码分析
./gradlew ktlintCheck                      # 运行 ktlint 代码风格检查
./gradlew ktlintFormat                     # 自动修复 ktlint 问题
./gradlew dependencyUpdates                # 检查依赖更新
./gradlew clean                            # 清理构建产物
```

## 构建架构

所有构建配置集中在根目录 `build.gradle.kts` 中：
- `configureApplication()` — 应用于使用 `android.application` 插件的模块（demo 应用）
- `configureLibrary()` — 自动应用于所有库模块
- `configureCompileTasks()` — Java/Kotlin 编译器设置，应用于所有项目
- Java 17 源码/目标兼容性，所有测试任务使用 JUnit Platform
- Detekt 配置文件位于 `10-configs/detekt.yml`，零容忍策略（max issues = 0）
- Ktlint 以 Android 模式运行，使用 checkstyle 报告格式

版本目录文件 `gradle/libs.versions.toml` 管理所有依赖版本、SDK 版本，并定义依赖 bundle（如 `androidx-full`、`lifecycle-full`、`test`、`android-test`）。

## 模块组织

主要模块分类：
- **lib-*** — 纯工具库（lib-bytes、lib-json、lib-compress、lib-common-kotlin、lib-common-android、lib-image、lib-reflection、lib-network）
- **lib-mvvm** — MVVM 架构组件（BaseViewModel、BaseState、BaseAction、UiEventManager）
- **lib-compose** — Jetpack Compose 扩展和组件
- **androidbase** — Android 核心工具，依赖多个 lib-* 模块
- **媒体模块** — audio、h264-hevc-decoder、adpcm-ima-qt-codec、ffmpeg-javacpp、ffmpeg-sdk、yuv、jpeg、x264
- **功能模块** — camerax、camera2live、opengl、nfc、webrtc、screencapture、basenetty、floatview
- **pref** — 偏好设置（基于 MMKV）
- **log** — 日志系统
- **demo** — 主 demo 应用，展示所有模块功能

## 架构模式

MVVM + Clean Architecture（Presentation → Domain → Data 三层架构）。`lib-mvvm` 中的关键组件：
- `BaseViewModel` 配合 `BaseState` 和 `BaseAction` 接口
- `UiEventManager` 处理 UI 事件
- 通过 `lib-compose` 实现 Compose 集成

依赖注入使用 Koin（非 Hilt）。

## 测试

- **JUnit 5**（Jupiter）作为主要测试框架
- **Mockk** 用于 mock，**Kluent** 用于断言，**Robolectric** 用于 Android 单元测试
- 测试并行运行：`maxParallelForks = 可用处理器数 / 2`
- 全局设置 `unitTests.isReturnDefaultValues = true` 和 `isIncludeAndroidResources = true`

## 代码质量

Detekt 和 Ktlint 应用于**所有项目**（包括根项目）。两者均为严格模式 — `ignoreFailures = false`，detekt 最大问题数 = 0。本地运行 `./gradlew staticCheck` 执行完整验证套件。

## 签名配置

Release 构建使用 keystore 属性，来源为 `gradle.properties`（本地）或环境变量（`KEYSTORE_PATH`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，用于 CI/CD）。启用 V1–V4 签名。

## SDK 约束

由于外部项目限制，`minSdk` 为 21（Android 5.0），这限制了部分依赖库的版本升级。
