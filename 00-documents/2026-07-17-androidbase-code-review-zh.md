# `androidbase` 模块代码审查报告

- **日期：** 2026-07-17
- **范围：** `androidbase` 模块 55 个 Kotlin 文件 / 约 8,150 行
- **方法：** 3 个并行 `kotlin-reviewer` 按子域切分审查
- **结论：** **BLOCK（发版阻断）** —— 这是公共 JitPack 库（发布后无法回收），CRITICAL 加密缺陷必须修复并经 security-reviewer 复核后方可发版。

## 总览

| 子域 | CRITICAL | HIGH | MEDIUM | LOW |
|------|:--:|:--:|:--:|:--:|
| A. cipher / device / receivers | **6** | 3 | 3 | 2 |
| B. media / coroutine / framework | 0 | 6 | 5 | 3 |
| C. exts / ui / livedata / utils | 0 | 5 | 6 | 3 |
| **合计** | **6** | **14** | **14** | **8** |

---

## 🔴 CRITICAL（6，全部在 `utils/cipher/`）

| # | 文件:行 | 问题 | 后果 |
|---|---------|------|------|
| C1 | `AESUtil.kt:300,344` | AES/CBC 使用**全零静态 IV**（正确的 `SecureRandom` 版 `generateIv()` 存在却是死代码） | IV 复用，等长明文前缀泄漏，违反 NIST SP 800-38A |
| C2 | `AESUtil.kt:23` | **盐仅 4 字节**（PBKDF2 自身 32 字节默认被降级） | ~2^16 次加密后密钥+IV 碰撞，CBC 机密性被击穿 |
| C3 | `PBKDF2Util.kt:39` | **PBKDF2 迭代仅 1000**（OWASP 2025 现值：SHA256 600,000 / SHA512 220,000 / SHA1 1,400,000 仅遗留） | 口令可被快速暴破，比预期快数个数量级 |
| C4 | `AESUtil.kt:352` | **`generateKey()` 用 `elapsedRealtimeNanos()`（开机时长）做种**，非 SecureRandom | 密钥空间可猜测，AES 强度形同虚设 |
| C5 | `RSAUtil.kt:37,201` | 裸 `"RSA"` 变换 = **隐式 PKCS1v1.5**，与自身文档推荐的 OAEP 矛盾 | Bleichenbacher 填充预言攻击 |
| C6 | `RSAUtil.kt:166,189` | **`sign/verify` 是裸私钥 RSA "加密"，非真数字签名**（无 hash-then-sign） | 具乘法可锻造性，签名可被伪造 |

**修复方向：** AES **首选改用 AES-GCM 认证加密**（OWASP A04:2025 建议 AEAD；仅"随机 IV"不足，CBC 无完整性保护、易受 padding-oracle），退路为 CBC + 随机 IV + encrypt-then-MAC；恢复 ≥16 字节盐；迭代次数按 OWASP 现值并基准测试到 >100ms；用 `KeyGenerator("AES")` / `SecureRandom` 生成密钥；RSA 改用 `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`；用 `java.security.Signature("SHA256withRSA")` 实现真签名。详见修改方案文档。

---

## 🟠 HIGH（14）—— 按主题聚类

### 媒体解析正确性/健壮性（B）
- **NALU 起始码判定运算符优先级 bug** —— `H264Util.kt:168` + `H265Util.kt:403`（复制粘贴同一 bug）。`&&` 优先级高于 `||`，致 `00 00 05 01` 被误判为合法起始码 → NALU 类型错误、关键帧误判、花屏。
- **`CodecUtil.findStartCode` 数组越界** —— `CodecUtil.kt:159` 只校验 `size<4` 未校验 `offSet+3`，在多处递增偏移循环中触发 `AIOOBE`。
- **`H265Util.getVps` 同类越界** —— `H265Util.kt:224`。
- **`YuvUtil` 转换函数缺入参边界校验** —— 处理摄像头/网络帧，畸变/截断帧直接崩溃（DoS 风险）。

