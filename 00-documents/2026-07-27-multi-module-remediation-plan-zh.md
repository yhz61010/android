# 多模块整改实施方案（实现文档）

- **日期**：2026-07-27
- **基线**：master HEAD `474b031ad`
- **设计文档（输入）**：[`2026-07-27-multi-module-code-review-zh.md`](./2026-07-27-multi-module-code-review-zh.md)（审查报告，含问题清单 + 修复建议）
- **覆盖范围**：全部 59 项（3 CRITICAL / 19 HIGH / 25 MEDIUM / 12 LOW）
- **目标**：把审查报告的每条 finding 落成可执行的整改步骤 —— 涉及文件、具体改法（含代码骨架）、TDD 测试点、验证命令、破坏性标注、整改顺序。

---

## 0. 通用约定

### 0.1 TDD 流程（每项适用，纯逻辑项强制）
1. **RED**：先写覆盖该缺陷的失败测试（重现 bug / 表达期望行为）。
2. **GREEN**：最小实现让测试通过。
3. **REFACTOR**：整理，保持绿。
4. 纯逻辑模块（lib-common-kotlin、lib-json、lib-compress）目标覆盖率 80%+；Android/UI/View 项以 Robolectric 或行为断言为主，硬件相关不强求覆盖。

### 0.2 验证命令
| 目的 | 命令 |
|------|------|
| 单模块单测 | `./gradlew :<module>:testDebugUnitTest`（纯 JVM 模块若无该任务，用 `./gradlew :<module>:test`，以 `./gradlew :<module>:tasks` 实际为准） |
| 静态分析 | `./gradlew detekt` / `./gradlew ktlintCheck`（自动修风格 `ktlintFormat`） |
| 全量质量套件 | `./gradlew staticCheck` |

> 每完成一项：先跑对应模块单测，再 `detekt`/`ktlintCheck`；阶段结束跑一次 `staticCheck`。

### 0.3 破坏性变更清单（需在 CHANGELOG / 迁移指南同步）
| 项 | 破坏性 | 说明 |
|----|--------|------|
| C3 | 行为破坏 | 移除 `doNotVerifier` 默认接入，默认改为平台校验；影响 `http` 模块下游 |
| H16 | 行为变更 | `multiCatch` 的 `catchBlock` 保持可空，但默认行为由「静默丢弃」改为「上抛」 |
| H18 | 行为变更 | `toObject<T>()` 泛型解析修正，`List<T>`/`Map<K,V>` 行为改变 |
| H19 | API 增强（兼容） | `decompress()` 新增带默认值的 `maxOutputSize` 参数，默认不破坏 |
| M-D5 | API 破坏 | `drawUserPath`/`getPaths` 签名 `MutableList`→`List` |
| M-C1 | API 破坏（**本轮延后**） | `AppNavigation.navController` 收窄为 private/protected；影响面大（需迁移所有直接访问 `navController` 的调用点）。经决策（方案 C）**暂不实施**，留待单独评估与迁移，详见 §0.5 |

### 0.3.1 构建 / 测试依赖前置（**动手前必读**）

以下模块**当前没有测试依赖配置**，相关阶段的 TDD 步骤执行前必须先在对应 `build.gradle.kts` 的 `dependencies {}` 补齐，否则测试无法编译：

| 模块 | 现状 | 需补充 |
|------|------|--------|
| lib-common-kotlin | 已有 `testImplementation(libs.bundles.test)` | 无（H14/H15/H16/M-K* 可直接写测试） |
| lib-compress | 已有 `libs.bundles.test` + `libs.bundles.powermock` | 无（H19/M-CP1 可直接写测试） |
| lib-common-android | **无** test 依赖 | `testImplementation(libs.bundles.test)`（该 bundle 已含 `robolectric`，Robolectric 项无需单列） |
| lib-network | **无** test 依赖 | `testImplementation(libs.bundles.test)` 即可 —— 该 bundle **已含** `test-coroutines`（`runTest` 可用），无需单列。SSL 测试所需 okhttp 从 `compileOnly` 提升为 `testImplementation`。（若确需单列，别名是 `libs.test.coroutines`，**非** `libs.kotlin.coroutines.test`） |
| lib-json | **无** test 依赖 | `testImplementation(libs.bundles.test)` + `testImplementation(libs.gson)`（当前 gson 是 `compileOnly`，测试用不到） |
| lib-compose | **无** test 依赖 | **JVM 单测**（H13 用 `runTest`；H11/H12 抽出的纯逻辑）需先加 `testImplementation(libs.bundles.test)`（含 `test-coroutines`）。**Compose UI 测试**另需 `androidTestImplementation(compose-ui-test-junit4)` + `debugImplementation(compose-ui-test-manifest)`；H10/H11/H12 优先抽纯逻辑做 JVM 单测以避免引入 UI test 栈 |

