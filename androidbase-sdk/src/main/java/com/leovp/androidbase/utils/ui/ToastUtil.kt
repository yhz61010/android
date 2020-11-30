package com.leovp.androidbase.utils.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.leovp.androidbase.BuildConfig
import com.leovp.androidbase.R
import com.leovp.androidbase.exts.android.dp2px

/**
 * Author: Michael Leo
 * Date: 19-7-17 下午8:27
 */
object ToastUtil {
    private var sToast: Toast? = null
    private lateinit var mApplicationCtx: Context

    fun init(ctx: Context) {
        mApplicationCtx = ctx
    }

    // ============================================
    fun showNormalToast(message: String?) {
        showToast(mApplicationCtx, message, Toast.LENGTH_SHORT, isFailed = false, defaultToast = true)
    }

    fun showNormalToast(@StringRes resId: Int) {
        showToast(mApplicationCtx, mApplicationCtx.getString(resId), Toast.LENGTH_SHORT, isFailed = false, defaultToast = true)
    }

    fun showNormalLongToast(@StringRes resId: Int) {
        showToast(mApplicationCtx, mApplicationCtx.getString(resId), Toast.LENGTH_LONG, isFailed = false, defaultToast = true)
    }

    fun showNormalLongToast(message: String?) {
        showToast(mApplicationCtx, message, Toast.LENGTH_LONG, isFailed = false, defaultToast = true)
    }
    // ============================================

    fun showToast(message: String?) {
        showToast(mApplicationCtx, message, Toast.LENGTH_SHORT, false)
    }

    fun showToast(@StringRes resId: Int) {
        showToast(mApplicationCtx, mApplicationCtx.getString(resId), Toast.LENGTH_SHORT, false)
    }

    fun showLongToast(@StringRes resId: Int) {
        showToast(mApplicationCtx, mApplicationCtx.getString(resId), Toast.LENGTH_LONG, false)
    }

    fun showLongToast(message: String?) {
        showToast(mApplicationCtx, message, Toast.LENGTH_LONG, false)
    }

    // ============================================
    fun showErrorToast(message: String?) {
        showToast(mApplicationCtx, message, Toast.LENGTH_SHORT, true)
    }

    fun showErrorToast(@StringRes resId: Int) {
        showToast(mApplicationCtx, mApplicationCtx.getString(resId), Toast.LENGTH_SHORT, true)
    }

    fun showErrorLongToast(@StringRes resId: Int) {
        showToast(mApplicationCtx, mApplicationCtx.getString(resId), Toast.LENGTH_LONG, true)
    }

    fun showErrorLongToast(message: String?) {
        showToast(mApplicationCtx, message, Toast.LENGTH_LONG, true)
    }

    // ============================================
    fun showDebugToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalToast("DEBUG: $message")
    }

    fun showDebugLongToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalLongToast("DEBUG: $message")
    }

    fun showDebugErrorToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalToast("DEBUG: $message")
    }

    fun showDebugErrorLongToast(message: String?) {
        if (BuildConfig.DEBUG) showNormalLongToast("DEBUG: $message")
    }

    // ------------

    fun showDebugToast(@StringRes resId: Int) {
        showDebugToast(mApplicationCtx.getString(resId))
    }

    fun showDebugLongToast(@StringRes resId: Int) {
        showDebugLongToast(mApplicationCtx.getString(resId))
    }

    fun showDebugErrorToast(@StringRes resId: Int) {
        showDebugErrorToast(mApplicationCtx.getString(resId))
    }

    fun showDebugErrorLongToast(@StringRes resId: Int) {
        showDebugErrorLongToast(mApplicationCtx.getString(resId))
    }

    // ============================================
    private fun showToast(
        context: Context,
        msg: String?,
        duration: Int,
        isFailed: Boolean,
        defaultToast: Boolean = false
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (defaultToast) Toast.makeText(context, msg, duration).show() else makeText(
                context,
                msg,
                duration,
                isFailed
            )
            return
        }
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post {
            if (defaultToast) {
                Toast.makeText(context, msg, duration).show()
            } else {
                makeText(context, msg, duration, isFailed)
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun makeText(
        context: Context,
        msg: String?,
        duration: Int,
        isFailed: Boolean
    ) {
        sToast?.cancel()

        val view = LayoutInflater.from(context).inflate(R.layout.toast_tools_layout, null)
        view.findViewById<TextView>(R.id.tv_text).run {
            setTextColor(context.resources.getColor(android.R.color.white))
            text = msg
        }

        sToast = Toast(context).also {
            it.view = view
            it.setGravity(Gravity.CENTER, 0, dp2px(-50F))
            it.duration = duration
        }

        if (isFailed) {
            view.setBackgroundResource(R.drawable.toast_bg_error)
        } else {
            view.setBackgroundResource(R.drawable.toast_bg_normal)
        }

        sToast?.show()
    }

    fun clearAllToast() {
        sToast?.cancel()
        sToast = null
    }
}