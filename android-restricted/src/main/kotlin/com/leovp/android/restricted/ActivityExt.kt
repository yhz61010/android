@file:Suppress("unused")

package com.leovp.android.restricted

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.leovp.log.LogContext

/**
 * Author: Michael Leo
 * Date: 2026/7/16 14:22
 */

private const val TAG = "ActivityExt"

/** Launch an Activity */
fun Context.startActivity(
    clsStr: String,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null,
) {
    val targetClass = runCatching { Class.forName(clsStr) }.getOrElse {
        LogContext.log.e(TAG, "Activity class not found: $clsStr", it)
        return
    }
    val intent = Intent(
        this, targetClass
    ).apply {
        flags?.let { addFlags(it) }
    }
    this.startActivity(
        if (extras == null) {
            intent
        } else {
            extras(intent)
        },
        options
    )
}

/** Launch an Activity in Fragment */
fun Fragment.startActivity(
    clsStr: String,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null,
) {
    val targetClass = runCatching { Class.forName(clsStr) }.getOrElse {
        LogContext.log.e(TAG, "Activity class not found: $clsStr", it)
        return
    }
    val intent = Intent(
        requireContext(), targetClass
    ).apply { flags?.let { addFlags(it) } }
    startActivity(
        if (extras == null) {
            intent
        } else {
            extras(intent)
        },
        options
    )
}
