@file:Suppress("unused")

package com.leovp.android.restricted

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * Author: Michael Leo
 * Date: 2026/7/16 14:22
 */

/** Launch an Activity */
fun Context.startActivity(
    clsStr: String,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null,
) {
    val intent = Intent(
        this, Class.forName(clsStr)
    ).apply {
        flags?.let { addFlags(it) }
    }
    this.startActivity(
        if (extras == null) {
            intent
        } else {
            extras(intent)
        }, options
    )
}

/** Launch an Activity in Fragment */
fun Fragment.startActivity(
    clsStr: String,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null,
) {
    val intent = Intent(
        requireContext(), Class.forName(clsStr)
    ).apply { flags?.let { addFlags(it) } }
    startActivity(
        if (extras == null) {
            intent
        } else {
            extras(intent)
        }, options
    )
}
