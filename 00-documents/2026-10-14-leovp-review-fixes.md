# Leovp 代码审查问题整改开发记录

## 1. 文档信息

- 日期：2026-10-14
- 分支：`review/native-modules`
- 整改基线：`fd3cd48b74c7eb6c02bf072335e5bae4f197ebbb`
- 对照材料：StarPay Pro 中的 Leovp 内嵌源码审查、整改计划和 FloatView 差异说明
- 实施原则：只处理能够在当前仓库源码中确认、且修改方向明确的问题；不提交、不推送，不改动条件不足的设计项

本次整改覆盖 `floatview`、`lib-common-android`、`lib-compose`、`lib-network`、`log`、`pref` 和
`androidbase`。修改包含生产代码、聚焦回归测试和测试依赖，没有修改版本号、发布配置或外部项目文件。

## 2. 问题与修改总览

| 编号 | 问题 | 主要修改 |
| --- | --- | --- |
| F1 | FloatView 在 `build()` 后、首次 `show()` 前访问属性可能读取未初始化的 `layoutParams` | 未挂载时写入并读取 `DefaultConfig`；只在窗口已加入后访问 `layoutParams` |
| F2 | Android 8.0 以下窗口类型通过按位或生成错误类型值 | 明确使用 `TYPE_PHONE` |
| F3 | 重复 tag 使用 `IllegalAccessError`，且展示失败只有二态结果 | 改为 `IllegalStateException`；新增三态 `FloatViewShowResult` |
| F4 | 展示失败后无法可靠决定是否删除 Manager 映射 | 依据窗口是否真实摘除返回三态；摘除失败时隐藏 view 并保留映射 |
| T1 | 自定义 Toast 展示失败后用户可能看不到提示 | FloatView 展示失败时回退系统 Toast；旧系统分支异常也统一回退并记录 |
| T2 | `cancelToast()` 宣称可跨线程调用，但直接操作主线程对象 | 非主线程调用统一投递到主线程，并清理延迟任务、Toast 和 FloatView |
| E1 | `result {}` 会把 JVM `Error` 包装成普通业务失败 | `CancellationException` 与 `Error` 都原样抛出 |
| E2 | 4xx 解码结果占用 `tag`，覆盖调用方业务标识 | `tag` 保持原值；新增独立只读 `errorData` |
| E3 | 服务端、序列化和通用 `ApiException` 的异常链与字段透传不一致 | 统一保留包装异常本身作为 `cause`，透传 `code`、`tag` |
| L1 | 低于日志门槛时仍会执行配置 lambda 和消息构造 | `AbsLog.isLoggable()` 在 lambda 求值前完成总开关与等级判断 |
| D1 | 防抖使用墙钟，系统校时会破坏间隔判断 | 改用 `SystemClock.elapsedRealtime()`，并为测试注入时钟 |
| D2 | 单调时钟从 0 开始时，首次点击会被误判为重复 | 用可空时间戳表示“尚未触发”，不再用 `0L` 兼作哨兵 |
| C1 | `DisposableEffect` 只以 owner 为 key，更换 ViewModel 后仍调用旧实例 | key 改为 `(lifecycleOwner, viewModel)` |
| G1 | `ForegroundComponent` 后台判断跨线程，且单例先发布后注册 | 延迟判断统一到主线程；注册成功后才发布实例；前台字段加可见性保证 |
| N1 | Wi-Fi 信号监听重复注册且缺少注销契约 | 只调用 `registerNetworkCallback()`；保存 callback 映射并提供配对注销 |
| N2 | Wi-Fi 回调线程写入、监控线程读取时缺少可见性保证 | `globalWifiSignal` 增加 `@Volatile` |
| P1 | 对象序列化失败返回空串，可能覆盖原有偏好值 | 仅在序列化结果非空时落盘 |

## 3. FloatView 状态机整改

### 3.1 `build()` 与 `show()` 之间的合法状态

