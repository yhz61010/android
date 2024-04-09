@file:Suppress("unused")

package com.leovp.android.exts

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable

/**
 * Author: Michael Leo
 * Date: 2022/6/16 10:41
 */

/**
 * Example
 * ```kotlin
 * val bundle: Bundle = Bundle()
 * bundle.putSerializable("key", "Testing")
 * val value: String? = bundle.getSerializableOrNull("key")
 * ```
 */
inline fun <reified T : Serializable> Bundle.getSerializableOrNull(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializable(key) as? T
    }

inline fun <reified T : Parcelable> Bundle.getParcelableOrNull(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }

inline fun <reified T : Serializable> Intent.getSerializableExtraOrNull(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(key) as? T
    }

inline fun <reified T : Parcelable> Intent.getParcelableExtraOrNull(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }

// ==============================

/**
 * Example1:
 * ```
 * val uid = getCompatContextInfo<ApplicationInfo>(PackageManager.GET_META_DATA).uid
 * val info1: ActivityInfo = getCompatContextInfo(PackageManager.GET_META_DATA)
 * val info2: ServiceInfo = getCompatContextInfo(PackageManager.GET_META_DATA)
 * ```
 *
 * @return Return one of the following type:
 * - ActivityInfo for `Activity` context
 * - ApplicationInfo for `Application` context
 * - ServiceInfo for `Service` context
 */
inline fun <reified O : PackageItemInfo> Context.getCompatContextInfo(flags: Int): O {
    val pm = this.packageManager
    when (this) {
        is Activity -> return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getActivityInfo(this.componentName, PackageManager.ComponentInfoFlags.of(flags.toLong()))
        } else {
            pm.getActivityInfo(this.componentName, flags)
        } as O // ActivityInfo
        is Application -> return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(this.getPackageName(), PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            pm.getApplicationInfo(this.getPackageName(), flags)
        } as O // ApplicationInfo
        is Service -> {
            val cn = ComponentName(this, this::class.java)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getServiceInfo(cn, PackageManager.ComponentInfoFlags.of(flags.toLong()))
            } else {
                pm.getServiceInfo(cn, flags)
            } as O // ServiceInfo
        }
        else -> error("Invalid context. $this")
    }
}

/**
 * val ai: ActivityInfo = getCompatContextInfo(context, PackageManager.GET_META_DATA)
 *
 * @return Return `ActivityInfo` for `BroadcastReceiver`.
 */
fun BroadcastReceiver.getCompatContextInfo(ctx: Context, flags: Int): ActivityInfo {
    val pm = ctx.packageManager
    val cn = ComponentName(ctx, this::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getReceiverInfo(cn, PackageManager.ComponentInfoFlags.of(flags.toLong()))
    } else {
        pm.getReceiverInfo(cn, flags)
    }
}

// ==============================

fun Context.getCompactPackageArchiveInfo(archiveFilePath: String, flags: Int): PackageInfo? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.packageManager.getPackageArchiveInfo(
            archiveFilePath,
            PackageManager.PackageInfoFlags.of(flags.toLong())
        )
    } else {
        this.packageManager.getPackageArchiveInfo(archiveFilePath, flags)
    }
}

/**
 * Add the following permission in your `AndroidManifest.xml`:
 * ```xml
 * <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
 * ```
 *
 * https://stackoverflow.com/a/64946118/1685062
 */
@SuppressLint("QueryPermissionsNeeded")
fun Context.queryCompactIntentActivities(intent: Intent, flags: Int): List<ResolveInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(flags.toLong())
        )
    } else {
        this.packageManager.queryIntentActivities(intent, flags)
    }
}

fun Context.getCompactPackageInfo(packageName: String, flags: Int): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(flags.toLong())
        )
    } else {
        this.packageManager.getPackageInfo(packageName, flags)
    }
}

// ==============================

fun Service.stopCompactForeground(removeNotification: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (removeNotification) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(Service.STOP_FOREGROUND_DETACH)
        }
    } else {
        @Suppress("DEPRECATION")
        stopForeground(removeNotification)
    }
}
