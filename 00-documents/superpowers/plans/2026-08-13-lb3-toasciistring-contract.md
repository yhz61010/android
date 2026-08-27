# LB-3 `ByteArray.toAsciiString()` 严格 ASCII 契约 —— 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `ByteArray.toAsciiString()` 的契约明确为严格 7-bit ASCII（`0..127`），高字节由“符号扩展乱码”改为 fail-fast 抛 `IllegalArgumentException`，并补 KDoc 与单元测试。

**Architecture:** 纯 `lib-bytes` 模块单函数改动 + 一组 JVM 单元测试。用 `byte.toInt() and 0xFF` 消除符号扩展，`require(code <= 0x7F)` 拒绝非 ASCII 字节。签名不变。

**Tech Stack:** Kotlin、JUnit 5 (Jupiter)、Gradle、detekt、ktlint。

**关联文档:** 设计 spec `00-documents/superpowers/specs/2026-08-13-lb3-toasciistring-contract-design.md`。

## Global Constraints

- `lib-bytes` 是**无 `log` 依赖**的纯工具模块：错误处理只能 `require` / 抛异常，**禁止**引入 `log` 依赖或 `android.util.Log`。
- detekt `maxIssues=0`、ktlint Android 模式，`ignoreFailures=false`：不得引入未用 import 或告警。`0x7F`/`0xFF` 等十六进制字面量本文件既有代码已大量使用，detekt 容忍。
- 代码注释、KDoc、commit message 一律**英文**。
- **签名不变**：`fun ByteArray.toAsciiString(delimiter: CharSequence = ","): String`。
- 纯逻辑，可 JVM 单测，**无需真机**。
- `CancellationException` 无关（非协程代码）。
- git 提交需维护者授权；commit 步骤在授权后执行，作者须为 `Michael Leo <yhzemail61010@aliyun.com>`。

---

### Task 1: 严格 ASCII 实现 + KDoc + 单元测试（TDD）

**Files:**
- Modify: `lib-bytes/src/main/kotlin/com/leovp/bytes/ByteArrayExt.kt:118-119`（`toAsciiString` 实现 + KDoc）
- Test: `lib-bytes/src/test/kotlin/com/leovp/bytes/BytesConversionUnitTest.kt`（新增 `asciiConverter()` 测试方法）

**Interfaces:**
- Consumes: 无（stdlib `mapIndexed` / `joinToString` / `require` / `String.format`）。
- Produces: `fun ByteArray.toAsciiString(delimiter: CharSequence = ","): String` —— 对 `0..127` 字节返回对应 ASCII 字符拼接串；任一字节 ≥`0x80` 抛 `IllegalArgumentException`。

- [ ] **Step 1: 先写失败测试**

在 `BytesConversionUnitTest.kt` 类内（`otherTests()` 之后、类右括号 `}` 之前）新增。`assertEquals` 与 `assertThrows` 已在文件顶部导入（第 3、5 行），无需新增 import。边界用例用 `n.toChar().toString()` 表达期望，避免字面不可见字符：

```kotlin
    @Test
    fun asciiConverter() {
        // default delimiter "," between characters
        assertEquals("H,i", byteArrayOf(72, 105).toAsciiString())
        // empty delimiter concatenates
        assertEquals("Hi", byteArrayOf(72, 105).toAsciiString(""))
        // lower boundary 0x00 (NUL) is valid ASCII
        assertEquals(0.toChar().toString(), byteArrayOf(0).toAsciiString())
        // upper boundary 0x7F (DEL) is valid ASCII; single element -> no delimiter
        assertEquals(0x7F.toChar().toString(), byteArrayOf(0x7F).toAsciiString())
        // empty array -> empty string
        assertEquals("", byteArrayOf().toAsciiString())
        // 0x80 is not ASCII -> fail fast
        assertThrows(IllegalArgumentException::class.java) {
            byteArrayOf(0x80.toByte()).toAsciiString()
        }
        // 0xFF (-1) is not ASCII -> fail fast
        assertThrows(IllegalArgumentException::class.java) {
            byteArrayOf(0xFF.toByte()).toAsciiString()
        }
    }
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./gradlew :lib-bytes:testDebugUnitTest --tests "com.leovp.bytes.BytesConversionUnitTest"`
Expected: FAIL —— 旧实现对 `0x80`/`0xFF` 不抛异常（返回 U+FF80/U+FFFF），两个 `assertThrows` 断言失败。

- [ ] **Step 3: 改实现 + 补 KDoc**

把 `ByteArrayExt.kt:118-119` 的：

```kotlin
fun ByteArray.toAsciiString(delimiter: CharSequence = ",") =
    map { it.toInt().toChar() }.joinToString(delimiter)
```

替换为：

