package com.leovp.demo.basic_components.examples.animation

import android.os.Bundle
import android.view.animation.*
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityViewAnimBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.base.ITAG

class ViewAnimActivity : BaseDemonstrationActivity<ActivityViewAnimBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityViewAnimBinding {
        return ActivityViewAnimBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "View Animation"
        initView()
    }

    private fun initView() {
        binding.btnAlpha.setOnSingleClickListener {
            val loadAnimation: Animation =
                AnimationUtils.loadAnimation(this, R.anim.view_anim_alpha)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnAlpha2.setOnSingleClickListener {
            val alphaAnim = AlphaAnimation(0.1f, 1.0f).apply { duration = 1000 }
            binding.ivBeauty.startAnimation(alphaAnim)
        }

        // ----------------------

        binding.btnRotate.setOnSingleClickListener {
            val loadAnimation = AnimationUtils.loadAnimation(this, R.anim.view_anim_rotate)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnRotate2.setOnSingleClickListener {
            val rotateAnim = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply { duration = 1000 }
            binding.ivBeauty.startAnimation(rotateAnim)
        }

        // ----------------------

        binding.btnScale.setOnSingleClickListener {
            val loadAnimation = AnimationUtils.loadAnimation(this, R.anim.view_anim_scale)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnScale2.setOnSingleClickListener {
            val scaleAnim = ScaleAnimation(
                0f, 1f, 0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply { duration = 2000 }
            binding.ivBeauty.startAnimation(scaleAnim)
        }

        // ----------------------

        binding.btnTranslate.setOnSingleClickListener {
            val loadAnimation = AnimationUtils.loadAnimation(this, R.anim.view_anim_translate)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnTranslate2.setOnSingleClickListener {
            val transAnim = TranslateAnimation(10f, 100f, 10f, 100f).apply { duration = 1000 }
            binding.ivBeauty.startAnimation(transAnim)
        }

        // ----------------------

        binding.btnSequence.setOnSingleClickListener {
            val loadAnimation = AnimationUtils.loadAnimation(this, R.anim.view_anim_sequence)
            binding.ivBeauty.startAnimation(loadAnimation)
        }

        binding.btnSequence2.setOnSingleClickListener {
            val scaleAnim = ScaleAnimation(
                0f, 1f, 0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply { duration = 2000 }
            val alphaAnim = AlphaAnimation(1.0f, 0.1f).apply { duration = 1000 }
            binding.ivBeauty.startAnimation(scaleAnim)
            scaleAnim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                }

                override fun onAnimationEnd(animation: Animation) {
                    binding.ivBeauty.startAnimation(alphaAnim)
                }

                override fun onAnimationRepeat(animation: Animation) {
                }
            })
        }

        // ----------------------

        binding.btnBlink.setOnSingleClickListener {
            val blinkAnim = AlphaAnimation(0.1f, 1.0f).apply {
                duration = 100
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
            binding.ivBeauty.startAnimation(blinkAnim)
        }

        binding.btnShake.setOnSingleClickListener {
            val shakeAnim = TranslateAnimation(-50f, 50f, 0f, 0f).apply {
                duration = 50
                repeatCount = 5
                repeatMode = Animation.REVERSE
            }
            binding.ivBeauty.startAnimation(shakeAnim)
        }
    }
}
