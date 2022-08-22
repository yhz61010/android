package com.leovp.android.exts

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.leovp.floatview.FloatView
import com.leovp.lib.common.android.R
import com.leovp.android.ui.ForegroundComponent
import com.leovp.android.utils.API
import com.leovp.kotlin.exts.fail
import com.leovp.kotlin.utils.SingletonHolder
import com.leovp.reflection.wrappers.ServiceManager
import kotlin.math.max

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */

data class ToastConfig(
    /**
     * Whether the host project is working in `DEBUG` mode.
     *
     * You **MUST** initialize `buildConfigInDebug` in the very beginning when you start your app.
     * Otherwise, the `debug` feature doesn't work.
     * For example in your custom Application.
     */
    var buildConfigInDebug: Boolean = false,

    @DrawableRes
    var toastIcon: Int? = null,

    /** Unit: px */
    var toastIconSize: Int = 24.px,
)

/**
 * Initialize `LeoToast` in your custom `Application` or somewhere as earlier as possible.
 *
 * ```
 * LeoToast.getInstance(ctx).apply {
 *      config = ToastConfig(BuildConfig.DEBUG, R.mipmap.ic_launcher_round)
 *      // DO NOT forget to call the following method on Android 11 or above.
 *      initForegroundComponentForToast(application)
 * }
 * ```
 *
 * Don't forget to call `removeToastRotationWatcher()` when you don't need custom toast anymore.
 * ```
 * LeoToast.getInstance(this).removeToastRotationWatcher()
 * ```
 */
class LeoToast private constructor(private val ctx: Context) {
    companion object : SingletonHolder<LeoToast, Context>(::LeoToast)

    var config: ToastConfig? = null

    private val toastRotationWatcher = object : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            if (FloatView.with(FLOAT_VIEW_TAG).exist() &&
                FloatView.with(FLOAT_VIEW_TAG).isDisplaying()
            ) {
                mainHandler.post {
                    FloatView.with(FLOAT_VIEW_TAG).screenOrientation = rotation
                    val viewWidth = FloatView.with(FLOAT_VIEW_TAG).floatViewWidth
                    val toastPos = calculateToastPosition(ctx, rotation, viewWidth)
                    //                Log.e("LEO-float-view",
                    //                    "toast onRotationChanged rotation=$rotation scrAvailSz=$scrAvailSz viewWidth=$viewWidth vw=$viewWidth x=$x y=$y")
                    FloatView.with(FLOAT_VIEW_TAG).setPosition(toastPos.x, toastPos.y)
                }
            }
        }
    }

    /**
     * **Attention:**
     * This method must be called for Android R(Android 11) or above.
     * Otherwise, the custom toast doesn't work when app in background.
     */
    fun initForegroundComponentForToast(app: Application, delay: Long = 500) {
        ForegroundComponent.init(app, delay)
    }

    private fun registerToastRotationWatcher() {
        ServiceManager.windowManager?.registerRotationWatcher(toastRotationWatcher)
    }

    fun removeToastRotationWatcher() {
        ServiceManager.windowManager?.removeRotationWatcher(toastRotationWatcher)
    }

    init {
        registerToastRotationWatcher()
    }
}

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 *               On Android 11+(Android R+), this parameter will be ignored.
 * @param bgColor Background hex color value with prefix '#'. Example: "#ff0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
fun Context.toast(
    @StringRes resId: Int,
    longDuration: Boolean = false,
    origin: Boolean = false,
    debug: Boolean = false,
    bgColor: String? = null,
    error: Boolean = false
) {
    toast(getString(resId), longDuration, origin, debug, bgColor, error)
}

/**
 * @param origin `true` to show Android original toast. `false` to show custom toast.
 *               On Android 11+(Android R+), this parameter will be ignored.
 * @param bgColor Background hex color value with prefix '#'. Example: "#ff0000"
 * @param error `true` will use `ERROR_BG_COLOR` as background.
 *              However, if you also set `bgColor`, `error` parameter will be ignored.
 */