> **注**：具体 bundle/别名以 `gradle/libs.versions.toml` 实际定义为准；补依赖属**构建配置变更**，也应记入变更记录。lib-compose 的 H10/H11/H12 若不引入 Compose UI test 栈，可退化为"抽出纯逻辑 + 代码审查"验证。

### 0.3.2 日志依赖策略（修正 log 相关建议）

审查报告中多处"经 `log` 模块记录"的建议，**仅适用于已依赖 log 的模块**：

- **lib-network** 有 `compileOnly(projects.log)`；lib-compose 有 `api(projects.log)` — 可用 log（测试时注意 `compileOnly` 需在 test 提升）。
- **lib-common-kotlin / lib-json / lib-compress** **无 log 依赖**，且 lib-common-kotlin 是基础工具模块 —— **不要为记日志而新增 log 依赖**。这些模块的错误处理改为：**上抛** / **可注入 handler 参数** / **返回类型化结果**，核心是"不静默吞掉、不吞 `CancellationException`"，而非强制记日志。
- **lib-common-android**（M-A1 涉及）：直接依赖中无项目 `log`（`api(projects.libCommonKotlin)` + `implementation(projects.floatview)`）。动手前先确认 log 是否经传递依赖可达；**不可达则同样改为上抛/handler，不为此新增 log 依赖**（注：现有代码 `ShellUtil.kt:122` 用的是 `android.util.Log`，非项目 log 模块）。
- **draw-on-screen**（H8、M-D1 涉及）：同样**未依赖项目 `log` 模块**。H8/M-D1 的"记录日志"改为：优先用 `android.util.Log` / 可注入 handler / 上抛，**不为日志新增项目 log 依赖**；若确要接入项目 log，须作为独立的构建配置变更评估。

### 0.4 建议整改阶段
- **Phase 1**：C1 → C2 → C3（发版必修）
- **Phase 2**：跨模块反模式统一整改 —— `runCatching`/`catch` 吞 `Throwable`/`CancellationException`（H6、H8、H13、H16、H17、M-K1）；外部输入未净化进入路径/命令/解压（已含 C1/C2、H5、H19）
- **Phase 3**：其余 HIGH（H1、H2、H3、H4、H7、H9、H10、H11、H12、H14、H15、H18）
- **Phase 4**：MEDIUM（25 项）
- **Phase 5**：LOW（12 项）

### 0.5 延后项（经决策暂不实施）
| 项 | 原因 | 后续处理 |
|----|------|----------|
| M-C1 | `AppNavigation.navController` 收窄为 private/protected 属 **API 破坏**，影响面大：需扫描并迁移所有直接访问 `navController` 的调用点（demo、androidbase、各 feature 模块），改用 `AppNavigation` 暴露的导航方法（`navigate*`/`popBackStack`/`relogin` 等）。经决策（方案 C），本轮 Phase 4 **仅做 M-D5、跳过 M-C1**。 | 作为独立任务：①`grep -rn "\.navController"` 全量盘点直接访问点；②为缺失的导航场景在 `AppNavigation` 补充方法；③逐调用点迁移；④在 CHANGELOG 记 API 破坏与迁移指引。 |
| M-D4 | `FingerPaintView` 将已提交 stroke 缓存进持久 bitmap、每帧只叠加进行中 path，属 **渲染架构级性能优化**：改变逐帧绘制语义、与 undo 全量重绘耦合，只能靠视觉回归验证、无法单测。在"本机不编译"前提下贸然改有回归风险，故本轮延后。 | 作为独立任务：①引入"已提交层 bitmap"与"进行中 path"两级绘制；②undo/clear 时使已提交层失效并全量重绘；③做视觉回归（多 stroke / undo / resize / blur 笔刷）后再合入。当前每帧全量重绘功能正确，仅非最优。 |
| L-A4 | `DeviceExt.kt`（770 行）按职责拆分为多文件（厂商检测 / 屏幕几何 / 方向计算）属 **纯结构重构**，无行为变化，与本分支"安全/正确性修复"主题无关；770 行拆分 diff 很大，且本机不编译时 import 易出现漏导/多导（→ ktlint/编译失败）。经决策本轮延后，避免污染聚焦的修复分支。 | 作为独立任务：①按职责将顶层扩展函数迁到同包新文件（`DeviceExt.kt` / `DeviceScreenExt.kt` / `DeviceOrientationExt.kt` 等，均属 `com.leovp.android.exts`，调用点无需改 import）；②逐文件核对 import 精确性；③本机 `ktlintCheck` + `detekt` + 编译验证后合入。 |

> M-D5（`drawUserPath`/`getPaths` 的 `MutableList`→`List`）按方案 C 正常实施；`MutableList` 为 `List` 子类型，多数传入 `MutableList` 的调用点源码兼容，仅显式声明形参类型处需适配。

---

## Phase 1 — CRITICAL

