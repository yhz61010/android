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

| 编号 | 严重度 | 摘要 | 备注 |
|------|--------|------|------|
| H1 | HIGH | PBKDF2Util SHA512/SHA1 公共重载默认迭代太低 | 修时须保留 legacy 1000（新增 ITERATIONS_LEGACY） |
| M1 | MEDIUM | GCM 失败回落 legacy 的完整性 gap | 已测试的既定取舍，可选严格 decrypt 变体 |
| M2 | MEDIUM | version/salt 未作 AAD | 加 updateAAD 硬化 |
| M3 | MEDIUM | OAEP MGF1 默认 SHA-1 与主哈希 SHA-256 不一致 | 改动密文兼容性，需迁移评估 |
| LOW-1 | LOW | 每次调用重跑满强度 PBKDF2 | 性能/本地 DoS 提示 |
| LOW-2 | LOW | `useSHA512` 参数易误读 | 迁移后 deprecate |
| LOW-3 | LOW | `RSAUtil.MAX_DECRYPT_LEN` 死代码 | 删除 |
| LOW-4 | LOW | `RSAUtil` self-import | 删除 |
| LOW-5 | LOW | RSA 错误日志含 Throwable | 确认 release sink |