fun Context.toast(
    msg: String?,
    longDuration: Boolean = false,
    origin: Boolean = false,
    debug: Boolean = false,
    bgColor: String? = null,
    error: Boolean = false
) {
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
private fun showToast(
    ctx: Context?,
    msg: String?,
    longDuration: Boolean = false,
    origin: Boolean,
    debug: Boolean,
    bgColor: String?,
    error: Boolean
) {
    if (ctx == null) return
    val toastCfg = LeoToast.getInstance(ctx).config ?: fail("Toast config can't be null.")

    if ((debug && !toastCfg.buildConfigInDebug)) {
        // Debug log only be shown in DEBUG flavor
        return
    }

    val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    val message: String = if (debug) "DEBUG: $msg" else (msg ?: "null")
    when {
        origin -> Toast.makeText(ctx, message, duration).show()
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
            //            Log.e("LEO-float-view", "ctx.screenSurfaceRotation=${ctx.screenSurfaceRotation} ctx.screenWidth=${ctx.screenWidth}")
            FloatView.with(FLOAT_VIEW_TAG).remove(true)
            val currentScreenOrientation = ctx.screenSurfaceRotation
            FloatView.with(ctx)
                .layout(R.layout.toast_layout) { v ->
                    decorateToast(ctx, v, message, bgColor, error)
                }
                .meta { viewWidth, _ ->
                    tag = FLOAT_VIEW_TAG
                    enableAlphaAnimation = true
                    enableDrag = false
                    systemWindow = ctx.canDrawOverlays
                    val toastPos = calculateToastPosition(ctx, currentScreenOrientation, viewWidth)
                    x = toastPos.x
                    y = toastPos.y
                    screenOrientation = currentScreenOrientation
                }
                .show()
            mainHandler.postDelayed(
                { FloatView.with(FLOAT_VIEW_TAG).remove() },
                if (longDuration) 3500 else 2000
            )
        }
        else -> {
            toast?.cancel()

            val view = LayoutInflater.from(ctx)
                .inflate(R.layout.toast_layout, null)
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
    val toastCfg = LeoToast.getInstance(ctx).config ?: fail("Toast config can't be null.")
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
    bgColor: String? = null,
    error: Boolean = false
) {
    val tv: TextView = rootView.findViewById(R.id.tv_text)
    tv.text = message
    //    tv.setTextColor(ContextCompat.getColor(tv.context, android.R.color.white))
    setDrawableIcon(ctx, tv)

    val defaultBgDrawable: Drawable = ResourcesCompat.getDrawable(
        ctx.resources,
        R.drawable.toast_bg_normal, null
    )!!
    if (!error && bgColor == null) {
        rootView.background = defaultBgDrawable
    } else { // with error or with bgColor
        val customDrawableWrapper = DrawableCompat.wrap(defaultBgDrawable).mutate()
        rootView.background = customDrawableWrapper
        val defBgColor = bgColor ?: if (error) ERROR_BG_COLOR else NORMAL_BG_COLOR
        DrawableCompat.setTint(customDrawableWrapper, Color.parseColor(defBgColor))
    }
}

private fun calculateToastPosition(ctx: Context, orientation: Int, viewWidth: Int): Point {
    val toastMaxWidth = ctx.resources.getDimensionPixelSize(R.dimen.toast_max_width)
    val toastMargin = ctx.resources.getDimensionPixelSize(R.dimen.toast_layout_margin_horizontal)
    val toastFinalWidth = toastMaxWidth + toastMargin
    val scrAvailSz = ctx.getScreenSize(orientation, ctx.screenAvailableResolution)
    val scrSz = ctx.getScreenSize(orientation, ctx.screenRealResolution)
    val widthDiff = when {
        ctx.isGoogle -> {
            if (Surface.ROTATION_90 == orientation) {
                (
                    max(scrSz.width, scrSz.height) - max(
                        scrAvailSz.width,
                        scrAvailSz.height
                    )
                    ) / (if (ctx.navigationBarHeight > 0) 1 else 2)
            } else 0
        }
        else -> 0
    }
    //    Log.e("LEO-float-view", "curScreenOrientation=$orientation viewWidth=$viewWidth")
    val vw = if (viewWidth >= toastFinalWidth) toastFinalWidth else viewWidth
    //    Log.e("LEO-float-view", "scrAvailSz=$scrAvailSz viewWidth=$viewWidth vw=$vw " +
    //            "toastMaxWidth=$toastMaxWidth toastMargin=$toastMargin toastFinalWidth=$toastFinalWidth " +
    //            "statusBarHeight=${ctx.statusBarHeight} widthDiff=$widthDiff navigationHeight=${ctx.navigationBarHeight}")
    val x = (scrAvailSz.width - vw) / 2 + widthDiff
    val y = scrAvailSz.height - if (isPortrait(orientation)) 60.px else 88.px // 128 / 104
    return Point(x, y)
}
