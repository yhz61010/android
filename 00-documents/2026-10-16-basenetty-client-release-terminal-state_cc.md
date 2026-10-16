# basenetty 客户端 `release()` 终态不对称：问题、原因与修改方案（提案，未改代码）

| 项 | 值 |
|---|---|
| 日期 | 2026-10-16 |
| 状态 | **提案。本文只记录分析与方案，代码一行未动** |
| 分支 / 基线 | `review/native-modules`，HEAD `d3058e2bc`，工作树含 2026-10-14 至 10-16 的全部未提交改动 |
| 涉及文件 | `basenetty/src/main/kotlin/com/leovp/basenetty/framework/client/BaseNettyClient.kt`（只读分析） |
| 上游文档 | `00-documents/2026-10-15-audio-netty-teardown-review-package_cc.md` §C.3 / §J；`00-documents/2026-09-20-netty-shutdown-nonblocking_cc.md` §11–§13 |
| 行号 | 以 2026-10-16 工作树为准 |

---

## 1. 问题从哪来

第六轮审查（审查包 §J）把服务端 `BaseNettyServer.stopServer()` 的形状收口为：

> 过了状态守卫之后，**无论 netty 关闭成功、超时还是抛异常**，`finally` 都把状态置为 `STOPPED` 并回调
> `onStopped()`（审查包 §I.2 / netty 记录 R22），`releaseInboundHandler()` 也在同一个 `try` 内（R23）。

客户端 `BaseNettyClient.release()` 在同一批改动里做了 `NonCancellable` 包裹（R18）和 `finally` 里无条件
`shutdownGracefully()`（R20），但**状态迁移没有做同样的处理**：只有成功路径把状态置为 `UNINITIALIZED`，
超时与失败两条路径让实例停在 `RELEASING`。两路审查者都没有把这列为问题（它不泄漏资源，见 §3），
本文作者在汇报时把它作为"可选项"提出，所有者要求把内容、原因与方案单独成文。

## 2. 现状代码

### 2.1 客户端 `release()` 三段（`BaseNettyClient.kt`）

守卫与 `NonCancellable` 入口（L853–872）：

```kotlin
    suspend fun release(): Boolean {
        LogContext.log.w(
            tag,
            "===== release() current state=${connectStatus.get().name} ====="
        )
        synchronized(this) {
            if (ClientConnectStatus.UNINITIALIZED == connectStatus.get() ||
                ClientConnectStatus.RELEASING == connectStatus.get() ||
                ClientConnectStatus.DISCONNECTING == connectStatus.get()
            ) {
                LogContext.log.w(
                    tag,
                    "Releasing now or already released or disconnecting or not initialized"
                )
                return false
            }
            connectStatus.set(ClientConnectStatus.RELEASING)
        }
        return withContext(NonCancellable) { releaseNow() }
    }
```

关闭体（L874–907），注意 `nettyReleased == null`（超时）直接 `return false`，状态不动：

```kotlin
    private suspend fun releaseNow(): Boolean {
        released = true
        disconnectManually = true
        LogContext.log.w(tag, "Releasing retry handler...")
        resumeConnectContinuation(connectStatus.get())
        stopRetryHandler()
        retryScope.cancel()
        //        retryThread.quitSafely()

        // Guarded like every other step: this hands control to subclass code, and letting it
        // throw here would skip the channel and the event loop group entirely.
        LogContext.log.w(tag, "Releasing default socket handler first...")
        try {
            defaultInboundHandler?.release()
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release inbound handler error.", e)
        }
        defaultInboundHandler = null
        channelInitializer = null

        // Bounded here, at the resource boundary, for the same reason BaseNettyServer.stopServer()
        // is: callers chain this into a teardown sequence that deliberately carries no timeout of
        // its own, so a release that cannot finish must not hold that sequence forever.
        val nettyReleased = withTimeoutOrNull(NETTY_RELEASE_TIMEOUT_IN_MILLS) { releaseNetty() }
        if (nettyReleased == null) {
            LogContext.log.e(
                tag,
                "Netty release did not finish within ${NETTY_RELEASE_TIMEOUT_IN_MILLS}ms. " +
                    "Check whether closing the channel, not the event loop, used up the budget."
            )
            return false
        }
        return nettyReleased
    }
```

netty 层（L909–934），`UNINITIALIZED` **只在成功分支**写入：

