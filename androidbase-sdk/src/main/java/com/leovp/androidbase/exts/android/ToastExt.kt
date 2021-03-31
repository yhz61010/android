package com.leovp.androidbase.exts.android

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */
fun toast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT, error: Boolean = false, debug: Boolean = false) {
    toast(app.getString(resId), length, error, debug)
}

fun toast(msg: String?, length: Int = Toast.LENGTH_SHORT, error: Boolean = false, debug: Boolean = false) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        showToast(msg, length, error, debug)
    } else {
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post { showToast(msg, length, error, debug) }
    }
}

private fun showToast(msg: String?, length: Int, error: Boolean, debug: Boolean) {
    val message: String? = if (debug) "DEBUG: $msg" else msg
    if (error) {
        Toast.makeText(app, HtmlCompat.fromHtml("<font color='red'>$message</font>", HtmlCompat.FROM_HTML_MODE_LEGACY), length).show()
    } else {
        Toast.makeText(app, message, length).show()
    }
}