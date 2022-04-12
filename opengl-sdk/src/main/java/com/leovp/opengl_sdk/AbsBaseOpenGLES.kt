package com.leovp.opengl_sdk

import android.opengl.GLES20
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import com.leovp.opengl_sdk.util.checkGlError
import com.leovp.opengl_sdk.util.compileShader
import com.leovp.opengl_sdk.util.linkProgram
import com.leovp.opengl_sdk.util.validateProgram

/**
 * Author: Michael Leo
 * Date: 2022/4/12 13:33
 */
abstract class AbsBaseOpenGLES {
    abstract fun getTagName(): String
    val tag: String by lazy { getTagName() }

    @Suppress("WeakerAccess")
    protected var programObjId: Int = 0

    @Suppress("WeakerAccess")
    protected var outputWidth: Int = 0

    @Suppress("WeakerAccess")
    protected var outputHeight: Int = 0

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

    protected fun getUniform(name: String): Int {
        if (programObjId < 1) throw IllegalArgumentException("Program ID=$programObjId is not valid. Make sure to call makeProgram() first.")
        return GLES20.glGetUniformLocation(programObjId, name)
    }

    protected fun getAttrib(name: String): Int {
        if (programObjId < 1) throw IllegalArgumentException("Program ID=$programObjId is not valid. Make sure to call makeProgram() first.")
        return GLES20.glGetAttribLocation(programObjId, name)
    }
}