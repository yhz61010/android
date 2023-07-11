@file:Suppress("unused")

package com.leovp.android.exts

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

// <editor-fold desc="Set status bar color">
/** Set status bar color by color int. */
fun Activity.statusBarColor(@ColorInt color: Int) {
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window?.statusBarColor = color
}

/** Set status bar color by resource id. */
fun Activity.statusBarColorRes(@ColorRes colorRes: Int) = statusBarColor(ContextCompat.getColor(this, colorRes))

// </editor-fold>

// <editor-fold desc="Immersive status bar">

/**
 * Use the background color of view as status bar's.
 * @param v The background color of status bar according to this view. This method doesn't work if no background of view.
 * @param darkMode Whether to use dark mode or not.
 */
fun Activity.immersive(v: View, darkMode: Boolean? = null) {
    val background = v.background
    if (background is ColorDrawable) {
        immersive(background.color, darkMode)
    }
}

/**
 * Make status bar in immersive mode with specific color. By default, transparent status bar.
 * When transparent status bar, your view will be overlap on status bar.
 * In this case, you'd better call [addStatusBarPadding] or [addStatusBarMargin] in the same time.
 *
 * @param color The color of status bar. Default value is transparent.
 * @param darkMode Whether to use dark mode or not.
 */
@Suppress("DEPRECATION")
fun Activity.immersive(@ColorInt color: Int = Color.TRANSPARENT, darkMode: Boolean? = null) {
    // On Android API 30 or above (R, Android 11), transparent or translucent status bar needs the following setting.
    // This method has already done the compatibility
    WindowCompat.setDecorFitsSystemWindows(window, false)

    if (Color.TRANSPARENT == color) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { // < Android 11
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    window.statusBarColor = color

    if (darkMode != null) {
        darkMode(darkMode)
    }
}

/**
 * Turn off status bar's immersive mode.
 */
@Suppress("DEPRECATION")
fun Activity.immersiveExit(darkMode: Boolean? = null) {
    // On Android API 30 or above (R, Android 11), turn off status bar's immersive mode needs the following setting.
    // This method has already done the compatibility
    WindowCompat.setDecorFitsSystemWindows(window, true)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { // < Android 11
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.statusBarColor))
    window.statusBarColor = typedArray.getColor(0, 0)
    typedArray.recycle()

    if (darkMode != null) {
        darkMode(darkMode)
    }
}

/**
 * Set immersive color of status bar by resource id.
 */
fun Activity.immersiveRes(@ColorRes color: Int, darkMode: Boolean? = null) = immersive(ContextCompat.getColor(this, color), darkMode)

// </editor-fold>

// <editor-fold desc="dark mode">
/**
 * 开关状态栏暗色模式, 并不会透明状态栏, 只是单纯的状态栏文字变暗色调.
 *
 * @param darkMode Whether to use dark mode or not.
 */
@Suppress("DEPRECATION")
fun Activity.darkMode(darkMode: Boolean = true) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = !darkMode
            controller.isAppearanceLightNavigationBars = !darkMode
            // controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        var systemUiVisibility = window.decorView.systemUiVisibility
        systemUiVisibility = if (darkMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
        window.decorView.systemUiVisibility = systemUiVisibility
    }
}

// </editor-fold>

// <editor-fold desc="padding and margin">

/**
 * Increase the view _paddingTop_ by status bar height.
 * Do NOT support _RelativeLayout_ due to the displaying problem about _centerInParent_-like attributes.
 *
 * @param remove If remove if true, decrease the view _paddingTop_ by status bar height.
 * If current view's _paddingTop_ is less than the status bar's height,
 */
fun View.addStatusBarPadding(remove: Boolean = false) {
    if (this is RelativeLayout) {
        throw UnsupportedOperationException("Unsupported set statusPadding for RelativeLayout")
    }
    val statusBarHeight = context.statusBarHeight
    // val lp = layoutParams
    // if (lp != null && lp.height > 0) {
    //     lp.height += statusBarHeight // increase height
    // }
    if (remove) {
        if (paddingTop < statusBarHeight) return
        setPadding(
            paddingLeft,
            paddingTop - statusBarHeight,
            paddingRight,
            paddingBottom
        )
    } else {
        setPadding(paddingLeft, paddingTop + statusBarHeight, paddingRight, paddingBottom)
    }
}

/**
 * Increase the view _topMargin_ by status bar height.
 *
 * @param remove If remove if true, decrease the view _topMargin_ by status bar height.
 * If current view's _topMargin_ is less than the status bar's height,
 */
fun View.addStatusBarMargin(remove: Boolean = false) {
    val statusBarHeight = context.statusBarHeight
    val lp = layoutParams as ViewGroup.MarginLayoutParams
    if (remove) {
        if (lp.topMargin < statusBarHeight) return
        lp.topMargin -= statusBarHeight
    } else {
        lp.topMargin += statusBarHeight
    }
    layoutParams = lp
}

/**
 * 创建假的透明栏
 */
private fun Context.setTranslucentView(container: ViewGroup, color: Int) {
    var simulateStatusBar: View? = container.findViewById(android.R.id.custom)
    if (simulateStatusBar == null && color != 0) {
        simulateStatusBar = View(container.context)
        simulateStatusBar.id = android.R.id.custom
        val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusBarHeight)
        container.addView(simulateStatusBar, lp)
    }
    simulateStatusBar?.setBackgroundColor(color)
}

// </editor-fold>

// <editor-fold desc="ActionBar">

/**
 * 设置ActionBar的背景颜色
 */
fun AppCompatActivity.setActionBarBackground(@ColorInt color: Int) {
    supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
}

fun AppCompatActivity.setActionBarBackgroundRes(@ColorRes colorRes: Int) {
    supportActionBar?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, colorRes)))
}

/**
 * 设置ActionBar的背景颜色为透明
 */
fun AppCompatActivity.setActionBarTransparent() {
    supportActionBar?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
}

// </editor-fold>
