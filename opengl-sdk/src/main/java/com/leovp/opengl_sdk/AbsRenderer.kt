package com.leovp.opengl_sdk

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import com.leovp.opengl_sdk.util.checkGlError
import com.leovp.opengl_sdk.util.compileShader
import com.leovp.opengl_sdk.util.createFloatBuffers
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * Author: Michael Leo
 * Date: 2022/4/2 14:19
 */
abstract class AbsRenderer : GLSurfaceView.Renderer {
    abstract fun getTagName(): String
    val tag: String by lazy { getTagName() }

    @Suppress("WeakerAccess")
    protected var programObjId: Int = 0

    @Suppress("WeakerAccess")
    protected var outputWidth: Int = 0

    @Suppress("WeakerAccess")
    protected var outputHeight: Int = 0

    protected var pointCoord: FloatBuffer = createFloatBuffers(VERTICES_COORD)
    protected var texVertices: FloatBuffer = createFloatBuffers(TEX_COORD)

    /**
     * The step of make program.
     *
     * 步骤1: 编译顶点着色器
     * 步骤2: 编译片段着色器
     * 步骤3: 将顶点着色器、片段着色器进行链接，组装成一个 OpenGL ES 程序
     * 步骤4: 通知 OpenGL ES 开始使用该程序
     */
    protected fun makeAndUseProgram(vertexShader: String, fragmentShader: String) {
        // 返回着色器对象：成功，非0
        val vertexShaderId: Int = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        // 返回着色器对象：成功，非0
        val fragmentShaderId: Int = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        makeAndUseProgram(vertexShaderId, fragmentShaderId)
    }

    @Suppress("WeakerAccess")
    protected fun makeAndUseProgram(vertexShaderId: Int, fragmentShaderId: Int) {
        LogContext.log.i(tag, "vertexShader=$vertexShaderId fragmentShader=$fragmentShaderId", outputType = ILog.OUTPUT_TYPE_SYSTEM)
        programObjId = com.leovp.opengl_sdk.util.makeProgram(vertexShaderId, fragmentShaderId)
        GLES20.glUseProgram(programObjId)
        checkGlError("glUseProgram")
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        outputWidth = width
        outputHeight = height
    }

    protected fun getUniform(name: String): Int {
        return GLES20.glGetUniformLocation(programObjId, name)
    }

    protected fun getAttrib(name: String): Int {
        return GLES20.glGetAttribLocation(programObjId, name)
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

        /**
         * 坐标占用的向量个数
         * 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)
         */
        const val POSITION_COMPONENT_COUNT = 2

        /**
         * 数据数组中每个顶点起始数据的间距：数组中每个顶点相关属性占的Byte值
         */
        const val STRIDE = POSITION_COMPONENT_COUNT * Float.SIZE_BYTES

        /**
         * OpenGL 的世界坐标系是 `[-1, -1, 1, 1]`
         *
         * 顺序：ABCD
         *
         * 与 Android 中的 Canvas 或者屏幕坐标体系不同，GL 的坐标起始位置在屏幕中心，
         * (0,0) 作为中心点，X 坐标从左到右，Y 坐标从下到上，在 `[-1,1]` 之间取值，再映射到屏幕上。
         *
         * ```
         * C(-1,1)        D(1,1)
         *       ┌────────┐
         *       │    ↑   │
         *       │ ───┼──→│ center (0,0)
         *       │    │   │
         *       └────────┘
         * A(-1,-1)       B(1,-1)
         * ```
         */
        val VERTICES_COORD = floatArrayOf(
            -1.0f, -1.0f, // lb
            1.0f, -1.0f,  // rb
            -1.0f, 1.0f,  // lt
            1.0f, 1.0f    // rt
        )

        /**
         * 纹理的坐标系。取值范围在 `[0,0]` 到 `[1,1]` 内。
         *
         * 顺序：ABCD
         *
         * 两个维度分别是 S、T，所以一般称为 ST 纹理坐标。而有些时候也叫UV坐标。
         * 纹理坐标方向性在 Android 上与我们平时熟悉的 Bitmap、canvas 等一致，都是顶点在左上角。
         *
         * ```
         * C(0,0)────s──→D(1,0)       C(0,1)        D(1,1)
         *   │  ┌───────┐               ↑  ┌───────┐
         *   t  │texture│               t  │texture│
         *   │  │       │               │  │       │
         *   ↓  └───────┘               │  └───────┘
         * A(0,1)        B(1,1)       A(0,0)────s──→B(1,0)
         * ```
         */
        val TEX_COORD = floatArrayOf(
            0.0f, 1.0f, // lb
            1.0f, 1.0f, // rb
            0.0f, 0.0f, // lt
            1.0f, 0.0f  // rt
        )
    }
}