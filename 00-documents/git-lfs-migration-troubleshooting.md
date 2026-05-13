# Git LFS Migration Troubleshooting Record

## Initial Problem

- `.git` directory: **220M**, total working tree: **484M**
- Root cause: large binary files committed directly into Git history across many commits and tags
  - FFmpeg source tarballs (`ffmpeg-4.4.tar.bz2`, `ffmpeg-5.0.tar.xz`, `ffmpeg-6.0.tar.xz`, `ffmpeg-8.1.tar.xz`): ~42M cumulative
  - Prebuilt static libraries `.a` (`libx264.a`, `libturbojpeg.a`, `libjpeg.a`): ~28M+
  - Prebuilt shared libraries `.so` and build artifacts (`libc++_shared.so`, `libx264a.so`): ~40M+
  - Media test files (`music.mp3`, `*.wav`, `*.pcm`, `*.h264`, `*.h265`, `*.caf`): ~20M+
  - These files had multiple historical versions, inflating the packfile

## Attempt 1: Add LFS tracking rules and push

**Action:**

```bash
git lfs track "*.so" "*.a" "*.mp3" ...
git add .gitattributes
git commit && git push
```

**Result:** Failed. `git lfs track` only affects **new commits**. Files in historical commits remained as full blobs in Git objects. The `.git` directory size did not decrease.

**Lesson:** Tracking rules alone do not rewrite history.

## Attempt 2: Run `git lfs migrate import --everything`

**Action:**

```bash
git lfs migrate import \
  --include="*.so,*.a,*.tar.xz,*.tar.bz2,*.tar.gz,*.mp3,*.wav,*.pcm,*.h264,*.h265,*.265,*.caf,*.ima4" \
  --everything
```

**Result:** Failed. All output showed `A -> A` (identical hashes before and after), meaning **nothing was converted**.

**Root cause:** `.gitattributes` already contained `filter=lfs` rules from the previous step. `git lfs migrate import` detected existing LFS rules and assumed the migration was already done, so it skipped all files.

## Attempt 3: Remove LFS rules, then migrate

**Action:**

```bash
# Remove LFS rules from .gitattributes
sed -i '/filter=lfs/d' .gitattributes

# Re-run migration
git lfs migrate import \
  --include="*.so,*.a,*.tar.xz,*.tar.bz2,*.tar.gz,*.mp3,*.wav,*.pcm,*.h264,*.h265,*.265,*.caf,*.ima4" \
  --everything
```

**Result:** Still showed `A -> A`. No conversion happened.

**Root cause:** Even though the working directory `.gitattributes` was modified, the committed `.gitattributes` in each historical commit still contained the LFS rules (from Attempt 1). `migrate import` reads the `.gitattributes` per-commit, not from the working directory.

## Attempt 4: Export then re-import

**Action:**

```bash
git lfs migrate export --include="..." --everything
git lfs migrate import --include="..." --everything
```

**Result:** Failed. Still 181 large blobs. `.git` grew to **865M** because export restored full blobs but import didn't convert them.

## Attempt 5: Force flag

**Action:**

```bash
git lfs migrate import --include="..." --everything --force
```

**Result:** Error: `unknown flag: --force`. Not supported in git-lfs 3.6.1.

## Attempt 6: Fresh clone without LFS rules + migrate

**Action:**

```bash
cd /tmp
rm -rf android
GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/yhz61010/android.git
cd android
sed -i '/filter=lfs/d' .gitattributes
git lfs migrate import --include="..." --everything
```

**Result:** Still `A -> A`. Verification showed 181 large blobs remaining.

**Root cause investigation:** Checked which commits contained the large blobs:

```bash
git cat-file -p <blob_hash> | head -c 20   # Confirmed real binary data (ID3 = MP3 header)
git branch --all --contains <commit_hash>   # Found: remotes/origin/feature-cmake, remotes/origin/fix/detekt-issues
```

**Root cause discovered:** The large blobs existed on **remote-only branches** (`feature-cmake`, `fix/detekt-issues`, `fix/ffmpeg-native-bugs`, `fix/floatview-bugs`, `master-groovy-version`). `git lfs migrate import --everything` only processes **local branches and tags**, not remote tracking refs. These remote branches were never checked out locally, so their history was never migrated.

## Final Successful Solution

### Step 1: Delete stale remote branches

```bash
git push origin --delete feature-cmake fix/detekt-issues fix/ffmpeg-native-bugs fix/floatview-bugs master-groovy-version
```

### Step 2: Fresh clone and verify

```bash
cd /tmp
rm -rf android
GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/yhz61010/android.git
cd android
```

### Step 3: Check results

```bash
git rev-list --objects --all \
  | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
  | awk '/^blob/ && $3 > 1000000' | wc -l
# Result: 1 (a 2MB .ima file)

du -sh .git
# Result: 31M
```

### Step 4: Add remaining file type to LFS

Added `*.ima` to `.gitattributes` and ran migration for the last file.

### Final Result

```
Large blobs: 0
.git size: 30M (down from 220M)
```

## Key Takeaways

1. **`git lfs track` does not rewrite history.** It only affects new commits. You must run `git lfs migrate import` to convert historical blobs.

2. **`git lfs migrate import` skips files if `.gitattributes` already has LFS rules.** Always ensure there are no existing `filter=lfs` rules before running the migration for the first time.

3. **`--everything` does not process remote tracking branches.** It only processes local branches and tags. Remote-only branches (`remotes/origin/*`) that were never checked out locally retain their original large blobs.

4. **Stale branches are the biggest trap.** Old feature branches that were merged but not deleted will keep references to pre-migration blobs, preventing size reduction even after a successful migration on `master`.

5. **The correct workflow is:**
   - Delete all stale/merged remote branches first
   - Fresh clone: `GIT_LFS_SKIP_SMUDGE=1 git clone <repo>`
   - Remove any existing `filter=lfs` rules from `.gitattributes` (if present)
   - Run: `git lfs migrate import --include="<patterns>" --everything`
   - Verify: `git rev-list --objects --all | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' | awk '/^blob/ && $3 > 1000000' | wc -l`
   - Clean up: `git reflog expire --expire=now --all && git gc --prune=now --aggressive`
   - Push: `git push --force --all && git push --force --tags`

6. **`.gitattributes` conflicts matter.** If `*.so binary` appears after `*.so filter=lfs`, the `binary` rule wins (last match takes precedence), and LFS will not manage `.so` files. Always keep only one rule per file type.
