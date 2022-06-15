@file:Suppress("unused")

package com.leovp.androidbase.exts.android

import android.os.Build
import android.os.Bundle
import java.io.Serializable

/**
 * Author: Michael Leo
 * Date: 2021/12/29 13:29
 */

/**
 * Example
 * ```kotlin
 * val bundle: Bundle = Bundle()
 * bundle.putSerializable("key", "Testing")
 * val value: String? = bundle.getDataOrNull("key")
 * ```
 */
inline fun <reified T : Serializable> Bundle.getDataOrNull(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializable(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializable(key) as? T
        }
