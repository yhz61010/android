package com.leovp.screencapture.screenrecord.base.strategies

import android.graphics.Bitmap
import android.media.MediaCodec
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLUtils
import android.view.Surface
import com.leovp.screencapture.screenrecord.base.TextureRenderer

/** Owns every EGL resource used by one screenshot recording session. */
internal class ScreenshotEglSession(private val width: Int, private val height: Int) {
    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var windowSurface: EGLSurface? = null
    private var encoderSurface: Surface? = null
    private var renderer: TextureRenderer? = null

    fun initialize(encoder: MediaCodec) {
        encoderSurface = encoder.createInputSurface()
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException(
                "eglDisplay == EGL14.EGL_NO_DISPLAY: " +
                    GLUtils.getEGLErrorString(EGL14.eglGetError())
            )
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException(
                "eglInitialize(): " + GLUtils.getEGLErrorString(EGL14.eglGetError())
            )
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_ALPHA_SIZE,
            8,
            EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID,
            1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        val configChosen = EGL14.eglChooseConfig(
            display,
            attribList,
            0,
            configs,
            0,
            configs.size,
            configCount,
            0
        )
        val chooseConfigError = EGL14.eglGetError()
        if (
            !configChosen ||
            chooseConfigError != EGL14.EGL_SUCCESS ||
            configCount[0] <= 0 ||
            configs[0] == null
        ) {
            throw RuntimeException(
                "eglChooseConfig(): success=$configChosen count=${configCount[0]} " +
                    "error=${GLUtils.getEGLErrorString(chooseConfigError)}"
            )
        }

        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            2,
            EGL14.EGL_NONE
        )
        context = EGL14.eglCreateContext(
            display,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            contextAttributes,
            0
        )
        var error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException(GLUtils.getEGLErrorString(error))
        }

        windowSurface = EGL14.eglCreateWindowSurface(
            display,
            configs[0],
            encoderSurface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException(GLUtils.getEGLErrorString(error))
        }
        if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)) {
            throw RuntimeException(
                "eglMakeCurrent(): " + GLUtils.getEGLErrorString(EGL14.eglGetError())
            )
        }
        renderer = TextureRenderer()
    }

    fun render(bitmap: Bitmap, mvp: FloatArray, presentationTimeNanos: Long) {
        checkNotNull(renderer) { "EGL session has not been initialized" }
            .draw(width, height, bitmap, mvp)
        EGLExt.eglPresentationTimeANDROID(display, windowSurface, presentationTimeNanos)
        EGL14.eglSwapBuffers(display, windowSurface)
    }

    fun release() {
        val ownedDisplay = display
        val ownedContext = context
        val ownedWindowSurface = windowSurface
        if (ownedDisplay != null && ownedDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                ownedDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (ownedWindowSurface != null && ownedWindowSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(ownedDisplay, ownedWindowSurface)
            }
            if (ownedContext != null && ownedContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(ownedDisplay, ownedContext)
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(ownedDisplay)
        }

        encoderSurface?.release()
        encoderSurface = null
        renderer = null
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        windowSurface = EGL14.EGL_NO_SURFACE
    }

    private companion object {
        // EGL_ANDROID_recordable. The public EGLExt field was added in API 26.
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
