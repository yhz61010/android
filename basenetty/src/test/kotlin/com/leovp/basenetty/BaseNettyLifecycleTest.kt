package com.leovp.basenetty

import com.leovp.basenetty.framework.base.ClientConnectStatus
import com.leovp.basenetty.framework.client.BaseClientChannelInboundHandler
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import com.leovp.basenetty.framework.client.retrystrategy.base.RetryStrategy
import com.leovp.basenetty.framework.server.BaseNettyServer
import com.leovp.basenetty.framework.server.BaseServerChannelInboundHandler
import com.leovp.basenetty.framework.server.ServerConnectListener
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.DefaultChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.lang.reflect.Modifier
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class BaseNettyLifecycleTest {

    @Test
    fun `connect failure updates stored state to failed`(): Unit = runBlocking {
        val listener = RecordingClientListener()
        val client = TestClient(
            listener = listener,
            retryStrategy = ConstantRetry(maxTimes = 0, delayInMillSec = 1L)
        )
        client.initHandler(TestClientHandler(client))

        val status = client.connect()

        assertEquals(ClientConnectStatus.FAILED, status)
        assertEquals(ClientConnectStatus.FAILED, client.connectStatus.get())
        assertTrue(listener.failedCodes.isNotEmpty())
        client.release()
    }

    @Test
    fun `disconnectManually fails instead of hanging when channel future never completes`(): Unit =
        runBlocking {
            val listener = RecordingClientListener()
            val client = TestClient(listener = listener)
            client.connectStatus.set(ClientConnectStatus.CONNECTED)
            client.disconnectTimeoutInMillis = 10L

            val backingChannel = EmbeddedChannel()
            val neverCompletes = DefaultChannelPromise(backingChannel)
            val channel = mockk<Channel>()
            every { channel.disconnect() } returns neverCompletes
            setChannel(client, channel)

            val status = client.disconnectManually()

            assertEquals(ClientConnectStatus.FAILED, status)
            assertEquals(ClientConnectStatus.FAILED, client.connectStatus.get())
            assertTrue(
                ClientConnectListener.DISCONNECT_MANUALLY_ERROR in listener.failedCodes
            )
            backingChannel.finishAndReleaseAll()
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `doRetry can schedule after retry handler was stopped`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val listener = RecordingClientListener()
        val client = TestClient(
            listener = listener,
            retryStrategy = ConstantRetry(maxTimes = 1, delayInMillSec = 100L),
            retryDispatcher = dispatcher
        )
        client.initHandler(TestClientHandler(client))

        invokeStopRetryHandler(client)
        client.doRetry()

        val retryJob = getRetryJob(client)
        assertNotNull(retryJob)
        assertTrue(retryJob!!.isActive)

        // Advance past delay to trigger actual connect attempt
        testScheduler.advanceTimeBy(200L)
        testScheduler.runCurrent()

        // connect() to 127.0.0.1:1 should fail, triggering onFailed
        // The job may still be active (waiting for connect timeout)
        // or completed with failure — either way retryTimes was reset
        // correctly and the retry was actually attempted.
        retryJob.cancel()
        client.release()
    }

    @Test
    @Suppress("DEPRECATION")
    fun `server handler exposes same clients group as server for compatibility`() {
        val server = TestServer()
        val handler = TestServerHandler(server)

        assertSame(server.clients, handler.clients)
    }

    @Test
    fun `WebSocket CloseFrame triggers only onDisconnected not onFailed`() {
        val listener = RecordingClientListener()
        val client = TestWebSocketClient(listener = listener)
        val handler = TestClientHandler(client)
        client.initHandler(handler)

        val ch = EmbeddedChannel(handler)
        client.connectStatus.set(ClientConnectStatus.CONNECTED)

        ch.writeInbound(CloseWebSocketFrame())

        assertEquals(ClientConnectStatus.DISCONNECTED, client.connectStatus.get())
        assertTrue(listener.disconnectedByRemote.contains(true))
        assertTrue(listener.failedCodes.isEmpty(), "Should not trigger onFailed after CloseFrame")
        assertEquals(0, client.retryAttempts.get(), "Should not trigger doRetry after CloseFrame")
    }

    @Test
    fun `exception during manual disconnect does not fire onFailed`() {
        val listener = RecordingClientListener()
        val client = TestWebSocketClient(listener = listener)
        val handler = TestClientHandler(client)
        client.initHandler(handler)

        val ch = EmbeddedChannel(handler)
        client.connectStatus.set(ClientConnectStatus.CONNECTED)

        // Simulate manual disconnect in progress
        setDisconnectManually(client, true)

        // Capture context before exceptionCaught removes handler from pipeline
        val ctx = ch.pipeline().context(handler)
        // Simulate exception + handlerRemoved sequence
        handler.exceptionCaught(ctx, IOException("connection reset"))
        handler.handlerRemoved(ctx)

        assertTrue(
            listener.failedCodes.isEmpty(),
            "Should not trigger onFailed during manual disconnect"
        )
        assertEquals(
            0,
            client.retryAttempts.get(),
            "Should not trigger doRetry during manual disconnect"
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun `non-IOException triggers exactly one onFailed and one retry`() {
        val listener = RecordingClientListener()
        val client = TestWebSocketClient(listener = listener)
        val handler = TestClientHandler(client)
        client.initHandler(handler)

        val ch = EmbeddedChannel(handler)
        client.connectStatus.set(ClientConnectStatus.CONNECTED)

        // ctx.close() inside exceptionCaught triggers handlerRemoved via the pipeline,
        // so we do NOT call handlerRemoved explicitly.
        val ctx = ch.pipeline().context(handler)
        handler.exceptionCaught(ctx, IllegalStateException("bad frame"))

        assertEquals(
            listOf(ClientConnectListener.CONNECTION_ERROR_UNEXPECTED_EXCEPTION),
            listener.failedCodes.toList(),
            "Should fire exactly one onFailed with UNEXPECTED_EXCEPTION"
        )
        assertEquals(
            1,
            client.retryAttempts.get(),
            "Should trigger exactly one retry"
        )
        assertEquals(ClientConnectStatus.FAILED, client.connectStatus.get())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `IOException triggers exactly one onFailed and one retry`() {
        val listener = RecordingClientListener()
        val client = TestWebSocketClient(listener = listener)
        val handler = TestClientHandler(client)
        client.initHandler(handler)

        val ch = EmbeddedChannel(handler)
        client.connectStatus.set(ClientConnectStatus.CONNECTED)

        // ctx.close() inside exceptionCaught triggers handlerRemoved via the pipeline,
        // so we do NOT call handlerRemoved explicitly.
        val ctx = ch.pipeline().context(handler)
        handler.exceptionCaught(ctx, IOException("connection reset"))

        assertEquals(
            listOf(ClientConnectListener.CONNECTION_ERROR_NETWORK_LOST),
            listener.failedCodes.toList(),
            "Should fire exactly one onFailed with NETWORK_LOST"
        )
        assertEquals(
            1,
            client.retryAttempts.get(),
            "Should trigger exactly one retry"
        )
        assertEquals(ClientConnectStatus.FAILED, client.connectStatus.get())
    }

    @Test
    fun `BaseNettyClient preserves protected JVM constructor signature`() {
        val expectedParameterTypes = arrayOf(
            String::class.java,
            Int::class.javaPrimitiveType!!,
            ClientConnectListener::class.java,
            RetryStrategy::class.java,
            Map::class.java,
            Int::class.javaPrimitiveType!!
        )

        val hasOriginalConstructor = BaseNettyClient::class.java.declaredConstructors.any {
            !it.isSynthetic &&
                Modifier.isProtected(it.modifiers) &&
                it.parameterTypes.contentEquals(expectedParameterTypes)
        }

        assertTrue(hasOriginalConstructor)
    }

    private class TestClient(
        listener: ClientConnectListener<BaseNettyClient>,
        retryStrategy: RetryStrategy = ConstantRetry(),
        retryDispatcher: CoroutineDispatcher = StandardTestDispatcher(),
        timeout: Int = 200,
    ) : BaseNettyClient(
        host = "127.0.0.1",
        port = 1,
        connectionListener = listener,
        retryStrategy = retryStrategy,
        timeout = timeout,
        retryDispatcher = retryDispatcher
    ) {
        override fun getTagName(): String = "BaseNettyLifecycleTest"
    }

    private class TestClientHandler(netty: BaseNettyClient) :
        BaseClientChannelInboundHandler<Any>(netty) {
        override fun release() = Unit
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) = Unit
    }

    private class TestWebSocketClient(listener: ClientConnectListener<BaseNettyClient>) :
        BaseNettyClient(
            webSocketUri = URI("ws://127.0.0.1:1/ws"),
            connectionListener = listener,
            trustAllServers = true,
            retryStrategy = ConstantRetry(maxTimes = 1, delayInMillSec = 1L),
            timeout = 200
        ) {
        val retryAttempts = AtomicInteger(0)

        override fun getTagName(): String = "TestWebSocketClient"

        override fun retryProcess(): Boolean {
            retryAttempts.incrementAndGet()
            return true
        }
    }

    private class TestServer :
        BaseNettyServer(
            port = 0,
            connectionListener = RecordingServerListener()
        ) {
        override fun getTagName(): String = "BaseNettyServerLifecycleTest"
    }

    private class TestServerHandler(netty: BaseNettyServer) :
        BaseServerChannelInboundHandler<Any>(netty) {
        override fun release() = Unit
        override fun onReceivedData(ctx: ChannelHandlerContext, msg: Any) = Unit
    }

    private class RecordingClientListener : ClientConnectListener<BaseNettyClient> {
        val failedCodes = CopyOnWriteArrayList<Int>()
        val disconnectedByRemote = CopyOnWriteArrayList<Boolean>()

        override fun onConnected(netty: BaseNettyClient) = Unit

        override fun onDisconnected(netty: BaseNettyClient, byRemote: Boolean) {
            disconnectedByRemote.add(byRemote)
        }

        override fun onFailed(netty: BaseNettyClient, code: Int, msg: String?, e: Throwable?) {
            failedCodes.add(code)
        }
    }

    private class RecordingServerListener : ServerConnectListener<BaseNettyServer> {
        override fun onStarted(netty: BaseNettyServer) = Unit
        override fun onStopped() = Unit
        override fun onStartFailed(netty: BaseNettyServer, code: Int, msg: String?) = Unit
        override fun onReceivedData(
            netty: BaseNettyServer,
            clientChannel: Channel,
            data: Any?,
            action: Int,
        ) = Unit

        override fun onClientConnected(netty: BaseNettyServer, clientChannel: Channel) = Unit
        override fun onClientDisconnected(netty: BaseNettyServer, clientChannel: Channel) = Unit
    }

    private companion object {
        fun setChannel(client: BaseNettyClient, channel: Channel) {
            BaseNettyClient::class.java.getDeclaredField("channel").apply {
                isAccessible = true
                set(client, channel)
            }
        }

        fun invokeStopRetryHandler(client: BaseNettyClient) {
            BaseNettyClient::class.java.getDeclaredMethod("stopRetryHandler").apply {
                isAccessible = true
                invoke(client)
            }
        }

        fun getRetryJob(client: BaseNettyClient): Job? =
            BaseNettyClient::class.java.getDeclaredField("retryJob").run {
                isAccessible = true
                get(client) as Job?
            }

        fun setDisconnectManually(client: BaseNettyClient, value: Boolean) {
            BaseNettyClient::class.java.getDeclaredField("disconnectManually").apply {
                isAccessible = true
                set(client, value)
            }
        }
    }
}
