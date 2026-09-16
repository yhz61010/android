# 仓库指南

## 协作与执行边界

- 使用中文沟通；本文件只维护中文，不创建 `AGENTS.zh-CN.md`。代码注释和提交信息使用英文，不添加 AI 署名或 `Co-Authored-By`。
- 先核对当前代码、配置和命令结果，再下结论；仍有影响方案的不明确事项时先问用户，不凭历史记录猜测。
- 仅要求审查、诊断、解释或方案时保持只读；要求修改时实现并验证。未经明确授权不提交、推送、发布或合并。
- 保留用户已有修改和未跟踪文件，不顺手清理、还原或覆盖无关内容；不要覆盖已有 `gradle.properties`、`local.properties`。
- Codex 可以在本机执行与任务相关的构建、静态检查、单测和已授权的真机测试。旧 Claude 记忆中的“禁止本地编译”不适用于当前 Codex 工作流；环境不足时报告缺口。
- 不把密码、令牌、签名私钥或其它秘密写入代码、日志、文档和记忆；不要求用户在对话中粘贴秘密。
- 本文件是项目共享规则入口；`CLAUDE.md` 和 `.claude/**` 是补充资料。冲突时按系统、开发者、用户当前明确要求及本文件处理，不能用“旧规则更严格”覆盖高优先级或较新的明确要求。

## 项目入口与模块边界

这是使用 Gradle Kotlin DSL 构建、通过 JitPack 发布的横向 Android 工具库集合，不是单一业务应用，不强制采用统一的 Clean Architecture 或依赖注入方案。

仓库内路径均以仓库根目录为基准，不把个人机器绝对路径写进共享规则。事实来源如下：

| 内容 | 权威入口 |
| --- | --- |
| 激活模块、仓库与插件解析 | `settings.gradle.kts` |
| 共享构建、测试、质量检查和发布逻辑 | 根 `build.gradle.kts` |
| 依赖、SDK、NDK、CMake、Java/Kotlin 和发布版本 | `gradle/libs.versions.toml` |
| Gradle 版本 | `gradle/wrapper/gradle-wrapper.properties` |
| 模块依赖、变体、Native 打包 | 对应模块的 `build.gradle.kts`、`CMakeLists.txt` |
| 代码格式、静态分析 | `.editorconfig`、`10-configs/detekt.yml` |

模块导航（完整激活列表以配置为准，Android 资源通常位于各模块的 `src/main/res`）：

- 集成演示：`demo`、`demo-dex`。
- 基础与共享能力：`lib-*`、`log`、`pref`、`http`、`androidbase`、`android-restricted`。
- 媒体：`audio`、`camera2live`、`camerax`、`screencapture`、`yuv`、`jpeg`、`ffmpeg-javacpp`，以及三个预编译 FFmpeg 包装模块。
- 图形与其它功能：`floatview`、`opengl`、`draw-on-screen`、`circle-progressbar`、`nfc`、`basenetty`、`aidl-client`、`dex`。

依赖和职责约束：

- 保持基础工具 → Android 共享能力 → 综合工具/功能模块 → Demo 的依赖方向；这不是要求每层必须经过 `androidbase`，不得引入循环或下层反向依赖 Demo。
- 与 Android 无关的逻辑优先放入对应基础库；Android 通用扩展放 `lib-common-android`；图片、网络、JSON 等归专用模块；敏感权限能力归 `android-restricted`。
- 默认使用 `implementation`；依赖类型属于公开 API 或确需传递暴露时才使用 `api`，同时核查下游影响。
- 修改公开类、构造器、函数、默认参数、返回类型或依赖可见性时，评估源码及二进制兼容性；破坏性变更须明确说明并更新调用点、相关文档和 `CHANGELOG.md`。
- Demo 也是集成验证入口；公开用法改变时同步调整对应示例，不能只验证库自身编译。

## 构建与验证

使用 JDK 17 和仓库自带的 `./gradlew`；其它工具链版本读取上表配置，不在本文件重复维护版本清单。开始构建前检查实际 JDK、SDK 和所需 Native 工具是否可用；仅在文件缺失时根据 `gradle.properties.template` 初始化本地配置。Git LFS 初始化参考 `README.md` 与 `00-documents/git-lfs-guide-zh.md`。

优先验证受影响范围，不默认执行全仓 `clean` 或全量构建：

| 场景 | 命令示例 |
| --- | --- |
| 库模块单测与质量检查（替换模块名） | `./gradlew :audio:testDebugUnitTest :audio:ktlintCheck :audio:detekt --rerun-tasks` |
| Demo 集成编译 | `./gradlew :demo:assembleDevDebug` |
| Demo 单测 | `./gradlew :demo:testDevDebugUnitTest` |
| Demo 真机测试 | `./gradlew :demo:connectedDevDebugAndroidTest` |
| 全仓格式与静态分析 | `./gradlew ktlintCheck detekt` |
| 全量质量验证 | `./gradlew staticCheck` |
| 本地发布链路 | `./gradlew publishToMavenLocal` |
| 库发布规则合并（替换模块名） | `./gradlew :android-restricted:mergeReleaseConsumerProguardFiles --rerun-tasks` |