`FloatViewCreator.build()` 的契约允许只创建 Manager 实例而不加入 `WindowManager`。此时
`FloatViewImpl.layoutParams` 尚未由 `init()` 初始化。旧代码的属性 setter/getter 在这个阶段直接访问
`layoutParams`，会触发 `UninitializedPropertyAccessException`。

修改后的规则如下：

```text
未加入窗口：DefaultConfig 是属性事实源
已加入窗口：先更新 DefaultConfig，再更新 WindowManager.LayoutParams
已释放：忽略后续属性修改
```

具体实现：

- `updateFloatViewGravity()` 先更新 `config.gravity`，只有 `windowAdded == true` 时才更新
  `layoutParams.gravity` 和调用 `updateViewLayout()`。
- `updateFloatViewPosition()` 先保存 `config.x/config.y`，未挂载时直接返回；已挂载时执行边界校正并同步
  `layoutParams` 与 `config`。
- `getFloatViewPosition()` 在未挂载时直接返回 `Point(config.x, config.y)`。
- `updateScreenOrientation()` 先更新方向配置和屏幕缓存，只有窗口已加入且未处于移除状态时才缩放
  `layoutParams.x/y`。
- `WindowManager.updateViewLayout()` 的异常记录到日志，避免窗口生命周期竞态直接击穿调用方。

这样可以安全支持以下调用顺序：

```kotlin
FloatView.with(context).layout(view).build()
FloatView.default().gravity = Gravity.TOP or Gravity.START
FloatView.default().position = Point(120, 160)
FloatView.default().show()
```

### 3.2 旧系统窗口类型

旧实现把 `TYPE_TOAST` 与 `TYPE_PHONE` 按位或。两者是枚举值而不是 flags，按位或会得到另一个窗口类型。
Android 8.0 以下分支现在明确选择 `WindowManager.LayoutParams.TYPE_PHONE`；Android 8.0 及以上继续使用
`TYPE_APPLICATION_OVERLAY`。

这项修改有 Robolectric API 25 回归测试，但系统悬浮窗权限和厂商 WindowManager 行为仍需真机验证。

### 3.3 重复 tag 的异常边界

重复 tag 是调用状态错误，不是 JVM 链接错误。`FloatViewManager.create()` 现在通过 `error()` 抛出
`IllegalStateException`，让上层只捕获可恢复的 `Exception`，不再依赖 `IllegalAccessError` 做业务控制流。

`FloatViewCreator.tryShow()` 使用显式 `try/catch (Exception)`，不会把 `AssertionError`、
`OutOfMemoryError` 或 `LinkageError` 降级成普通展示失败。

### 3.4 三态展示结果

新增公开枚举 `FloatViewShowResult`：

| 状态 | 窗口状态 | Manager 操作 | Toast 操作 |
| --- | --- | --- | --- |
| `SHOWN` | 自定义 view 已显示 | 保留映射 | 不回退 |
| `FAILED_AND_DETACHED` | 展示失败且确认已摘除 | 删除对应实例映射 | 回退系统 Toast |
| `FAILED_BUT_ATTACHED_HIDDEN` | 展示失败，摘除也失败，但 view 已隐藏 | 保留映射供后续重试 | 回退系统 Toast |

`FloatViewImpl.show()` 的关键失败流程为：

```text
捕获可恢复 Exception
  -> 尝试把 customView 设为 GONE
  -> remove(true)
  -> detachWindow()
  -> windowAdded=false：FAILED_AND_DETACHED
  -> windowAdded=true ：FAILED_BUT_ATTACHED_HIDDEN
```

`FloatViewManager.show(tag)` 只在 `FAILED_AND_DETACHED` 时用
`windowMap.remove(tag, instance)` 删除映射。带实例参数的删除可避免误删同 tag 下已经替换的新实例。

若重复展示前旧窗口摘除失败，代码先隐藏旧 view，再返回
`FAILED_BUT_ATTACHED_HIDDEN`。这样系统 Toast 可以安全回退，同时 Manager 仍保有下一次
`remove(true)` 所需的唯一引用。

监听器注销也增加独立异常边界，确保显示监听或方向监听注销失败时仍会继续处理其余清理步骤。

### 3.5 兼容性

