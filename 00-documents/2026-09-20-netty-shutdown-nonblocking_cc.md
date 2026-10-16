# basenetty 关闭路径去阻塞化（2026-09-20）

> **状态：源码改动已落地，未在本机编译或运行。** 所有 Gradle 与真机结论均标记为"未验证"，
> 需按本文 §8 的清单在本地执行。

## 1. 起因

在对 `audio` 模块与 demo 音频路径做性能/并发审查时，发现一条与"确定性关闭"直接冲突的实现：
`BaseNettyServer.stopServer()` 用 `syncUninterruptibly()` 阻塞调用线程，而 demo 里所有调用点
都在 `Dispatchers.IO` 协程中 —— 线程语义与协程语义错配。

该方法自己的 KDoc 写着：

> Stop and release server using **syncUninterruptibly** method.(Full release will cost almost 4s.)
> So you'd better NOT call this method in main thread.

"almost 4s"不是玄学，是可以算出来的。

## 2. 问题的准确诊断

改动前 `BaseNettyServer.stopServer()` 里有三处阻塞：

```kotlin
close().syncUninterruptibly()                          // 关 channel
bossGroup.shutdownGracefully().syncUninterruptibly()   // 串行
workerGroup.shutdownGracefully().syncUninterruptibly() // 串行
```

三个叠加问题：

1. **`shutdownGracefully()` 无参版用 Netty 默认值**：quiet period = 2 秒、timeout = 15 秒。
   quiet period 的语义是"事件循环在这段时间内没有新任务才真正关闭"。
2. **两个 group 串行关闭**：`bossGroup` 等满自己的 quiet period 之后才轮到 `workerGroup`。
   两者**互相独立**，串行等于把两段 quiet period 相加 ⇒ 下限 2 + 2 = 4 秒。这就是 KDoc 里
   "almost 4s"的来源。
3. **`syncUninterruptibly()` 让取消失效**。它的问题不是"丢失中断"（Netty 会在等待结束时把
   interrupt 标志补回线程），而是**中断不起作用**：协程取消 → 线程中断 → 它照样等满全程。
   叠加上 audio teardown 设计把关闭体放进 `teardownGate.run { withContext(NonCancellable) }`
   且门闩按设计不设超时，结果就是 Activity 退出路径硬等约 4 秒，所有并发等待者一起等。

同样的 `close().syncUninterruptibly()` 也存在于 `BaseNettyClient.release()`，旁边还留着一段
困惑注释：

```kotlin
// closeFuture().syncUninterruptibly()
// syncUninterruptibly() will get stuck here.
// Why???
```

## 3. 关键发现：客户端已经做对了一半

`BaseNettyClient.release()` 关闭 `workerGroup` 时用的**不是** `syncUninterruptibly()`，而是
listener 回调：

```kotlin
workerGroup.shutdownGracefully() // syncUninterruptibly() will not get stuck here.
    .addListener { f -> ... cont.resume(true) }
```

也就是说，**"用 listener 而不是 sync"这个做法在本仓库已有先例，只是服务端没跟上，客户端自己
也只做了一半**（`close()` 那处仍在 sync）。因此本次改动不是引入新设计，而是把两边补齐并抽出
统一桥接。

## 4. 改了什么

### 4.1 新增 `basenetty/.../framework/base/NettyFutureExt.kt`

统一的 Netty `Future` → 协程桥接：

```kotlin
internal suspend fun <V> Future<V>.awaitCompletion() {
    if (isDone) {
        failureOrNull()?.let { throw it }
        return
    }
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        val listener = GenericFutureListener<Future<V>> { future ->
            val failure = future.failureOrNull()
            if (failure == null) cont.resume(Unit) else cont.resumeWithException(failure)
        }
        addListener(listener)
        cont.invokeOnCancellation { removeListener(listener) }
    }
}

private fun Future<*>.failureOrNull(): Throwable? = when {
    isSuccess -> null
    isCancelled -> IllegalStateException("Netty future was cancelled")
    else -> cause() ?: IllegalStateException("Netty future failed without a cause")
}
```

**为什么这么写：**

- **不阻塞线程。** 等待期间 `Dispatchers.IO` 的线程被释放回池，而不是被 park 住。
- **取消只摘 listener，不取消 future。** 被等待的对象（channel close / event loop shutdown）
  已经在执行中，半途放弃只会把调用方本想释放的资源留在原地。这一点写进了 KDoc，避免后续
  被"优化"成 `future.cancel(true)`。
- **已完成的 future 走 `isDone` 快速路径，不挂起**（2026-10-14 复审 R7，HIGH）。这不是优化：
  `DefaultPromise.addListener()` 对已完成的 future 会把通知任务投递到该 future 的 executor；
  若那个 event loop 已终止，Netty 只记一条 "Failed to submit a listener notification task"，
  listener **永不触发**。`stopServer()` 之后再 `startServer()`（bind 返回的注册 promise 挂在已死的
  boss loop 上）、同一实例第二次 `stopServer()`（`close()` 投递到已死的 loop）都会产出这样的
  future——前者会在 `lifecycleMutex` 内永久挂起，后者白等 3 秒超时。`sync()` 没有这个问题，因为
  `await()` 先查 `isDone()`、`rethrowIfFailed()` 直接抛 cause。本文初稿那句"已完成的 future 会
  内联回调"是错的：不在事件循环线程上时它是经 executor 派发的，而派发可能失败。
- **被取消的 Netty future 映射为 `IllegalStateException`**（R8）。`Future.cause()` 对取消的
  future 返回 `java.util.concurrent.CancellationException`，在 JVM 上就是协程那一个类；原样抛出会
  让 `awaitOrLog()` / `releaseNetty()` 把它当成调用方自己被取消而重抛。
- 之前这类桥接**内联散落**在 `BaseNettyClient` 的 `connect()`、握手、`disconnectManually()`、
  `release()` 四处，没有可复用入口；抽出后服务端也能用。
- **`internal` 而非 `public`**（复审 R5）。`basenetty` 是 JitPack 发布模块，而两个调用方都在
  本模块内，没有理由对下游承诺这个签名。
- **`cause()` 加了 null 兜底**（复审 R6）。它是平台类型；对标准 `DefaultPromise` 而言，
  能走到 listener 就意味着 `isDone()`，此时 `!isSuccess` ⇒ cause 必非 null，所以**当前不会
  NPE**。但若将来遇到返回 null 的 `Future` 实现，NPE 会在 netty 线程里抛出、continuation
  永不恢复——那是最难查的一类挂起。兜底成本为零。

### 4.2 `BaseNettyServer.stopServer()`：改 suspend + 并行关闭 + quiet period 0 + 资源层超时

```kotlin
suspend fun stopServer(): Boolean {
    // ... 守卫与状态迁移不变 ...
    if (withTimeoutOrNull(NETTY_SHUTDOWN_TIMEOUT_IN_MILLS) { shutdownNetty() } == null) {
        LogContext.log.e(tag, "Netty shutdown did not finish within ...")
    }
    // ... onStopped() 与返回值不变 ...
}

private suspend fun shutdownNetty() {
    serverChannel.close().awaitOrLog("Close channel")

    val boss = bossGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    val worker = workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
    boss.awaitOrLog("Shutdown bossGroup")
    worker.awaitOrLog("Shutdown workerGroup")
}
```

**逐条原因：**

| 改动 | 原因 |
|---|---|
| **两个 group 先都发出 shutdown，再分别 await** | 它们互相独立。原先"关一个、等一个、再关下一个"把两段 quiet period 相加；先发后等让它们重叠，取最大值而非求和。**这一条单独就把 4 秒砍掉一半。** |
| **quiet period 传 0** | quiet period 的用途是让在途任务安静下来再关事件循环。对一个正在被销毁、channel 已经关掉的 server 没有价值。配合上一条，等待从"秒级"降到"毫秒级"。 |
| **`syncUninterruptibly()` → `awaitCompletion()`** | 不占线程、可取消。 |
| **`fun` → `suspend fun`** | 上一条的前提。同时把"这是一个会等待的操作"写进类型，调用方不会再误以为它能在主线程调。 |
| **`withTimeoutOrNull` 包住 netty 部分** | 超时放在**资源边界**，而不是放在调用方的关闭门闩上 —— 这正是 audio teardown 设计 §4.7.1 已经写下的规则："如需超时，应加在具体资源操作层并保留后续释放"。超时后仍然执行状态迁移与 `onStopped()`，不会把调用方卡死，也不会跳过后续清理。 |
| **新增 `Future<V>.awaitOrLog()` 私有助手** | 三处 await 都需要"普通异常记日志、取消向上抛"。`runCatching` 会连 `CancellationException` 一起吞掉，从而让 `withTimeoutOrNull` 的超时失效，因此用显式 `try/catch` 并重抛取消。 |
| **删掉 `closeFuture()` 裸调用** | 原代码 `close().syncUninterruptibly()` 之前有一行 `closeFuture()`，结果被直接丢弃，是死代码。 |
| **KDoc 重写** | 原 KDoc 宣传的是 "using syncUninterruptibly ... cost almost 4s ... NOT call in main thread"，与改动后的实现完全相反，必须同步更正，否则会误导下一个维护者把它改回去。 |
| **守卫新增 `STOPPED`**（2026-10-14 复审 R9） | 对已停止的实例再次调用直接返回 `false`，不再重跑整个序列、不再二次回调 `onStopped()`。此前第二次调用会在 `serverChannel.close()` 上遇到已终止的 event loop（见 R7），白等 3 秒超时并打出"did not finish within 3000ms"——与 §8.3 第 3 项的验收要求自相矛盾。`SocketServerActivity`（Stop 按钮后返回）与 `WebSocketServerActivity` 今天就会走到这条路径。 |

