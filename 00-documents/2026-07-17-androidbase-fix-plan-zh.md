# `androidbase` 修改方案

- **日期：** 2026-07-17
- **配套文档：** [`2026-07-17-androidbase-code-review-zh.md`](./2026-07-17-androidbase-code-review-zh.md)（审查报告）
- **说明：** 本文档为审查发现问题的详细修改方案，仅为方案，不含实际改动。仅保留中文版（按需求作为审查阶段材料）。
- **修订：** 已并入 Codex 二次复核意见（AES-GCM 优先、OWASP 数值订正、CrashHandler/PhoneCallReceiver 措辞、staticCheck 可用性、新增 yuvRotate270 越界）。来源：OWASP Password Storage Cheat Sheet、OWASP Top 10 2025 A04 Cryptographic Failures。

---

## 0. 关键前置：加密格式的向后兼容策略

`AESUtil`/`RSAUtil` 是公共 JitPack API。**任何改动加密格式（IV、盐长、迭代次数、padding、加密算法）都会让旧密文无法解密**。因此 CRITICAL 修复必须配套**密文格式版本化**，否则升级即数据不可读。

**推荐做法**：新密文加 1 字节版本前缀。
- `decrypt` 先读版本字节：无版本 / 旧结构 → 走 `legacy` 解密路径（保留但标 `@Deprecated`，仅用于读旧数据）；`0x01` → 走新安全路径。
- `encrypt` 一律输出新格式。

这样"旧数据能读、新数据安全"。下面每条 CRITICAL 方案都基于此。

---

## 1. CRITICAL（cipher）

### C1+C2 AES 零 IV + 4 字节盐 —— `AESUtil.kt`

> **首选：改用 AES-GCM（authenticated encryption）**，而不仅仅是"CBC + 随机 IV"。OWASP A04(2025) 明确建议使用认证加密，并强调同一 key 下 IV 不得重复。CBC 无完整性保护、易受 padding-oracle 攻击；只改随机 IV 不够。

**首选方案（AES-GCM）**，新格式：`[ver:1][salt:16][iv:12][ciphertext+tag]`
```
CIPHER = "AES/GCM/NoPadding"，tagLen = 128 bit
encrypt:
  1. salt = generateSalt(16)
  2. iv   = generateIv(12)                       // GCM 推荐 12 字节，每次随机、绝不复用
  3. rawKey = PBKDF2Util.generateKeyWithSHA256(secKey.hex, salt, NEW_ITERATIONS)
  4. cipher.init(ENCRYPT_MODE, rawKey, GCMParameterSpec(128, iv))
  5. return byteArrayOf(VERSION_1) + salt + iv + cipher.doFinal(plainData)   // doFinal 已含 tag
decrypt:
  读第 0 字节 == VERSION_1 → 拆 salt/iv/ct，GCMParameterSpec 校验 tag；否则走 legacyDecrypt()
```

**退路方案（若必须保留 CBC）**：随机 IV + **encrypt-then-MAC**（HMAC-SHA256 覆盖 `iv+ct`，附加 MAC 到输出并在解密前先验 MAC）。不可只做随机 IV。

**通用改动：**
- 删除 `DEFAULT_PRE_SALT_LENGTH = 4`，新增 `SALT_LEN = 16`、`VERSION_1 = 0x01`。
- `generateIv()`（368-373）从死代码变为正式调用；移除 291/298 的注释。
- `legacyDecrypt()`：保留旧结构（salt=[0,4)、zero IV、1000 迭代）仅用于解旧密文，标 `@Deprecated`。
- 更新所有 KDoc 里"salt prefix of 4 bytes"的描述。

### C3 PBKDF2 迭代 1000 太低 —— `PBKDF2Util.kt:39`

- `DEFAULT_ITERATIONS = 1000` → 提高到当前 OWASP 推荐（Password Storage Cheat Sheet，2025）：
  - **PBKDF2-HMAC-SHA256：600,000**
  - **PBKDF2-HMAC-SHA512：220,000**
  - PBKDF2-HMAC-SHA1：1,400,000（仅遗留场景）
- **注意 minSdk 21 老设备性能**：以上为现代基准。落地前做一次基准，目标单次派生 >100ms（类自身文档 259-261 行的要求）；若老设备过慢，可在"安全下限"内取折中值并记录取舍理由。
- 迭代次数是密文格式一部分：新格式固定用 `NEW_ITERATIONS` 常量，legacy 路径仍传 1000。`AESUtil` 调派生函数要显式传迭代数，别用默认。
- 顺带修 `outputKeyLengthInBits` 默认值语义错误：`DEFAULT_SALT_LENGTH shl 3`（用盐常量算密钥长度，数值凑巧=256）→ 新增独立 `DEFAULT_KEY_LENGTH_BITS = 256`。
- 若首选 GCM，需补一个 `generateKeyWithSHA256` 变体（当前只有 SHA1/SHA512）。
- **⚠️ minSdk 21 provider 可用性（落地前必须确认）**：`PBKDF2WithHmacSHA256` 需 API 26+（这也是现有 SHA512 分支标 `@RequiresApi(O)` 的原因），API 21–25 仅保证 `PBKDF2WithHmacSHA1`。因此"新 AES-GCM 格式默认用 SHA256 派生"在老设备上会不可用。落地前必须明确 API 21–25 的派生算法策略（例如：老设备回落到 SHA1 高迭代、或引入自带 PBKDF2 实现的 provider），否则新格式在老设备直接失效。