- 原有 `FloatViewCreator.show(): Unit` 的 JVM 签名保持 `void show()`。
- `tryShow()` 和 `FloatViewShowResult` 是新增 API，不改变现有调用方源码。
- `FloatViewImpl` 与 `FloatViewManager` 的结果类型变化发生在模块内部。
- 重复 tag 的异常从 `Error` 改为 `IllegalStateException`，属于错误语义修正。

## 4. Toast 展示与取消

### 4.1 展示失败回退

Android 11 及以上的自定义 Toast 通过 `FloatViewCreator.tryShow()` 获取明确结果。结果不是 `SHOWN` 时，
立即调用系统 `Toast.makeText()`，且不再安排 FloatView 延迟移除任务。

旧系统自定义 Toast 的布局加载、装饰、窗口展示发生异常时，最外层异常边界会：

1. 使用 `android.util.Log` 记录原始异常；
2. 尝试显示系统 Toast；
3. 系统 Toast 自身也失败时记录第二个异常，不继续递归回退。

该模块没有为了日志新增额外依赖。

### 4.2 跨线程取消

`cancelToast()` 现在构造统一的主线程清理动作：

```text
removeCallbacksAndMessages(null)
  -> toast.cancel()
  -> toast = null
  -> FloatView.remove()
```

主线程调用时同步执行；其它线程调用时通过 `mainHandler.post()` 投递。非主线程路径是异步取消，调用函数
本身不会等待主线程完成。

## 5. 网络结果异常映射

### 5.1 保留取消和 JVM 致命错误语义

`generic.result()` 与 `net.result()` 的外层仍使用 `runCatching` 收集普通请求异常，但在映射前执行：

```kotlin
if (err is CancellationException) throw err
if (err is Error) throw err
```

因此协程取消会继续沿协程层级传播，`AssertionError` 等 JVM 严重错误也不会伪装成
`Result.Failure`。

### 5.2 分离业务 tag 与响应错误数据

旧 4xx 分支把响应体解码结果写入 `ResultResponseException.tag`，覆盖调用方在请求异常中保存的交易号、
请求标识等业务数据。

`ResultResponseException` 新增只读属性：

```kotlin
var errorData: Any? = null
    private set
```

内部通过 `@PublishedApi internal` 的 `attachErrorData()` 为公开内联函数赋值，并使用 `@JvmSynthetic`
避免 Java 调用方把该内部写入口当作普通公开 API。映射后的含义固定为：

- `tag`：调用方提供的业务标识；
- `errorData`：响应体按结果类型解码后的结构；
- `responseBodyString`：原始响应体文本；
- `cause`：原始请求异常包装对象。

### 5.3 异常链统一

- `ApiServerException` 映射为 `ResultServerException` 时，`cause` 保存 `ApiServerException` 本身，不跳过
  中间层直接保存它的 cause。
- `ApiSerializationException` 同样作为 `ResultConvertException.cause` 保留。
- 通用 `ApiException` 映射时透传 `code`、`message`、`tag`，并把异常本身作为 cause。

这使日志和崩溃上报可以同时看到网络层上下文与最底层根因。

## 6. 日志惰性求值

`AbsLog` 新增统一判断：

```kotlin
fun isLoggable(level: LogLevel): Boolean = enableLog && level >= logLevel
```

`v/d/i/w/e/userOp` 顶层扩展在创建 `LogConfig`、执行配置 lambda 和构造消息之前调用该方法。Release
日志门槛高于 DEBUG 时，JSON 序列化、字符串拼接和配置副作用都不会发生。

各实例日志方法也复用同一判断，避免扩展层与实际输出层的等级规则分叉。

## 7. 防抖时钟与首次事件

以下入口从 `System.currentTimeMillis()` 改为 `SystemClock.elapsedRealtime()`：

- `OnSingleClickListener`
- `DebounceHandler`
- `MultiDebounceHandler`
- `Modifier.debounceClickable()`

原因是防抖计算需要时间间隔，不应受网络校时、时区调整或用户手动修改系统时间影响。

