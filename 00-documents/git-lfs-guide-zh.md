# Git LFS 使用指南

## 概述

Git LFS（大文件存储）用轻量级指针替代仓库中的大文件，实际文件内容存储在远程服务器上。这能显著减小 `.git` 目录体积。

---

## 1. 安装 Git LFS

```bash
# Ubuntu/Debian
sudo apt install git-lfs

# macOS
brew install git-lfs
```

## 2. 在仓库中启用

```bash
git lfs install
```

## 3. 追踪大文件类型

```bash
git lfs track "*.so"
git lfs track "*.o"
git lfs track "*.a"
git lfs track "*.tar.xz"
git lfs track "*.tar.bz2"
git lfs track "*.tar.gz"
git lfs track "*.mp3"
git lfs track "*.mp4"
git lfs track "*.wav"
git lfs track "*.pcm"
git lfs track "*.h264"
git lfs track "*.h265"
git lfs track "*.caf"
git lfs track "*.ima"
git lfs track "*.ima4"
```

以上命令会在 `.gitattributes` 中自动生成规则：

```
*.so filter=lfs diff=lfs merge=lfs -text
*.o filter=lfs diff=lfs merge=lfs -text
*.a filter=lfs diff=lfs merge=lfs -text
*.tar.xz filter=lfs diff=lfs merge=lfs -text
*.tar.bz2 filter=lfs diff=lfs merge=lfs -text
*.tar.gz filter=lfs diff=lfs merge=lfs -text
*.mp3 filter=lfs diff=lfs merge=lfs -text
*.mp4 filter=lfs diff=lfs merge=lfs -text
*.wav filter=lfs diff=lfs merge=lfs -text
*.pcm filter=lfs diff=lfs merge=lfs -text
*.h264 filter=lfs diff=lfs merge=lfs -text
*.h265 filter=lfs diff=lfs merge=lfs -text
*.caf filter=lfs diff=lfs merge=lfs -text
*.ima filter=lfs diff=lfs merge=lfs -text
*.ima4 filter=lfs diff=lfs merge=lfs -text
```

## 4. 提交 `.gitattributes`

```bash
git add .gitattributes
git commit -m "Track large binary files with Git LFS"
```

## 5. 迁移已有历史中的大文件（关键步骤）

`git lfs track` 仅对**新提交**生效。要清理历史中已有的大文件：

```bash
git lfs migrate import --include="*.so,*.o,*.a,*.tar.xz,*.tar.bz2,*.tar.gz,*.mp3,*.mp4,*.wav,*.pcm,*.h264,*.h265,*.caf,*.ima,*.ima4" --everything
```

该命令会**重写 Git 历史**，将历史中的大文件转为 LFS 指针，`.git` 目录会大幅缩小。

### 清理历史

```bash
# 清除所有分支的 reflog 记录。Reflog 是 Git 的"撤销历史"，记录了 HEAD 和分支的每次变动。
# 它会引用旧的 commit，导致旧 blob 无法被回收。
# --expire=now 表示立即过期所有记录（默认保留 90 天）。
git reflog expire --expire=now --all
# 垃圾回收。--prune=now 立即删除所有不可达的对象（不再被任何 commit/分支/标签/reflog 引用的 blob）；
# --aggressive 更彻底地重新压缩 packfile，耗时更长但压缩率更高。
git gc --prune=now --aggressive
```

## 6. 强制推送

由于历史被重写，需要强制推送：

```bash
git push --force --all
git push --force --tags
```

---

## 注意事项

| 项目 | 说明 |
|---|---|
| **GitHub 免费额度** | 1GB 存储 + 1GB/月带宽 |
| **协作者** | 迁移后所有协作者需重新 clone |
| **JitPack** | JitPack 支持 Git LFS，但需确认预编译 `.so` 能正常拉取 |
| **不可逆** | `migrate import` 会重写历史，建议先备份 |

---

## 首次克隆仓库（新用户）

如果仓库已经启用了 Git LFS，克隆后请按以下步骤操作：

