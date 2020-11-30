package com.leovp.androidbase.exts.android

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Author: Michael Leo
 * Date: 2020/9/29 上午11:52
 */
fun Context.toast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, getString(resId), length).show()
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, msg, length).show()
}