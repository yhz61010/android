package com.leovp.opengl_sdk.filter

import android.content.Context
import android.opengl.GLES20
import kotlin.math.abs
import kotlin.math.sin

/**
 * 发光滤镜
 */
class LightUpFilter(context: Context) : BaseFilter(context, fragmentShader = FRAGMENT_SHADER) {
    override fun getTagName() = "LightUpFilter"

    companion object {
        const val FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 v_TexCoord;
                uniform sampler2D u_TextureUnit;
                uniform float intensity;
                void main() {
                    vec4 src = texture2D(u_TextureUnit, v_TexCoord);
                    vec4 addColor = vec4(intensity, intensity, intensity, 1.0);
                    gl_FragColor = src + addColor;
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
        val intensity = abs(sin((System.currentTimeMillis() - startTime) / 1000.0)) / 4.0
        GLES20.glUniform1f(intensityLocation, intensity.toFloat())
    }
}