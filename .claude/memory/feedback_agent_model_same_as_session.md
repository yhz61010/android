---
name: 代理使用与当前会话相同的模型
description: 调用 ECC 插件代理（及其他子代理）时，必须覆盖其定义中的 model，使用与当前主会话相同的模型
type: feedback
---

调用任何子代理（尤其是 ECC 插件代理，如 `ecc:kotlin-reviewer`、`ecc:cpp-reviewer`、
`ecc:code-reviewer`、`ecc:security-reviewer`、`ecc:planner`、`ecc:tdd-guide` 等）时，
**不要沿用代理定义里的 `model: sonnet/opus/haiku`**，而是通过 Agent 工具的 `model`
参数显式指定为与当前主会话相同的模型。

当前主会话模型为 Fable 5.1（`~/.claude/settings.json` 中 `claude-fable-5-1[1m]`），
因此调用时传 `model: "fable"`。若日后主会话模型变更，以当时的主会话模型为准。
`fork` 类型天然继承主模型，无需额外处理。

**Why:** 用户于 2026-09-15 明确要求代理工作时使用和当前会话相同的模型，
不接受插件默认的降级模型（sonnet/haiku）。

**How to apply:** 每次 Agent 调用都带上 `model` 覆盖；项目规则
`.claude/rules/common/performance.md` 中的 Haiku/Sonnet/Opus 分层选型策略在本项目中
被此约定覆盖，不再按其分配模型。
