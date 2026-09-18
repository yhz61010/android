# FFmpeg Runtime Capability 验证工程

一个独立的消费者工程，用于证明三个 FFmpeg 封装模块**无法被同时引入同一个 App**。

English version: [README.md](README.md)

## 为什么需要它

下面三个已发布模块各自打包了**同一套 native 库**：

| 模块 | 内容 |
|------|------|
| `adpcm-ima-qt-codec` | ADPCM IMA QT 音频 |
| `h264-hevc-decoder` | H.264 / HEVC 视频 |
| `adpcm-ima-qt-codec-h264-hevc-decoder` | 上面两者合并为一个产物 |

同时依赖其中任意两个，APK 里就会出现**两份同名 `.so`**。最终哪一份生效取决于打包器和加载器，
因此故障只会在**运行期、真机上**暴露——表现为崩溃，或者更糟：静默的错误行为。

为了把它变成构建期失败，根 `build.gradle.kts` 给这三个模块加了一个共享 capability：

```kotlin
// build.gradle.kts，对 ffmpegNativeRuntimeModules 中的每个模块生效
outgoing.capability("com.leovp.android:ffmpeg-native-runtime:${project.version}")
```

Gradle 规定**同一个 capability 只允许有一个提供者**，所以同时请求其中两个会在依赖解析阶段
直接失败，并给出明确的冲突信息。

**本工程用于验证这层保护真的生效**——针对的是**真实发布产物**而不是本地项目依赖图。因为
capability 存在于发布出来的 Gradle Module Metadata 中，用 composite build 是验证不到的。

## `verify.sh` 做了什么

1. 从 `gradle/libs.versions.toml` 读取 `leo-version`。
2. 用 `publishReleasePublicationToMavenLocal` 把三个模块发布到一个由 `mktemp -d` 创建的
   **临时** Maven 仓库。脚本用 `trap` 在退出时删除它，**不会污染本机 `~/.m2`**。
3. 断言每个发布出来的 `.module` 元数据里确实声明了 `"name": "ffmpeg-native-runtime"`。
4. **正向用例**——分别单独依赖三个模块构建本工程的 `:app`，三次都必须组装成功。
5. **负向用例**——分别依赖三种两两组合（音频+视频、音频+合并、视频+合并）构建 `:app`，
   三次都**必须失败**，且构建日志中必须能匹配到 `ffmpeg-native-runtime` capability 字样。
   **某个组合如果构建成功，脚本会 `exit 1`**：那意味着保护已经失效。

`app/build.gradle.kts` 只是一个空壳 APK，依赖项由 Gradle property 动态注入，这正是一个工程
就能覆盖全部六种用例的原因：

```kotlin
implementation("com.leovp.android:$module:$ffmpegVersion")  // module 来自 -PffmpegModules
```

## 如何运行

```bash
./10-configs/ffmpeg-runtime-consumer-test/verify.sh
```

在任意目录下执行均可，脚本会根据自身位置推算仓库根目录。成功时输出：

```
FFmpeg runtime capability verification passed.
```

除此之外的任何结果都是失败——脚本开启了 `set -euo pipefail`，遇到第一个问题就会停下。

### 前置条件

- **JDK 17** 与可用的 Android SDK，与主构建要求一致。
- **ripgrep（`rg`）** 需在 `PATH` 中。脚本用它检索发布元数据和构建日志，缺失会直接失败。
- **不需要设备或模拟器。** 这里不在 Android 上执行任何代码，只检查组装与依赖解析。

耗时较长：先发布三个模块，再跑六次构建，且全部带 `--rerun-tasks`。

## 与主构建的关系

这是一个**独立的 Gradle 构建**，自带 `settings.gradle.kts`，并且**有意没有**被写入仓库的
`settings.gradle.kts`。因此：

- `./gradlew staticCheck`、`assembleDebug` 等日常任务**完全不会触及它**。
- 它不会被发布，也不属于任何发布产物。
- 它优先从 `mavenLocal()` 解析依赖，这正是它能看到那个临时仓库的原因。

## 何时需要重跑

改动以下任一项之后需要重跑：

- 根 `build.gradle.kts` 中的 capability 声明，或它所作用的 `ffmpegNativeRuntimeModules` 集合；
- 三个封装模块的发布配置（坐标、variant、Module Metadata）；
- 这些模块打包了哪些 native 库，或打包方式。

## 失败如何解读

| 现象 | 含义 |
|------|------|
| 某个正向用例组装失败 | 该模块已无法被单独消费——发布或打包出现了回归。 |
| 某个组合竟然组装成功 | **保护已经失效。** 两套同名 `.so` 现在能同时进入一个 APK。 |
| 组合确实失败，但日志里没有 capability 字样 | 它是因别的原因挂的，该用例什么也没证明。查看脚本输出中给出的日志文件路径。 |
| `rg: command not found` | 请安装 ripgrep；此次什么都没有验证到。 |

## 文件说明

| 路径 | 用途 |
|------|------|
| `verify.sh` | 验证逻辑的全部。以下文件都只是为它服务。 |
| `settings.gradle.kts` | 声明这是独立构建；`mavenLocal()` 优先。 |
| `build.gradle.kts` | 本工程的根构建脚本。 |
| `app/build.gradle.kts` | 空壳 APK；依赖来自 `-PffmpegModules` / `-PffmpegVersion`。 |
| `app/src/main/AndroidManifest.xml` | 最小清单，使 APK 可被组装。 |

## 来历

由提交 `e9ac8a093 fix(native): harden media modules and packaging` 引入。相应的验证记录见
`00-documents/2026-08-27-native-modules-remediation-plan-zh.md` §1.1。
