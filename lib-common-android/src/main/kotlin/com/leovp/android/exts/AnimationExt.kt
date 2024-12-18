package com.leovp.android.exts

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.View

/**
 * Author: Michael Leo
 * Date: 2022/1/11 15:28
 */

fun flipHorizontal(oldView: View, newView: View, durationInMills: Long) {
    val animator1: ObjectAnimator = ObjectAnimator.ofFloat(oldView, "rotationY", 0F, 90F)
    val animator2: ObjectAnimator = ObjectAnimator.ofFloat(newView, "rotationY", -90F, 0F)
    // animator2.interpolator = OvershootInterpolator(2.0f)
    animator1.addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {
            oldView.visibility = View.GONE
            animator2.setDuration(durationInMills).start()
            newView.visibility = View.VISIBLE
        }

        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    })
    animator1.setDuration(durationInMills).start()
}
