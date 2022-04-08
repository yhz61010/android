package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.leoandroidbaseutil.R
import com.leovp.opengl_sdk.BaseRenderer
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_POSITION_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_TEX_VERTEX_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.ProjectionMatrixHelper
import com.leovp.opengl_sdk.util.TextureHelper
import com.leovp.opengl_sdk.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 纹理渲染 - 单个纹理单元
 */
class L6_2_1_TextureRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L6_2_1_TextureRenderer::class.java.simpleName

    companion object {
        private const val VERTEX_SHADER = """
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                // 纹理坐标：2个分量，S 和 T 坐标
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
        private val POINT_DATA_FIRE_L = floatArrayOf(
            -1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, -1.0f)

        private val POINT_DATA_BEAUTY = floatArrayOf(
            -0.5f, -0.5f,
            -0.5f, 0.5f,
            0.5f, 0.5f,
            0.5f, -0.5f)

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
        private val TEX_VERTEX = floatArrayOf(
            0f, 1f,
            0f, 0f,
            1f, 0f,
            1f, 1f)
    }

    private val vertexDataFireL: FloatBuffer = createFloatBuffer(POINT_DATA_FIRE_L)
    private val vertexDataBeauty: FloatBuffer = createFloatBuffer(POINT_DATA_BEAUTY)

    private var uTextureUnitLocation: Int = 0
    private val texVertexBuffer: FloatBuffer = createFloatBuffer(TEX_VERTEX)

    /** 纹理数据 */
    private lateinit var textureBeanFireL: TextureHelper.TextureBean
    private lateinit var textureBeanBeauty: TextureHelper.TextureBean

    private var aPositionLocation: Int = 0
    private lateinit var projectionMatrixHelper: ProjectionMatrixHelper

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(210f / 255, 255f / 255, 209f / 255, 1f)
        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        aPositionLocation = getAttrib("a_Position")
        projectionMatrixHelper = ProjectionMatrixHelper(programObjId, "u_Matrix")
        // 纹理坐标索引
        val aTexCoordLocation = getAttrib("a_TexCoord")
        uTextureUnitLocation = getUniform("u_TextureUnit")
        // 纹理数据
        textureBeanFireL = TextureHelper.loadTexture(ctx, R.drawable.img_fire_l)
        textureBeanBeauty = TextureHelper.loadTexture(ctx, R.drawable.beauty)

        // 加载纹理坐标
        GLES20.glVertexAttribPointer(aTexCoordLocation, TWO_DIMENSIONS_TEX_VERTEX_COMPONENT_COUNT, GLES20.GL_FLOAT, false, 0, texVertexBuffer)
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

        drawFireL()
        drawBeauty()
    }

    private fun drawFireL() {
        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMENSIONS_POSITION_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, vertexDataFireL)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        // 设置当前活动的纹理单元为纹理单元 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        // 将纹理 ID 绑定到当前活动的纹理单元上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureBeanFireL.textureId)
        // 将纹理单元传递片段着色器的 u_TextureUnit
        GLES20.glUniform1i(uTextureUnitLocation, 0)
        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // GL_TRIANGLE_FAN：以一个点为三角形公共顶点，组成一系列相邻的三角形。
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, POINT_DATA_FIRE_L.size / TWO_DIMENSIONS_POSITION_COMPONENT_COUNT)
    }

    private fun drawBeauty() {
        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMENSIONS_POSITION_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, vertexDataBeauty)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        // 绑定新的纹理 ID 到已激活的纹理单元上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureBeanBeauty.textureId)
        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // GL_TRIANGLES：每隔三个顶点构成一个三角形，为多个三角形组成。例如：ABC，DEF，GHI
        // GL_TRIANGLE_STRIP: 每相邻三个顶点组成一个三角形，为一系列相接三角形构成。例如：ABC、BCD、CDE、DEF
        // GL_TRIANGLE_FAN：以一个点为三角形公共顶点，组成一系列相邻的三角形。
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, POINT_DATA_BEAUTY.size / TWO_DIMENSIONS_POSITION_COMPONENT_COUNT)
    }
}
