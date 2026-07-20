# `androidbase` 发版前检查清单与迁移说明

- **日期：** 2026-07-20
- **适用范围：** 本次安全审查/修复引入的全部变更（P0–P3 + HIGH + cipher 复核修复）
- **配套文档：**
  - [`2026-07-17-androidbase-code-review-zh.md`](./2026-07-17-androidbase-code-review-zh.md)（原始审查）
  - [`2026-07-17-androidbase-fix-plan-zh.md`](./2026-07-17-androidbase-fix-plan-zh.md)（修改方案）
  - [`2026-07-20-androidbase-cipher-security-review-zh.md`](./2026-07-20-androidbase-cipher-security-review-zh.md)（cipher 复核报告）
- **状态：** `fix/androidbase-security-review` 分支已推送到远端；本轮 Codex 复核后新增修复仍是工作区改动，尚未提交/推送。

---

## 0. 发版前必办清单

| # | 事项 | 状态 | 说明 |
|---|------|------|------|
| 1 | 提交并推送本轮新增修复 | ⏳ 待办 | 当前分支已有远端基线；本轮 Codex 复核修复仍需单独提交/推送 |
| 2 | 主版本号 bump | ⏳ 待办 | 本次含破坏性变更（见 §2），应升主版本 |
| 3 | 编写 CHANGELOG / 迁移说明 | ⏳ 待办 | 可直接引用本文 §2/§3 |
| 4 | 修正 `staticCheck` | ⏳ 待办 | 依赖不存在的 `:app` 模块，见 §5 |
| 5 | 全量单测 + 静态检查 | ✅ 分模块已验 | `:androidbase` compile/detekt/ktlint + cipher/media 单测全绿 |

---

## 1. 推送状态

`fix/androidbase-security-review` 分支上的既有安全修复提交已推送到远端。本轮 Codex 复核后新增的修复（AES 跨 API SHA256 KDF、CrashHandler 重复初始化、RSA 空字符串分片）仍是工作区改动，尚未提交/推送。

后续推送如需重新认证，请注意：

1. 之前用于推送的 PAT 已在会话中暴露、应视为泄露，**须到 https://github.com/settings/tokens 撤销**。
2. 本机存储凭据（leizhiliang）对 `yhz61010/android` **无写权限**。

推送时请提供有效写权限凭据并明确授权。

---

## 2. 破坏性变更（CHANGELOG / 迁移说明）

> 以下变更会影响 JitPack 外部使用方，**必须**在版本说明中标注，并建议升**主版本号**。

### 2.1 `AESUtil` — 加密格式与 API

- **密文格式变更**：由 `AES/CBC` + 静态零 IV + 4 字节盐 + 1000 次 PBKDF2，改为 **AES-GCM 认证加密**，新格式 `[version:1][salt:16][iv:12][ciphertext+tag]`，并把 `version‖salt` 绑为 GCM AAD。
  - `version=0x01`：GCM + PBKDF2-HMAC-SHA256 / 600k（API 26+）
  - `version=0x02`：GCM + PBKDF2-HMAC-SHA1 / 1.4M（API 21–25）
  - API 21–25 解密 `version=0x01` 时使用标准 PBKDF2-HMAC-SHA256 fallback，保证跨设备读取 SHA256 新格式密文。
- **向后兼容**：`decrypt(...)` 读版本字节，旧密文走 `@Deprecated legacyDecrypt`（4 字节盐、零 IV、1000 迭代）仍可解；`encrypt(...)` 一律输出新格式。
- **⚠️ 单向兼容**：**新版本加密的数据无法被旧版本库解密**。跨版本共享密文的场景需协调升级。
- **新增** `decryptStrict(cipherBytes, secKey)`：只接受新 GCM 格式、认证失败即抛，永不回落 legacy（需要 AEAD 保证时用它）。
- **`generateKey`**：移除基于 `SystemClock` 播种的 `generateKeyBySHA512/SHA1`，改为 `generateKey(bits=256)`（`KeyGenerator` + 系统熵）。移除了 `@RequiresApi(O)`。
- **`useSHA512` 参数**：现**仅影响 legacy 解密路径**，对新 GCM 格式无效（其 KDF 由版本字节决定）。

### 2.2 `RSAUtil` — 填充、签名语义

