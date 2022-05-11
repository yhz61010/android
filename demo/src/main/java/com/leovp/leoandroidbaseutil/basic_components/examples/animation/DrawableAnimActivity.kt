package com.leovp.leoandroidbaseutil.basic_components.examples.animation

import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityDrawableAnimBinding
import com.leovp.log_sdk.base.ITAG


class DrawableAnimActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDrawableAnimBinding.inflate(layoutInflater).apply { setContentView(root) }
        title = "Drawable Animation"

        // Load the ImageView that will host the animation and
        // set its background to our AnimationDrawable XML resource.
        binding.ivDrawableAnim.setBackgroundResource(R.drawable.drawable_anim_list)

        // Get the background, which has been compiled to an AnimationDrawable object.
        val frameAnimation: AnimationDrawable = binding.ivDrawableAnim.background as AnimationDrawable

        // Start the animation (looped playback by default).
        frameAnimation.start()

    }
}