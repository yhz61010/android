package com.leovp.leoandroidbaseutil.basic_components.examples.animation

import android.os.Bundle
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityAnimationBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener

class AnimationActivity : BaseDemonstrationActivity() {

    private lateinit var binding: ActivityAnimationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnimationBinding.inflate(layoutInflater).apply { setContentView(root) }

        binding.btnDrawableAnim.setOnSingleClickListener { startActivity<DrawableAnimActivity>() }
        binding.btnViewAnim.setOnSingleClickListener { startActivity<ViewAnimActivity>() }
        binding.btnPropAnim.setOnSingleClickListener { startActivity<PropertyAnimActivity>() }
    }
}