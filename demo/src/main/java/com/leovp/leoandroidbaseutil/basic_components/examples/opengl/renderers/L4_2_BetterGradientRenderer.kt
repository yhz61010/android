package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.GLConstants.RGB_COLOR_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMEN_POS_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.ProjectionMatrixHelper
import com.leovp.opengl_sdk.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 渐变色 - 优化数据传递
 */
class L4_2_BetterGradientRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L4_2_BetterGradientRenderer::class.java.simpleName

    private companion object {
        /** 顶点着色器：之后定义的每个都会传 1 次给顶点着色器 */
        private const val VERTEX_SHADER = """
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                // a_Color：从外部传递进来的每个顶点的颜色值
                attribute vec4 a_Color;
                // v_Color：将每个顶点的颜色值传递给片段着色器
                varying vec4 v_Color;
                void main()
                {
                    v_Color = a_Color;
                    gl_Position = u_Matrix * a_Position;
                    gl_PointSize = 30.0;
                }
        """

        /**
         * 片段着色器
         */
        private const val FRAGMENT_SHADER = """
                precision mediump float;
                // v_Color：从顶点着色器传递过来的颜色值
                varying vec4 v_Color;
                void main()
                {
                    gl_FragColor = v_Color;
                }
        """

        /**
         * 顶点数据数组
         * 一个顶点有 5 个向量数据：x、y、r、g、b
         */
        private val POINT_DATA = floatArrayOf(
            -0.5f, -0.5f, 1f, 0.5f, 0.5f,
            0.5f, -0.5f, 1f, 0f, 1f,
            -0.5f, 0.5f, 0f, 1f, 1f,
            0.5f, 0.5f, 1f, 1f, 0f
        )

        /**
         * 数据数组中每个顶点起始数据的间距：数组中每个顶点相关属性占的 Byte 值
         */
        private val STRIDE = (TWO_DIMEN_POS_COMPONENT_COUNT + RGB_COLOR_COMPONENT_COUNT) * Float.SIZE_BYTES
    }

    /**
     * 顶点坐标数据缓冲区
     * 分配一个块 Native 内存，用于与 GL 通讯传递。(我们通常用的数据存在于 Dalvik 的内存中，1.无法访问硬件；2.会被垃圾回收)
     */
    private val vertexData: FloatBuffer = createFloatBuffer(POINT_DATA)

    private lateinit var projectionMatrixHelper: ProjectionMatrixHelper

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        val aPositionLocation = getAttrib("a_Position")
        val aColorLocation = getAttrib("a_Color")
        projectionMatrixHelper = ProjectionMatrixHelper(programObjId, "u_Matrix")

        // 关联顶点坐标属性和缓存数据
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMEN_POS_COMPONENT_COUNT, GLES20.GL_FLOAT, false, STRIDE, vertexData)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        // 将数组的初始读取位置右移 2 位，所以数组读取的顺序是 r1, g1, b1, x2, y2, r2, g2, b2...
        vertexData.position(TWO_DIMEN_POS_COMPONENT_COUNT)
        // RGB_COLOR_COMPONENT_COUNT：从数组中每次读取 3 个向量
        // STRIDE：每次读取间隔是 (2 个位置 + 3 个颜色值) * Float 占的 Byte 位
        GLES20.glVertexAttribPointer(aColorLocation, RGB_COLOR_COMPONENT_COUNT, GLES20.GL_FLOAT, false, STRIDE, vertexData)
        GLES20.glEnableVertexAttribArray(aColorLocation)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)
        projectionMatrixHelper.enable(width, height)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // GL_TRIANGLE_STRIP: 每相邻三个顶点组成一个三角形，为一系列相接三角形构成。例如：ABC、BCD、CDE、DEF
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, POINT_DATA.size / (TWO_DIMEN_POS_COMPONENT_COUNT + RGB_COLOR_COMPONENT_COUNT))
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, POINT_DATA.size / (TWO_DIMEN_POS_COMPONENT_COUNT + RGB_COLOR_COMPONENT_COUNT))
    }
}