- **`sign/verify` 语义变更（破坏性）**：
  - 旧：用 `Cipher + 私钥` 的"假签名"，`verify` 返回解密后的 `ByteArray`。
  - 新：`sign(...)` 用 `Signature("SHA256withRSA")` 产生真实签名（`ByteArray?`）；**`verify(encodedPubKey, data, signature)` 返回 `Boolean`**。
  - **迁移**：所有 `verify` 调用点需从"取返回数据"改为"判断布尔"。旧假签名方法**未保留**。
- **加密填充**：`RSA/ECB/OAEPWithSHA-256AndMGF1Padding`，并显式设 `OAEPParameterSpec` 使 MGF1 掩码哈希为 SHA-256（否则默认 SHA-1）。**旧 PKCS1 v1.5 密文与新 OAEP 不互通**。
- `MAX_ENCRYPT_LEN` 由 `KEY_SIZE/8 - 11` 改为 OAEP-SHA256 的 `190`；分片 encrypt/decrypt 改为**按字节**分片（修复多字节 UTF-8 拆分损坏）。
- 删除未用的 `MAX_DECRYPT_LEN`。

### 2.3 `PBKDF2Util` — 迭代次数默认值

- **SHA512/SHA1 公共重载默认迭代提升**（破坏性：相同口令+盐派生出的密钥变了）：
  - `generateKeyWithSHA512(...)` 默认 `ITERATIONS_SHA512 = 220_000`（原 1000）
  - `generateKeyWithSHA1(...)` 默认 `ITERATIONS_SHA1 = 1_400_000`（原 1000）
- 新增常量 `ITERATIONS_SHA256=600_000`、`ITERATIONS_SHA512`、`ITERATIONS_SHA1`、`ITERATIONS_LEGACY=1000`（仅供 legacy 解密显式传入）。
- 新增 `generateKeyWithSHA256(...)` 系列重载（`@RequiresApi(O)`）。
- **迁移**：若外部代码依赖旧的 1000 次默认派生结果，需显式传 `iterations = 1000`（或 `ITERATIONS_LEGACY`）。

### 2.4 其它公共 API

- **`BluetoothUtil.setPairingConfirmation(...)`**：返回类型由 `Unit` 改为 `Boolean`（失败返回 `false`）。
- **`WifiUtil.connectWifi(...)`**：`enc` 默认值由 `WifiEncType.WEP` 改为 `WifiEncType.WPA`。
- **`YuvUtil`**：6 个仓库内 0 引用的公共函数标 `@Deprecated`（`convertYUV420888ToNV21`、`cropYUV420`、`frameMirror`、`generateFromImage`、`i420ToRGBABitmap`、`rgbToI420`），计划下个主版本移除。

---

## 3. 非破坏性修复（行为修正 / 硬化）

| 模块 | 修复 |
|------|------|
| H264Util / H265Util | NALU 起始码校验运算符优先级 bug（`&&`/`||` 误组合）→ 全 `||` |
| CodecUtil.findStartCode | 越界修正：按 `offSet+4` 校验而非 `data.size` |
| H265Util.getVps | 复用修好的 `findStartCode`，消除内联越界 |
| YuvUtil | `yuvRotate270` Y 平面越界修正 + 全帧转换函数入参 `require` 边界校验 |
| Base64Ext.fromBase64 | 解码由 US-ASCII 改 UTF-8，与 `toBase64` 对称 |
| ConnectionLiveData | 在线时上报实际 `networkType`（原误报 TYPE_OFFLINE） |
| NetworkMonitor | HandlerThread `interrupt()` → `quitSafely()`（消除线程泄漏） |
| BaseActivity | 异步 DNS 回调创建 NetworkMonitor 前判 `isDestroyed/isFinishing`；`dispatchTouchEvent` 去 `currentFocus!!` |
| LeoTextureView | Surface 复用/先 release 旧值/消费后置 null |
| PcmToWavUtil | `FileInputStream` 用 `use{}` 关闭 |
| CrashHandler | 改为 wrapper，链式调用 previous handler（不吞第三方上报） |
| BluetoothUtil | 5 处反射调用统一 `runCatching`，失败 log 并返回 false |
| PhoneCallReceiver | 通话状态机收敛到线程安全的独立持有者（monitor 锁），修跨广播/多线程一致性 |
| SoundEffectPlayer | `ConcurrentHashMap`/`newKeySet` + `openFd().use{}` |
| DeviceSound | 复用单一 `MediaActionSound` 实例 + `release()`（消除 native 泄漏） |
| CameraUtil.performCrop | 补 `FLAG_GRANT_WRITE_URI_PERMISSION` + 捕获通用异常；**保留 file:// 输出**（未换 FileProvider，需真机验证，见 §4） |
| H264/H265/RSA | 异常日志保留堆栈（`log.e(TAG, msg, e)`）；RSA runCatching 增加失败日志 |
| cipher | GCM AAD（M2）、RSA OAEP MGF1 显式 SHA-256（M3）、PBKDF2 迭代硬化（H1）、`decryptStrict`（M1） |

