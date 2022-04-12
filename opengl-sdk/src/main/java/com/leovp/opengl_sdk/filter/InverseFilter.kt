package com.leovp.opengl_sdk.filter

import android.content.Context

/**
 * 反色滤镜
 */
class InverseFilter(context: Context) : BaseFilter(context, fragmentShader = FRAGMENT_SHADER) {
    override fun getTagName() = "InverseFilter"

    companion object {
        const val FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 v_TexCoord;
                uniform sampler2D u_TextureUnit;
                void main() {
                    vec4 src = texture2D(u_TextureUnit, v_TexCoord);
                    gl_FragColor = vec4(1.0 - src.r, 1.0 - src.g, 1.0 - src.b, 1.0);
                }
                """
    }
}