### C1. FileDocumentUtil 路径穿越（same-UID 范围内读/写）
- **文件**：`lib-common-android/.../utils/FileDocumentUtil.kt`（`getPathFromExtSD` 183-211、`getDriveFilePath` 213-246、`copyFileToInternalStorage` 253-300、`getFileRealPath` 58-61）
- **破坏性**：否（对合法输入行为不变，仅拒绝恶意路径）
- **改法**：
  1. 新增私有工具函数集中做净化与校验：
     ```kotlin
     private fun sanitizedFileName(rawName: String): String {
         val name = File(rawName).name            // 仅取最后一段
         require(name.isNotBlank() && name != "." && name != "..") { "Invalid file name" }
         require(!name.contains('/') && !name.contains('\\')) { "Illegal path separator in name" }
         return name
     }

     private fun resolveWithinBase(base: File, childName: String): File {
         val target = File(base, sanitizedFileName(childName)).canonicalFile
         require(target.canonicalPath.startsWith(base.canonicalFile.canonicalPath + File.separator)) {
             "Path escapes base directory"
         }
         return target
     }
     ```
  2. `getDriveFilePath`/`copyFileToInternalStorage`：把 `File(context.cacheDir, name)` / 字符串拼接改为 `resolveWithinBase(cacheDir, name)` / `resolveWithinBase(File(filesDir, newDirName), name)`。
  3. `getPathFromExtSD`（当前签名 `private fun getPathFromExtSD(pathData: Array<String>): String`，183 行）：
     - **签名改为 `String?`**（要能返回 null；调用方 `getFileRealPath` 58-61 需相应处理 null 分支）。
     - 入口加 malformed docId 防护：`if (pathData.size < 2) return null`（当前直接访问 `pathData[1]`（185 行）会对畸形 docId 抛 `ArrayIndexOutOfBoundsException`）。
     - 对每个候选 `fullPath`（`primary`/`SECONDARY_STORAGE`/`EXTERNAL_STORAGE` 三个分支）先 `canonicalPath` 规范化，校验仍落在对应存储根之下再返回，否则跳过 / 返回 `null`。
     - 修掉末行恒等返回 `return if (fileExists(fullPath)) fullPath else fullPath`（210 行，两分支相同，应 `else null`）。
  4. 不再用 `uri.authority` 字符串相等作为信任依据（配合 H3/H4 一并加固该文件）。
  5. 因 `getPathFromExtSD` 返回类型由 `String` 变 `String?`，`getFileRealPath` 内对其结果的使用需加 null 处理（`?:` 回退或提前返回 null）。
- **测试**（Robolectric，`lib-common-android/src/test`）：
  - `DISPLAY_NAME = "../../evil.db"` → 写入被拒/落在 base 内（断言抛出或路径受限）。
  - docId 含 `primary:../../..` → `getPathFromExtSD` 返回 null。
  - 正常文件名 → 行为与现状一致（回归）。
- **验证**：`./gradlew :lib-common-android:testDebugUnitTest detekt ktlintCheck`

### C2. ShellUtil 命令注入（forceStop/uninstallApk 参数拼接）
- **文件**：`lib-common-android/.../utils/shell/ShellUtil.kt`（`forceStop` 94-96、`uninstallApk` 178-180；`execCmd` 41-96 保留为底层 API）
- **破坏性**：否（合法包名不受影响）
- **改法**：
  1. 新增包名校验：
     ```kotlin
     private val PACKAGE_NAME_REGEX =
         Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
     private fun requireValidPackage(pkg: String): String {
         require(PACKAGE_NAME_REGEX.matches(pkg)) { "Illegal package name: $pkg" }
         return pkg
     }
     ```
  2. **明确路径选择**（现状：`forceStop` 94-96、`uninstallApk` 178-180 都走 `execCmd(..., isRoot = true)` root shell —— `am force-stop`/`pm uninstall` 本就需要 root/系统权限，无法简单换成非 root 参数数组）：
     - **主方案（推荐，最小改动，保留现有 root 行为）**：仅在两个 wrapper 内加 `requireValidPackage(pkgName)` 白名单校验，命令仍经 `execCmd(..., true)` 走 root shell。校验把注入面收敛到"合法包名字符集"，不改变权限模型。
     - **可选增强**：若确有非 root 场景，再新增独立的参数数组 API（如 `ProcessBuilder(listOf("pm","uninstall",pkgName))`），但需说明其对普通应用通常无权限、与现有 root 版本并存而非替换。
     - 结论：本轮按**主方案**执行 —— root 版本保留，靠包名白名单堵注入；不强行改造成非 root 数组调用。
  3. `execCmd(String)` KDoc 补充："命令字符串由调用方保证可信/已校验；本函数按设计执行任意 shell（含 root）。"
- **测试**：
  - `uninstallApk("a; rm -rf /sdcard")` → 抛 `IllegalArgumentException`（不进入 shell）。
  - `uninstallApk("com.leovp.demo")` → 通过校验（mock/验证参数）。