非 Compose handler 通过 `internal` 构造器注入 `timeSource`，生产默认使用单调时钟。公开的
`DebounceHandler(Long)` 与 `DebounceHandler()` 由次构造器上的 `@JvmOverloads` 显式生成，既保留源码调用，
也保留已发布版本中的 JVM 描述符 `(J)V` 与 `()V`。测试可以传入确定性时钟。

时间戳改为可空值：`null` 表示从未触发。该设计修复了进程启动后
`elapsedRealtime() < debounceTime`，特别是测试时钟为 `0L` 时，首次事件被错误拒绝的问题。
Compose handler 与 modifier 继续保留原有的严格边界：只有经过时间大于防抖窗口才再次放行；
`OnSingleClickListener` 继续保留原有的“大于等于间隔即可放行”行为。

## 8. Compose 生命周期桥接

`LifecycleViewModelBridge()` 原来使用：

```kotlin
DisposableEffect(lifecycleOwner)
```

现在改为：

```kotlin
DisposableEffect(lifecycleOwner, viewModel)
```

当组合位置不变但 ViewModel 实例变化时，Compose 会移除旧 observer，并为新 ViewModel 注册 observer，
防止生命周期事件继续发送给已经替换的实例。

## 9. ForegroundComponent 并发与发布

旧实现使用单独 `HandlerThread` 延迟判断后台状态，但 Activity 生命周期回调在主线程修改 `paused` 和
`isForeground`。这会形成跨线程可见性问题，也允许旧后台任务与新的 resume 事件竞争。

修改后：

- Handler 绑定 `Looper.getMainLooper()`，状态转换与生命周期回调串行执行；
- `isForeground` 增加 `@Volatile`，保证直接从其它线程读取时可见；
- listener 使用 `CopyOnWriteArrayList`，回调异常逐个记录，不影响其它 listener；
- `init()` 先创建实例并完成 `registerActivityLifecycleCallbacks()`，注册成功后才写入 volatile 单例；
- 注册过程抛异常时不会暴露一个未注册完成的半初始化单例。

## 10. Wi-Fi 网络回调生命周期

旧 Android 12+ 路径对同一个 `NetworkCallback` 同时调用 `registerNetworkCallback()` 和
`requestNetwork()`，会建立重复请求，且没有对应注销入口。

修改后的所有权流程：

```text
调用方 lambda
  -> IdentityHashMap 按对象身份保存 framework NetworkCallback
  -> registerNetworkCallback() 一次
  -> 使用同一个 lambda 调用 unregister
  -> 从 Map 移除并调用 unregisterNetworkCallback()
```

选择 `IdentityHashMap` 是为了落实“同一个 callback 实例”的注销契约，不依赖函数对象可能变化的
`equals()` 实现。注册同一实例前会先注销旧的 framework callback，避免重复监听。

删除 `requestNetwork()` 后，该功能只需要 `ACCESS_NETWORK_STATE`；KDoc 和 `@RequiresPermission` 同步
移除了不再需要的 `CHANGE_NETWORK_STATE`。

`NetworkMonitor` 持有稳定的 `wifiSignalCallback` 字段，在 `stopMonitor()` 中配对注销。框架回调线程写入、
监控 HandlerThread 读取的 `globalWifiSignal` 使用 `@Volatile` 保证可见性。注销异常时 callback 映射保留，
允许调用方重试；`stopMonitor()` 使用 `try/finally`，确保系统注销失败也不会阻止本地 HandlerThread 和缓存
状态清理。

## 11. 偏好对象序列化失败保护

`toJsonString()` 的既有契约是序列化异常时记录日志并返回空串。旧 `AbsPref.put()` 会把这个空串继续写入，
覆盖 key 中原有的有效内容。

Kotlin `put<T>()` 和 Java `put4Java()` 的对象分支现在都先保存序列化结果，仅当结果非空时调用底层
`put(key, json)`。基础类型、字符串和 Set 的既有行为不变。

## 12. 测试覆盖

新增或扩展的聚焦测试包括：

