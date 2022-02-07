package com.leovp.circle_progressbar.state

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.leovp.circle_progressbar.R
import com.leovp.circle_progressbar.base.State

/**
 * Author: Michael Leo
 * Date: 2022/1/28 16:33
 */
class FinishState(view: View) : State(view) {
    override fun state(): Type = Type.STATE_FINISHED

    override fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?, @ColorInt defColor: Int, defDrawable: Drawable?) {
        if (attrs != null && attr != null) {
            val iconResId = attr.getResourceId(R.styleable.CircleProgressbar_finishIconDrawable, R.drawable.ic_default_finish)
            internalIcon = context.getDrawable(iconResId)!!
            iconTint = attr.getColor(R.styleable.CircleProgressbar_finishIconTintColor, DEF_ICON_TINT)
            width = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_finishIconWidth, getIcon().minimumWidth)
            height = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_finishIconHeight, getIcon().minimumHeight)

            val backgroundResId = attr.getResourceId(R.styleable.CircleProgressbar_finishBackgroundDrawable, -1)
            backgroundDrawable = if (backgroundResId != -1) context.getDrawable(backgroundResId) else defDrawable
            backgroundColor = attr.getColor(R.styleable.CircleProgressbar_finishBackgroundColor, defColor)
        } else {
            internalIcon = context.getDrawable(R.drawable.ic_default_finish)!!
            width = getIcon().minimumWidth
            height = getIcon().minimumHeight
        }
    }
}