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

/**
 * Author: Michael Leo
 * Date: 2022/1/28 15:06
 */
abstract class State(private val view: View) {
    abstract fun state(): Type

    protected var internalIcon: Drawable? = null
        set(value) {
            field = value
            view.invalidate()
        }

    fun getIcon(): Drawable = internalIcon!!

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

    abstract fun setAttributes(context: Context, attrs: AttributeSet?, attr: TypedArray?, @ColorInt defColor: Int, defDrawable: Drawable?)

    companion object {
        val DEF_BG_COLOR = Color.parseColor("#018786")
        const val DEF_ICON_TINT = Color.WHITE
    }

    enum class Type(val value: Int) {
        STATE_IDLE(1),
        STATE_INDETERMINATE(2),
        STATE_DETERMINATE(3),
        STATE_FINISHED(4),
        STATE_ERROR(5),
        STATE_CANCEL(6);

        companion object {
            fun getState(value: Int) = values().first { it.value == value }
        }
    }
}