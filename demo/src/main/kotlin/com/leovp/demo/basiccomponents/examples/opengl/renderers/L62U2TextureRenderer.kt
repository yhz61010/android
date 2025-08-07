package com.leovp.demo.basiccomponents.examples.opengl.renderers

import android.content.Context
import android.opengl.GLES20
import com.leovp.demo.R
import com.leovp.opengl.BaseRenderer
import com.leovp.opengl.util.GLConstants.TWO_DIMEN_POS_COMPONENT_COUNT
import com.leovp.opengl.util.GLConstants.TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT
import com.leovp.opengl.util.ProjectionMatrixHelper
import com.leovp.opengl.util.TextureHelper
import com.leovp.opengl.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 纹理渲染 - 多个纹理单元
 */
class L62U2TextureRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L62U2TextureRenderer::class.java.simpleName

    companion object {
        private const val VERTEX_SHADER = """
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;

                // 纹理坐标：2 个分量，S 和 T 坐标
                attribute vec2 a_TexCoord;
                varying vec2 v_TexCoord;

                attribute vec2 a_TexCoord2;
                varying vec2 v_TexCoord2;

                void main() {
                    v_TexCoord = a_TexCoord;
                    v_TexCoord2 = a_TexCoord2;
                    gl_Position = u_Matrix * a_Position;
                }
                """

        private const val FRAGMENT_SHADER = """
                precision mediump float;

                varying vec2 v_TexCoord;
                varying vec2 v_TexCoord2;

                // sampler2D：二维纹理数据的数组
                uniform sampler2D u_TextureUnit1;
                uniform sampler2D u_TextureUnit2;

                bool isOutRect(vec2 coord) {
                    return coord.x < 0.0 || coord.x > 1.0 || coord.y < 0.0 || coord.y > 1.0;
                }

                void main() {
                    vec4 texture1 = texture2D(u_TextureUnit1, v_TexCoord);
                    vec4 texture2 = texture2D(u_TextureUnit2, v_TexCoord2);
                    bool isOut2 = isOutRect(v_TexCoord2);

                    if (isOut2) {
                        // 贴纸范围外,绘制背景
                        gl_FragColor = texture1;
                    } else {
                        gl_FragColor = texture2;
                    }
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
        private val POINT_DATA_BEAUTY = floatArrayOf(
            -0.2f,
            -0.2f,
            -0.2f,
            0.2f,
            0.2f,
            0.2f,
            0.2f,
            -0.2f
        )

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
        private val TEX_COORD = floatArrayOf(
            0f,
            1f,
            0f,
            0f,
            1f,
            0f,
            1f,
            1f
        )
    }

    private val vertexBufferFireL: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            -1.0f,
            -1.0f,
            -1.0f,
            1.0f,
            1.0f,
            1.0f,
            1.0f,
            -1.0f
        )
    )
    private val textureBufferFireL: FloatBuffer = createFloatBuffer(TEX_COORD)
    private val textureBufferBeauty: FloatBuffer = createFloatBuffer(vertexToTextureBeauty(POINT_DATA_BEAUTY))
    private var textureLocationFireL: Int = 0
    private var textureLocationBeauty: Int = 0
    private var aPositionLocation: Int = 0

    private fun vertexToTextureBeauty(@Suppress("SameParameterValue") vertex: FloatArray): FloatArray = floatArrayOf(
        // -0.4f, 1.4f,
        // -0.4f, -0.4f,
        // 1.4f, -0.4f,
        // 1.4f, 1.4f
        -(vertex[2] + 1.0f) / 2.0f,
        2 - (vertex[3] + 1.0f) / 2.0f,
        -(vertex[0] + 1.0f) / 2.0f,
        -(vertex[1] + 1.0f) / 2.0f,
        2 - (vertex[6] + 1.0f) / 2.0f,
        -(vertex[7] + 1.0f) / 2.0f,
        2 - (vertex[4] + 1.0f) / 2.0f,
        2 - (vertex[5] + 1.0f) / 2.0f
    )

    /** 纹理数据 */
    private lateinit var textureBeanFireL: TextureHelper.TextureBean
    private lateinit var textureBeanBeauty: TextureHelper.TextureBean

    private lateinit var projectionMatrixHelper: ProjectionMatrixHelper

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(210f / 255, 216f / 255, 209f / 255, 1f)
        makeProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        projectionMatrixHelper = ProjectionMatrixHelper(programObjId, "u_Matrix")

        aPositionLocation = getAttrib("a_Position")

        val texCoordLocationFireL = getAttrib("a_TexCoord")
        val texCoordLocationBeauty = getAttrib("a_TexCoord2")
        textureLocationFireL = getUniform("u_TextureUnit1")
        textureLocationBeauty = getUniform("u_TextureUnit2")

        // 纹理数据
        textureBeanFireL = TextureHelper.loadTexture(ctx, R.drawable.img_fire_l)
        textureBeanBeauty = TextureHelper.loadTexture(ctx, R.drawable.beauty)

        // 加载纹理坐标
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(
            texCoordLocationFireL,
            TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT,
            GLES20.GL_FLOAT,
            false,
            0,
            textureBufferFireL
        )
        // 通知 GL 程序使用指定的纹理属性索引
        GLES20.glEnableVertexAttribArray(texCoordLocationFireL)

        GLES20.glVertexAttribPointer(
            texCoordLocationBeauty,
            TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT,
            GLES20.GL_FLOAT,
            false,
            0,
            textureBufferBeauty
        )
        // 通知 GL 程序使用指定的纹理属性索引
        GLES20.glEnableVertexAttribArray(texCoordLocationBeauty)

        // 关联顶点坐标属性和缓存数据
        // 1. 位置索引；
        // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
        // 3. 数据类型；
        // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
        // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
        // 6. 数据缓冲区
        GLES20.glVertexAttribPointer(
            aPositionLocation,
            TWO_DIMEN_POS_COMPONENT_COUNT,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBufferFireL
        )
        // 通知 GL 程序使用指定的顶点属性索引
        GLES20.glEnableVertexAttribArray(aPositionLocation)
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

        // 几何图形相关定义：http://wiki.jikexueyuan.com/project/opengl-es-guide/basic-geometry-definition.html
        // GL_TRIANGLES：每隔三个顶点构成一个三角形，为多个三角形组成。例如：ABC，DEF，GHI
        // GL_TRIANGLE_STRIP: 每相邻三个顶点组成一个三角形，为一系列相接三角形构成。例如：ABC、BCD、CDE、DEF
        // GL_TRIANGLE_FAN：第一个点和之后所有相邻的 2 个点构成一个三角形。
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, POINT_DATA_BEAUTY.size / TWO_DIMEN_POS_COMPONENT_COUNT)
    }

    private fun drawFireL() {
        // 设置当前活动的纹理单元为纹理单元 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        // 将纹理 ID 绑定到当前活动的纹理单元上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureBeanFireL.textureId)
        // 将纹理单元传递片段着色器的 u_TextureUnit1
        GLES20.glUniform1i(textureLocationFireL, 0)
    }

    private fun drawBeauty() {
        // 设置当前活动的纹理单元为纹理单元 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        // 将纹理 ID 绑定到当前活动的纹理单元上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureBeanBeauty.textureId)
        // 将纹理单元传递片段着色器的 u_TextureUnit2
        GLES20.glUniform1i(textureLocationBeauty, 1)
    }
}
