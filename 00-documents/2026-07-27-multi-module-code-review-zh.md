# 多模块代码审查报告

- **日期**：2026-07-27
- **分支**：`master`（安全审查分支已合并）
- **审查范围**：`draw-on-screen`、`lib-common-android`、`lib-compose`、`lib-common-kotlin`、`lib-compress`、`lib-json`、`lib-network`（约 9,600 行 Kotlin）
- **方式**：5 个并行审查代理（kotlin-reviewer × 4 + security-reviewer × 1），交叉重叠项已去重，多个 CRITICAL 由两个代理独立印证。
- **严重度定义**：CRITICAL = 可被利用的安全漏洞 / 数据丢失（阻断发布）；HIGH = 明确的 bug 或质量问题（合并前应修复）；MEDIUM = 可维护性问题（建议修复）；LOW = 风格 / 小建议（可选）。

> 说明：本文档只记录问题，不含代码改动。修复应遵循项目 TDD 流程（先补失败测试，再修复）。
>
> **范围补充**：C3 的"下游默认启用"证据涉及 `http` 模块（`BaseHttpRequest.kt`），该模块不在本次审查范围内，故 C3 按**跨模块问题**记录。基线：master HEAD `474b031ad`。
>
> **整改状态（2026-08-03）**：本报告为问题快照，内容保持不变。整改按 ID 逐项落地在实现文档
> [`2026-07-27-multi-module-remediation-plan-zh.md`](./2026-07-27-multi-module-remediation-plan-zh.md)（Phase 1–5）。
> 当前 **56/59 项已实施并提交**于 `fix/multi-module-remediation` 分支；3 项经决策延后（M-C1 / M-D4 / L-A4，
> 见实现文档 §0.5）。逐项进度与提交划分见实现文档「追踪清单 / 整改进度小结」。

---

## 严重度概览

| 模块 | CRITICAL | HIGH | MEDIUM | LOW |
|------|:--:|:--:|:--:|:--:|
| lib-common-android | 2 | 5 | 4 | 4 |
| lib-network | 1 | 1 | 4 | 0 |
| draw-on-screen | 0 | 3 | 6 | 2 |
| lib-compose | 0 | 4 | 4 | 2 |
| lib-common-kotlin | 0 | 3 | 4 | 3 |
| lib-json | 0 | 2 | 2 | 0 |
| lib-compress | 0 | 1 | 1 | 1 |
| **合计** | **3** | **19** | **25** | **12** |

> 注：C3 计入 `lib-network`，但其影响链涉及 `http` 模块（`BaseHttpRequest.kt` 默认接入 `doNotVerifier`）。

---

## 🔴 CRITICAL（阻断发布，必须修复）

### C1. 路径穿越 → 当前 App（same UID）范围内的文件读 / 写
**文件**：`lib-common-android/src/main/kotlin/com/leovp/android/utils/FileDocumentUtil.kt`
**印证**：kotlin-reviewer + security-reviewer 两个代理独立发现

> **影响边界澄清**：Android 应用沙箱下，攻击通常**无法**跨越到其它 App 的 `/data/data/<other-pkg>/`；实际可达范围是**当前 App 进程 / same UID 有权访问的文件**（自身私有目录、外部存储等）。在此边界内问题依然严重（可覆盖 App 自身数据库 / SharedPrefs）。

这是一个被多方消费的库，所有 `Uri`（经 `ACTION_SEND`/`ACTION_VIEW`/文档选择器/深链传入）都应视为不可信输入。该文件存在两个方向的穿越向量：

- **读向量** —— `getPathFromExtSD`（183-211，由 `getFileRealPath` 58-61 调用）：
  当 `isExternalStorageDocument(uri)` 命中（仅比较 `uri.authority == "com.android.externalstorage.documents"`，无 provider 往返 / 权限校验，可被 `Uri.parse()` 伪造）时，把 docId 按 `:` 拆分后第二段直接拼到外部存储根：
  ```kotlin
  fullPath = Environment.getExternalStorageDirectory().toString() + relativePath
  ```
  未过滤 `..`。传入形如 `content://com.android.externalstorage.documents/document/primary:../../../../<path>` 的 docId 可解析到外部存储根之外，读取当前 App（same UID）有权访问的文件。