---

## 4. 需真机/后续验证的事项

- **`CameraUtil.performCrop`**：仍使用 `Uri.fromFile`（file://）作为 crop 输出。
  - 原因：部分 crop app 对 `content://`（FileProvider）输出兼容性差，故**未机械换 FileProvider**。
  - 风险：API 24+ 在 StrictMode 下向其它 app 传 file:// 可能抛 `FileUriExposedException`（已加通用异常捕获兜底）。
  - **待办**：在真机上对常见 crop app 验证后，再决定是否改用 FileProvider。

---

## 5. `staticCheck` 可用性

根 `build.gradle.kts` 的 `staticCheck` 依赖 `app:assembleAndroidTest`，但 `settings.gradle.kts` 中**无 `:app` 模块**，可能跑不通。

- **建议**：核对并修正 `staticCheck` 依赖（改为存在的模块，或移除该依赖）。
- **临时替代**：使用模块级命令
  ```bash
  ./gradlew :androidbase:testDebugUnitTest
  ./gradlew :androidbase:detekt
  ./gradlew :androidbase:ktlintCheck
  ```
  > 本机构建注意事项见 `.claude/memory/project_build_env.md`（需 `local.properties` + SDK platform-36/build-tools 36）。

---

## 6. 有意保留现状的清理项（低价值/高风险）

| 项 | 决定 | 理由 |
|----|------|------|
| M1 GCM→legacy 回落完整性 gap | 已提供 `decryptStrict` | 默认 `decrypt` 保留兼容回落（已测试的取舍） |
| H265Util `extractNalu` 抽取 | 保留 | 纯装饰性 DRY，不改行为/API，编辑摩擦大 |
| Clipboard `removeCallbacks` | 保留 | 需重写 Android Q 有意的 1s 延迟读取，风险高 |
| YuvUtil 物理删除 + 拆文件 | 改为 `@Deprecated` | 删公共 API 属破坏性；`object` 拆文件对 Kotlin 不自然 |
| LOW-5 RSA 日志含 Throwable | 确认可接受 | log 模块经门控、Logcat 按应用隔离，JCE 异常不含明文 |

---

## 7. 本次相关提交（本地，未推送）

```
f6164d73f refactor(androidbase): deprecate unused public YuvUtil functions (P3)
e3622d29f fix(androidbase): crop write-permission and DeviceSound reuse (P3 LOW)
fe0d55e4a docs: mark all cipher review findings (H1-M3, LOW-1..5) resolved
a6c307f83 feat(cipher): add strict AES-GCM decrypt and document derivation cost
39a0cda11 docs: mark cipher review findings H1/M2/M3/LOW-3/LOW-4 as resolved
a655446ca fix(cipher): address security review findings H1/M2/M3 and LOW cleanups
d6a4373c6 docs: add cipher (P0) security review report
46f050adf fix(androidbase): address MEDIUM review items (P3)
6e6120e65 fix(androidbase): harden Bluetooth reflection and phone-call state (HIGH)
8a6854e96 fix(androidbase): fix resource leaks and crash-handler chaining (P2)
c97254328 style(cipher): reformat RSAUtil.verify to satisfy both ktlint and detekt
5c14a33a6 fix(media): correct NALU parsing, YUV bounds, Base64 and network type (P1)
76a0371f8 fix(cipher): harden AES/RSA per security review P0 (C1-C6)
```
