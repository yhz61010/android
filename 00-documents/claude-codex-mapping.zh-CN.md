# Claude 到 CodeX 的映射说明

本仓库同时保留 Claude Code 元数据与 CodeX 仓库指引。
本文档用于说明：在不删除、不重命名任何 Claude 专用文件的前提下，如何让现有 Claude 资产被 CodeX 复用。

## 基本原则

- 保留所有 Claude Code 文件。
- 以 `AGENTS.md` 作为 CodeX 的主入口。
- 将 `CLAUDE.md` 和 `.claude/**` 视为 CodeX 的补充说明。
- 只将最稳定、最有价值的规则提升到 `AGENTS.md`。
- 通过引用复用已有 Claude skills 和 memory 文件，而不是完整复制它们的内容。

## 映射关系

### `AGENTS.md`

`AGENTS.md` 是 CodeX 的主仓库级指令文件。
它应包含：

- 稳定的仓库工作流规则
- 语言约定
- 文件放置规则
- 明确指出 CodeX 在什么场景下应读取哪些 Claude 文件

### `CLAUDE.md`

`CLAUDE.md` 继续作为 Claude Code 的项目说明文件保留。
对于 CodeX，它应被视为补充项目上下文，尤其包括：

- 构建与开发命令
- 模块总览
- Native 构建说明
- 测试与质量工具说明
- SDK 与工具链限制

### `.claude/rules/*`

Claude 的 rules 映射为 CodeX 的按需补充规则。
当任务涉及代码风格、写作风格或个人工作流约定时，应读取这些文件。

当前示例：

- `.claude/rules/personal-style.md`

### `.claude/memory/*`

Claude 的 memory 文件映射为可复用的仓库偏好和历史决策记录。
对于 CodeX，这些内容应在相关场景中被引用；当其中某些约定演化为稳定团队规则时，可部分提升到 `AGENTS.md`。

当前示例包括：

- 沟通语言偏好
- 文档目录约定
- 注释与提交信息必须使用英文
- 不添加 `Co-Authored-By` trailer

### `.claude/skills/*`

Claude skills 不会自动成为 CodeX 的原生 skills。
在本仓库中，它们通过 `AGENTS.md` 中的说明被复用，即由 `AGENTS.md` 告诉 CodeX 在什么场景下读取哪个 skill 文件。

当前示例：

- `.claude/skills/mobile-android-design/SKILL.md`
- `.claude/skills/find-skills/SKILL.md`

如果未来某个 Claude skill 需要成为真正的一等 CodeX skill，应显式迁移到 CodeX 的 skills 体系，而不是假设它能被自动发现。

### `.claude/commands/*`

Claude commands 不能直接映射为 CodeX 的命令原语。
除非确实有必要将其转换为以下形式，否则应继续将它们保留为 Claude 专用工作流资产：

- 仓库文档
- shell 脚本
- `AGENTS.md` 中的任务型说明

## CodeX 推荐读取顺序

当 CodeX 在本仓库中工作时，建议按以下顺序使用上下文：

1. 先读取 `AGENTS.md`。
2. 再读取 `CLAUDE.md` 作为补充项目说明。
3. 只有在任务与其用途匹配时，再读取对应的 `.claude/**` 文件。

示例：

- 处理仓库风格或贡献者约定时，读取 `.claude/rules/personal-style.md` 和 `.claude/memory/MEMORY.md`。
- 处理 Android 设计任务时，读取 `.claude/skills/mobile-android-design/SKILL.md`。
- 处理 skills 查找或管理相关请求时，读取 `.claude/skills/find-skills/SKILL.md`。

## 后续维护规则

未来新增 Claude 文件时，建议按下面的流程处理：

1. 将 Claude 文件保留在其原始位置。
2. 判断该内容属于以下哪一类：
   - 稳定的仓库规则
   - 补充性的项目上下文
   - 面向特定任务的工作流
3. 如果它是稳定规则，则在 `AGENTS.md` 中做摘要。
4. 如果它是任务型内容，仅当 CodeX 需要在匹配场景下自动参考它时，才在 `AGENTS.md` 中增加简短指针。
5. 除非某条说明必须始终生效，否则不要把大段 Claude 文档直接复制进 `AGENTS.md`。

## 非目标

这套映射方案并不打算：

- 删除 `.claude/**`
- 重命名 Claude 文件
- 把所有 Claude 资产都转换成原生 CodeX skill
- 强制两个系统使用完全相同的文件格式

这套方案的目标是互操作，而不是彻底合并。