- FloatView：`build()` 后属性访问、三态展示、致命 Error 透传、摘除失败时隐藏并保留映射、重复展示、
  重复 tag、API 25 窗口类型。
- Toast：FloatView 附加失败时系统 Toast 回退、后台线程取消转主线程。
- ForegroundComponent：后台 listener 在主线程执行、生命周期注册完成前单例不可见。
- Network：只注册一次、不调用 `requestNetwork()`、能够配对注销。
- ResultExt：取消透传、Error 透传、通用 ApiException 字段、服务端 cause、`tag/errorData` 分离。
- Log：关闭等级时配置与消息 lambda 均不执行；开启等级时消息正常输出。
- Debounce：时钟为 0 的首次事件、间隔边界、多 key 独立时间戳。
- Compose 生命周期：更换 ViewModel 后旧 observer 被移除，新实例接收事件。
- Pref：对象序列化失败不会覆盖原值。

开发过程按红绿循环先观察到新增测试因缺少 API 或旧行为而失败，再实现生产代码；第一轮转绿后又通过
自审补出了“时钟为 0 的首次事件”和“Error 不得降级”两个失败用例，修正后重新转绿。第二轮自审进一步
把 FloatView 展示结果从二态收敛为三态，并覆盖摘除失败状态。

## 13. 验证结果

### 13.1 单元测试

执行：

```bash
./gradlew \
  :floatview:testDebugUnitTest \
  :lib-common-android:testDebugUnitTest \
  :lib-compose:testDebugUnitTest \
  :lib-network:testDebugUnitTest \
  :log:testDebugUnitTest \
  :pref:testDebugUnitTest \
  :androidbase:testDebugUnitTest \
  --rerun-tasks
```

结果：`BUILD SUCCESSFUL`，302 个任务全部实际执行。

最后一次 `ResultResponseException` API 可见性收敛后，又单独执行
`:lib-network:testDebugUnitTest --rerun-tasks`，49 个任务全部实际执行并通过。

### 13.2 格式与静态分析

以下模块的 `ktlintCheck` 与 `detekt` 已通过：

- `floatview`
- `lib-common-android`
- `lib-compose`
- `log`
- `pref`
- `androidbase`

`lib-network` 的模块级 `ktlintCheck` 和 `detekt` 都只报告
`HttpLoggingInterceptor.kt:217` 的同一处 100 字符超长行。该行在整改基线 HEAD 中已经存在，本次没有修改
该文件；本次修改的 `lib-network` 源码和测试没有新增报告。本记录不把存量问题写成“本次检查通过”，也不
越过整改范围顺手修改它。

### 13.3 Demo 集成编译

执行：

```bash
./gradlew :demo:assembleDevDebug --rerun-tasks
```

结果：`BUILD SUCCESSFUL`，600 个任务全部实际执行。构建中仍有仓库既有的 Android API 弃用警告、音频
弃用 API 警告、CMake SDK XML 版本警告和 Gradle 10 兼容性提示，没有新增编译错误。

### 13.4 其它检查

- `git diff --check`：通过。
- JVM 签名核对：原 `FloatViewCreator.show(): void` 保持不变；`DebounceHandler` 的 `(J)V` 与 `()V`
  均明确保留；
  `ResultResponseException` 原构造器保持存在。
- 分支相对上游：整改开始及文档编写前均为 ahead 0 / behind 0；本次未提交、未推送。

## 14. 真机验证边界

本次没有执行真机测试。以下行为不能由 Robolectric 或 JVM 测试完全证明：

1. API 21～25 上持有悬浮窗权限时 `TYPE_PHONE` 的真实加入、旋转与移除行为；
2. Android 10 及以下自定义 Toast 的布局异常回退；
3. Android 11 及以上前台、后台、无悬浮窗权限三种 Toast 路径；
4. WindowManager 在 Activity 销毁、重复进入退出和快速 show/remove 下的厂商差异；
5. Android 12+ Wi-Fi callback 在网络切换、重复 start/stop 和长时间运行时是否持续配对；
6. Compose 页面切换 owner/ViewModel 后的实际 UI 生命周期回调。

