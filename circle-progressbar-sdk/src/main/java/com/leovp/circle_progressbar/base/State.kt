package com.leovp.circle_progressbar.base

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.leovp.circle_progressbar.util.dp2px
import java.io.Serializable

/**
 * Author: Michael Leo
 * Date: 2022/1/28 15:06
 */
abstract class State(private val view: View) : Serializable {
    protected var _icon: Drawable? = null
        set(value) {
            field = value
            view.invalidate()
        }

    fun getIcon(): Drawable = _icon!!

    var width: Int = Resources.getSystem().dp2px(48f)
        set(value) {
            field = value
            view.invalidate()
        }

    var height: Int = Resources.getSystem().dp2px(48f)
        set(value) {
            field = value
            view.invalidate()
        }

    @ColorInt
    var iconTint: Int = DEF_ICON_TINT
        set(value) {
            field = value
            view.invalidate()
        }

    @ColorInt
    var backgroundColor: Int = DEF_BG_COLOR
        set(value) {
            field = value
            view.invalidate()
        }

    var backgroundDrawable: Drawable? = null
        set(value) {
            field = value
            view.invalidate()
        }

    abstract fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?)

    companion object {
        const val DEF_BG_COLOR = 0x4c000000
        const val DEF_ICON_TINT = Color.WHITE
    }
}