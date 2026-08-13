# CX-5 设计 —— CameraFragment 相机绑定生命周期修正（2026-08-13）

> 关联：`00-documents/2026-08-04-eight-module-code-review-zh.md`（CX-5 决策项，第 246 行）、
> `00-documents/2026-08-11-remediation-progress-and-review-zh.md`（整改进度）。
> 分支：`fix/eight-module-remediation`。

## 1. 背景与问题

`camerax` 库内两个 Fragment 绑定相机 use-case 时，传入的 `LifecycleOwner` 不一致：

| 文件 | 位置 | 传入 owner | 语义 |
|------|------|-----------|------|
| `CameraFragment.kt` | `bindCameraUseCases()` `:409` | **`this`**（Fragment） | 绑定活到 Fragment DESTROYED |
| `VideoFragment.kt` | `:296` | `viewLifecycleOwner`（View） | 绑定在 `onDestroyView` 自动解绑 |

`this` 与 `viewLifecycleOwner` 的差异只在 **“Fragment 存活但 View 被销毁又重建”**（加入返回栈后返回、
`retainInstance` 配置变更）时暴露：

- `viewLifecycleOwner`：View 销毁 → 相机解绑、释放预览 Surface；View 重建 → 重绑。**安全默认**。
- `this`：View 销毁但相机仍绑定 → 继续持有旧 Surface、相机保持打开 → 可能泄漏 / 后台占用相机 /
  旧 Surface 失效时崩溃。

**内部矛盾（佐证 `this` 系误用）**：`CameraFragment` 的相机是 Fragment 作用域（`this`），但其 UI 控制
全是 View 作用域——`initCameraGesture(viewFinder, camera!!)`、曝光 slider 的 `addOnChangeListener`
（挂在 `cameraUiContainerTopBinding` 上）。View 重建后这些 View 级监听会重新添加，而 Fragment 级的旧
camera 仍绑着，可能出现监听挂到陈旧 camera / 重复绑定。

## 2. 决策（已与维护者确认）

1. **产品意图**：`CameraFragment` **没有**“相机需跨 View 销毁存活”的真实需求；`this` 判定为
   照抄旧 CameraX 官方样例导致的误用。
2. **Q-A（是否先补观察性日志）**：**直接改**。`VideoFragment` 已用 `viewLifecycleOwner` 且工作正常，
   等于已有活的参照，无需先加临时生命周期日志。
3. **Q-C（动画回调竞态）**：**加入口守卫**。

## 3. 变更方案

仅改 `CameraFragment.kt`，两处：

### 3.1 绑定宿主 `this` → `viewLifecycleOwner`

`bindCameraUseCases()` 内 `camProvider.bindToLifecycle(this, ...)`（`:408-409`）的第一个实参
`this` 改为 `viewLifecycleOwner`。相机随 View 生命周期绑定/解绑，与 `VideoFragment` 对齐；
View 重建时整体重绑，第 1 节的 UI 监听作用域矛盾自然消失。

### 3.2 入口守卫（覆盖动画回调竞态）

`bindCameraUseCases()` 有三个调用点：
- `:256` `onViewCreated` 内（view 必存在）
- `:726` 相机切换动画 `onAnimationEnd` 回调（**可能在 `onDestroyView` 之后触发**）
- `:865` `closeRatioAndSelect` 用户操作（view 存在）

`:726` 是异步回调：用户在切换动画播放中途离开页面时，回调可能在 View 销毁后触发。此时函数内部访问
`viewLifecycleOwner` 会抛 `IllegalStateException`。在**函数入口**统一加守卫（一处覆盖全部三个调用点）：

```kotlin
private fun bindCameraUseCases() {
    if (!isAdded || view == null) {
        LogContext.log.w(logTag, "bindCameraUseCases() skipped: view is not available")
        return
    }
    // ... 现有逻辑（内部改用 viewLifecycleOwner）
}
```

守卫用 `view == null` 判定（`onDestroyView` 后 `getView()` 返回 null，而 `viewLifecycleOwner`
在此状态下访问即抛异常）；`isAdded` 为额外保险。

## 4. 不做的事（YAGNI / 范围外）

- **不改 `VideoFragment`**：它已用 `viewLifecycleOwner`。其调用点是否也需同款入口守卫属独立一致性
  跟进项，不纳入 CX-5 范围（若后续统一，另开条目）。
- **不引入相机跨 View 存活的保活机制**：已确认无此需求。
- **不加临时观察性日志**（Q-A 已决策直接改）。

## 5. 测试与验证

- **单元测试**：不适用。`bindCameraUseCases()` 为 private，依赖真实 `ProcessCameraProvider` 与
  Fragment 生命周期；CameraX + 相机硬件不可在 JVM 单测中有意义地覆盖（与 `testing.md`“相机等硬件
  依赖模块不强求覆盖”一致）。
- **真机回归**（与 R-5 共用同一次设备回归，不单独阻塞代码改动）：
  1. 旋转、切后台再返回 → 相机正常重绑、无泄漏、无黑屏；
  2. 加入返回栈后返回 → View 重建后相机与手势/曝光控制正常；
  3. 相机切换动画播放中途按返回键退出页面 → 守卫命中、不崩、无异常日志；
  4. `closeRatioAndSelect` 切换比例 → 正常重绑。

## 6. 影响面

- **对外 API**：无签名变更（内部行为调整）。相机在 `onDestroyView` 解绑属行为收敛（更安全），
  记入 CHANGELOG「修复」段。
- **detekt/ktlint**：新增守卫不引入未用 import；`view`/`isAdded` 为 Fragment 既有成员。