建议至少使用一台 API 21～26 设备和一台较新 Android 设备覆盖上述场景，并检查窗口泄漏、重复 Toast、
回调泄漏和线程异常日志。

## 15. 未纳入本次整改的事项

此前审查中需要产品语义、兼容策略或更大范围重构才能决定的问题仍未修改，包括文件路径穿越策略、签名
读取失败契约、FloatView `touchable` 动态更新方式、默认日志策略、重复 Compose/MVVM 实现归并、
`UiEventManager.trySend()` 失败策略等。这些不能因为本次相关模块已经构建和测试通过而视为已解决。

## 16. 2026-10-15 二次审查反馈修复

开发 AI 在首轮整改后指出 5 个问题。源码、已发布标签 `5.15.8` 的实现和当前字节码逐项核对后，5 条均成立；
其中前 4 条需要修改代码，第 5 条需要补发布迁移说明。本节记录根因、代码级修改和回归验证方法。

### 16.1 恢复 `DebounceHandler()V` JVM ABI

首轮为了给测试注入单调时钟，将带默认参数的主构造器改成了 `internal` 主构造器和公开次构造器：

```kotlin
constructor(defaultDebounceTime: Long = 1000L) :
    this(defaultDebounceTime, SystemClock::elapsedRealtime)
```

Kotlin 默认参数会生成合成的默认参数入口，但次构造器不会因此自动生成 Java 可直接调用的 `()V`。因此
Kotlin 源码中的 `DebounceHandler()` 仍能编译，旧 Java 或已经编译的消费端却会在运行时找不到
`DebounceHandler.<init>()V`，形成 `NoSuchMethodError`。

修复是在公开次构造器上增加 `@JvmOverloads`。这样同时生成：

- `DebounceHandler(long)`，保持显式防抖时间入口；
- `DebounceHandler()`，恢复 `5.15.8` 已发布的无参 JVM 构造器；
- Kotlin 默认参数所需的合成构造器。

回归测试不只做 Kotlin 源码调用，而是使用 `DebounceHandler::class.java.getConstructor()` 获取并实例化真正的
无参 JVM 构造器；最终还要用 `javap -p` 直接核对描述符。

### 16.2 保留旧 FloatView 的真实挂载状态

旧自定义 Toast 立即移除失败时，`FloatViewImpl.detachWindow()` 会保留 `windowAdded = true` 和 manager
映射，便于之后重试。这时用相同 tag 创建新 FloatView 会在 `build()` 阶段抛出重复 tag 异常。首轮
`tryShow()` 将所有 `Exception` 固定转换为 `FAILED_AND_DETACHED`，造成“旧窗口仍挂载”却报告“已摘除”。

修改后的状态链如下：

1. `finishRemoval()` 在 framework 摘除失败时立即隐藏旧 view，同时保留 `windowAdded`、`removing` 和
   manager 映射以便重试；
2. `FloatViewCreator.tryShow()` 捕获可恢复的创建异常后查询同 tag 旧实例的状态；
3. 旧实例确实发生摘除失败且仍挂载时返回 `FAILED_BUT_ATTACHED_HIDDEN`，健康旧实例则保持可见，并为新实例
   返回 `FAILED_AND_DETACHED`；
4. 查询过程不再修改健康旧窗口，`Error` 也仍然向上传播。

回归测试模拟 `removeViewImmediate()` 抛出异常，确认第二次 `tryShow()` 返回
`FAILED_BUT_ATTACHED_HIDDEN`、manager 仍持有旧实例、旧 window 仍在模拟窗口集合中且 view 已变为
`GONE`。测试使用 `finally` 恢复 mock 并完成移除，避免失败断言污染其它生命周期用例。

### 16.3 注销失败不再破坏 Activity 销毁，并保留句柄供重试

`BaseActivity.onDestroy()` 会直接调用 `NetworkMonitor.stopMonitor()`。首轮虽然用 `finally` 保证 monitor
线程和局部状态释放，但 `ConnectivityManager.unregisterNetworkCallback()` 的 `Exception` 仍会穿透，可能
阻止 `super.onDestroy()`；同时 `NetworkUtil` 的全局 `IdentityHashMap` 会继续持有 callback，而 callback
又捕获 `NetworkMonitor` 与 Activity context，形成进程级引用链。

