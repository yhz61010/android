package com.leovp.androidbase.exts.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */
fun Context.toast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT) {
    toast(getString(resId), length)
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(this, msg, length).show()
    } else {
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, msg, length).show() }
    }
}