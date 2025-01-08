package com.leovp.demo.basiccomponents.examples

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityViewStubBinding
import com.leovp.log.base.ITAG

class ViewStubActivity : BaseDemonstrationActivity<ActivityViewStubBinding>(R.layout.activity_view_stub) {
    override fun getTagName(): String = ITAG

    var stubImageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handler(Looper.getMainLooper()).postDelayed({
        //     toast("Custom toast in background")
        // }, 2000)
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityViewStubBinding {
        return ActivityViewStubBinding.inflate(layoutInflater)
    }

    fun onShowClick(@Suppress("UNUSED_PARAMETER") view: View) {
        // ViewStub.inflate() can be only called once
        binding.viewStub.runCatching { inflate() }.fold(
            onSuccess = {
                stubImageView =
                    it.findViewById<ImageView>(R.id.stub_img).also { iv ->
                        iv.setImageResource(R.drawable.beauty)
                    }
            },
            onFailure = {
                binding.viewStub.visibility = View.VISIBLE
            }
        )
    }

    fun onChangeClick(@Suppress("UNUSED_PARAMETER") view: View) {
        stubImageView?.setImageResource(R.mipmap.ic_launcher)
    }

    fun onHideClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.viewStub.visibility = View.INVISIBLE
    }
}