### 4.3 `BaseNettyClient.release()`：去掉最后一处 `syncUninterruptibly()`

`release()` 原本是 `suspend fun release(): Boolean = suspendCancellableCoroutine { cont -> ... }`，
内部混用了三种风格：早退分支 `cont.resume(false)`、channel 关闭用阻塞 `syncUninterruptibly()`、
group 关闭用 `addListener` 回调。改为普通 suspend 体 + `awaitCompletion()` 之后三者统一。

**签名完全不变**（仍是 `suspend fun release(): Boolean`），因此**所有调用方与现有单元测试均无需
改动**。

顺带订正了那段 "Why???" 注释。答案是：`closeFuture()` 只有在 channel **真正关闭之后**才完成，
而当时还没有人调用过 `close()`，自然是永久等待；应该等的是 `close()` 返回的那个 future。新注释
把这条结论写在原地。

行为差异只有一处：shutdown future 失败时，日志文案由 `"Release socket failed!!!"`（warn）变为
`"Release socket error."`（error），返回值仍是 `false`。

`workerGroup.shutdownGracefully()` 同样改为传 quiet period 0。

### 4.4 `AudioReceiver`：`cleanupScope` 是必需前置，不是被动连带改动

`BaseNettyServer.stopServer()` 改 suspend 后，demo 里 `ScreenShareMasterActivity:513`、
`SocketServerActivity:125/:143`、`WebSocketServerActivity:150/:174` 这 5 个调用点已经在
`cs.launch { }` 内，**唯一**不在协程内的是 `AudioReceiver.stopServer()`（当时的
`AudioReceiver.kt:213`；2026-10-14 起 `AudioReceiver.startServer()` 的补偿路径又多一处调用，
见 spec §20.1，全仓合计 7 处）。因此：

- `AudioReceiver.stopServer()` 由 `fun` 改为 `suspend fun`；
- 两个 netty 监听回调 `onClientDisconnected()` / `onStartFailed()` 是非挂起接口实现，签名改不了，
  因此新增一个 `SupervisorJob + Dispatchers.IO + CoroutineExceptionHandler` 的 `cleanupScope`，
  回调中改为 `cleanupScope.launch { stopServer() }`；
- `AudioActivity.kt:371` 的 `launchCleanup("Stop audio receiver") { audioReceiver?.stopServer() }`
  接受 `suspend () -> Unit`，无需改动。

**这是 audio teardown 计划 Task 3 Step 3 的一次提前落地（子集）**，见
`00-documents/superpowers/plans/2026-09-16-audio-deprecated-teardown-removal.md`。该计划里与
`cleanupScope` 配套的 `SuspendTeardownGate`（一次性关闭门闩）与 `stopping` 标志**本次没有一起
引入**，因此：

- 计划中的门闩仍然必须补上，在那之前 `AudioReceiver` 侧的并发关闭保护是不完整的。
  实施该计划时，Task 3 Step 3 关于 `cleanupScope` 的部分可标记为"已落地"，其余照旧。
  **（2026-10-14：门闩与 `stopping` 标志已随 audio 计划 Task 3 落地，见 spec §20。）**

**本节此前的定级是错的**（复审修正，见 §10 的 R1）。原文写的是"现状与改动前相比**没有变差**：
改动前两个回调也可以并发同步调用 `stopServer()`，靠的是 `BaseNettyServer.stopServer()` 的
`UNINITIALIZED` 守卫"。两句都站不住：

1. **`cleanupScope` 修掉的是一个 Netty `BlockingOperationException`，不是"没有变差"。**
   `onClientDisconnected()` 由 `BaseServerChannelInboundHandler.channelInactive()`（`:55-67`）调用，
   **跑在 netty worker 事件循环线程上**。改动前它同步调 `stopServer()` →
   `workerGroup.shutdownGracefully().syncUninterruptibly()`，即**在 workerGroup 自己的事件循环
   线程上等这个 group 终止**；Netty 的 `DefaultPromise` 在 await 前会 `checkDeadLock()`，
   `executor().inEventLoop()` 为真时直接抛 `BlockingOperationException`。
   这条路径至今没爆，唯一原因是 §4.5 修掉的那个 `receiverServer` 恒 null 让最后一步从未执行。
   **因此 `cleanupScope` 与 §4.5 必须同批落地**：单独修好 `receiverServer` 而不改回调调用方式，
   会立刻把这个异常暴露出来。
2. **`UNINITIALIZED` 守卫只挡真并发，挡不住顺序重复调用。** `stopServer()` 在 `:230` 先把状态
   设成 `UNINITIALIZED` 占位，收尾时 `:245` 又设回 `STOPPED`。于是**第二次**调用看到
   `STOPPED != UNINITIALIZED`、`::serverChannel.isInitialized` 仍为 true，整套 teardown 再跑一遍，
   `connectionListener.onStopped()` 被通知两次。
   `AudioReceiver` 现在有三个关闭入口（两个 netty 回调 + `AudioActivity:371`），顺序重复调用是
   常态而非例外——这正是 audio teardown 计划里 `SuspendTeardownGate` 要解决的问题，本次仍未引入。
   **（2026-10-14 已改：守卫加入 `STOPPED`，重复调用直接返回 `false`、`onStopped()` 只通知一次，
   见 §11 R9；`AudioReceiver` 侧的 `SuspendTeardownGate` 也已落地。）**

### 4.5 `BaseNettyServer.startServer()`：改 suspend + 不再阻塞整个服务器生命周期

这一条最初被列为"本次不改"，后追加实施。

改动前：

```kotlin
@Synchronized
fun startServer() {
    ...
    serverChannel = bootstrap.bind(port).sync().channel()
    connectState.set(STARTED)
    connectionListener.onStarted(this)
    // After running the line below, the process will get stuck there and wait for client connection
    serverChannel.closeFuture().sync()      // ← 一直停在这里，直到服务器关闭
    LogContext.log.i(tag, "===== Stopping server... =====")
}
```

**它不属于 `syncUninterruptibly()` 那一族，是一个语义问题**：这个方法实际做的是"运行服务器直到
结束"，名字却叫 `startServer()`。两条后果：

1. `server = Server(...).also { it.startServer() }` 这种写法**永远拿不到赋值** —— `also` 的返回
   赋值发生在块执行完之后，而块永不返回。`AudioReceiver.kt` 正是这么写的，导致
   `receiverServer` 在服务器成功运行期间恒为 `null`，`stopServer()` 的最后一步是死代码
   （即 audio teardown 设计 §18 的 H1）。
2. `@Synchronized` 的监视器被持有整个会话。任何其它 `@Synchronized` 成员都会与之死锁；
   `stopServer()` 之所以没出问题，只是因为它恰好没加这个注解。

改动后：

```kotlin
suspend fun startServer() {
    lifecycleMutex.withLock {
        ...
        val bindFuture = bootstrap.bind(port)
        bindFuture.awaitCompletion()
        serverChannel = bindFuture.channel()
        connectState.set(ServerConnectStatus.STARTED)
        // Observe the close instead of blocking on it.
        serverChannel.closeFuture().addListener {
            LogContext.log.i(tag, "===== Server channel closed =====")
        }
        connectionListener.onStarted(this)
    }
}
```

**逐条原因：**

| 改动 | 原因 |
|---|---|
| **去掉 `serverChannel.closeFuture().sync()`** | 这是本条改动的全部目的。绑定成功即返回，`startServer()` 名副其实。 |
| **`bind(port).sync()` → `bind(port).awaitCompletion()`** | 绑定本身也是阻塞等待，换成同一个桥接；失败时抛出的语义与 `sync()` 一致，仍由原有 catch 分支处理。 |
| **给 `closeFuture()` 挂 listener** | 保留原来"channel 关闭时打一条日志"的可观测性 —— 那条日志此前是靠 `sync()` 返回后才打出来的。listener 不占线程。 |
| **`@Synchronized` → `Mutex`** | Kotlin **不允许** `@Synchronized` 标注 suspend 函数（编译错误），而且监视器会阻塞派发线程。`Mutex` 是对原语义最忠实的替换：仍然串行化并发的 `startServer()`，但可取消、不占线程。 |
| **新增 `catch (e: CancellationException) { throw e }`** | 在 JVM 上 `CancellationException` 是普通 `Exception` 子类，若不单独重抛就会落进 `catch (e: Exception)` 分支，把"调用方被取消"误报成 `onStartFailed()`。这是改 suspend 之后新引入的风险，必须显式处理。 |
| **日志文案 `"===== connect() ..."` → `"===== startServer() ..."`** | 原文是从客户端复制过来的笔误。 |

