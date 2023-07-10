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


//<editor-fold desc="Set status bar color">
/** Set status bar color by color int. */
fun Activity.statusBarColor(@ColorInt color: Int) {
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window?.statusBarColor = color
}

/** Set status bar color by resource id. */
fun Activity.statusBarColorRes(@ColorRes colorRes: Int) = statusBarColor(ContextCompat.getColor(this, colorRes))

//</editor-fold>

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
 * Set the color of status bar or make it transparent.
 * 设置透明状态栏或者状态栏颜色, 此函数会导致状态栏覆盖界面,
 * 如果不希望被状态栏遮挡Toolbar请再调用[statusPadding]设置视图的paddingTop 或者 [statusMargin]设置视图的marginTop为状态栏高度
 *
 * 如果不指定状态栏颜色则会应用透明状态栏(全屏属性), 会导致键盘遮挡输入框
 *
 * @param color The color of status bar. Default value is transparent.
 * @param darkMode Whether to use dark mode or not.
 */
fun Activity.immersive(@ColorInt color: Int = Color.TRANSPARENT, darkMode: Boolean? = null) {
    window.statusBarColor = color

    // On Android API 30 or above (R, Android 11), transparent or translucent status bar needs the following setting.
    // This method has already done the compatibility
    WindowCompat.setDecorFitsSystemWindows(window, false)

    if (Color.TRANSPARENT == color) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    // Add this if your want your status bar translucent.
    // window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

    if (darkMode != null) {
        darkMode(darkMode)
    }

    // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
    //     window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    //     window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    // }
    //
    // when (color) {
    //     Color.TRANSPARENT -> {
    //         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    //             // https://stackoverflow.com/a/64828028
    //             WindowCompat.setDecorFitsSystemWindows(window, false)
    //             WindowInsetsControllerCompat(window, window.decorView).let { controller ->
    //                 controller.hide(WindowInsetsCompat.Type.statusBars()) // controller.hide(WindowInsetsCompat.Type.navigationBars())
    //                 controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    //             }
    //         } else {
    //             window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    //             window.statusBarColor = Color.TRANSPARENT
    //         }
    //     }
    //
    //     else -> window.statusBarColor = color
    // }
}

/**
 * 退出沉浸式状态栏并恢复默认状态栏颜色
 *
 * @param black 是否显示黑色状态栏白色文字(不恢复状态栏颜色)
 */
fun Activity.immersiveExit(black: Boolean = false) {
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

    // 恢复默认状态栏颜色
    if (black) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    } else {
        val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.statusBarColor))
        window.statusBarColor = typedArray.getColor(0, 0)
        typedArray.recycle()
    }

    // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    //     // https://stackoverflow.com/a/64828028
    //     WindowCompat.setDecorFitsSystemWindows(window, false)
    //     WindowInsetsControllerCompat(window, window.decorView).let { controller ->
    //         controller.hide(WindowInsetsCompat.Type.statusBars()) // controller.hide(WindowInsetsCompat.Type.navigationBars())
    //         controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    //     }
    // } else {
    //     window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    //     window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    // }
    //
    // // Restore the default color of status bar.
    // if (black) {
    //     window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    // } else {
    //     val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.statusBarColor))
    //     window.statusBarColor = typedArray.getColor(0, 0)
    //     typedArray.recycle()
    // }
}

/**
 * Set immersive color of status bar by resource id.
 */
@JvmOverloads
fun Activity.immersiveRes(@ColorRes color: Int, darkMode: Boolean? = null) = immersive(ContextCompat.getColor(this, color), darkMode)

// </editor-fold>


//<editor-fold desc="dark mode">
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
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

//</editor-fold>

// <editor-fold desc="间距">

/**
 * 增加View的paddingTop, 增加高度为状态栏高度, 用于防止视图和状态栏重叠
 * 如果是RelativeLayout设置padding值会导致centerInParent等属性无法正常显示
 * @param remove 如果默认paddingTop大于状态栏高度则添加无效, 如果小于状态栏高度则无法删除
 */
@JvmOverloads
fun View.statusPadding(remove: Boolean = false) {
    if (this is RelativeLayout) {
        throw UnsupportedOperationException("Unsupported set statusPadding for RelativeLayout")
    }
    val statusBarHeight = context.statusBarHeight
    val lp = layoutParams
    if (lp != null && lp.height > 0) {
        lp.height += statusBarHeight // increase height
    }
    if (remove) {
        if (paddingTop < statusBarHeight) return
        setPadding(paddingLeft, paddingTop - statusBarHeight, paddingRight, paddingBottom)
    } else {
        if (paddingTop >= statusBarHeight) return
        setPadding(paddingLeft, paddingTop + statusBarHeight, paddingRight, paddingBottom)
    }
}

/**
 * 增加View的marginTop值, 增加高度为状态栏高度, 用于防止视图和状态栏重叠
 * @param remove 如果默认marginTop大于状态栏高度则添加无效, 如果小于状态栏高度则无法删除
 */
@JvmOverloads
fun View.statusMargin(remove: Boolean = false) {
    val statusBarHeight = context.statusBarHeight
    val lp = layoutParams as ViewGroup.MarginLayoutParams
    if (remove) {
        if (lp.topMargin < statusBarHeight) return
        lp.topMargin -= statusBarHeight
        layoutParams = lp
    } else {
        if (lp.topMargin >= statusBarHeight) return
        lp.topMargin += statusBarHeight
        layoutParams = lp
    }
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

//</editor-fold>

//<editor-fold desc="ActionBar">

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

//</editor-fold>
