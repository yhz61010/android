package com.leovp.android.exts

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Author: Michael Leo
 * Date: 2023/7/11 16:46
 */

val Activity.actionBarHeight
    get(): Int {
        val tv = TypedValue()
        var actionBarHeight = 0
        if (this.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }
        return actionBarHeight
    }

// <editor-fold desc="Set ActionBar background">

/**
 * Set the color of ActionBar by color int.
 */
fun AppCompatActivity.setActionBarBackground(@ColorInt color: Int) {
    supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
}

/**
 * Set the color of ActionBar by color resource.
 */
fun AppCompatActivity.setActionBarBackgroundRes(@ColorRes colorRes: Int) {
    supportActionBar?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, colorRes)))
}

/**
 * Transparent ActionBar.
 */
fun AppCompatActivity.setActionBarTransparent() {
    supportActionBar?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
}

// </editor-fold>
