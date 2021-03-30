package com.leovp.androidbase.exts.android

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */
fun toast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT) {
    toast(app.getString(resId), length)
}

fun toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(app, msg, length).show()
    } else {
        // Be sure toast can be shown in thread
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, length).show() }
    }
}