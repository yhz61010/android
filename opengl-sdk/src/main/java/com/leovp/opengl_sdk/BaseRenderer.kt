package com.leovp.opengl_sdk

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.leovp.opengl_sdk.util.VerticesUtil
import java.nio.ByteBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * Author: Michael Leo
 * Date: 2022/4/2 14:19
 */
abstract class BaseRenderer : AbsBaseOpenGLES(), GLSurfaceView.Renderer {
    var rendererCallback: RendererCallback? = null

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        outputWidth = width
        outputHeight = height

        // Set the OpenGL viewport to fill the entire surface.
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * 调整渲染纹理的缩放比例
     * @param width 图像宽度
     * @param height 图像高度
     */
    protected fun createKeepRatioFloatArray(width: Int, height: Int, keepRatio: Boolean, screenWidth: Int, screenHeight: Int): FloatArray {
        val floatArray: FloatArray =
                if (!keepRatio) {
                    VerticesUtil.VERTICES_COORD
                } else {
                    if (screenWidth > 0 && screenHeight > 0) {
                        val screenRatio = screenHeight.toFloat() / screenWidth.toFloat()
                        val specificRatio = height.toFloat() / width.toFloat()
                        when {
                            screenRatio == specificRatio -> VerticesUtil.VERTICES_COORD
                            screenRatio < specificRatio  -> {
                                val widthScale = screenRatio / specificRatio
                                floatArrayOf(
                                    -widthScale, -1.0f,
                                    widthScale, -1.0f,
                                    -widthScale, 1.0f,
                                    widthScale, 1.0f
                                )
                            }
                            else                         -> {
                                val heightScale = specificRatio / screenRatio
                                floatArrayOf(
                                    -1.0f, -heightScale,
                                    1.0f, -heightScale,
                                    -1.0f, heightScale,
                                    1.0f, heightScale
                                )
                            }
                        }
                    } else {
                        VerticesUtil.VERTICES_COORD
                    }
                }
        return floatArray
    }

    // 0 -> I420 (YUV420P)  YYYYYYYY UUVV
    // 1 -> NV12 (YUV420SP) YYYYYYYY UVUV
    // 2 -> NV21 (YUV420SP) YYYYYYYY VUVU
    @Suppress("unused")
    enum class Yuv420Type(val value: Int) {
        I420(0),
        NV12(1),
        NV21(2);

        companion object {
            fun getType(value: Int) = values().first { it.value == value }
        }
    }

    /** 渲染完毕的回调 */
    interface RendererCallback {
        fun onRenderDone(buffer: ByteBuffer, width: Int, height: Int)
    }

    @Synchronized
    protected fun readFramePixelBuffer(x: Int = 0, y: Int = 0, width: Int = outputWidth, height: Int = outputHeight) {
        val buffer = ByteBuffer.allocate(width * height * Float.SIZE_BYTES).also {
            GLES20.glReadPixels(x, y, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, it)
        }
        rendererCallback?.onRenderDone(buffer, width, height)
    }

    @Synchronized
    protected fun readFrameBitmap(width: Int = outputWidth, height: Int = outputHeight): Bitmap {
        val buffer = ByteBuffer.allocate(width * height * 4)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    open fun onClick() {}

    companion object {
        // I420, YV12
        const val THREE_PLANAR = 3

        // NV12, NV21
        const val TWO_PLANAR = 2
    }
}