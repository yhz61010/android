package com.leovp.opengl_sdk.filter

import android.content.Context
import android.opengl.GLES20
import kotlin.math.abs
import kotlin.math.sin

/**
 * 缩放滤镜
 */
class ScaleFilter(context: Context) : BaseFilter(context, fragmentShader = FRAGMENT_SHADER) {
    override fun getTagName() = "ScaleFilter"

    companion object {
        const val FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 v_TexCoord;
                uniform sampler2D u_TextureUnit;
                uniform float intensity;

                vec2 scale(vec2 srcCoord, float x, float y) {
                    return vec2((srcCoord.x - 0.5) / x + 0.5, (srcCoord.y - 0.5) / y + 0.5);
                }

                void main() {
                    vec2 offsetTexCoord = scale(v_TexCoord, intensity, intensity);
                    if (offsetTexCoord.x >= 0.0 && offsetTexCoord.x <= 1.0 &&
                        offsetTexCoord.y >= 0.0 && offsetTexCoord.y <= 1.0) {
                        gl_FragColor = texture2D(u_TextureUnit, offsetTexCoord);
                    }
                }
                """
    }

    private var intensityLocation: Int = 0
    private var startTime: Long = 0

    override fun onCreated() {
        super.onCreated()
        startTime = System.currentTimeMillis()
        intensityLocation = getUniform("intensity")
    }

    override fun onDraw() {
        super.onDraw()
        val intensity = abs(sin((System.currentTimeMillis() - startTime) / 1000.0)) + 0.5
        GLES20.glUniform1f(intensityLocation, intensity.toFloat())
    }
}