### C4 `generateKey()` 用开机时长做种 —— `AESUtil.kt:351-364`

```
fun generateKey(bits: Int = 256): SecretKey =
    KeyGenerator.getInstance("AES").apply { init(bits) }.generateKey()
```
- 删除基于 `SystemClock.elapsedRealtimeNanos()` 的 `generateKeyBySHA512/SHA1`。若保留 API 形态，内部改用 `SecureRandom().nextBytes(...)`。
- `@RequiresApi(O)` 可去掉（`KeyGenerator("AES")` 全版本可用）。

### C5 RSA 隐式 PKCS1v1.5 —— `RSAUtil.kt`

⚠️ **陷阱**：当前 `KeyFactory.getInstance(CIPHER_TRANSFORMATION)` 恰因 `CIPHER_TRANSFORMATION="RSA"` 才能工作。改 transformation 后 `KeyFactory` 会抛异常。必须拆两个常量：
```
private const val KEY_ALGORITHM = "RSA"                                            // 给 KeyFactory
private const val CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"  // 给 Cipher
```
- 全部 `KeyFactory.getInstance(...)`（101,124,168,191）改用 `KEY_ALGORITHM`。
- `MAX_ENCRYPT_LEN`：`KEY_SIZE/8 - 11` → OAEP-SHA256 的 `KEY_SIZE/8 - 2*32 - 2 = 190`；`MAX_DECRYPT_LEN` 保持 256。
- `encryptStringByFragment` 应对 `ByteArray` 按**字节**分片（多字节字符会超限），而非 `substring` 按字符。

### C6 `sign/verify` 不是真签名 —— `RSAUtil.kt:147-194`

**API 破坏性变更**（`verify` 语义从"解密返回数据"改为"验签返回布尔"）：
```
fun sign(encodedPriKey: ByteArray, data: ByteArray): ByteArray? = runCatching {
    val priKey = KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(encodedPriKey))
    Signature.getInstance("SHA256withRSA").run { initSign(priKey); update(data); sign() }
}.getOrNull()

fun verify(encodedPubKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean = runCatching {
    val pubKey = KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(encodedPubKey))
    Signature.getInstance("SHA256withRSA").run { initVerify(pubKey); update(data); verify(signature) }
}.getOrDefault(false)
```
- 移除用 `Cipher.ENCRYPT_MODE + 私钥` 的假签名。旧 API 若保留，标 `@Deprecated` 并改名 `rawRsaEncryptWithPrivateKey`。

> 以上 6 条完成后按 `code-review.md` 交 **security-reviewer** 复核再发版。

---

## 2. HIGH（14 + 1 补充）

### 媒体正确性（B）
- **NALU 起始码优先级 bug** `H264Util.kt:168` / `H265Util.kt:403`：`b0!=0 || b1!=0 && b2!=0 || b3!=1` → 全 `||`：`b0!=0 || b1!=0 || b2!=0 || b3!=1`。两处同改。
- **`CodecUtil.findStartCode:159`**：`if (offSet < 0 || data.size < 4)` → `if (offSet < 0 || offSet + 4 > data.size)`。
- **`H265Util.getVps:224`**：循环上界 `4 until data.size` → `4 until data.size - 3`，或复用修好的 `findStartCode`。
- **`YuvUtil` 系列**：每个 public 转换函数入口加 `require(data.size >= expectedSize) { "..." }`；或返回 `null`/`Result` 而非放任 `AIOOBE`。
- **【补充，Codex 发现，纳入 P1】`YuvUtil.yuvRotate270()`**：`for (j in width downTo 1)` 从 `j == width` 起索引，存在越界风险；修 YuvUtil 时一并处理（改用 `width - 1 downTo 0` 或校正索引）。

### 资源/生命周期泄漏（B+C）
- **`NetworkMonitor.kt:156`**：`monitorThread.interrupt()` → `monitorThread.quitSafely()`（API 18+）。
- **`BaseActivity.kt:239`**：异步 DNS 回调创建 `NetworkMonitor` 前判 `if (isDestroyed || isFinishing) return`；更优用 `lifecycleScope` 发起随生命周期取消。
- **`LeoTextureView.kt:66`**：复用 `mySurfaceTexture` 时用同一实例构造 `Surface`；覆盖 `this.surface` 前先 `release()` 旧值；消费后置 `null`。
- **`PcmToWavUtil.kt:168`**：`FileInputStream(src).use { copy(it, encoded) }`。