- **验证**：同 C1。

### C3. SslUtils 关闭主机名校验且下游默认启用（跨模块）
- **文件**：`lib-network/.../SslUtils.kt`（`doNotVerifier` 67-69、KDoc 25-53）；下游 `http/.../retrofit/base/BaseHttpRequest.kt:30-35`
- **破坏性**：行为破坏（默认从 trust-all 改为平台校验）—— 必须写入 CHANGELOG + 迁移指南
- **构建现状约束**：`lib-network/build.gradle.kts` **未启用** `buildFeatures { buildConfig = true }`，也**没有** `androidx.annotation` 依赖（依赖全为 `compileOnly`）。因此**不能**直接用 `BuildConfig.DEBUG` 或 `@VisibleForTesting` —— 会编译失败。
- **改法**：
  1. `BaseHttpRequest.kt`：`certificateInputStream == null` 分支改为 **fail-closed** —— 使用平台默认 `HostnameVerifier`，不再接入 `doNotVerifier`。这是本项核心修复。
  2. `SslUtils`：移除把 `doNotVerifier` 作为推荐默认用法；`doNotVerifier` 本身可保留为公开原语（供测试/调用方显式选择），但 **KDoc 明确标注"仅测试用途，生产禁用"**，不再作为默认接入。
     - 若坚持要用 `@VisibleForTesting`/`BuildConfig.DEBUG` gate，则**须先**在 `build.gradle.kts` 加 `buildFeatures { buildConfig = true }` 与 `androidx.annotation` 依赖 —— 作为额外的构建配置变更单独评估，非本项默认动作。
  3. 删除 KDoc 中推广 `doNotVerifier` 的生产用法示例；自签/固定场景改文档指向 `CertificatePinner` 或 `customVerifier`/`systemDefaultTrustManager`。
- **测试**：
  - 未配置证书时构建的客户端使用的 `HostnameVerifier` 不是恒真（断言非 `doNotVerifier`）。
  - `customVerifier` 对匹配/不匹配 hostname 行为正确。
- **验证**：`./gradlew :lib-network:testDebugUnitTest detekt ktlintCheck`；若改到 http 模块则一并 `:http:testDebugUnitTest`。
- **注**：http 模块不在原审查范围，此项跨模块，改动 http 前先确认对该模块其它调用方无回归。

---

## Phase 2 + 3 — HIGH（19 项）

> 排序：先做跨模块反模式（H6/H8/H13/H16/H17），再逐模块其余。

### lib-common-android

**H1. ShellUtil Process 死锁（waitFor 先于排流）** — `ShellUtil.kt:75-84`｜破坏性：否
- 改法：先/并发排空 `inputStream`/`errorStream`（各起一个线程或用协程），再 `waitFor()`；或用 `ProcessBuilder.redirectErrorStream(true)` + 单线程读完再 wait。
- 测试：mock/构造一个输出 >64KB 的命令场景，断言不挂起、拿到完整输出（Robolectric 下可对纯逻辑抽取的排流函数做单测）。

**H2. TouchHelper pointer ID/index 混用（两处）** — `TouchHelper.kt:92-106`(`processCancelTouch`)、`108-123`(`processTouchDown` 117-118)｜破坏性：否
- 改法：取坐标一律用 pointer **index**：`processCancelTouch` 用 `for (i in 0 until event.pointerCount)` 的 `i` 传给 `getX(i)/getY(i)`；`processTouchDown` 用 `event.findPointerIndex(activePointerId)` 得 index；`getPointerId(i)` 仅作负载 ID 上报。
- 测试：构造多指 `MotionEvent`（ID≠index）→ 回调收到正确坐标、不抛异常。

**H3. uri.path!! 可 NPE** — `FileDocumentUtil.kt:151-153`｜破坏性：否
- 改法：`uri.path ?: return null`（或 `?.let{}`）。
- 测试：opaque Uri（`path==null`）→ 返回 null，不崩溃。

**H4. Drive/WhatsApp URI cursor/stream 不安全** — `FileDocumentUtil.kt:213-246,253-300`｜破坏性：否
- 改法：`getColumnIndexOrThrow`；`if (!cursor.moveToFirst()) return null`；`openInputStream(uri) ?: return null`。与 C1 同文件一并改。
- 测试：省略 DISPLAY_NAME 列 / 空 cursor / openInputStream 返回 null → 均安全返回 null。

**H5. NetworkUtil ping 参数未净化** — `NetworkUtil.kt:167-205`｜破坏性：否
- 改法：`ipAddress` 按 IP/主机名正则校验；`numberOfPackages` 钳制正整数上限；优先 `ProcessBuilder(listOf("/system/bin/ping","-c",n.toString(),ipAddress))`。
- 测试：`"8.8.8.8 -f"` / 负数包数 → 拒绝或钳制。

