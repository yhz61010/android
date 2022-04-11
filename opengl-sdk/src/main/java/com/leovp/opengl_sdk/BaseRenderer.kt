package com.leovp.opengl_sdk

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import com.leovp.opengl_sdk.util.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * Author: Michael Leo
 * Date: 2022/4/2 14:19
 */
abstract class BaseRenderer : GLSurfaceView.Renderer {
    abstract fun getTagName(): String
    val tag: String by lazy { getTagName() }

    @Suppress("WeakerAccess")
    protected var programObjId: Int = 0

    @Suppress("WeakerAccess")
    protected var outputWidth: Int = 0

    @Suppress("WeakerAccess")
    protected var outputHeight: Int = 0

    protected var pointCoord: FloatBuffer = createFloatBuffer(VerticesUtil.VERTICES_COORD)
    protected var texVertices: FloatBuffer = createFloatBuffer(VerticesUtil.TEX_COORD)

    /**
     * The step of make program.
     *
     * 步骤1: 编译顶点着色器
     * 步骤2: 编译片段着色器
     * 步骤3: 将顶点着色器、片段着色器进行链接，组装成一个 OpenGL ES 程序
     * 步骤4: 通知 OpenGL ES 开始使用该程序
     *
     * @return OpenGL ES Program ID
     */
    @Suppress("unused")
    fun makeProgram(vertexShaderCode: String, fragmentShaderCode: String) {
        val vertexShaderId = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShaderId = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        makeProgram(vertexShaderId, fragmentShaderId)
    }

    /**
     * The step of make program.
     *
     * 步骤1: 编译顶点着色器
     * 步骤2: 编译片段着色器
     * 步骤3: 将顶点着色器、片段着色器进行链接，组装成一个 OpenGL ES 程序
     * 步骤4: 通知 OpenGL ES 开始使用该程序
     *
     * @return OpenGL ES Program ID
     */
    fun makeProgram(vertexShaderId: Int, fragmentShaderId: Int) {
        programObjId = linkProgram(vertexShaderId, fragmentShaderId)
        LogContext.log.i(tag, "makeProgram() programObjId=$programObjId", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        if (!validateProgram(programObjId)) throw RuntimeException("OpenGL ES: Make program exception.")

        GLES20.glUseProgram(programObjId)
        checkGlError("glUseProgram")
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        outputWidth = width
        outputHeight = height

        // Set the OpenGL viewport to fill the entire surface.
        GLES20.glViewport(0, 0, width, height)
    }

    protected fun getUniform(name: String): Int {
        if (programObjId < 1) throw IllegalArgumentException("Program ID=$programObjId is not valid. Make sure to call makeProgram() first.")
        return GLES20.glGetUniformLocation(programObjId, name)
    }

    protected fun getAttrib(name: String): Int {
        if (programObjId < 1) throw IllegalArgumentException("Program ID=$programObjId is not valid. Make sure to call makeProgram() first.")
        return GLES20.glGetAttribLocation(programObjId, name)
    }

    /**
     * 调整渲染纹理的缩放比例
     * @param width YUV 数据宽度
     * @param height YUV 数据高度
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

    companion object {
        // I420, YV12
        const val THREE_PLANAR = 3

        // NV12, NV21
        const val TWO_PLANAR = 2
    }

    @Synchronized
    protected fun readFramePixelBuffer(x: Int = 0, y: Int = 0, width: Int = outputWidth, height: Int = outputHeight): ByteBuffer {
        return ByteBuffer.allocate(width * height * 4).also {
            GLES20.glReadPixels(x, y, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, it)
        }
    }

    @Synchronized
    protected fun readFrameBitmap(width: Int = outputWidth, height: Int = outputHeight): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.copyPixelsFromBuffer(readFramePixelBuffer(width = width, height = height))
        }
    }

    open fun onClick() {}
}