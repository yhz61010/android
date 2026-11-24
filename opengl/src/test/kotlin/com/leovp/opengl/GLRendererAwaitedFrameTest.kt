package com.leovp.opengl

import android.opengl.GLES20
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class GLRendererAwaitedFrameTest {
    @Test
    fun `resubmitting video dimensions cannot undo the current surface aspect ratio`() {
        Class.forName(GLES20::class.java.name, true, GLES20::class.java.classLoader)
        mockkStatic(GLES20::class)
        every { GLES20.glViewport(any(), any(), any(), any()) } returns Unit
        try {
            val renderer = GLRenderer(RuntimeEnvironment.getApplication())
            renderer.setVideoDimension(4, 2, 400, 800)
            renderer.onSurfaceChanged(mockk<GL10>(), 800, 400)
            renderer.setVideoDimension(4, 2, 400, 800)

            val field = GLRenderer::class.java.getDeclaredField("pointCoord")
                .apply { isAccessible = true }
            val vertices = field.get(renderer) as FloatBuffer
            assertEquals(-1f, vertices.get(0))
            assertEquals(-1f, vertices.get(1))
            assertEquals(1f, vertices.get(6))
            assertEquals(1f, vertices.get(7))
        } finally {
            unmockkStatic(GLES20::class)
        }
    }

    @Test
    fun `the real draw callback consumes one queued frame and wakes its producer`() = runTest {
        val renderer = GLRenderer(RuntimeEnvironment.getApplication())
        val uploads = mutableListOf<Int>()
        val producer = launch {
            renderer.renderAndAwait({ uploads += 1 }, {})
            renderer.renderAndAwait({ uploads += 2 }, {})
        }
        runCurrent()
        assertTrue(uploads.isEmpty())
        assertFalse(producer.isCompleted)

        renderer.onDrawFrame(mockk<GL10>())
        runCurrent()
        assertEquals(listOf(1), uploads)
        assertFalse(producer.isCompleted)

        renderer.onDrawFrame(mockk<GL10>())
        producer.join()
        assertEquals(listOf(1, 2), uploads)
    }
}
