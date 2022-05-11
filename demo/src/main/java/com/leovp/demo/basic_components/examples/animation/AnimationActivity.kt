package com.leovp.demo.basic_components.examples.animation

import android.os.Bundle
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityAnimationBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.base.ITAG

class AnimationActivity : BaseDemonstrationActivity() {

    override fun getTagName(): String = ITAG

    private lateinit var binding: ActivityAnimationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnimationBinding.inflate(layoutInflater).apply { setContentView(root) }

        binding.btnDrawableAnim.setOnSingleClickListener { startActivity<DrawableAnimActivity>() }
        binding.btnViewAnim.setOnSingleClickListener { startActivity<ViewAnimActivity>() }
        binding.btnPropAnim.setOnSingleClickListener { startActivity<PropertyAnimActivity>() }
    }
}