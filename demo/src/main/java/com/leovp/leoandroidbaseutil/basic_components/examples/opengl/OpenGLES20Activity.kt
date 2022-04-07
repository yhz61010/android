package com.leovp.leoandroidbaseutil.basic_components.examples.opengl

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers.*
import com.leovp.leoandroidbaseutil.databinding.ActivityOpenGles20Binding
import java.io.Serializable

class OpenGLES20Activity : BaseDemonstrationActivity(), AdapterView.OnItemClickListener {
    private lateinit var binding: ActivityOpenGles20Binding

    private lateinit var simpleAdapter: ArrayAdapter<Item>

    class Item(val title: String, val clazz: Class<*>) : Serializable {
        override fun toString(): String {
            return title
        }
    }

    private val items = arrayOf(
        Item("L1_1_基础框架", L1_1_BasicSkeletonRenderer::class.java),
        Item("L1_2_点绘制", L1_2_PointRenderer::class.java),
        Item("L2_1_基础图形绘制 - 点，线，三角形", L2_1_BasicShapeRenderer::class.java),
        Item("L2_2_基础图形绘制 - 多边形", L2_2_PolygonRenderer::class.java),
        Item("L3_1_正交投影 - 多边形", L3_1_OrthoPolygonRenderer::class.java),
        Item("L3_2_正交投影 - 多边形 - 封装版", L3_2_RefactorOrthoPolygonRenderer::class.java),
        Item("L4_1_渐变色", L4_1_BasicGradientRenderer::class.java),
        Item("L4_2_渐变色 - 优化数据传递", L4_2_BetterGradientRenderer::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenGles20Binding.inflate(layoutInflater).apply { setContentView(root) }

        simpleAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, android.R.id.text1, items)
        binding.list.adapter = simpleAdapter
        binding.list.onItemClickListener = this
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        startActivity<OpenGLESPlaygroundActivity>({ intent -> intent.putExtra("item", items[position]) })
    }

    companion object {
        fun getRenderer(className: Class<*>, context: Context): GLSurfaceView.Renderer? {
            try {
                val constructor = className.getConstructor(Context::class.java)
                return constructor.newInstance(context) as GLSurfaceView.Renderer
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}