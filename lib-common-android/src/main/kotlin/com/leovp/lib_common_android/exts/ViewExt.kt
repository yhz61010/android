@file:Suppress("unused")

package com.leovp.lib_common_android.exts

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.*

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:17
 */

/** Milliseconds used for UI animations */
const val ANIMATION_FAST_MILLIS = 50L
const val ANIMATION_SLOW_MILLIS = 100L

val Context.layoutInflater: LayoutInflater get() = LayoutInflater.from(this)

var View.startMargin: Int
    get() = (this.layoutParams as ViewGroup.MarginLayoutParams).marginStart
    set(value) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> { marginStart = value }
    }

var View.endMargin: Int
    get() = (this.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
    set(value) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> { marginEnd = value }
    }

var View.topMargin: Int
    get() = (this.layoutParams as ViewGroup.MarginLayoutParams).topMargin
    set(value) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = value }
    }

var View.bottomMargin: Int
    get() = (this.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
    set(value) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = value }
    }

var View.startPadding: Int
    get() = paddingStart
    set(value) {
        updateLayoutParams { setPaddingRelative(value, paddingTop, paddingEnd, paddingBottom) }
    }

var View.endPadding: Int
    get() = paddingEnd
    set(value) {
        updateLayoutParams { setPaddingRelative(paddingStart, paddingTop, value, paddingBottom) }
    }

var View.topPadding: Int
    get() = paddingTop
    set(value) {
        updateLayoutParams { setPaddingRelative(paddingStart, value, paddingEnd, paddingBottom) }
    }

var View.bottomPadding: Int
    get() = paddingBottom
    set(value) {
        updateLayoutParams { setPaddingRelative(paddingStart, paddingTop, paddingEnd, value) }
    }

// -------------------

fun Window.fitSystemWindows() = WindowCompat.setDecorFitsSystemWindows(this, false)

fun ViewGroup.circularReveal(button: ImageButton) {
    ViewAnimationUtils.createCircularReveal(
        this,
        button.x.toInt() + button.width / 2,
        button.y.toInt() + button.height / 2,
        0f,
        if (button.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            this.width.toFloat()
        else
            this.height.toFloat()
    ).apply {
        duration = 500
        doOnStart { visibility = View.VISIBLE }
    }.start()
}

fun ViewGroup.circularClose(button: ImageButton, action: () -> Unit = {}) {
    ViewAnimationUtils.createCircularReveal(
        this,
        button.x.toInt() + button.width / 2,
        button.y.toInt() + button.height / 2,
        if (button.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) this.width.toFloat() else this.height.toFloat(),
        0f
    ).apply {
        duration = 500
        doOnStart { action() }
        doOnEnd { visibility = View.GONE }
    }.start()
}

fun View.onWindowInsets(action: (View, WindowInsetsCompat) -> Unit) {
    ViewCompat.requestApplyInsets(this)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        action(v, insets)
        insets
    }
}

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

internal class OnSingleClickListener(
    private val interval: Long = INTERVAL_TIME,
    private val action: (view: View) -> Unit
) {
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
fun View.simulateClick(reactivateDelay: Long = ANIMATION_FAST_MILLIS) {
    performClick()
    isPressed = true
    invalidate()
    postDelayed({
        invalidate()
        isPressed = false
    }, reactivateDelay)
}

/** Pad this view with the insets provided by the device cutout (i.e. notch) */
@RequiresApi(Build.VERSION_CODES.P)
fun View.padWithDisplayCutout() {

    /** Helper method that applies padding from cutout's safe insets */
    fun doPadding(cutout: DisplayCutout) = setPadding(
        cutout.safeInsetLeft,
        cutout.safeInsetTop,
        cutout.safeInsetRight,
        cutout.safeInsetBottom
    )

    // Apply padding using the display cutout designated "safe area"
    rootWindowInsets?.displayCutout?.let { doPadding(it) }

    // Set a listener for window insets since view.rootWindowInsets may not be ready yet
    setOnApplyWindowInsetsListener { _, insets ->
        insets.displayCutout?.let { doPadding(it) }
        insets
    }
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
