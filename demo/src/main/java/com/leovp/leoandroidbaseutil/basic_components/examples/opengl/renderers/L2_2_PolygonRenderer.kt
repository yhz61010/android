package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_POSITION_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * 基础图形绘制 - 多边形
 */
open class L2_2_PolygonRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L2_2_PolygonRenderer::class.java.simpleName

    private companion object {
        /** 顶点着色器：之后定义的每个都会传 1 次给顶点着色器 */
        private const val VERTEX_SHADER = """
                attribute vec4 a_Position;
                void main()
                {
                    gl_Position = a_Position;
                    gl_PointSize = 5.0;
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

        /** 多边形顶点与中心点的距离 */
        private const val RADIUS: Float = 0.5f

        /** 起始点的弧度 */
        private const val START_POINT_RADIAN: Float = (2 * Math.PI / 4).toFloat()
    }

    open val vertexShader: String
        get() = VERTEX_SHADER

    /**
     * 多边形的顶点数，即边数。不含中心点和闭合点。
     * 实际边数为该值 + 1。
     */
    private var polygonVertexCount = 2

    /** 绘制所需要的顶点数 */
    private lateinit var pointData: FloatArray

    /**
     * 顶点坐标数据缓冲区
     * 分配一个块 Native 内存，用于与 GL 通讯传递。(我们通常用的数据存在于 Dalvik 的内存中，1.无法访问硬件；2.会被垃圾回收)
     */
    private var vertexData: FloatBuffer? = null

    /** 顶点坐标在 OpenGL 程序中的索引 */
    private var aPositionLocation: Int = 0

    /** 颜色 uniform 在 OpenGL 程序中的索引 */
    private var uColorLocation: Int = 0

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)

        makeProgram(vertexShader, FRAGMENT_SHADER)

        uColorLocation = GLES20.glGetUniformLocation(programObjId, "u_Color")
        aPositionLocation = GLES20.glGetAttribLocation(programObjId, "a_Position")

        GLES20.glEnableVertexAttribArray(aPositionLocation)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        updateVertexData()

        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // Drawing sequence priority. The latter the higher.
        drawShape()
        drawLine()
        drawPoint()

        updatePolygonVertexCount()
    }

    private fun updateVertexData() {
        // 边数+中心点+闭合点；一个点包含 x、y 两个向量
        pointData = FloatArray((polygonVertexCount + 2) * 2)

        // 组成多边形的每个三角形的中心点角的弧度
        val radian: Float = (2 * Math.PI / polygonVertexCount).toFloat()
        // 中心点
        pointData[0] = 0f
        pointData[1] = 0f
        // 多边形的顶点数据
        for (i in 0 until polygonVertexCount) {
            pointData[2 * i + 2] = RADIUS * cos((radian * i + START_POINT_RADIAN))
            pointData[2 * i + 2 + 1] = RADIUS * sin((radian * i + START_POINT_RADIAN))
        }
        // 闭合点：与多边形的第一个顶点重叠
        pointData[polygonVertexCount * 2 + 2] = RADIUS * cos(START_POINT_RADIAN)
        pointData[polygonVertexCount * 2 + 3] = RADIUS * sin(START_POINT_RADIAN)

        vertexData = createFloatBuffer(pointData)

        // 关联顶点坐标属性和缓存数据
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMENSIONS_POSITION_COMPONENT_COUNT, GLES20.GL_FLOAT, false, 0, vertexData)
    }

    private fun drawPoint() {
        // 更新 u_Color 的值，即更新画笔颜色
        GLES20.glUniform4f(uColorLocation, 0.0f, 0.0f, 0.0f, 1.0f)
        // 使用数组绘制图形：1.绘制的图形类型；2.从顶点数组读取的起点；3.从顶点数组读取的顶点个数
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, polygonVertexCount + 2)
    }

    private fun drawLine() {
        // GL_LINE_LOOP：按顺序将所有的点连接起来，包括首位相连
        GLES20.glUniform4f(uColorLocation, 1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 1, polygonVertexCount)
    }

    private fun drawShape() {
        GLES20.glUniform4f(uColorLocation, 1.0f, 1.0f, 0.0f, 1.0f)
        // GL_TRIANGLE_FAN：第一个点和之后所有相邻的 2 个点构成一个三角形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, polygonVertexCount + 2)
    }

    /** 更新多边形的边数 */
    private fun updatePolygonVertexCount() {
        polygonVertexCount++
        polygonVertexCount = if (polygonVertexCount > 32) 3 else polygonVertexCount
    }
}