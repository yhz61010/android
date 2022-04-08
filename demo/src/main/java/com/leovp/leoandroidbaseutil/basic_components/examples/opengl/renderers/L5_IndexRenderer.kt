package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_POSITION_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.ProjectionMatrixHelper
import com.leovp.opengl_sdk.util.createFloatBuffer
import com.leovp.opengl_sdk.util.createShortBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 索引绘制
 *
 * 索引就是让不同的顶点按照规定的顺序来绘制，这样就不会导致各种三角形的错乱。
 */
class L5_IndexRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L5_IndexRenderer::class.java.simpleName

    private companion object {
        /** 顶点着色器：之后定义的每个都会传 1 次给顶点着色器 */
        private const val VERTEX_SHADER = """
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                void main()
                {
                    gl_Position = u_Matrix * a_Position;
                }
        """

        /**
         * 片段着色器
         * uniform：可用于顶点和片段着色器，一般用于对于物体中所有顶点或者所有的片段都相同的量。比如光源位置、统一变换矩阵、颜色等。
         */
        private const val FRAGMENT_SHADER = """
                // 定义所有浮点数据类型的默认精度；有 lowp、mediump、highp 三种，但只有部分硬件支持片段着色器使用 highp。(顶点着色器默认 highp)
                precision mediump float;
                uniform vec4 u_Color;
                void main()
                {
                    // gl_FragColor：GL 中默认定义的输出变量，决定了当前片段的最终颜色
                    gl_FragColor = u_Color;
                }
        """

        /**
         * 顶点数据数组
         * 点的 x,y 坐标（x，y 各占 1 个分量，也就是说每个点占用 2 个分量）。
         */
        private val POINT_DATA = floatArrayOf(
            -0.5f, -0.5f,
            0.5f, -0.5f,
            0.5f, 0.5f,
            -0.5f, 0.5f,
            0f, -1.0f,
            0f, 1.0f
        )

        /**
         * 数组绘制的索引：当前是绘制三角形，所以是 3 个元素构成一个绘制顺序
         */
        private val VERTEX_INDEX = shortArrayOf(
            0, 1, 2,
            0, 2, 3,
            0, 4, 1,
            3, 2, 5)
    }

    /**
     * 顶点坐标数据缓冲区
     *
     * 分配一个块 Native 内存，用于与 GL 通讯传递。(我们通常用的数据存在于 Dalvik 的内存中，1.无法访问硬件；2.会被垃圾回收)
     */
    private val vertexData: FloatBuffer = createFloatBuffer(POINT_DATA)

    /**
     * 顶点索引数据缓冲区：ShortBuff，占 2 位的Byte
     */
    private val vertexIndexBuffer: ShortBuffer = createShortBuffer(VERTEX_INDEX)

    private var uColorLocation: Int = 0
    private lateinit var projectionMatrixHelper: ProjectionMatrixHelper

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)

        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        uColorLocation = getUniform("u_Color")
        val aPositionLocation = getAttrib("a_Position")
        projectionMatrixHelper = ProjectionMatrixHelper(programObjId, "u_Matrix")

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
        projectionMatrixHelper.enable(width, height)
    }

    override fun onDrawFrame(unused: GL10) {
        // 使用 glClearColor 设置的颜色，刷新 Surface
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUniform4f(uColorLocation, 0.0f, 1.0f, 1.0f, 1.0f)

        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // 绘制相对复杂的图形时，若顶点有较多重复时，对比数据占用空间而言，glDrawElements 会比 glDrawArrays 小很多，也会更高效。
        // 因为在有重复顶点的情况下，glDrawArrays 方式需要的 3 个顶点位置是用 Float 型的，占 3*4 的 Byte 值，
        // 而 glDrawElements 需要 3 个 Short 型的，占 3*2 Byte值。
        // 1. 图形绘制方式
        // 2. 绘制的顶点数
        // 3. 索引的数据格式
        // 4. 索引的数据 Buffer
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, VERTEX_INDEX.size, GLES20.GL_UNSIGNED_SHORT, vertexIndexBuffer)
    }
}