```kotlin
    private suspend fun releaseNetty(): Boolean {
        try {
            closeChannelAndAwait()
        } finally {
            // Reached on the timeout too, as the cancellation it carries passes through here. The
            // request does not suspend, so a close that used up the whole budget still leaves the
            // group shutting down instead of alive for good - same shape as
            // BaseNettyServer.shutdownNetty().
            //
            // Quiet period 0: it exists to let in-flight work settle before the loop dies, which
            // buys nothing for a client whose channel is already closed.
            LogContext.log.w(tag, "Releasing socket...")
            workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        }
        return try {
            workerGroup.terminationFuture().awaitCompletion()
            connectStatus.set(ClientConnectStatus.UNINITIALIZED)
            LogContext.log.w(tag, "=====> Socket released <=====")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release socket error.", e)
            false
        }
    }
```

### 2.2 服务端对照（`BaseNettyServer.kt` L352–375，第六轮之后）

```kotlin
        connectState.set(ServerConnectStatus.UNINITIALIZED)
        withContext(NonCancellable) {
            try {
                // Inside the try as well: releaseInboundHandler() only catches Exception, and an
                // Error out of subclass code must not leave the state on UNINITIALIZED either.
                releaseInboundHandler()
                shutdownBounded { shutdownNetty() }
            } finally {
                // In a finally, because the KDoc above promises these two run regardless.
                // ...
                LogContext.log.w(tag, "=====> Server released <=====")
                connectState.set(ServerConnectStatus.STOPPED)
                try {
                    connectionListener.onStopped()
                } catch (e: Exception) {
                    LogContext.log.e(tag, "onStopped() error.", e)
                }
            }
        }
        return@withLock true
```

### 2.3 三条路径的终态对照

| 路径 | 服务端 `stopServer()` | 客户端 `release()` |
|---|---|---|
| netty 在预算内确认终止 | `STOPPED`，返回 `true` | `UNINITIALIZED`，返回 `true` |
| 3 秒预算耗尽（`withTimeoutOrNull` 返回 null） | `STOPPED`，返回 `true`（超时只记错误日志） | **`RELEASING`**，返回 `false` |
| `terminationFuture()` 等待抛非取消异常 | `STOPPED`，异常继续向上抛 | **`RELEASING`**，返回 `false` |
| `close()` / `shutdownGracefully()` 自己抛 | `STOPPED`，异常继续向上抛 | **`RELEASING`**，异常继续向上抛（`releaseNow()` 没有 `finally`） |

## 3. 这为什么算问题，以及为什么不算泄漏

**不是资源泄漏。** 超时与失败两条路径上，`workerGroup.shutdownGracefully()` 已在 `releaseNetty()` 的
`finally` 里发出（R20），`released = true` 已置位，retry scope 已取消，inbound handler 已释放。netty 的
`shutdownGracefully` 幂等，再调一次也不会多做什么。两路审查者据此没有把它列为缺陷，这个判断是对的。

**是契约不一致。** 具体后果有四条：

1. **后续 `release()` 被守卫拒绝。** 停在 `RELEASING` 的实例再调 `release()`，守卫返回 `false` 并打
   "Releasing now or already released ..." 日志——而此时根本没有释放在进行。调用方（例如
   `AudioSender.stop()` 的最后一步）看到 `false` 无法区分"正在释放"和"上次释放超时"。
2. **`connectStatus` 对外暴露的是一个"进行中"的私有状态。** `ClientConnectStatus.RELEASING` 的 KDoc
   写着 "PRIVATE status for library, DO NOT use it in your codes"，但一个已经结束的 `release()` 把它
   永久留在了公开可读的 `connectStatus` 上。
3. **和服务端的承诺不对称。** `stopServer()` 的 KDoc 承诺"状态迁移与 `onStopped()` 无论如何都会执行"
   （R22 为此把它们放进 `finally`）；`release()` 的 KDoc（L834–852）只说"过了守卫后在 `NonCancellable`
   下运行、超时仍然生效"，没有对终态作承诺，实现也确实没给。两个类由同一批人在同一周改成同一形状，
   却在这一点上分叉，之后维护时容易被当成有意为之。
4. **`releaseNow()` 没有 `finally`。** `closeChannelAndAwait()` 内部已 try/catch，但
   `workerGroup.shutdownGracefully()` 自己抛（极罕见）会穿出 `releaseNetty()` 的 `finally` 再穿出
   `releaseNow()`，状态同样停在 `RELEASING`。服务端对应位置（R22）已经补了 `finally`。

## 4. 修改方案（主项）

### 4.1 原则

照服务端的形状改：**状态落到终态进 `finally`，返回值继续如实报告 netty 是否在预算内确认终止。**
不吞异常，不改返回值语义，不改超时值，不改守卫。

