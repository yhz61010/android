# `androidbase` Cipher（P0）安全复核报告

- **日期：** 2026-07-20
- **复核范围：** P0 加密硬化后的当前代码（`AESUtil.kt`、`RSAUtil.kt`、`PBKDF2Util.kt`），交叉核对引入提交 `76a0371f8` 及配套单测（`AESUtilTest.kt`、`RSAUtilTest.kt`）。
- **配套文档：** [`2026-07-17-androidbase-code-review-zh.md`](./2026-07-17-androidbase-code-review-zh.md)（原始审查）、[`2026-07-17-androidbase-fix-plan-zh.md`](./2026-07-17-androidbase-fix-plan-zh.md)（修改方案）。
- **执行方：** security-reviewer agent。
- **处置决定：** 本轮**保持代码现状，仅记录**。以下为待办项，发版前应逐条评估。

---

## 结论概览

核心 AEAD（AES-GCM）切换、新 GCM 格式的密钥派生接线、真实 RSA 签名实现均判定**正确**。无 CRITICAL，无硬编码密钥，新路径无静态 IV/盐复用，无残留假加密。发现 1 个 HIGH 回归 + 若干 MEDIUM/LOW 硬化缺口。

---

## HIGH

### H1 — `PBKDF2Util` 的 SHA512/SHA1 公共便捷重载默认仍为 1000 迭代

- `generateKeyWithSHA512(...)`（`PBKDF2Util.kt:76-172`）全部重载默认 `iterations = DEFAULT_ITERATIONS`（1000），从未接到 `ITERATIONS_SHA512`（220,000）。
- `generateKeyWithSHA1(...)`（`PBKDF2Util.kt:289-380`）同样默认 1000，未接 `ITERATIONS_SHA1`（1,400,000）。
- 经 `git show 76a0371f8` 确认：硬化提交只把**新增的** `generateKeyWithSHA256` 重载接到 `ITERATIONS_SHA256=600_000`，SHA512/SHA1 的默认未动。
- `PBKDF2Util` 标注 `@Suppress("MemberVisibilityCanBePrivate")`，是**有意公开**的可复用工具。用于**新派生密钥**的便捷重载（如 `generateKeyWithSHA512(plainPassphrase, saltLength=DEFAULT_SALT_LENGTH, ...)`，行 162；SHA1 对应行 370）在调用方不显式覆盖 `iterations` 时，会静默只做 1000 轮，重新引入本次硬化本要消除的暴力破解弱点。
- **`AESUtil` 不受影响**：新 GCM 格式显式传 `ITERATIONS_SHA256`/`ITERATIONS_SHA1`（`AESUtil.kt:339,341`）；`legacyDecrypt`（`AESUtil.kt:411-414`）刻意依赖隐式 1000 以还原旧数据，**这是正确的**。风险仅针对其它/未来 `PBKDF2Util` 公共 API 使用方。

**修复方向（注意 legacy 依赖）：** `legacyDecrypt` 必须继续用 1000。建议新增公共常量 `ITERATIONS_LEGACY = 1000` 供 `legacyDecrypt` 显式传入，然后把 SHA512/SHA1 公共重载默认改为 `ITERATIONS_SHA512`/`ITERATIONS_SHA1`。切勿直接改默认而不先让 legacy 显式传 1000，否则旧密文无法解密。

---

## MEDIUM

### M1 — GCM 认证失败回落 legacy CBC 可能把认证失败伪装成"成功"（返回损坏明文）

`decrypt()`（`AESUtil.kt:372-381`）版本字节匹配时先试 GCM，`runCatching` 捕获**任何**失败后回落 `legacyDecrypt`（PKCS7/CBC）。PKCS7 填充校验对随机/篡改字节有约 1/256 概率误通过，故被篡改/伪造的 version-0x01/0x02 密文偶尔会经 legacy 路径"成功"解成垃圾数据而不抛异常，削弱了"decrypt 成功即数据已认证"的保证。

这是**已测试的既定取舍**：`AESUtilTest.kt:49-60`（"tampered cipher text never decrypts back to the original"）显式接受"抛异常**或**返回非原始字节"为通过。它从不泄露真实明文（机密性 fail-closed），非机密恢复型漏洞，但对不独立校验负载的调用方是真实的完整性/可靠性缺口。建议：(a) 用 AAD 绑定格式元数据（见 M2），仅对长度/结构不符 GCM 格式的输入回落 legacy，而非每次 GCM 认证失败都回落；或 (b) 提供绝不静默回落的严格 decrypt 变体。

### M2 — version 字节与盐未作为 AAD 认证

`Cipher.getInstance(CIPHER_GCM)` 仅用 `GCMParameterSpec` 初始化，无 `updateAAD()` 把 `[version:1][salt:16]` 绑进 GCM tag（`AESUtil.kt:325-329, 383-396`）。当前篡改这些字节仍会间接导致解密失败（错误派生密钥 ⇒ tag 不匹配），故非独立可利用；属潜在硬化缺口。建议 encrypt/decrypt 两侧 `cipher.updateAAD(byteArrayOf(version) + salt)`。

