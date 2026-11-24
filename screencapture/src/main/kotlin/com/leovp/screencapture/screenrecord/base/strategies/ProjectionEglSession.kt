package com.leovp.screencapture.screenrecord.base.strategies

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.leovp.log.LogContext

/**
 * One projection input and EGL context, owned exclusively by [thread]. Encoder surfaces are
 * borrowed; callers detach the EGL output before releasing an encoder surface. Screenshots use
 * an FBO in the same context and never disconnect the producer or stop the encoder.
 */
internal class ProjectionEglSession(private val onFailure: (Throwable) -> Unit) {
    private val thread by lazy { HandlerThread("projection-egl").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val pendingScreenshotCount = AtomicInteger()
    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface? = null
    private var output: EGLSurface? = null
    private var texture: SurfaceTexture? = null
    private var input: Surface? = null
    private var textureId = 0
    private var program = 0
    private var width = 0
    private var height = 0
    private var recording = false
    private var hasFrame = false
    private val transform = FloatArray(16)
    private val screenshots = ArrayDeque<Screenshot>()
    private val vertices = ByteBuffer.allocateDirect(VERTICES.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(VERTICES).position(0) }

    fun initialize(encoderSurface: Surface, width: Int, height: Int): Surface = call {
        initializeEgl()
        program = createProgram()
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        textureParameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        texture = SurfaceTexture(textureId).also {
            it.setDefaultBufferSize(width, height)
            it.setOnFrameAvailableListener({ onFrame() }, handler)
        }
        input = Surface(checkNotNull(texture))
        replaceOutputOnThread(encoderSurface, width, height)
        checkNotNull(input)
    }

    fun setRecording(enabled: Boolean) = call {
        recording = enabled
        if (!enabled) clearScreenshots()
    }

    fun detachOutput() = call {
        recording = false
        makeCurrent(pbuffer)
        output?.let { check(EGL14.eglDestroySurface(display, it)) }
        output = null
    }

    fun replaceOutput(encoderSurface: Surface, width: Int, height: Int) = call {
        replaceOutputOnThread(encoderSurface, width, height)
    }

    fun resize(width: Int, height: Int) = call {
        texture?.setDefaultBufferSize(width, height)
        this.width = width
        this.height = height
        // Invalidate the cached frame until another texture update arrives. The producer
        // may still have an older frame in flight while its display is being resized.
        hasFrame = false
        clearScreenshots()
    }

    fun screenshot(width: Int, height: Int, result: (Bitmap) -> Unit) {
        validateScreenshotSize(width, height)
        check(!closed.get() && !failed.get()) { "Projection EGL session has ended" }
        while (true) {
            val pending = pendingScreenshotCount.get()
            check(pending < MAX_PENDING_SCREENSHOTS) { "Too many pending screenshots" }
            if (pendingScreenshotCount.compareAndSet(pending, pending + 1)) break
        }
        if (!handler.post {
                if (closed.get() || failed.get() || !recording) {
                    pendingScreenshotCount.decrementAndGet()
                } else {
                    screenshots.addLast(Screenshot(width, height, result))
                    if (hasFrame) deliverScreenshots()
                }
            }
        ) {
            pendingScreenshotCount.decrementAndGet()
            error("Projection EGL thread rejected screenshot")
        }
    }

    private fun onFrame() {
        if (closed.get() || failed.get()) return
        try {
            makeCurrent(pbuffer)
            val source = checkNotNull(texture)
            source.updateTexImage()
            if (closed.get() || failed.get()) return
            source.getTransformMatrix(transform)
            hasFrame = true
            if (recording) {
                makeCurrent(output)
                draw(width, height)
                check(EGLExt.eglPresentationTimeANDROID(display, output, source.timestamp))
                check(EGL14.eglSwapBuffers(display, output))
                deliverScreenshots()
            }
        } catch (error: Throwable) {
            fail(error)
        }
    }

    private fun fail(error: Throwable) {
        recording = false
        clearScreenshots()
        if (failed.compareAndSet(false, true)) onFailure(error)
    }

    private fun clearScreenshots() {
        while (screenshots.isNotEmpty()) {
            screenshots.removeFirst()
            pendingScreenshotCount.decrementAndGet()
        }
    }

    private fun deliverScreenshots() {
        while (screenshots.isNotEmpty()) {
            val request = screenshots.removeFirst()
            pendingScreenshotCount.decrementAndGet()
            var bitmap: Bitmap? = null
            try {
                bitmap = readBitmap(request.width, request.height)
                if (!closed.get() && !failed.get()) {
                    request.result(bitmap)
                    bitmap = null // Ownership was transferred to the receiver.
                }
            } catch (error: Exception) {
                recoverScreenshotFailure(error)
            } catch (error: Throwable) {
                fail(error)
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun recoverScreenshotFailure(error: Exception) {
        LogContext.log.e("ProjectionEgl", "Screenshot request failed", error)
        // A failed FBO allocation can leave GL_INVALID_VALUE/OUT_OF_MEMORY pending. Consume that
        // request's errors so the next encoder draw cannot mistake them for a frame failure.
        repeat(MAX_GL_ERRORS) {
            when (GLES20.glGetError()) {
                GLES20.GL_NO_ERROR -> return
                GL_CONTEXT_LOST -> {
                    fail(error)
                    return
                }
            }
        }
        fail(IllegalStateException("Screenshot GL error state did not clear", error))
    }

    private fun initializeEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != null && display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL initialize failed" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, CONFIG_ATTRIBUTES, 0, configs, 0, 1, count, 0))
        check(count[0] > 0) { "No recordable EGL config" }
        config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(context != null && context != EGL14.EGL_NO_CONTEXT) { "EGL context creation failed" }
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        check(pbuffer != null && pbuffer != EGL14.EGL_NO_SURFACE) { "EGL pbuffer creation failed" }
        makeCurrent(pbuffer)
    }

    private fun replaceOutputOnThread(surface: Surface, width: Int, height: Int) {
        check(output == null) { "Detach the previous encoder EGL surface before replacing it" }
        output = EGL14.eglCreateWindowSurface(
            display, config, surface, intArrayOf(EGL14.EGL_NONE), 0
        )
        check(output != null && output != EGL14.EGL_NO_SURFACE) { "EGL encoder surface failed" }
        this.width = width
        this.height = height
    }

    private fun makeCurrent(surface: EGLSurface?) {
        check(surface != null && surface != EGL14.EGL_NO_SURFACE) { "Missing EGL surface" }
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL makeCurrent failed" }
    }

    private fun draw(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "image"), 0)
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "transform"), 1, false, transform, 0
        )
        val position = GLES20.glGetAttribLocation(program, "position")
        val uv = GLES20.glGetAttribLocation(program, "uv")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, VERTEX_STRIDE, vertices)
        vertices.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, VERTEX_STRIDE, vertices)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "Projection GL draw failed" }
    }

    private fun readBitmap(width: Int, height: Int): Bitmap {
        validateScreenshotSize(width, height)
        makeCurrent(pbuffer)
        val textures = IntArray(1)
        val framebuffers = IntArray(1)
        try {
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glGenFramebuffers(1, framebuffers, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
            textureParameters(GLES20.GL_TEXTURE_2D)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[0])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, textures[0], 0
            )
            check(
                GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) ==
                    GLES20.GL_FRAMEBUFFER_COMPLETE
            ) { "Screenshot framebuffer incomplete" }
            draw(width, height)
            val pixels = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL)
            GLES20.glReadPixels(
                0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels
            )
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "Screenshot readPixels failed" }
            return bitmapFromGlPixels(width, height, pixels)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, framebuffers, 0)
            GLES20.glDeleteTextures(1, textures, 0)
        }
    }

    /**
     * Transfers terminal cleanup to the EGL thread before waiting at most two seconds. On timeout
     * or caller interruption, the queued task still owns all native resources and [retireBorrowed]
     * until GL teardown finishes. The caller must never reuse or independently free them. An
     * interrupt is restored on the caller. Repeated release never transfers ownership twice.
     */
    fun release(retireBorrowed: () -> Unit = {}, onRetired: (Throwable?) -> Unit = {}) {
        if (!closed.compareAndSet(false, true)) return
        val task = FutureTask {
            var failure: Throwable? = null
            try {
                releaseOnThread()
            } catch (error: Throwable) {
                failure = error
            }
            try {
                retireBorrowed()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            } finally {
                thread.quitSafely()
            }
            try {
                onRetired(failure)
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
            failure?.let {
                // This task may finish after its external waiter timed out or was interrupted.
                LogContext.log.e("ProjectionEgl", "Projection retirement failed", it)
                throw it
            }
        }
        if (Thread.currentThread() === thread) {
            task.run()
        } else {
            check(handler.post(task)) { "Projection EGL thread rejected retirement" }
        }
        await(task)
        if (Thread.currentThread() !== thread) thread.join(THREAD_TIMEOUT_MS)
    }

    private fun releaseOnThread() {
        recording = false
        clearScreenshots()
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        cleanup { texture?.setOnFrameAvailableListener(null) }
        cleanup { input?.release() }
        input = null
        cleanup { texture?.release() }
        texture = null
        val eglDisplay = display
        if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            cleanup {
                makeCurrent(pbuffer)
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                GLES20.glDeleteProgram(program)
            }
            cleanup {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
            }
            cleanup { output?.let { EGL14.eglDestroySurface(eglDisplay, it) } }
            cleanup { pbuffer?.let { EGL14.eglDestroySurface(eglDisplay, it) } }
            cleanup { context?.let { EGL14.eglDestroyContext(eglDisplay, it) } }
            cleanup { EGL14.eglReleaseThread() }
            cleanup { EGL14.eglTerminate(eglDisplay) }
        }
        display = null
        context = null
        output = null
        pbuffer = null
        failure?.let { throw it }
    }

    private fun <T> call(allowClosed: Boolean = false, action: () -> T): T {
        check(allowClosed || !closed.get()) { "Projection EGL session has been released" }
        if (Thread.currentThread() === thread) return action()
        val task = FutureTask(action)
        check(handler.post(task)) { "Projection EGL thread rejected operation" }
        return await(task)
    }

    private fun <T> await(task: FutureTask<T>): T {
        try {
            return task.get(THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: java.util.concurrent.ExecutionException) {
            throw (error.cause ?: error)
        }
    }

    private data class Screenshot(val width: Int, val height: Int, val result: (Bitmap) -> Unit)

    private companion object {
        const val THREAD_TIMEOUT_MS = 2_000L
        const val MAX_PENDING_SCREENSHOTS = 4
        const val MAX_GL_ERRORS = 16
        const val GL_CONTEXT_LOST = 0x0507
        const val BYTES_PER_PIXEL = 4
        const val VERTEX_STRIDE = 4 * Float.SIZE_BYTES
        const val EGL_RECORDABLE_ANDROID = 0x3142
        val VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f
        )
        val CONFIG_ATTRIBUTES = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE
        )
        const val VERTEX_SHADER = "attribute vec4 position; attribute vec2 uv;" +
            "uniform mat4 transform; varying vec2 texCoord;" +
            "void main(){ gl_Position=position; texCoord=(transform*vec4(uv,0.,1.)).xy; }"
        const val FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float; varying vec2 texCoord; uniform samplerExternalOES image;" +
            "void main(){ gl_FragColor=texture2D(image,texCoord); }"

        fun validateScreenshotSize(width: Int, height: Int) {
            require(width > 0 && height > 0) { "Screenshot dimensions must be positive" }
            require(width.toLong() * height <= Int.MAX_VALUE / BYTES_PER_PIXEL) {
                "Screenshot is too large"
            }
        }

        fun textureParameters(target: Int) {
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun createProgram(): Int {
            val vertex = shader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            var fragment = 0
            var program = 0
            try {
                fragment = shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
                program = GLES20.glCreateProgram()
                GLES20.glAttachShader(program, vertex)
                GLES20.glAttachShader(program, fragment)
                GLES20.glLinkProgram(program)
                val status = IntArray(1)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
                check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
                return program
            } catch (error: Throwable) {
                GLES20.glDeleteProgram(program)
                throw error
            } finally {
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
            }
        }

        fun shader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            try {
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val status = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
                return shader
            } catch (error: Throwable) {
                GLES20.glDeleteShader(shader)
                throw error
            }
        }
    }
}

/** Converts GL's bottom-first RGBA rows to Android's top-first pixel storage. */
internal fun bitmapFromGlPixels(width: Int, height: Int, pixels: ByteBuffer): Bitmap {
    val colors = IntArray(width * height)
    for (y in 0 until height) {
        pixels.position((height - 1 - y) * width * 4)
        for (x in 0 until width) {
            val red = pixels.get().toInt() and 0xff
            val green = pixels.get().toInt() and 0xff
            val blue = pixels.get().toInt() and 0xff
            val alpha = pixels.get().toInt() and 0xff
            colors[y * width + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
}
