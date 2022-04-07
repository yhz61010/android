package com.leovp.leoandroidbaseutil.basic_components.examples.opengl

import android.opengl.GLSurfaceView
import android.os.Bundle
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.opengl_sdk.BaseRenderer

class OpenGLESPlaygroundActivity : BaseDemonstrationActivity() {
    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_glesplayground)
        glSurfaceView = findViewById(R.id.glSurfaceView)

        val item: OpenGLES20Activity.Item = intent.getSerializableExtra("item") as OpenGLES20Activity.Item
        title = item.title

        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setEGLConfigChooser(false)
        val render: GLSurfaceView.Renderer = OpenGLES20Activity.getRenderer(item.clazz, this)!!
        glSurfaceView.setRenderer(render)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        glSurfaceView.setOnClickListener {
            glSurfaceView.requestRender()
            if (render is BaseRenderer) {
                render.onClick()
            }
        }
    }
}