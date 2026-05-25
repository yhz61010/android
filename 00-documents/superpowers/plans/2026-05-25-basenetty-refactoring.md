# basenetty Module Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 12 code review issues in the basenetty module and add regression tests.

**Architecture:** Preserve all public APIs. Restructure internals by extracting helper methods from `BaseNettyClient.connect()`, fixing retry lifecycle with `SupervisorJob` + replaceable child job, removing dead server-side WebSocket client code, and making `EventBus` thread-safe with `CopyOnWriteArrayList`.

**Tech Stack:** Kotlin, Netty 4.1.108, Kotlin Coroutines 1.10.2, JUnit 5, Mockk, Netty EmbeddedChannel

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `basenetty/build.gradle.kts` | Modify | Add test dependencies |
| `basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/BaseNetty.kt` | Modify | @Deprecated on SERVER enum values |
| `basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/decoder/CustomSocketByteStreamDecoder.kt` | Modify | Fix threshold, add validation |
| `basenetty/src/main/kotlin/com/leovp/basenetty/eventbus/util/EventBus.kt` | Modify | Thread-safe handlers |
| `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt` | Modify | Own `clients` field |
| `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseServerChannelInboundHandler.kt` | Modify | Remove dead code, use netty.clients |
| `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseClientChannelInboundHandler.kt` | Modify | Remove sync() |
| `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt` | Modify | Retry lifecycle, cert caching, connect refactor, disconnectManually fix |
| `basenetty/src/test/kotlin/com/leovp/basenetty/CustomSocketByteStreamDecoderTest.kt` | Create | Decoder tests |
| `basenetty/src/test/kotlin/com/leovp/basenetty/EventBusTest.kt` | Create | EventBus tests |
| `basenetty/src/test/kotlin/com/leovp/basenetty/RetryStrategyTest.kt` | Create | Retry strategy tests |

---

### Task 1: Create branch and add test dependencies

**Files:**
- Modify: `basenetty/build.gradle.kts`

- [ ] **Step 1: Create feature branch**

```bash
git checkout -b fix/basenetty-refactoring
```

- [ ] **Step 2: Add test dependencies to build.gradle.kts**

In `basenetty/build.gradle.kts`, add inside the `dependencies` block, after the existing `api(projects.libNetwork)` line:

```kotlin
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime.only)
```

- [ ] **Step 3: Create test directory**

```bash
mkdir -p basenetty/src/test/kotlin/com/leovp/basenetty
```

- [ ] **Step 4: Verify build compiles**

```bash
./gradlew :basenetty:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add basenetty/build.gradle.kts
git commit -m "build(basenetty): add test dependencies for JUnit 5, Mockk, coroutines-test"
```

---

### Task 2: Fix CustomSocketByteStreamDecoder — TDD

**Files:**
- Create: `basenetty/src/test/kotlin/com/leovp/basenetty/CustomSocketByteStreamDecoderTest.kt`
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/decoder/CustomSocketByteStreamDecoder.kt`

- [ ] **Step 1: Write failing tests**

Create `basenetty/src/test/kotlin/com/leovp/basenetty/CustomSocketByteStreamDecoderTest.kt`:

```kotlin
package com.leovp.basenetty

import com.leovp.basenetty.framework.base.decoder.CustomSocketByteStreamDecoder
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.nio.ByteOrder
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test

class CustomSocketByteStreamDecoderTest {

    private fun createChannel() = EmbeddedChannel(CustomSocketByteStreamDecoder())

    private fun writeFrame(channel: EmbeddedChannel, payload: ByteArray): Boolean {
        val buf = Unpooled.buffer(Int.SIZE_BYTES + payload.size)
        buf.writeIntLE(payload.size)
        buf.writeBytes(payload)
        return channel.writeInbound(buf)
    }

    @Test
    fun `empty buffer produces no output`() {
        val ch = createChannel()
        ch.writeInbound(Unpooled.EMPTY_BUFFER)
        ch.readInbound<Any>().shouldBeNull()
    }

