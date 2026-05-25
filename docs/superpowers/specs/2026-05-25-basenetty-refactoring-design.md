# basenetty Module Refactoring Design

**Date:** 2026-05-25  
**Scope:** Fix 12 issues identified in code review, add unit tests  
**Branch:** `fix/basenetty-refactoring`  
**Approach:** Extract helper methods (Option B) — preserve public API, restructure internals  
**Revision:** v2 — incorporates ChatGPT review feedback (6 adjustments)

---

## Part 1: BaseNettyClient Lifecycle Fixes

### 1.1 retryScope Permanently Cancelled (#1)

**Problem:** `retryScope` is a `val` with a single `Job`. After `stopRetryHandler()` calls `retryScope.cancel()`, subsequent `retryScope.launch {}` silently does nothing.

**Fix:** Keep a stable scope with `SupervisorJob`, only cancel/replace the child job:

```kotlin
private val retryScope = CoroutineScope(SupervisorJob() + retryDispatcher)
private var retryJob: Job? = null
@Volatile private var released = false

fun doRetry() {
    if (released) return
    if (retryProcess()) return
    retryTimes.getAndIncrement()
    if (retryTimes.get() > retryStrategy.getMaxTimes()) {
        stopRetryHandler()
        connectStatus.set(ClientConnectStatus.FAILED)
        connectionListener.onFailed(...)
    } else {
        retryJob = retryScope.launch {
            delay(retryStrategy.getDelayInMillSec(retryTimes.get()))
            ensureActive()
            connect()
        }
    }
}

private fun stopRetryHandler() {
    retryJob?.cancel()
    retryJob = null
    retryTimes.set(0)
}
```

`release()` sets `released = true` and cancels the entire `retryScope`.

The `retryDispatcher` defaults to `Dispatchers.IO` but is injectable via an `internal` constructor parameter for testing (see Part 4).

### 1.2 disconnectManually() Hangs (#2)

**Problem:** When `channel` is not initialized, no branch resumes the continuation — it suspends forever.

**Fix:**
- Add `else` branch when `!::channel.isInitialized` to set state to `DISCONNECTED` and resume immediately.
- Suspend-wrap the Netty `ChannelFuture` and use `withTimeoutOrNull(5000)` instead of blocking `await()`.
- Use `AtomicBoolean.compareAndSet(false, true)` inside continuation to guarantee single resume.

```kotlin
suspend fun disconnectManually(): ClientConnectStatus =
    suspendCancellableCoroutine { cont ->
        val resumed = AtomicBoolean(false)
        fun resumeOnce(status: ClientConnectStatus) {
            if (resumed.compareAndSet(false, true)) {
                cont.resume(status)
            }
        }
        // ... state checks (use resumeOnce) ...
        stopRetryHandler()
        defaultInboundHandler?.release()

        if (!::channel.isInitialized) {
            connectStatus.set(ClientConnectStatus.DISCONNECTED)
            connectionListener.onDisconnected(this, byRemote = false)
            resumeOnce(connectStatus.get())
            return@suspendCancellableCoroutine
        }

        runCatching {
            channel.disconnect().addListener { f ->
                if (f.isSuccess) {
                    connectStatus.set(ClientConnectStatus.DISCONNECTED)
                    connectionListener.onDisconnected(this, byRemote = false)
                    resumeOnce(connectStatus.get())
                } else {
                    connectStatus.set(ClientConnectStatus.FAILED)
                    connectionListener.onFailed(
                        this, DISCONNECT_MANUALLY_ERROR, "Disconnect failed"
                    )
                    resumeOnce(connectStatus.get())
                }
            }
            // Schedule timeout fallback on the Netty event loop
            // or use coroutine-level withTimeoutOrNull wrapping the
            // entire suspendCancellableCoroutine
        }.onFailure {
            connectStatus.set(ClientConnectStatus.FAILED)
            connectionListener.onFailed(
                this, DISCONNECT_MANUALLY_EXCEPTION, "Disconnect exception"
            )
            resumeOnce(connectStatus.get())
        }
    }
```

Alternatively, wrap the entire disconnect in a coroutine-friendly helper:

