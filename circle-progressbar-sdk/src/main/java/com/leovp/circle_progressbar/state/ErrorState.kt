package com.leovp.circle_progressbar.state

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import com.leovp.circle_progressbar.R
import com.leovp.circle_progressbar.base.State

/**
 * Author: Michael Leo
 * Date: 2022/1/28 17:51
 */
class ErrorState(view: View) : State(view) {
    override fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?) {
        if (attrs != null && attr != null) {
            val iconResId = attr.getResourceId(R.styleable.CircleProgressbar_errorIconDrawable, R.drawable.ic_default_error)
            _icon = context.getDrawable(iconResId)!!
            width = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_errorIconWidth, getIcon().minimumWidth)
            height = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_errorIconHeight, getIcon().minimumHeight)

            val backgroundResId = attr.getResourceId(R.styleable.CircleProgressbar_errorBackgroundDrawable, -1)
            if (backgroundResId != -1) backgroundDrawable = context.getDrawable(backgroundResId)
            backgroundColor = attr.getColor(R.styleable.CircleProgressbar_errorBackgroundColor, DEF_BG_COLOR)
            iconTint = attr.getColor(R.styleable.CircleProgressbar_errorIconTintColor, DEF_ICON_TINT)
        } else {
            _icon = context.getDrawable(R.drawable.ic_default_error)!!
            width = getIcon().minimumWidth
            height = getIcon().minimumHeight
        }
    }
}