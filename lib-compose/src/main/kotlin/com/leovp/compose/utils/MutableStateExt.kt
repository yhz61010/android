@file:Suppress("unused")

package com.leovp.compose.utils

import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Author: Michael Leo
 * Date: 2025/8/15 15:42
 */

fun <T> SnapshotStateList<T>.replaceAll(newList: List<T>) {
    clear()
    addAll(newList)
}
