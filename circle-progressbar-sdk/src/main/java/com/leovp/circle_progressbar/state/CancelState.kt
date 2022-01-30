package com.leovp.circle_progressbar.state

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import com.leovp.circle_progressbar.R
import com.leovp.circle_progressbar.base.State

/**
 * Author: Michael Leo
 * Date: 2022/1/28 17:55
 */
class CancelState(view: View) : State(view) {
    override fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?) {
        if (attrs != null && attr != null) {
            val iconResId = attr.getResourceId(R.styleable.CircleProgressbar_cancelIconDrawable, R.drawable.ic_default_cancel)
            _icon = context.getDrawable(iconResId)!!
            width = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_cancelIconWidth, getIcon().minimumWidth)
            height = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_cancelIconHeight, getIcon().minimumHeight)

            val backgroundResId = attr.getResourceId(R.styleable.CircleProgressbar_cancelBackgroundDrawable, -1)
            if (backgroundResId != -1) backgroundDrawable = context.getDrawable(backgroundResId)
            backgroundColor = attr.getColor(R.styleable.CircleProgressbar_cancelBackgroundColor, DEF_BG_COLOR)
            iconTint = attr.getColor(R.styleable.CircleProgressbar_cancelIconTintColor, DEF_ICON_TINT)
        } else {
            _icon = context.getDrawable(R.drawable.ic_default_cancel)!!
            width = getIcon().minimumWidth
            height = getIcon().minimumHeight
        }
    }
}