### 正确性（C）
- **`ConnectionLiveData.kt:100`**：`type = NetworkUtil.TYPE_OFFLINE` → `type = networkType`。
- **`Base64Ext.kt:51`**：`toString(Charset.forName("US-ASCII"))` → `toString(Charsets.UTF_8)`。
- **`CrashHandler.kt:12`（措辞订正）**：现状是——传入 custom handler 时**直接替换**默认 handler（保存到 `defaultExceptionHandler` 字段却从不读取/链式调用）；不传参时等价空操作。修复：改为 wrapper，保存 `previous = Thread.getDefaultUncaughtExceptionHandler()`，自定义处理后**链式调用** `previous?.uncaughtException(t, e)`，避免吞掉第三方（Crashlytics/Bugsnag）上报。

### 安全/健壮（A）
- **`BluetoothUtil.kt:57-119`**：5 处反射调用统一包 `runCatching { }`（对齐 `setPin` 写法），失败 `log` 并返回 `false`。
- **`PhoneCallReceiver.kt:121`（方案订正）**：不要机械地把 `companion object` 状态直接改成实例属性。它是 `BroadcastReceiver`，若由系统/manifest 为每次广播创建**不同实例**，纯实例状态会丢失跨广播的通话状态。应**重新设计状态归属与同步**：例如把会话状态收敛到一个独立的、线程安全的持有者（单例 + 同步，或由外部注入的状态管理器），既避免多实例串扰，又不丢跨广播状态。
- **`AESUtil.kt:292`**：`useSHA512` 在 API<26 降级 SHA-1 时用 `log` 的 `w` 级记录一次。

---

## 3. MEDIUM / LOW（简要方向）

- `WifiUtil.kt:43` 默认 `WifiEncType.WEP` → 改为必填参数或默认 `WPA`。
- `RSAUtil` 各 `runCatching{}.getOrNull()` 增加 `log`（不泄密）后再返回 null。
- `SoundEffectPlayer` 集合换 `ConcurrentHashMap`/`synchronizedSet`；`openFd().use{}`。
- `CameraUtil.performCrop`：**⚠️ 不要机械照搬 `takePhoto()` 直接换 `FileProvider`**——原代码明确注释 crop 输出"不能用 FileProvider"（部分 crop app 对 `content://` 输出 URI 兼容性差）。应单独验证目标 crop app 兼容性：至少补 `FLAG_GRANT_WRITE_URI_PERMISSION`，并在真机上对常见 crop app 做兼容测试后再决定实现方式；无法验证时保留现状并记录 `FileUriExposedException` 风险。
- `BaseActivity.dispatchTouchEvent` `currentFocus!!` → `currentFocus?.let{}`。
- H264/H265 `catch` 改 `log.e(TAG, "...", e)` 保留堆栈。
- `H265Util` getSps/getPps/getSei 抽 `extractNalu(data, matcher)`；`YuvUtil.kt` 删 ~330 行死代码并按职责拆文件。
- LOW：`KClass<out Activity>` 约束、剪贴板 `removeCallbacks`、删 `TrafficStatHelper` 失效方法、去多余 self-import、`!!`→`checkNotNull`、`DeviceSound` 单例复用+release。

---

## 4. 落地顺序与测试

1. **P0**：cipher 三文件 + 格式版本化（首选 AES-GCM）→ 补 round-trip 单测（新格式加密→解密、legacy 密文仍可解、GCM tag 篡改应失败、RSA 签名验签）→ security-reviewer 复核。
2. **P1**：媒体项（含 NALU 优先级、findStartCode/getVps 越界、YuvUtil 边界 + **yuvRotate270 越界**）+ Base64/ConnectionLiveData → 针对畸变/截断字节流、非 ASCII round-trip 加单测。
3. **P2**：资源泄漏 5 项。
4. **P3**：MEDIUM/LOW 清理。

**测试命令（需先复核）：**
- 模块级单测：`./gradlew :androidbase:testDebugUnitTest`
- ⚠️ `./gradlew staticCheck` **需先确认可用**：当前根 `build.gradle.kts` 的 `staticCheck` 依赖 `app:assembleAndroidTest`，而 `settings.gradle.kts` 中并无 `:app` 模块，可能跑不通。落地前应核对并修正 `staticCheck` 的依赖（或改为存在的模块），否则以模块级 detekt/ktlint/test 命令替代。

> **兼容性提示**：C1–C3、C5–C6 均为破坏性变更，应在版本号上体现（次/主版本 bump），并在 CHANGELOG/迁移说明中标注旧密文与旧 `sign/verify` 的处理方式。
