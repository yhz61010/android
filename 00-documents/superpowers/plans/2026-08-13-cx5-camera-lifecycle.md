# CX-5 CameraFragment 相机绑定生命周期修正 —— 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `CameraFragment` 相机绑定的 `LifecycleOwner` 从 `this`（Fragment）改为 `viewLifecycleOwner`（View），在 `bindCameraUseCases()` 入口加视图可用性守卫，并让相机切换动画的延迟 UI 恢复随 View 生命周期取消，消除跨 View 销毁的相机泄漏/崩溃隐患。

**Architecture:** 单文件、三类内部改动，无 API 签名变更。与同库 `VideoFragment`（已用 `viewLifecycleOwner`）及既有 CX-3 守卫惯用法（`if (!isAdded || view == null) return`）对齐。相机随 View 生命周期绑定/解绑，View 重建时整体重绑；动画结束后的延迟 UI 操作由触发时的 `viewLifecycleOwner.lifecycleScope` 承载。

**Tech Stack:** Kotlin、AndroidX Fragment、CameraX（`ProcessCameraProvider.bindToLifecycle`）、Gradle、detekt、ktlint。

**关联文档:** 设计 spec `00-documents/superpowers/specs/2026-08-13-cx5-camera-lifecycle-design.md`。

## Global Constraints

- `minSdk` = 21，不得使用更高 API 门槛的调用（`isAdded` / `getView()` / `viewLifecycleOwner` 均 API 21 可用）。
- detekt `maxIssues=0`、ktlint Android 模式，`ignoreFailures=false`：改动不得引入任何未用 import / 私有成员告警。
- `camerax` 模块**有** `log` 依赖：日志一律用 `com.leovp.log.LogContext`（禁用 `android.util.Log`）。
- 代码注释与 commit message 一律**英文**。
- **无对外 API 签名变更**：仅内部行为收敛（相机在 `onDestroyView` 解绑）。
- 相机 + CameraX 硬件依赖，**不做 JVM 单元测试**（与 `testing.md` 一致）；验证 = 编译 + 静态检查 + 记录在案的真机回归清单。
- git 提交需由维护者授权；本计划的 commit 步骤在维护者确认后执行，作者须为 `Michael Leo <yhzemail61010@aliyun.com>`。

---

### Task 1: 修正绑定宿主及离页竞态（CameraFragment.kt）

**Files:**
- Modify: `camerax/src/main/kotlin/com/leovp/camerax/fragments/CameraFragment.kt:260`（函数入口插入守卫）
- Modify: `camerax/src/main/kotlin/com/leovp/camerax/fragments/CameraFragment.kt:408-409`（`this` → `viewLifecycleOwner`）
- Modify: `camerax/src/main/kotlin/com/leovp/camerax/fragments/CameraFragment.kt` 相机切换动画结束回调（延迟 UI 恢复改用 View lifecycle scope）
- Test: 无（硬件依赖，见 Global Constraints）

**Interfaces:**
- Consumes: `androidx.fragment.app.Fragment` 既有成员 `isAdded: Boolean`、`getView(): View?`、`viewLifecycleOwner: LifecycleOwner`；`LifecycleOwner.lifecycleScope`；`delay`；`LogContext.log.w(tag, msg)`；私有成员 `logTag`。
- Produces: 无新公开符号。`bindCameraUseCases()` 签名不变（`private fun bindCameraUseCases()`）。

- [ ] **Step 1: 在 `bindCameraUseCases()` 入口加视图守卫**

在 `CameraFragment.kt:260` `private fun bindCameraUseCases() {` 之后、`showAvailableRatio(`（现 261 行）之前插入。守卫必须在最顶端——紧随其后的 `incPreviewGridBinding.viewFinder` / `cameraUiContainerTopBinding.btnRatio` 在 View 销毁后同样会抛。

```kotlin
    private fun bindCameraUseCases() {
        if (!isAdded || view == null) {
            LogContext.log.w(logTag, "bindCameraUseCases() skipped: view is not available")
            return
        }
        showAvailableRatio(
```

- [ ] **Step 2: 绑定宿主 `this` → `viewLifecycleOwner`**

`CameraFragment.kt:408-409`，`bindToLifecycle` 的第一个实参：

```kotlin
            camera = camProvider.bindToLifecycle(
                viewLifecycleOwner,
                hdrCameraSelector ?: lensFacing,
                preview,
                imageCapture,
                imageAnalyzer
            ).apply {
```

（仅把原 `this` 改为 `viewLifecycleOwner`，其余参数与 `.apply {}` 块不变。）

- [ ] **Step 3: 让相机切换后的延迟 UI 恢复随 View 销毁取消**

相机切换按钮点击时捕获当前 View owner，并替换原主线程 `Handler.postDelayed`：

```kotlin
val currentViewLifecycleOwner = viewLifecycleOwner
switchBtn.animate()
    .rotationBy(-180f)
    .setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            currentViewLifecycleOwner.lifecycleScope.launch {
                delay(500)
                enableUI(true)
            }
        }
    })
```

删除不再使用的 `Handler` / `Looper` import。`bindCameraUseCases()` 仍在点击回调中同步执行，不能把它
描述成 `onAnimationEnd` 的异步调用。

