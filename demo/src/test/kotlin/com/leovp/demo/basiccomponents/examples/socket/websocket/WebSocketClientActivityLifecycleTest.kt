package com.leovp.demo.basiccomponents.examples.socket.websocket

import android.view.View
import com.leovp.basenetty.framework.base.ClientConnectStatus
import com.leovp.basenetty.framework.client.BaseNettyClient
import com.leovp.basenetty.framework.client.ClientConnectListener
import com.leovp.basenetty.framework.client.retrystrategy.ConstantRetry
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.netty.channel.Channel
import io.netty.channel.DefaultChannelPromise
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.ImmediateEventExecutor
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class WebSocketClientActivityLifecycleTest {
    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun `release waits for manual disconnect before terminating the owned worker group`() =
        checkDisconnectThenRelease(destroy = false)

    @Test
    fun `destroy waits for manual disconnect before terminating the owned worker group`() =
        checkDisconnectThenRelease(destroy = true)

    private fun checkDisconnectThenRelease(destroy: Boolean) {
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val controller = Robolectric.buildActivity(WebSocketClientActivity::class.java).setup()
        val activity = controller.get()
        setField(activity, "cs", scope)
        val client = WebSocketClientActivity.WebSocketClientDemo(
            URI("ws://127.0.0.1:1"),
            mockk<ClientConnectListener<BaseNettyClient>>(relaxed = true),
            false,
            ConstantRetry(0, 1)
        )
        val channel = mockk<Channel>()
        val disconnect = DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE)
        every { channel.disconnect() } returns disconnect
        every { channel.close() } returns
            DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE).setSuccess()
        BaseNettyClient::class.java.getDeclaredField("channel").apply {
            isAccessible = true
            set(client, channel)
        }
        val group = BaseNettyClient::class.java.getDeclaredField("workerGroup").run {
            isAccessible = true
            get(client) as NioEventLoopGroup
        }
        client.connectStatus.set(ClientConnectStatus.CONNECTED)
        setField(activity, "webSocketClient", client)
        val view = View(activity)
        try {
            activity.onDisconnectClick(view)
            scheduler.runCurrent()
            assertEquals(ClientConnectStatus.DISCONNECTING, client.connectStatus.get())

            if (destroy) {
                controller.pause().stop().destroy()
            } else {
                repeat(2) { activity.onConnectRelease(view) }
                activity.onDisconnectClick(view)
            }
            scheduler.runCurrent()

            assertSame(
                "The Activity must retain ownership until disconnect and release finish",
                client,
                field(activity, "webSocketClient")
            )
            assertFalse(group.isShuttingDown)
            disconnect.setSuccess()
            scheduler.runCurrent()
            // A done Netty future can wake awaiters before its listeners have dispatched the
            // coroutine continuation. Wait behind release's listener before draining test tasks.
            val terminationNotified = CountDownLatch(1)
            group.terminationFuture().addListener { terminationNotified.countDown() }
            assertTrue(terminationNotified.await(5, TimeUnit.SECONDS))
            scheduler.runCurrent()

            assertTrue(
                "The real client release must terminate its event loop group",
                group.isTerminated
            )
            assertEquals(ClientConnectStatus.UNINITIALIZED, client.connectStatus.get())
            assertNull(field(activity, "webSocketClient"))
        } finally {
            disconnect.trySuccess()
            scheduler.runCurrent()
            runBlocking { client.release() }
            if (!destroy) controller.pause().stop().destroy()
            scheduler.runCurrent()
            scope.cancel()
        }
    }

    private fun field(activity: WebSocketClientActivity, name: String): Any? =
        WebSocketClientActivity::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(activity)
        }

    private fun setField(activity: WebSocketClientActivity, name: String, value: Any) {
        WebSocketClientActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(activity, value)
        }
    }
}
