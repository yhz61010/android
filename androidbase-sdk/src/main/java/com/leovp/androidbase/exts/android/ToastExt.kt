package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.text.HtmlCompat
import com.leovp.androidbase.R
import com.leovp.lib_common_android.utils.API

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */

/**
 * Whether the host project is working in `DEBUG` mode.
 *
 * You **MUST** initialize `buildConfigInDebug` in the very beginning when you start your app. For example in your custom Application.
 */
var buildConfigInDebug: Boolean = false

/**
 * @param normal On Android 11+(Android R+), this parameter will be ignored.
 * @param errorColor Hex color value with prefix '#'. Example: "#ff0000"
 */
fun Context.toast(@StringRes resId: Int, longDuration: Boolean = false, error: Boolean = false, debug: Boolean = false, normal: Boolean = false, errorColor: String? = null) {
    toast(getString(resId), longDuration, error, debug, normal, errorColor)
}

/**
 * @param normal On Android 11+(Android R+), this parameter will be ignored.
 * @param errorColor Hex color value with prefix '#'. Example: "#ff0000"
 */
fun Context.toast(msg: String?, longDuration: Boolean = false, error: Boolean = false, debug: Boolean = false, normal: Boolean = false, errorColor: String? = null) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        showToast(this, msg, longDuration, error, debug, normal, errorColor)
    } else {
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post { showToast(this, msg, longDuration, error, debug, normal, errorColor) }
    }
}


// ==========

private var toast: Toast? = null

@SuppressLint("InflateParams")
private fun showToast(ctx: Context?, msg: String?, longDuration: Boolean = false, error: Boolean, debug: Boolean, normal: Boolean, errorColor: String?) {
    if ((debug && !buildConfigInDebug) || ctx == null) {
        // Debug log only be shown in DEBUG flavor
        return
    }
    val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    val message: String? = if (debug) "DEBUG: $msg" else msg
    if (normal || API.ABOVE_R) {
        if (error) {
            val errorMsgColor = errorColor ?: "#e65432"
            Toast.makeText(ctx, HtmlCompat.fromHtml("<font color='$errorMsgColor'>$message</font>", HtmlCompat.FROM_HTML_MODE_LEGACY), duration).show()
        } else {
            Toast.makeText(ctx, message, duration).show()
        }
    } else {
        toast?.cancel()

        val view = LayoutInflater.from(ctx).inflate(R.layout.toast_tools_layout, null).also { v ->
            v.findViewById<TextView>(R.id.tv_text).let { tv ->
                tv.setTextColor(ContextCompat.getColor(tv.context, android.R.color.white))
                tv.text = message
            }
            if (error) {
                if (errorColor == null) {
                    v.setBackgroundResource(R.drawable.toast_bg_error)
                } else {
                    val errorDrawable: Drawable = ResourcesCompat.getDrawable(ctx.resources, R.drawable.toast_bg_error, null)!!
                    val errorDrawableWrapper = DrawableCompat.wrap(errorDrawable).mutate()
                    v.background = errorDrawableWrapper
                    DrawableCompat.setTint(errorDrawableWrapper, Color.parseColor(errorColor))
                }
            } else {
                v.setBackgroundResource(R.drawable.toast_bg_normal)
            }
        }

        toast = Toast(ctx).apply {
            @Suppress("DEPRECATION")
            this.view = view
            setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, ctx.resources.dp2px(50F))
            this.duration = duration
            show()
        }
    }
}

/**
 * This method also works in thread.
 */
fun cancelToast() {
    toast?.cancel()
}