### 4.2 代码

`releaseNow()`：把 netty 释放段包进 `try`，`finally` 写终态。

```kotlin
    private suspend fun releaseNow(): Boolean {
        released = true
        disconnectManually = true
        LogContext.log.w(tag, "Releasing retry handler...")
        resumeConnectContinuation(connectStatus.get())
        stopRetryHandler()
        retryScope.cancel()

        // Guarded like every other step: this hands control to subclass code, and letting it
        // throw here would skip the channel and the event loop group entirely.
        LogContext.log.w(tag, "Releasing default socket handler first...")
        try {
            defaultInboundHandler?.release()
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release inbound handler error.", e)
        }
        defaultInboundHandler = null
        channelInitializer = null

        try {
            // Bounded here, at the resource boundary, for the same reason
            // BaseNettyServer.stopServer() is: callers chain this into a teardown sequence that
            // deliberately carries no timeout of its own, so a release that cannot finish must
            // not hold that sequence forever.
            val nettyReleased =
                withTimeoutOrNull(NETTY_RELEASE_TIMEOUT_IN_MILLS) { releaseNetty() }
            if (nettyReleased == null) {
                LogContext.log.e(
                    tag,
                    "Netty release did not finish within ${NETTY_RELEASE_TIMEOUT_IN_MILLS}ms. " +
                        "Check whether closing the channel, not the event loop, used up the budget."
                )
                return false
            }
            return nettyReleased
        } finally {
            // Same shape as BaseNettyServer.stopServer(): the state transition is promised
            // regardless of how the netty release ended. Left on RELEASING, every later
            // release() would return false at the guard while releaseNetty()'s own finally has
            // already requested the group shutdown - there is nothing a retry could add, and
            // connectStatus would keep showing a private in-progress state for good.
            connectStatus.set(ClientConnectStatus.UNINITIALIZED)
            LogContext.log.w(tag, "=====> Socket released <=====")
        }
    }
```

`releaseNetty()`：成功分支删掉状态写入与那行日志，其余不变。

```kotlin
    private suspend fun releaseNetty(): Boolean {
        try {
            closeChannelAndAwait()
        } finally {
            // （注释不变）
            LogContext.log.w(tag, "Releasing socket...")
            workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        }
        return try {
            workerGroup.terminationFuture().awaitCompletion()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogContext.log.e(tag, "Release socket error.", e)
            false
        }
    }
```

改动量：`releaseNow()` 多一层 `try/finally`（约 +8 行），`releaseNetty()` 少 2 行。无新 import。
`releaseNow()` 现约 45 行，改后约 53 行，仍远低于 detekt `LongMethod` 的 150。`ReturnCount` 不变（两个
`return`）。

### 4.3 语义与安全性论证

| 关注点 | 结论 | 依据 |
|---|---|---|
| 返回值 | 不变：成功 `true`，超时或 `terminationFuture` 失败 `false` | `return` 语句未动，只是外面多了 `finally` |
| 异常 | 不变：`close()` / `shutdownGracefully()` / `awaitCompletion()` 抛的非取消异常继续向上传播 | `finally` 不捕获 |
| 终态 | 三条路径都 `UNINITIALIZED`，与枚举 KDoc "Only you release socket, it will be in this status" 一致 | §4.2 |
| 提前置 `UNINITIALIZED` 会不会放行重连 | 不会。`connect()` 的守卫（L383–409）只拒绝 `CONNECTING` / `CONNECTED` / `RELEASING` / `DISCONNECTING`，**从不按 `UNINITIALIZED` 拒绝**；释放后禁止重连靠的是已终止或正在关闭的 group 让 `bootstrap.connect()` 抛 `RejectedExecutionException`，L494 分支已处理。超时路径上 `shutdownGracefully()` 已发出，group 处于 shutting-down，新任务同样被拒 | `connect()` 守卫；`SingleThreadEventExecutor.isShuttingDown()` |
| retry 链 | 不受影响：`released = true` 在 `releaseNow()` 第一行就置位，`doRetry()`（L783–788）按它短路 | 代码 |
| 与 `disconnectManually()` 的交互 | 不变：它的守卫按 `UNINITIALIZED` 拒绝（L629–631），改后超时的实例也被拒绝，之前（`RELEASING`）同样被拒绝 | 守卫 |
| `-ea` / DEBUG 模式 | 无影响，没有跨 `withContext` 比较异常身份 | — |

### 4.4 随动改动（共四处）

