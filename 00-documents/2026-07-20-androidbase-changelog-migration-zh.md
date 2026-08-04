# 变更日志与迁移指南 — `androidbase` 安全审查

本次发版汇集 `fix/androidbase-security-review` 分支：对 cipher、媒体、Android 工具层的一轮安全审查与硬化。含对**公共 API 与密文/存储格式的破坏性变更**，因此**必须升主版本号**。

- **格式：** 参考 [Keep a Changelog](https://keepachangelog.com/)。
- **上一版本：** `5.15.8`。
- **目标版本：** _主版本 bump —— 发版时确定（例如 `6.0.0`）。_

---

## [未发布] — 目标主版本

### ⚠️ 破坏性变更

#### `AESUtil` — 密文格式与 API

- **密文格式变更。** 由 `AES/CBC` + 静态零 IV + 4 字节盐 + 1000 次 PBKDF2，改为 **AES-GCM 认证加密**，
  布局 `[version:1][salt:16][iv:12][ciphertext+tag]`，并把 `version‖salt` 绑为 GCM AAD。
  - `version=0x01`：GCM + PBKDF2-HMAC-SHA256 / 600k（API 26+）
  - `version=0x02`：GCM + PBKDF2-HMAC-SHA1 / 1.4M（API 21–25）
  - API 21–25 解密 `version=0x01` 时使用标准 PBKDF2-HMAC-SHA256 fallback，保证 SHA256 密文跨设备可读。
- **单向兼容。** 已弃用的 `decryptLegacy(...)` 兼容入口会读版本字节，旧密文仍可经 legacy 路径解开
  （4 字节盐、零 IV、1000 迭代）。但**本版本加密的数据无法被旧版本库解密**。跨版本共享密文的场景需协调升级。
- **解密 API 迁移。** 严格 AES-GCM 解密入口为 `decrypt(...)`；原兼容回落入口改名为
  `decryptLegacy(...)`，标记 `@Deprecated`，并提供 `ReplaceWith("decrypt(...)")`。只有读取旧 CBC 存量密文时才继续保留 `decryptLegacy(...)`。
- **`useSHA512` 参数** 已从所有 `encrypt(...)` 重载移除。它只保留在 `decryptLegacy(...)` 重载中，并且**仅影响 legacy 解密路径**；新 GCM 格式的 KDF 由版本字节决定。
- 移除基于 `SystemClock` 播种的 `generateKeyBySHA512/SHA1`，改为 `generateKey(bits = 256)`
  （`KeyGenerator` + 系统熵）。移除 `@RequiresApi(O)`。

#### `RSAUtil` — 签名与填充

- **`sign` / `verify` 语义变更。** 旧：用 `Cipher` + 私钥的"假签名"，`verify` 返回解密后的 `ByteArray`。
  新：`sign(...)` 用 `Signature("SHA256withRSA")` 产生真实签名（`ByteArray?`）；
  **`verify(encodedPubKey, data, signature)` 返回 `Boolean`**。旧假签名方法**未保留**。
- **加密填充** 改为 `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`，并显式设 `OAEPParameterSpec` 使 MGF1
  掩码哈希为 SHA-256（否则默认 SHA-1）。**旧 PKCS#1 v1.5 密文与新 OAEP 不互通。**
- `MAX_ENCRYPT_LEN` 由 `KEY_SIZE/8 - 11` 改为 `190`（OAEP-SHA256）。分片 encrypt/decrypt 改为
  **按字节**分片（修复多字节 UTF-8 跨分片拆分损坏）。
- 删除未用的 `MAX_DECRYPT_LEN`。

#### `PBKDF2Util` — 默认迭代次数

- **SHA512 / SHA1 重载默认迭代提升**（破坏性：相同口令+盐派生出的密钥变了）：
  - `generateKeyWithSHA512(...)` 默认 `ITERATIONS_SHA512 = 220_000`（原 1000）
  - `generateKeyWithSHA1(...)` 默认 `ITERATIONS_SHA1 = 1_400_000`（原 1000）
- 若外部代码依赖旧的 1000 次默认派生结果，需显式传 `iterations = 1000`（或 `ITERATIONS_LEGACY`）。

#### 其它公共 API

- **`BluetoothUtil.setPairingConfirmation(...)`**：返回类型 `Unit` → `Boolean`（失败返回 `false`）。
- **`WifiUtil.connectWifi(...)`**：`enc` 默认值由 `WifiEncType.WEP` 改为 `WifiEncType.WPA`。
- **`YuvUtil`**：6 个未用公共函数标 `@Deprecated`
  （`convertYUV420888ToNV21`、`cropYUV420`、`frameMirror`、`generateFromImage`、
  `i420ToRGBABitmap`、`rgbToI420`），计划下个主版本移除。

### 新增

- `AESUtil.decrypt(...)`：**仅**接受新 GCM 格式，认证失败即抛，永不回落 legacy。
  需要 AEAD 保证时使用。新增重载覆盖 `String`/`ByteArray` 密文与 `String`/`ByteArray`/`SecretKey` 密钥组合：
  `decrypt(String, String)`、`decrypt(String, ByteArray)`、`decrypt(String, SecretKey)`、
  `decrypt(ByteArray, String)`、`decrypt(ByteArray, ByteArray)`、`decrypt(ByteArray, SecretKey)`。
- `PBKDF2Util`：`generateKeyWithSHA256(...)` 系列重载，以及常量
  `ITERATIONS_SHA256 = 600_000`、`ITERATIONS_SHA512`、`ITERATIONS_SHA1`、
  `ITERATIONS_LEGACY = 1000`（仅供 legacy 解密显式传入）。
- `PBKDF2Util`：为 API 21–25（JCA provider 缺 `PBKDF2WithHmacSHA256`）内置 PBKDF2-HMAC-SHA256
  fallback（RFC 8018）。移除 `@RequiresApi(O)` —— SHA256 重载在 minSdk 21 上全程可用。
  回落仅在 `NoSuchAlgorithmException` 时触发，其它异常照抛。

### 修复（非破坏性硬化）

- **媒体 / 编解码：** `H264Util`/`H265Util` NALU 起始码运算符优先级 bug；`CodecUtil.findStartCode`
  越界修正；`H265Util.getVps` 复用修好的 `findStartCode`；`YuvUtil.yuvRotate270` Y 平面越界修正 +
  全帧转换函数 `require` 边界校验。
- **编码：** `Base64Ext.fromBase64` 按 UTF-8 解码（与 `toBase64` 对称）。
- **网络：** `ConnectionLiveData` 在线时上报真实 `networkType`；`NetworkMonitor` 用 `quitSafely()`
  取代 `interrupt()`（消除线程泄漏）；`BaseActivity` 异步 DNS 回调前判 `isDestroyed/isFinishing`，
  去 `currentFocus!!`。
- **资源 / 生命周期：** `LeoTextureView` Surface 复用/释放；`PcmToWavUtil` 用 `use{}` 关流；
  `SoundEffectPlayer` 并发集合 + `openFd().use{}`，并改用
  `Collections.newSetFromMap(ConcurrentHashMap())` 代替 `ConcurrentHashMap.newKeySet()`，避免 API 21
  运行时兼容风险；`DeviceSound` 复用单一 `MediaActionSound` + `release()`（消除 native 泄漏）。
- **`CrashHandler`：** previous handler 改为每次 init 的局部 `val`（修复重复初始化的 `StackOverflowError`），
  并把 custom 与 previous handler 调用都包 `runCatching`，避免行为异常的上报器中断链式调用。
- **`BluetoothUtil`：** 5 处反射调用统一 `runCatching`（log + 返回 `false`）。
- **`PhoneCallReceiver`：** 通话状态机收敛到线程安全持有者（monitor 锁）。
- **`CameraUtil.performCrop`：** 补 `FLAG_GRANT_WRITE_URI_PERMISSION` + 通用异常捕获。
  仍输出 `file://`（见"后续待办"）。
- **`AESUtil.deriveKey`：** 去掉 `SDK_INT ≥ O` 守卫，按版本字节忠实还原 KDF，`version=0x01`（SHA256）
  密文在 API 21–25 上也用 SHA256 派生（此前误用 SHA1 → GCM 认证跨设备必失败）。
- **`RSAUtil.encryptStringByFragment`：** 空字符串输入走单块 `encrypt`，修复空输入 encrypt/decrypt 不对称。

### 安全

- AES-GCM AAD 绑定 `version‖salt`；RSA OAEP 强制 MGF1 SHA-256；PBKDF2 迭代硬化；
  `decrypt` 提供无回落的 AEAD。
- `PBKDF2Util` 口令改用 NIO 缓冲编码为 UTF-8，绕开不可擦除的 `String` 中间态，可清零临时密钥材料。

---

## 迁移指南

### 1. RSA `verify` —— 从字节改为布尔

```kotlin
// 旧 —— verify 返回解密后的数据
val payload: ByteArray? = RSAUtil.verify(pubKey, signedData)
if (payload != null) { /* 可信 */ }

// 新 —— sign()/verify() 为真实 RSA 签名
val signature: ByteArray? = RSAUtil.sign(priKey, data)
val ok: Boolean = RSAUtil.verify(pubKey, data, signature!!)
if (ok) { /* 可信 */ }
```

### 2. AES 密文不向后兼容

- 本版本加密的数据**无法**被升级前的客户端读取。若跨 app/库版本共享密文，须同时升级生产方与消费方，
  或对存量数据重新加密。
- 读旧数据仍可通过已弃用的 `decryptLegacy(...)` 自动兼容（版本字节 → legacy 路径）。当必须拒绝一切非认证 GCM 格式时，使用 `decrypt(...)`。
- `encrypt(...)` 不再接受 `useSHA512` 参数；若有命名参数或三参调用，迁移为两参调用。`decryptLegacy(...)`
  可继续传 `useSHA512`，但它只用于读取旧 CBC 数据。

### 3. RSA 密文不向后兼容

- 新 OAEP-SHA256 密文与旧 PKCS#1 v1.5 密文不互通。升级后须对持久化的 RSA 数据重新加密。

### 4. PBKDF2 默认迭代已变

```kotlin
// 要复现旧的 1000 次默认派生结果，需显式传入：
val key = PBKDF2Util.generateKeyWithSHA1(passphrase, salt, PBKDF2Util.ITERATIONS_LEGACY)
```

### 5. 次要 API 签名变化

- `BluetoothUtil.setPairingConfirmation(...)` 现返回 `Boolean` —— 需判断结果。
- `WifiUtil.connectWifi(...)` 默认 WPA —— WEP 网络需显式传 `enc`。
- 下个主版本前迁移掉已 `@Deprecated` 的 `YuvUtil` 函数。

---

## 后续待办（不阻塞发版）

- **`CameraUtil.performCrop`** 仍使用 `file://` crop 输出（未采用 FileProvider，因部分 crop app 对
  `content://` 兼容性差）。API 24+ 在 StrictMode 下可能抛 `FileUriExposedException`（已用通用捕获兜底）。
  建议在真机上对常见 crop app 验证后，再决定是否改用 FileProvider。

## 安全备注

- 此前工作会话中暴露过的 Personal Access Token 应视为泄露，若仍有效**须到
  <https://github.com/settings/tokens> 撤销**。

---

_另见：_
[发版前检查清单](./2026-07-20-androidbase-pre-release-checklist-zh.md) ·
[cipher 安全审查报告](./2026-07-20-androidbase-cipher-security-review-zh.md)
