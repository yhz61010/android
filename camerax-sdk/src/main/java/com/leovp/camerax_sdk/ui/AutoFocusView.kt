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
        visibility = INVISIBLE
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AutoFocusView)
        focusImg = typedArray.getResourceId(R.styleable.AutoFocusView_focusingDrawable, NO_ID)
        focusSucceedImg = typedArray.getResourceId(R.styleable.AutoFocusView_focusSuccessDrawable, NO_ID)
        focusFailedImg = typedArray.getResourceId(R.styleable.AutoFocusView_focusFailDrawable, NO_ID)
        typedArray.recycle()
        if (focusImg == NO_ID || focusSucceedImg == NO_ID || focusFailedImg == NO_ID) {
            throw RuntimeException("Any focus images can not be null.")
        }
    }

    fun startFocus(x: Int, y: Int) {
        if (focusImg == NO_ID || focusSucceedImg == NO_ID || focusFailedImg == NO_ID) {
            throw RuntimeException("Any focus images can not be null.")
        }
        setImageResource(focusImg)
        post {
            visibility = VISIBLE
            val params = layoutParams as FrameLayout.LayoutParams
            params.leftMargin = x - width / 2
            params.topMargin = y - height / 2
            layoutParams = params
            startAnimation(focusAnimation)
        }
    }

    fun focusSuccess() {
        setImageResource(focusSucceedImg)
        postDelayed({ visibility = GONE }, 500)
    }

    fun focusFail() {
        setImageResource(focusFailedImg)
        postDelayed({ visibility = GONE }, 500)
    }

    companion object {
        private const val NO_ID = -1
    }
}