**连带修改 `AudioReceiver.startServer()`：**

- 由 `fun` 改为 `suspend fun`（调用方 `AudioActivity.kt:408` 已在 `ioScope.launch { }` 内，无需改动）；
- **把赋值提到阻塞/挂起调用之前**，不再用 `also { }` 在出口赋值：

```kotlin
val server = AudioReceiverWebSocket(10020, connectionListener)
receiverServer = server
receiverHandler = AudioReceiverWebSocketHandler(server)
server.initHandler(receiverHandler)
server.startServer()
```

`startServer()` 非阻塞之后，`also { }` 写法本身已经能赋上值；仍然改掉它的理由是
`onStarted()` 与第一个 `onClientConnected()` 都可能**从 `startServer()` 内部**回调出来，
那一刻 `receiverServer` 还是 `null`。先赋值消除这一整类窗口。仓库里已有正确写法的先例：
`WebSocketServerActivity.kt:162-166`。

**⇒ audio teardown 设计 §18 的 H1（`receiverServer` 恒 null）在本次一并解决。**

**但"先赋值 + 快速返回"打开了一个新窗口**（复审新增，见 §10 的 R2）。`startServer()` 现在很快
返回，而 `stopServer()` **原先不持有 `startMutex`**，于是：`AudioActivity.onAudioReceiverClick`
（`:408`）与 `onStop()` 的 `launchCleanup`（`:371`）跑在同一个 Activity `ioScope` 上、彼此并发；
若 `stopServer()` 在 bind 尚未完成时插进来，它会看到 `::serverChannel.isInitialized == false`
→ 早退返回 `false` → 随后 bind 成功、服务器**一直监听着端口没人再停**。

这在改动前不会发生：那时 `receiverServer` 恒为 null，`receiverServer?.stopServer()` 是空语句；
是 §4.5 把 `receiverServer` 修成真值之后，这条路径才第一次真正可达。

修法：把 `startMutex` 更名为 `lifecycleMutex`，**`stopServer()` 也持有它**，让"启动中到来的停止"
等待 bind 结束而不是早退。锁只在 start 期间短暂持有（bind 完即释放），stop 最多持有约 3 秒
（自带超时），两者之间没有重入。

**（2026-10-14 R15 补充）** 这把锁只串行化、不排序：若 stop 先拿到锁，它看到的仍是"未初始化"。
这一分支现在由 `releaseUnstartedServer()` 收口——在锁内关掉两个 group 并置 `STOPPED`，让随后
拿到锁的 `startServer()` 看到 `STOPPED` 直接经 `onStartFailed` 报告
`CONNECTION_ERROR_ALREADY_RELEASED`，不再 bind。详见 §11 R15。

### 4.6 顺带订正的过时注释

`WebSocketServerActivity.kt:168-169` 原注释写的是
"startServer() blocks at closeFuture().sync() for the server's whole lifetime, so it must run off
the main thread" —— 改动后不再成立，已更正为"binds and returns，但仍需在协程中调用"。
`cs.launch { }` 本身保留不变。

### 4.7 `AudioSender`：`senderClient` 赋值提前（解决 §18 H2）

这一条与 §4.5 一样，最初被列为"本次不改"（原 §6.2），后追加实施。

改动前（`AudioSender.kt:86-91`）：

```kotlin
ioScope.launch {
    senderClient =
        AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000)).also {
            senderHandler = AudioSenderWebSocketHandler(it)
            it.initHandler(senderHandler)
            it.connect()      // suspend；内部 f.sync() 最长 CONNECTION_TIMEOUT_IN_MILLS = 30 s
        }
}
```

与 `AudioReceiver` 完全同形，只是阻塞调用换成了 `connect()`。`also { }` 的赋值发生在块
**返回之后**，于是 `senderClient` 在整个连接窗口内为 `null`：

1. `connect()` **从自己内部同步回调** `onFailed()`，而 `onFailed()` 调 `stop()`。此刻
   `senderClient` 仍是 `null` ⇒ `senderClient?.disconnectManually()` 与 `senderClient?.release()`
   双双空转：重试链不终止，两个 EventLoopGroup 不 shutdown。
2. 若关闭发生在 `connect()` 挂起期间并取消了这个协程，赋值**永远不会发生**，上面两处空转
   从"窗口期问题"变成永久性的。

**与 H1 的关键差别**：`BaseNettyServer.startServer()` 那边是**阻塞到永不返回**，所以
`receiverServer` 在服务器运行期间**恒为** null；`connect()` 这边只是**挂起最多 30 秒**，所以
`senderClient` 只在连接窗口内为 null。但 `onFailed` 恰恰只在这个窗口里触发——问题的严重性
不因窗口有限而降低。

改动后：

```kotlin
ioScope.launch {
    // Assign before connect(), not through `also { }` on the way out: connect() parks for
    // up to CONNECTION_TIMEOUT_IN_MILLS and calls onFailed() from inside itself, so the
    // trailing assignment leaves senderClient null for exactly the window in which stop()
    // needs it to end the retry chain and release the client.
    val client =
        AudioSenderWebSocket(uri, connectionListener, true, ConstantRetry(10, 2000))
    senderClient = client
    senderHandler = AudioSenderWebSocketHandler(client)
    client.initHandler(senderHandler)
    client.connect()
}
```

**逐条原因：**

| 改动 | 原因 |
|---|---|
| **`also { }` → 先 `val client`，再逐行赋值** | 构造与赋值之间没有挂起点，因此不存在"已构造但未赋值"的中间态；`connect()` 之后才可能被取消，而那时 `senderClient` 已经是真值。 |
| **`it` → 具名 `client`** | `also { it }` 去掉之后 `it` 不再存在；具名也让"这个实例同时被赋给字段和传给 handler"一目了然。 |
| **`senderHandler` 仍先于 `initHandler()` 赋值** | 保持原有顺序：`initHandler(senderHandler)` 读的就是这个字段（原代码同样是先赋值再传入）。 |
| **不动 `micRecorder` 那一段** | 它本来就在 `launch` 之外顺序执行，与本条无关。 |

**为什么这次一起改了。** §4.5 把 `receiverServer` 修成真值之后，两个 audio demo 类只剩
`AudioSender` 一处仍是旧形状；而本次 netty 侧改动（`release()` 的 quiet period 0 与 3 秒资源层
超时）**恰恰是让 `senderClient?.release()` 变快的那一批**——若 `senderClient` 仍为 null，这批
收益在 sender 路径上一分都拿不到。

**只改赋值时机还不够**，因为 `stop()` 本身是坏的——见 §4.8，本次一并改了。

### 4.8 `AudioSender.stop()`：改 suspend + 真正等待释放

改动前：

```kotlin
fun stop() {
    audioPlayer?.release()          // ① 先 release，worker 还在 play()
    micRecorder?.stopRecord()       // ② 已弃用 API，且不等待录音 Job
    ioScope.launch {
        senderClient?.disconnectManually()   // suspend
        senderClient?.release()              // suspend
    }
    ioScope.cancel()                // ③ 紧接着取消掉上面那个协程
}
```

三个独立缺陷：

1. **`ioScope.cancel()` 让那两个释放调用根本不会运行。** `launch { }` 尚未被派发就被取消，
   于是 `disconnectManually()` 与 `release()` **一次都没执行过**——重试链不终止，两个
   EventLoopGroup 从不 shutdown。这与 §4.7 的赋值时机**无关**：`senderClient` 就算非空，
   这两行也跑不到。**§4.7 是必要条件而非充分条件，两处都改才有效果。**
2. **`audioPlayer?.release()` 排在最前面，而 worker 还活着。** `startPlayThread()` 的循环仍在
   `audioPlayer?.play(audioData)`，此刻 scope 尚未取消。PCM 路径下这正是设计文档反复引用的那条
   `AudioTrackShared: Assertion failed` → `SIGABRT`。
3. `micRecorder?.stopRecord()` 是已弃用 API，且不等待录音 Job 结束。

改动后：

```kotlin
suspend fun stop() {
    // 先接管全部资源并清空字段（与 AudioActivity.stopPcmPlayback() 同形）
    val client = senderClient
    val recorder = micRecorder
    val player = audioPlayer
    senderClient = null
    senderHandler = null
    micRecorder = null
    audioPlayer = null

    client?.disconnectManually()                     // 1. 先断连，终止重试链
    recorder?.stopRecordAndJoin()                    // 2. 停止产生新帧并等待录音 Job
    player?.stop()                                   // 3. 停 track（不释放）
    ioScope.coroutineContext[Job]?.cancelAndJoin()   // 4. 取消并等待两个 worker
    player?.release()                                // 5. 此时才释放 AudioTrack
    client?.release()                                // 6. 最后 shutdown EventLoopGroup
}
```

**逐条原因：**