### lib-network

**H6. result() 吞 CancellationException** — `http/generic/ResultExt.kt:55-58`、`http/net/ResultExt.kt:47-50`｜破坏性：否
- 改法：`getOrElse`/catch 内 `if (err is CancellationException) throw err` 后再分类包装。
- 测试（`runTest`）：在 `withContext` 挂起期间取消作用域 → 抛 `CancellationException`，不返回 Failure。

### draw-on-screen（`FingerPaintView.kt`）

**H7. undo() 计数越界** — `342-347`｜破坏性：否
- 改法：`if (paths.isNotEmpty()) { paths.removeAt(paths.lastIndex); countDrawn-- }`，并 `countDrawn = countDrawn.coerceAtLeast(0)`。
- 测试：空 `paths` 连续 `undo()` → `countDrawn` 不为负（抽出计数逻辑做纯函数单测，或 Robolectric）。

**H8. onDraw() 吞所有 Throwable** — `296-310`｜破坏性：否（跨模块反模式）
- 改法：收窄捕获范围；失败**按 §0.3.2 处理**（draw-on-screen 无项目 log 依赖 —— 用 `android.util.Log` / handler / 上抛，不新增 log 依赖），不静默。
- 测试：注入抛异常路径 → 有日志/可观测（或收窄后异常可传播到测试断言）。

**H9. paths 同步不一致** — `291-312` vs `366-379`｜破坏性：否（行为语义需定）
- 改法（择一）：(a) 文档/断言强制所有变更方法仅主线程；或 (b) `onDraw` 开头 `val snapshot = paths.toList()` 后遍历快照，变更方在单锁下整体替换列表引用。推荐 (b) 若确有后台 `drawUserPath` 用例。
- 测试：并发 `drawUserPath` + 渲染快照不抛 CME（对快照策略做单测）。

### lib-compose

**H10. DisposableEffect 未复原导航栏** — `GenericEventHandler.kt:53-59`｜破坏性：否
- 改法：`onDispose { navigationBarVisibility(window, view, isShow = true) }`（或保存进入前状态再复原）。
- 测试：Compose UI 测试或对 `navigationBarVisibility` 调用序列断言（离开组合时以 `isShow=true` 调用）。

**H11. rememberDebounceClickHandler 陈旧闭包** — `DebounceExt.kt:38-55`｜破坏性：否
- 改法：`val current by rememberUpdatedState(onClick)`，记忆的 lambda 内调 `current()`。
- 测试：Compose 测试，重组更换 `onClick` 后点击调用最新版本。

**H12. Modifier.dispatchTouchEvent 陈旧闭包** — `ModifierExt.kt:80-102`｜破坏性：否
- 改法：`pointerInput(onTouch)` 以 `onTouch` 为 key，或循环内引用 `rememberUpdatedState` 值。
- 测试：重组更换 `onTouch` → 事件触发最新回调。

**H13. HorizontalAutoPager 吞 CancellationException** — `HorizontalAutoPager.kt:132-161`｜破坏性：否（跨模块反模式）
- 改法：循环体 `catch (e: CancellationException) { throw e }`，仅吞其它可恢复异常，或不用 runCatching。
- 测试：取消 `LaunchedEffect` key → 协程正常取消（`runTest` 断言）。

### lib-common-kotlin

**H14. formatDecimalSeparator 负数错误** — `NumericExt.kt:78-82,94-98`｜破坏性：否
- 改法：符号单独处理，且**不能用 `abs()`** —— `abs(Long.MIN_VALUE)` 因溢出仍为负、`toString()` 仍带 `-`。改为**从字符串剥离符号**：
  ```kotlin
  fun Long.formatDecimalSeparator(): String {
      val s = this.toString()                       // 负数含前导 '-'
      val negative = s.startsWith("-")
      val digits = (if (negative) s.substring(1) else s)
          .reversed().chunked(3).joinToString(",").reversed()
      return if (negative) "-$digits" else digits
  }
  ```
  Int 版委托 Long 版（`this.toLong().formatDecimalSeparator()`，配合 L-K3 抽公共 helper）。字符串路径天然正确处理 `Long.MIN_VALUE`（`-9223372036854775808`）。
- 测试：`-100`→`-100`、`-123456`→`-123,456`、`1000`→`1,000`、`0`→`0`、`Long.MIN_VALUE`→`-9,223,372,036,854,775,808`（关键边界，验证不残留符号错误）、`Int.MIN_VALUE`。

**H15. round() locale/Infinity 崩溃** — `FloatExt.kt:10-17`｜破坏性：否
- 改法：`DecimalFormat(pattern, DecimalFormatSymbols(Locale.ENGLISH))` 或改用 `BigDecimal(this).setScale(precision, RoundingMode.HALF_UP).toDouble()`；`NaN`/`Infinity` 提前返回原值。
- 测试：`Locale.setDefault(Locale.GERMANY)` 下 `3.14.round(1)` 不崩溃且正确；`Double.POSITIVE_INFINITY.round()`、`NaN` 不崩溃。