```kotlin
suspend fun disconnectManually(): ClientConnectStatus {
    // ... state checks ...
    return withTimeoutOrNull(5000L) {
        suspendCancellableCoroutine<ClientConnectStatus> { cont ->
            channel.disconnect().addListener { f -> cont.resume(...) }
        }
    } ?: run {
        connectStatus.set(ClientConnectStatus.FAILED)
        connectionListener.onFailed(this, DISCONNECT_MANUALLY_ERROR, "Disconnect timeout")
        connectStatus.get()
    }
}
```

### 1.3 connect() Refactor — Extract Helper Methods (#3)

**Problem:** 100-line `connect()` mixes connection, SSL, WebSocket handshake, failure handling, and retry triggering.

**Fix:** Extract private helpers, keep `connect()` as the coordinator:

```kotlin
// New private methods:
private fun ChannelPipeline.configureSsl(socketChannel: SocketChannel)
private fun handleConnectSuccess(cont, channel)   // uses resumeOnce pattern
private fun handleConnectFailure(cont, code, msg, cause?)  // uses resumeOnce pattern
```

`handleConnectFailure` is the single point that:
1. Sets `connectStatus = FAILED`
2. Calls `connectionListener.onFailed(...)`
3. Resumes `cont` once (via `AtomicBoolean` guard)
4. Calls `doRetry()`

The `connect()` function uses the same `AtomicBoolean`-based `resumeOnce` pattern as `disconnectManually()`, ensuring every path through `connect()` resumes the continuation exactly once.

### 1.4 WSS Certificate Stream Consumed (#4)

**Problem:** `certificateInputStream` is read once; subsequent reconnects get an empty stream.

**Fix:** Cache as `ByteArray` at construction time:

```kotlin
private var certificateBytes: ByteArray? = null

// In constructor that accepts InputStream:
this.certificateBytes = certInputStream.readBytes()

fun getCertificateInputStream(): InputStream? =
    certificateBytes?.let { ByteArrayInputStream(it) }
```

Remove the existing `getCertificateInputStream()` method that manually reads and copies the stream.

---

## Part 2: Server-Side Refactoring

### 2.1 Remove Client Handshaker from Server Handler (#5)

**Problem:** `BaseServerChannelInboundHandler` uses `WebSocketClientHandshaker` (client-side class) and has dead code in `handleHttpRequest()`.

**Fix:** Delete all client-side WebSocket handshake code from the server handler:

- Remove fields: `handshaker`, `channelPromise`
- Remove method: `handleHttpRequest()`
- Remove `channelRead0`'s `FullHttpResponse` branch
- Remove `handshaker?.close()` from `channelInactive` (WebSocket close is handled by `WebSocketServerProtocolHandler`)
- Remove imports: `WebSocketClientHandshaker`, `WebSocketClientHandshakerFactory`, `WebSocketVersion`, `FullHttpResponse`, `DefaultHttpHeaders`, `ChannelPromise`, `URI`

The pipeline already has `WebSocketServerProtocolHandler` which handles the upgrade. The handler only processes `WebSocketFrame` messages.

### 2.2 Separate Server Lifecycle State from Client Events (#6)

**Problem:** `connectState` is a single `AtomicReference` overwritten by each client connect/disconnect event.

**Fix:** Keep `CLIENT_CONNECTED` and `CLIENT_DISCONNECTED` in the enum but mark them `@Deprecated`:

```kotlin
enum class ServerConnectStatus {
    UNINITIALIZED,
    STARTED,
    STOPPED,
    @Deprecated("Use ServerConnectListener callbacks instead. Will be removed in a future release.")
    CLIENT_CONNECTED,
    @Deprecated("Use ServerConnectListener callbacks instead. Will be removed in a future release.")
    CLIENT_DISCONNECTED,
    FAILED
}
```

Stop writing `CLIENT_CONNECTED` / `CLIENT_DISCONNECTED` to `connectState` in the server handler. The `connectState` field now only holds `UNINITIALIZED`, `STARTED`, `STOPPED`, or `FAILED`. Client connection events are reported exclusively via `connectionListener.onClientConnected/onClientDisconnected` callbacks.