1. **`release()` KDoc**（L834–852）追加一段，例如：
   > Whatever the netty release does, this returns with [connectStatus] on
   > [ClientConnectStatus.UNINITIALIZED]; a timeout or a failed termination future only changes
   > the return value to `false`. A later release() therefore returns `false` at the guard, which
   > is correct: the group shutdown has already been requested.
2. **`CHANGELOG.md`** `basenetty 生命周期改为挂起、非阻塞` 条目下 `release()` 那条补一句：
   "超时或失败后状态同样落到 `UNINITIALIZED`（此前停在私有状态 `RELEASING`，后续 `release()` 被守卫
   拒绝且无法分辨原因）"。
3. **测试**（`BaseNettyLifecycleTest.kt`）新增一例，复用既有用例
   `disconnectManually fails instead of hanging when channel future never completes`（L75–94）的手法：
   注入一个 `close()` 永不完成的 mock `Channel`，让 `closeChannelAndAwait()` 吃光 3 秒预算。

   ```kotlin
   @Test
   fun `release that times out still lands on UNINITIALIZED and reports false`(): Unit =
       runBlocking {
           val client = TestClient(listener = RecordingClientListener())
           client.connectStatus.set(ClientConnectStatus.CONNECTED)

           val backingChannel = EmbeddedChannel()
           val channel = mockk<Channel>()
           every { channel.close() } returns DefaultChannelPromise(backingChannel)
           setChannel(client, channel)

           // Eats the whole NETTY_RELEASE_TIMEOUT_IN_MILLS budget: close() never completes, so
           // the timeout is what ends releaseNetty(), through its finally.
           assertFalse(client.release())

           assertEquals(ClientConnectStatus.UNINITIALIZED, client.connectStatus.get())
           assertFalse(client.release())
       }
   ```

   该用例真实等待 3 秒（类级 `@Timeout` 60 秒内），改前它在第二个断言处失败（状态为 `RELEASING`），
   是一个先红后绿的用例。`mockk` 的 `Channel` 其余方法未被调用（`closeChannelAndAwait()` 只调
   `close()`），与既有用例一致。
4. **文档**：netty 记录新增一条 R 编号（现有到 R26），审查包 §J 追加一项；审查包 §C.3.4 的
   `release()` 拆分代码块随之刷新。

## 5. 相关但更大的不对称：`release()` 对从未连接的客户端直接拒绝

分析主项时暴露出一个独立且影响更大的问题，记录在此，**不建议与主项合并实施**。

### 5.1 现象

`release()` 守卫把 `UNINITIALIZED` 一概拒绝（L859），而 `UNINITIALIZED` 同时是：

- 构造后的**默认态**（枚举 KDoc："It's the default status after initializing netty client object"）；
- `release()` 成功后的**终态**（同一段 KDoc："Only you release socket, it will be in this status"）。

于是一个已构造、但 `connect()` 尚未把状态推进到 `CONNECTING` 的客户端调 `release()`，直接返回 `false`，
什么都不做。`NioEventLoopGroup` 是属性初始化器建的（L247 `private val workerGroup = NioEventLoopGroup()`），
构造即存在，这种情况下它**永久泄漏**。这正是服务端 "stop before start" 的客户端翻版，服务端在本批改动里
用 `releaseUnstartedServer()` 收了口（审查包 §C.2.5、netty 记录 R15），客户端没有。

### 5.2 现在是怎么绕过去的

- `AudioSender.stop()` 第 1 步的注释（第六轮 J.9 补齐的那段）论证：`senderClient = client` 到
  `connect()` 内置 `CONNECTING` 之间没有挂起点，且 start() 的 job 在最后一步前已被 cancel-and-join，
  所以最后一步看到的非 null `senderClient` 不可能是 `UNINITIALIZED`。这是调用方替库守住的不变量，
  注释已警告"在那一段加挂起点会重新打开 `NioEventLoopGroup` 泄漏"。
- demo `WebSocketClientActivity.onDestroy()` 注释里的"窄窗口"：connect 协程已派发、尚未赋值
  `webSocketClient`，`release()` 对 null 无操作，之后建出的 client 无人释放。即便字段非 null，若它还
  没到 `CONNECTING`，`release()` 也会被守卫拒绝——同一个根因。

### 5.3 两个方案

**方案 B（推荐，最小改动）**：守卫改用已有的 `released` 标志判断"已释放"，`UNINITIALIZED` 不再被拒。

