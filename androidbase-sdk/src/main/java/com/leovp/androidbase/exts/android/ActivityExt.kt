package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.Fragment
import kotlin.reflect.KClass

fun Activity.ignoreDuplicateStartSplash(): Boolean {
    return if (this.intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT > 0) {
        this.finish()
        true
    } else false
}

/**
 * This method must be called before setContentView.
 *
 * @param act The activity
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
    this.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
    this.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

    // https://blog.csdn.net/c15522627353/article/details/52452490
    // https://blog.csdn.net/lyabc123456/article/details/88683425
    // Always hide virtual navigation
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        this.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        // https://blog.csdn.net/qiyei2009/article/details/74435809
        this.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // https://blog.csdn.net/weixin_37997371/article/details/83536953
            val lp = this.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            this.attributes = lp
        }
    } else {
        val v = this.decorView
        v.systemUiVisibility = View.GONE
    }
}

// ============================================================================

/** Launch a Activity */
fun Context.startActivity(cls: KClass<*>, flags: Int? = null) = this.startActivity(Intent(this, cls.java).apply { flags?.let { addFlags(it) } })

/** Launch a Activity */
fun Fragment.startActivity(cls: KClass<*>, flags: Int? = null) = this.startActivity(Intent(context, cls.java).apply { flags?.let { addFlags(it) } })

/** Launch a Activity */
fun Context.startActivity(cls: KClass<*>, flags: Int? = null, options: Bundle? = null) = this.startActivity(Intent(this, cls.java).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity */
fun Fragment.startActivity(cls: KClass<*>, flags: Int? = null, options: Bundle? = null) =
    this.startActivity(Intent(context, cls.java).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity */
@JvmOverloads
fun Activity.startActivityForResult(cls: KClass<*>, requestCode: Int, flags: Int? = null, options: Bundle? = null) {
    startActivityForResult(Intent(this, cls.java).apply { flags?.let { addFlags(it) } }, requestCode, options)
}

/** Launch a Activity */
@JvmOverloads
fun Fragment.startActivityForResult(cls: KClass<*>, requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(context, cls.java), requestCode, options)
}

/** Launch applications detail page */
fun Context.startAppDetails() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.packageUri))

/** Launch applications detail page */
fun Fragment.startAppDetails() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.requireContext().packageUri))
}

/** Launch applications detail page */
@JvmOverloads
fun Activity.startAppDetailsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.packageUri), requestCode, options)
}

/** Launch applications detail page */
@JvmOverloads
fun Fragment.startAppDetailsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.requireContext().packageUri), requestCode, options)
}

/** Launch internal storage settings page */
fun Context.startStorageSettings() {
    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
}

/** Launch internal storage settings page */
fun Fragment.startStorageSettings() {
    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
}

/** Launch internal storage settings page */
@JvmOverloads
fun Activity.startStorageSettingsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), requestCode, options)
}

/** Launch internal storage settings page */
@JvmOverloads
fun Fragment.startStorageSettingsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), requestCode, options)
}