package com.leovp.demo.basiccomponents.examples.animation

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.AccelerateInterpolator
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityPropertyAnimBinding
import com.leovp.log.base.ITAG
import kotlin.random.Random

/**
 * https://developer.android.com/guide/topics/graphics/prop-animation?hl=zh-cn
 * https://juejin.cn/post/6846687601118691341#heading-6
 *
 * LinearInterpolator 线性（匀速）
 * AccelerateInterpolator 持续加速
 * DecelerateInterpolator 持续减速
 * AccelerateDecelerateInterpolator 先加速后减速
 * OvershootInterpolator 结束时回弹一下
 * AnticipateInterpolator 开始回拉一下
 * BounceInterpolator 结束时Q弹一下
 * CycleInterpolator 来回循环
 */
class PropertyAnimActivity : BaseDemonstrationActivity<ActivityPropertyAnimBinding>(R.layout.activity_property_anim) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityPropertyAnimBinding {
        return ActivityPropertyAnimBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Property Animation"

        binding.btnAlpha.setOnSingleClickListener {
            //            binding.ivBeauty.animate().alpha(Random.nextFloat()).duration = 1000

            ObjectAnimator.ofFloat(binding.ivBeauty, "alpha", 1.0f, Random.nextFloat(), 1.0f)
                .apply {
                    duration = 2000
                    start()
                }
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
        binding.btnTranslate.setOnSingleClickListener {
            ObjectAnimator.ofFloat(binding.ivBeauty, "translationX", 0f, 20f, -20f).apply {
                duration = 1000
                start()
            }
        }
    }
}