**H16. multiCatch 静默丢弃 + 裸 Throwable** — `ExceptionExt.kt:34-50,42`｜破坏性：行为变更（签名不破坏）
- 改法：`catchBlock` **保持可空**（不破坏签名），但**默认行为由静默丢弃改为上抛** —— `catchBlock == null` 时 `throw e`，不再 `catchBlock?.invoke(e)` 后无声吞掉。用 `try { runBlock() } catch (e: Exception)`（不用捕获 `Throwable` 的写法，从而 `Error` 天然不被捕获），并在分类前先 `if (e is CancellationException) throw e`。lib-common-kotlin 无 log 依赖，**不引入 log**（见 §0.3.2）。
- 测试：匹配异常且无 handler → **上抛**（`catchBlock` 可空路径存在，测试合法）；提供 handler → handler 被调用且异常不外泄；`CancellationException` 透传；`Error`（如 `StackOverflowError`）不被吞。

### lib-json（`JsonExt.kt`）

**H17. 吞 Throwable（无日志）** — `60,74-79,94-99`｜破坏性：否（跨模块反模式）
- 改法：lib-json **无 log 依赖**（见 §0.3.2），**不引入 log**。注意 `runCatching` **固定捕获 `Throwable`、无法收窄** —— 要不吞 `Error` 必须改用显式 `try { ... } catch (e: Exception) { if (e is CancellationException) throw e; null/"" }`（`Exception` 不含 `Error`；`CancellationException` 先 rethrow）。如需可观测失败，提供**可选的 `onError: (Throwable) -> Unit` 参数**（默认空实现）由调用方注入，而非硬编码 log。
- 测试：畸形 JSON → 返回 null；`CancellationException` 透传不被吞；注入 `onError` → 被调用。

**H18. toObject<T>() 泛型擦除** — `JsonExt.kt:74-79`｜破坏性：行为变更
- 改法：reified 重载内 `val type = object : TypeToken<T>() {}.type` 委托 `toObject(type)`。
- 测试：`"""[{"a":1}]""".toObject<List<Foo>>()` 元素为 `Foo` 而非 `LinkedTreeMap`。

### lib-compress

**H19. decompress() 无界（解压炸弹）** — `FlaterExt.kt:20-34`｜破坏性：API 增强（兼容）
- 改法：新增 `maxOutputSize: Long = DEFAULT_MAX`（合理默认，如 64MiB），累计输出超限即**显式 `throw IOException(...)`**（勿用 `require`/`check`，那会抛 `IllegalArgumentException`/`IllegalStateException`，与"解压/IO 失败"语义不符）：
  ```kotlin
  fun ByteArray.decompress(bufferSize: Int = 8 shl 10, maxOutputSize: Long = 64L shl 20): ByteArray {
      var total = 0L
      // while 循环内：
      //   total += readLen
      //   if (total > maxOutputSize) throw IOException("Decompressed size exceeds limit: $maxOutputSize")
  }
  ```
- 测试：构造高压缩比小输入 + 低 `maxOutputSize` → 抛出且不 OOM；正常数据往返一致（回归）。

---

## Phase 4 — MEDIUM（25 项）

> 改法多为局部；除注明外不破坏 API。每项建议至少一个针对性测试或回归确认。

### lib-common-android
| ID | 文件:行 | 改法 | 测试 |
|----|---------|------|------|
| M-A1 | ToastExt:320 / ResourcesExt:59 / DeviceUtil:262-264 / ForegroundComponent:142 / TouchHelper:68,89,105,122 | 替换 `printStackTrace()`，**按 §0.3.2 处理**：用现有 `android.util.Log` / 可注入 handler / 上抛，**不新增项目 log 依赖** | 冒烟：失败路径可观测、不静默 |
| M-A2 | ResourcesExt:35-48 vs 50-61 | 统一错误处理策略（两者一致，优先返回类型化结果/`Result`） | I/O 失败两函数行为一致 |
| M-A3 | DeviceExt:395-399 | `getImei` 反射失败加 debug 日志；注释隐藏 API 风险 | 反射失败返回 null 且有日志 |
| M-A4 | DeviceExt:685 | `display?.rotation ?: windowManager.defaultDisplay.rotation` | display 为 null 时回退不崩溃 |

### lib-network
| ID | 文件:行 | 改法 | 测试 |
|----|---------|------|------|
| M-N1 | HttpLoggingInterceptor:148-160,211-224 | 加可配置脱敏头集合，默认掩盖 `Authorization`/`Cookie`/`Set-Cookie` | 敏感头被掩码 |
| M-N2 | ApiException:3 | 删除无用误导 `import android.R.id.message` | 编译通过（ktlint no-unused-imports） |
| M-N3 | SslUtils:100,108 | `certificateInputStream`/`hostnames` 加 `@Volatile` 或封装为一次性不可变配置 | 无（并发可见性，代码审查确认） |
| M-N4 | SslUtils:110-114 | `val names = requireNotNull(hostnames){...}; names.contains(hostname)` | hostname 匹配/不匹配正确 |

