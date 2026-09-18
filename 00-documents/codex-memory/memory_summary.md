v2

## 项目概况

这是一个使用 Gradle Kotlin DSL 构建、通过 JitPack 发布的多模块 Android 工具库仓库。当前代码、配置和 `AGENTS.md` 始终优先于历史记忆；记忆只用于定位既有经验和待复核事项。

## 稳定协作偏好

- 默认使用中文沟通；代码注释和提交信息使用英文。
- 只要求审查、诊断、解释或方案时保持只读；明确要求修改后才编辑。
- 不修改 `CLAUDE.md`、`.claude/**` 或外部 Claude 文件。
- Claude Code 在远端服务器运行，不能编译代码或执行任何 Gradle 命令；其开发和审查结论只能作为源码静态分析。
- Codex 可以在本机执行受影响范围的构建、质量检查、单测和已授权真机测试。
- 交付时分别报告静态审查、Gradle/质量检查、单测、构建、真机验证和未执行项。
- 保留用户已有修改；未经明确授权不提交、推送、发布、合并、reset、clean、stash、pull、merge 或 rebase。

## 使用提示

- Git 同步先检查工作树、暂存区和 ahead/behind，再决定是否需要整合远端。
- 历史 `BUILD SUCCESSFUL`、设备结果、分支名、提交号和工具链版本都不可直接当作当前证据。
- Camera、音频、MediaCodec、MediaProjection、OpenGL、窗口生命周期、NFC、蓝牙和 JNI/Native 结论必须保留真机验证边界。
- Gradle/Git 的只读锁或缓存写入错误通常是环境权限问题，不应直接归因为代码或网络故障。
- 发布和 Native 问题优先核查实际 Gradle/CMake 输入、Git LFS 实化、ABI、产物内容和发布元数据。

## 记忆主题

### 安全 Git 与交付

- 工作树、暂存区、未推送提交和上游差异必须分开报告。
- `fetch` 只刷新远端引用，不等于已经整合；整合动作需要单独授权。

### 设备标识与 Kotlin 修复

- `getUuid()` 返回随机 UUID v4，不是稳定设备标识。
- MediaDrm 资源需要在所有路径释放，设备标识还需评估隐私、恢复出厂和厂商行为。
- Kotlin 原始字符串中的多行运行时插值可能绕过 `trimIndent()` 的预期缩进。

### Camera 与媒体生命周期

- MediaCodec buffer、EGL context、callback 和关闭屏障都需要明确 owner 与线程约束。
- 协程取消应保留并重抛；初始化中断、迟到回调和重复关闭都需要独立覆盖。
- 静态性能判断不能替代 FPS、丢帧、时延、内存和实际画面/声音验证。

### JitPack、Native 与发布

- Native 链接出现 `unknown directive: version` 时先检查输入是否仍是 Git LFS pointer。
- Android library 若被根构建统一引用 `consumer-rules.pro`，模块根目录必须存在该文件。
- Release 说明只基于已推送范围，不能混入本地未提交修改。

## 历史资料入口

详细任务索引见 `MEMORY.md`。只在索引明确指向时读取对应 `rollout_summaries/`，并对其中所有时效性事实重新验证。
