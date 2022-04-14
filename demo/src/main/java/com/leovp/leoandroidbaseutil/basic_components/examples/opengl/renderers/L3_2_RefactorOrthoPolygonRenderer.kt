package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import com.leovp.opengl_sdk.util.ProjectionMatrixHelper
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 正交投影 - 多边形(封装版)
 *
 * 点击屏幕查看效果
 */
class L3_2_RefactorOrthoPolygonRenderer(@Suppress("unused") private val ctx: Context) : L2_2_PolygonRenderer(ctx) {
    override fun getTagName(): String = L3_2_RefactorOrthoPolygonRenderer::class.java.simpleName

    private companion object {
        /** 顶点着色器：之后定义的每个都会传 1 次给顶点着色器 */
        private const val VERTEX_SHADER = """
                // mat4：4×4 的矩阵
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                void main()
                {
                    // 矩阵与向量相乘得到最终的位置
                    gl_Position = u_Matrix * a_Position;
                    gl_PointSize = 10.0;
                }
        """
    }

    override val vertexShader: String
        get() = VERTEX_SHADER

    private lateinit var projectionMatrixHelper: ProjectionMatrixHelper

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(gl, config)
        projectionMatrixHelper = ProjectionMatrixHelper(programObjId, "u_Matrix")
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)
        projectionMatrixHelper.enable(width, height)
    }
}