- [ ] **Step 4: 编译 camerax 模块**

Run: `./gradlew :camerax:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（无未解析引用；`viewLifecycleOwner` / `isAdded` / `view` 均为 Fragment 成员）。

- [ ] **Step 5: 静态检查**

Run: `./gradlew :camerax:detekt :camerax:ktlintCheck`
Expected: BUILD SUCCESSFUL，0 issue（守卫未引入新 import；`LogContext` 已在文件既有 import 中）。

- [ ] **Step 6: 提交（仅代码；维护者授权后执行）**

```bash
git add camerax/src/main/kotlin/com/leovp/camerax/fragments/CameraFragment.kt
git commit -m "fix(camerax): bind camera use cases to view lifecycle (CX-5)"
```

---

### Task 2: 记录 CHANGELOG 与整改进度

**Files:**
- Modify: `CHANGELOG.md:141`（`### 修复 (Fixed)` 段，CX-3 条目之后新增 CX-5 条目）
- Modify: `00-documents/2026-08-11-remediation-progress-and-review-zh.md`（§1 与相关处标记 CX-5 已解决）

**Interfaces:**
- Consumes: 无。
- Produces: 无代码符号；文档更新。

- [ ] **Step 1: 在 CHANGELOG `### 修复 (Fixed)` 段的 CX-3 条目（现 141 行）之后新增**

```markdown
- **CX-5 `CameraFragment` 相机绑定改用 `viewLifecycleOwner`**：`bindCameraUseCases()` 原将 use-case
  绑定到 Fragment（`this`），View 销毁但 Fragment 保留时相机仍绑定、持有旧预览 Surface；改为绑定
  `viewLifecycleOwner`（与 `VideoFragment` 对齐）并在函数入口加 `if (!isAdded || view == null) return`
  守卫；相机切换动画结束后的延迟 UI 恢复改由当前 View 的 `lifecycleScope` 执行，修复动画中途离页后
  访问已清空 binding 的崩溃，以及跨 View 重建的 Surface 泄漏/相机占用。
```

- [ ] **Step 2: 更新整改进度文档**

在 `00-documents/2026-08-11-remediation-progress-and-review-zh.md` 中，把 §1 累计说明里“`CX-5`/`LB-3` 决策项”一句改为“`LB-3` 决策项（`CX-5` 已于 2026-08-13 修复，见 CHANGELOG 与 `superpowers/specs/2026-08-13-cx5-camera-lifecycle-design.md`）”。

- [ ] **Step 3: 提交文档（含 spec 与本计划；维护者授权后执行）**

```bash
git add CHANGELOG.md \
  00-documents/2026-08-11-remediation-progress-and-review-zh.md \
  00-documents/superpowers/specs/2026-08-13-cx5-camera-lifecycle-design.md \
  00-documents/superpowers/plans/2026-08-13-cx5-camera-lifecycle.md
git commit -m "docs(camerax): record CX-5 camera lifecycle fix and design/plan"
```

---

### Task 3: 真机回归（与 R-5 共用同一次设备回归）

**Files:** 无（人工设备验证）。

**Interfaces:** 无。

- [ ] **Step 1: 构建 demo debug 包**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 按清单在真机核对 `CameraFragment`**

1. 旋转、切后台再返回 → 相机正常重绑、无泄漏、无黑屏；
2. 加入返回栈后返回 → View 重建后相机与手势/曝光控制正常；
3. 相机切换动画播放**中途按返回键退出页面** → 延迟 UI 恢复随 View 生命周期取消，不崩、无 binding 空指针异常；
4. `closeRatioAndSelect` 切换比例 → 正常重绑。

- [ ] **Step 3: 记录回归结论**

在整改进度文档「⚠️ 未验证事项」处补记 CX-5 真机回归结果（通过 / 问题 / 待执行）。在真机回归完成前，
必须明确写为“代码与构建验证完成，真机回归待执行”，不能将构建成功等同于真机通过。

---

## Self-Review

**Spec coverage:**
- spec §3.1（`this`→`viewLifecycleOwner`）→ Task 1 Step 2 ✓
- spec §3.2（入口守卫）→ Task 1 Step 1 ✓
- spec §3.3（延迟 UI 恢复绑定 View 生命周期）→ Task 1 Step 3 ✓
- spec §5（不做单测、真机回归清单）→ Task 1「无 Test」+ Task 3 ✓
- spec §6（CHANGELOG「修复」段、无 API 变更）→ Task 2 Step 1 ✓
- spec §4（不改 VideoFragment / 不加保活 / 不加临时日志）→ 计划未含相应任务，符合“不做的事” ✓

**Placeholder scan:** 无 TBD/TODO；守卫与 CHANGELOG 均为完整可粘贴代码；无“同 Task N”式省略。

**Type consistency:** `bindCameraUseCases()` 全程签名一致；守卫用的 `isAdded`/`view`/`viewLifecycleOwner`/`LogContext.log.w`/`logTag` 在 Task 1 Interfaces 已列明来源，且 CX-3（CHANGELOG:140）已在本模块使用同一 `if (!isAdded || view == null) return` 惯用法。
