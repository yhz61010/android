package com.leovp.circle_progressbar.state

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import com.leovp.circle_progressbar.R
import com.leovp.circle_progressbar.base.State

/**
 * Author: Michael Leo
 * Date: 2022/1/28 15:19
 */
class IdleState(view: View) : State(view) {
    override fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?) {
        if (attrs != null && attr != null) {
            val iconResId = attr.getResourceId(R.styleable.CircleProgressbar_idleIconDrawable, R.drawable.ic_default_idle)
            _icon = context.getDrawable(iconResId)!!
            width = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_idleIconWidth, getIcon().minimumWidth)
            height = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_idleIconHeight, getIcon().minimumHeight)

            val backgroundResId = attr.getResourceId(R.styleable.CircleProgressbar_idleBackgroundDrawable, -1)
            if (backgroundResId != -1) backgroundDrawable = context.getDrawable(backgroundResId)
            backgroundColor = attr.getColor(R.styleable.CircleProgressbar_idleBackgroundColor, DEF_BG_COLOR)
            iconTint = attr.getColor(R.styleable.CircleProgressbar_idleIconTintColor, DEF_ICON_TINT)
        } else {
            _icon = context.getDrawable(R.drawable.ic_default_idle)!!
            width = getIcon().minimumWidth
            height = getIcon().minimumHeight
        }
    }
}