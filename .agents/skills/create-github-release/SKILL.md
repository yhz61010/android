---
name: create-github-release
description: 为当前仓库创建 GitHub Release；先查询并告知 GitHub 当前最新 Release 版本，再给出推荐新版本号并询问用户要创建的版本号，随后生成英文 Release 标题和更新内容。适用于用户要求 Codex 创建、草拟、发布或准备 GitHub Release、基于 tag 的 release、release notes、changelog-backed release 或版本化 GitHub release 的场景。
---

# Create GitHub Release

使用这个 skill 为当前仓库创建 GitHub Release，并确保 Release 标题和更新内容使用英文。

## 必须先确认

创建或草拟任何 GitHub Release 前，必须先查询 GitHub 当前最新 Release，并把最新 Release 版本号告诉用户；然后基于最新 Release 和已验证的变更性质给出一个推荐的新版本号，再询问用户需要创建的 Release 版本号。

如果无法查询 GitHub 当前最新 Release，必须明确说明查询失败的原因或缺失的权限/工具，并询问用户是否继续使用本地 tag 或用户指定版本进行 dry run / release 准备。

推荐版本号只是建议，不是用户确认。不要从 Gradle 文件、Git tag、分支名、commit message 或文档中自行决定最终版本号。即使用户已经在同一个请求中给出了版本号，也要先复述该版本号并请用户确认，然后才能创建 Release。

推荐版本号规则：

- 如果已验证的 changelog、migration guide、review report 或 commit range 明确包含 breaking changes，推荐最新 Release 的下一个主版本号，例如 `5.15.8` 后推荐 `6.0.0`。
- 如果只包含向后兼容的新功能，推荐下一个次版本号，例如 `5.15.8` 后推荐 `5.16.0`。
- 如果只包含向后兼容的 bug fix、文档或内部维护变更，推荐下一个补丁版本号，例如 `5.15.8` 后推荐 `5.15.9`。
- 如果当前项目不是语义化版本，或最新 Release 版本号无法安全解析，说明无法可靠推荐，并询问用户要使用的版本号。
- 如果用户指定的版本号与推荐版本号不同，只说明差异和依据，不要强行改成推荐版本号。

Release title 必须使用 `Release <confirmed-version>` 格式。例如版本号为 `5.15.9` 时，Release title 必须是 `Release 5.15.9`。

如果以下信息不明确，且会实质影响 Release 内容或创建方式，继续前必须询问用户：

- 目标分支或目标 commit。
- tag 名称格式，尤其是是否使用 `v` 前缀。
- 创建 draft release 还是直接发布。
- 是否标记为 prerelease。
- 更新内容是从 commits、文档还是用户提供的摘要生成。

## 工作流程

1. 先读取仓库说明，包括 `AGENTS.md` 和相关子目录说明。
2. 查询 GitHub 当前最新 Release：
   - 优先使用可用的 GitHub connector。
   - 如果 connector 不可用，可使用 `gh release view --json tagName,name,publishedAt,isDraft,isPrerelease,url`。
   - 只把 GitHub Release 当作“最新 Release”来源；不要把本地最新 tag 直接说成 GitHub 最新 Release。
   - 告诉用户当前最新 Release 版本号、标题和发布时间；如果没有 Release 或无法查询，要明确说明。
3. 基于 GitHub 最新 Release 和已验证变更性质，给出推荐的新版本号，并询问用户要创建的 Release 版本号。
4. 检查 Git 仓库状态：
   - 当前分支与 upstream。
   - 工作区是否干净。
   - 当前 `HEAD`。
   - 现有 tag，以及请求的版本 tag 是否已在本地或远端存在。
5. 仅在用户请求或仓库规则要求时执行 pull 或 fetch。
6. 确定 Release 变更范围：
   - 优先使用上一个 release tag 到已确认 release tag 的范围。
   - 如果无法确定合适的上一个 tag，询问用户应使用哪个范围。
7. 基于已验证来源生成英文 Release 内容：
   - 选定范围内的 Git commits。
   - 相关变更文件。
   - 仓库内已有 changelog 或 migration 文档。
   - 当前任务中执行过的构建、测试或验证结果。
8. Release 内容必须基于事实。不要编造功能、修复、兼容性说明、性能提升或安全影响。
9. 创建 GitHub Release 前，先把拟定的 release title、tag、target commit、draft/prerelease 状态和英文 release notes 展示给用户确认。
10. 只有用户确认准确版本和内容后，才能创建 GitHub Release。
11. 创建完成后，报告 GitHub Release URL、tag、target commit 和实际使用的命令。

## GitHub Release 内容格式

GitHub Release 的 title、release notes、迁移说明、breaking changes、安全说明和 verification 内容都必须使用英文。

Release title 必须是：

```text
Release <confirmed-version>
```

例如：

```text
Release 5.15.9
```

除非项目已有固定格式，否则使用简洁结构：

```markdown
## What's Changed

- ...

## Verification

- ...
```

只有当当前 diff 或项目文档能证实时，才添加 migration notes、breaking changes 或 security notes。

## 工具使用建议

优先使用可用的 GitHub connector 创建 Release。如果 connector 能力不可用，在确认鉴权和仓库身份后使用 `gh release create` 或 `gh release edit`。

通过命令行创建时，使用明确的 target、固定 title 格式和 notes file。示例：

```bash
gh release create <tag> --target <commit> --title "Release <confirmed-version>" --notes-file <notes-file>
```

只有在用户明确要求或确认后，才能追加 `--draft` 或 `--prerelease`。

## 安全规则

- 未询问并确认版本号前，禁止创建 Release。
- 除非用户明确要求对应操作，否则禁止覆盖或删除已有 tag 或 release。
- 当工作区状态导致目标不明确时，禁止 push tag、创建 tag 或发布 Release。
- Release notes 中禁止包含 secrets、本地敏感路径、凭据或私有 token。
- 如果仓库使用 Git LFS、签名、native binaries 或生成产物，需要说明已执行或仍需执行的 release 影响验证。
