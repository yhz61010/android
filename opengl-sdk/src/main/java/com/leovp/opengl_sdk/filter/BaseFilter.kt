package com.leovp.opengl_sdk.filter

import android.content.Context
import android.opengl.GLES20
import com.leovp.opengl_sdk.AbsBaseOpenGLES
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMEN_POS_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.ProjectionMatrixHelper
import com.leovp.opengl_sdk.util.TextureHelper
import com.leovp.opengl_sdk.util.VerticesUtil
import com.leovp.opengl_sdk.util.createFloatBuffer
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * 基础滤镜
 */
open class BaseFilter(val ctx: Context,
    private val vertexShader: String = VERTEX_SHADER,
    private val fragmentShader: String = FRAGMENT_SHADER) : AbsBaseOpenGLES() {

    override fun getTagName() = "BaseFilter"

    companion object {
        const val VERTEX_SHADER = """
                uniform mat4 u_Matrix;
                attribute vec4 a_Position;
                attribute vec2 a_TexCoord;
                varying vec2 v_TexCoord;
                void main() {
                    v_TexCoord = a_TexCoord;
                    gl_Position = u_Matrix * a_Position;
                }
                """
        const val FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 v_TexCoord;
                uniform sampler2D u_TextureUnit;
                void main() {
                    gl_FragColor = texture2D(u_TextureUnit, v_TexCoord);
                }
                """
    }

    private val vertexData: FloatBuffer = createFloatBuffer(VerticesUtil.VERTICES_COORD_CW)

    private var uTextureUnitLocation: Int = 0
    private val texVertexBuffer: FloatBuffer = createFloatBuffer(VerticesUtil.TEX_COORD_CW)

    /** 纹理数据 */
    var textureBean: TextureHelper.TextureBean? = null
    private lateinit var projectionMatrixHelper: ProjectionMatrixHelper

    open fun onCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        makeProgram(vertexShader, fragmentShader)

        val aPositionLocation = getAttrib("a_Position")
        projectionMatrixHelper = ProjectionMatrixHelper(programObjId, "u_Matrix")
        // 纹理坐标索引
        val aTexCoordLocation = getAttrib("a_TexCoord")
        uTextureUnitLocation = getUniform("u_TextureUnit")

        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMEN_POS_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, vertexData)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        // 加载纹理坐标
        GLES20.glVertexAttribPointer(aTexCoordLocation, TWO_DIMEN_TEX_VERTEX_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, texVertexBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoordLocation)

        // 开启纹理透明混合，这样才能绘制透明图片
        GLES20.glEnable(GL10.GL_BLEND)
        GLES20.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
    }

    open fun onSizeChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        projectionMatrixHelper.enable(width, height)
    }

    open fun onDraw() {
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT)
        // 纹理单元：在 OpenGL 中，纹理不是直接绘制到片段着色器上，而是通过纹理单元去保存纹理

        // 设置当前活动的纹理单元为纹理单元0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        // 将纹理 ID 绑定到当前活动的纹理单元上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureBean?.textureId ?: 0)

        // 将纹理单元传递片段着色器的 u_TextureUnit
        GLES20.glUniform1i(uTextureUnitLocation, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, VerticesUtil.VERTICES_COORD.size / TWO_DIMEN_POS_COMPONENT_COUNT)
    }

    fun onDestroy() {
        GLES20.glDeleteProgram(programObjId)
        programObjId = 0
    }
}