修复在真正拥有映射的 `NetworkUtil` 完成：

- 只有 framework 注销成功后才从 `wifiNetworkCallbacks` 移除映射；
- 捕获并记录普通 `Exception`，teardown 不再因为平台状态异常而中止；
- 注销失败时保留映射供显式注销或下次注册重试；重复注册时若旧 callback 无法注销，不再注册第二个
  framework callback；
- 不捕获 `Error`，避免把 VM/链接等致命故障伪装成可恢复异常。

回归测试让 framework 注销抛出 `IllegalStateException`，确认调用方不再收到异常；恢复 framework 后再次注销，
确认会进行第二次调用并成功清除映射。另一个用例确认旧 callback 注销失败时不会注册第二个 callback。执行该
测试时还发现 `lib-common-android` 虽有 JUnit 4/
Robolectric 用例，却未声明 Vintage runtime，导致这些测试此前在 JUnit Platform 下不可发现；模块现已增加
`testRuntimeOnly(libs.bundles.test.runtime.only)`，并使用本机已有的 Robolectric API 33 运行环境。

### 16.4 错误体读取与解码不再吞掉 `Error`

两个 `result()` 的外层已显式重抛 `Error`，但 4xx 分支内部仍使用 `runCatching`。`runCatching` 捕获的是
所有 `Throwable`，因此响应体读取或 Kotlin Serialization 解码期间的 `OutOfMemoryError`、`LinkageError`
等仍会被降级成 `null`。

新增内部共享边界 `payloadOperationOrNull()`，并让 generic 与 Net 两套实现的 body 读取、4xx 错误体解码、
`ConvertException` body 读取都经过该边界。它只捕获并记录 `Exception`，显式重抛
`CancellationException`，且完全不捕获 `Error`。这样普通格式错误仍按原契约得到 `errorData = null`，取消与
致命错误则保留结构化并发和 JVM 语义。

回归测试分两层：共享边界注入会抛 `AssertionError` 的解码动作；Net 版本使用真实自定义 `ResponseBody`，
在 `source()` 抛 `AssertionError`，从完整 `result()` 路径确认 body 读取阶段也不会吞掉致命错误。

### 16.5 `tag` 到 `errorData` 的迁移说明

4xx 的 `ResultResponseException.tag` 从“解码后的错误体”恢复为调用方请求 tag，错误体迁移到新增的
`errorData`。该变化源码兼容但行为不兼容：旧代码可能继续编译，却在运行时读到含义或类型不同的数据。

`CHANGELOG.md` 的 `Unreleased / 变更` 现已明确记录：

- 旧代码若把 `tag` 当成错误体，必须改读 `errorData`；
- 请求上下文继续从 `tag` 获取；
- 错误体读取/解码对 `Exception`、取消和 `Error` 的新边界。

### 16.6 二次审查测试闭环

每个代码问题均先新增失败测试并在旧实现上观察到预期失败：

- 无参构造器：`NoSuchMethodException`；
- FloatView：实际仍挂载却返回 `FAILED_AND_DETACHED`；
- 网络 callback：注销异常直接穿透测试；
- payload：解码或 body 读取阶段的 `AssertionError` 被降级。

修复后的窄范围测试已经分别转绿。完整模块测试、质量检查、Demo 集成编译、JVM 描述符和
`git diff --check` 的最终结果在本轮全部复验完成后补入本节，不能沿用首轮的历史结果代替。

### 16.7 真机证据边界

本节修改发生在此前 SUNMI P3H（Android 11）验证之后；当前尚未把包含本节修改的新 APK 重新安装到设备。
因此此前设备结果只能作为旧构建基线，不能证明本节的 FloatView 或 teardown 修复已经通过真机验证。

## 17. 2026-10-16 全量复审追加修复

本轮重新审查全部待提交差异，并对二次审查后的代码再次执行失败复现与回归。新增修复如下：