`testDebugUnitTest` 不能替代带 flavor 的 `testDevDebugUnitTest`。`staticCheck` 聚合质量检查、各模块 debug 变体单测/lint 和应用仪器测试 APK 构建，不代表已执行真机测试。

验证要求：

- 代码或构建变更在提交前运行受影响模块的 `ktlintCheck`、`detekt` 及对应测试；共享构建配置变更扩大到受影响下游。纯文档变更检查事实、路径、格式和 `git diff --check`，不必运行 Gradle。
- 高风险或刚修复的路径使用 `--rerun-tasks`，确认测试实际执行；不能把 `UP-TO-DATE`、`NO-SOURCE` 或编译通过当作回归测试通过。
- 公开 API 变更至少编译库、直接下游和对应 Demo；跨模块契约变更运行 `:demo:assembleDevDebug`。
- Gradle `Test` 使用 JUnit Platform，主要使用 JUnit 5；Robolectric/JUnit 4 测试依赖 Vintage 引擎。单测位于 `src/test/kotlin` 或 `src/test/java`，设备测试位于 `src/androidTest`；测试类以 `Test` 或 `UnitTest` 结尾，优先就近补回归。
- Android 单测启用了默认返回值和资源支持；Mock/Robolectric 不能证明真实 Codec、WindowManager 或 Native 驱动行为。Demo 仪器测试通过 `AndroidJUnitRunner` 和 `AndroidJUnit5Builder` 接入 JUnit 5。
- Camera、音频、MediaCodec、MediaProjection、OpenGL、窗口生命周期、NFC、蓝牙和 JNI/Native 变更需真机验证；至少覆盖一台 API 21～26 和一台较新设备，并检查适用的旋转、前后台、重复进入退出、资源释放和长时间运行场景。
- 媒体验证记录设备/API、分辨率、帧率、编码器、内存和丢帧等相关指标，并验证实际画面/声音；“没有崩溃”不等于功能或性能通过。
- 没有匹配设备或无法执行某项检查时，明确记录未验证项；交付区分静态审查、单测、构建、真机结果及历史结果，不能将已有问题无证据地归为“本次无关”。

## 代码风格与错误处理

- Kotlin 优先，4 空格缩进，包名使用 `com.leovp.*`；类/对象用 `UpperCamelCase`，函数/属性用 `lowerCamelCase`，常量用 `UPPER_SNAKE_CASE`。
- 具体格式遵循 `.editorconfig`，不要另加已禁用的排序规则。Detekt 零容忍；重构后同步清理失效的 import、私有成员和死代码，不通过放宽检查掩盖问题。
- 最低支持 API 21。新增 Android/Java API 和升级依赖前检查最低版本；高版本入口使用 SDK 分支、AndroidX 兼容实现或低版本等价写法，不能仅凭编译通过认定兼容。
- 日志方式依据模块实际依赖：已有 `log` 依赖时用 `com.leovp.log.base.d/e` 或 `LogContext`；没有时不要为日志新增依赖。Android 工具可用 `android.util.Log`，与 Android 无关的逻辑用异常或注入回调，避免新增 Android 依赖。
- `lib-network` 的 `log` 是 `compileOnly`，运行时由调用方提供；不要误当成自动传递依赖。
- `lib-json` 的 `toJsonString()`/`toObject()` 保持现有契约：内部记录转换异常并返回 `""`/`null`，不新增 `onError` 参数；`CancellationException` 继续抛出。
- 涉及异步资源时同时检查正常结束、初始化失败、取消、重复关闭和迟到回调；明确资源所有者与线程约束，不用吞异常替代正确释放。

## Native、ABI 与发布

不要把“目录存在”“Gradle 编译通过”和“Native 源码已进入发布产物”混为一谈：

