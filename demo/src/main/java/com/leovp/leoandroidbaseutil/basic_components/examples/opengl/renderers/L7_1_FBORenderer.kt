package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.leovp.leoandroidbaseutil.R
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_POSITION_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_TEX_VERTEX_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.ProjectionMatrixHelper
import com.leovp.opengl_sdk.util.TextureHelper
import com.leovp.opengl_sdk.util.VerticesUtil
import com.leovp.opengl_sdk.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * FrameBuffer 离屏渲染
 *
 * 点击屏幕，显示图像
 */
class L7_1_FBORenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L7_1_FBORenderer::class.java.simpleName

    private companion object {
        /** 顶点着色器：之后定义的每个都会传 1 次给顶点着色器 */
        private const val VERTEX_SHADER = """
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                // 纹理坐标：2 个分量，S 和 T 坐标
                attribute vec2 a_TexCoord;
                varying vec2 v_TexCoord;
                void main()
                {
                    v_TexCoord = a_TexCoord;
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
                varying vec2 v_TexCoord;
                // sampler2D：二维纹理数据的数组
                uniform sampler2D u_TextureUnit;
                void main()
                {
                    vec4 pic = texture2D(u_TextureUnit, v_TexCoord);
                    float gray = (pic.r + pic.g + pic.b) / 3.;
                    // gl_FragColor：GL 中默认定义的输出变量，决定了当前片段的最终颜色
                    gl_FragColor = vec4(gray, gray, gray, pic.a);
                }
        """

        /** 顶点数据数组 */
        private val POINT_DATA = VerticesUtil.VERTICES_COORD_CW

        /** 纹理坐标 */
        private val TEX_VERTEX = VerticesUtil.TEX_COORD_CW

        private val projectionMatrix = ProjectionMatrixHelper.projectionMatrix
    }

    /**
     * 顶点坐标数据缓冲区
     *
     * 分配一个块 Native 内存，用于与 GL 通讯传递。(我们通常用的数据存在于 Dalvik 的内存中，1.无法访问硬件；2.会被垃圾回收)
     */
    private val vertexData: FloatBuffer = createFloatBuffer(POINT_DATA)
    private val texVertexBuffer: FloatBuffer = createFloatBuffer(TEX_VERTEX)

    private var uMatrixLocation: Int = 0
    private var uTextureUnitLocation: Int = 0

    /** 纹理数据 */
    private lateinit var textureBean: TextureHelper.TextureBean

    private val frameBuffer = IntArray(1)
    private val texture = IntArray(1)

    var isCurrentFrameRead = false

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        val aPositionLocation = getAttrib("a_Position")
        uMatrixLocation = getUniform("u_Matrix")

        val aTexCoordLocation = getAttrib("a_TexCoord")
        uTextureUnitLocation = getUniform("u_TextureUnit")

        // 关联顶点坐标属性和缓存数据
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMENSIONS_POSITION_COMPONENT_COUNT, GLES20.GL_FLOAT, false, 0, vertexData)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        // 加载纹理坐标
        GLES20.glVertexAttribPointer(aTexCoordLocation, TWO_DIMENSIONS_TEX_VERTEX_COMPONENT_COUNT, GLES20.GL_FLOAT, false, 0, texVertexBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoordLocation)

        // 纹理数据
        textureBean = TextureHelper.loadTexture(ctx, R.drawable.beauty)

        // 由于 Android 屏幕上绘制的起始点在左上角，而 GL 纹理坐标是在左下角，所以需要进行水平翻转，即 Y 轴翻转
        Matrix.scaleM(projectionMatrix, 0, 1f, -1f, 1f)

        // 开启纹理透明混合，这样才能绘制透明图片
        GLES20.glEnable(GL10.GL_BLEND)
        GLES20.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onDrawFrame(unused: GL10) {
        if (!isCurrentFrameRead) return

        // 使用 glClearColor 设置的颜色，刷新 Surface
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)

        // 1. 创建 FrameBuffer、纹理对象
        createEnv()
        // 2. 配置 FrameBuffer 相关的绘制存储信息，并且绑定到当前的绘制环境上
        bindFrameBufferInfo()
        // 3. 更新视图区域
        GLES20.glViewport(0, 0, textureBean.width, textureBean.height)
        // 4. 绘制图片
        drawTexture()
        // 5. 读取当前画面上的像素信息
        readFramePixelBuffer(0, 0, textureBean.width, textureBean.height)
        // 6. 解绑 FrameBuffer
        unbindFrameBufferInfo()
        // 7. 删除 FrameBuffer，纹理对象
        deleteEnv()
    }

    private fun createEnv() {
        // 1. 创建 FrameBuffer
        GLES20.glGenFramebuffers(1, frameBuffer, 0)

        // 2.1 生成纹理对象
        GLES20.glGenTextures(1, texture, 0)
        // 2.2 绑定纹理对象
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        // 2.3 设置纹理对象的相关信息：颜色模式，大小
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, textureBean.width, textureBean.height,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        // 2.4 纹理过滤参数设置
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        // 2.5 解绑当前纹理，避免后续无关的操作影响了纹理内容
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun bindFrameBufferInfo() {
        // 1. 绑定 FrameBuffer 到当前的绘制环境上
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])
        // 2. 将纹理对象挂载到 FrameBuffer 上，存储颜色信息
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texture[0], 0)
    }

    private fun drawTexture() {
        GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, projectionMatrix, 0)

        // 纹理单元：在 OpenGL 中，纹理不是直接绘制到片段着色器上，而是通过纹理单元去保存纹理

        // 设置当前活动的纹理单元为纹理单元 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        // 将纹理 ID 绑定到当前活动的纹理单元上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureBean.textureId)
        // 将纹理单元传递片段着色器的 u_TextureUnit
        GLES20.glUniform1i(uTextureUnitLocation, 0)

        // 几何图形相关定义：
        // http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // https://blog.csdn.net/xiajun07061225/article/details/7455283
        // GL_TRIANGLES：每隔三个顶点构成一个三角形，为多个三角形组成。例如：ABC，DEF，GHI
        // GL_TRIANGLE_STRIP: 每相邻三个顶点组成一个三角形，为一系列相接三角形构成。例如：ABC、CBD、CDE、EDF
        // GL_TRIANGLE_FAN：第一个点和之后所有相邻的 2 个点构成一个三角形。
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, POINT_DATA.size / TWO_DIMENSIONS_POSITION_COMPONENT_COUNT)
    }

    private fun unbindFrameBufferInfo() {
        // 解绑 FrameBuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun deleteEnv() {
        GLES20.glDeleteFramebuffers(1, frameBuffer, 0)
        GLES20.glDeleteTextures(1, texture, 0)
    }
}