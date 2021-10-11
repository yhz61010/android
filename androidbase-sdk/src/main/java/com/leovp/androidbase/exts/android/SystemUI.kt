package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat

/**
 * Author: Michael Leo
 * Date: 2021/10/11 15:45
 */

/**
 * This method must be called before setContentView.
 *
 * Example:
 * ```kotlin
 * // Call this method in `onCreate` before `setContentView`
 * requestFullScreen(this)
 *
 * // Generally, call this method in `onResume` to let navigation bar always hide
 * hideNavigationBar(this)
 * ```
 */
fun Activity.requestFullScreen() {
    this.requestWindowFeature(Window.FEATURE_NO_TITLE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        this.window.insetsController?.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        @Suppress("DEPRECATION")
        this.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
    // or
//        act.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
}

/**
 * This method must be called before setContentView.
 *
 * Example:
 * ```kotlin
 * requestFullScreen(this)
 * hideNavigationBar(this)
 * setContentView(R.layout.activity_splash)
 * ```
 */
@SuppressLint("ObsoleteSdkInt")
fun Activity.hideNavigationBar() {
    this.window.hideNavigationBar()
}

@SuppressLint("ObsoleteSdkInt")
fun Window.hideNavigationBar() {
    // Translucent virtual NavigationBar
//    this.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        this.setDecorFitsSystemWindows(false)
    } else {
        @Suppress("DEPRECATION")
        // https://blog.csdn.net/c15522627353/article/details/52452490
        // https://blog.csdn.net/lyabc123456/article/details/88683425
        // Always hide virtual navigation
        this.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    // https://blog.csdn.net/qiyei2009/article/details/74435809
    this.navigationBarColor = Color.TRANSPARENT

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // https://blog.csdn.net/weixin_37997371/article/details/83536953
        val lp = this.attributes
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        this.attributes = lp
    }
}

/**
 * For devices with notches at the top of the display, you can add the following to your v27 theme.xml file
 * make the UI appear either side of the notch:
 * ```xml
 * <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
 * ```
 *
 * @see <a href="https://stackoverflow.com/a/64828067">Solution</a>
 */
//fun Window.hideSystemUI(mainContainer: View) {
//    WindowCompat.setDecorFitsSystemWindows(this, false)
//    WindowInsetsControllerCompat(this, mainContainer).let { controller ->
//        controller.hide(WindowInsetsCompat.Type.systemBars())
//        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//    }
//}

/**
 * For devices with notches at the top of the display, you can add the following to your v27 theme.xml file
 * make the UI appear either side of the notch:
 * ```xml
 * <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
 * ```
 *
 * @see <a href="https://stackoverflow.com/a/64828067">Solution</a>
 */
//fun Window.showSystemUI(mainContainer: View) {
//    WindowCompat.setDecorFitsSystemWindows(this, true)
//    WindowInsetsControllerCompat(this, mainContainer).show(WindowInsetsCompat.Type.systemBars())
//}