- **写向量** —— `getDriveFilePath`（213-246）/ `copyFileToInternalStorage`（253-300）：
  用未净化的 `DISPLAY_NAME` 构造目标路径并写入：
  ```kotlin
  val name = cursor.getString(nameIndex)
  val file = File(context.cacheDir, name)          // getDriveFilePath
  // 或
  File(context.filesDir.toString() + "/" + newDirName + "/" + name)  // copyFileToInternalStorage
  FileOutputStream(file).use { ... }
  ```
  恶意 provider 把 `DISPLAY_NAME` 设为 `"../../../../databases/important.db"`，`File(base, name)` 与字符串拼接都承认 `..`，写入落到 `cacheDir`/`filesDir` 之外 —— 可覆盖应用自身的 SQLite 数据库、SharedPrefs XML 等（CWE-22，Uri/DisplayName 版 Zip Slip）。

**修复建议**：
1. 只取文件名段：`File(name).name`；拒绝含 `..`、`/`、`\` 的名字。
2. I/O 前对最终路径 `canonicalPath` / `canonicalFile` 做规范化，并校验仍是目标根目录的子路径。
3. 不要把 `uri.authority` 字符串相等当作鉴权手段。

---

### C2. Shell 命令注入（经 `forceStop` / `uninstallApk` 拼接外部参数）
**文件**：`lib-common-android/src/main/kotlin/com/leovp/android/utils/shell/ShellUtil.kt`
**印证**：kotlin-reviewer + security-reviewer 两个代理独立发现

> **定位澄清**：`execCmd(String)`（41-96）本身是**原始 shell API** —— 启动真实交互式 shell（`ProcessBuilder("sh")` 或 `"su"`）并把命令字符串写入其 stdin（`osw.write(command)`），命令由 shell 解释，`;`、`&&`、`|`、反引号、`$()`、`>` 等元字符全部生效。它执行任意 shell 是**设计如此**，不应被当作"命令注入 bug"整体重写；真正的缺陷是**上层 wrapper 把不可信参数裸拼进 shell 字符串**。

存在缺陷的是两个用裸字符串拼接调用方参数的公开 wrapper：
```kotlin
fun forceStop(pkgName: String) { execCmd("am force-stop $pkgName", true) }
fun uninstallApk(pkgName: String) { execCmd("pm uninstall $pkgName", true) }
```
若 `pkgName` 来自外部输入（Intent extra / 深链 / IPC / 服务端响应），`"com.foo; rm -rf /sdcard; #"` 可执行任意附加命令；`uninstallApk` 走 `su` → 在已 root 设备上等于**完整 root 级命令注入**。

**修复建议**（聚焦进入 shell 的不可信参数，而非 `execCmd` 本身）：
1. 在 `forceStop`/`uninstallApk` 等 wrapper 内对包名按 Android 包名文法严格白名单校验：`^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$`，非法即拒绝。
2. 优先改用参数数组、不经 shell：`ProcessBuilder(listOf("pm", "uninstall", pkgName))`。
3. `execCmd(String)` 作为底层 API 保留，但应在 KDoc 明确"调用方须保证命令字符串可信 / 已校验"。

---

### C3. TLS 主机名校验被彻底关闭，且被下游默认启用
**文件**：`lib-network/src/main/kotlin/com/leovp/network/SslUtils.kt:67-69`（KDoc 25-53 亦推荐此用法）
**印证**：lib-network kotlin-reviewer + security-reviewer 两个代理独立发现

```kotlin
val doNotVerifier = HostnameVerifier { _, _ -> true }   // 接受任意域名证书
```
该 trust-all 主机名校验器作为公开 API 提供，且 KDoc 把 `builder.hostnameVerifier(SslUtils.doNotVerifier)` 当作推荐接入方式。更严重的是，兄弟模块 `http/src/main/kotlin/com/leovp/http/retrofit/base/BaseHttpRequest.kt:30-35` 在**未配置证书（默认/未初始化态）时默认接入**它：
```kotlin
if (SslUtils.certificateInputStream == null) {
    httpClientBuilder.hostnameVerifier(SslUtils.doNotVerifier)   // 永远返回 true
    httpClientBuilder.sslSocketFactory(SslUtils.createSocketFactory(SslUtils.PROTOCOL), SslUtils.systemDefaultTrustManager())
}
```
**攻击场景**：攻击者持有任意域名（自己合法拥有的域名）的 CA 签名证书，即可对应用真实 API 主机做 MITM —— 因为只校验证书链、不校验主机名绑定，握手成功，凭据 / token / PII 全部可被读取修改（流氓 Wi-Fi、DNS 欺骗、恶意代理场景）。

**修复建议**：
1. 移除 `doNotVerifier` 作为默认；若必须保留测试用途，仅在 `BuildConfig.DEBUG` 下启用并大声告警。
2. 默认路径（未配置证书时）应 fail-closed，使用平台正常 `HostnameVerifier`。
3. 自签 / 固定场景改用 OkHttp `CertificatePinner` 或现有 `customVerifier`/`systemDefaultTrustManager`；删除 KDoc 中推广 `doNotVerifier` 的生产用法示例。

---

## 🟠 HIGH（合并前应修复，19 项）

### lib-common-android

**H1. `ProcessBuilder` 死锁 —— `waitFor()` 先于排空 stdout/stderr**
`utils/shell/ShellUtil.kt:75-84`
```kotlin
result = process.waitFor()
if (isNeedResultMsg) { process.inputStream... ; process.errorStream... }
```
子进程管道缓冲有限（Linux ~64KB）。命令输出超过缓冲（如进程众多时的 `ps`、`mount`）会在子进程写管道时阻塞，而无人读取 → `waitFor()` 永不返回，调用线程挂起。影响 `getProcessesList()`、`isProcessRunning()`、`remountFileSystem()` 等所有消费者。
**修复**：用独立线程 / 协程与 `waitFor()` 并发排空两个流。

**H2. pointer ID 与 pointer index 混用 —— 多指处理崩溃 / 取错坐标（两处）**
`utils/TouchHelper.kt:92-106`（`processCancelTouch`）、`utils/TouchHelper.kt:108-123`（`processTouchDown`，117-118 行）
```kotlin
// processCancelTouch
for (i in 0 until MAX_TOUCH_POINTS) {
    val activePointerId = event.getPointerId(i)
    touchListener.onEvent(..., event.getX(activePointerId)..., event.getY(activePointerId)...)
}
// processTouchDown（同类）
event.getX(activePointerId).roundToInt()   // activePointerId 是 ID，却被当 index 传入
event.getY(activePointerId).roundToInt()
```
`getX/getY` 接收的是 pointer **index**（`0 until pointerCount`），而非 pointer **ID**。ID≠index（任一指抬起 / 落下后常见）时抛 `IllegalArgumentException` 或返回错误手指坐标。`processCancelTouch` 还额外固定循环 `0 until MAX_TOUCH_POINTS(10)` 忽略 `pointerCount`，`i >= pointerCount` 时 `getPointerId(i)` 抛异常；整个循环体套在单个 `runCatching` 中 → 首次异常即中止全部循环，静默吞掉后续 UP 通知。`processTouchDown` 同样把 `activePointerId`（ID）当索引传给 `getX/getY`（117-118）。
**修复**：所有取坐标处以 pointer **index** 作 `getX/getY` 的参数（`processCancelTouch` 用 `for (i in 0 until event.pointerCount)` 的 `i`；`processTouchDown` 用 `event.findPointerIndex(activePointerId)` 得到的 index），`getPointerId(i)` 仅作为负载 ID 上报。

**H3. `uri.path!!` 未检查非空断言**
`utils/FileDocumentUtil.kt:151-153`
```kotlin
} catch (e: NumberFormatException) { uri.path!!.replaceFirst(...) }
```
`Uri.getPath()` 对 opaque URI 合法返回 `null` → 在一个专为防御性解析外部 content URI 的函数里抛未捕获 NPE。
**修复**：`uri.path ?: return null` 或 `?.let { ... }`。

**H4. Drive/WhatsApp URI 解析中不安全的 cursor / stream 处理**
`utils/FileDocumentUtil.kt:213-246`（`getDriveFilePath`）、`253-300`（`copyFileToInternalStorage`）
- `getColumnIndex(DISPLAY_NAME)`（非 `getColumnIndexOrThrow`）→ provider 省略该列时 `getString(-1)` 抛异常；
- `openInputStream(uri)` 可返回 `null`，`inputStream!!.available()` NPE；
- 忽略 `moveToFirst()` 返回值，空 cursor 时读取叠加崩溃。
**修复**：用 `getColumnIndexOrThrow`；读取前检查 `moveToFirst()`；`openInputStream()` 先判空。

**H5. ping 目标未净化拼入命令**
`utils/NetworkUtil.kt:167-205`（`getLatency`）
```kotlin
val pingCommand = String.format(..., "/system/bin/ping -c %d %s", numberOfPackages, ipAddress)
Runtime.getRuntime().exec(pingCommand)
```
`Runtime.exec(String)` 不经 shell（按空白分词），故无经典 shell 注入；但 `ipAddress` 未校验，`"8.8.8.8 -f"` / `"-i 0.0001 host"` 可注入额外 ping 参数（泛洪 / 超大包），`numberOfPackages` 无上限 → 设备资源耗尽 DoS。（security-reviewer 评 MEDIUM，此处取较高级别。）
**修复**：`ipAddress` 按 IP/主机名正则校验；`numberOfPackages` 钳制到小正整数范围；优先用 `ProcessBuilder(listOf("/system/bin/ping", "-c", n.toString(), ipAddress))`。

### lib-network

**H6. `result()` 包装器吞掉 `CancellationException`，破坏结构化并发**
`http/generic/ResultExt.kt:55-58`、`http/net/ResultExt.kt:47-50`
```kotlin
runCatching { Result.Success(withContext(dispatcher) { block() }) }
```
`runCatching` 捕获 `Throwable`，含 `CancellationException`。调用方协程在 `withContext` 挂起期间被取消时，取消被转成 `Result.Failure`，协程观察不到自身取消。
**场景**：ViewModel 在 `viewModelScope` 中 `result { api.fetch() }`，界面销毁、作用域取消，`result()` 之后的代码仍以合成 Failure 继续运行（更新已销毁的 StateFlow / 误触发重试）。
**修复**：包装前 rethrow：`if (err is CancellationException) throw err` 再分类。

### draw-on-screen（`FingerPaintView.kt`）

**H7. `undo()` 在无路径可删时仍递减 `countDrawn`**
`342-347`
```kotlin
paths.takeIf { it.isNotEmpty() }?.removeAt(paths.lastIndex)
countDrawn--   // 无条件执行
```
`paths` 为空时删除是 no-op，但 `countDrawn--` 照常执行 → 计数变负、与 `paths.size` 失同步。下一帧 `onDraw` 中 `index >= countDrawn`（299）对所有 index 恒真 → 每帧给所有 stroke 重设 `maskFilter`，破坏"已提交"优化并导致已模糊 stroke 闪烁。
**修复**：仅在真正删除时递减，并钳制 `countDrawn >= 0`。

**H8. `onDraw()` 静默吞掉所有异常（含 Error），无日志**
`296-310`
```kotlin
runCatching { ... }.onFailure { /* You can ignore this error. */ }
```
`runCatching` 捕获 `Throwable`。绘制循环中任何 bug（并发修改 `paths` 的 IOOBE、绘制大 `brushBitmap` 时的 OOM）被丢弃、零日志 → 画布静默变空 / 变旧，无诊断线索，违反"绝不静默吞错"约定。
**修复**：收窄到真正可恢复的异常；失败时经 `log` 模块记录。

**H9. `paths` 同步不一致，给出虚假的线程安全保证**
`366-379`（`clear`/`drawUserPath` 加 `@Synchronized`）vs `291-312`（`onDraw`）、`228-281`（触摸）、`undo`（未加锁）
`@Synchronized` 仅在同步方法间互斥，无法保护普通 `ArrayList` 在 `onDraw()` 迭代（主线程绘制）时被 `clear()`/`drawUserPath()`（若从后台线程调用，`drawUserPath` 为加载外部路径数据而设计，此用法可信）并发修改 → `onDraw` 的 `for (index in paths.indices)` 中途 `paths.clear()` 抛 CME / IOOBE 崩溃（且被 H8 吞掉、静默不可见）。
**修复**：要么强制所有变更方法仅主线程调用（与常规 View API 一致），要么在 `onDraw` 开头拷贝 `paths` 到局部 `val` 快照，变更方在单锁下整体替换列表。

### lib-compose

**H10. `DisposableEffect` 的清理动作与安装动作相同，未复原**
`composable/event/base/GenericEventHandler.kt:53-59`
```kotlin
DisposableEffect(view) {
    navigationBarVisibility(window, view, isShow = false)
    onDispose { navigationBarVisibility(window, view, isShow = false) } // 又是 false
}
```
`onDispose` 应复原（重新显示导航栏），却再次 `isShow = false`。全代码库仅另有 app 控制的 `UiEvent.NavigationBar` 事件会显示导航栏，不保证每屏触发 → 任何承载 `EventHandler`/`GenericEventHandler` 的界面会**永久隐藏系统导航栏**。
**修复**：`onDispose { navigationBarVisibility(window, view, isShow = true) }`（或恢复到进入前状态）。

**H11. `rememberDebounceClickHandler` 陈旧闭包**
`ext/DebounceExt.kt:38-55`
`remember {}` 无 key，返回的 lambda 只创建一次、永久捕获首帧的 `onClick`。当 `onClick` 引用随重组变化的状态（如 `{ viewModel.submit(currentValue) }`）时，点击总是执行首帧版本、静默丢弃更新。对比同模块 `OnLifecycleEvent.kt:34` 正确用了 `rememberUpdatedState`。
**修复**：用 `rememberUpdatedState(onClick)`，在记忆的 lambda 内调用更新后的引用。

**H12. `Modifier.dispatchTouchEvent` 在不重启的 `pointerInput(Unit)` 中捕获陈旧 `onTouch`**
`ext/ModifierExt.kt:80-102`
`pointerInput(Unit)` 生命周期内仅启动一次、重组不重启 → 若应用该 modifier 的可组合项以不同 `onTouch` 重组（引用新外部状态），事件循环仍调用附加时捕获的首个 `onTouch`。列表项 `onTouch = { selectItem(item.id) }` 会一直调用初次绑定项的处理器。
**修复**：`pointerInput` 以 `onTouch` 为 key，或用状态持有者（`rememberUpdatedState`）在循环内引用。

**H13. 自动滚动循环用 `runCatching` 吞 `CancellationException`**
`composable/pager/HorizontalAutoPager.kt:132-161`
注释已承认捕获 `CancellationException` 但从不 rethrow，命中项目明令禁止的反模式。`LaunchedEffect` 被取消（`underDragging` 翻转 / 离开组合）时取消信号被吞，破坏结构化并发、掩盖诊断。
**修复**：仅捕获窄异常，或显式 `catch (e: CancellationException) { throw e }`。

### lib-common-kotlin

**H14. `Int/Long.formatDecimalSeparator()` 对特定负数输出错误**
`exts/NumericExt.kt:78-82`（Int）、`94-98`（Long）
`reversed().chunked(3).joinToString(",").reversed()` 在量级位数恰为 3 的倍数（3/6/9…）时把 `-` 号误分到独立分组。经手动推演验证：`(-100)` → `"-,100"`（应 `"-100"`）；`(-123456)` → `"-,123,456"`（应 `"-123,456"`）。`NumericExtUnitTest.kt` 仅覆盖 `gcd`/`getRatio`，**此函数无测试**，bug 静默发布。
**修复**：符号单独处理（格式化 `abs(value)` 后按需前置 `-`）。

**H15. `Double/Float.round()` 在非英语默认 locale 及 `Infinity` 下崩溃**
`exts/FloatExt.kt:10-17`
`DecimalFormat(pattern)`（无参构造）使用 `Locale.getDefault()` 符号，结果再经 `String.toDouble()`（仅接受 `.` 小数点）解析回。
- 逗号小数点 locale（德 / 法 / 意 / 西 / 巴葡 / 俄，真机 / CI 常见）下，`3.14.round()` 格式化为 `"3,14"`，`"3,14".toDouble()` 对普通输入抛 `NumberFormatException`；
- 即便英语 locale，`Double.POSITIVE_INFINITY.round()` 格式化为 `"∞"`，`"∞".toDouble()` 抛异常。
**修复**：用 `DecimalFormatSymbols(Locale.ENGLISH)`（与 `ReadableByteExt.kt` 一致）或 `BigDecimal(this).setScale(precision, roundingMode)`；`NaN`/`Infinity` 提前特判。

**H16. `multiCatch` 在省略 `catchBlock` 时静默丢弃预期异常**
`exts/ExceptionExt.kt:34-50`
第 44 行 `catchBlock?.invoke(e)`：若捕获异常匹配 `exceptions` vararg 但 `catchBlock` 为 `null`（文档标注可空），异常被完全丢弃 —— 不 rethrow、不记录、无副作用。`multiCatch(runBlock = { db.write(x) }, exceptions = arrayOf(IOException::class))` 无 `catchBlock` 时静默吞掉失败写入，违反"绝不静默吞错"。此外 catch 裸 `Throwable`（含 Error/CancellationException，见 M-K1）。
**修复**：要求 `catchBlock` 非空，或默认为经 `log` 模块记录的 lambda。

### lib-json（`JsonExt.kt`）

**H17. 所有解析 / 序列化失败被静默吞掉（含 `Throwable`）**
`60`（`toJsonString`）、`74-79`（`toObject<T>()`）、`94-99`（`toObject(type)`）
三个函数均 `runCatching{}.getOrElse("")/getOrNull()`，`runCatching` 捕获 `Throwable`（`JsonSyntaxException`、`ClassCastException`、乃至 `StackOverflowError`/`OutOfMemoryError`）→ 全转为空串 / `null`、零日志。畸形 / 类型不符的 JSON 静默变 `null`，与"数据本就为空"（KDoc 所述）无法区分，无排查线索。
**修复**：捕获 `Exception`（非 `Throwable`），按需 rethrow `CancellationException`/`Error`，返回前经 `log` 记录。

**H18. 具化 `toObject<T>()` 对参数化类型丢失泛型信息**
`74-79`
`gson.fromJson(this, T::class.java)` 中 `T::class.java` 擦除泛型。`T = List<Foo>`/`Map<K,V>` 时 Gson 反序列化为 `List<LinkedTreeMap>`/裸类型。`jsonString.toObject<List<CmdBean>>()` 编译通过、返回非空，但元素实为 `LinkedTreeMap`，在远处消费点抛 `ClassCastException`（且不在本函数 `runCatching` 内、直接崩溃）。
**修复**：KDoc 显著标注该限制，或内部用 `object : TypeToken<T>() {}.type` 委托给 `toObject(type: Type)`。

### lib-compress

**H19. 无界解压 —— 解压炸弹 / OOM DoS**
`FlaterExt.kt:20-34`（`ByteArray.decompress()`）
**印证**：security-reviewer + 纯 Kotlin kotlin-reviewer 两个代理发现
输出无上限，`bufferSize`（默认 8KiB）只限单块大小。小体积恶意 / 损坏 deflate 流（raw deflate 可超 1000:1）使 `ByteArrayOutputStream` 无限增长直至堆耗尽。作为可能接收网络 / 文件字节的公开扩展函数，风险直接。
**修复**：新增 `maxOutputSize: Long` 参数，超限即 fail-fast 抛出；或在 KDoc 明确仅可用于可信数据并在调用点设限。

---

## 🟡 MEDIUM（25 项）

### lib-common-android
- **M-A1** 多处用 `printStackTrace()` 代替 `log` 模块（违反错误处理约定）：`exts/ToastExt.kt:320`、`exts/ResourcesExt.kt:59`、`utils/DeviceUtil.kt:262-264`、`ui/ForegroundComponent.kt:142`、`utils/TouchHelper.kt:68,89,105,122`。
- **M-A2** 近似重复的文件保存函数错误处理不一致：`exts/ResourcesExt.kt:35-48`（`saveRawResourceToFile`，**无**错误处理，异常上抛）vs `50-61`（`saveAssetToFile`，`runCatching{}.getOrElse`）。应统一策略，优先返回类型化结果。
- **M-A3** 反射调用隐藏 / 灰名单 API：`exts/DeviceExt.kt:395-399`（`getImei` 反射 `TelephonyManager.getImei`），受隐藏 API 限制、`runCatching` 静默返回 null、失败无日志。应记录风险 / 加 debug 日志。
- **M-A4** `exts/DeviceExt.kt:685` `display!!.rotation`：`Activity.getDisplay()` 某些生命周期状态返回 null → NPE。应 `display?.rotation ?: windowManager.defaultDisplay.rotation`。

### lib-network
- **M-N1** `interceptors/HttpLoggingInterceptor.kt:148-160,211-224`：`Level.HEADERS`/`BODY` 下逐字记录所有头（`Authorization`/`Cookie`/`Set-Cookie`）与请求 / 响应体（凭据 / token / PII），无脱敏机制（缺上游 OkHttp 的 `redactHeader()`）。消费方误在生产开启即泄露。应加可脱敏头名集合，默认掩盖敏感头。
- **M-N2** `exception/ApiException.kt:3`：`import android.R.id.message` 是误导性无用导入 —— 它被类内同名 `message` 属性遮蔽、无实际用途，疑似 IDE 误自动导入。应删除。（注：`lib-network` 本身是 Android library 模块，"引入框架符号"并非问题所在；问题仅是该导入无用且误导。）
- **M-N3** `SslUtils.kt:100,108`：`certificateInputStream`、`hostnames` 为非 `@Volatile` 可变 `var`，被 OkHttp 连接池线程读、主线程写 → JMM 可见性 bug，`customVerifier` 可能读到陈旧 / null 值抛 `IllegalArgumentException`。应 `@Volatile` 或封装为一次性初始化的不可变配置。
- **M-N4** `SslUtils.kt:110-114`：`customVerifier` 中 `requireNotNull(hostnames)` 结果被丢弃，随后 `hostnames!!.contains(...)` 再次读并强解，冗余 `!!` 且有 TOCTOU 窗口。应 `val names = requireNotNull(hostnames) { ... }; names.contains(hostname)`。

### draw-on-screen（`FingerPaintView.kt`）
- **M-D1** `114-133` `init`：`runCatching { 属性读取... }` 无 `onFailure`，任一 `getColor`/`getDimension` 抛异常则后续属性赋值被静默跳过、视图部分配置。应加 `onFailure` 日志。
- **M-D2** `150-157` `onSizeChanged`：每次替换 `brushBitmap`/`brushCanvas` 未先 `recycle()` 旧 Bitmap → 多次尺寸变化（分屏 / 多窗 / 旋转）产生可避免的内存压力 / GC 抖动。
- **M-D3** `150-157`：未防 0 / 负尺寸，`Bitmap.createBitmap(w, h, ...)` 在 `w==0||h==0` 时抛 `IllegalArgumentException`。应 `if (w <= 0 || h <= 0) return`。
- **M-D4** `291-312` `onDraw()`：每帧清空并从 index 0 重绘全部 path（含已提交 stroke），`BlurMaskFilter` 强制软件渲染 → 绘制成本随 stroke 数线性增长，活跃绘制时明显卡顿。应缓存已提交 stroke 到持久 bitmap，每帧只叠加进行中 path。
- **M-D5** `375,381`：`drawUserPath(userPath: MutableList<...>)` / `getPaths(): MutableList<...>` 公开 API 暴露可变集合类型（违反"返回 List 非 MutableList"约定，`getPaths` 已防御性拷贝但返回类型仍是 MutableList）。应改为 `List<...>`。
- **M-D6** `204-226` `onTouchEvent`：无 `ACTION_CANCEL` 分支。父容器（ScrollView/ViewPager）中途拦截手势时，进行中的 `Path` 未闭合、`countDrawn` 未递增，会被每帧当"进行中"无限重绘。应加 `ACTION_CANCEL` 处理。

### lib-compose
- **M-C1** `composable/nav/AppNavigation.kt:40`：`val navController: NavHostController` 为 public，收到 `AppNavigation` 的可组合项可透传获取原始 NavController，违背该类"传 lambda / 包装而非 NavController"的设计意图。应 `private`/`protected`，只暴露预期导航方法。
- **M-C2** `utils/UtilExt.kt:34-35`：顶层 `SimpleDateFormat` 单例非线程安全，并发格式化（后台线程 + 主线程重组）风险输出错乱 / `NumberFormatException`。应用 `DateTimeFormatter` 或 `ThreadLocal`。
- **M-C3** `composable/CustomOutlinedTextField.kt:51-57`、`composable/SearchBar.kt:62-69`：用 `var` + 条件重赋值表示派生值，应改为 `val` + `if`/`when` 表达式或 `Modifier.then`（immutability 约定）。
- **M-C4** `composable/event/base/GenericEventHandler.kt:64`：`LaunchedEffect(events, lifecycleOwner)` 以 `events: Flow` 为 key，若调用方传入每次重组新建的 Flow（`flow{}`/`.map{}`）则每次重组重启、重订阅、重启窗口内漏 / 重事件。应在 API 契约中要求 `events` 为稳定引用。

### lib-common-kotlin
- **M-K1** `exts/ExceptionExt.kt:42`：`multiCatch` 捕获裸 `Throwable`（`OutOfMemoryError`/`StackOverflowError`/`CancellationException` 均被路由 / 吞噬）。应收窄到 `Exception` 或先 rethrow `CancellationException`/`Error`。
- **M-K2** `exts/ReadableByteExt.kt:153`：`humanReadableByteCount()` 用 `Locale.getDefault()`，而同文件其他格式化（102-123）均显式 `Locale.ENGLISH` → 逗号 locale 下产出 `"1,50MiB"`，不一致且不可安全再解析。应传 `Locale.ENGLISH`。
- **M-K3** `exts/FloatExt.kt:11`：`"#".repeat(precision)` 对 `precision < 0` 抛底层 `IllegalArgumentException`，无 API 边界校验。应 `require(precision >= 0) { ... }`。
- **M-K4** `utils/SingletonHolder.kt:30`、`utils/SingletonHolder2.kt:33`：`creator!!(arg)` 双检锁下当前安全但违反"禁用 `!!`"约定，重构 / 继承时为隐患。应 `checkNotNull(creator) { ... }(arg)`。

### lib-json（`JsonExt.kt`）
- **M-J1** `45-58`：`val gson: Gson get() = GsonBuilder()...create()` 为计算属性，每次访问都反射新建 `GsonBuilder` + 两个匿名 `ExclusionStrategy` + `Gson`。Gson 实例线程安全、应复用。应 `by lazy` 单例。
- **M-J2** `94-99`：inline `runCatching` 内 `return gson.fromJson(...)` 为非局部返回，成功路径直接退出、`.getOrNull()` 成死代码（与 74-79 重载风格不一致，后续加 `.onSuccess`/日志会静默失效）。应去掉 `return`，以表达式作最后一行。

### lib-compress
- **M-CP1** `FlaterExt.kt:14-34`：`compress()`/`decompress()` 无异常契约 KDoc。`decompress()` 对非 deflate / 损坏字节抛 `ZipException`/`IOException`，未捕获 / 包装 / 记录，调用方可能不知需 try/catch。应文档化或用 `Result`/sealed 类型包装。

---

## 🟢 LOW（12 项）

### lib-common-android
- **L-A1** `ui/ForegroundComponent.kt:92`：`checkRunnable?.let { handler.removeCallbacks(checkRunnable!!) }` 在 `let` 内又 `!!`，应用 `it`。
- **L-A2** `exts/SmartSize.kt:14-16`：`var size/long/short` 构造后从不重赋值，应 `val`。
- **L-A3** `exts/FileExt.kt:17-30`（`InputStream.toFile`）：无显式错误处理，`IOException` 上抛（部分调用方包裹、部分不，见 M-A2）。
- **L-A4** `exts/DeviceExt.kt` 770 行，逼近 800 行上限，可按职责拆分（厂商检测 / 屏幕几何 / 方向计算）。

### lib-compose
- **L-C1** 遗留注释 / 死代码：`composable/event/DispatchTouchEvent.kt:24-28`、`composable/pager/HorizontalAutoPager.kt:54,68-73,102-114`、`composable/event/base/GenericEventHandler.kt:33,157-159`。应删除。
- **L-C2** `composable/loading/RippleAnimation.kt:36-41`：每次重组新建 `List` 包裹（已正确 `remember` 的）两个 `Animatable`。应 `remember { listOf(...) }`。

### lib-common-kotlin
- **L-K1** `exts/CalendarExt.kt:12-27`：`formatTimestamp()`/`formatTimestampShort()` 未校验负时长，产出混淆的带符号分量。应 `require(this >= 0)` 或钳制为 0。
- **L-K2** `exts/NumericExt.kt:61-64`：`getRatio` 注释漂移（提及"递归循环重复太多次"，实际 `getOrElse` 处理的是负输入 / 除零）。
- **L-K3** `exts/NumericExt.kt:78-82,94-98`：`Int`/`Long` 版 `formatDecimalSeparator` 逻辑完全重复（且共享 H14 缺陷），修复后应抽私有 helper。

### lib-compress
- **L-CP1** `FlaterExt.kt:27`：遗留死代码注释 `//baos.buffered(bufferSize).write(ins.readBytes())`。应删除。

