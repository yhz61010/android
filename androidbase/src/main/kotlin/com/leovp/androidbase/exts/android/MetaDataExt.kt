@file:Suppress("unused")

package com.leovp.androidbase.exts.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import com.leovp.android.exts.getCompatContextInfo

/**
 * Author: Michael Leo
 * Date: 2022/10/20 13:51
 */

/**
 * Get meta data in specified context.
 * The context can be either Activity, Application, Service or Broadcast.
 *
 * https://developer.android.com/guide/topics/manifest/meta-data-element?hl=zh-cn
 *
 * @param key   The meta data key
 * @return The value of meta data
 */
inline fun <reified T : Any> Context.getMetaData(key: String): T? {
    // ActivityInfo
    // ApplicationInfo
    // ServiceInfo
    val info: PackageItemInfo = getCompatContextInfo(PackageManager.GET_META_DATA)
    return genericMetaDataResult(info, key)
}

/**
 * Get meta data with BroadcastReceiver.
 *
 * https://developer.android.com/guide/topics/manifest/meta-data-element?hl=zh-cn
 *
 * @param key   The meta data key
 * @return The value of meta data
 */
inline fun <reified T : Any> BroadcastReceiver.getMetaData(ctx: Context, key: String): T? {
    // ActivityInfo
    val info: ActivityInfo = getCompatContextInfo(ctx, PackageManager.GET_META_DATA)
    return genericMetaDataResult(info, key)
}

inline fun <reified T : Any> genericMetaDataResult(info: PackageItemInfo, key: String): T? {
    return when (T::class) {
        String::class -> info.metaData.getString(key) as? T
        Int::class -> info.metaData.getInt(key) as? T
        Boolean::class -> info.metaData.getBoolean(key) as? T
        Float::class -> info.metaData.getFloat(key) as? T
        else -> null
    }
}
