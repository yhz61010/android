# Git LFS Usage Guide

## Overview

Git LFS (Large File Storage) replaces large files in the repository with lightweight pointers, while storing the actual file contents on a remote server. This significantly reduces the `.git` directory size.

---

## 1. Install Git LFS

```bash
# Ubuntu/Debian
sudo apt install git-lfs

# macOS
brew install git-lfs
```

## 2. Enable in Repository

```bash
git lfs install
```

## 3. Track Large File Types

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

This automatically generates rules in `.gitattributes`:

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

## 4. Commit `.gitattributes`

```bash
git add .gitattributes
git commit -m "Track large binary files with Git LFS"
```

## 5. Migrate Existing History (Key Step)

`git lfs track` only applies to **new commits**. To clean up large files already in the Git history:

```bash
git lfs migrate import --include="*.so,*.o,*.a,*.tar.xz,*.tar.bz2,*.tar.gz,*.mp3,*.mp4,*.wav,*.pcm,*.h264,*.h265,*.caf,*.ima,*.ima4" --everything
```

This **rewrites Git history**, converting large files in history to LFS pointers. The `.git` directory size will be significantly reduced.

### Clean History

```bash
# Clear all reflog entries. Reflog is Git's "undo history" that records every change to HEAD and branches.
# It holds references to old commits, preventing old blobs from being garbage collected.
# --expire=now expires all entries immediately (default retention is 90 days).
git reflog expire --expire=now --all
# Garbage collection. --prune=now immediately deletes all unreachable objects
# (blobs no longer referenced by any commit/branch/tag/reflog).
# --aggressive recompresses the packfile more thoroughly — slower but better compression.
git gc --prune=now --aggressive
```

## 6. Force Push

Since history has been rewritten, a force push is required:

```bash
git push --force --all
git push --force --tags
```

---

## Important Notes

| Item | Description |
|---|---|
| **GitHub Free Quota** | 1 GB storage + 1 GB/month bandwidth |
| **Collaborators** | All collaborators must re-clone after migration |
| **JitPack** | JitPack supports Git LFS, but verify that prebuilt `.so` files can be fetched properly |
| **Irreversible** | `migrate import` rewrites history — back up first |

---

## First-Time Clone (For New Users)

If the repository already uses Git LFS, follow these steps after cloning:

### Step 1: Install Git LFS

```bash
# Ubuntu/Debian
sudo apt install git-lfs

# macOS
brew install git-lfs
```

### Step 2: Clone the repository

```bash
# Option A: Normal clone (automatically downloads LFS files)
git clone https://github.com/yhz61010/android.git

# Option B: Clone without downloading LFS files first (for slow networks)
GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/yhz61010/android.git
cd android
git lfs pull    # Download LFS files manually
```

### Step 3: Enable Git LFS in local repository

```bash
cd android
git lfs install
```

After this, all future `git pull` and `git checkout` operations will automatically handle LFS files.

### Verify LFS is working

```bash
git lfs ls-files    # Should list all LFS-tracked files
```

If files show as pointer text (starting with `version https://git-lfs.github.com/spec/v1`) instead of actual content, run:

```bash
git lfs pull
```

---

## Adding a New File Type to LFS

When you need to track a new file extension (e.g., `.aac`) that is **not yet** in `.gitattributes`:

```bash
# 1. Add the new tracking rule
git lfs track "*.aac"

# 2. Commit the updated .gitattributes
git add .gitattributes
git commit -m "Track *.aac files with Git LFS"

# 3. Add and commit the new files
git add path/to/file.aac
git commit -m "Add audio file"
git push
```

If there are already `.aac` files in the Git history that you want to migrate to LFS:

```bash
git lfs migrate import --include="*.aac" --everything
git push --force --all
```

---

## Adding a New File of an Already-Tracked Type

If the file type is already tracked by LFS (e.g., `.so` is already in `.gitattributes`), simply add and commit the file — Git LFS handles it automatically:

```bash
git add path/to/newlib.so
git commit -m "Add new native library"
git push
```

No extra steps needed. Git automatically stores it via LFS based on the existing `.gitattributes` rules.

You can verify the file is being tracked by LFS:

```bash
git lfs ls-files | grep newlib.so
```

---

## Removing Old Large Blobs from History

When you optimize or replace a large file (e.g., compress an image), the old version still exists in Git history as a blob. Commands like `git rev-list --objects --all | git cat-file --batch-check` will still show the old size.

### Find old large blobs

```bash
git rev-list --objects --all \
  | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
  | awk '/^blob/ && $3 > 500000 {print $3, $4}' \
  | sort -rn
```

### Method 1: Using `git filter-repo` (Recommended)

`git filter-repo` is the modern replacement for `git filter-branch`, faster and safer.

```bash
# Install
pip install git-filter-repo

# Remove a specific file from all history
git filter-repo --path demo/src/main/res/drawable-nodpi/img_3024x4032.jpeg --invert-paths

# Or remove by file size (e.g., blobs larger than 1MB)
git filter-repo --strip-blobs-bigger-than 1M
```

### Method 2: Using `git filter-branch`

If `git filter-repo` is not available:

```bash
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch --index-filter \
  'git rm --cached --ignore-unmatch path/to/large-file' \
  --tag-name-filter cat -- --all
```

### After rewriting history

```bash
# Clean up backup refs
git for-each-ref --format='delete %(refname)' refs/original | git update-ref --stdin

# Expire reflog and garbage collect
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Force push all branches and tags
git push --force --all
git push --force --tags
```

> **Warning:** Rewriting history changes all commit hashes. All collaborators must re-clone the repository after a force push. For small files (under ~1MB), the trade-off is usually not worth it.

---

## Common Commands

```bash
git lfs ls-files          # List all LFS-tracked files
git lfs status            # Show LFS file status
git lfs env               # Show LFS environment info
git lfs untrack "*.mp3"   # Stop tracking a file type
```
