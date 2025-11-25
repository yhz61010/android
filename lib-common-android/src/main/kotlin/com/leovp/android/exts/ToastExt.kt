@file:Suppress("unused")

package com.leovp.android.exts

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.GravityInt
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.toColorInt
import com.leovp.android.R
import com.leovp.android.ui.ForegroundComponent
import com.leovp.android.utils.API
import com.leovp.floatview.FloatView
import com.leovp.kotlin.utils.SingletonHolder

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Initialize `LeoToast` in your custom `Application` or somewhere as earlier as possible.
 *
 * ```
 * LeoToast.getInstance(application).init(
 *     LeoToast.ToastConfig(
 *         buildConfigInDebug = BuildConfig.DEBUG,
 *         toastIcon = R.mipmap.ic_launcher_round
 *     )
 * )
 * ```
 */
class LeoToast private constructor(private val ctx: Context) {
    companion object : SingletonHolder<LeoToast, Context>(::LeoToast) {
        /** Unit: sp */
        const val DEF_FONT_SIZE = 14f
    }

    lateinit var config: ToastConfig
        private set

    fun init(config: ToastConfig = ToastConfig()) {
        // Log.e("LEO-toast", "=====> registerToastRotationWatcher() <=====")
        this.config = config
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            initForegroundComponentForToast(ctx.applicationContext as Application)
        }
    }

    data class ToastConfig(
        /**
         * Whether the host project is working in `DEBUG` mode.
         *
         * You **MUST** initialize `buildConfigInDebug` in the very beginning when you start your app.
         * Otherwise, the `debug` feature doesn't work.
         * For example in your custom Application.
         */
        var buildConfigInDebug: Boolean = false,

        @param:DrawableRes
        var toastIcon: Int? = null,

        /** Unit: px */
        var toastIconSize: Int = 24.px,

        /** Unit: sp */
        var textSize: Float = DEF_FONT_SIZE,

        /** Hex color value with prefix '#'. Example: "#FFCC0000". */
        var textColor: String? = null,

        /** Hex color value with prefix '#'. Example: "#FFCC0000". */
        var bgColor: String? = null,

        @param:LayoutRes
        var layout: Int = R.layout.toast_layout,

        @param:GravityInt
        var gravity: Int = Gravity.BOTTOM,

        var duration: Int = Toast.LENGTH_SHORT,

        /** Only valid for Android 11+ */
        var durationShortMs: Long = 2000L,

        /** Only valid for Android 11+ */
        var durationLongMs: Long = 3500L,

        /** Unit: dp */
        var extraMargin: Int = 0,
    )

    /**
     * **Attention:**
     * This method must be called for Android R(Android 11) or above.
     * Otherwise, the custom toast doesn't work when app in background.
     */
    private fun initForegroundComponentForToast(app: Application, delay: Long = 500) {
        ForegroundComponent.init(app, delay)
    }
}

// ==============================

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 * @param textColor Text hex color value with prefix '#'. Example: "#FFCC0000".
 * @param bgColor Background hex color value with prefix '#'. Example: "#FFCC0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
fun Context.toast(
    @StringRes resId: Int,
    longDuration: Boolean? = null,
    origin: Boolean = false,
    debug: Boolean = false,
    textColor: String? = null,
    bgColor: String? = null,
    error: Boolean = false,
) {
    toast(
        msg = getString(resId),
        longDuration = longDuration,
        origin = origin,
        debug = debug,
        textColor = textColor,
        bgColor = bgColor,
        error = error
    )
}

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 *               On Android 11+(Android R+), this parameter will be ignored.
 * @param bgColor Background hex color value with prefix '#'. Example: "#FFCC0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
fun Context.toast(
    msg: String?,
    longDuration: Boolean? = null,
    origin: Boolean = false,
    debug: Boolean = false,
    textColor: String? = null,
    bgColor: String? = null,
    error: Boolean = false,
) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        showToast(
            ctx = this,
            msg = msg,
            longDuration = longDuration,
            origin = origin,
            debug = debug,
            textColor = textColor,
            bgColor = bgColor,
            error = error
        )
    } else {
        // Be sure toast can be shown in thread
        mainHandler.post {
            showToast(
                ctx = this,
                msg = msg,
                longDuration = longDuration,
                origin = origin,
                debug = debug,
                textColor = textColor,
                bgColor = bgColor,
                error = error
            )
        }
    }
}

// ==========

const val TOAST_ERROR_BG_COLOR = "#FFF16C4C"
const val TOAST_NORMAL_TEXT_COLOR_WHITE = "#FFFFFFFF"

private var toast: Toast? = null
private const val FLOAT_VIEW_TAG = "leo-enhanced-custom-toast"

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 *               On Android 11+(Android R+), this parameter will be ignored.
 * @param bgColor Background hex color value with prefix '#'. Example: "#FFCC0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