### draw-on-screen（`FingerPaintView.kt`）
- **L-D1** `295,311`：`canvas.save()`/`restore()` 之间未改变 matrix/clip，为无效开销。
- **L-D2** `204-226`：自定义 `onTouchEvent` 未调 `performClick()`，无障碍服务依赖它（lint `ClickableViewAccessibility`）。如属有意应加注释 / suppress。

---

## 关键结论与建议整改顺序

1. **发版前必修 3 个 CRITICAL**：C1 路径穿越、C2 shell 注入、C3 TLS trust-all 默认启用 —— 均可被消费方应用实际触发。
2. **跨模块反复出现的两类反模式**，建议作为专项统一整改：
   - `runCatching`/`catch` 吞掉 `Throwable`（尤其 `CancellationException`）：H6、H13、H16、H17、H8、M-K1 等。
   - 外部输入未净化即进入文件路径 / 命令 / 解压：C1、C2、H5、H19。
3. **纯逻辑 bug 无测试覆盖**（H14 负数千分位、H15 locale/Infinity round），修复时必须补单测（项目 TDD 约定，纯逻辑模块目标 80% 覆盖）。

**建议顺序**：CRITICAL（C1→C2→C3）→ 跨模块 CancellationException 吞噬整改 → 逐模块清 HIGH → MEDIUM → LOW。

---

*本报告由 5 个并行审查代理生成并经交叉去重整合。所有 `file:line` 均为仓库相对路径，可直接跳转。*