### M3 — RSA OAEP 主哈希（SHA-256）与 MGF1 掩码哈希可能不一致

`CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"`（`RSAUtil.kt:42`）把主 OAEP 摘要设为 SHA-256，但标准 JCA provider 下仅凭该字符串会把 **MGF1** 掩码摘要留在默认 SHA-1；`cipherDoFinal()`（`RSAUtil.kt:193-198`）调用 `cipher.init(opmode, key)` 未传 `OAEPParameterSpec`，从未显式设 `MGF1ParameterSpec.SHA256`。非 OAEP IND-CCA2 证明的已知破坏，但偏离命名暗示的全 SHA-256 配置，且可能与显式绑定双哈希的其它 OAEP 实现互操作失败。建议 encrypt/decrypt 两侧 `cipher.init()` 显式传 `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)`。

> ⚠️ M3 改变密文兼容性：用现字符串加密的旧 OAEP 数据（MGF1=SHA-1）在显式设 MGF1=SHA-256 后将解不开。需版本化或迁移评估。

---

## LOW

- **`AESUtil.kt:323, 391`** — 每次 `encrypt()`/`decrypt()` 都从头跑满强度 PBKDF2（600k/1.4M 轮），不缓存每口令派生密钥。非漏洞，但 CPU/电量成本值得记录；若 API 暴露给攻击者可控调用量（循环解密大量外部小 blob）存在本地 DoS 隐患。
- **`AESUtil.kt`（行 62,77,100,116…）** — `useSHA512` 参数文档标注"encryption 时忽略"，仅影响 deprecated legacy decrypt 路径。功能正常，但易被误读为控制新 GCM 格式；旧 CBC 数据完全迁移后可考虑 deprecate/重命名。
- **`RSAUtil.kt:55`** — `MAX_DECRYPT_LEN` 声明但全文件未使用（死代码）。
- **`RSAUtil.kt:7`** — `import ...RSAUtil.getKeyPair` 是本文件内对自身的冗余 self-import；无害，应清理（ktlint 未用导入类问题）。
- **`RSAUtil.kt:114, 137, 170`** — 错误日志包含原始 `Throwable`。此处 JCE 异常（BadPadding/OAEP/Signature）通常不含明文或密钥字节，风险低；建议确认 `log` 模块在 release 构建不把堆栈转发到低信任 sink。

---

## 已验证正确（无问题）

- IV 生成（`AESUtil.kt:434-439`）、盐生成（`PBKDF2Util.kt:384-389`）均用无种子 `SecureRandom()`，新格式无静态/零 IV。
- GCM tag 128 bit（`GCM_TAG_LENGTH_BITS=128`）、IV 12 字节（`GCM_IV_LENGTH=12`），符合 NIST SP 800-38D；每消息新盐 ⇒ 每次派生密钥不同，无同 key 下 IV 复用风险。
- 新 GCM 格式迭代数正确接到版本字节（`AESUtil.deriveKey`，`AESUtil.kt:337-344`）：API 26+ SHA256/600k，以下 SHA1/1.4M，符合 OWASP 2025。
- `KEY_ALGORITHM`（"RSA"）与 `CIPHER_TRANSFORMATION` 正确拆分，`KeyFactory` 正常。
- `MAX_ENCRYPT_LEN = 2048/8 - 2*32 - 2 = 190`，符合 OAEP-SHA256 开销公式。
- 真实签名：`Signature.getInstance("SHA256withRSA")`，`verify()` 经 `getOrDefault(false)` fail-closed（`RSAUtil.kt:162-189`）。
- 分片 encrypt/decrypt（`RSAUtil.kt:209-237`）按字节分片/重组，修复多字节 UTF-8 拆分 bug。
- 三文件均无硬编码密钥/凭据。

---

## 待办清单（发版前评估）

> **处理进度：** 提交 `a655446ca`（H1/M2/M3/LOW-3/LOW-4）与 `a6c307f83`（M1/LOW-1/LOW-2）已修复/处理全部条目；LOW-5 经确认为可接受，无需改代码。

