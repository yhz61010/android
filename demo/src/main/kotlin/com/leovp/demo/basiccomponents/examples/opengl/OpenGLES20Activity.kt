package com.leovp.demo.basiccomponents.examples.opengl

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L11DynamicPointRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L12U1BallRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L1U1BasicSkeletonRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L1U2PointRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L2U1BasicShapeRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L2U2PolygonRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L3U1OrthoPolygonRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L3U2RefactorOrthoPolygonRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L4U1BasicGradientRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L4U2BetterGradientRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L5IndexRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L62U2TextureRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L6U1TextureRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L6U2Sub1TextureRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L6U2Sub3TextureRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L7U1FBORenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L7U2FBORenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L8U1FilterRenderer
import com.leovp.demo.databinding.ActivityOpenGles20Binding
import com.leovp.log.base.ITAG
import kotlinx.parcelize.Parcelize

class OpenGLES20Activity :
    BaseDemonstrationActivity<ActivityOpenGles20Binding>(R.layout.activity_open_gles20),
    AdapterView.OnItemClickListener {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityOpenGles20Binding =
        ActivityOpenGles20Binding.inflate(layoutInflater)

    private lateinit var simpleAdapter: ArrayAdapter<Item>

    @Parcelize
    data class Item(val title: String, val clazz: Class<*>) : Parcelable {
        override fun toString(): String = title
    }

    private val items = arrayOf(
        Item("L1_1_基础框架", L1U1BasicSkeletonRenderer::class.java),
        Item("L1_2_点绘制", L1U2PointRenderer::class.java),
        Item("L2_1_基础图形绘制 - 点，线，三角形", L2U1BasicShapeRenderer::class.java),
        Item("L2_2_基础图形绘制 - 多边形", L2U2PolygonRenderer::class.java),
        Item("L3_1_正交投影 - 多边形", L3U1OrthoPolygonRenderer::class.java),
        Item("L3_2_正交投影 - 多边形 - 封装版", L3U2RefactorOrthoPolygonRenderer::class.java),
        Item("L4_1_渐变色", L4U1BasicGradientRenderer::class.java),
        Item("L4_2_渐变色 - 优化数据传递", L4U2BetterGradientRenderer::class.java),
        Item("L5_索引绘制", L5IndexRenderer::class.java),
        Item("L6_1_纹理渲染", L6U1TextureRenderer::class.java),
        Item("L6_2_1_纹理渲染 - 单个纹理单元", L6U2Sub1TextureRenderer::class.java),
        Item("L6_2_2_纹理渲染 - 多个纹理单元", L62U2TextureRenderer::class.java),
        Item("L6_2_3_纹理渲染 - 蒙版遮罩", L6U2Sub3TextureRenderer::class.java),
        Item("L7_1_FrameBuffer 离屏渲染", L7U1FBORenderer::class.java),
        Item("L7_2_FrameBuffer 离屏渲染 - RenderBuffer", L7U2FBORenderer::class.java),
        Item("L8_1_滤镜渲染", L8U1FilterRenderer::class.java),
        Item("L11_动态改变顶点位置 & 颜色", L11DynamicPointRenderer::class.java),
        Item("L12_1_球体", L12U1BallRenderer::class.java)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        simpleAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, android.R.id.text1, items)
        binding.list.adapter = simpleAdapter
        binding.list.onItemClickListener = this
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        startActivity<OpenGLESPlaygroundActivity>({ intent -> intent.putExtra("item", items[position]) })
    }

    companion object {
        fun getRenderer(className: Class<*>, context: Context): GLSurfaceView.Renderer? = runCatching {
            val constructor = className.getConstructor(Context::class.java)
            constructor.newInstance(context) as GLSurfaceView.Renderer
        }.getOrNull()
    }
}