    @Test
    fun `buffer smaller than header produces no output`() {
        val ch = createChannel()
        ch.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))
        ch.readInbound<Any>().shouldBeNull()
    }

    @Test
    fun `zero byte payload decodes correctly`() {
        val ch = createChannel()
        writeFrame(ch, byteArrayOf())
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        result.readableBytes().shouldBeEqualTo(0)
        result.release()
    }

    @Test
    fun `one byte payload decodes correctly`() {
        val ch = createChannel()
        writeFrame(ch, byteArrayOf(0x42))
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        result.readableBytes().shouldBeEqualTo(1)
        result.readByte().shouldBeEqualTo(0x42)
        result.release()
    }

    @Test
    fun `normal multi-byte frame decodes correctly`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val ch = createChannel()
        writeFrame(ch, payload)
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        val decoded = ByteArray(result.readableBytes())
        result.readBytes(decoded)
        decoded.shouldBeEqualTo(payload)
        result.release()
    }

    @Test
    fun `half packet reassembly works`() {
        val ch = createChannel()
        val payload = byteArrayOf(10, 20, 30, 40)
        val fullBuf = Unpooled.buffer(Int.SIZE_BYTES + payload.size)
        fullBuf.writeIntLE(payload.size)
        fullBuf.writeBytes(payload)
        val allBytes = ByteArray(fullBuf.readableBytes())
        fullBuf.readBytes(allBytes)
        fullBuf.release()

        // Write first half
        ch.writeInbound(Unpooled.wrappedBuffer(allBytes, 0, 4))
        ch.readInbound<Any>().shouldBeNull()

        // Write second half
        ch.writeInbound(Unpooled.wrappedBuffer(allBytes, 4, allBytes.size - 4))
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        val decoded = ByteArray(result.readableBytes())
        result.readBytes(decoded)
        decoded.shouldBeEqualTo(payload)
        result.release()
    }

    @Test
    fun `negative length closes channel`() {
        val ch = createChannel()
        val buf = Unpooled.buffer(Int.SIZE_BYTES)
        buf.writeIntLE(-1)
        ch.writeInbound(buf)
        ch.isOpen.shouldBeFalse()
    }

    @Test
    fun `oversized frame closes channel`() {
        val ch = createChannel()
        val buf = Unpooled.buffer(Int.SIZE_BYTES)
        // Write a length that exceeds MAX_FRAME_SIZE (10 MB)
        buf.writeIntLE(11 * 1024 * 1024)
        ch.writeInbound(buf)
        ch.isOpen.shouldBeFalse()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :basenetty:testDebugUnitTest --tests "com.leovp.basenetty.CustomSocketByteStreamDecoderTest" --continue
```

Expected: Several tests FAIL (zero-byte payload blocked by `bufLen < 6`, negative/oversized not handled).

- [ ] **Step 3: Implement the fix**

Replace the entire content of `basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/decoder/CustomSocketByteStreamDecoder.kt`:

```kotlin
package com.leovp.basenetty.framework.base.decoder

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

/**
 * Custom byte stream decoder with a 4-byte little-endian length prefix.
 *
 * Frame format: [4-byte LE length][payload bytes]
 *
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
class CustomSocketByteStreamDecoder : ByteToMessageDecoder() {
    companion object {
        /** Maximum allowed frame payload size (10 MB). */
        const val MAX_FRAME_SIZE = 10 * 1024 * 1024
    }

    override fun decode(
        ctx: ChannelHandlerContext,
        inBuf: ByteBuf,
        out: MutableList<Any>
    ) {
        if (inBuf.readableBytes() < Int.SIZE_BYTES) return

        inBuf.markReaderIndex()
        val dataLen = inBuf.readIntLE()

        if (dataLen < 0 || dataLen > MAX_FRAME_SIZE) {
            ctx.close()
            return
        }

        if (inBuf.readableBytes() < dataLen) {
            inBuf.resetReaderIndex()
            return
        }
        out.add(inBuf.readBytes(dataLen))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :basenetty:testDebugUnitTest --tests "com.leovp.basenetty.CustomSocketByteStreamDecoderTest"
```

Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/decoder/CustomSocketByteStreamDecoder.kt basenetty/src/test/kotlin/com/leovp/basenetty/CustomSocketByteStreamDecoderTest.kt
git commit -m "fix(basenetty): fix decoder min frame threshold 6->4, add length validation"
```

---

### Task 3: Fix EventBus thread safety — TDD

**Files:**
- Create: `basenetty/src/test/kotlin/com/leovp/basenetty/EventBusTest.kt`
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/eventbus/util/EventBus.kt`

- [ ] **Step 1: Write failing tests**

Create `basenetty/src/test/kotlin/com/leovp/basenetty/EventBusTest.kt`:

```kotlin
package com.leovp.basenetty

import com.leovp.basenetty.eventbus.base.EventBusAttributes
import com.leovp.basenetty.eventbus.handler.EventBusHandler
import com.leovp.basenetty.eventbus.util.EventBus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class EventBusTest {

    @AfterEach
    fun tearDown() {
        EventBus.clearAllHandlers()
    }

    @Test
    fun `register and processHandlers invokes handler`() {
        val invoked = AtomicInteger(0)
        val handler = EventBusHandler { invoked.incrementAndGet() }
        EventBus.register("addr1", handler = handler)
        EventBus.processHandlers("addr1") { _, h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(1)
    }

    @Test
    fun `unregister removes handler`() {
        val invoked = AtomicInteger(0)
        EventBus.register("addr2", handler = EventBusHandler { invoked.incrementAndGet() })
        EventBus.unregister("addr2")
        EventBus.processHandlers("addr2") { _, h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(0)
    }

    @Test
    fun `clearAllHandlers clears both handlers and reply handlers`() {
        val invoked = AtomicInteger(0)
        EventBus.register("addr3", handler = EventBusHandler { invoked.incrementAndGet() })
        EventBus.send("addr4", handler = EventBusHandler { invoked.incrementAndGet() })
        EventBus.clearAllHandlers()
        EventBus.processHandlers("addr3") { _, h -> h.handle(null) }
        EventBus.processReplyHandler("addr4") { h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(0)
    }

    @Test
    fun `processReplyHandler removes handler after use`() {
        val invoked = AtomicInteger(0)
        val result = EventBus.send("addr5", handler = EventBusHandler { invoked.incrementAndGet() })
        val replyAddr = result[EventBusAttributes.REPLY_ADDRESS] as String
        EventBus.processReplyHandler(replyAddr) { h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(1)
        // Second call should not invoke
        EventBus.processReplyHandler(replyAddr) { h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(1)
    }

    @Test
    fun `concurrent register does not lose handlers`() {
        val threadCount = 10
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val address = "concurrent-addr"

        val threads = (0 until threadCount).map { i ->
            Thread {
                barrier.await()
                EventBus.register(address, handler = EventBusHandler { })
                latch.countDown()
            }
        }
        threads.forEach { it.start() }
        latch.await()

        val count = AtomicInteger(0)
        EventBus.processHandlers(address) { _, _ -> count.incrementAndGet() }
        count.get().shouldBeEqualTo(threadCount)
    }

    @Test
    fun `send without handler does not add reply address`() {
        val result = EventBus.send("addr6", message = "hello")
        (EventBusAttributes.REPLY_ADDRESS !in result).shouldBeTrue()
    }
}
```

- [ ] **Step 2: Run tests to verify `concurrent register` fails**

```bash
./gradlew :basenetty:testDebugUnitTest --tests "com.leovp.basenetty.EventBusTest" --continue
```

Expected: `concurrent register does not lose handlers` FAILS (race condition loses handlers).

- [ ] **Step 3: Implement the fix**

Replace the entire content of `basenetty/src/main/kotlin/com/leovp/basenetty/eventbus/util/EventBus.kt`:

```kotlin
@file:Suppress("unused")

package com.leovp.basenetty.eventbus.util

import com.leovp.basenetty.eventbus.base.EventBusAttributes
import com.leovp.basenetty.eventbus.handler.EventBusHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Author: Michael Leo
 * Date: 2021/7/26 14:07
 */
object EventBus {

    private val handlers: ConcurrentMap<String, CopyOnWriteArrayList<EventBusHandler>> =
        ConcurrentHashMap()
    private val replyHandlers: ConcurrentMap<String, EventBusHandler> =
        ConcurrentHashMap()

    /** same as `request` */
    fun send(
        address: String,
        message: Any? = null,
        headers: Map<String, Any>? = null,
        handler: EventBusHandler? = null,
    ): Map<String, Any> =
        constructData(EventBusAttributes.TYPE_SEND, address, message, headers, null, handler)

    fun publish(
        address: String,
        message: Any? = null,
        headers: Map<String, Any>? = null
    ): Map<String, Any> =
        constructData(EventBusAttributes.TYPE_PUBLISH, address, message, headers)

    /** same as `consumer` */
    fun register(
        address: String,
        headers: Map<String, Any>? = null,
        customFields: Map<String, Any?>? = null,
        handler: EventBusHandler,
    ): Map<String, Any> = constructData(
        EventBusAttributes.TYPE_REGISTER,
        address,
        null,
        headers,
        customFields,
        handler
    )

    fun unregister(
        address: String,
        headers: Map<String, Any>? = null
    ): Map<String, Any> {
        handlers.remove(address)
        return constructData(EventBusAttributes.TYPE_UNREGISTER, address, null, headers)
    }

    // =============================================

    fun processHandlers(
        address: String,
        handle: (idx: Int, h: EventBusHandler) -> Unit
    ) = handlers[address]?.forEachIndexed { idx, h -> handle(idx, h) }

    fun processReplyHandler(
        address: String,
        handle: (h: EventBusHandler) -> Unit
    ) {
        replyHandlers.remove(address)?.let { handle(it) }
    }

    // =============================================

    fun clearHandlers() {
        handlers.clear()
    }

    fun clearAllHandlers() {
        clearHandlers()
        replyHandlers.clear()
    }

    // =============================================

    private fun addHandler(address: String, handler: EventBusHandler) {
        handlers.computeIfAbsent(address) { CopyOnWriteArrayList() }.add(handler)
    }

    private fun addReplyHandler(address: String, handler: EventBusHandler) {
        replyHandlers.putIfAbsent(address, handler)
    }

    // =============================================

    private fun constructData(
        type: String,
        address: String,
        message: Any? = null,
        headers: Map<String, Any>? = null,
        customFields: Map<String, Any?>? = null,
        handler: EventBusHandler? = null,
    ): Map<String, Any> {
        val eventBusObj = mutableMapOf<String, Any>()
        eventBusObj[EventBusAttributes.TYPE] = type
        eventBusObj[EventBusAttributes.ADDRESS] = address
        headers?.let { eventBusObj[EventBusAttributes.HEADERS] = it }
        message?.let { eventBusObj[EventBusAttributes.BODY] = it }

        when (type) {
            EventBusAttributes.TYPE_SEND -> {
                handler?.let {
                    val replyAddress = UUID.randomUUID().toString()
                    eventBusObj[EventBusAttributes.REPLY_ADDRESS] = replyAddress
                    addReplyHandler(replyAddress, it)
                }
            }

            EventBusAttributes.TYPE_REGISTER -> {
                customFields?.let { map ->
                    for ((key, value) in map) {
                        if (null != value) eventBusObj[key] = value
                    }
                }
                handler?.let { addHandler(address, it) }
            }

            else -> Unit
        }
        return eventBusObj
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :basenetty:testDebugUnitTest --tests "com.leovp.basenetty.EventBusTest"
```

Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add basenetty/src/main/kotlin/com/leovp/basenetty/eventbus/util/EventBus.kt basenetty/src/test/kotlin/com/leovp/basenetty/EventBusTest.kt
git commit -m "fix(basenetty): fix EventBus thread safety with CopyOnWriteArrayList + computeIfAbsent"
```

---

### Task 4: Add RetryStrategy tests

**Files:**
- Create: `basenetty/src/test/kotlin/com/leovp/basenetty/RetryStrategyTest.kt`

- [ ] **Step 1: Write tests**

Create `basenetty/src/test/kotlin/com/leovp/basenetty/RetryStrategyTest.kt`:

```kotlin
package com.leovp.basenetty

import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.basenetty.framework.client.retrystrategy.ExponentRetry
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class RetryStrategyTest {

    @Test
    fun `ConstantRetry returns fixed delay`() {
        val retry = ConstantRetry(maxTimes = 5, delayInMillSec = 3000L)
        retry.getMaxTimes().shouldBeEqualTo(5)
        retry.getDelayInMillSec(1).shouldBeEqualTo(3000L)
        retry.getDelayInMillSec(3).shouldBeEqualTo(3000L)
        retry.getDelayInMillSec(5).shouldBeEqualTo(3000L)
    }

    @Test
    fun `ConstantRetry default values`() {
        val retry = ConstantRetry()
        retry.getMaxTimes().shouldBeEqualTo(10)
        retry.getDelayInMillSec(1).shouldBeEqualTo(2000L)
    }

    @Test
    fun `ExponentRetry returns exponential delay`() {
        val retry = ExponentRetry(maxTimes = 5, base = 1L)
        retry.getMaxTimes().shouldBeEqualTo(5)
        // base=1: delay = (1 shl (n-1)) * 1000
        retry.getDelayInMillSec(1).shouldBeEqualTo(1000L)  // 1 << 0 = 1
        retry.getDelayInMillSec(2).shouldBeEqualTo(2000L)  // 1 << 1 = 2
        retry.getDelayInMillSec(3).shouldBeEqualTo(4000L)  // 1 << 2 = 4
        retry.getDelayInMillSec(4).shouldBeEqualTo(8000L)  // 1 << 3 = 8
        retry.getDelayInMillSec(5).shouldBeEqualTo(16000L) // 1 << 4 = 16
    }

    @Test
    fun `ExponentRetry with base 2`() {
        val retry = ExponentRetry(maxTimes = 3, base = 2L)
        // base=2: delay = (2 shl (n-1)) * 1000
        retry.getDelayInMillSec(1).shouldBeEqualTo(2000L)  // 2 << 0 = 2
        retry.getDelayInMillSec(2).shouldBeEqualTo(4000L)  // 2 << 1 = 4
        retry.getDelayInMillSec(3).shouldBeEqualTo(8000L)  // 2 << 2 = 8
    }

    @Test
    fun `ExponentRetry default values`() {
        val retry = ExponentRetry()
        retry.getMaxTimes().shouldBeEqualTo(5)
        retry.getDelayInMillSec(1).shouldBeEqualTo(1000L)
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew :basenetty:testDebugUnitTest --tests "com.leovp.basenetty.RetryStrategyTest"
```

Expected: All 5 tests PASS (these test existing correct behavior)

- [ ] **Step 3: Commit**

```bash
git add basenetty/src/test/kotlin/com/leovp/basenetty/RetryStrategyTest.kt
git commit -m "test(basenetty): add RetryStrategy unit tests for ConstantRetry and ExponentRetry"
```

---

### Task 5: @Deprecated ServerConnectStatus enum values + remove sync() from server handler

**Files:**
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/BaseNetty.kt`
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseServerChannelInboundHandler.kt`

- [ ] **Step 1: Add @Deprecated to ServerConnectStatus enum values**

In `basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/BaseNetty.kt`, replace the `CLIENT_CONNECTED` and `CLIENT_DISCONNECTED` entries:

Replace:
```kotlin
    CLIENT_CONNECTED,

    /**
     * After connecting, this connection is **ONLY** be working in this status if you do intent to
     * disconnect to server as you expect.
     *
     * **Attention:** [FAILED] listeners will **NOT** trigger [CLIENT_DISCONNECTED] listener.
     */
    CLIENT_DISCONNECTED,
```

With:
```kotlin
    @Deprecated(
        "Use ServerConnectListener.onClientConnected callback instead.",
        level = DeprecationLevel.WARNING
    )
    CLIENT_CONNECTED,

    /**
     * After connecting, this connection is **ONLY** be working in this status if you do intent to
     * disconnect to server as you expect.
     *
     * **Attention:** [FAILED] listeners will **NOT** trigger [CLIENT_DISCONNECTED] listener.
     */
    @Deprecated(
        "Use ServerConnectListener.onClientDisconnected callback instead.",
        level = DeprecationLevel.WARNING
    )
    CLIENT_DISCONNECTED,
```

- [ ] **Step 2: Remove sync() and connectState writes from server handler exceptionCaught**

In `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseServerChannelInboundHandler.kt`, replace the `exceptionCaught` method (lines 91-117):

Replace:
```kotlin
    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val exceptionType = when (cause) {
            is IOException -> "IOException"
            is IllegalArgumentException -> "IllegalArgumentException"
            else -> "Unknown Exception"
        }
        LogContext.log.e(tag, "===== Caught $exceptionType =====")
        LogContext.log.e(tag, "Exception: ${cause.message}", cause)

        //        val channel = ctx.channel()
        //        val isChannelActive = channel.isActive
        //        LogContext.log.e(tagName, "Channel is active: $isChannelActive")
        //        if (isChannelActive) {
        //            ctx.close()
        //        }
        ctx.close().syncUninterruptibly()

        netty.connectState.set(ServerConnectStatus.FAILED)
        netty.connectionListener.onStartFailed(
            netty,
            ServerConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION,
            "Caught exception"
        )

        LogContext.log.e(tag, "============================")
    }
```

With:
```kotlin
    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val exceptionType = when (cause) {
            is IOException -> "IOException"
            is IllegalArgumentException -> "IllegalArgumentException"
            else -> "Unknown Exception"
        }
        LogContext.log.e(tag, "===== Caught $exceptionType =====")
        LogContext.log.e(tag, "Exception: ${cause.message}", cause)
        // Do not call sync() — it blocks the event loop thread.
        // channelInactive will handle cleanup.
        ctx.close()
        LogContext.log.e(tag, "============================")
    }
```

- [ ] **Step 3: Remove connectState writes for CLIENT_CONNECTED/CLIENT_DISCONNECTED from server handler**

In the same file, in `channelActive` (line 60), remove:
```kotlin
        netty.connectState.set(ServerConnectStatus.CLIENT_CONNECTED)
```

In `channelInactive` (line 74), remove:
```kotlin
        netty.connectState.set(ServerConnectStatus.CLIENT_DISCONNECTED)
```

The `connectionListener.onClientConnected(...)` and `connectionListener.onClientDisconnected(...)` calls remain — only the `connectState` writes are removed.

- [ ] **Step 4: Verify build compiles**

```bash
./gradlew :basenetty:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL (with deprecation warnings, which is expected)

- [ ] **Step 5: Commit**

```bash
git add basenetty/src/main/kotlin/com/leovp/basenetty/framework/base/BaseNetty.kt basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseServerChannelInboundHandler.kt
git commit -m "refactor(basenetty): deprecate CLIENT_CONNECTED/CLIENT_DISCONNECTED, remove sync() from server exceptionCaught"
```

---

### Task 6: Remove dead WebSocket client code from server handler + move clients to BaseNettyServer

**Files:**
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseServerChannelInboundHandler.kt`
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt`

- [ ] **Step 1: Add `clients` field to BaseNettyServer**

In `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt`, add these imports at the top:

```kotlin
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
```

Then add after the `defaultServerInboundHandler` declaration (after line 67):

```kotlin
    internal val clients: ChannelGroup =
        DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
```

- [ ] **Step 2: Rewrite BaseServerChannelInboundHandler**

Replace the entire content of `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseServerChannelInboundHandler.kt`:

```kotlin
package com.leovp.basenetty.framework.server

import com.leovp.basenetty.framework.base.ReadSocketDataListener
import com.leovp.log.LogContext
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import java.io.IOException

/**
 * Author: Michael Leo
 * Date: 20-8-5 下午8:18
 */
abstract class BaseServerChannelInboundHandler<T>(
    private val netty: BaseNettyServer
) : SimpleChannelInboundHandler<T>(), ReadSocketDataListener<T> {

    private val tag = netty.tag

    abstract fun release()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerAdded =====")
        super.handlerAdded(ctx)
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(
            tag,
            "===== Channel is registered to EventLoop ====="
        )
        super.channelRegistered(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        LogContext.log.i(
            tag,
            "===== Client Channel is active: " +
                "${ctx.channel().remoteAddress()} ====="
        )
        val clientChannel = ctx.channel()
        netty.clients.add(clientChannel)
        super.channelActive(ctx)
        netty.connectionListener.onClientConnected(netty, clientChannel)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        LogContext.log.w(
            tag,
            "===== Client disconnected: " +
                "${ctx.channel().remoteAddress()} ====="
        )
        val clientChannel = ctx.channel()
        netty.clients.remove(clientChannel)
        super.channelInactive(ctx)
        netty.connectionListener.onClientDisconnected(
            netty, clientChannel
        )
    }

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        LogContext.log.i(
            tag,
            "===== Channel is unregistered from EventLoop ====="
        )
        super.channelUnregistered(ctx)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        LogContext.log.i(tag, "===== handlerRemoved =====")
        super.handlerRemoved(ctx)
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(
        ctx: ChannelHandlerContext,
        cause: Throwable
    ) {
        val exceptionType = when (cause) {
            is IOException -> "IOException"
            is IllegalArgumentException -> "IllegalArgumentException"
            else -> "Unknown Exception"
        }
        LogContext.log.e(tag, "===== Caught $exceptionType =====")
        LogContext.log.e(tag, "Exception: ${cause.message}", cause)
        ctx.close()
        LogContext.log.e(tag, "============================")
    }

    override fun userEventTriggered(
        ctx: ChannelHandlerContext,
        evt: Any?
    ) {
        LogContext.log.i(tag, "===== userEventTriggered ($evt) =====")
        super.userEventTriggered(ctx, evt)
    }

    /**
     * DO NOT override this method
     */
    override fun channelRead0(ctx: ChannelHandlerContext, msg: T) {
        if (netty.isWebSocket) {
            val frame = msg as WebSocketFrame
            if (frame is CloseWebSocketFrame) {
                LogContext.log.w(
                    tag,
                    "=====> WebSocket Client received close frame <====="
                )
                ctx.channel().close()
                return
            }
        }
        onReceivedData(ctx, msg)
    }
}
```

- [ ] **Step 3: Verify build compiles**

```bash
./gradlew :basenetty:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all existing tests still pass**

```bash
./gradlew :basenetty:testDebugUnitTest
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseServerChannelInboundHandler.kt basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt
git commit -m "refactor(basenetty): remove dead WebSocket client code from server handler, move clients to BaseNettyServer"
```

---

### Task 7: Remove sync() from client handler exceptionCaught

**Files:**
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseClientChannelInboundHandler.kt`

- [ ] **Step 1: Replace sync() with fire-and-forget close**

In `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseClientChannelInboundHandler.kt`, replace the try/catch block in `exceptionCaught` (lines 177-183):

Replace:
```kotlin
        runCatching {
            ctx.close().sync()
        }.onFailure {
            LogContext.log.e(tag, "close channel error.", it)
            it.printStackTrace()
        }
```

With:
```kotlin
        ctx.close()
```

- [ ] **Step 2: Verify build compiles**

```bash
./gradlew :basenetty:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseClientChannelInboundHandler.kt
git commit -m "fix(basenetty): remove blocking sync() from client exceptionCaught to prevent event loop deadlock"
```

---

### Task 8: Fix byteOrder parameter in BaseNettyClient and BaseNettyServer

**Files:**
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt`
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt`

- [ ] **Step 1: Simplify BaseNettyClient.executeUnifiedCommand — remove byteOrder param and dead branch**

In `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt`, modify `executeUnifiedCommand` signature (line 696-705) to remove the `byteOrder` parameter:

Replace:
```kotlin
    private fun executeUnifiedCommand(
        cmdTag: String,
        cmdDesc: String?,
        cmd: Any?,
        isPing: Boolean,
        showContent: Boolean,
        showLog: Boolean = true,
        fullOutput: Boolean = false,
        byteOrder: ByteOrder,
    ): Boolean {
```

With:
```kotlin
    private fun executeUnifiedCommand(
        cmdTag: String,
        cmdDesc: String?,
        cmd: Any?,
        isPing: Boolean,
        showContent: Boolean,
        showLog: Boolean = true,
        fullOutput: Boolean = false,
    ): Boolean {
```

Then replace the ByteArray hex logging block (lines 734-744):

Replace:
```kotlin
                if (showLog) {
                    val cmdMsg = "$logPrefix[${cmd.size}]"
                    val hex: String? = if (showContent) {
                        if (ByteOrder.BIG_ENDIAN ==
                            byteOrder
                        ) {
                            cmd.toHexString()
                        } else {
                            cmd.toHexString()
                        }
                    } else {
                        null
                    }
```

With:
```kotlin
                if (showLog) {
                    val cmdMsg = "$logPrefix[${cmd.size}]"
                    val hex: String? =
                        if (showContent) cmd.toHexString() else null
```

- [ ] **Step 2: Update public methods to not pass byteOrder to private method**

In the same file, update `executeCommand` (around line 803):

Replace:
```kotlin
    ) = executeUnifiedCommand(
        cmdTag,
        cmdDesc,
        cmd,
        isPing = false,
        showContent = showContent,
        showLog = showLog,
        fullOutput = fullOutput,
        byteOrder = byteOrder
    )
```

With:
```kotlin
    ) = executeUnifiedCommand(
        cmdTag,
        cmdDesc,
        cmd,
        isPing = false,
        showContent = showContent,
        showLog = showLog,
        fullOutput = fullOutput,
    )
```

Update `executePingCommand` similarly (around line 825):

Replace:
```kotlin
    ) = executeUnifiedCommand(
        cmdTag,
        cmdDesc,
        cmd,
        isPing = true,
        showContent = showContent,
        showLog = showLog,
        fullOutput = fullOutput,
        byteOrder = byteOrder
    )
```

With:
```kotlin
    ) = executeUnifiedCommand(
        cmdTag,
        cmdDesc,
        cmd,
        isPing = true,
        showContent = showContent,
        showLog = showLog,
        fullOutput = fullOutput,
    )
```

Add KDoc to both public methods' `byteOrder` parameter:
```kotlin
    /**
     * ...existing docs...
     *
     * @param byteOrder **Deprecated — has no effect.** The byte array is
     *   sent as-is regardless of byte order. This parameter will be
     *   removed in a future release.
     */
```

Remove the `import java.nio.ByteOrder` line from BaseNettyClient.kt (it is no longer used internally, but still needed for the public parameter type — keep it).

- [ ] **Step 3: Apply the same fix to BaseNettyServer.executeUnifiedCommand**

In `basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt`, apply the identical changes:

Remove `byteOrder: ByteOrder,` from `executeUnifiedCommand` signature (line 246).

Replace the hex logging block (lines 276-284):
```kotlin
                    val hex: String? = if (showContent) {
                        if (ByteOrder.BIG_ENDIAN ==
                            byteOrder
                        ) {
                            cmd.toHexString()
                        } else {
                            cmd.toHexString()
                        }
                    } else {
                        null
                    }
```

With:
```kotlin
                    val hex: String? =
                        if (showContent) cmd.toHexString() else null
```

Update `executeCommand` and `executePingCommand` to not pass `byteOrder` to the private method. Add the same KDoc deprecation note.

- [ ] **Step 4: Verify build**

```bash
./gradlew :basenetty:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt basenetty/src/main/kotlin/com/leovp/basenetty/framework/server/BaseNettyServer.kt
git commit -m "fix(basenetty): remove dead byteOrder branching from executeUnifiedCommand, deprecate parameter in KDoc"
```

---

### Task 9: Fix BaseNettyClient — retry lifecycle, certificate caching, disconnectManually

**Files:**
- Modify: `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt`

This is the largest task. It addresses issues #1 (retryScope), #2 (disconnectManually), #3 (connect refactor), and #4 (certificate caching).

- [ ] **Step 1: Fix certificate caching (#4)**

In `BaseNettyClient.kt`, replace the `certificateInputStream` field and `getCertificateInputStream` method (lines 180-198):

Replace:
```kotlin
    private var certificateInputStream: InputStream? = null
    private var trustAllServers: Boolean = false

    fun getCertificateInputStream(): InputStream? {
        if (certificateInputStream == null) {
            return null
        }
        return runCatching {
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(8 shl 10)
            var len: Int
            while (certificateInputStream!!.read(buffer).also { len = it } > -1) {
                baos.write(buffer, 0, len)
            }
            // DO NOT close certificateInputStream stream or else we can not clone it anymore
            baos.flush()
            ByteArrayInputStream(baos.toByteArray())
        }.getOrNull()
    }
```

With:
```kotlin
    private var certificateBytes: ByteArray? = null
    private var trustAllServers: Boolean = false

    fun getCertificateInputStream(): InputStream? =
        certificateBytes?.let { ByteArrayInputStream(it) }
```

In the first `protected constructor` (cert-based, line 96-126), change line 120:

Replace:
```kotlin
        this.certificateInputStream = certInputStream
```

With:
```kotlin
        this.certificateBytes = certInputStream.readBytes()
```

Remove unused imports: `ByteArrayOutputStream`.

- [ ] **Step 2: Fix retry lifecycle (#1) — SupervisorJob + retryJob**

Replace the `retryScope` declaration (line 200):

Replace:
```kotlin
    private val retryScope = CoroutineScope(Dispatchers.IO + Job())
```

With:
```kotlin
    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryJob: Job? = null
    @Volatile private var released = false
```

Add import: `import kotlinx.coroutines.SupervisorJob`

Replace the `doRetry()` method (lines 534-567):

Replace:
```kotlin
    fun doRetry() {
        if (retryProcess()) return

        retryTimes.getAndIncrement()
        if (retryTimes.get() > retryStrategy.getMaxTimes()) {
            LogContext.log.e(
                tag,
                "===== Connect failed in doRetry() - Exceed max retry times. ====="
            )
            stopRetryHandler()
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyClient,
                ClientConnectListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                "Exceed max retry times."
            )
        } else {
            LogContext.log.w(
                tag,
                "Reconnect($retryTimes) in " +
                    "${retryStrategy.getDelayInMillSec(retryTimes.get())}ms | " +
                    "current state=${connectStatus.get().name}"
            )
            // retryHandler.postDelayed({ connect() },
            // retryStrategy.getDelayInMillSec(retryTimes.get()))
            retryScope.launch {
                runCatching {
                    delay(retryStrategy.getDelayInMillSec(retryTimes.get()))
                    ensureActive()
                    connect()
                }.onFailure { LogContext.log.e(tag, "Do retry failed.", it) }
            }
        }
    }
```

With:
```kotlin
    fun doRetry() {
        if (released) return
        if (retryProcess()) return

        retryTimes.getAndIncrement()
        if (retryTimes.get() > retryStrategy.getMaxTimes()) {
            LogContext.log.e(
                tag,
                "===== Connect failed in doRetry() " +
                    "- Exceed max retry times. ====="
            )
            stopRetryHandler()
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this@BaseNettyClient,
                ClientConnectListener.CONNECTION_ERROR_EXCEED_MAX_RETRY_TIMES,
                "Exceed max retry times."
            )
        } else {
            LogContext.log.w(
                tag,
                "Reconnect($retryTimes) in " +
                    "${retryStrategy.getDelayInMillSec(retryTimes.get())}ms" +
                    " | current state=${connectStatus.get().name}"
            )
            retryJob = retryScope.launch {
                runCatching {
                    delay(
                        retryStrategy.getDelayInMillSec(retryTimes.get())
                    )
                    ensureActive()
                    connect()
                }.onFailure {
                    LogContext.log.e(tag, "Do retry failed.", it)
                }
            }
        }
    }
```

Replace `stopRetryHandler()` (lines 643-651):

Replace:
```kotlin
    private fun stopRetryHandler() {
        LogContext.log.i(tag, "stopRetryHandler()")
        //        retryHandler.removeCallbacksAndMessages(null)
        //        retryThread.interrupt()
        runCatching { retryScope.cancel() }.onFailure {
            LogContext.log.e(tag, "Cancel retry coroutine error.", it)
        }
        retryTimes.set(0)
    }
```

With:
```kotlin
    private fun stopRetryHandler() {
        LogContext.log.i(tag, "stopRetryHandler()")
        retryJob?.cancel()
        retryJob = null
        retryTimes.set(0)
    }
```

Remove the now-unused import: `import kotlinx.coroutines.cancel`

- [ ] **Step 3: Fix disconnectManually() (#2) — handle uninitialized channel**

Replace the `disconnectManually()` method (lines 467-532):

Replace:
```kotlin
    suspend fun disconnectManually(): ClientConnectStatus = suspendCancellableCoroutine { cont ->
        LogContext.log.w(
            tag,
            "===== disconnectManually() current state=${connectStatus.get().name} ====="
        )
        synchronized(this) {
            val connStatus = connectStatus.get()
            if (ClientConnectStatus.DISCONNECTED == connStatus ||
                ClientConnectStatus.UNINITIALIZED == connStatus
            ) {
                LogContext.log.w(
                    tag,
                    "Socket is not connected or already disconnected or not initialized."
                )
                cont.resume(connectStatus.get())
                return@suspendCancellableCoroutine
            } else if (ClientConnectStatus.DISCONNECTING == connectStatus.get()) {
                LogContext.log.w(tag, "Socket is disconnecting now. Stop processing.")
                cont.resume(connectStatus.get())
                return@suspendCancellableCoroutine
            }
            connectStatus.set(ClientConnectStatus.DISCONNECTING)
        }
        disconnectManually = true

        // The [DISCONNECTED] status and listener will be assigned and triggered in ChannelHandler
        // if connection has been connected before.
        // However, if connection status is [CONNECTING],
        // it ChannelHandler [channelInactive] will not be triggered.
        // In this case, we do not change the connect status.

        stopRetryHandler()
        defaultInboundHandler?.release()
        runCatching {
            // Add sync() here to make sure
            // the listener of channel disconnect method will be triggered here.
            if (::channel.isInitialized) {
                channel.disconnect().sync().addListener { f ->
                    if (f.isSuccess) {
                        LogContext.log.w(tag, "===== disconnectManually() done =====")
                        connectStatus.set(ClientConnectStatus.DISCONNECTED)
                        connectionListener.onDisconnected(this, byRemote = false)
                        cont.resume(connectStatus.get())
                    } else {
                        LogContext.log.w(tag, "===== disconnectManually() failed =====")
                        connectStatus.set(ClientConnectStatus.FAILED)
                        connectionListener.onFailed(
                            this,
                            ClientConnectListener.DISCONNECT_MANUALLY_ERROR,
                            "Disconnect manually failed"
                        )
                        cont.resume(connectStatus.get())
                    }
                }
            }
        }.onFailure {
            LogContext.log.e(tag, "disconnectManually error.", it)
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this,
                ClientConnectListener.DISCONNECT_MANUALLY_EXCEPTION,
                "Disconnect manually exception"
            )
            cont.resume(connectStatus.get())
        }
    }
```

With:
```kotlin
    suspend fun disconnectManually(): ClientConnectStatus =
        suspendCancellableCoroutine { cont ->
            val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
            fun resumeOnce(status: ClientConnectStatus) {
                if (resumed.compareAndSet(false, true)) {
                    cont.resume(status)
                }
            }

            LogContext.log.w(
                tag,
                "===== disconnectManually() " +
                    "current state=${connectStatus.get().name} ====="
            )
            synchronized(this) {
                val connStatus = connectStatus.get()
                if (ClientConnectStatus.DISCONNECTED == connStatus ||
                    ClientConnectStatus.UNINITIALIZED == connStatus
                ) {
                    LogContext.log.w(
                        tag,
                        "Socket is not connected or already " +
                            "disconnected or not initialized."
                    )
                    resumeOnce(connectStatus.get())
                    return@suspendCancellableCoroutine
                } else if (ClientConnectStatus.DISCONNECTING ==
                    connectStatus.get()
                ) {
                    LogContext.log.w(
                        tag,
                        "Socket is disconnecting now. Stop processing."
                    )
                    resumeOnce(connectStatus.get())
                    return@suspendCancellableCoroutine
                }
                connectStatus.set(ClientConnectStatus.DISCONNECTING)
            }
            disconnectManually = true

            stopRetryHandler()
            defaultInboundHandler?.release()

            if (!::channel.isInitialized) {
                LogContext.log.w(
                    tag,
                    "Channel not initialized. Set DISCONNECTED."
                )
                connectStatus.set(ClientConnectStatus.DISCONNECTED)
                connectionListener.onDisconnected(
                    this, byRemote = false
                )
                resumeOnce(connectStatus.get())
                return@suspendCancellableCoroutine
            }

            runCatching {
                channel.disconnect().addListener { f ->
                    if (f.isSuccess) {
                        LogContext.log.w(
                            tag,
                            "===== disconnectManually() done ====="
                        )
                        connectStatus.set(
                            ClientConnectStatus.DISCONNECTED
                        )
                        connectionListener.onDisconnected(
                            this, byRemote = false
                        )
                        resumeOnce(connectStatus.get())
                    } else {
                        LogContext.log.w(
                            tag,
                            "===== disconnectManually() failed ====="
                        )
                        connectStatus.set(ClientConnectStatus.FAILED)
                        connectionListener.onFailed(
                            this,
                            ClientConnectListener.DISCONNECT_MANUALLY_ERROR,
                            "Disconnect manually failed"
                        )
                        resumeOnce(connectStatus.get())
                    }
                }
            }.onFailure {
                LogContext.log.e(
                    tag, "disconnectManually error.", it
                )
                connectStatus.set(ClientConnectStatus.FAILED)
                connectionListener.onFailed(
                    this,
                    ClientConnectListener.DISCONNECT_MANUALLY_EXCEPTION,
                    "Disconnect manually exception"
                )
                resumeOnce(connectStatus.get())
            }
        }
```

- [ ] **Step 4: Fix release() — set released flag, cancel retryScope**

In the `release()` method, after `connectStatus.set(ClientConnectStatus.RELEASING)` (around line 599), add:

```kotlin
        released = true
```

And after `stopRetryHandler()` (around line 603), add:

```kotlin
        retryScope.cancel()
```

Add import: `import kotlinx.coroutines.cancel`

- [ ] **Step 5: Verify build and all tests**

```bash
./gradlew :basenetty:compileDebugKotlin && ./gradlew :basenetty:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 6: Commit**

```bash
git add basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt
git commit -m "fix(basenetty): fix retry lifecycle with SupervisorJob, cert caching, disconnectManually safety"
```

---

### Task 10: Run detekt and fix any violations

**Files:**
- Potentially any file modified in previous tasks

- [ ] **Step 1: Run detekt on the module**

```bash
./gradlew :basenetty:detekt
```

- [ ] **Step 2: Fix any violations reported**

If detekt reports MaxLineLength or other issues on the modified code, fix them (wrap lines, adjust formatting).

- [ ] **Step 3: Run full test suite to verify nothing broke**

```bash
./gradlew :basenetty:testDebugUnitTest
```

Expected: All tests PASS

- [ ] **Step 4: Commit if there were fixes**

```bash
git add -u basenetty/
git commit -m "style(basenetty): fix detekt violations in refactored code"
```

---

### Task 11: Full project verification

- [ ] **Step 1: Run full project compile**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL — ensures no other module that depends on basenetty broke.

- [ ] **Step 2: Run full project detekt**

```bash
./gradlew detekt --continue
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run basenetty tests one final time**

```bash
./gradlew :basenetty:testDebugUnitTest
```

Expected: All tests PASS
