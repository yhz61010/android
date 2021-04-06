package com.leovp.leoandroidbaseutil.basic_components.examples.animation

import android.os.Bundle
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityAnimationBinding

class AnimationActivity : BaseDemonstrationActivity() {

    private lateinit var binding: ActivityAnimationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnimationBinding.inflate(layoutInflater).apply { setContentView(root) }
    }
}