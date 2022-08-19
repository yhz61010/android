package com.leovp.demo.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.demo.R
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMEN_POS_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.ProjectionMatrixHelper
import com.leovp.opengl_sdk.util.TextureHelper
import com.leovp.opengl_sdk.util.VerticesUtil
import com.leovp.opengl_sdk.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 纹理绘制
 */
class L6_1_TextureRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L6_1_TextureRenderer::class.java.simpleName

    companion object {
        private const val VERTEX_SHADER = """
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                // 纹理坐标：2 个分量，S 和 T 坐标
                attribute vec2 a_TexCoord;
                varying vec2 v_TexCoord;
                void main() {
                    v_TexCoord = a_TexCoord;
                    gl_Position = u_Matrix * a_Position;
                }
                """

        private const val FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 v_TexCoord;
                // sampler2D：二维纹理数据的数组
                uniform sampler2D u_TextureUnit;
                void main() {
                    gl_FragColor = texture2D(u_TextureUnit, v_TexCoord);
                }
                """

        /**
         * 顶点坐标与纹理坐标对应即可。
         * 通过修改顶点坐标，即可控制图像的大小。
         *
         * 顺序： ABCD
         * ```
         * B(-1,1)        C(1,1)
         *       ┌────────┐
         *       │    ↑   │
         *       │ ───┼──→│ center (0,0)
         *       │    │   │
         *       └────────┘
         * A(-1,-1)       D(1,-1)
         * ```
         */
        private val POINT_DATA = VerticesUtil.VERTICES_COORD_CW

        /**
         * 纹理坐标
         * 顶点坐标与纹理坐标对应即可。
         * 顺序： ABCD
         *
         * ```
         * B(0,0)────s──→C(1,0)
         *   │  ┌───────┐
         *   t  │texture│
         *   │  │       │
         *   ↓  └───────┘
         * A(0,1)        D(1,1)
         * ```
         */
        private val TEX_VERTEX = VerticesUtil.TEX_COORD_CW
    }

    private val vertexData: FloatBuffer = createFloatBuffer(POINT_DATA)

    private var uTextureUnitLocation: Int = 0
    private val texVertexBuffer: FloatBuffer = createFloatBuffer(TEX_VERTEX)

    /** 纹理数据 */
    private lateinit var textureBean: TextureHelper.TextureBean

    private lateinit var projectionMatrixHelper: ProjectionMatrixHelper

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // 设置刷新屏幕时候使用的颜色值,顺序是 RGBA，值的范围从 0~1。GLES20.glClear 调用时使用该颜色值。
        GLES20.glClearColor(210f / 255, 255f / 255, 209f / 255, 1f)
        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        val aPositionLocation = getAttrib("a_Position")
        projectionMatrixHelper = ProjectionMatrixHelper(programObjId, "u_Matrix")
        // 纹理坐标索引
        val aTexCoordLocation = getAttrib("a_TexCoord")
        uTextureUnitLocation = getUniform("u_TextureUnit")
        // 纹理数据
        textureBean = TextureHelper.loadTexture(ctx, R.drawable.beauty)

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

        // 加载纹理坐标
        GLES20.glVertexAttribPointer(
            aTexCoordLocation, TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, texVertexBuffer
        )
        GLES20.glEnableVertexAttribArray(aTexCoordLocation)

        // 开启纹理透明混合，这样才能绘制透明图片
        GLES20.glEnable(GL10.GL_BLEND)
        GLES20.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)
        projectionMatrixHelper.enable(width, height)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT)
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
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, POINT_DATA.size / TWO_DIMEN_POS_COMPONENT_COUNT)
    }
}