### draw-on-screen（`FingerPaintView.kt`）
| ID | 行 | 改法 | 测试 |
|----|----|------|------|
| M-D1 | 114-133 | `init` runCatching 加 `onFailure`（**按 §0.3.2**：`android.util.Log`/handler/上抛，不新增 log 依赖） | 坏属性 → 可观测、不静默跳过 |
| M-D2 | 150-157 | 新 Bitmap 前 `brushBitmap?.recycle()` | 多次 resize 不泄漏（Robolectric/审查） |
| M-D3 | 150-157 | `if (w<=0||h<=0) return` | 0 尺寸不崩溃 |
| M-D4 | 291-312 | 已提交 stroke 缓存到持久 bitmap，每帧只叠加进行中 path | 绘制结果不变（视觉回归/审查） |
| M-D5 | 375,381 | 签名 `MutableList`→`List`（**API 破坏**），内部 `toMutableList()` | 调用方编译 + 行为回归 |
| M-D6 | 204-226 | 加 `ACTION_CANCEL` 分支（调 `handleTouchEnd()` 或丢弃进行中 path） | 取消手势后无残留进行中 path |

### lib-compose
| ID | 文件:行 | 改法 | 测试 |
|----|---------|------|------|
| M-C1 | AppNavigation:40 | `navController` 改 `private`/`protected`，只暴露导航方法 | 编译 + 调用方迁移 |
| M-C2 | UtilExt:34-35 | `SimpleDateFormat` → `DateTimeFormatter`（线程安全）或 `ThreadLocal` | 并发格式化正确 |
| M-C3 | CustomOutlinedTextField:51-57 / SearchBar:62-69 | `var`+重赋值 → `val`+`if/when`/`Modifier.then` | 渲染回归 |
| M-C4 | GenericEventHandler:64 | KDoc 要求 `events` 为稳定引用（如需可用 `rememberUpdatedState` 兜底） | 文档；调用方审查 |

### lib-common-kotlin
| ID | 文件:行 | 改法 | 测试 |
|----|---------|------|------|
| M-K1 | ExceptionExt:42 | 收窄到 `Exception` 或先 rethrow `CancellationException`/`Error`（与 H16 一并） | 见 H16 |
| M-K2 | ReadableByteExt:153 | `format(...)` 传 `Locale.ENGLISH` | 逗号 locale 下输出用 `.` |
| M-K3 | FloatExt:11 | `require(precision >= 0){...}` | 负 precision 抛清晰异常 |
| M-K4 | SingletonHolder:30 / SingletonHolder2:33 | `creator!!` → `checkNotNull(creator){...}` | 现有单例测试回归 |

### lib-json / lib-compress
| ID | 文件:行 | 改法 | 测试 |
|----|---------|------|------|
| M-J1 | JsonExt:45-58 | `gson` 改 `by lazy` 单例 | 序列化行为回归 |
| M-J2 | JsonExt:94-99 | 去掉 inline runCatching 内 `return`，末表达式返回 | 成功/失败路径回归 |
| M-CP1 | FlaterExt:14-34 | KDoc 补异常契约（`ZipException`/`IOException`），或 `Result` 包装 | 文档；坏输入行为断言 |

---

## Phase 5 — LOW（12 项）

> 多为风格/清理，通常无需新测试（跑 detekt/ktlint 即可）；破坏性均为否。

| ID | 文件:行 | 改法 |
|----|---------|------|
| L-A1 | ForegroundComponent:92 | `let` 内 `checkRunnable!!` → 用 `it` |
| L-A2 | SmartSize:14-16 | `var size/long/short` → `val` |
| L-A3 | FileExt:17-30 | `InputStream.toFile` 加显式错误处理（与 M-A2 策略一致） |
| L-A4 | DeviceExt（770 行） | 按职责拆分（厂商检测/屏幕几何/方向计算）到多文件（**本轮延后，见 §0.5**） |
| L-C1 | DispatchTouchEvent:24-28 / HorizontalAutoPager:54,68-73,102-114 / GenericEventHandler:33,157-159 | 删除注释/死代码 |
| L-C2 | RippleAnimation:36-41 | 改为 `val circles = remember { listOf(Animatable(0f), Animatable(0f)) }`（**去掉内部两个 `remember`**，勿在 remember lambda 内再调 composable remember） |
| L-K1 | CalendarExt:12-27 | 负时长 `require(this>=0)` 或钳制为 0（含契约文档） |
| L-K2 | NumericExt:61-64 | 修正 `getRatio` 漂移注释 |
| L-K3 | NumericExt:78-82,94-98 | 抽私有 helper 消除 Int/Long 重复（配合 H14） |
| L-CP1 | FlaterExt:27 | 删除死代码注释 |
| L-D1 | FingerPaintView:295,311 | 删除无效 `save()`/`restore()` |
| L-D2 | FingerPaintView:204-226 | 加 `performClick()` 调用或注释/`@Suppress("ClickableViewAccessibility")` |