- `lib-image`、`yuv`、`jpeg` 使用 Gradle/CMake 构建 JNI；入口位于模块根目录或 `src/main/cpp`。`yuv`、`jpeg` 还链接预编译底层库，重编 JNI 不代表底层库已更新。
- `adpcm-ima-qt-codec`、`h264-hevc-decoder`、`adpcm-ima-qt-codec-h264-hevc-decoder` 打包预编译库；相应源码和脚本位于 `ffmpeg-sdk`，重建流程见 `ffmpeg-sdk/docs/README.md`。修改源码后重建对应裁剪配置及包装模块，更新并核对发布二进制；临时启用 `ffmpeg-sdk` 不应进入常规发布配置，不能只跑包装模块的 Gradle 任务。
- 三个预编译 FFmpeg 模块严格三选一：只需音频选音频模块，只需视频选视频模块，同时需要两者选组合模块。保持 release 变体/发布元数据中的共享 capability 冲突保护，不假设 debug 依赖图同样受保护；不能用 `pickFirst` 掩盖不兼容组合。
- 三个包装模块维护 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 产物；同步维护独立模块与组合模块中的重复 JNI 包装代码。涉及二进制时核查全部声明 ABI 的产物，至少验证 ARM 两种 ABI 的真实加载，缺少环境时列明缺口。
- `ffmpeg-javacpp` 当前仅支持 `arm64-v8a`，Native 库来自 `android-arm64` classifier 依赖；检查依赖及发布元数据，不要求其 AAR 本体内嵌所有传递依赖的 `.so`。
- Demo 的 `abiFilters` 不代表每个功能都支持同样的 ABI。其它 Native 模块按自身声明、输入库和最终产物确定验证矩阵。
- `ffmpeg-sdk`、`webrtc`、`x264`、`libjpeg-turbo`、`libyuv` 未纳入当前常规 Gradle 构建，不等于其源码与生成脚本没有用途。
- 修改 JNI 同时核对 Kotlin/Java 声明、注册表/导出符号、CMake 输入、资源生命周期、ABI、`SONAME`/`NEEDED` 与 16 KB 页面对齐；声明支持不能替代构建及加载证据。
- 所有 Android 库需提供模块根目录的 `consumer-rules.pro`（可为空），根构建会统一引用。发布或混淆规则变更验证 release 合并及必要的消费端行为。
- 新增/替换 `.so`、`.a`、媒体或源码压缩包前检查 `.gitattributes` 和 Git LFS 指南，确认不是指针文件被误作真实输入；签名、Native 二进制和发布配置变更在交付中显式说明。

## Git 与交付

- 开始工作先检查 `git status --short --branch`、当前分支及上游；分开报告未提交修改、未推送提交与 ahead/behind。
- 审查分支或合并前用 `git symbolic-ref refs/remotes/origin/HEAD`、远端分支列表和当前 refs 核对主线，不假设名称是 `main`；本地远端引用也可能过期。
- “拉取最新代码”先保护工作树、fetch 并核对差异；未获整合授权不自动 pull、merge、rebase、stash、reset 或 clean。
- 提交使用显式文件列表，并检查 `git diff --cached` 与 `git diff --cached --check`；不夹带用户无关改动。`.idea/**`、`AGENTS.md`、`CLAUDE.md` 默认不提交，用户明确点名授权时例外，不删除或还原其本地内容。
- 使用现有 Git 身份和简短英文祈使句提交标题，可使用 `fix(scope): ...` 等前缀；未经授权不改身份、版本号或创建 Release。
- 推送后核对提交、上游差异及远端结果；本地 commit 成功不等于 push 成功。
- 交付说明修改范围、验证命令/结果及待验证事项；PR 保持聚焦，UI 变更附相关截图。发生合并冲突时记录冲突文件和处理方式。

## 文档、记忆与 Claude 互操作

- AI 生成文档统一放 `00-documents/`，只维护中文，不创建英文副本或另建 `docs/`；普通文档按日期和主题命名，例如 `2026-09-16-native-review.md`。
- Superpowers 设计放 `00-documents/superpowers/specs/`，计划放 `00-documents/superpowers/plans/`，实施记录放 `00-documents/superpowers/`；均使用中文。不为未启用功能创建空目录或模板。
- `CLAUDE.md`、`.claude/**` 及 Claude 生成的文件默认保留，只读参考，用户明确要求时才修改；优化本文件不意味着同步修改 Claude 配置。
- 按任务读取补充材料：工作风格读 `.claude/memory/MEMORY.md` 和存在时的 `.claude/rules/personal-style.md`；Android UI/UX 设计读 `.claude/skills/mobile-android-design/SKILL.md`；技能查找/创建/安装读 `.claude/skills/find-skills/SKILL.md`。路径不存在时说明并使用可用替代，不凭空补文件。
- 有本机主 Codex 记忆时优先按需查询；`00-documents/codex-memory/` 是共享历史快照，不是当前代码、分支或测试结果的事实来源。
- 新克隆需恢复历史时，先读快照 `README.md`，再按索引读 `memory_summary.md`、`MEMORY.md`、相关 `extensions/ad_hoc/notes/` 和 `rollout_summaries/`；无需整库加载。
- 只有用户明确要求时才更新 Codex 记忆；需共享时按授权刷新仓库快照，不同步无关项目资料或敏感内容。旧记忆中的双语文档等约定不得覆盖本文件现行规则。