### 第一步：安装 Git LFS

```bash
# Ubuntu/Debian
sudo apt install git-lfs

# macOS
brew install git-lfs
```

### 第二步：克隆仓库

```bash
# 方式 A：普通克隆（自动下载 LFS 文件）
git clone https://github.com/yhz61010/android.git

# 方式 B：先克隆但不下载 LFS 文件（适合网络较慢的情况）
GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/yhz61010/android.git
cd android
git lfs pull    # 手动下载 LFS 文件
```

### 第三步：在本地仓库中启用 Git LFS

```bash
cd android
git lfs install
```

之后，所有 `git pull` 和 `git checkout` 操作都会自动处理 LFS 文件。

### 验证 LFS 是否正常工作

```bash
git lfs ls-files    # 应列出所有 LFS 追踪的文件
```

如果文件显示为指针文本（以 `version https://git-lfs.github.com/spec/v1` 开头）而非实际内容，运行：

```bash
git lfs pull
```

---

## 新增文件类型到 LFS

当需要追踪一个**尚未**在 `.gitattributes` 中的新文件扩展名时（如 `.aac`）：

```bash
# 1. 添加新的追踪规则
git lfs track "*.aac"

# 2. 提交更新后的 .gitattributes
git add .gitattributes
git commit -m "Track *.aac files with Git LFS"

# 3. 添加并提交新文件
git add path/to/file.aac
git commit -m "Add audio file"
git push
```

如果 Git 历史中已经存在需要迁移到 LFS 的 `.aac` 文件：

```bash
git lfs migrate import --include="*.aac" --everything
git push --force --all
```

---

## 新增已追踪类型的文件

如果文件类型已被 LFS 追踪（如 `.so` 已在 `.gitattributes` 中），直接添加并提交即可，Git LFS 会自动处理：

```bash
git add path/to/newlib.so
git commit -m "Add new native library"
git push
```

无需额外操作。Git 会根据 `.gitattributes` 中的规则自动通过 LFS 存储该文件。

可以验证文件是否被 LFS 追踪：

```bash
git lfs ls-files | grep newlib.so
```

---

## 从历史中移除旧的大 blob

当你优化或替换了一个大文件（例如压缩图片）后，旧版本仍然以 blob 形式存在于 Git 历史中。使用 `git rev-list --objects --all | git cat-file --batch-check` 等命令仍会显示旧的大小。

### 查找旧的大 blob

```bash
git rev-list --objects --all \
  | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
  | awk '/^blob/ && $3 > 500000 {print $3, $4}' \
  | sort -rn
```

### 方法 1：使用 `git filter-repo`（推荐）

`git filter-repo` 是 `git filter-branch` 的现代替代工具，更快更安全。

```bash
# 安装
pip install git-filter-repo

# 从所有历史中移除指定文件
git filter-repo --path demo/src/main/res/drawable-nodpi/img_3024x4032.jpeg --invert-paths

# 或按文件大小移除（例如大于 1MB 的 blob）
git filter-repo --strip-blobs-bigger-than 1M
```

### 方法 2：使用 `git filter-branch`

如果无法使用 `git filter-repo`：

```bash
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch --index-filter \
  'git rm --cached --ignore-unmatch path/to/large-file' \
  --tag-name-filter cat -- --all
```

### 重写历史后的清理工作

```bash
# 清除备份引用
git for-each-ref --format='delete %(refname)' refs/original | git update-ref --stdin

# 清除 reflog 并垃圾回收
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# 强制推送所有分支和标签
git push --force --all
git push --force --tags
```

> **警告：** 重写历史会改变所有 commit 哈希。强制推送后，所有协作者必须重新克隆仓库。对于较小的文件（不超过约 1MB），通常不值得为此重写整个历史。

---

## 常用命令

```bash
git lfs ls-files          # 列出所有 LFS 追踪的文件
git lfs status            # 显示 LFS 文件状态
git lfs env               # 显示 LFS 环境信息
git lfs untrack "*.mp3"   # 停止追踪某类文件
```
