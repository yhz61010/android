package com.leovp.opengl

import android.opengl.GLSurfaceView
import android.util.Size
import com.leovp.opengl.ui.LeoGLSurfaceView
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class LeoGLSurfaceViewFrameOwnershipTest {
    @Test
    fun `cancelled upload retains pixels after the caller reuses its array`() = runTest {
        val view = LeoGLSurfaceView(RuntimeEnvironment.getApplication())
        val renderer = field(view, "renderer", LeoGLSurfaceView::class.java) as GLRenderer
        GLRenderer::class.java.getDeclaredField("hasVisibility").apply {
            isAccessible = true
            set(renderer, true)
        }
        val frames = field(renderer, "awaitedFrames", GLRenderer::class.java)
            as AwaitedFrameRenderer
        val pixels = byteArrayOf(1, 2, 3, 4, 5, 6)
        val producer = launch {
            view.renderAndAwait(pixels, Size(2, 2), Size(2, 2))
        }
        val preparing = CountDownLatch(1)
        val continuePrepare = CountDownLatch(1)
        val drawFailure = AtomicReference<Throwable?>()
        var glThread: Thread? = null
        try {
            runCurrent()
            val pending = field(frames, "pending", AwaitedFrameRenderer::class.java)
                as AtomicReference<*>
            val request = requireNotNull(pending.get())
            val prepareField = request.javaClass.getDeclaredField("prepare").apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val prepare = prepareField.get(request) as () -> Unit
            prepareField.set(request, {
                preparing.countDown()
                check(continuePrepare.await(5, TimeUnit.SECONDS))
                prepare()
            })
            glThread = Thread {
                try {
                    frames.drawFrame {}
                } catch (error: Throwable) {
                    drawFailure.set(error)
                }
            }.apply { start() }
            assertTrue(preparing.await(2, TimeUnit.SECONDS))

            producer.cancelAndJoin()
            pixels.fill(9)
            continuePrepare.countDown()
            glThread.join(3_000)
            assertTrue(!glThread.isAlive)
            drawFailure.get()?.let { throw it }

            val y = field(renderer, "y", GLRenderer::class.java) as ByteBuffer
            val u = field(renderer, "u", GLRenderer::class.java) as ByteBuffer
            val v = field(renderer, "v", GLRenderer::class.java) as ByteBuffer
            assertEquals(listOf<Byte>(1, 2, 3, 4), List(4) { y.get(it) })
            assertEquals(5.toByte(), u.get(0))
            assertEquals(6.toByte(), v.get(0))
        } finally {
            continuePrepare.countDown()
            producer.cancelAndJoin()
            glThread?.join(3_000)
            GLSurfaceView::class.java.getDeclaredMethod("onDetachedFromWindow").apply {
                isAccessible = true
                invoke(view)
            }
        }
    }

    private fun field(owner: Any, name: String, type: Class<*>): Any? =
        type.getDeclaredField(name).apply { isAccessible = true }.get(owner)
}
