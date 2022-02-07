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
 * Date: 2022/1/28 15:19
 */
class IdleState(view: View) : State(view) {
    override fun state(): Type = Type.STATE_IDLE

    override fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?, @ColorInt defColor: Int, defDrawable: Drawable?) {
        if (attrs != null && attr != null) {
            val iconResId = attr.getResourceId(R.styleable.CircleProgressbar_idleIconDrawable, R.drawable.ic_default_idle)
            internalIcon = context.getDrawable(iconResId)!!
            iconTint = attr.getColor(R.styleable.CircleProgressbar_idleIconTintColor, DEF_ICON_TINT)
            width = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_idleIconWidth, getIcon().minimumWidth)
            height = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_idleIconHeight, getIcon().minimumHeight)

            val backgroundResId = attr.getResourceId(R.styleable.CircleProgressbar_idleBackgroundDrawable, -1)
            backgroundDrawable = if (backgroundResId != -1) context.getDrawable(backgroundResId) else defDrawable
            backgroundColor = attr.getColor(R.styleable.CircleProgressbar_idleBackgroundColor, defColor)
        } else {
            internalIcon = context.getDrawable(R.drawable.ic_default_idle)!!
            width = getIcon().minimumWidth
            height = getIcon().minimumHeight
        }
    }
}