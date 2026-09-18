---
name: feedback-repo-scope-single-project
description: 本仓库的文档与记忆只覆盖自身，不引入其它项目的内容
metadata:
  type: feedback
---

本仓库是**公开仓库**。所有文档、记忆、rollout summary 只描述本仓库，不得引入其它项目的
名称、路径或经验。

**Why:** 公开仓库中混入其它项目的内容会随发布一并公开。

**How to apply:**
- 生成任何文档或记忆前，先确认内容只与本仓库相关。
- 需要清理时按关键词扫描务必人工复核，避免误伤同名的技术标识符
  （例：`x264/src/main/jni/libx264/tools/gas-preprocessor.pl` 中的汇编伪指令）。
- 用户明确表示：需要删除时**不要补写删除说明**，静默删除即可。

相关：[[feedback-docs-location]]、[[feedback-sync-memory]]、[[feedback-memory-sync-policy]]
