---
name: docs-save-location
description: Documents should be saved to ./00-documents, not ./docs
type: feedback
---

项目中需要保存文档时，保存到 `./00-documents` 目录下，而不是 `./docs`。

**Why:** 项目已有 `00-documents` 目录作为文档存放位置，用户明确指定了这个约定。

**How to apply:** 任何需要生成或保存 Markdown 文档（如 /save-solution）时，目标路径使用 `./00-documents/`。
