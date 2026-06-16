# Codex 记忆快照

本目录保存这个 Android 项目的 Codex 记忆快照，目的是让以后 clone 仓库的 agent 或贡献者可以直接了解项目历史上下文、用户偏好和已验证的工作规则。

## 读取顺序

从全新 clone 启动时，按下面顺序读取：

1. `memory_summary.md`
2. `MEMORY.md`
3. `extensions/ad_hoc/notes/`
4. 当 `MEMORY.md` 指向具体历史任务时，再读取 `rollout_summaries/` 下的对应文件

`local-command-execution.md` 记录了原 Codex 记忆中的本机命令执行偏好。

## 已包含内容

- `MEMORY.md`：可搜索的长期项目记忆索引。
- `memory_summary.md`：用户偏好和常见项目知识的摘要。
- `local-command-execution.md`：本机验证命令偏好。
- `extensions/ad_hoc/`：用户明确要求写入的临时记忆 note。
- `rollout_summaries/`：被 `MEMORY.md` 引用的历史会话摘要。

## 未包含内容

下面这些内容有意不放入项目快照：

- `.git/`：源记忆目录的本地 Git 元数据。
- `raw_memories.md`：原始记忆导出，噪音较多，也可能包含更多本机细节。
- 原始 session JSONL 文件：正常项目交接不需要完整实现日志。

## 同步规则

活动中的 Codex 主记忆仍然是本机 `~/.codex/memories` 目录。本目录只是适合提交到仓库的共享快照。当主记忆有变化且需要让以后 clone 的人继承时，提交前把本目录从 `~/.codex/memories` 刷新一遍。

刷新本快照时不要修改 Claude 管理的文件。除非用户明确要求，`CLAUDE.md`、`.claude/**` 和外部 Claude 记忆目录都只作为只读参考。