| 改动 | 原因 |
|---|---|
| **`fun` → `suspend fun`** | 释放链上的 `disconnectManually()` / `release()` / `stopRecordAndJoin()` 全是 suspend。此前把它们塞进 `ioScope.launch { }` 正是为了绕开这一点，而那个 scope 下一行就被取消——改 suspend 是唯一能"真正等到释放完成"的形式。 |
| **去掉 `ioScope.launch { }` + `ioScope.cancel()`，改用 `cancelAndJoin()`** | `cancel()` 不等待；而 `release()` 必须在 worker 停稳之后执行，否则 shutdown EventLoopGroup 时 worker 可能还在用 channel。`cancelAndJoin()` 同时解决"释放不执行"与"释放与 worker 并发"两件事。 |
| **`disconnectManually()` 提到第一步** | 它设置的正是 `doRetry()` 早退所依据的标志。排在后面时，teardown 期间的连接失败会触发重连，2 秒后 `onConnected()` 新建一个 `AudioPlayer`，而此刻已没有人再释放它。它不触碰任何音频资源，提前的代价为零（设计文档 §4.7.3，第八次复审）。 |
| **`stopRecord()` → `stopRecordAndJoin()`** | 前者已弃用且不等待录音 Job；后者自带一次性守卫（`stopped` CAS + `releaseCompleted.await()`），重复调用安全。顺手消掉一条弃用警告。 |
| **`player?.stop()` 与 `player?.release()` 拆开，中间夹 `cancelAndJoin()`** | 本条改动里**唯一真正消除崩溃风险**的一步。`stop()` 不释放任何东西，只让 track 停下；`release()` 移到 join 之后，保证没有 worker 还能调 `play()`。顺序取自 `StreamPlayerStopper.prepareStop()`。 |
| **先取局部变量再清空字段** | `stop()` 对一个会话**可达两次**（netty 的 `onFailed`/`onDisconnected` 一次，Activity `onStop()` 一次）。重复 release `AudioTrack` 或 netty client 不是无害操作。写法取自 `AudioActivity.stopPcmPlayback()`。**这不等于一次性门闩**：两个并发调用仍可能都读到非空字段。真正的门闩（`SuspendTeardownGate`）已于 2026-10-14 落地，本行的"局部变量 + 清空字段"写法随之被 `teardownGate.run { teardownSteps(...) }` 取代，以 `AudioSender.kt` 为准。 |
| **两处 netty 回调改为 `cleanupScope.launch { stop() }`** | 与 §4.4 的 `AudioReceiver` 同理：`stop()` 现在是 suspend，而 `onDisconnected`/`onFailed` 是非挂起接口方法；更关键的是 **`onFailed()` 会从 `connect()` 内部同步回调，而 `connect()` 跑在 `ioScope` 上**——若在那里直接跑 `stop()`，`cancelAndJoin()` 会等待自己所在的协程，必死锁。 |
| **新增 `cleanupScope`**（Task 3 Step 3 的 `AudioSender` 一半） | 与 `AudioReceiver` 的那个逐字同构：`SupervisorJob` + `Dispatchers.IO` + 一个 `CoroutineExceptionHandler`，**故意永不取消**。没有它，上一行那两处回调无处可跑。 |

**调用方无需改动。** `AudioActivity.kt:372` 的
`launchCleanup("Stop audio sender") { audioSender?.stop() }` —— `launchCleanup` 的形参本就是
`suspend () -> Unit`（`:387`），且它跑在 Activity 自己的 `ioScope` 上、不是 `AudioSender.ioScope`
的后代，`cancelAndJoin()` 不会自等。

**当时仍未实施、已于 2026-10-14 随 audio 计划 Task 3 落地的部分**（上面"改动后"的 `stop()`
代码也已被 `teardownGate.run { teardownSteps(...) }` 取代，以 `AudioSender.kt` 与 spec §20 为准）：

- `SuspendTeardownGate` 一次性门闩——目前并发的两次 `stop()` 仍可能同时进入；
- `teardownSteps()` 平铺串联——目前任一步抛出会跳过其后全部步骤；
- `stopping: AtomicBoolean`——目前 teardown 期间到来的 `onConnected()` 仍会新建 `AudioPlayer`；
- `containsJob()` 自调用 `require()` 守卫——目前靠"所有调用点都已核对"保证，不是代码强制；
- 两个 `ArrayBlockingQueue` 改 `Channel`；
- `audioPlayer?.releaseAndJoin()`（要等 Task 2 给 `AudioPlayer` 加这个 API）。

**已知风险：`cancelAndJoin()` 可能等待正在进行的 `connect()`**，最长
`CONNECTION_TIMEOUT_IN_MILLS`（30 秒）。缓解是 `BaseNettyClient` 的
`cont.invokeOnCancellation { cancelConnectFuture() }`，**该缓解未经实测**——设计文档 §8 风险表与
§6.3 已为它单列真机项。改动前不存在这个等待（`cancel()` 不 join），但代价是释放根本不发生。

## 5. 破坏性变更

| API | 变更 | 影响 |
|---|---|---|
| `BaseNettyServer.stopServer()` | `fun` → `suspend fun`，返回值 `Boolean` 与状态语义不变；对已停止实例的重复调用自 2026-10-14 起直接返回 `false`（R9） | **对 JitPack 下游是源码 + 二进制破坏性变更**；仓库内 7 个调用点（含 2026-10-14 新增的 `AudioReceiver.startServer()` 补偿调用）全部已在协程中，**无需改动任何调用点** |
| `BaseNettyServer.startServer()` | `@Synchronized fun` → `suspend fun`，且**不再阻塞到服务器关闭** | **源码 + 二进制 + 行为三重破坏性变更**。仓库内 4 个调用点（`ScreenShareMasterActivity:507`、`SocketServerActivity:136`、`WebSocketServerActivity:170`、`AudioReceiver:217`）均已在协程中，无需改动；但**依赖"startServer() 返回即代表服务器已停止"的下游必须改**——改用 `ServerConnectListener.onStopped()` |
| `AudioReceiver.startServer(ctx)` / `stopServer()` | 均改为 `suspend`（demo 内部类，不属公开面） | 无外部影响 |
| `AudioSender.stop()` | `fun` → `suspend fun`（demo 内部类，不属公开面） | 无外部影响。两个调用点已改：netty 回调走 `cleanupScope.launch { }`，`AudioActivity.kt:372` 的 `launchCleanup` 形参本就是 `suspend () -> Unit` |
| `BaseNettyServer.startServer()`（续） | 签名不变；2026-10-15 第三次复审新增 `NETTY_BIND_TIMEOUT_IN_MILLS`（3 秒）**bind 上界**，超时则 `close()` 掉 channel、状态置 `FAILED`、经 `onStartFailed(CONNECTION_ERROR_SERVER_START_ERROR)` 报告（R21） | 签名不变；`onStartFailed` 的触发条件多了一种。bind 是本地 socket 操作，正常路径远不到 3 秒 |
| `BaseNettyClient.release()` | 签名不变，仅实现重写；新增 `NETTY_RELEASE_TIMEOUT_IN_MILLS`（3 秒）资源层超时，超时返回 `false` | 无签名影响；返回 `false` 的条件多了一种 |
| `Future<V>.awaitCompletion()` | **新增，且为 `internal`** | 不进入公开面。两个调用方都在 `basenetty` 模块内，没有理由对下游承诺这个签名（复审 R5） |

迁移指引：`server.stopServer()` 必须在协程中调用。它现在最多等待
`NETTY_SHUTDOWN_TIMEOUT_IN_MILLS`（3 秒）后就会放弃 netty 侧的等待并继续完成状态迁移，不会
再无限期占用调用线程。`client.release()` 同理，上界为 `NETTY_RELEASE_TIMEOUT_IN_MILLS`。

`CHANGELOG.md` 需要在 `## [Unreleased]` 的 `### 变更 (Changed)` 下记录这条；建议与 audio
teardown 那批破坏性变更**合并在同一个版本发布**，避免下游连续两次适配。

## 6. 本次明确没有改的

### 6.1 `BaseNettyClient.connect()` 内的 `f.sync()`

`BaseNettyClient.kt:430` 仍在用阻塞的 `f.sync()` 做连接等待，旁边的注释写明它最长阻塞
`CONNECTION_TIMEOUT_IN_MILLS`（30 秒），并留了一句 "Future improvement: migrate to fully
async addListener-based connection"。

不在本次动它的理由：`connect()` 的实现是一整套状态机（早退分支、重试挂钩
`connectContinuation` / `connectResume`、WebSocket 握手二次挂起、`invokeOnCancellation` 里的
`cancelConnectFuture()`），改成全异步等于重写这个状态机，风险与本次"去阻塞化"的收益不成比例。
而且它已有缓解：`cont.invokeOnCancellation { cancelConnectFuture() }`（`:399-402`）会在取消时
取消 `ChannelFuture`，使阻塞的 `sync()` 尽快抛出。

**但这条缓解是未实测的假设**，audio teardown 设计 §6.3 已经为它单列了一项真机验证
（"连接进行中触发关闭"，要求记录 teardown 耗时）。

### 6.2 ~~`AudioSender.start()` 的 `senderClient` 赋值窗口~~（已追加实施，见 §4.7）

本条最初列在"本次不改"，理由是它属于 audio teardown 计划 Task 3 Step 4 的范围。后来一并实施了
**赋值时机**这一半，见 §4.7。

