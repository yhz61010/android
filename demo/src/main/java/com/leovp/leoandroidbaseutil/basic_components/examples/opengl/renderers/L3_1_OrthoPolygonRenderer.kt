package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 正交投影 - 多边形
 */
class L3_1_OrthoPolygonRenderer(@Suppress("unused") private val ctx: Context) : L2_2_PolygonRenderer(ctx) {
    override fun getTagName(): String = "L3_1_OrthoPolygonRenderer"

    private companion object {
        /**
         * 顶点着色器：之后定义的每个都会传 1 次给顶点着色器
         */
        private const val VERTEX_SHADER = """
                // mat4：4×4 的矩阵
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                void main()
                {
                    // 矩阵与向量相乘得到最终的位置
                    gl_Position = u_Matrix * a_Position;
                    gl_PointSize = 0.0;
                }
        """
    }

    override val vertexShader: String
        get() = VERTEX_SHADER

    private var uMatrixLocation: Int = 0

    /**
     * 矩阵数组
     */
    private val mProjectionMatrix = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        super.onSurfaceCreated(unused, config)
        uMatrixLocation = getUniform("u_Matrix")
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)

        // 边长比(>=1)，非宽高比
        val aspectRatio: Float = if (width > height) width.toFloat() / height else height.toFloat() / width

        // 1. 矩阵数组
        // 2. 结果矩阵起始的偏移量
        // 3. left  ：x 的最小值
        // 4. right ：x 的最大值
        // 5. bottom：y 的最小值
        // 6. top   ：y 的最大值
        // 7. near  ：z 的最小值
        // 8. far   ：z 的最大值
        if (width > height) {
            // 横屏
            Matrix.orthoM(mProjectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)
        } else {
            // 竖屏 or 正方形
            Matrix.orthoM(mProjectionMatrix, 0, -1f, 1f, -aspectRatio, aspectRatio, -1f, 1f)
        }
        // 更新u_Matrix的值，即更新矩阵数组
        GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, mProjectionMatrix, 0)
    }
}