```kotlin
/**
 * Converts each byte to its 7-bit ASCII character and joins them with [delimiter].
 *
 * Strict ASCII only: every byte MUST be in 0..127 (0x00..0x7F, control chars and DEL included).
 * A byte >= 0x80 is not a valid ASCII code point and fails fast with [IllegalArgumentException]
 * instead of being silently mangled by sign extension. For arbitrary bytes use [toHexString].
 *
 * @param delimiter separator inserted between characters (default ",").
 * @return the joined ASCII string ("" for an empty array).
 * @throws IllegalArgumentException if any byte is outside 0..127.
 */
fun ByteArray.toAsciiString(delimiter: CharSequence = ","): String = mapIndexed { index, byte ->
    val code = byte.toInt() and 0xFF
    require(code <= 0x7F) {
        val value = "0x%02X".format(code)
        "toAsciiString: byte at index $index is $value, " +
            "outside ASCII range 0x00..0x7F"
    }
    code.toChar()
}.joinToString(delimiter)
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./gradlew :lib-bytes:testDebugUnitTest --tests "com.leovp.bytes.BytesConversionUnitTest"`
Expected: PASS（`asciiConverter` 及既有测试全绿）。

- [ ] **Step 5: 静态检查**

Run: `./gradlew :lib-bytes:detekt :lib-bytes:ktlintCheck`
Expected: BUILD SUCCESSFUL，0 issue（未新增 import；十六进制字面量既有先例）。

- [ ] **Step 6: 提交（维护者授权后执行）**

```bash
git add lib-bytes/src/main/kotlin/com/leovp/bytes/ByteArrayExt.kt \
  lib-bytes/src/test/kotlin/com/leovp/bytes/BytesConversionUnitTest.kt
git commit -m "fix(lib-bytes): enforce strict ASCII contract in toAsciiString (LB-3)"
```

---

### Task 2: 记录 CHANGELOG 与整改进度

**Files:**
- Modify: `CHANGELOG.md`（`### 修复 (Fixed)` 段新增 LB-3 条目）
- Modify: `00-documents/2026-08-11-remediation-progress-and-review-zh.md`（§1 标记 LB-3 已解决）

**Interfaces:**
- Consumes: 无。
- Produces: 无代码符号；文档更新。

- [ ] **Step 1: 在 CHANGELOG `### 修复 (Fixed)` 段新增（紧接 LB-1 条目之后）**

```markdown
- **LB-3 `ByteArray.toAsciiString()` 明确严格 ASCII 契约**:原实现 `it.toInt().toChar()` 对 ≥`0x80`
  的字节符号扩展成 U+FF80..U+FFFF 乱码;改为逐字节 `and 0xFF` 后要求 `0..127`,非 ASCII 字节
  fail-fast 抛 `IllegalArgumentException`(含索引与十六进制值),并补 KDoc 与单元测试。对 0..127 输入
  行为不变。**行为变更**:含高字节的输入由静默乱码变为抛异常。
```

- [ ] **Step 2: 更新整改进度文档 §1**

把 `00-documents/2026-08-11-remediation-progress-and-review-zh.md` 中 §1 累计说明里的
“`LB-3` 决策项（`CX-5` 代码已于…）”一句，改为写明 LB-3 亦已解决，例如：
“`CX-5`、`LB-3` 均已于 2026-08-13 修复（见 CHANGELOG 与 `superpowers/specs/` 下对应设计文档）；本轮 72 项 + 决策项全部收口，仅余 R-5 深改（待真机）”。（以当时文档实际措辞为准，保持事实一致。）

- [ ] **Step 3: 提交文档（含 spec 与本计划；维护者授权后执行）**

```bash
git add CHANGELOG.md \
  00-documents/2026-08-11-remediation-progress-and-review-zh.md \
  00-documents/superpowers/specs/2026-08-13-lb3-toasciistring-contract-design.md \
  00-documents/superpowers/plans/2026-08-13-lb3-toasciistring-contract.md
git commit -m "docs(lib-bytes): record LB-3 strict ASCII contract fix and design/plan"
```

---

## Self-Review

**Spec coverage:**
- spec §2.1/§3（严格 ASCII 0..127，`and 0xFF` + `require`）→ Task 1 Step 3 ✓
- spec §2.2（≥0x80 fail-fast `IllegalArgumentException`）→ Task 1 Step 1（测试）+ Step 3（实现）✓
- spec §2.3（签名不变）→ Task 1 Step 3 保留 `delimiter=","` ✓
- spec §2.4（KDoc）→ Task 1 Step 3 KDoc 块 ✓
- spec §4（7 个测试用例，JUnit5 风格）→ Task 1 Step 1 `asciiConverter()`（默认/空分隔符/0x00/0x7F/空数组/0x80/0xFF 共 7 断言）✓
- spec §5（行为变更记 CHANGELOG）→ Task 2 Step 1 ✓
- spec §6（不改签名/不加占位符/不加反向解码/不动其它函数）→ 计划未含相应任务，符合“不做的事” ✓

**Placeholder scan:** 无 TBD/TODO；测试与实现均为完整可粘贴代码；无“同 Task N”式省略。

**Type consistency:** `toAsciiString(delimiter: CharSequence = ","): String` 在 spec、实现、测试三处一致；测试调用 `.toAsciiString()` / `.toAsciiString("")` 与签名匹配；`assertEquals`/`assertThrows` 均为文件既有 import。
