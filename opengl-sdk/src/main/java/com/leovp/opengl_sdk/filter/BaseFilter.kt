package com.leovp.opengl_sdk.filter

import android.content.Context
import android.opengl.GLES20
import com.leovp.opengl_sdk.AbsBaseOpenGLES
import com.leovp.opengl_sdk.R
import com.leovp.opengl_sdk.util.*
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_POSITION_COMPONENT_COUNT
import com.leovp.opengl_sdk.util.GLConstants.TWO_DIMENSIONS_TEX_VERTEX_COMPONENT_COUNT
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * 基础滤镜
 */
open class BaseFilter(val ctx: Context,
    private val vertexShader: String = ctx.readAssetsFileAsString(R.raw.base_vertex_shader),
    private val fragmentShader: String = ctx.readAssetsFileAsString(R.raw.base_fragment_shader)) : AbsBaseOpenGLES() {

    override fun getTagName() = "BaseFilter"

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

        GLES20.glVertexAttribPointer(aPositionLocation, TWO_DIMENSIONS_POSITION_COMPONENT_COUNT,
            GLES20.GL_FLOAT, false, 0, vertexData)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        // 加载纹理坐标
        GLES20.glVertexAttribPointer(aTexCoordLocation, TWO_DIMENSIONS_TEX_VERTEX_COMPONENT_COUNT,
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

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, VerticesUtil.VERTICES_COORD.size / TWO_DIMENSIONS_POSITION_COMPONENT_COUNT)
    }

    fun onDestroy() {
        GLES20.glDeleteProgram(programObjId)
        programObjId = 0
    }
}