@SuppressLint("InflateParams")
private fun showToast(
    ctx: Context,
    msg: String?,
    longDuration: Boolean?,
    origin: Boolean,
    debug: Boolean,
    textColor: String?,
    bgColor: String?,
    error: Boolean,
) {
    val toastCfg = LeoToast.getInstance(ctx).config

    if ((debug && !toastCfg.buildConfigInDebug)) {
        // Debug log only be shown in DEBUG flavor
        return
    }

    val isLongDuration = longDuration ?: (toastCfg.duration == Toast.LENGTH_LONG)
    val duration = if (isLongDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    val message: String = if (debug) "DEBUG: $msg" else (msg ?: "null")
    runCatching {
        when {
            origin || ctx is Application -> Toast.makeText(ctx, message, duration).show()
            API.ABOVE_R -> {
                runCatching {
                    if (ForegroundComponent.get().isBackground && !ctx.canDrawOverlays) {
                        Toast.makeText(ctx, message, duration).show()
                        return
                    }
                }.onFailure {
                    Toast.makeText(ctx, message, duration).show()
                    return
                }
                // if (error || bgColor != null) {
                //     val errorMsgColor = bgColor ?: ERROR_BG_COLOR
                //     Toast.makeText(ctx, HtmlCompat.fromHtml("<font color='$errorMsgColor'>$message</font>",
                //         HtmlCompat.FROM_HTML_MODE_LEGACY), duration).show()
                // } else {
                //     Toast.makeText(ctx, message, duration).show()
                // }
                mainHandler.removeCallbacksAndMessages(null)
                FloatView.with(FLOAT_VIEW_TAG).remove(true)
                FloatView.with(ctx)
                    .layout(toastCfg.layout) { v ->
                        decorateToast(
                            ctx = ctx,
                            rootView = v,
                            message = message,
                            textSize = toastCfg.textSize,
                            textColor = textColor ?: toastCfg.textColor,
                            bgColor = bgColor ?: toastCfg.bgColor,
                            error = error
                        )
                    }
                    .meta { viewWidth, viewHeight ->
                        // Log.d("LEO-FV", "viewWidth=$viewWidth viewHeight=$viewHeight  dp=${viewWidth.dp}x${viewHeight.dp}")
                        tag = FLOAT_VIEW_TAG
                        gravity = toastCfg.gravity or Gravity.CENTER_HORIZONTAL
                        enableAlphaAnimation = true
                        enableDrag = false
                        systemWindow = ctx.canDrawOverlays
                        // val toastPos = calculateToastPosition(ctx, viewWidth)
                        // x = toastPos.x
                        // y = toastPos.y
                    }
                    .show()
                mainHandler.postDelayed(
                    { FloatView.with(FLOAT_VIEW_TAG).remove() },
                    if (isLongDuration) {
                        toastCfg.durationLongMs
                    } else {
                        toastCfg.durationShortMs
                    }
                )
            }

            else -> {
                toast?.cancel()

                val view = LayoutInflater.from(ctx)
                    .inflate(toastCfg.layout, null)
                    .also { v ->
                        decorateToast(
                            ctx = ctx,
                            rootView = v,
                            message = message,
                            textSize = toastCfg.textSize,
                            textColor = textColor ?: toastCfg.textColor,
                            bgColor = bgColor ?: toastCfg.bgColor,
                            error = error
                        )
                    }

                toast = Toast(ctx).apply {
                    @Suppress("DEPRECATION")
                    this.view = view
                    setGravity(
                        /* gravity = */ Gravity.CENTER_HORIZONTAL or toastCfg.gravity,
                        /* xOffset = */ 0,
                        /* yOffset = */ (64 + toastCfg.extraMargin).px
                    )
                    this.duration = duration
                    show()
                }
            }
        }
    }.onFailure { it.printStackTrace() }
}

/**
 * This method also works in thread.
 */
fun cancelToast() {
    toast?.cancel()
    FloatView.with(FLOAT_VIEW_TAG).remove()
}

private fun setDrawableIcon(ctx: Context, tv: TextView) {
    val toastCfg = LeoToast.getInstance(ctx).config
    toastCfg.toastIcon?.let { iconRes ->
        // tv.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        val iconDrawable = ContextCompat.getDrawable(ctx, iconRes)
        iconDrawable?.setBounds(0, 0, toastCfg.toastIconSize, toastCfg.toastIconSize)
        tv.compoundDrawablePadding = 8.px
        tv.setCompoundDrawables(iconDrawable, null, null, null)
    }
}

private fun decorateToast(
    ctx: Context,
    rootView: View,
    message: String,
    textSize: Float,
    textColor: String? = null,
    bgColor: String? = null,
    error: Boolean = false,
) {
    val tv: TextView = rootView.findViewById(R.id.tv_text)
    tv.text = message
    tv.textSize = textSize
    //    tv.setTextColor(ContextCompat.getColor(tv.context, android.R.color.white))
    setDrawableIcon(ctx, tv)

//    val defaultBgDrawable: Drawable = ResourcesCompat.getDrawable(
//        ctx.resources,
//        R.drawable.toast_bg_normal,
//        null
//    )!!
    val containerViewGroup: LinearLayout = rootView.findViewById(R.id.ll_container)
    val defaultBgDrawable = containerViewGroup.background
    if (!error && bgColor == null) { // without error and without bgColor
        containerViewGroup.background = defaultBgDrawable
        tv.setTextColor(textColor?.toColorInt() ?: tv.currentTextColor)
    } else {
        // - with error and with bgColor
        // - with error but without bgColor
        // - without error but with bgColor

        tv.setTextColor((textColor ?: TOAST_NORMAL_TEXT_COLOR_WHITE).toColorInt())

        val customDrawableWrapper = DrawableCompat.wrap(defaultBgDrawable).mutate()
        containerViewGroup.background = customDrawableWrapper
        val defBgColor = bgColor ?: TOAST_ERROR_BG_COLOR
        DrawableCompat.setTint(customDrawableWrapper, defBgColor.toColorInt())
    }
}
