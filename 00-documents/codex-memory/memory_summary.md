v1

## User Profile

用户长期在 Android 多模块仓库、`dcd-demo`/`dcd-server`/`starpaypro` 这一组项目里工作，常把 agent 用于代码 review、Gradle/JitPack 排障、仓库协作文档编写，以及本地共享记忆维护。高频任务不是泛泛问答，而是“先读真实代码/日志/配置，再给最小可复核结论”的工程协作。

用户希望 agent 直接执行并给证据链，而不是先讲大段方案。默认中文沟通；写 `AGENTS.md` 或类似仓库指南时，正文通常要中文、文件名保持英文，内容要短、直接、紧贴当前仓库。做 review 时，用户更在意业务需求是否满足、跨仓库影响是否正确、以及下一轮复审是否容易对照，而不是抽象的代码洁癖。

这位用户还会持续维护 Codex/Claude 派生记忆和外部镜像，所以路径映射、共享记忆入口、以及“Claude 文件只读”的边界是长期高优先级约束。对需要正常文件系统/网络访问的构建验证，用户已经明确允许按本机直执行思路处理。

## User preferences

- 默认使用中文回复；若任务是生成或更新 `AGENTS.md`，沿用“记住，以后用中文与 AGENTS.md”：正文中文，文件名保持 `AGENTS.md`
- 遇到 `CLAUDE.md`、`.claude/**`、外部 `Claude/` 记忆目录时，默认按“任何时候都不要修改 Claude 的文件”处理：只读读取，转换内容写到 Codex-owned 目录 [ad-hoc note]
- 遇到“唤醒记忆”时，先重载共享记忆/当前 `.claude` 内容，再决定是否下钻 `rollout_summaries/`；不要直接进入实现
- 更新主 Codex 记忆时，默认同步 StarPay mirror `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/10-StarPay/Codex/current-codex-memory/`，也同步 DCD mirror `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex/current-codex-memories/` [ad-hoc note]
- 更新 `starpaypro` 的 `AGENTS.md` 时，默认同步到 `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/10-StarPay/AGENTS.md` [ad-hoc note]
- 用户要求 review 时，默认围绕业务需求和跨仓库影响组织结论；若同时涉及 Demo/生产双端，优先把生产代码放在主审查路径
- 用户说“我的修改方案你看一下”时，要按实际代码逐项核对，不要把用户方案或 assistant 方案当成已落地事实
- 构建/Gradle/JitPack 失败先从真实日志、依赖链、publication 配置、仓库源顺序定位最小根因；用户更新文件后，要重新看当前状态，不沿用旧结论
- 用户要求“在新分支修改发现的问题”时，默认先 review/定位，再建分支修复；收尾补“主要修改了什么内容”
- 对迭代 review 任务，默认保留下一轮复审清单，不要在未看新代码前提前结案

## General Tips

- 先按 `cwd` 路由：`/home/yhz61010/StudioProjects/android`、`/home/yhz61010/StudioProjects/dcd-demo`、`/home/yhz61010/NST/AndroidProjects/starpaypro` 是不同项目，很多规则只能在同 checkout 复用。
- StarPay/DCD 的 shared-memory 唤醒顺序先看仓库 `AGENTS.md`，再看 mirror 里的 `memory_summary.md`、`MEMORY.md`，最后按关键词定向开 `rollout_summaries/`；不要先扫整份 `raw_memories.md`。
- StarPay/Claude 路径映射先用已验证本地路径：`~/yhz61010/Documents/StarPay/` -> `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/10-StarPay`；DCD 本机项目路径是 `/home/yhz61010/StudioProjects/dcd-demo`，不要把旧远端路径当成当前路径 [ad-hoc note]
- 遇到 DCD review，先确认业务链路和字段语义：outbox/pending-result 生命周期、`hasPendingResult` 的 BUSY 判断、`pairingCode + orderNo` 隔离、`RefundFee` vs `amount`、DCD 设置入口权限。
- 查 Android JitPack 问题先搜 `DeviceCompat`、`XXPermissions`、`version https://git-lfs.github.com/spec/v1`、`generatePomFileForAndroidbasePublication`、`jitpack.yml`。
- `.so` 链接时报 `unknown directive: version` 时，优先怀疑 Git LFS pointer；同时看文件首行、`git ls-tree -l` blob 大小，以及 `jitpack.yml` 是否在 native 编译前执行了 LFS 拉取。
- `demo` 模块 unresolved reference 不一定是源码丢失，可能是 provider 模块 Kotlin 增量产物陈旧；先看 provider 源文件和 `build/intermediates`，再决定是否 `--rerun-tasks`。
- `/home/yhz61010/StudioProjects/android` 的风格迁移完成标准是 `detekt`、`ktlintCheck`、物理行长扫描、`git diff --check`；只改配置通常不够。
- `.claude` 记忆链接可能陈旧；查单个文件是否存在时，用定向 `find`/文件名搜索，不要先全量扫目录。

## What's in Memory

### /home/yhz61010/StudioProjects/dcd-demo

#### 2026-05-25

