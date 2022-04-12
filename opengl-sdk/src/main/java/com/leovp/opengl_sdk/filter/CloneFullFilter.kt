package com.leovp.opengl_sdk.filter

import android.content.Context
import android.opengl.GLES20

/**
 * 完全克隆滤镜
 */
class CloneFullFilter(context: Context) : BaseFilter(context, fragmentShader = FRAGMENT_SHADER) {
    override fun getTagName() = "CloneFullFilter"

    companion object {
        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform sampler2D u_TextureUnit;
            uniform float cloneCount;
            void main() {
                gl_FragColor = texture2D(u_TextureUnit, v_TexCoord * cloneCount);
            }
        """
    }

    override fun onCreated() {
        super.onCreated()
        GLES20.glUniform1f(getUniform("cloneCount"), 3.0f)
    }
}