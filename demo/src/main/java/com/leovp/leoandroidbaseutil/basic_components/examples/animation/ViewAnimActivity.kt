package com.leovp.leoandroidbaseutil.basic_components.examples.animation

import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityViewAnimBinding


class ViewAnimActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityViewAnimBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewAnimBinding.inflate(layoutInflater).apply { setContentView(root) }
        title = "View Animation"


        initView()
    }

    private fun initView() {
        binding.btnAlpha.setOnSingleClickListener {
            val loadAnimation: Animation = AnimationUtils.loadAnimation(this, R.anim.view_anim_alpha)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnRotate.setOnSingleClickListener {
            val loadAnimation = AnimationUtils.loadAnimation(this, R.anim.view_anim_rotate)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnScale.setOnSingleClickListener {
            val loadAnimation = AnimationUtils.loadAnimation(this, R.anim.view_anim_scale)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnTranslate.setOnSingleClickListener {
            val loadAnimation = AnimationUtils.loadAnimation(this, R.anim.view_anim_translate)
            binding.ivBeauty.startAnimation(loadAnimation)
        }
    }
}