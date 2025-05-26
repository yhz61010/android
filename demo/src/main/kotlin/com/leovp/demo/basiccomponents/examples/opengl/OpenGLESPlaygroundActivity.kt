package com.leovp.demo.basiccomponents.examples.opengl

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import com.leovp.android.exts.createFile
import com.leovp.android.exts.getParcelableExtraOrNull
import com.leovp.android.exts.toast
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.opengl.renderers.L8U1FilterRenderer
import com.leovp.demo.basiccomponents.examples.opengl.renderers.baseL7.L7BaseRenderer
import com.leovp.demo.databinding.ActivityOpenGlesplaygroundBinding
import com.leovp.image.writeToFile
import com.leovp.log.base.ITAG
import com.leovp.opengl.BaseRenderer
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class OpenGLESPlaygroundActivity :
    BaseDemonstrationActivity<ActivityOpenGlesplaygroundBinding>(R.layout.activity_open_glesplayground) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityOpenGlesplaygroundBinding {
        return ActivityOpenGlesplaygroundBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val item: OpenGLES20Activity.Item = intent.getParcelableExtraOrNull("item")!!
        title = item.title

        binding.glSurfaceView.setEGLContextClientVersion(2)
        binding.glSurfaceView.setEGLConfigChooser(false)
        val renderer: GLSurfaceView.Renderer = OpenGLES20Activity.getRenderer(item.clazz, this)!!
        binding.glSurfaceView.setRenderer(renderer)
        binding.glSurfaceView.renderMode = when (renderer) {
            is L8U1FilterRenderer -> GLSurfaceView.RENDERMODE_CONTINUOUSLY
            else -> GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        when (renderer) {
            is L7BaseRenderer -> readCurrentFrame(renderer)
        }

        binding.glSurfaceView.setOnClickListener {
            binding.glSurfaceView.requestRender()
            if (renderer is BaseRenderer) {
                renderer.onClick()
            }
        }
    }

    private fun readCurrentFrame(renderer: L7BaseRenderer) {
        val imageView = ImageView(this)
        val params =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        binding.rootView.addView(imageView, params)

        renderer.rendererCallback = object : BaseRenderer.RendererCallback {
            override fun onRenderDone(buffer: ByteBuffer, width: Int, height: Int) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                buffer.clear()
                thread {
                    val outBmpFile = createFile("FBO.png")
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
