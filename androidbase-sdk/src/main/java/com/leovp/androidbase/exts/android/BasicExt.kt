@file:Suppress("unused")

package com.leovp.androidbase.exts.android

import android.os.Bundle

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
inline fun <reified T> Bundle.getDataOrNull(key: String): T? = getSerializable(key) as? T