### 2.3 Move `clients` ChannelGroup to BaseNettyServer (#7)

**Problem:** `clients` is a mutable field in `@Sharable` handler — multi-client state conflicts.

**Fix:** Move `clients` to `BaseNettyServer` as `internal val`:

```kotlin
// BaseNettyServer:
internal val clients: ChannelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

// BaseServerChannelInboundHandler accesses it via netty reference:
abstract class BaseServerChannelInboundHandler<T>(
    private val netty: BaseNettyServer
) : SimpleChannelInboundHandler<T>(), ReadSocketDataListener<T> {
    // Use netty.clients instead of own field
}
```

The handler's existing `val clients` field is removed; all references use `netty.clients`.

### 2.4 Remove sync() from exceptionCaught (#11)

**Problem:** `ctx.close().syncUninterruptibly()` / `ctx.close().sync()` in event loop thread can deadlock.

**Fix in BaseServerChannelInboundHandler:**
```kotlin
override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    // ... logging ...
    ctx.close()  // fire-and-forget; channelInactive handles cleanup
    // Do NOT update connectState or call listener here —
    // channelInactive will handle it
}
```

**Fix in BaseClientChannelInboundHandler:**
```kotlin
override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    // ... logging, set caughtException = true ...
    ctx.close()  // no sync(); channelInactive/handlerRemoved will handle retry
    // State updates remain after ctx.close() — they execute immediately,
    // ctx.close() just initiates async close
}
```

---

## Part 3: Decoder, API Cleanup, EventBus

### 3.1 CustomSocketByteStreamDecoder Threshold (#8)

**Problem:** `if (bufLen < 6)` requires 6 bytes minimum but the length prefix is only 4 bytes.

**Fix:**
```kotlin
override fun decode(ctx: ChannelHandlerContext, inBuf: ByteBuf, out: MutableList<Any>) {
    if (inBuf.readableBytes() < Int.SIZE_BYTES) return  // 4 bytes for length field

    inBuf.markReaderIndex()
    val dataLen = inBuf.readIntLE()

    // Validate frame length
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

companion object {
    const val MAX_FRAME_SIZE = 10 * 1024 * 1024  // 10 MB safety limit
}
```

### 3.2 byteOrder Parameter Cleanup (#9)

**Problem:** `byteOrder` parameter is accepted but has zero effect — both branches call `cmd.toHexString()` identically.

**Fix:** Keep the parameter in public `executeCommand` / `executePingCommand` for API compatibility. Remove it from the internal `executeUnifiedCommand` private method. Add KDoc clarification:

```kotlin
/**
 * @param byteOrder **Deprecated — has no effect.** The byte array is sent as-is
 *   regardless of byte order. This parameter will be removed in a future release.
 */
@JvmOverloads
fun executeCommand(
    cmd: Any?,
    cmdDesc: String? = null,
    cmdTag: String = tag,
    showContent: Boolean = true,
    showLog: Boolean = true,
    fullOutput: Boolean = false,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,  // kept for compatibility
) = executeUnifiedCommand(cmdTag, cmdDesc, cmd, isPing = false, showContent, showLog, fullOutput)
```

Internal `executeUnifiedCommand` drops `byteOrder` and simplifies:
```kotlin
val hex: String? = if (showContent) cmd.toHexString() else null
```

Apply the same change to both `BaseNettyClient` and `BaseNettyServer`.

### 3.3 EventBus Thread Safety (#10)

**Problem:** `ConcurrentHashMap` + `MutableList` (ArrayList) is not fully thread-safe.

**Fix:**

```kotlin
private val handlers: ConcurrentMap<String, CopyOnWriteArrayList<EventBusHandler>> =
    ConcurrentHashMap()

private fun addHandler(address: String, handler: EventBusHandler) {
    handlers.computeIfAbsent(address) { CopyOnWriteArrayList() }.add(handler)
}

fun processReplyHandler(address: String, handle: (h: EventBusHandler) -> Unit) {
    replyHandlers.remove(address)?.let { handle(it) }
}
```

`CopyOnWriteArrayList` is appropriate here because handlers are registered infrequently but read/iterated frequently.

---

