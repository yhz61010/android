# Git LFS Usage Guide / Git LFS 使用指南

## Overview / 概述

Git LFS (Large File Storage) replaces large files in the repository with lightweight pointers, while storing the actual file contents on a remote server. This significantly reduces the `.git` directory size.

Git LFS（大文件存储）用轻量级指针替代仓库中的大文件，实际文件内容存储在远程服务器上。这能显著减小 `.git` 目录体积。

---

## 1. Install Git LFS / 安装 Git LFS

```bash
# Ubuntu/Debian
sudo apt install git-lfs

# macOS
brew install git-lfs
```

## 2. Enable in Repository / 在仓库中启用

```bash
git lfs install
```

## 3. Track Large File Types / 追踪大文件类型

```bash
git lfs track "*.so"
git lfs track "*.a"
git lfs track "*.tar.xz"
git lfs track "*.tar.bz2"
git lfs track "*.tar.gz"
git lfs track "*.mp3"
```

This automatically generates rules in `.gitattributes`:

以上命令会在 `.gitattributes` 中自动生成规则：

```
*.so filter=lfs diff=lfs merge=lfs -text
*.a filter=lfs diff=lfs merge=lfs -text
*.tar.xz filter=lfs diff=lfs merge=lfs -text
*.tar.bz2 filter=lfs diff=lfs merge=lfs -text
*.tar.gz filter=lfs diff=lfs merge=lfs -text
*.mp3 filter=lfs diff=lfs merge=lfs -text
```

## 4. Commit `.gitattributes` / 提交 `.gitattributes`

```bash
git add .gitattributes
git commit -m "Track large binary files with Git LFS"
```

## 5. Migrate Existing History (Key Step) / 迁移已有历史中的大文件（关键步骤）

`git lfs track` only applies to **new commits**. To clean up large files already in the Git history:

`git lfs track` 仅对**新提交**生效。要清理历史中已有的大文件：

```bash
git lfs migrate import --include="*.so,*.a,*.tar.xz,*.tar.bz2,*.tar.gz,*.mp3" --everything
```

This **rewrites Git history**, converting large files in history to LFS pointers. The `.git` directory size will be significantly reduced.

该命令会**重写 Git 历史**，将历史中的大文件转为 LFS 指针，`.git` 目录会大幅缩小。

## 6. Force Push / 强制推送

Since history has been rewritten, a force push is required:

由于历史被重写，需要强制推送：

```bash
git push --force --all
git push --force --tags
```

---

## Important Notes / 注意事项

| Item / 项目 | Description / 说明 |
|---|---|
| **GitHub Free Quota / GitHub 免费额度** | 1 GB storage + 1 GB/month bandwidth / 1GB 存储 + 1GB/月带宽 |
| **Collaborators / 协作者** | All collaborators must re-clone after migration / 迁移后所有协作者需重新 clone |
| **JitPack** | JitPack supports Git LFS, but verify that prebuilt `.so` files can be fetched properly / JitPack 支持 Git LFS，但需确认预编译 `.so` 能正常拉取 |
| **Irreversible / 不可逆** | `migrate import` rewrites history — back up first / `migrate import` 会重写历史，建议先备份 |

## Common Commands / 常用命令

```bash
git lfs ls-files          # List all LFS-tracked files / 列出所有 LFS 追踪的文件
git lfs status            # Show LFS file status / 显示 LFS 文件状态
git lfs env               # Show LFS environment info / 显示 LFS 环境信息
git lfs untrack "*.mp3"   # Stop tracking a file type / 停止追踪某类文件
```
