package com.leovp.androidbase.utils.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.leovp.androidbase.BuildConfig
import com.leovp.androidbase.R
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.dp2px

/**
 * When use this utility, you MUST initialize [app] first
 *
 * Author: Michael Leo
 * Date: 19-7-17 下午8:27
 */
@Suppress("unused")
@Deprecated("Use Toast extension(ToastExt.kt) to replace this utility.")
object ToastUtil {
    private var toast: Toast? = null

    // ============================================
    @Suppress("WeakerAccess")
    fun showNormalToast(message: String?) {
        showToast(message, Toast.LENGTH_SHORT, isFailed = false, defaultToast = true)
    }

    fun showNormalToast(@StringRes resId: Int) {
        showToast(app.getString(resId), Toast.LENGTH_SHORT, isFailed = false, defaultToast = true)
    }

    fun showNormalLongToast(@StringRes resId: Int) {
        showToast(app.getString(resId), Toast.LENGTH_LONG, isFailed = false, defaultToast = true)
    }

    @Suppress("WeakerAccess")
    fun showNormalLongToast(message: String?) {
        showToast(message, Toast.LENGTH_LONG, isFailed = false, defaultToast = true)
    }
    // ============================================

    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(message)", "com.leovp.androidbase.exts.android.toast"))
    fun showToast(message: String?) {
        showToast(message, Toast.LENGTH_SHORT, false)
    }

    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(resId)", "com.leovp.androidbase.exts.android.toast"))
    fun showToast(@StringRes resId: Int) {
        showToast(app.getString(resId), Toast.LENGTH_SHORT, false)
    }

    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(resId, length = Toast.LENGTH_LONG)", "com.leovp.androidbase.exts.android.toast"))
    fun showLongToast(@StringRes resId: Int) {
        showToast(app.getString(resId), Toast.LENGTH_LONG, false)
    }

    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(message, length = Toast.LENGTH_LONG)", "com.leovp.androidbase.exts.android.toast"))
    fun showLongToast(message: String?) {
        showToast(message, Toast.LENGTH_LONG, false)
    }

    // ============================================
    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(message)", "com.leovp.androidbase.exts.android.toast"))
    fun showErrorToast(message: String?) {
        showToast(message, Toast.LENGTH_SHORT, true)
    }

    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(resId)", "com.leovp.androidbase.exts.android.toast"))
    fun showErrorToast(@StringRes resId: Int) {
        showToast(app.getString(resId), Toast.LENGTH_SHORT, true)
    }

    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(resId, length = Toast.LENGTH_LONG)", "com.leovp.androidbase.exts.android.toast"))
    fun showErrorLongToast(@StringRes resId: Int) {
        showToast(app.getString(resId), Toast.LENGTH_LONG, true)
    }

    @Deprecated("Custom Toast is deprecated on Android R+", ReplaceWith("toast(message, length = Toast.LENGTH_LONG)", "com.leovp.androidbase.exts.android.toast"))
    fun showErrorLongToast(message: String?) {
        showToast(message, Toast.LENGTH_LONG, true)
    }

    // ============================================
    fun showDebugToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalToast("DEBUG: $message")
    }

    @Suppress("WeakerAccess")
    fun showDebugLongToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalLongToast("DEBUG: $message")
    }

    fun showDebugErrorToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalToast("DEBUG: $message")
    }

    @Suppress("WeakerAccess")
    fun showDebugErrorLongToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalLongToast("DEBUG: $message")
    }

    // ------------

    fun showDebugToast(@StringRes resId: Int) {
        showDebugToast(app.getString(resId))
    }

    fun showDebugLongToast(@StringRes resId: Int) {
        showDebugLongToast(app.getString(resId))
    }

    fun showDebugErrorToast(@StringRes resId: Int) {
        showDebugErrorToast(app.getString(resId))
    }

    fun showDebugErrorLongToast(@StringRes resId: Int) {
        showDebugErrorLongToast(app.getString(resId))
    }

    // ============================================
    private fun showToast(
        msg: String?,
        duration: Int,
        isFailed: Boolean,
        defaultToast: Boolean = false
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (defaultToast) Toast.makeText(app, msg, duration).show() else makeText(
                msg,
                duration,
                isFailed
            )
            return
        }
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post {
            if (defaultToast) {
                Toast.makeText(app, msg, duration).show()
            } else {
                makeText(msg, duration, isFailed)
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun makeText(
        msg: String?,
        duration: Int,
        isFailed: Boolean
    ) {
        toast?.cancel()

        val view = LayoutInflater.from(app).inflate(R.layout.toast_tools_layout, null)
        view.findViewById<TextView>(R.id.tv_text).run {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            text = msg
        }

        toast = Toast(app).also {
            it.view = view
            it.setGravity(Gravity.CENTER, 0, dp2px(-50F))
            it.duration = duration
        }

        if (isFailed) {
            view.setBackgroundResource(R.drawable.toast_bg_error)
        } else {
            view.setBackgroundResource(R.drawable.toast_bg_normal)
        }

        toast?.show()
    }

    fun clearAllToast() {
        toast?.cancel()
        toast = null
    }
}