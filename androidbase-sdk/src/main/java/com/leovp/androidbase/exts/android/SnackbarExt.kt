package com.leovp.androidbase.exts.android

import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar

/**
 * Author: Michael Leo
 * Date: 2020/9/11 上午11:32
 */

/**
 * Example:
 * ```
 * view.snack(R.string.your_string) {
 *     action("Undo" /* or use string resource id */, Color.RED) { adapter.undo() }
 * }
 * ```
 */
inline fun View.snack(@StringRes messageRes: Int, length: Int = Snackbar.LENGTH_LONG, crossinline f: Snackbar.() -> Unit) {
    snack(resources.getString(messageRes), length, f)
}

/**
 * Example:
 * ```
 * view.snack("Undo last delete?") {
 *     action("Undo" /* or use string resource id */, Color.RED) { adapter.undo() }
 * }
 * ```
 */
inline fun View.snack(message: String, length: Int = Snackbar.LENGTH_LONG, crossinline f: Snackbar.() -> Unit) {
    val snack = Snackbar.make(this, message, length)
    snack.f()
    snack.show()
}

/**
 * @param color The action text color. DO NOT use color resource id directly.
 * If you want use color resource id, do like this:
 * ```kotlin
 * ContextCompat.getColor(context, R.color.design_default_color_primary)
 * ```
 */
fun Snackbar.action(@StringRes actionRes: Int, color: Int? = null, listener: (View) -> Unit) {
    action(view.resources.getString(actionRes), color, listener)
}

/**
 * @param color The action text color. DO NOT use color resource id directly.
 * If you want use color resource id, do like this:
 * ```kotlin
 * ContextCompat.getColor(context, R.color.design_default_color_primary)
 * ```
 */
fun Snackbar.action(action: String, color: Int? = null, listener: (View) -> Unit) {
    setAction(action, listener)
    color?.let { setActionTextColor(it) }
}