```kotlin
        synchronized(this) {
            val status = connectStatus.get()
            if (released ||
                ClientConnectStatus.RELEASING == status ||
                ClientConnectStatus.DISCONNECTING == status
            ) {
                LogContext.log.w(tag, "Releasing now or already released or disconnecting")
                return false
            }
            released = true
            connectStatus.set(ClientConnectStatus.RELEASING)
        }
        return withContext(NonCancellable) { releaseNow() }
```

并把 `releaseNow()` 第一行的 `released = true` 删掉（已在守卫内置位；`released` 是 `@Volatile`，
L238–239，守卫在 `synchronized(this)` 内读写，两者都够）。

- 从未连接的客户端：`channel` 未初始化 → `closeChannelAndAwait()` 直接返回 → `shutdownGracefully` →
  等 `terminationFuture` → `UNINITIALIZED`，返回 `true`。group 不再泄漏。
- 已释放的客户端：`released == true` → 守卫拒绝，返回 `false`，与现在一致。
- 终态仍是 `UNINITIALIZED`，枚举 KDoc 不用改。
- 既有测试 `connect failure updates stored state to failed` 末尾新增的 `assertTrue(client.release())`
  不受影响（`FAILED` 本来就放行）。
- 新测试：`TestClient` 构造后直接 `assertTrue(client.release())`，断言 `UNINITIALIZED`，再
  `assertFalse(client.release())`。

**方案 A（干净但破坏性）**：给 `ClientConnectStatus` 新增终态 `RELEASED`，`release()` 落 `RELEASED`，
守卫拒绝 `RELEASED` / `RELEASING` / `DISCONNECTING`，`UNINITIALIZED` 回归"仅默认态"的单一含义。
按 CLAUDE.md「API 演进约定」允许破坏，但下游所有对 `ClientConnectStatus` 做 `when` 的代码都要补分支，
`connectStatus` 的对外语义变化要进 CHANGELOG 破坏性变更，`AudioSender` / demo 的 `when` 也要同步。收益
是语义清晰，代价是改动面跨模块。

**推荐 B**。原因：B 消掉的是真实泄漏，改动局限在一个函数的守卫；A 解决的是命名含义重载，属于另一个
层面的整理，可以在 B 之后单独评估。

### 5.4 B 之后仍存在的残余

- `DISCONNECTING` 期间调 `release()` 仍返回 `false`。顺序调用不受影响（`disconnectManually()` 是挂起函数，
  返回时已结束）；**并发**的 disconnect 与 release 会让 release 被拒，调用方得再调一次。这是 HEAD 已有
  的行为，审查包 §C.3.5 已列为既有遗留。
- `WebSocketClientActivity` 里"字段还是 null"的那半个窗口是 Activity 自己的问题，库侧无法解决。

## 6. 风险与未验证

- 两个方案都未编译、未跑测试（本仓库约定：不在本机跑 Gradle）。§4.4 与 §5.3 的测试是先红后绿的设计，
  实施时应先跑红再改代码。
- 主项（§4）的 3 秒测试依赖真实时间；若后续把 `NETTY_RELEASE_TIMEOUT_IN_MILLS` 改为可注入，可以缩短。
- §5 方案 B 改了守卫的判定依据，`release()` 对 `UNINITIALIZED` 的放行会让"构造后立刻 release"从
  no-op 变成真实关闭 group，耗时由 0 变为最多 2 秒（`shutdownGracefully(0, 2, SECONDS)` 的 timeout）。
  对 demo 的影响：`WebSocketClientActivity.onDestroy()` 的 `release()` 在窄窗口里会真正释放，而不是
  返回 `false`，这是期望的变化。

## 7. 需要所有者决定的点

| # | 决定 | 建议 |
|---|---|---|
| 1 | 是否实施 §4（终态进 `finally`） | 实施。改动小，补齐与服务端的对称，附先红后绿的测试 |
| 2 | 是否实施 §5 方案 B（守卫改用 `released`） | 实施，但作为独立提交（`fix(basenetty): release a never-connected client's event loop group`），与 §4 分开 |
| 3 | 是否推进 §5 方案 A（新增 `RELEASED` 态） | 暂缓。B 落地后若仍觉得 `UNINITIALIZED` 双重含义碍事再做 |
| 4 | §4.4 第 3 项的 3 秒真实等待是否可接受 | 可接受；`BaseNettyLifecycleTest` 已有同量级的真实等待用例 |

两项若都实施，提交切分建议：§4 并入审查包 §H 的 basenetty 提交（它是同一形状的补齐），§5 B 单独一个
提交，CHANGELOG 分别记一条。