`AudioSender.stop()` 本身随后也一并改了，见 §4.8。门闩、`teardownSteps()`、`stopping` 标志与
队列改 Channel 当时仍未实施，属 Task 3 Step 4 的本体工作，已于 2026-10-14 落地（spec §20）。

## 7. 改动文件清单

| 文件 | 改动 |
|---|---|
| `basenetty/.../framework/base/NettyFutureExt.kt` | **新增**。`internal suspend fun Future<V>.awaitCompletion()` 桥接（复审 R5 收窄为 `internal`，并对 `cause()` 为 null 加兜底） |
| `basenetty/.../framework/server/BaseNettyServer.kt` | `startServer()` 与 `stopServer()` 均改 suspend；`startServer()` 去掉 `closeFuture().sync()`、`@Synchronized` 换 `Mutex`、新增取消重抛分支；新增 `shutdownNetty()` 与 `Future<V>.awaitOrLog()`；新增 `lifecycleMutex`（**`stopServer()` 也持有**，复审 R2）与两个 companion 私有常量；`defaultServerInboundHandler?.release()` 纳入异常保护（复审 R4）；两处 KDoc 重写；新增 import |
| `basenetty/.../framework/client/BaseNettyClient.kt` | `release()` 由 `suspendCancellableCoroutine` 改写为普通 suspend 体；`close().syncUninterruptibly()` → `awaitCompletion()`；`shutdownGracefully` 传 quiet period 0；订正 "Why???" 注释；抽出 `releaseNetty()` 并加 `withTimeoutOrNull` 资源层超时（复审 R3）；`defaultInboundHandler?.release()` 纳入异常保护（复审 R4）；新增 companion 常量 `SHUTDOWN_TIMEOUT_IN_SECONDS` 与 `NETTY_RELEASE_TIMEOUT_IN_MILLS`；新增 import |
| `demo/.../audio/receiver/AudioReceiver.kt` | `startServer()` / `stopServer()` 均改 suspend；`receiverServer` 赋值提到 `startServer()` 之前（解决 §18 H1）；新增 `cleanupScope`；两处 netty 回调改为 `cleanupScope.launch { }`；新增 import |
| `demo/.../audio/sender/AudioSender.kt` | `senderClient` 赋值提到 `connect()` 之前，去掉 `also { }` 出口赋值（§4.7，解决 §18 H2）；`stop()` 改 `suspend` 并重排关闭顺序（§4.8）；新增 `cleanupScope`，两处 netty 回调改为 `cleanupScope.launch { }`；`stopRecord()` 换 `stopRecordAndJoin()`；import 增删 |
| `demo/.../socket/websocket/WebSocketServerActivity.kt` | 订正 `:168-169` 已过时的注释 |

## 8. 验证状态

### 8.1 已完成（静态分析）

- 改动文件无超过 100 字符的行（`BaseNettyClient.kt:35`、`:88` 的两条超长行为**改动前既有**，
  分别是一条 netty 长 import 与一段 KDoc ASCII 图）。
- `basenetty/src/test/.../BaseNettyLifecycleTest.kt` 经核对**不调用** `startServer()` /
  `stopServer()`，因此服务端签名变更不会导致 `:basenetty:testDebugUnitTest` 编译失败。
- `basenetty/build.gradle.kts:26` 已有 `api(libs.kotlin.coroutines.core)`，新桥接所需依赖具备。
- `startServer()` 4 个、`stopServer()` 7 个调用点逐一核对（`SocketServerActivity` 与
  `WebSocketServerActivity` 各有两处 `stopServer()`，`AudioReceiver` 自 2026-10-14 起有两处：
  门闩的最后一步与 `startServer()` 的补偿路径），全部已在协程上下文中
  （`ScreenShareMasterActivity` / `SocketServerActivity` / `WebSocketServerActivity` 用 `cs.launch`，
  `AudioReceiver` 经 `AudioActivity.kt:408` 的 `ioScope.launch` 与 `:371` 的 `launchCleanup`）。
- `release()` 重写后函数体内已无残留的 `cont.` 引用。
- `basenetty/src/main` 内已无 `syncUninterruptibly()`；剩余唯一的 `.sync()` 是
  `BaseNettyClient.kt:430`，即 §6.1 说明的未改项。
- `basenetty/src/main` 内已无 `@Synchronized`（只剩两处提及它的注释）。

### 8.2 未验证（需本地执行）

```bash
./gradlew --continue --rerun-tasks \
  :basenetty:compileDebugKotlin :basenetty:testDebugUnitTest \
  :basenetty:detekt :basenetty:ktlintCheck \
  :demo:compileDevDebugKotlin :demo:detekt :demo:ktlintCheck
```

重点关注：

- `NettyFutureExt.kt` 中 `GenericFutureListener<Future<V>>` 的 SAM 转换与
  `addListener` / `removeListener` 的 Java 泛型通配是否被 Kotlin 接受；
- `startServer()` 里 `lifecycleMutex.withLock { ... return@withLock ... }` 与 `this`
  （在 `withLock` 的无接收者 lambda 内应仍指向 `BaseNettyServer`）的解析；
- `stopServer()` 改为 `= lifecycleMutex.withLock { ... return@withLock ... }` 表达式体后的
  返回类型推断；
- `AudioReceiver` 新增 import 是否全部被使用（detekt `maxIssues = 0`）；
- `basenetty` 是否仍有其它地方依赖 `startServer()` 的阻塞语义（编译期会直接报错）。

### 8.3 真机验证清单

1. **关闭耗时（最直接的验收指标）**：`WebSocketServerActivity` / `SocketServerActivity`
   启动服务器后退出，测量从 `===== stopServer() =====` 到 `=====> Server released <=====`
   的 logcat 时间戳差。**改动前约 4 秒，改动后应在数百毫秒以内。**
2. **端口释放**：退出后立即重新进入并启动服务器，不应出现
   `BindException: Address already in use`。
3. **bind 超时分支**（R21 新增）：正常路径下不应出现
   `Bind did not finish within 3000ms`。若出现，说明 boss loop 被 handler 卡住了，不要靠调大
   这个上界解决。可顺带确认超时后状态为 `FAILED`：再调一次 `stopServer()` 应当走
   `releaseUnstartedServer()` 把两个 group 关掉（日志 `Not started; releasing the event loops`）。
4. **超时分支**：正常路径下不应出现 `Netty shutdown did not finish within 3000ms` 或
   `Netty release did not finish within 3000ms`。若出现，**先看日志尾部那句提示**：
   预算可能是被 `Close channel` 阶段吃掉的，而不是 event loop 还有任务。确认是后者再去查
   具体 handler，两种情况都不该靠调大超时解决。
5. **客户端释放**：`SocketClientActivity` / `WebSocketClientActivity` 连接后退出，确认
   `=====> Socket released <=====` 出现且只出现一次，不出现 `Release socket error.`。
6. **netty 回调触发的关闭**（`AudioReceiver` 新增的 `cleanupScope` 只在这条路径上生效）：
   从对端断开触发 `onClientDisconnected()`，确认不出现 `Audio receiver teardown failed`、
   进程不崩。**重点看有没有 `BlockingOperationException`**——改动前这条路径会在 netty 事件
   循环线程上等自己的 group 终止（§4.4 第 1 条），`cleanupScope` 就是为它而加；§4.5 修好
   `receiverServer` 之后这条路径才第一次真正可达，因此这是本批必测项而非可选项。
   同一场景再看**第二次关闭**：断开后立刻退出 Activity，`onClientDisconnected` 与
   `launchCleanup` 会先后各调一次 `stopServer()`。`AudioReceiver` 侧由 `SuspendTeardownGate`
   合并为一次；即便绕过门闩直接对 `BaseNettyServer` 调两次，第二次也应打出
   `Already released or not initialized` 后立即返回（2026-10-14 起 `STOPPED` 在守卫内，R9），
   `onStopped` **只通知一次**，且**不出现** `Netty shutdown did not finish within 3000ms`。
7. **`startServer()` 确实返回了**（§4.5 的直接验收）：进入 `AudioActivity` 启动 receiver 后，
   确认 `AudioActivity.kt:408` 那个 `ioScope.launch` **正常结束**而不是长期驻留；
   最简观察方式是退出 Activity 后确认 `AudioReceiver.stopServer()` 的日志序列完整出现
   （改动前 `receiverServer` 恒 null，最后一步不会执行，也就看不到
   `===== stopServer() =====` 之后的 server 释放日志）。
8. **`onStarted` 时序**：`startServer()` 现在在 bind 成功后立刻回调 `onStarted()` 再返回。
   确认 `AudioReceiver` / 三个 socket demo 的 `onStarted` 仍只触发一次，且不早于 bind 成功。
9. **`Server channel closed` 日志**：停止服务器时应出现一次
   `===== Server channel closed =====`（新的 listener 产生），它替代了原先 `sync()` 返回后的
   `===== Stopping server... =====`。
