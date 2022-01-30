package com.leovp.circle_progressbar.state

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import com.leovp.circle_progressbar.R
import com.leovp.circle_progressbar.base.State

/**
 * Author: Michael Leo
 * Date: 2022/1/28 16:33
 */
class FinishState(view: View) : State(view) {
    override fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?) {
        if (attrs != null && attr != null) {
            val iconResId = attr.getResourceId(R.styleable.CircleProgressbar_finishIconDrawable, R.drawable.ic_default_finish)
            _icon = context.getDrawable(iconResId)!!
            width = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_finishIconWidth, getIcon().minimumWidth)
            height = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_finishIconHeight, getIcon().minimumHeight)

            val backgroundResId = attr.getResourceId(R.styleable.CircleProgressbar_finishBackgroundDrawable, -1)
            if (backgroundResId != -1) backgroundDrawable = context.getDrawable(backgroundResId)
            backgroundColor = attr.getColor(R.styleable.CircleProgressbar_finishBackgroundColor, DEF_BG_COLOR)
            iconTint = attr.getColor(R.styleable.CircleProgressbar_finishIconTintColor, DEF_ICON_TINT)
        } else {
            _icon = context.getDrawable(R.drawable.ic_default_finish)!!
            width = getIcon().minimumWidth
            height = getIcon().minimumHeight
        }
    }
}