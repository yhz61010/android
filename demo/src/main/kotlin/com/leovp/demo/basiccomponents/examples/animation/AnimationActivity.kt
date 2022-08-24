package com.leovp.demo.basiccomponents.examples.animation

import android.os.Bundle
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityAnimationBinding
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.log.base.ITAG

class AnimationActivity : BaseDemonstrationActivity<ActivityAnimationBinding>() {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAnimationBinding {
        return ActivityAnimationBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.btnDrawableAnim.setOnSingleClickListener { startActivity<DrawableAnimActivity>() }
        binding.btnViewAnim.setOnSingleClickListener { startActivity<ViewAnimActivity>() }
        binding.btnPropAnim.setOnSingleClickListener { startActivity<PropertyAnimActivity>() }
    }
}
