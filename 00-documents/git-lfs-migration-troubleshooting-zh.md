# Git LFS 迁移排障记录

## 初始问题

- `.git` 目录：**220M**，工作区总计：**484M**
- 根本原因：大量二进制文件在多个提交和标签中被直接提交到 Git 历史
  - FFmpeg 源码压缩包（`ffmpeg-4.4.tar.bz2`、`ffmpeg-5.0.tar.xz`、`ffmpeg-6.0.tar.xz`、`ffmpeg-8.1.tar.xz`）：累计约 42M
  - 预编译静态库 `.a`（`libx264.a`、`libturbojpeg.a`、`libjpeg.a`）：约 28M+
  - 预编译动态库 `.so` 及构建中间产物（`libc++_shared.so`、`libx264a.so`）：约 40M+
  - 媒体测试文件（`music.mp3`、`*.wav`、`*.pcm`、`*.h264`、`*.h265`、`*.caf`）：约 20M+
  - 这些文件有多个历史版本，导致 packfile 膨胀

## 尝试 1：添加 LFS 追踪规则并推送

**操作：**

```bash
git lfs track "*.so" "*.a" "*.mp3" ...
git add .gitattributes
git commit && git push
```

**结果：** 失败。`git lfs track` 仅对**新提交**生效。历史提交中的文件仍以完整 blob 形式存在于 Git 对象中。`.git` 目录大小没有减小。

**教训：** 追踪规则本身不会重写历史。

## 尝试 2：运行 `git lfs migrate import --everything`

**操作：**

```bash
git lfs migrate import \
  --include="*.so,*.a,*.tar.xz,*.tar.bz2,*.tar.gz,*.mp3,*.wav,*.pcm,*.h264,*.h265,*.265,*.caf,*.ima4" \
  --everything
```

**结果：** 失败。所有输出显示 `A -> A`（前后哈希相同），意味着**没有任何文件被转换**。

**根因：** `.gitattributes` 中已经包含了上一步添加的 `filter=lfs` 规则。`git lfs migrate import` 检测到已有 LFS 规则后，认为迁移已完成，跳过了所有文件。

## 尝试 3：先移除 LFS 规则，再迁移

**操作：**

```bash
# 从 .gitattributes 中移除 LFS 规则
sed -i '/filter=lfs/d' .gitattributes

# 重新运行迁移
git lfs migrate import \
  --include="*.so,*.a,*.tar.xz,*.tar.bz2,*.tar.gz,*.mp3,*.wav,*.pcm,*.h264,*.h265,*.265,*.caf,*.ima4" \
  --everything
```

**结果：** 仍然显示 `A -> A`，没有转换。

**根因：** 虽然工作目录中的 `.gitattributes` 被修改了，但每个历史提交中已提交的 `.gitattributes` 仍然包含 LFS 规则（来自尝试 1）。`migrate import` 逐提交读取 `.gitattributes`，而不是从工作目录读取。

## 尝试 4：先导出再重新导入

**操作：**

```bash
git lfs migrate export --include="..." --everything
git lfs migrate import --include="..." --everything
```

**结果：** 失败。仍有 181 个大 blob。`.git` 膨胀至 **865M**，因为 export 将 LFS 指针还原为完整文件，但 import 未能重新转换。

## 尝试 5：使用 --force 参数

**操作：**

```bash
git lfs migrate import --include="..." --everything --force
```

**结果：** 错误：`unknown flag: --force`。git-lfs 3.6.1 不支持此参数。

## 尝试 6：全新克隆（不含 LFS 规则）+ 迁移

**操作：**

```bash
cd /tmp
rm -rf android
GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/yhz61010/android.git
cd android
sed -i '/filter=lfs/d' .gitattributes
git lfs migrate import --include="..." --everything
```

**结果：** 仍然 `A -> A`。验证显示仍有 181 个大 blob。

**根因调查：** 检查哪些提交包含这些大 blob：

```bash
git cat-file -p <blob_hash> | head -c 20   # 确认是真实二进制数据（ID3 = MP3 文件头）
git branch --all --contains <commit_hash>   # 发现位于：remotes/origin/feature-cmake、remotes/origin/fix/detekt-issues
```

**发现根因：** 大 blob 存在于**仅远程的分支**（`feature-cmake`、`fix/detekt-issues`、`fix/ffmpeg-native-bugs`、`fix/floatview-bugs`、`master-groovy-version`）中。`git lfs migrate import --everything` 只处理**本地分支和标签**，不处理远程跟踪引用。这些远程分支从未在本地检出，因此它们的历史从未被迁移。

## 最终成功方案

### 第 1 步：删除过时的远程分支

```bash
git push origin --delete feature-cmake fix/detekt-issues fix/ffmpeg-native-bugs fix/floatview-bugs master-groovy-version
```

### 第 2 步：全新克隆并验证

```bash
cd /tmp
rm -rf android
GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/yhz61010/android.git
cd android
```

### 第 3 步：检查结果

```bash
git rev-list --objects --all \
  | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
  | awk '/^blob/ && $3 > 1000000' | wc -l
# 结果：1（一个 2MB 的 .ima 文件）

du -sh .git
# 结果：31M
```

### 第 4 步：将剩余文件类型加入 LFS

在 `.gitattributes` 中添加 `*.ima` 并运行迁移处理最后一个文件。

### 最终结果

```
大文件 blob 数：0
.git 大小：30M（从 220M 降下来）
```

## 关键经验

1. **`git lfs track` 不会重写历史。** 它只对新提交生效。必须运行 `git lfs migrate import` 来转换历史中的 blob。

2. **如果 `.gitattributes` 已有 LFS 规则，`git lfs migrate import` 会跳过文件。** 首次运行迁移前，务必确保没有已存在的 `filter=lfs` 规则。

3. **`--everything` 不处理远程跟踪分支。** 它只处理本地分支和标签。从未在本地检出的远程分支（`remotes/origin/*`）会保留其原始大 blob。

4. **过时分支是最大的陷阱。** 已合并但未删除的旧功能分支会保持对迁移前 blob 的引用，即使在 `master` 上成功迁移后也无法缩减体积。

5. **正确的操作流程是：**
   - 先删除所有过时/已合并的远程分支
   - 全新克隆：`GIT_LFS_SKIP_SMUDGE=1 git clone <repo>`
   - 移除 `.gitattributes` 中已有的 `filter=lfs` 规则（如果有的话）
   - 运行：`git lfs migrate import --include="<patterns>" --everything`
   - 验证：`git rev-list --objects --all | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' | awk '/^blob/ && $3 > 1000000' | wc -l`
   - 清理：`git reflog expire --expire=now --all && git gc --prune=now --aggressive`
   - 推送：`git push --force --all && git push --force --tags`

6. **`.gitattributes` 规则冲突很重要。** 如果 `*.so binary` 出现在 `*.so filter=lfs` 之后，`binary` 规则胜出（最后匹配优先），LFS 将不会管理 `.so` 文件。每种文件类型只保留一条规则。
