package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.createFloatBuffers
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 基础图形绘制 - 点，线，三角形
 */
class L2_1_BasicShapeRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = "L2_1_BasicShapeRenderer"

    private companion object {
        /** 顶点着色器：之后定义的每个都会传 1 次给顶点着色器 */
        private const val VERTEX_SHADER = """
                attribute vec4 a_Position;
                void main()
                {
                    gl_Position = a_Position;
                    gl_PointSize = 30.0;
                }
        """

        /**
         * 片段着色器
         * uniform：可用于顶点和片段着色器，一般用于对于物体中所有顶点或者所有的片段都相同的量。比如光源位置、统一变换矩阵、颜色等。
         */
        private const val FRAGMENT_SHADER = """
                precision mediump float;
                uniform vec4 u_Color;
                void main()
                {
                    gl_FragColor = u_Color;
                }
        """

        /**
         * 顶点数据数组
         * 点的 x,y 坐标（x，y 各占 1 个分量，也就是说每个点占用 2 个分量）。
         * 该数组表示 4 个顶点数据，也就是 4 个点的坐标。
         */
        private val POINT_DATA = floatArrayOf(
            0f, .5f,
            -.5f, 0f,
            0f, -.5f,
            .5f, 0f
        )
    }

    /**
     * 顶点坐标数据缓冲区
     * 分配一个块 Native 内存，用于与 GL 通讯传递。(我们通常用的数据存在于 Dalvik 的内存中，1.无法访问硬件；2.会被垃圾回收)
     */
    private val vertexData: FloatBuffer = createFloatBuffers(POINT_DATA)

    /**
     * 颜色 uniform 在 OpenGL 程序中的索引
     */
    private var uColorLocation: Int = 0

    private var drawCount: Int = 0
    private val maxDrawCount = POINT_DATA.size / TWO_DIMENSIONS_POSITION_COMPONENT_COUNT

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)

        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        uColorLocation = GLES20.glGetUniformLocation(programObjId, "u_Color")
        val aPositionLocation = GLES20.glGetAttribLocation(programObjId, "a_Position")

        // 关联顶点坐标属性和缓存数据
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMENSIONS_POSITION_COMPONENT_COUNT, GLES20.GL_FLOAT, false, 0, vertexData)
        GLES20.glEnableVertexAttribArray(aPositionLocation)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)
        // Set the OpenGL viewport to fill the entire surface.
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(glUnused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawCount++

        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // Drawing sequence priority. The latter the higher.
        drawTriangle()
        drawLine()
        drawPoint()
        if (drawCount >= maxDrawCount) drawCount = 0
    }

    private fun drawPoint() {
        // 更新 u_Color 的值，即更新画笔颜色
        GLES20.glUniform4f(uColorLocation, 0.0f, 0.0f, 0.0f, 1.0f)
        // 使用数组绘制图形：1.绘制的图形类型；2.从顶点数组读取的起点；3.从顶点数组读取的顶点个数
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, drawCount)
    }

    private fun drawLine() {
        // GL_LINES：每 2 个点构成一条线段
        // GL_LINE_LOOP：按顺序将所有的点连接起来，包括首位相连
        // GL_LINE_STRIP：按顺序将所有的点连接起来，不包括首位相连
        GLES20.glUniform4f(uColorLocation, 1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, drawCount)
    }

    private fun drawTriangle() {
        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // GL_TRIANGLES：每 3 个点构成一个三角形
        // GL_TRIANGLE_STRIP: 相邻3个点构成一个三角形,不包括首位两个点。例如：ABC、BCD、CDE、DEF
        // GL_TRIANGLE_FAN：第一个点和之后所有相邻的 2 个点构成一个三角形
        GLES20.glUniform4f(uColorLocation, 1.0f, 1.0f, 0.0f, 1.0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, drawCount)
    }
}