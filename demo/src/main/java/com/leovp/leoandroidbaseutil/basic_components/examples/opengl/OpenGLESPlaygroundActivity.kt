package com.leovp.leoandroidbaseutil.basic_components.examples.opengl

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import com.leovp.androidbase.exts.android.createTmpFile
import com.leovp.androidbase.exts.android.toast
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers.L7_1_FBORenderer
import com.leovp.leoandroidbaseutil.databinding.ActivityOpenGlesplaygroundBinding
import com.leovp.lib_image.writeToFile
import com.leovp.opengl_sdk.BaseRenderer
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class OpenGLESPlaygroundActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityOpenGlesplaygroundBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenGlesplaygroundBinding.inflate(layoutInflater).apply { setContentView(root) }

        val item: OpenGLES20Activity.Item = intent.getSerializableExtra("item") as OpenGLES20Activity.Item
        title = item.title

        binding.glSurfaceView.setEGLContextClientVersion(2)
        binding.glSurfaceView.setEGLConfigChooser(false)
        val renderer: GLSurfaceView.Renderer = OpenGLES20Activity.getRenderer(item.clazz, this)!!
        binding.glSurfaceView.setRenderer(renderer)
        binding.glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        binding.glSurfaceView.setOnClickListener {
            binding.glSurfaceView.requestRender()
            if (renderer is BaseRenderer) {
                renderer.onClick()
            }
        }

        when (renderer) {
            is L7_1_FBORenderer -> readCurrentFrame(renderer)
        }
    }

    private fun readCurrentFrame(renderer: L7_1_FBORenderer) {
        val imageView = ImageView(this)
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        binding.rootView.addView(imageView, params)

        renderer.rendererCallback = object : BaseRenderer.RendererCallback {
            override fun onRenderDone(buffer: ByteBuffer, width: Int, height: Int) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                buffer.clear()
                thread {
                    val outBmpFile = createTmpFile(".png")
                    bitmap.writeToFile(outBmpFile, imgType = Bitmap.CompressFormat.PNG)
                    toast("The generated bitmap has been written to ${outBmpFile.absolutePath}")

                    runOnUiThread { imageView.setImageBitmap(bitmap) }
                }
            }
        }

        imageView.setOnClickListener {
            renderer.isCurrentFrameRead = true
            binding.glSurfaceView.requestRender()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.glSurfaceView.onPause()
    }
}