10. **启动中立即退出**（复审 R2 的验收项）：进入 `AudioActivity`，点 receiver 后**立刻**退出
   Activity，让 `startServer()` 与 `stopServer()` 在 `lifecycleMutex` 上真正竞争。确认
   `stopServer()` 等到 bind 结束才执行（日志顺序为 `Start successfully` → `stopServer()`），
   **不出现** `Already released or not initialized` 后服务器仍占着端口的情况；随后立刻重进
   并再次启动，不应出现 `BindException: Address already in use`。

11. **sender 的关闭真正发生了**（§4.7 + §4.8 的直接验收，也是 spec §6.3 "重试链确实终止"
    那一项）：`AudioActivity` 点 sender，连一个**不存在**的服务端地址，让 `connect()` 走失败
    路径；确认 `onFailed` 之后 logcat **不再**出现 `Reconnect(n) in 2000ms`，且
    `=====> Socket released <=====` 出现。改动前这两条都不成立——`senderClient` 在那一刻为
    `null`（§4.7），且释放协程会被 `ioScope.cancel()` 取消（§4.8）。
12. **sender 正常退出**：连上真实服务端、通话数秒后退出 Activity。确认
    `Audio sender teardown failed` **不出现**，录音与播放都停止，且
    `=====> Socket released <=====` 只出现一次（`stop()` 会被 `onDisconnected` 与 `onStop()`
    各触发一次，`SuspendTeardownGate` 让第二次等待同一结果——**若看到两次 release，就是门闩
    失效，属回归**，必须查明，不得当作已知项记录了事）。
13. **连接进行中退出**（§4.8 的已知风险项）：填一个**不可达但会超时**的地址（例如某个被丢包的
    内网 IP），在 `connect()` 挂起期间立刻退出 Activity。测量 `onStop()` 到 sender 释放日志的
    耗时：若接近 30 秒，说明 `cancelConnectFuture()` 这条缓解**不生效**，需回到 §6.1 重新设计；
    这正是 spec §6.3"连接进行中触发关闭"那一项。
14. **stop 先于 start**（R15 的验收项）：`SocketServerActivity` 或 `WebSocketServerActivity` 点
    Start 后**立刻**退出，让 `stopServer()` 抢在 `startServer()` 之前拿到锁。确认 logcat 出现
    `Not started; releasing the event loops so a later start is refused`，随后的 `startServer()`
    打出 `Netty server had already been released. Re-create it.` 并回调 `onStartFailed`，
    **不出现** `Start successfully`，状态仍为 `STOPPED`；
    重进后再次启动不报 `BindException`。

## 9. 与既有文档的关系

- `00-documents/superpowers/specs/2026-09-16-audio-deprecated-teardown-removal-design.md`
  §4.7.1 的"超时加在资源操作层"原则，本次在 `stopServer()` 上首次落地。
- 同文档 §18（第九次复审）记录的 **H1（`receiverServer` 恒 null）已在本次解决**，见 §4.5。
  该 spec 的 §4.7.4、§8 风险表与 §18.2/§18.3 **均已标注"✅ 已实施"**。
- 同文档 §18 的 **H2（`AudioSender.senderClient` 赋值窗口）已在本次解决**，见 §4.7；
  **`AudioSender.stop()` 也一并重写**，见 §4.8。因此 spec §6.3 的"重试链确实终止"那条真机项
  **本批第一次具备通过的条件**，应当实测。
- 同目录 plan 的 Task 3 Step 3（`cleanupScope`）现已在 `AudioSender` 与 `AudioReceiver`
  **两侧都落地**；Step 4 中"`stop()` 改 suspend + 关闭顺序"这一半也已落地，当时仍待实施的
  `SuspendTeardownGate`、`teardownSteps()`、`stopping` 标志、`containsJob()` 守卫与队列改
  `Channel` 已于 2026-10-14 全部落地（spec §20）。
- 同目录 plan 的 Task 3 Step 3（`cleanupScope`）在 `AudioReceiver` 上已提前落地，见 §4.4；
  Task 3 Step 5 中"`stopServer()` 改 suspend"这一半也已落地。配套的 `SuspendTeardownGate`
  与 `stopping` 标志已于 2026-10-14 落地。
- spec §4.7.2 已据本文 §4.4 更新：`cleanupScope` 在 `AudioReceiver` 上标注"✅ 已落地"，
  并补上"它修的是 `BlockingOperationException`、必须与 `receiverServer` 修复同批"的定级；
  同时提醒 `AudioSender` 侧（Task 3 Step 3 未落地部分）适用同一条理由。
- spec §4.7.4 的 `stopping` 标志一节已补注：本次的 `lifecycleMutex` 只关闭了 `BaseNettyServer`
  自己那一层的 start/stop 竞态，`AudioReceiver` 的 `audioPlayer` / `micRecorder` / 两个 worker
  仍在锁的管辖之外，**该标志照旧必须实施**。
- spec 头部的"源码基准已漂移"清单已补上 `OpusStreamPlayer.kt`（逐帧日志门控）与本次
  netty 侧的第二批改动（`lifecycleMutex`、`releaseNetty()`）。

## 10. 复审记录（2026-09-20）

对 §1–§9 描述的全部改动做了一次独立复审，逐条与源码比对。**核心方案成立**：4 秒的来源
（默认 quiet period 2 秒 × 两个独立 `NioEventLoopGroup` 串行）按 `BaseNettyServer.kt:67-68`
核实无误，"先发后等 + quiet period 0"的改法正确；"Why???"注释的解答也正确。
以下 6 条已就地修正，代码与本文同批改动。

| 编号 | 级别 | 问题 | 修正 |
|---|---|---|---|
| R1 | HIGH | §4.4 把 `AudioReceiver` 的 `cleanupScope` 定级为"被动连带改动 / 没有变差"。实际它修掉的是一个 Netty `BlockingOperationException`：`onClientDisconnected()` 跑在事件循环线程上，改前会在 workerGroup 自己的线程上等该 group 终止。此前未爆，只因 `receiverServer` 恒 null 让最后一步从未执行——而 §4.5 恰好把它修好了 | §4.4 重写定级，并写明"与 §4.5 必须同批落地"；§8.3 第 5 项升为必测 |
| R2 | HIGH | §4.5 让 `startServer()` 快速返回、并把 `receiverServer` 提前赋值，但 `stopServer()` **不持有** `startMutex`。启动中到来的停止会看到 `::serverChannel.isInitialized == false` 而早退返回 false，随后 bind 成功、服务器占着端口没人再停。这条路径正是被 §4.5 第一次打通的 | `startMutex` 更名 `lifecycleMutex`，`stopServer()` 一并持有；§4.5 补说明；§8.3 新增第 9 项"启动中立即退出" |
| R3 | MEDIUM | 资源层超时只加在服务端，客户端 `release()` 没有——而 `release()` 正是 audio plan 里 `AudioSender.stop()` 的收尾步骤，跑在不设超时的门闩里。§9 自称"§4.7.1 原则首次落地"，落一半 | 抽出 `releaseNetty()`，加 `withTimeoutOrNull(NETTY_RELEASE_TIMEOUT_IN_MILLS)`，超时返回 `false`；§5、§8.3 同步 |
| R4 | MEDIUM | 两端的 `defaultServerInboundHandler?.release()` / `defaultInboundHandler?.release()` 都在超时保护之外且无 try/catch。它把控制权交给子类代码，一抛异常就会跳过 channel 与全部 event loop group | 两处各包 try/catch 并记日志 |
| R5 | MEDIUM | `awaitCompletion()` 是 `public`，等于给已发布模块新增公开面，而两个调用方都在模块内；§5 也没登记这条新增 API | 收窄为 `internal`；§4.1、§5 同步 |
| R6 | LOW | `resumeWithException(future.cause())` 中 `cause()` 是平台类型。当前不会 NPE（见 §4.1），但一旦为 null，异常会在 netty 线程抛出、continuation 永不恢复 | 加 `?: IllegalStateException(...)` 兜底 |

另有两条**不改、仅记录**：

- **常量放置**：`BaseNettyServer` 的两个常量原为文件级，而同一次改动里客户端把常量放进了
  `companion object`，且服务端本来就有 companion。已统一移入 companion，纯风格修正。
- ~~**顺序重复调用 `stopServer()` 会通知两次 `onStopped()`**（§4.4 第 2 条）。这是既有行为，
  不在本次范围；已写进 `stopServer()` 的 KDoc，并在 §8.3 第 5 项注明"不要当成本次回归"。~~
  2026-10-14 复审发现第二次调用不只是多一次回调，还会在已终止的 event loop 上白等 3 秒
  （§11 R7），因此一并改为 `STOPPED` 守卫（R9）；`AudioReceiver` 侧另有 `SuspendTeardownGate`。

### 10.1 复审确认无误的部分

- `bossGroup` / `workerGroup` 确为两个独立 `NioEventLoopGroup`（`:67-68`），并行关闭成立
- `startServer()` / `stopServer()` 的全部调用点都在协程内；`ScreenShareMasterActivity` 那三处
  `stopServer()` 是 Activity 自己的私有包装（`:511-514`），内部 `cs.launch`，不是 netty 的方法
