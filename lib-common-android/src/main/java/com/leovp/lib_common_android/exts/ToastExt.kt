package com.leovp.lib_common_android.exts

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
import com.leovp.lib_common_android.R
import com.leovp.lib_common_android.utils.API

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */

/**
 * Whether the host project is working in `DEBUG` mode.
 *
 * You **MUST** initialize `buildConfigInDebug` in the very beginning when you start your app.
 * For example in your custom Application.
 */
var buildConfigInDebug: Boolean = false

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 *               On Android 11+(Android R+), this parameter will be ignored.
 * @param bgColor Background hex color value with prefix '#'. Example: "#ff0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
fun Context.toast(@StringRes resId: Int,
    longDuration: Boolean = false,
    origin: Boolean = false,
    debug: Boolean = false,
    bgColor: String? = null,
    error: Boolean = false) {
    toast(getString(resId), longDuration, origin, debug, bgColor, error)
}

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 *               On Android 11+(Android R+), this parameter will be ignored.
 * @param bgColor Background hex color value with prefix '#'. Example: "#ff0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
fun Context.toast(msg: String?,
    longDuration: Boolean = false,
    origin: Boolean = false,
    debug: Boolean = false,
    bgColor: String? = null,
    error: Boolean = false) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        showToast(this, msg, longDuration, origin, debug, bgColor, error)
    } else {
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post {
            showToast(this, msg, longDuration, origin, debug, bgColor, error)
        }
    }
}


// ==========

private const val NORMAL_BG_COLOR = "#e6212122"
private const val ERROR_BG_COLOR = "#e6e65432"

private var toast: Toast? = null

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 *               On Android 11+(Android R+), this parameter will be ignored.
 * @param bgColor Background hex color value with prefix '#'. Example: "#ff0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
@SuppressLint("InflateParams")
private fun showToast(ctx: Context?,
    msg: String?,
    longDuration: Boolean = false,
    origin: Boolean,
    debug: Boolean,
    bgColor: String?,
    error: Boolean) {
    if ((debug && !buildConfigInDebug) || ctx == null) {
        // Debug log only be shown in DEBUG flavor
        return
    }
    val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    val message: String? = if (debug) "DEBUG: $msg" else msg
    if (origin || API.ABOVE_R) {
        if (error || bgColor != null) {
            val errorMsgColor = bgColor ?: ERROR_BG_COLOR
            Toast.makeText(ctx, HtmlCompat.fromHtml("<font color='$errorMsgColor'>$message</font>",
                HtmlCompat.FROM_HTML_MODE_LEGACY), duration).show()
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
            val defaultDrawable: Drawable = ResourcesCompat.getDrawable(ctx.resources,
                R.drawable.toast_bg_normal, null)!!
            if (!error && bgColor == null) {
                v.background = defaultDrawable
            } else { // with error or with bgColor
                val customDrawableWrapper = DrawableCompat.wrap(defaultDrawable).mutate()
                v.background = customDrawableWrapper
                val defaultBgColor: String =
                        bgColor ?: if (error) ERROR_BG_COLOR else NORMAL_BG_COLOR
                DrawableCompat.setTint(customDrawableWrapper, Color.parseColor(defaultBgColor))
            }
        }

        toast = Toast(ctx).apply {
            @Suppress("DEPRECATION")
            this.view = view
            setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, ctx.resources.dp2px(64F))
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