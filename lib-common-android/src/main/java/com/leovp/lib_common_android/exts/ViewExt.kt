@file:Suppress("unused")

package com.leovp.lib_common_android.exts

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:17
 */

/**
 * Attention:
 * Use this below Build.VERSION_CODES.R(API < 30 AKA Android 11)
 *
 * Combination of all flags required to put activity into immersive mode
 */
@Suppress("DEPRECATION")
const val FLAGS_FULLSCREEN =
        View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                // More flags
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

/** Milliseconds used for UI animations */
const val ANIMATION_FAST_MILLIS = 50L
const val ANIMATION_SLOW_MILLIS = 100L

/**
 * You should use this click listener to replace with `setOnClickListener` to avoid duplicated click on view
 */
fun View.setOnSingleClickListener(interval: Long = OnSingleClickListener.INTERVAL_TIME, action: (view: View) -> Unit) {
    val actionListener = OnSingleClickListener(interval, action)

    // This is the only place in the project where we should actually use setOnClickListener
    setOnClickListener {
        actionListener.doClick(this)
    }
}

fun View.removeOnSingleClickListener() {
    setOnClickListener(null)
    isClickable = false
}

internal class OnSingleClickListener(private val interval: Long = INTERVAL_TIME, private val action: (view: View) -> Unit) {
    companion object {
        const val INTERVAL_TIME: Long = 500
    }

    private var lastClickTime = 0L

    @Synchronized
    private fun isDuplicatedClick(): Boolean {
        val time = System.currentTimeMillis()
        val delta = time - lastClickTime
        if (delta < interval) {
            return true
        }
        lastClickTime = time
        return false
    }

    fun doClick(view: View) {
        if (!isDuplicatedClick()) {
            action.invoke(view)
        }
    }
}

/**
 * Simulate a button click, including a small delay while it is being pressed to trigger the
 * appropriate animations.
 */
fun View.simulateClick(interval: Long = ANIMATION_FAST_MILLIS) {
    performClick()
    isPressed = true
    invalidate()
    postDelayed({
        invalidate()
        isPressed = false
    }, interval)
}

/** Same as [AlertDialog.show] but setting immersive mode in the dialog's window */
fun AlertDialog.showImmersive() {
    val win = window ?: return
    // Set the dialog to not focusable
    win.setFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    )


    // Make sure that the dialog's window is in full screen
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // https://stackoverflow.com/a/64828028
        WindowCompat.setDecorFitsSystemWindows(win, false)
        WindowInsetsControllerCompat(win, win.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        // https://blog.csdn.net/c15522627353/article/details/52452490
        // https://blog.csdn.net/lyabc123456/article/details/88683425
        // Always hide virtual navigation

        @Suppress("DEPRECATION")
        win.decorView.systemUiVisibility = FLAGS_FULLSCREEN
    }

    // Show the dialog while still in immersive mode
    show()

    // Set the dialog to focusable again
    win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
}

val Context.canDrawOverlays: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)