### 资源/生命周期泄漏（B + C）
- **`NetworkMonitor` HandlerThread 泄漏** —— `NetworkMonitor.kt:156` 用 `interrupt()` 无法退出 `Looper`，应 `quitSafely()`。
- **`BaseActivity` 异步 DNS 回调泄漏 Activity+线程** —— `BaseActivity.kt:239`，回调创建新 `NetworkMonitor` 未判 `isDestroyed`。
- **`LeoTextureView` Surface 不一致 + 原生资源泄漏** —— `LeoTextureView.kt:66`，旋转/重建时渲染到已丢弃纹理（黑屏），旧 `Surface` 从不 `release()`。
- **`PcmToWavUtil` 文件句柄泄漏** —— `PcmToWavUtil.kt:168` `FileInputStream` 未 `.use{}`。

### 正确性 bug（C）
- **`ConnectionLiveData` 在线却报 `TYPE_OFFLINE`** —— `ConnectionLiveData.kt:100` 类型硬编码错误。
- **`Base64Ext` 编解码字符集不对称** —— `Base64Ext.kt:33/51` 编码 UTF-8、解码 US-ASCII，中文/emoji round-trip 被破坏。
- **`CrashHandler` 直接替换默认 handler、不链式调用** —— `CrashHandler.kt:12` 传入 custom handler 时直接替换默认 handler（保存到字段却从不读取/链式调用），无 wrapper，Crashlytics/Bugsnag 上报失效。修复：改 wrapper 并链式调用 previous handler。

### 安全/健壮（A）
- **蓝牙反射调用未捕获异常** —— `BluetoothUtil.kt:57-119`，5 处 hidden-API 反射无 `runCatching`，OEM/高版本崩溃宿主 App。
- **`PhoneCallReceiver` 用 `companion object` 存每通电话状态（含号码 PII）** —— `PhoneCallReceiver.kt:121`，多实例共享静态可变状态、无同步。
- **PBKDF2 `useSHA512` 在 API<26 静默降级 SHA-1 且无日志** —— `AESUtil.kt:292`。

---

## 🟡 MEDIUM（14，摘要）

WEP 默认加密（`WifiUtil.kt:43`）· RSA 全异常吞成 null 无日志 · `boundedDevices` 只快照一次 · `KeepAliveReceiver` 缺 `goAsync()` · `withCancellableContext` 用 GlobalScope · `SoundEffectPlayer` 集合非线程安全 + AFD 异常未关 · `CameraUtil.performCrop` crop output URI 兼容性与 `FileUriExposedException` 风险需单独验证 · `BaseActivity.dispatchTouchEvent` `!!`+宽泛 catch 竞态 · H264/H265 catch 只记 `e.message` 丢堆栈 · `H265Util` getSps/getPps/getSei DRY 重复 · `YuvUtil.kt` 962 行超限含约 330 行死代码 · `ActivityExt` 约 200 行三套重复模板 · `LeoTextureView` 监听器未清空 / flag 位 `when` 精确匹配错误。

## ⚪ LOW（8，摘要）

`startActivity(KClass<*>)` 未约束 Activity 子类 · 剪贴板 `postDelayed(1000)` 持 Activity · `TrafficStatHelper` 依赖已失效 `/proc/uid_stat` · 多余 self-import · `!!` 用法 · `DeviceSound` 不复用/不 release。

---

## 建议修复优先级

- **P0（发版前必须）：** 6 个加密 CRITICAL → 交 security-reviewer 复核。
- **P1（正确性 bug，影响所有调用方）：** NALU 优先级 bug、越界（findStartCode/getVps/YuvUtil）、Base64 不对称、ConnectionLiveData 类型错误。
- **P2（资源泄漏与集成回归）：** NetworkMonitor / BaseActivity / LeoTextureView / PcmToWav（资源泄漏）、CrashHandler（集成回归，第三方上报失效）。
- **P3：** MEDIUM/LOW 的 DRY、死代码清理、线程安全。

**无问题：** `coroutine` 子域（`ConcurrencyHelpers`）—— 结构化并发实现干净。
