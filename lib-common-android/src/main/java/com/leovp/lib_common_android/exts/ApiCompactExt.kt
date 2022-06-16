@file:Suppress("unused")

package com.leovp.lib_common_android.exts

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
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
 * private val uid1 = getCompatContextInfo<Context, ApplicationInfo>(ctx, PackageManager.GET_META_DATA).uid
 * private val uid2 = getCompatContextInfo<Activity, ApplicationInfo>(activityCtx, PackageManager.GET_META_DATA).uid
 * ```
 *
 * Example2:
 * ```
 * val info1: ActivityInfo = getCompatContextInfo(activityCtx, PackageManager.GET_META_DATA)
 * val info2: ServiceInfo = getCompatContextInfo(serviceCtx, PackageManager.GET_META_DATA, clazz)
 * ```
 *
 * @param clazz This parameter is only valid when [ctx] is either [Service] or [android.content.BroadcastReceiver].
 *
 * @return Return one of the following type:
 * - ActivityInfo for `Activity` and `BroadcastReceiver` context
 * - ApplicationInfo for `Application` context
 * - ServiceInfo for `Service` context
 */
inline fun <reified T : Context, reified O : PackageItemInfo> getCompatContextInfo(ctx: T,
    value: Int,
    clazz: Class<*>? = null): O {
    when (ctx) {
        is Activity    -> return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.packageManager.getActivityInfo(ctx.componentName,
                PackageManager.ComponentInfoFlags.of(value.toLong()))
        } else {
            @Suppress("DEPRECATION") ctx.getPackageManager()
                .getActivityInfo(ctx.componentName, value)
        } as O // ActivityInfo
        is Application -> return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.getPackageManager()
                .getApplicationInfo(ctx.getPackageName(),
                    PackageManager.ApplicationInfoFlags.of(value.toLong()))
        } else {
            @Suppress("DEPRECATION") ctx.getPackageManager()
                .getApplicationInfo(ctx.getPackageName(), value)
        } as O // ApplicationInfo
        is Service     -> {
            val cn = ComponentName(ctx, clazz!!)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.getPackageManager()
                    .getServiceInfo(cn, PackageManager.ComponentInfoFlags.of(value.toLong()))
            } else {
                @Suppress("DEPRECATION")
                ctx.getPackageManager().getServiceInfo(cn, value)
            } as O // ServiceInfo
        }
        else           -> { // BroadcastReceiver
            val cn = ComponentName(ctx, clazz!!)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getReceiverInfo(cn,
                    PackageManager.ComponentInfoFlags.of(value.toLong()))
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getReceiverInfo(cn, value)
            } as O // ActivityInfo
        }
    }
}