## Part 4: Regression Tests

### Test Framework
- JUnit 5 (Jupiter) — project standard
- Netty EmbeddedChannel for handler/decoder tests
- Mockk for mocking listeners and loggers
- kotlinx-coroutines-test for coroutine testing (with `StandardTestDispatcher`)

### Testability: Injectable Dispatcher

`BaseNettyClient` gains an `internal` constructor parameter for the retry dispatcher:

```kotlin
abstract class BaseNettyClient protected constructor(
    // ... existing params ...
    internal val retryDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseNetty {
    private val retryScope = CoroutineScope(SupervisorJob() + retryDispatcher)
    // ...
}
```

Tests inject `StandardTestDispatcher` so that `delay()` in `doRetry()` is virtual-time-controlled. This avoids slow real-time waits and flaky timing.

### Test Classes

| File | Tests |
|------|-------|
| `CustomSocketByteStreamDecoderTest.kt` | Empty buffer returns nothing; 1-byte payload decodes correctly; normal multi-byte frame; half-packet reassembly (two writes); negative length closes channel; oversized frame closes channel |
| `EventBusTest.kt` | Register + processHandlers; concurrent register from multiple threads (no ConcurrentModificationException); unregister removes handler; clearAllHandlers; send with reply handler; processReplyHandler removes after use |
| `RetryStrategyTest.kt` | ConstantRetry returns fixed delay; ExponentRetry returns 2^(n-1) * base * 1000; max times boundary values |
| `BaseNettyClientRetryTest.kt` | Retry fires after connect failure (using TestDispatcher + advanceTimeBy); retry stops after maxTimes; retry works after stopRetryHandler + new connect cycle; released client does not retry |

### Test Location
`basenetty/src/test/kotlin/com/leovp/basenetty/`

### Test Dependencies (basenetty/build.gradle.kts)
```kotlin
testImplementation(libs.bundles.test)
testRuntimeOnly(libs.bundles.test.runtime.only)
```

---

## File Change Summary

| File | Action |
|------|--------|
| `BaseNettyClient.kt` | Modify: retryScope with SupervisorJob, retryJob, injectable dispatcher, extract helpers, cache cert bytes, add released flag, fix disconnectManually with resumeOnce + withTimeoutOrNull |
| `BaseClientChannelInboundHandler.kt` | Modify: `ctx.close().sync()` -> `ctx.close()` |
| `BaseServerChannelInboundHandler.kt` | Modify: remove WebSocketClientHandshaker/handleHttpRequest/FullHttpResponse, use netty.clients, remove sync(), stop writing CLIENT_CONNECTED/CLIENT_DISCONNECTED to connectState |
| `BaseNettyServer.kt` | Modify: add `internal val clients`, update connectState usage |
| `BaseNetty.kt` | Modify: add @Deprecated to CLIENT_CONNECTED/CLIENT_DISCONNECTED |
| `CustomSocketByteStreamDecoder.kt` | Modify: threshold 6->4, add length validation, add MAX_FRAME_SIZE |
| `EventBus.kt` | Modify: CopyOnWriteArrayList, computeIfAbsent, fix processReplyHandler |
| `basenetty/build.gradle.kts` | Modify: add test dependencies |
| `CustomSocketByteStreamDecoderTest.kt` | New |
| `EventBusTest.kt` | New |
| `RetryStrategyTest.kt` | New |
| `BaseNettyClientRetryTest.kt` | New |

---

## Risks and Mitigations

1. **ServerConnectStatus enum @Deprecated values** — External code using `CLIENT_CONNECTED`/`CLIENT_DISCONNECTED` will get deprecation warnings but will not break. Gives consumers time to migrate to listener-based approach.

2. **Behavior change in exceptionCaught** — Removing `sync()` means exception handling becomes asynchronous. Mitigated: `handlerRemoved`/`channelInactive` already handles state transitions and retry; the sync was only blocking the event loop unnecessarily.

3. **Certificate caching memory** — Large certificates stay in memory. Acceptable: certificates are typically small (< 10 KB).

4. **Injectable dispatcher changes constructor signature** — Marked `internal` with default value, so only test code within the module can use it. No public API change.
