package com.leovp.leoandroidbaseutil.basic_components.examples.animation

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.AccelerateInterpolator
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityPropertyAnimBinding
import kotlin.random.Random

/**
 * https://developer.android.com/guide/topics/graphics/prop-animation?hl=zh-cn
 */
class PropertyAnimActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityPropertyAnimBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPropertyAnimBinding.inflate(layoutInflater).apply { setContentView(root) }
        title = "Property Animation"

        binding.btnAlpha.setOnSingleClickListener {
            binding.ivBeauty.animate().alpha(Random.nextFloat()).duration = 1000
        }
        binding.btnScale.setOnSingleClickListener {
            val scaleValue = Random.nextInt(150, 250) * 1.0F / 100
            val scaleX = ObjectAnimator.ofFloat(binding.ivBeauty, "scaleX", 0f, scaleValue).apply {
                duration = 1000
                interpolator = AccelerateInterpolator() // E.g. Linear, Accelerate, Decelerate
            }
            val scaleY = ObjectAnimator.ofFloat(binding.ivBeauty, "scaleY", 0f, scaleValue).apply {
                duration = 1000
                interpolator = AccelerateInterpolator() // E.g. Linear, Accelerate, Decelerate
            }
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                start()
            }
        }
        binding.btnRotate.setOnSingleClickListener {
            binding.ivBeauty.animate().rotation(Random.nextInt(0, 360).toFloat()).duration = 1000
        }
        binding.btnTranslate.setOnSingleClickListener { }
    }
}