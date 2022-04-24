package com.leovp.camerax_sdk.ui

import android.content.Context
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.leovp.camerax_sdk.R

/**
 * 对焦动图显示
 */
class AutoFocusView(context: Context, attrs: AttributeSet? = null) : AppCompatImageView(context, attrs) {
    private var focusImg = NO_ID
    private var focusSucceedImg = NO_ID
    private var focusFailedImg = NO_ID
    private val focusAnimation: Animation = AnimationUtils.loadAnimation(context, R.anim.focusview_show)

    init {
        visibility = GONE
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AutoFocusView)
        focusImg = typedArray.getResourceId(R.styleable.AutoFocusView_focus_focusing_id, NO_ID)
        focusSucceedImg = typedArray.getResourceId(R.styleable.AutoFocusView_focus_success_id, NO_ID)
        focusFailedImg = typedArray.getResourceId(R.styleable.AutoFocusView_focus_fail_id, NO_ID)
        typedArray.recycle()
        if (focusImg == NO_ID || focusSucceedImg == NO_ID || focusFailedImg == NO_ID) {
            throw RuntimeException("Any focus images can not be null.")
        }
    }

    fun startFocus(x: Int, y: Int) {
        if (focusImg == NO_ID || focusSucceedImg == NO_ID || focusFailedImg == NO_ID) {
            throw RuntimeException("Any focus images can not be null.")
        }
        val params = layoutParams as FrameLayout.LayoutParams
        params.topMargin = y - measuredHeight / 2
        params.leftMargin = x - measuredWidth / 2
        layoutParams = params
        visibility = VISIBLE
        setImageResource(focusImg)
        startAnimation(focusAnimation)
    }

    fun focusSuccess() {
        setImageResource(focusSucceedImg)
        postDelayed({ visibility = GONE }, 600)
    }

    fun focusFail() {
        setImageResource(focusFailedImg)
        postDelayed({ visibility = GONE }, 600)
    }

    companion object {
        private const val NO_ID = -1
    }
}