- DCD shared-memory AGENTS 与外部镜像: AGENTS.md, 共享记忆加载, current-codex-memories, 唤醒记忆, 20-DCD/Codex, diff -qr
  - desc: `dcd-demo` 如何通过 `AGENTS.md` 启动共享记忆、同步外部 DCD Codex mirror、保持 Claude 只读。遇到 fresh clone 记忆恢复、DCD mirror 更新、或路径映射问题时先搜这里。
  - learnings: mirror 路径是 `.../20-DCD/Codex/current-codex-memories/`；验证镜像一致性优先用 `diff -qr -x .git -x .agents -x .codex`；“唤醒记忆”可作为简短触发词 [ad-hoc note]

### /home/yhz61010/NST/AndroidProjects/starpaypro

#### 2026-05-25

- StarPay shared-memory 唤醒与 Claude 只读边界: 唤醒记忆, AGENTS.md, current-codex-memory, MEMORY.md, memory_summary.md, Claude readonly, DCD path
  - desc: `starpaypro` 的共享记忆入口、外部 mirror、Claude 只读边界和 DCD 本地路径映射。遇到 StarPay 记忆恢复、外部记忆读取顺序、或 mirror 同步时先搜这里。
  - learnings: 实用加载顺序是 `AGENTS.md` -> `current-codex-memory/MEMORY.md` -> `memory_summary.md` -> 定向 `rollout_summaries/`；不要先扫 `raw_memories.md` [ad-hoc note]

### /home/yhz61010/StudioProjects/dcd-demo + /home/yhz61010/NST/AndroidProjects/starpaypro

#### 2026-05-19

- DCD 业务状态机 review 与复审清单: DCD, StarPay Pro, dcd-demo, dcd-server, outbox, CANCEL, pairingCode, RefundFee, HelpActivity
  - desc: 跨 `dcd-demo`、`dcd-server` 和 `starpaypro` 的业务 review 记忆。遇到 DCD 联调、退款校验、状态机复审、或 Claude Code 改动复核时先搜这里。
  - learnings: 重点检查 `pendingResult` 生命周期、`hasPendingResult` 的 BUSY 逻辑、`(pairingCode, orderNo)` 隔离、`HelpActivity` 的 DCD 设置入口、以及 REFUND 不能套用 `amount > 0`。

### /home/yhz61010/StudioProjects/android

#### 2026-05-18

- basenetty review 与 demo 增量编译排障: basenetty, EventBus, BaseNettyClient, demo, assembleDevDebug, androidbase, --rerun-tasks
  - desc: `basenetty` 模块 review/修复，以及 `demo` 被 `androidbase` 陈旧 Kotlin 产物阻塞的排障路径。遇到模块回归测试补齐、生命周期缺陷、或 provider 模块增量产物异常时先搜这里。
  - learnings: `CopyOnWriteArrayList`、证书字节缓存、`disconnectManually()` 非初始化分支、`handlerRemoved()` 状态保护已验证；`./gradlew :androidbase:compileDebugKotlin --rerun-tasks` 能刷新陈旧产物。

- JitPack / LFS / publication / style-policy 调试: DeviceCompat, git-lfs, version https://git-lfs.github.com/spec/v1, jitpack.yml, duplicate coordinates, max_line_length 100
  - desc: Android 仓库里常见的 JitPack 失败模式与风格迁移收尾标准。遇到 `DeviceCompat` 解析失败、jpeg `.so` 链接错误、POM duplicate coordinates、或 detekt/ktlint 行长治理时先搜这里。
  - learnings: `.so` 报 `unknown directive: version` 先查 LFS 实化；POM 阶段报 multiple publications 先查 `singleVariant(\"release\")` 与显式 `MavenPublication(\"release\")` 的坐标冲突。

### Older Memory Topics

#### /home/yhz61010/StudioProjects/android

- `.claude` 记忆重载与 AGENTS 落点: .claude, CLAUDE.md, 唤醒记忆, AGENTS.md, AGENTS.zh-CN.md, 00-documents
  - desc: Android 仓库里 `.claude` 记忆如何重载、哪些规则应沉淀到 AGENTS、以及双语文档/提交约束；cwd=/home/yhz61010/StudioProjects/android。

#### /home/yhz61010/NST/AndroidProjects/starpaypro

- 中文 AGENTS.md 贡献者指南: AGENTS.md, starpaypro, 文档用中文写, UTF-8, 文件名要用英文, settings.gradle, shared-libs
  - desc: `starpaypro` 仓库的协作文档与模块概览记忆。写 `AGENTS.md`、贡献指南或确认模块/测试/本地依赖布局时先搜这里；cwd=/home/yhz61010/NST/AndroidProjects/starpaypro。

#### /home/yhz61010/StudioProjects/dcd-demo

- Repository guideline authoring 请求: AGENTS.md, Repository Guidelines, concise, 200-400 words, repo-specific
  - desc: 只有文档写作偏好，没有已验证仓库事实。写 `dcd-demo` 的 AGENTS/Repository Guidelines 时可借用篇幅和结构约束；cwd=/home/yhz61010/StudioProjects/dcd-demo。

#### Cross-workflow collaboration

- 中文协作默认值: 中文, 用中文与我对话, AGENTS.md, language preference
  - desc: 用户级语言与 AGENTS 文档偏好。任何未指定语言的任务、尤其文档任务，先搜这里；cwd=workflow-wide。
