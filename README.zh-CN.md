[English](README.md) | [简体中文](README.zh-CN.md)

[![Leo Version](https://jitpack.io/v/com.leovp/android.svg)](https://jitpack.io/#com.leovp/android)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.3.10-blue)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.0.1-orange)](https://developer.android.com/studio/releases/gradle-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.0-green)](https://gradle.org)
[![Native FFmpeg](https://img.shields.io/badge/Native_FFmpeg-8.1.1-important)](https://www.ffmpeg.org/releases/ffmpeg-8.1.1.tar.xz)
[![JavaCPP FFmpeg](https://img.shields.io/badge/JavaCPP_FFmpeg-8.0.1--1.5.13-important)](https://repo1.maven.org/maven2/org/bytedeco/ffmpeg/8.0.1-1.5.13/)

[![Android Studio](https://img.shields.io/badge/Android_Studio-Panda_4_|_2025.3.4_Patch_1-blue)](https://developer.android.com/studio)
[![Build Java Version](https://img.shields.io/badge/JDK-17-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Java/JVM Target](https://img.shields.io/badge/Java%2FJVM_Target-17-green)](https://docs.oracle.com/en/java/javase/17/)
[![NDK](https://img.shields.io/badge/NDK-29.0.14206865-important)](https://developer.android.com/ndk/downloads)

[![CodeFactor](https://www.codefactor.io/repository/github/yhz61010/android/badge)](https://www.codefactor.io/repository/github/yhz61010/android)

# 注意事项

项目配置：

- `minSdk` 为 `21`（Android 5.0），此前为 ~~`24`（Android 7.0）~~。
- `targetSdk` 为 `36`（Android 16，Baklava）。

由于当前参与的其他项目存在兼容性限制，`minSdk` 目前必须保持为 `21`，暂时不能提高。这也会间接限制部分开源依赖升级到最新版本。

本项目使用 **Git LFS** 管理大型二进制文件，包括 `.so`、`.a`、`.mp3`、`.mp4`、`.tar.xz` 等。

## 首次配置

1. 克隆仓库前安装 Git LFS：

   ```bash
   # Ubuntu/Debian
   sudo apt install git-lfs

   # macOS
   brew install git-lfs
   ```

2. 克隆仓库：

   ```bash
   git clone https://github.com/yhz61010/android.git
   cd android
   git lfs install
   ```

   网络较慢时，可以在克隆阶段跳过 LFS 文件下载，之后再单独拉取：

   ```bash
   GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/yhz61010/android.git
   cd android
   git lfs install
   git lfs pull
   ```

3. 验证 Git LFS：

   ```bash
   git lfs ls-files
   ```

未正确安装 Git LFS 时，受 LFS 管理的文件只会显示为大约 130 字节的指针文件。安装 Git LFS 并运行 `git lfs pull` 即可获取实际内容。

更多说明请参阅 [Git LFS 指南](00-documents/git-lfs-guide.md)。

# 项目概览

`LeoAndroidBaseUtil` 是一个通过 JitPack 发布的多模块 Android 工具库项目。它不是单一应用，也不是面向单一业务领域的 SDK。仓库包含可复用的 Kotlin 与 Android 基础能力、较高层的平台组件、媒体与 Native 集成，以及用于组合验证各个库的两个 Demo 应用。

当前参与 Gradle 构建的模块以 `settings.gradle.kts` 为准。仓库根目录中存在某个目录，并不代表它就是当前启用的模块；未在 `settings.gradle.kts` 中引入的历史目录或实验目录不会参与当前构建。

## 架构概览

本仓库更适合被理解为横向工具库集合，而不是采用统一 Clean Architecture 或应用级数据流的单一项目。建议的依赖方向为：

```text
纯 Kotlin 基础库
        ↓
Android 基础与横切能力库
        ↓
androidbase 与专用功能库
        ↓
demo / demo-dex 集成应用
```

底层库应保持可复用，不应反向依赖上层功能模块或 Demo。`androidbase` 是常用 Android 能力的主要集合，但并不是所有工具代码的默认归宿。纯 Kotlin、图片、JSON、网络、反射和权限敏感代码应分别放入对应的专用模块。

该示意图描述的是目标职责边界，并不是对当前 Gradle 依赖图的机械复制。仓库中仍存在历史形成的跨层依赖，例如 `lib-common-android` 的自定义 Toast 实现直接使用了 `floatview`。如果不重构 `ToastExt.kt`，或者不改变 Android 11 及以上版本的自定义 Toast 行为，就不能直接删除该 Gradle 依赖。

## 模块地图

| 分类 | 模块 | 职责 |
|---|---|---|
| Demo 应用 | `demo`、`demo-dex` | 交互示例与跨模块集成验证 |
| 公共基础库 | `lib-common-kotlin`、`lib-common-android`、`lib-bytes`、`lib-json`、`lib-compress`、`lib-image`、`lib-exif`、`lib-reflection` | Kotlin、Android、字节、序列化、图片、EXIF 和反射工具 |
| 核心与横切能力库 | `log`、`pref`、`lib-network`、`http`、`lib-mvvm`、`lib-compose`、`androidbase`、`android-restricted` | 日志、偏好设置、网络、架构辅助、Compose、Android 通用工具和受限 API |
| Camera、音频与媒体 | `audio`、`camerax`、`camera2live`、`screencapture`、`yuv`、`jpeg`、`ffmpeg-javacpp`、`adpcm-ima-qt-codec`、`h264-hevc-decoder`、`adpcm-ima-qt-codec-h264-hevc-decoder` | 采集、播放、录制、像素转换、Codec、FFmpeg 和 JNI 集成 |
| 设备、图形和其他功能 | `draw-on-screen`、`floatview`、`opengl`、`nfc`、`basenetty`、`aidl-client`、`dex`、`circle-progressbar` | UI 浮层、OpenGL、NFC、Socket、AIDL、动态代码加载和通用控件 |

## 关键模块

### `androidbase`

主要 Android 工具模块，提供 Activity、Fragment、Application 和 Service 基类、协程与生命周期辅助、Android 与 Kotlin 扩展、密码学工具、设备与网络工具，以及 H.264、H.265、YUV、WAV 和 MediaCodec 相关能力。

该模块通过 Gradle `api` 暴露了多个底层模块，因此修改它的公开 API 可能通过传递依赖影响大量调用方。涉及敏感权限或受限平台能力的实现应放入 `android-restricted`，不要重新将这些权限引入 `androidbase`。

### `camerax`

该模块提供完整的 CameraX 使用流程，不只是简单的 CameraX 扩展。它包含权限处理、预览、对焦与曝光控制、拍照、录像、相册与媒体查看、图像分析、方向处理、偏好设置和声音反馈。

主流程从 `CameraXActivity` 和 `CameraFragment` 开始，通用拍照与媒体处理逻辑位于 `BaseCameraXFragment`。

### `camera2live`

该模块是较底层的 Camera2 录像实现，主要数据流为：

```text
BaseCamera2Fragment
  → Camera2ComponentHelper
  → Camera2 / ImageReader
  → YUV 处理策略
  → CameraAvcEncoder / MediaCodec
  → H.264 编码数据回调
```

该流程依赖摄像头驱动、YUV 布局、硬件 Codec 和 Android 生命周期行为，因此除单元测试和仪器测试外，还必须进行真机验证。

### `audio` 与 Native Codec 模块

`audio` 包含录音、播放、AAC、Opus、PCM，以及同步和异步 MediaCodec 抽象。Codec、YUV、JPEG 和 FFmpeg 模块会组合 Kotlin/Java API 与 JNI、C/C++ 或预编译 Native 库。

修改 Native 代码时，必须同步核对 Kotlin/Java native 声明、导出符号、CMake 输入、打包 ABI 和 Git LFS 对象。

### `demo` 与 `demo-dex`

Demo 项目既是使用示例，也是集成检查入口。`demo` 覆盖 Android 工具、密码学、网络、CameraX、Camera2、音频、屏幕采集、OpenGL、NFC、蓝牙、Wi-Fi 和其他功能模块；`demo-dex` 主要验证动态 Dex 加载。

修改公开库 API 时，应同步更新测试和对应 Demo 调用点，再编译受影响的下游模块。涉及跨模块变更时，`:demo:assembleDevDebug` 是主要集成构建任务。

## 构建与验证

共享构建配置位于 `build.gradle.kts`，启用的模块位于 `settings.gradle.kts`，SDK、工具链、依赖和发布版本集中在 `gradle/libs.versions.toml`。项目使用 JDK 17 和仓库内置的 Gradle Wrapper。

常用命令：

```bash
# 构建所有已配置模块
./gradlew assemble

# 构建主要 Demo 集成变体
./gradlew :demo:assembleDevDebug

# 强制运行单个模块的单元测试
./gradlew :androidbase:testDebugUnitTest --rerun-tasks

# 运行全仓库格式与静态检查
./gradlew ktlintCheck detekt

# 验证本地 Maven/JitPack 发布产物
./gradlew publishToMavenLocal
```

所有 Gradle `Test` 任务均使用 JUnit 5。JVM 测试位于 `src/test`，设备测试位于 `src/androidTest`。Camera、音频、MediaCodec、MediaProjection、OpenGL、NFC、蓝牙和 JNI/Native 相关修改还必须在真机上测试。当相关 API 或硬件行为存在版本差异时，至少覆盖一台 API 21 至 26 设备和一台较新的 Android 设备。

项目必须继续支持 API 21。新增 Android 或 Java API 前，应确认其 API level；需要更高版本时，必须提供 SDK 判断、AndroidX 兼容 API 或等价的低版本实现。

## 推荐阅读入口

- `settings.gradle.kts`：当前启用模块的事实来源。
- `build.gradle.kts`：共享 Android、Kotlin、测试、Lint 和发布配置。
- `gradle/libs.versions.toml`：SDK、工具链、依赖和发布版本。
- `androidbase/src/main/kotlin/com/leovp/androidbase`：主要 Android 工具 API。
- `demo/src/main/kotlin/com/leovp/demo/MainActivity.kt`：主要 Demo 入口。
- `demo/src/main/kotlin/com/leovp/demo/basiccomponents/BasicFragment.kt`：基础功能目录。
- `camerax/src/main/kotlin/com/leovp/camerax/fragments/CameraFragment.kt`：CameraX 工作流。
- `camera2live/src/main/kotlin/com/leovp/camera2live/Camera2ComponentHelper.kt`：Camera2 拍摄与录像协调器。

# 故障排查

## JitPack 依赖下载失败

如果遇到 `Could not find <library>.aar (com.github.xxx:xxx)`，并且 Gradle 的搜索位置指向腾讯云、阿里云等镜像而不是 JitPack，可能是 Gradle 缓存从错误仓库解析了依赖元数据。

清理受影响依赖的错误缓存，然后重新构建：

```bash
# 示例：清理 com.github.liangjingkanji:Net 缓存
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.github.liangjingkanji
rm -rf ~/.gradle/caches/modules-2/metadata-2.*/descriptors/com.github.liangjingkanji

# 重新构建
./gradlew :demo:assembleDevDebug
```

# 使用已发布的库

使用已发布产物的调用方不需要克隆本仓库，也不需要复制本仓库的 `gradle.properties.template`。在项目的 `settings.gradle.kts` 中加入 JitPack：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

仅使用库时不需要签名凭据。签名配置只用于从本仓库构建或签名应用，并且由各应用模块分别管理。

## Kotlin DSL

在版本目录中按需添加库：

```toml
[libraries]
leo-androidbase = { module = "com.leovp.android:androidbase", version.ref = "<latest-version>" }
leo-lib-compose = { module = "com.leovp.android:lib-compose", version.ref = "<latest-version>" }
leo-pref = { module = "com.leovp.android:pref", version.ref = "<latest-version>" }
```

然后在模块的 `build.gradle.kts` 中引入：

```kotlin
implementation(libs.leo.androidbase)
```

## Groovy DSL

```groovy
dependencies {
    implementation "com.leovp.android:androidbase:<latest>"
    implementation "com.leovp.android:lib-compose:<latest>"
    implementation "com.leovp.android:pref:<latest>"
}
```

## 构建本仓库

从源码构建时，将 `gradle.properties.template` 复制为 `gradle.properties`，并按需调整本地 Gradle 配置。应用签名由各个应用模块单独配置；设置 CI/CD 凭据前，请先阅读 [Keystore 安全指南](00-documents/KEYSTORE_SECURITY_GUIDE.md)。

# 上传到 Bintray（Maven 和 JCenter）

```sh
./gradlew clean build bintrayUpload -PbintrayUser=yhz61010 -PbintrayKey=<Your API Key> -PdryRun=false
```

可以通过 [API Key](https://bintray.com/profile/edit) 获取 API Key。

# 已知问题

1. ~~Audio Demo 的 `AudioActivity` 无法在 OnePlus 8T 上播放 AAC 文件。该问题已解决，参见 `AacFilePlayer.kt` 和提交 `bf1bfe248a54308cd91cfa7f516026b237413119`。~~
2. Audio Demo 实时通信使用 8 kHz、16 bit、双声道 Codec 时，接收端音量过低。
3. `FFMpegH264Activity` 和 `FFMpegH265Activity` 播放视频时返回上一页面，会在 `h264_hevc_decoder_all_in_one_file.cpp` 的 `decode()` 方法调用 `avcodec_send_packet` 时崩溃。

# TODO

~~1. Camera2Live：初始化 `Camera2Component` 时必须手动指定编码器类型。后续版本将根据摄像头参数自动识别编码器。该问题已解决。~~

~~2. Network Monitor：原实现只能在日志中查看网络流量和 Ping，不能通过监听器获取。后续版本将提供网络流量和 Ping 变化监听器。该问题已解决。~~

# 日志系统

多数项目都会使用日志封装来统一管理日志。本项目通过 `LogContext` 允许宿主项目注入自己的日志实现，从而决定日志如何输出和保存。

项目提供了 `LLog`，它是 Android 默认 Log 的封装。自定义日志实现需要继承 `AbsLog`；`AbsLog` 已实现 `ILog`，可以传给 `LogContext.setLogImpl()`。仅实现 `ILog` 还不能直接注入，因为当前 `setLogImpl()` 接收的是 `AbsLog`。

应尽早初始化 `LogContext`，推荐在 `Application` 中完成。初始化前，`LogContext` 会使用关闭输出的 `LLog` 作为后备实现，因此日志调用不会崩溃，但日志内容会被丢弃。

具体用法请参考 `LogActivity`。也可以使用腾讯 Mars 的 Xlog 实现自定义日志包装器。

# Camera2Live 模块设备信息

原 README 声明，下列设备均支持 `OMX.google.h264.encoder` H.264 编码器。

设备型号、摄像头硬件等级、编码器、FPS 和分辨率列表属于原始兼容性记录。为避免两份大型数据表产生内容漂移，完整数据继续维护在英文 README 的 [Camera2Live Device Camera Information List](README.md#about-camera2live-moduledevice-camera-information-list) 中。
