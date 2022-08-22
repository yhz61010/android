package com.leovp.demo.basiccomponents.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.opengl.BaseRenderer
import com.leovp.opengl.util.GLConstants.TWO_DIMEN_POS_COMPONENT_COUNT
import com.leovp.opengl.util.createFloatBuffer
import java.nio.FloatBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 动态改变顶点位置 & 颜色
 */
class L11_DynamicPointRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L11_DynamicPointRenderer::class.java.simpleName

    private companion object {
        /** 顶点着色器：之后定义的每个都会传 1 次给顶点着色器 */
        private const val VERTEX_SHADER = """
                // vec4：4 个分量的向量：x、y、z、w。从外部传递进来的每个顶点的颜色值
                attribute vec4 a_Position;
                void main()
                {
                    // gl_Position：GL中默认定义的输出变量，决定了当前顶点的最终位置
                    gl_Position = a_Position;
                    // gl_PointSize：GL中默认定义的输出变量，决定了当前顶点的大小
                    gl_PointSize = 80.0;
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
        private val POINT_DATA = floatArrayOf(0f, 0f)
    }

    private val random = Random()

    /**
     * 顶点坐标数据缓冲区
     *
     * 分配一个块 Native 内存，用于与 GL 通讯传递。(我们通常用的数据存在于 Dalvik 的内存中，1.无法访问硬件；2.会被垃圾回收)
     */
    private val vertexData: FloatBuffer = createFloatBuffer(POINT_DATA)
    private var uColorLocation: Int = 0

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        val aPositionLocation = getAttrib("a_Position")
        uColorLocation = getUniform("u_Color")

        // 关联顶点坐标属性和缓存数据
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(
            aPositionLocation, TWO_DIMEN_POS_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, vertexData
        )
        GLES20.glEnableVertexAttribArray(aPositionLocation)
    }

    override fun onDrawFrame(unused: GL10) {
        // 使用 glClearColor 设置的颜色，刷新 Surface
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 只要持有传递给 GL 层的 Buffer 引用，就可以动态改变相关的数据信息
        vertexData.put(
            floatArrayOf(
                0.9f * random.nextFloat() * (if (random.nextFloat() > 0.5f) 1 else -1).toFloat(),
                0.9f * random.nextFloat() * (if (random.nextFloat() > 0.5f) 1 else -1).toFloat()
            )
        )
        vertexData.position(0)

        GLES20.glUniform4f(uColorLocation, random.nextFloat(), random.nextFloat(), random.nextFloat(), 1.0f)

        // 使用数组绘制图形：1.绘制的图形类型；2.从顶点数组读取的起点；3.从顶点数组读取的顶点个数
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, POINT_DATA.size / TWO_DIMEN_POS_COMPONENT_COUNT)
    }
}
