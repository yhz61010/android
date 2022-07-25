package com.leovp.lib_common_android.exts

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.leovp.floatview_sdk.FloatView
import com.leovp.lib_common_android.R
import com.leovp.lib_common_android.ui.ForegroundComponent
import com.leovp.lib_common_android.utils.API


/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */

/**
 * Whether the host project is working in `DEBUG` mode.
 *
 * You **MUST** initialize `buildConfigInDebug` in the very beginning when you start your app.
 * Otherwise, the `debug` feature doesn't work.
 * For example in your custom Application.
 */
var buildConfigInDebug: Boolean = false

/**
 * This method must be called for Android R(Android 11) or above.
 * Otherwise, the custom toast doesn't work when app in background.
 */
@RequiresApi(API.R)
fun initForegroundComponentForToast(app: Application, delay: Long = 500) {
    ForegroundComponent.init(app, delay)
}

@DrawableRes
var toastIcon: Int? = null

/**
 * Unit: px
 */
var toastIconSize: Int = 20.px

private val mainHandler = Handler(Looper.getMainLooper())

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
        mainHandler.post {
            showToast(this, msg, longDuration, origin, debug, bgColor, error)
        }
    }
}


// ==========

private const val NORMAL_BG_COLOR = "#646464"
private const val ERROR_BG_COLOR = "#F16C4C"

private var toast: Toast? = null
private const val FLOAT_VIEW_TAG = "leo-enhanced-custom-toast"

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
    val message: String = if (debug) "DEBUG: $msg" else (msg ?: "null")
    when {
        origin      -> Toast.makeText(ctx, message, duration).show()
        API.ABOVE_R -> {
            try {
                if (ForegroundComponent.get().isBackground && !ctx.canDrawOverlays) {
                    Toast.makeText(ctx, message, duration).show()
                    return
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, message, duration).show()
                return
            }
            //        if (error || bgColor != null) {
            //            val errorMsgColor = bgColor ?: ERROR_BG_COLOR
            //            Toast.makeText(ctx, HtmlCompat.fromHtml("<font color='$errorMsgColor'>$message</font>",
            //                HtmlCompat.FROM_HTML_MODE_LEGACY), duration).show()
            //        } else {
            //            Toast.makeText(ctx, message, duration).show()
            //        }
            mainHandler.removeCallbacksAndMessages(null)
            FloatView.with(FLOAT_VIEW_TAG).remove()
            FloatView.with(ctx)
                .layout(R.layout.toast_tools_layout) { v ->
                    decorateToast(ctx, v, message, bgColor, error)
                }
                .meta { viewWidth, _ ->
                    tag = FLOAT_VIEW_TAG
                    enableAlphaAnimation = true
                    enableDrag = false
                    systemWindow = ctx.canDrawOverlays
                    x = (ctx.screenWidth - viewWidth) / 2
                    y = ctx.screenRealHeight - 108.px
                }
                .show()
            mainHandler.postDelayed({ FloatView.with(FLOAT_VIEW_TAG).remove() },
                if (longDuration) 3500 else 2000)
        }
        else        -> {
            toast?.cancel()

            val view = LayoutInflater.from(ctx)
                .inflate(R.layout.toast_tools_layout, null)
                .also { v ->
                    decorateToast(ctx, v, message, bgColor, error)
                }

            toast = Toast(ctx).apply {
                @Suppress("DEPRECATION")
                this.view = view
                setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, 64.px)
                this.duration = duration
                show()
            }
        }
    }
}

/**
 * This method also works in thread.
 */
fun cancelToast() {
    toast?.cancel()
    FloatView.with(FLOAT_VIEW_TAG).remove()
}

private fun setDrawableIcon(ctx: Context, tv: TextView) {
    toastIcon?.let { iconRes ->
        // tv.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        val iconDrawable = ContextCompat.getDrawable(ctx, iconRes)
        iconDrawable?.setBounds(0, 0, toastIconSize, toastIconSize)
        tv.compoundDrawablePadding = 8.px
        tv.setCompoundDrawables(iconDrawable, null, null, null)
    }
}

private fun decorateToast(ctx: Context, rootView: View, message: String, bgColor: String? = null,
    error: Boolean = false) {
    val tv: TextView = rootView.findViewById(R.id.tv_text)
    tv.text = message
    //    tv.setTextColor(ContextCompat.getColor(tv.context, android.R.color.white))
    setDrawableIcon(ctx, tv)

    val defaultBgDrawable: Drawable = ResourcesCompat.getDrawable(ctx.resources,
        R.drawable.toast_bg_normal, null)!!
    if (!error && bgColor == null) {
        rootView.background = defaultBgDrawable
    } else { // with error or with bgColor
        val customDrawableWrapper = DrawableCompat.wrap(defaultBgDrawable).mutate()
        rootView.background = customDrawableWrapper
        val defBgColor = bgColor ?: if (error) ERROR_BG_COLOR else NORMAL_BG_COLOR
        DrawableCompat.setTint(customDrawableWrapper, Color.parseColor(defBgColor))
    }
}