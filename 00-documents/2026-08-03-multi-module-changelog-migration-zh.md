# 变更日志与迁移指南 — 多模块代码审查整改

本次发版汇集 `fix/multi-module-remediation` 分支：对 `draw-on-screen`、`lib-common-android`、
`lib-compose`、`lib-common-kotlin`、`lib-compress`、`lib-json`、`lib-network`（及跨模块的 `http`）
一轮安全 / 正确性审查与硬化。含**默认网络行为、反序列化语义、异常传播与个别公共 API 的破坏性变更**，
因此**建议升主版本号**。

- **格式：** 参考 [Keep a Changelog](https://keepachangelog.com/)。
- **上一版本：** `5.15.8`（`gradle/libs.versions.toml` 的 `leo-version`）。
- **目标版本：** _主版本 bump —— 发版时确定（例如 `6.0.0`）。若与 `androidbase` 安全审查同批发布，合入同一主版本。_
- **对应文档：** 审查报告 [`2026-07-27-multi-module-code-review-zh.md`](./2026-07-27-multi-module-code-review-zh.md)、
  实现方案 [`2026-07-27-multi-module-remediation-plan-zh.md`](./2026-07-27-multi-module-remediation-plan-zh.md)。
  ID（C/H/M/L）在三份文档间保持一致。

---

## [未发布] — 目标主版本

### ⚠️ 破坏性变更

#### 网络 — TLS 主机名校验默认 fail-closed（C3，跨模块）

- **默认行为由 trust-all 改为平台校验。** 此前 `http/.../retrofit/base/BaseHttpRequest.kt` 在
  `certificateInputStream == null` 分支默认接入 `SslUtils.doNotVerifier`（**恒真** `HostnameVerifier`，
  等于关闭主机名校验）。现改为 **fail-closed**：未配置自定义证书时使用**平台默认 `HostnameVerifier`**，
  正常校验证书链与主机名。
- **影响：** 之前"能连上"的自签名 / 主机名不匹配 / 抓包代理（Charles、mitmproxy 等）场景，
  升级后将因校验失败而**握手中断**。
- `SslUtils.doNotVerifier` **仍保留**为公开原语（供测试或调用方显式选择），但 KDoc 已明确标注
  **"仅测试用途，生产禁用"**，不再作为任何默认路径接入。

#### lib-json — `toObject<T>()` 泛型反序列化语义修正（H18）

- **泛型元素类型不再被擦除。** 此前 `String.toObject<T>()`（reified 重载）内部用
  `gson.fromJson(this, T::class.java)`，泛型参数被擦除：`toObject<List<Foo>>()` 实际得到的元素是
  `LinkedTreeMap` 而非 `Foo`，后续对元素的强转 / 访问在运行时才炸。现改用
  `object : TypeToken<T>() {}.type`，元素类型正确保留。
- **JSON 转换失败现在由工具函数内部记录日志。** `Any?.toJsonString()` 与 `String?.toObject()` 系列仍只负责
  JSON 转换：失败时打印异常日志并返回默认值（`""` / `null`），不向调用方暴露 `onError` 参数。
  `CancellationException` 仍会透传，不记录为 JSON 转换错误；`Error` 不再被捕获。
- **依赖变化：** `lib-json` 为内部错误日志接入项目 `log` 模块。
- **影响：** 依赖旧（错误）行为的代码极少见；但若某处**刻意**接收 `LinkedTreeMap` 结构，需改为面向
  真实类型。绝大多数调用点是从"运行时崩溃"变为"正确工作"。

#### lib-compress — `ByteArray.decompress()` 增加解压上限（H19）

- **新增 `maxOutputSize: Long` 参数，默认 `64 MiB`（`64L shl 20`）。** 累计解压输出超过上限即
  **抛出 `IOException`**（而非耗尽堆内存），用于防御解压炸弹。
- 签名：`fun ByteArray.decompress(bufferSize: Int = 8 shl 10, maxOutputSize: Long = 64L shl 20): ByteArray`
  —— **源码兼容**（新增带默认值的尾参数），但**运行时行为变更**：解压结果 > 64 MiB 的合法大数据现在会抛异常。
- **影响：** 需要解压超过 64 MiB 可信数据的调用点，须显式传入更大的 `maxOutputSize`。

#### lib-common-kotlin — `multiCatch` 默认不再静默吞异常（H16）

- **签名不变**（`catchBlock` / `uncaughtBlock` 仍可空），但**默认行为变更**：
  - 匹配到指定异常但**未提供 `catchBlock`** → 现在**上抛**该异常（此前 `catchBlock?.invoke(e)` 后无声吞掉）。
  - 未匹配且**未提供 `uncaughtBlock`** → 现在**上抛**（此前同样被吞）。
  - `CancellationException` **始终透传**，永不吞掉。
  - 捕获范围由 `Throwable` 收窄为 `Exception`，故 `Error`（如 `OutOfMemoryError`/`StackOverflowError`）
    **不再被捕获**。
- **影响：** 之前依赖 `multiCatch` "静默吞掉一切"的代码，升级后可能抛出此前被隐藏的异常。
  需要保留"吞掉"语义的调用点，必须显式传入 `catchBlock = { }` / `uncaughtBlock = { }`。

#### draw-on-screen — `FingerPaintView` 集合类型收窄（M-D5）

- `getPaths()` 返回类型 `MutableList<Pair<Path, Paint>>` → **`List<Pair<Path, Paint>>`**。
- `drawUserPath(userPath: ...)` 形参 `MutableList<...>` → **`List<...>`**（放宽，`MutableList` 是 `List` 子类型，
  传入侧源码兼容）。
- **影响：** 对 `getPaths()` 返回值做**写操作**（`.add` / `.clear` / `.set` 等）的调用点会**编译失败**。
  已知直接引用者为 3 个 demo Activity（FloatView / ScreenShareClient / ScreenShareMaster）。

### 行为变更（非签名破坏，但可能暴露此前被隐藏的异常）

以下为跨模块"停止吞异常 / 透传取消"的一批修复（H6 / H8 / H13 / H17 及相关 MEDIUM/LOW）。签名不变，
但**语义上会让此前被静默吞掉的错误重新浮现**，属可观察行为变化：

- **协程取消透传：** `runCatching` / `catch` 不再吞掉 `CancellationException` —— 结构化并发下取消能正确传播
  （`HorizontalAutoPager` 自动轮播、各 `LaunchedEffect` 等）。
- **不再吞 `Error`：** 多处由捕获 `Throwable` 收窄为 `Exception`，`OutOfMemoryError` 等严重错误不再被静默掩盖。
- **失败可观测：** JSON 转换失败现在经 `LogContext` 记录并返回默认值；`FingerPaintView.onDraw`
  渲染失败、`TouchHelper`/`ForegroundComponent`/`DeviceUtil` 等处的异常现在经 `android.util.Log` 记录
  （这些模块无项目 `log` 依赖，遵循日志策略 §0.3.2）而非 `printStackTrace()` 后丢弃。
- **HTTP 日志脱敏（M-N1）：** `HttpLoggingInterceptor` 现默认对 `Authorization` / `Cookie` / `Set-Cookie` /
  `Proxy-Authorization` 头做脱敏（`██ (redacted)`）。若你的日志分析依赖这些头的明文，需通过
  `HttpLoggingInterceptor.redactedHeaderNames` 调整集合。

### 修复 / 硬化（非破坏，摘要）

- **安全：** 路径穿越硬化（C1，`FileDocumentUtil`）、Shell 参数校验（C2，`ShellUtil` + ping 目标校验）。
- **并发正确性：** `ShellUtil` 并发排空 stdout/stderr 避免管道缓冲死锁（H1）；`FingerPaintView` 路径读写加锁 +
  快照渲染（H7/H9）。
- **UI 正确性：** 导航栏在离开组合时恢复（H10）；debounce / dispatchTouch 修复陈旧闭包（H11/H12）；
  `MotionEvent` 按 pointer index 取坐标（H2）。
- **数值 / 格式：** 千分位负数与 `Long.MIN_VALUE`（H14）、`round()` 的 locale/非有限值（H15）、字节格式 locale（M-K2）。

---

## 迁移指南

> 仅列**需要动作**的场景。若你未使用下述 API / 未依赖旧默认行为，则无需改动。

### 1. 网络：允许非校验连接（仅开发 / 抓包）（C3）

**旧：** 未配置证书时自动跳过主机名校验，任何自签名 / 代理都能连。

**新：** 默认平台校验，握手会对非法证书失败。

**如何迁移：**
- **生产：** 无需动作，这是期望行为；如需固定证书用 `CertificatePinner` 或为 `SslUtils` 配置
  `certificateInputStream` + `hostnames`。
- **开发 / 抓包：** 仅在 debug 构建中显式选择放宽校验，**切勿**进入 release。例如显式传入
  `SslUtils.doNotVerifier`（已标注仅测试用途），并用 `BuildConfig.DEBUG` 之类的开关隔离。

### 2. lib-json：泛型反序列化（H18）

**旧：** `json.toObject<List<Foo>>()` 元素实为 `LinkedTreeMap`。

**新：** 元素为 `Foo`。

**错误处理：** `toJsonString()` / `toObject()` 的签名保持无错误回调参数；转换失败时函数内部记录日志，
并继续返回 `""` / `null`。`CancellationException` 不是 JSON 转换失败，会继续向上抛出。

**如何迁移：** 通常无需动作（原来多半是 bug）。若某处刻意消费 `LinkedTreeMap`，改为声明真实类型。

### 3. lib-compress：大数据解压（H19）

**旧：** `bytes.decompress()` 无上限。

**新：** 默认上限 64 MiB，超限抛 `IOException`。

**如何迁移：** 已知会解压超过 64 MiB 的**可信**数据时，显式放宽：
```kotlin
val out = bytes.decompress(maxOutputSize = 256L shl 20) // 256 MiB
```

### 4. lib-common-kotlin：`multiCatch`（H16）

**旧：** 未给 `catchBlock` 也会静默吞掉匹配异常。

**新：** 未给 `catchBlock` → 上抛；`CancellationException` 透传；`Error` 不再被捕获。

**如何迁移：** 若确实想吞掉，显式提供空处理块：
```kotlin
multiCatch(
    runBlock = { /* ... */ },
    IllegalArgumentException::class,
    catchBlock = { /* 显式忽略或记录 */ },
    uncaughtBlock = { /* 显式忽略或记录 */ },
)
```

### 5. draw-on-screen：`FingerPaintView.getPaths()`（M-D5）

**旧：** 返回 `MutableList`，可直接写。

**新：** 返回 `List`（只读）。

**如何迁移：** 若需修改，先复制：
```kotlin
val editable = view.getPaths().toMutableList()
editable.add(/* ... */)
view.drawUserPath(editable) // drawUserPath 形参已放宽为 List，传 MutableList 兼容
```

### 6. HTTP 日志脱敏（M-N1）

**旧：** 请求 / 响应头明文打印。

**新：** 敏感头默认脱敏。

**如何迁移：** 如需自定义脱敏集合：
```kotlin
HttpLoggingInterceptor.redactedHeaderNames =
    setOf("authorization", "cookie", "set-cookie", "proxy-authorization", "x-api-key")
```

---

## 发版检查清单

- [ ] `gradle/libs.versions.toml` 的 `leo-version` 主版本 bump（由维护者操作）。
- [ ] 本机 `./gradlew staticCheck` 全绿。
- [ ] 将本文「⚠️ 破坏性变更」并入正式 CHANGELOG / release notes。
- [ ] 通知跨版本共享数据 / 网络配置的下游（尤其 C3、H18、M-D5）。
- [ ] 3 项延后（M-C1 / M-D4 / L-A4）在 release notes 标注为"已知项 / 后续处理"（见实现方案 §0.5）。