- `BaseNettyLifecycleTest` 确实不碰 `startServer` / `stopServer`
- `basenetty/build.gradle.kts:26` 有 `api(libs.kotlin.coroutines.core)`
- 改动文件中超 100 字符的仍只有 `BaseNettyClient.kt:35`（102）与 `:88`（205），均为既有
- `withTimeoutOrNull` 在 `NonCancellable` 父作用域内**仍然有效**（它取消的是自己创建的
  `TimeoutCoroutine`），因此把这两个方法放进 audio 的 `SuspendTeardownGate` 不会让超时失效
- `startServer()` 里 `catch (e: CancellationException) { throw e }` 排在
  `catch (e: Exception)` 之前，顺序正确；与 `RejectedExecutionException` 分支互不影响

## 11. 第二次复审（2026-10-14）

Task 1–4 的 audio 实施完成后，对本文全部改动做了一次独立静态复审（未运行 Gradle）。
核心方案依旧成立；以下已就地修正，代码与本文同批改动。

| 编号 | 级别 | 问题 | 修正 |
|---|---|---|---|
| R7 | HIGH | `awaitCompletion()` 对**已完成且 executor 已终止**的 future 永不恢复：`DefaultPromise.addListener()` 把通知投递到已死的 event loop，Netty 只记日志。两条可达路径：`stopServer()` 后再 `startServer()`（`bind()` 返回挂在已死 boss loop 上的失败 promise）→ 在 `lifecycleMutex` 内永久挂起，之后所有 `stopServer()` 一起挂；同一实例第二次 `stopServer()`（`close()` 投递失败）→ 白等 3 秒超时、重复 `onStopped()`，与 §8.3 第 3 项矛盾。`startServer()` KDoc 承诺的 `RejectedExecutionException` 与对应 catch 分支因此成了死代码 | `awaitCompletion()` 增加 `isDone` 快速路径：成功直接返回，失败直接抛 cause；§4.1 的代码与"内联回调"那条说明同步订正；`startServer()` KDoc 改为"经 `onStartFailed` 报告，不抛出" |
| R8 | LOW | 被取消的 Netty future 的 `cause()` 是 `java.util.concurrent.CancellationException`，与协程的是同一个类；`awaitOrLog()` / `releaseNetty()` 会把它当作调用方自身被取消而重抛 | `failureOrNull()` 把 `isCancelled` 映射为 `IllegalStateException("Netty future was cancelled")` |
| R9 | MEDIUM | 对已 `STOPPED` 的实例再调 `stopServer()` 会重跑整个序列并二次回调 `onStopped()`；叠加 R7 还会白等 3 秒。`SocketServerActivity`（Stop 后返回）与 `WebSocketServerActivity`（Stop 后立即退出）今天就会走到 | 守卫加入 `STOPPED`，重复调用返回 `false`；KDoc、§4.2、§5、§8.3 第 5 项、§10 同步 |
| R10 | LOW | `startServer()` 在 bind 期间被取消：bind 在 boss loop 上继续并可能成功，但 `serverChannel` 永不赋值、状态停留 `UNINITIALIZED`，`stopServer()` 永远返回 `false` 而端口被占 | 取消分支先 `bindFuture?.channel()?.close()` 再重抛 |
| R11 | LOW | `BaseNettyClient.release()` 的 KDoc 首段仍写 "using **syncUninterruptibly** ... almost 2200ms ... NOT call in main thread"，与其后新增的说明自相矛盾（服务端对应 KDoc 当时已改，客户端漏了） | 首段重写 |
| R12 | LOW | `ServerConnectListener` 回调在持有 `lifecycleMutex` 期间执行，而 Mutex 不可重入；一个在回调里同步等待 `stopServer()` 结果的监听器会死锁。当前监听器都只 `launch`，无实际问题 | `stopServer()` KDoc 补一句 |
| R14 | LOW | 两端 `pipeline().removeAll { true }` 是空操作（改动前即存在）：`ChannelPipeline` 实现 `Iterable<Map.Entry<String, ChannelHandler>>`，Kotlin 解析到标准库的 `MutableIterable.removeAll(predicate)`，它对 `iterator()` 逐项 `remove()`；而 `DefaultChannelPipeline.iterator()` 是 `toMap().entrySet().iterator()`，`toMap()` 新建一个 `LinkedHashMap` 快照——删的是快照，pipeline 一个 handler 都没少。已用 `javap` 对照本机缓存的 netty-transport 4.1.93 与 4.1.110 字节码核实。关 channel 前清空 pipeline 本身也无必要：`close()` 会沿 pipeline 触发 `channelInactive` / `handlerRemoved` | 两端连同各自的 `try/catch` 与 "Remove pipeline handlers error." 日志一并删除；§4.2 代码块同步 |
| R15 | MEDIUM | `lifecycleMutex` 只串行化、不排序：demo 把 start 与 stop 起在两个独立协程上（点 Start 后立刻退出），若 stop 先拿到锁，它在 `::serverChannel.isInitialized == false` 处返回 `false`，随后 start 正常 bind，服务器占着端口没人再停，两个 group 也永不关闭。R2 只在 start 先拿到锁时成立。`AudioReceiver` 已由 spec §20.1 的 re-check 兜住，三个 socket demo 仍暴露 | "stop 先于 start" 分支改为 `releaseUnstartedServer()`：在锁内关掉两个 EventLoopGroup、置 `STOPPED`、仍返回 `false`、不回调 `onStopped()`（没有东西曾经启动）。随后拿到锁的 `startServer()` 看到 `STOPPED` 直接拒绝：回调 `onStartFailed(CONNECTION_ERROR_ALREADY_RELEASED)`，**状态保持 `STOPPED`**（若改成 `FAILED`，而 `serverChannel` 恰好由更早的一次 start 初始化过，后续 `stopServer()` 会穿过守卫把整套 teardown 连同 `onStopped()` 再跑一遍）——由状态而非 Netty 线程时序决定（在已终止的 group 上 bind 也会失败，但若 group 的关闭耗尽了 3 秒预算还停在 SHUTTING_DOWN，bind 可能被接受后再被拒，`awaitCompletion()` 将持锁永久挂起）。顺带修掉"从未 start 的实例 group 永不关闭"的既有泄漏。`BaseNettyLifecycleTest` 新增用例覆盖该分支与 R9 |
| R16 | HIGH | 3 秒预算若在 `serverChannel.close()` 阶段耗尽，`awaitOrLog` 重抛取消，`shutdownEventLoops()` 根本不执行——两个 group 连 `shutdownGracefully` 都没被调用，而状态仍置 `STOPPED`、回调 `onStopped()`，之后再调 `stopServer()` 直接返回 `false`，无法补救。这恰是预算要兜底的病态场景（boss loop 被 handler 卡住时 close 就会挂）。改动前（9/20 批次）即存在 | 拆成非挂起的 `requestEventLoopShutdown()`（`shutdownGracefully` 幂等）与 `awaitEventLoopShutdown()`（等 `terminationFuture()`）；`shutdownNetty()` 把请求放进 close 的 `finally`，超时穿过时也必然发出 |
| R17 | LOW | `releaseUnstartedServer()` 不释放 `defaultServerInboundHandler`、不清 `channelInitializer`，与完整路径不一致；`initHandler()` 装上的 handler 若持有资源则 stop-before-start 时永不释放 | 抽出 `releaseInboundHandler()` 两处共用 |
| R18 | LOW | `awaitCompletion()` 的两条修正（R7 快速路径、R8 取消映射）没有直接测试 | `BaseNettyLifecycleTest` 新增两个用例：已终止 executor 上的失败 promise 不挂起；被取消的 promise 以 `IllegalStateException("Netty future was cancelled")` 报告 |
| R19 | MEDIUM | `stopServer()` / `release()` / `disconnectManually()` 的调用方协程若在关闭体中途被取消，`CancellationException` 正确上抛，但状态停在 `UNINITIALIZED` / `RELEASING` / `DISCONNECTING` 这些"进行中"态：之后 `stopServer()`、`release()` 永远返回 `false`，`onStopped()` 不触发，event loop group 不再关闭。当前无调用方会取消这些作用域，但库的 KDoc 承诺"状态迁移与通知仍会执行"在取消路径不成立 | 三个方法在状态守卫**之后**的关闭体全部包进 `withContext(NonCancellable)`（客户端两处抽成 `disconnectNow()` / `releaseNow()`），`releaseUnstartedServer()` 同样。守卫与 `lifecycleMutex` 的等待留在外面，排队中被取消仍立即退出。`withTimeoutOrNull` 在 `NonCancellable` 内照常生效（§10.1），3 秒上界不受影响。与 audio 侧 `SuspendTeardownGate` / `MicRecorder.finishRecorderReleaseAndJoin()` 的规则一致 |
| R20 | MEDIUM | 客户端 `releaseNetty()` 没有同步 R16 的修法：`channel.close()` 若吃光 3 秒预算，`workerGroup.shutdownGracefully()` 根本不会被调用，group 永久泄漏 | 关 channel 抽成 `closeChannelAndAwait()` 放进 `try`，`finally` 里无条件发出 `shutdownGracefully(0, 2, SECONDS)`，随后等 `terminationFuture()` |
| R13 | LOW | 本文与代码不一致：§4.5 / §8.2 仍写 `startMutex`；§5 / §8.1 写 `stopServer()` 4 个调用点，实际 6 个（`SocketServerActivity`、`WebSocketServerActivity` 各两处），加上本轮 spec §20.1 新增的补偿调用为 7 个；§4.1 "已完成的 future 会内联回调"不成立（见 R7） | 本节同批订正 |

