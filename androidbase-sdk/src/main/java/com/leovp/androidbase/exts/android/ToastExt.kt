package com.leovp.androidbase.exts.android

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.leovp.androidbase.BuildConfig
import com.leovp.androidbase.R
import com.leovp.androidbase.utils.system.API

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */

/**
 * @param normal On Android 11+(Android R+), this parameter will be ignored.
 */
fun toast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT, error: Boolean = false, debug: Boolean = false, normal: Boolean = false) {
    toast(app.getString(resId), duration, error, debug, normal)
}

/**
 * @param normal On Android 11+(Android R+), this parameter will be ignored.
 */
fun toast(msg: String?, duration: Int = Toast.LENGTH_SHORT, error: Boolean = false, debug: Boolean = false, normal: Boolean = false) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        showToast(msg, duration, error, debug, normal)
    } else {
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post { showToast(msg, duration, error, debug, normal) }
    }
}

private var toast: Toast? = null

@SuppressLint("InflateParams")
private fun showToast(msg: String?, duration: Int, error: Boolean, debug: Boolean, normal: Boolean) {
    if (debug && !BuildConfig.DEBUG) {
        // Debug log only be shown in DEBUG flavor
        return
    }
    val message: String? = if (debug) "DEBUG: $msg" else msg
    if (normal || API.ABOVE_R) {
        if (error) {
            Toast.makeText(app, HtmlCompat.fromHtml("<font color='#eeff41'>$message</font>", HtmlCompat.FROM_HTML_MODE_LEGACY), duration).show()
        } else {
            Toast.makeText(app, message, duration).show()
        }
    } else {
        toast?.cancel()

        val view = LayoutInflater.from(app).inflate(R.layout.toast_tools_layout, null).apply {
            findViewById<TextView>(R.id.tv_text).run {
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                text = message
            }
            setBackgroundResource(if (error) R.drawable.toast_bg_error else R.drawable.toast_bg_normal)
        }

        toast = Toast(app).apply {
            this.view = view
            setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, dp2px(50F))
            this.duration = duration
            show()
        }
    }
}