| 编号 | 严重度 | 摘要 | 状态 |
|------|--------|------|------|
| H1 | HIGH | PBKDF2Util SHA512/SHA1 公共重载默认迭代太低 | ✅ 已修：默认改 OWASP 常量，新增 `ITERATIONS_LEGACY`，legacyDecrypt 显式传 1000，加回归测试 |
| M1 | MEDIUM | GCM 失败回落 legacy 的完整性 gap | ✅ 已处理：新增 `decryptStrict()`（永不回落、认证失败即抛）；`decrypt()` 保留兼容回落。M2 后 version/salt 篡改已被 AAD 拒绝 |
| M2 | MEDIUM | version/salt 未作 AAD | ✅ 已修：encrypt/decrypt 两侧 `updateAAD(version‖salt)`（新格式未发版，无兼容影响） |
| M3 | MEDIUM | OAEP MGF1 默认 SHA-1 与主哈希 SHA-256 不一致 | ✅ 已修：显式 `OAEPParameterSpec(SHA-256/MGF1-SHA256)`（OAEP-SHA256 未发版，无兼容影响） |
| LOW-1 | LOW | 每次调用重跑满强度 PBKDF2 | ✅ 已处理：`decrypt()` KDoc 记录成本与本地 DoS 提示（不缓存——盐每消息随机，缓存无益且留存密钥更不安全） |
| LOW-2 | LOW | `useSHA512` 参数易误读 | ✅ 已处理：`decrypt()` KDoc 明确其仅影响 legacy 路径 |
| LOW-3 | LOW | `RSAUtil.MAX_DECRYPT_LEN` 死代码 | ✅ 已删 |
| LOW-4 | LOW | `RSAUtil` self-import | ✅ 已删 |
| LOW-5 | LOW | RSA 错误日志含 Throwable | ✅ 已确认可接受：`log` 模块经 `enableLog`/level 门控、输出走按应用隔离的 Logcat，JCE 异常不含明文/密钥，风险低，无需改代码 |

---

## 附录：第二轮复核（Codex 后续修复 + review 跟进）

- **复核范围：** 提交 `c7e17f9db`（Codex 复核后续修复）+ 本轮 security-reviewer / kotlin-reviewer 双视角审查。
- **结论：** 无 CRITICAL / HIGH。核心手写 `pbkdf2HmacSha256Fallback` 经 RFC 8018 §5.2 逐项核对**正确**，并有 JCA 逐字节对比测试；`AESUtil.deriveKey` 去 `SDK_INT≥O` 守卫为真实 bug 修复，非削弱。

### `c7e17f9db` 引入的修复（已推送）

| 项 | 摘要 | 定位 |
|----|------|------|
| AES 跨 API SHA256 KDF | `deriveKey` 按版本字节忠实还原 KDF，`version=0x01`(SHA256) 密文在 API 21–25 上可解 | `AESUtil.kt:deriveKey` |
| PBKDF2 SHA256 fallback | API 21–25 provider 缺失时回落内置 PBKDF2-HMAC-SHA256（RFC 8018） | `PBKDF2Util.kt:pbkdf2HmacSha256Fallback` |
| CrashHandler 递归 | previous handler 改为每次 init 的局部 `val`，消除重复初始化递归自调用 | `CrashHandler.kt:initCrashHandler` |
| RSA 空串分片 | 空字符串走单块 `encrypt`，修复 encrypt/decrypt 不对称 | `RSAUtil.kt:encryptStringByFragment` |

### 本轮 review 新发现（`4d44fe4d4` 已处理 3 项）

| 编号 | 严重度 | 摘要 | 状态 |
|------|--------|------|------|
| F1 | MEDIUM | fallback 派发 `runCatching{Throwable}` 过宽，掩盖非"provider 缺失"的真实错误 | ✅ 已修：收窄为仅 `NoSuchAlgorithmException` 触发，其余照抛（`4d44fe4d4`） |
| F2 | MEDIUM | `intToBigEndian` 与 lib-bytes `Int.toBytes()` 重复（DRY） | ✅ 已修：改用 `Int.toBytes()`，删私有函数（`4d44fe4d4`） |
| F3 | MEDIUM | fallback 派发分支无端到端测试（反射仅测纯算法） | ✅ 已修：抽 `internal sha256KeyWithFallback` seam，加派发/rethrow 确定性测试（`4d44fe4d4`） |
| F4 | MEDIUM | 口令经 `String(plainPassphrase)` 中间态无法擦除（堆上残留） | ✅ 已修：改用 NIO `CharBuffer`→UTF-8 编码（`charsToUtf8Bytes`），绕过 `String`，编码后清零临时缓冲；输出字节不变，加多字节回归测试 |
| F5 | MEDIUM | CrashHandler 链式 previous handler 调用未包 `runCatching`（既有代码，非本轮引入） | ✅ 已修：previous handler 调用同样包 `runCatching`，与 custom handler 对称，异常不逃逸默认处理器；加抛异常 previous handler 回归测试 |
| F6 | LOW | `RSAUtil.encryptStringByFragment` runCatching 内非局部 `return` 绕过 `.getOrNull()` | ⏳ 保留：当前安全（`encrypt` 不抛），但脆弱，建议改 tail 表达式 |
| F7 | LOW | `deriveKey` KDoc 措辞暗示其读版本字节，实际只收 `useSha256: Boolean` | ⏳ 保留：措辞小瑕疵 |
| F8 | INFO | 非 ASCII 口令跨 API 直调可能 UTF-8 与 provider 内部编码不一致 | 不影响 AESUtil；仅外部直调者需注意 |

> **F4/F5** 已作为可选硬化项修复（见上表状态）；**F6/F7/F8** 为记录性备注，暂保留。
