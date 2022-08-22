package com.leovp.demo.basiccomponents.examples.opengl.renderers

import android.content.Context
import com.leovp.demo.R
import com.leovp.opengl.BaseRenderer
import com.leovp.opengl.filter.*
import com.leovp.opengl.util.TextureHelper
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 滤镜渲染
 */
class L8_1_FilterRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    override fun getTagName(): String = L8_1_FilterRenderer::class.java.simpleName

    private val filterList = ArrayList<BaseFilter>()
    private var drawIndex = 0
    private var isChanged = false
    private var currentFilter: BaseFilter

    private lateinit var textureBean: TextureHelper.TextureBean

    init {
        filterList.add(CrossFilter(ctx))
        filterList.add(ClonePartFilter(ctx))
        filterList.add(BaseFilter(ctx))
        filterList.add(CloneFullFilter(ctx))
        filterList.add(TranslateFilter(ctx))
        filterList.add(ScaleFilter(ctx))
        filterList.add(InverseFilter(ctx))
        filterList.add(GrayFilter(ctx))
        filterList.add(LightUpFilter(ctx))
        currentFilter = filterList[0]
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        currentFilter.onCreated()
        textureBean = TextureHelper.loadTexture(ctx, R.drawable.beauty)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)
        currentFilter.onSizeChanged(width, height)
        currentFilter.textureBean = textureBean
    }

    override fun onDrawFrame(gl: GL10) {
        if (isChanged) {
            currentFilter = filterList[drawIndex]

            filterList.forEach {
                if (it != currentFilter) {
                    it.onDestroy()
                }
            }

            currentFilter.onCreated()
            currentFilter.onSizeChanged(outputWidth, outputHeight)
            currentFilter.textureBean = textureBean
            isChanged = false
        }

        currentFilter.onDraw()
    }

    override fun onClick() {
        super.onClick()
        drawIndex++
        drawIndex = if (drawIndex >= filterList.size) 0 else drawIndex
        isChanged = true
    }
}
