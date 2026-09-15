# FloatView 窗口释放问题与修复

## 1. 问题原因

审查范围为 `floatview` 模块，重点是 `FloatViewImpl`、管理器及 Demo 的销毁调用。

1. `addView()` 注册成功与 View 首次 attach 不是同一时刻。原实现用
   `view.windowToken != null` 判断是否移除，导致同一个主线程回调中先显示再移除时，
   已注册但尚未 attach 的窗口被跳过。管理器随后删除 tag，窗口失去库侧清理入口。
2. 开启透明度动画后，默认移除要等待 500 ms 退场动画完成。若在 Activity 销毁时调用，
   框架可能先发现尚未移除的应用窗口，记录 `WindowLeaked`。动画默认关闭，不能据此
   判定所有普通页面退出都会触发。此日志也不等同于进程必然崩溃或窗口永久残留。
3. 单例管理器持有实现、Context 和 View，却未自动绑定 Activity 销毁。遗漏显式移除时，
   即使框架清除了窗口，单例和监听器仍可能保留 Activity。
4. 显示流程在成功添加窗口后还有显示、动画和位置更新；后续步骤失败时原实现仅注销
   监听器，没有回滚窗口。退场动画结束前又提前删除了管理记录，生命周期无法接管。

## 2. 代码修改

### 2.1 窗口所有权与状态

- `FloatViewImpl.windowAdded` 仅在 `addView()` 成功返回后置位；移除按该状态执行，
  不再以 `windowToken` 或 `isAttachedToWindow` 作为是否已注册的依据。
- `removing` 阻止退场期间重新显示、拖动或更新布局；`released` 保证终结只执行一次。
- 显示失败调用立即移除，回滚已经取得的窗口和监听器资源。
- 框架已移除窗口导致的 `IllegalArgumentException` 视为已经脱离管理；其他移除异常
  记录日志并保留实例及所有权，调用方可用 `remove(true)` 重试，避免静默丢失清理入口。
- `FloatViewManager` 仅在实例完成移除后删除相应 tag，并检查实例身份。

### 2.2 动画、任务及引用清理

- 保存透明度动画和吸边动画；替换或释放前先解绑回调，再取消动画。
- 退场只由结束回调完成移除，避免取消与结束双回调重复执行终结逻辑。
- `remove(true)` 可接管正在执行的退场；动画无法启动时回退到立即移除。
- 取消方向处理任务、注销显示和传感器监听；迟到的方向回调检查窗口状态。
- 终结时清除库安装的触摸监听器、配置中的触摸回调和 View 引用。

### 2.3 Activity 生命周期与线程契约

- 新增 `FloatViewOwner`，通过系统 `ActivityLifecycleCallbacks` 绑定 Activity，支持
  `ContextWrapper` 包装；销毁时立即移除窗口，也清理只 build、未 show 的实例。
- Activity 创建的系统悬浮窗同样随 Activity 销毁，以免其 Context、View 和回调遗留。
- 拒绝为已经 finishing/destroyed 的 Activity 创建实例；显示前再次检查所有者状态。
- 窗口操作限定主线程，增加公开 API 注解及关键执行入口检查。
- `FloatViewActivity` 和 `ScreenShareMasterActivity` 在销毁时显式立即移除。

## 3. 调用方需要注意的行为变化

- 退场期间 `exist()` 仍为 true，tag 仍被占用；如需立即重建同名窗口，先调用
  `FloatView.with(tag).remove(immediately = true)`。
- Activity Context（包括包装 Context）创建的窗口不再跨越该 Activity 的销毁。
  需要跨页面保留的系统悬浮窗应使用 Service 等长生命周期所有者创建，View 和回调也
  不得保留旧 Activity，且必须在 Service 结束时显式移除。仅换 Context 无法消除调用方
  自身的 View/闭包引用泄漏。
- `invisible()` 仅隐藏窗口，仍不等同于释放；需要结束使用时调用 `remove()`。
- 不新增高于 API 21 才可用的生产代码入口。真机测试中读取全局窗口列表的
  `WindowInspector` 使用 API 29 条件保护，只存在于仪器测试。

## 4. 验证记录

### 4.1 单元测试及构建

- 修改前 5 条回归测试均因预期断言失败，确认能够检出原问题。
- 修复后 `FloatViewLifecycleTest` 共 9 条通过：首次 attach 前移除、重复显示、Activity
  销毁、动画退场接管、显示异常回滚、移除异常重试、旧动画回调与 View 复用、仅 build
  未 show 的销毁、Activity 所有的系统悬浮窗销毁。
- 以下命令使用 JDK 17 和 `--rerun-tasks` 实际执行并通过：

```bash
./gradlew :floatview:testDebugUnitTest :floatview:ktlintCheck :floatview:detekt \
  :demo:ktlintCheck :demo:detekt :demo:assembleDevDebug --rerun-tasks
```

### 4.2 真机回归

设备：P3H（Android 11 / API 30），Demo 系统悬浮窗权限已授权。
仪器测试 `FloatViewWindowAndroidTest` 实际执行 **3 条全部通过，0 跳过**：

| 场景 | 实测内容与结果 |
| --- | --- |
| 首次 attach 前移除 | 同一主线程回调内连续 20 次显示并立即移除；确认显示后 token 仍为空，移除后 parent、全局窗口列表及 tag 均无残留 |
| 退场动画期间销毁 | 先确认 View 已 attach，再启动退场；确认 tag 尚在，随后销毁页面，窗口和 tag 均释放 |
| 反复进入退出页面 | 连续 5 次进入 FloatView Demo 并退出，检查所有已创建悬浮窗及管理表均清理 |

检查这三条测试采集的 logcat，未发现 `WindowLeaked`、`has leaked window`、
`FATAL EXCEPTION`、窗口移除失败或 `show()` 失败日志。

```bash
ANDROID_SERIAL=P321D54210063 ./gradlew :demo:ktlintCheck :demo:detekt \
  :demo:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.leovp.demo.FloatViewWindowAndroidTest
```

测试报告位于 `demo/build/reports/androidTests/connected/debug/flavors/dev/`。
本次修改后的仪器测试实际重新执行；其依赖沿用上一轮已经强制重建通过的 APK 构建结果。

测试环境曾暴露既有配置问题：Demo 的设备测试依赖中含 Robolectric，导致
`ActivityScenario` 和 `MonitoringInstrumentation.startActivitySync()` 加载
`RobolectricThreadChecker`，在设备上因未初始化 JVM 沙箱抛出 NPE，尚未运行到窗口操作。
本测试改用真实 Context 启动 Activity，借助生命周期回调和有上限的等待协调启动/销毁，
避免这些入口；没有修改生产应用的依赖配置。随后三条窗口测试均正常完成。

### 4.3 验证边界

Robolectric 与模拟窗口管理器验证了异常注入和生命周期分支，不能代替所有系统版本的
真实窗口行为。API 21～26 和较新 Android 设备仍需复测；长期 Service 系统悬浮窗的
前后台、旋转、权限撤销与长时间运行也需要单独验证。