本文正文的行号均按 2026-09-20 的 checkout 记载，此后 `AudioActivity.kt`、`AudioReceiver.kt`、
`BaseNettyClient.kt` 等已被 audio 计划 Task 3/4 改动，行号有漂移；函数名是权威锚点。§4.4、§4.8、
§6.2、§9 中所有"仍待实施"的 audio 门闩相关项均已于 2026-10-14 落地，已就地加注。

### 11.1 不改、仅记录

本轮复审列出的待确认项（R15 排序、R14 空操作 `removeAll`、R19 取消后非终态）均已在确认后实施，
本节暂无遗留项。

## 12. 第三次复审（2026-10-15，外部）

两路独立审查（主审查者逐路径复核 + 一个通用审查代理全量扫 diff）对 basenetty 提出两条，均已修。
完整上下文见 `00-documents/2026-10-15-audio-netty-teardown-review-package_cc.md` §I。

| 编号 | 级别 | 问题 | 修法 |
|---|---|---|---|
| R21 | MEDIUM | `startServer()` 的 `bindFuture.awaitCompletion()` 是本批改动里**唯一没有上界的等待**，而它在 `lifecycleMutex` 之内。boss loop 一旦卡住，`stopServer()` 抢不到锁 → 挂在它后面的整条 teardown 链（`AudioReceiver` 的一次性门闩按设计无超时）永久停住。两路审查独立汇合于此处 | 新增 `NETTY_BIND_TIMEOUT_IN_MILLS = 3_000L`，`withTimeoutOrNull` 包住 bind；超时则 `close()` channel（`serverChannel` 在这条路径上从未赋值，否则端口会一直被占）、状态置 **`FAILED` 而非 `STOPPED`**（两个 group 还活着，后续 `stopServer()` 必须仍能释放）、经 `onStartFailed` 报告。顺带改用局部 `val future`，消掉可空 `var` 被 lambda 捕获的智能转型问题 |
| R22 | LOW | `releaseInboundHandler()` 刻意 try/catch 了，紧挨着的 `shutdownBounded { shutdownNetty() }` 没有。`awaitOrLog()` 只兜住两处 *await*；`close()` 或 `shutdownGracefully()` 自己抛的话会穿过 `withTimeoutOrNull` 与 `withContext(NonCancellable)`，于是 `connectState.set(STOPPED)` 与 `onStopped()` 都不执行 → 实例永久停在 `UNINITIALIZED`，后续每次 `stopServer()` 都在「Already stopping」返回 `false`，`onStopped()` 永不送达。与 `stopServer()` KDoc 自己的承诺直接矛盾 | 状态迁移与 `onStopped()` 移入 `finally`；`onStopped()` 另加 try/catch（子类代码，且 finally 里抛出会顶掉上面正在抛的那个）。`releaseUnstartedServer()` 同形处理。**异常仍向调用方传播**，只是两个承诺过的副作用不再被跳过 |

### 12.1 本轮确认无误、未改的部分

- **`withTimeoutOrNull` 在 `withContext(NonCancellable)` 内确实生效**（§8.2 的开放问题，现已关闭）：
  `withTimeout` 建的 `TimeoutCoroutine` 成为 body 的 Job，`NonCancellable` 只阻止外层取消**传入**，
  超时是这个 `TimeoutCoroutine` 取消自己；`awaitCompletion()` 用 `suspendCancellableCoroutine`，
  有可取消挂起点。两路审查独立得出同一结论。
- **`awaitCompletion()` 的 `isDone` 快速路径仍只收窄、未关闭 check-then-`addListener` 竞态**：
  future 可能在检查之后、`addListener` 之前完成，此时通知交给可能已终止的 executor。R21 之后
  **所有调用点都有上界**，后果从「永久挂起」降为「最多等一个超时」。加 post-add `isDone` 重检是
  正确的加固，本轮未做，留待下一轮。
- `connect()` 内保留的 `f.sync()`（§6.1）维持原判。另核清一条：`connect()` 的状态检查与
  `disconnectManually()` 用的是**同一个 `synchronized(this)`**，所以「`connect()` 里的
  `disconnectManually = false` 会覆盖掉刚设上的标志」这条竞态不存在。

### 12.2 未验证

R21 的超时分支与 R22 的 `finally` 路径**都没有自动化覆盖**：前者要把 boss loop 卡住，后者要让
`close()` / `shutdownGracefully()` 直接抛（netty 正常是 fail promise 而不是抛）。`BaseNettyLifecycleTest`
里没有合适手段构造，只能靠评审 + §8.3 第 3 项的真机观察。

## 13. 第四次复审（2026-10-16）

两路并行静态审查之一专看 basenetty + 3 个 socket demo 调用方：0 Critical、0 Important、5 Minor，
全部已修。完整上下文见 `00-documents/2026-10-15-audio-netty-teardown-review-package_cc.md` §J。

| 编号 | 级别 | 问题 | 修法 |
|---|---|---|---|
| R23 | LOW | `stopServer()` 与 `releaseUnstartedServer()` 里 `releaseInboundHandler()` 在 R22 加的 `try` **之外**。它只 `catch (e: Exception)`，子类 `release()` 抛 `Error` 时状态留在 `UNINITIALIZED`、两个 group 存活——正是 R22 注释声称已排除的情形 | 移到 `try` 第一句，两处同形 |
| R24 | LOW | demo 的 `WebSocketClientActivity` / `EventBusBridgeClientActivity` 删掉紧随 `launch` 的 `cancel()` 后 scope 永不取消；"取消帮不上忙"只对立刻取消成立 | `scope.launch { try { client?.release() } finally { scope.cancel() } }`：在 `release()` 返回后取消，能取消一个仍在连接中的 `connect()`；`import kotlinx.coroutines.cancel` 恢复 |
| R25 | LOW | CHANGELOG：标题只写"服务端"但条目含客户端；`release()` 一条"此前只在状态守卫处返回 `false`"过窄（旧实现 `shutdownGracefully` 失败或抛异常也返回 `false`） | 两处措辞订正 |
| R26 | LOW | CHANGELOG 明文承诺的三条路径无测试：取消 `stopServer()` 调用方仍落 `STOPPED` 且 `onStopped()` 一次；启动失败后 stop 释放 group 并拒绝再次启动；`BaseNettyClient.release()` 返回 `true` 且落 `UNINITIALIZED` | `BaseNettyLifecycleTest` 新增两个用例（`CoroutineStart.UNDISPATCHED` + `cancel()`；`ServerSocket(0)` 占端口后 `TestServer(port = ...)`），既有 `connect failure` 用例末尾补 `release()` 断言；`TestServer` 增加 `port` 参数 |

另：`stopServer()` 的 "Already stopping" 守卫现已不可达（mutex 串行化 + `finally` 必落 `STOPPED`），
保留为防御并加注释。

### 13.1 本轮确认无误、未改的部分

- 状态机逐路径走查：无法构造第二次 `onStopped()`、stop 后滞留 `STARTED` / `UNINITIALIZED`、或
  `serverChannel` 已初始化却为 `FAILED` 的状态。
- `bootstrap.bind()` 在已终止的 group 上返回**失败的 future** 而不是抛（`AbstractUnsafe.register`
  捕获 `RejectedExecutionException` 后 `safeSetFailure`），因此 `catch (e: RejectedExecutionException)`
  分支是经 `awaitCompletion()` 快速路径到达的；CE 分支先于 `Exception` 分支；`bindFuture?.channel()
  ?.close()` 在 bind 返回 pending future 后安全（`eventLoop` 已设置）。
- 全仓调用点（`ScreenShareMasterActivity`、`SocketServerActivity`、`WebSocketServerActivity`、
  `AudioReceiver`、`AudioActivity`）都已在 `launch {}` / suspend 上下文中调用；没有调用方依赖
  `startServer()` 阻塞到服务器关闭。
- 既有遗留维持原判：`connect()` 的 `f.sync()`（§6.1）；`disconnectManually()` 守卫放行 `RELEASING`
  并覆盖为 `DISCONNECTING`（守卫与 HEAD 字节相同）；`defaultServerInboundHandler = null` 与 worker
  正在跑的 `initChannel` 之间的顺序（HEAD 相同）。

### 13.2 未验证

R26 的三个用例未在本机执行。R23 的 `Error` 路径与 R22 一样无自动化覆盖。

### 13.3 提出但未实施：客户端 `release()` 终态不对称

`release()` 超时或 `terminationFuture` 失败时状态停在私有态 `RELEASING`，而服务端三条路径都落
`STOPPED`；另有"从未连接的客户端调 `release()` 被 `UNINITIALIZED` 守卫拒绝、`NioEventLoopGroup`
泄漏"这一更大的不对称。分析、原因与两套修改方案（均未动代码）见
`00-documents/2026-10-16-basenetty-client-release-terminal-state_cc.md`，待所有者决定后再实施。