---

## 追踪清单（勾选用）

- [x] Phase 1：C1 / C2 / C3
- [x] Phase 2：H6 / H8 / H13 / H16 / H17 + H5 / H19（反模式专项）
- [x] Phase 3：H1 / H2 / H3 / H4 / H7 / H9 / H10 / H11 / H12 / H14 / H15 / H18
- [x] Phase 4：M-A1..4 / M-N1..4 / M-D1..3,D5,D6 / M-C2..4 / M-K1..4 / M-J1..2 / M-CP1（**M-C1、M-D4 延后见 §0.5**）
- [x] Phase 5：L-A1..3 / L-C1..2 / L-K1..3 / L-CP1 / L-D1..2（**L-A4 延后见 §0.5**）
- [ ] 破坏性变更（C3/H16/H18/H19/M-D5）写入 CHANGELOG + 迁移指南（发版前处理）
- [ ] 本机 `./gradlew staticCheck` 全绿（统一验证）

### 整改进度小结（截至 2026-08-03）

- **实施完成**：59 项发现中 **56 项已实施并提交**，3 项经决策延后（M-C1 / M-D4 / L-A4，见 §0.5）。
- **提交分布**（`fix/multi-module-remediation` 分支）：
  - Phase 1（C1/C2/C3）+ Phase 2（反模式专项）：早前逐条提交（见 `git log` 中 `fix(...C1)`/`fix(multi-module) Phase 2 batch 1..2`）。
  - Phase 3+4+5：按模块归并为 7 个提交 —— `fix(lib-common-android)` / `fix(lib-common-kotlin)` / `fix(lib-json)` / `fix(lib-compose)` / `fix(lib-network)` / `docs(lib-compress)` / `fix(draw-on-screen)`。
- **待办**：①本机 `staticCheck`（test+detekt+ktlint）统一验证；②发版前将破坏性变更（C3 TLS、H16、H18 泛型语义、H19、M-D5 `MutableList→List`）写入 CHANGELOG / 迁移指南；③择机处理 3 项延后。

### 评审后续修正（2026-08-04）

在 Codex 代码评审后又落了一轮修正（提交见 `fix/multi-module-remediation` 分支）：

- **Codex 评审后续**（`fbe651727`）—— 修了两个原 59 项 findings 未覆盖的真 bug：
  - `FileDocumentUtil.copyFileToInternalStorage`：缓冲区曾按 `min(InputStream.available(), 1MB)` 取值，content provider 合法返回 `0` 时得到 `ByteArray(0)` → `read()` 恒返回 0 → **死循环挂死**；改为固定 8KB 缓冲。
  - `FingerPaintView` `countDrawn` 重构（超出原 H7）：自增绑定到笔画真正终结（`handleTouchEnd`）、全程 `coerceIn(0, paths.size)`，修复"DOWN 落在图像外/`getDrawable()==null` 时不加 path 但 UP 仍自增"导致的**计数漂移 + 污染已提交笔画**。
  - `ShellUtilDrainTest`：`assertTimeoutPreemptively<Pair<String,String>>` 显式类型参（编译修复）。
  - `HorizontalAutoPager`：空 catch 改为 `com.leovp.log.base.e(...)`（lib-compose 本就 `api(projects.log)`，与模块约定一致）。
- **AESUtil API 改名**（`714c2f2fb` + `c8102c445`）：`decryptStrict(...)` → `decrypt(...)`（严格 AES-GCM 成为默认名）、旧 `decrypt(…useSHA512)` → `decryptLegacy(...)`（宽松、自动识别新旧格式）；并移除 `decryptLegacy` 上会把旧 CBC 密文调用方误迁到严格 `decrypt()` 的 `@Deprecated/ReplaceWith` 陷阱。androidbase 三份 cipher 文档已同步。
- **YuvUtil 反弃用**（`714c2f2fb` + `ebe269ca8`）：撤销先前 P3 的 `@Deprecated`，6 个函数保留为公共 API；androidbase changelog/checklist 已同步（方案 B）。
- **范围说明**：以上 AESUtil / YuvUtil 改动使本分支**扩到 androidbase**（原计划仅含 7 个 lib 模块，属评审后追加）。开 PR 时需说明"多模块整改 + androidbase 二次改动"两个主题，或考虑拆分。

---

*本实现文档与设计文档（审查报告）逐项对应；ID 保持一致，便于交叉追踪。所有 `file:line` 为仓库相对路径。*