- 健康的同 tag FloatView 不再因新的 `tryShow()` 失败而被隐藏；只有旧实例确实处于“移除失败且仍挂载”
  状态时才返回 `FAILED_BUT_ATTACHED_HIDDEN`。
- Wi-Fi framework callback 注销失败时不再丢失映射，后续可以重试；替换注册无法先注销旧 callback 时，
  不会建立第二个监听。
- Toast 的容错边界只捕获 `Exception`，`AssertionError` 等 `Error` 不再降级为系统 Toast；相应 Robolectric
  用例使用真实窗口指标与可视 display，确保确实执行到 `WindowManager.addView()`。
- `NetworkMonitor` 每次启动创建独立调度会话，旧 generation 在采样前、业务 callback 前和再次调度前均会
  失效；因此 stop 后立即重启或运行中重复 start 都不会让旧任务发送过期结果或形成第二条采样循环。单次采样或
  业务 callback 抛出普通异常时记录日志并继续下一轮，`Error` 不会被 `runCatching` 吞掉。
- FloatView 使用独立状态记录真实的窗口摘除失败；正常退场动画尚未结束时，重复 `tryShow()` 不再误报
  `FAILED_BUT_ATTACHED_HIDDEN`。
- Compose 生命周期测试改用官方 `createComposeRule()` 驱动真实重组，并补齐 Compose 测试依赖与 Vintage
  runtime，避免“测试类存在但没有被执行”。
- AndroidBase 启用 Vintage 后暴露出旧测试同时使用 JUnit 5 `@Test` 与无实际用途的 JUnit 4 PowerMock runner；
  已移除无效 runner、`@PrepareForTest` 和 PowerMock 依赖，测试只由 Jupiter 执行，Robolectric 生命周期用例
  由 Vintage 执行。
- 修复 `HttpLoggingInterceptor` 的既有超长行，`lib-network` 模块级质量门禁不再被存量格式问题阻断。

本轮新增覆盖包括健康重复 FloatView、动画移除期间重复展示、注销失败重试、替换注册失败、Toast 致命错误
透传、`NetworkMonitor` 重启、运行中重复 start、旧代任务隔离与可恢复 callback 异常、Compose ViewModel
替换。最终复验结果如下：

- 七个受影响模块的 `testDebugUnitTest --rerun-tasks` 串行执行成功，共执行 302 个 Gradle 任务；
- 七个受影响模块的 `ktlintCheck` 与 `detekt --rerun-tasks` 串行执行成功，共执行 56 个 Gradle 任务；
- `:demo:assembleDevDebug --rerun-tasks` 成功，共执行 600 个 Gradle 任务；
- `git diff --check` 通过。

本轮没有执行真机测试，以上 JVM/Robolectric、静态检查和构建结果不替代真实窗口、网络与 Toast 行为的设备
验证。

## 18. 2026-10-19 中断后复验

继续处理 NetworkMonitor 的剩余并发问题：最终状态检查、流量统计更新和业务回调在同一生命周期锁内执行；
低版本 Wi-Fi 查询只使用本轮局部结果；启动失败统一回滚并释放调度器。普通异常后继续调度，致命错误直接
传播且不再安排下一轮。公开 KDoc 说明回调线程及停止时等待已有回调完成的约束；同时补齐 `errorData` 迁移说明。

最终以单个串行 Gradle 调用验证当前工作树，参数包含 `--rerun-tasks -Drobolectric.offline=true --max-workers=1`：

- androidbase、floatview、lib-common-android、lib-compose、lib-network、log、pref 的 `testDebugUnitTest`：
  共 120 条测试，失败、错误、跳过均为 0，其中 NetworkMonitor 生命周期回归 8 条。
- 上述七模块的 `ktlintCheck` 和 `detekt` 全部通过。
- `:demo:assembleDevDebug` 通过；整个调用实际执行 783 个 Gradle 任务。
- `git diff --check` 通过；未提交、推送或执行真机测试。

最后一轮静态复审未发现新的可操作问题。设备端窗口行为、网络变化与前后台切换仍需真机验证。
