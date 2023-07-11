@file:Suppress("unused")

package com.leovp.android.exts

/**
 * Author: Michael Leo
 * Date: 2023/7/10 14:12
 */

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

// https://juejin.cn/post/6956488908641304583

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
fun Activity.immersive(@ColorInt color: Int = Color.TRANSPARENT, darkMode: Boolean? = null) {
    // Allow content to extend into status bar.
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Set the color of status bar.
    window.statusBarColor = color

    // According the document, when set _statusBarColor_, we need also do the following step:
    // 1. Set WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag.
    // 2. Unset WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS flag.
    // However, it still works if I don't do that.
    //
    // if (Color.TRANSPARENT == color) {
    //     if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { // < Android 11
    //         window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    //         window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    //     }
    // }

    // Request the system to apply the window insets to the specified view.
    // Window insets represent the areas of the window that are not covered by UI elements,
    // such as the status bar or navigation bar.
    //
    // In order to deal with immersive modes, we have to make a request to update the view's layout or behavior
    // to accommodate any changes in the window insets, where the view needs to be aware of the system window areas
    // to ensure proper rendering and interaction.
    ViewCompat.requestApplyInsets(window.decorView)

    // Check WindowCompat.getInsetsController method in [statusBarDarkMode] method.
    if (darkMode != null) {
        // Set the text color of status bar.
        statusBarDarkMode(darkMode)
    }
}

/**
 * Turn off status bar's immersive mode.
 */
fun Activity.immersiveExit(darkMode: Boolean? = null) {
    // Disallow content to extend into status bar.
    WindowCompat.setDecorFitsSystemWindows(window, true)

    // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { // < Android 11
    //     window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    //     window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    // }

    val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.statusBarColor))
    window.statusBarColor = typedArray.getColor(0, 0)
    typedArray.recycle()

    // Request the system to apply the window insets to the specified view.
    // Window insets represent the areas of the window that are not covered by UI elements,
    // such as the status bar or navigation bar.
    //
    // In order to deal with immersive modes, we have to make a request to update the view's layout or behavior
    // to accommodate any changes in the window insets, where the view needs to be aware of the system window areas
    // to ensure proper rendering and interaction.
    ViewCompat.requestApplyInsets(window.decorView)

    if (darkMode != null) {
        statusBarDarkMode(darkMode)
    }
}

/**
 * Set immersive color of status bar by resource id.
 */
fun Activity.immersiveRes(@ColorRes color: Int, darkMode: Boolean? = null) = immersive(ContextCompat.getColor(this, color), darkMode)

// </editor-fold>

// <editor-fold desc="dark mode">

/**
 * Set dark mode for status bar.
 *
 * @param darkMode Whether to use dark mode or not.
 */
fun Activity.statusBarDarkMode(darkMode: Boolean = true) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = !darkMode
    // controller.isAppearanceLightNavigationBars = !darkMode
    // controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    // when {
    //     Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
    //         WindowInsetsControllerCompat(window, window.decorView).let { controller ->
    //             controller.isAppearanceLightStatusBars = !darkMode
    //             controller.isAppearanceLightNavigationBars = !darkMode
    //             // controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    //         }
    //     }
    //     else -> {
    //         var systemUiVisibility = window.decorView.systemUiVisibility
    //         systemUiVisibility = if (darkMode) {
    //             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //                 systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    //             } else {
    //                 systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    //             }
    //         } else {
    //             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //                 systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
    //             } else {
    //                 systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    //             }
    //         }
    //         window.decorView.systemUiVisibility = systemUiVisibility
    //     }
    // }
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

// </editor-fold>

// <editor-fold desc="Other utilities">

val Context.statusBarHeight
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get(): Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

// </editor-fold>
