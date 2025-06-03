@file:Suppress("unused")

package com.leovp.androidbase.exts.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.leovp.android.exts.packageUri
import kotlin.reflect.KClass

fun Activity.ignoreDuplicateStartSplash(): Boolean {
    return if (this.intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT > 0) {
        this.finish()
        true
    } else {
        false
    }
}

// ============================================================================

/** Launch a Activity */
fun Context.startActivity(
    cls: Class<*>,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) {
    val intent = Intent(this, cls).apply { flags?.let { addFlags(it) } }
    this.startActivity(if (extras == null) intent else extras(intent), options)
}

/** Launch a Activity */
fun Context.startActivity(
    kcls: KClass<*>,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) = startActivity(kcls.java, extras, flags, options)

/** Launch a Activity */
fun Context.startActivity(
    clsStr: String,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) = startActivity(Class.forName(clsStr), extras, flags, options)

/** Launch a Activity */
inline fun <reified T : Context> Context.startActivity(
    noinline extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) {
    startActivity(T::class.java, extras, flags, options)
}

// --------------------

/** Launch a Activity in Fragment */
fun Fragment.startActivity(
    cls: Class<*>,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) {
    val intent = Intent(requireContext(), cls).apply { flags?.let { addFlags(it) } }
    startActivity(if (extras == null) intent else extras(intent), options)
}

/** Launch a Activity in Fragment */
fun Fragment.startActivity(
    kcls: KClass<*>,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) = startActivity(kcls.java, extras, flags, options)

/** Launch a Activity in Fragment */
fun Fragment.startActivity(
    clsStr: String,
    extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) = startActivity(Class.forName(clsStr), extras, flags, options)

/** Launch a Activity in Fragment */
inline fun <reified T : Fragment> Fragment.startActivity(
    noinline extras: ((intent: Intent) -> Intent)? = null,
    flags: Int? = null,
    options: Bundle? = null
) {
    startActivity(T::class.java, extras, flags, options)
}

// -----

/**
 * Launch a Activity
 *
 * Attention:
 * According to [Official document](https://developer.android.com/reference/android/content/Intent.html#FLAG_ACTIVITY_NEW_TASK):
 *
 * > This flag can not be used when the caller is requesting a result from the activity being launched.
 *
 * The flag `Intent.FLAG_ACTIVITY_NEW_TASK` is incompatible with `startActivityForResult`.
 * So any activity that is started by an `Intent` to which the flag "FLAG_ACTIVITY_NEW_TASK" was added can not return a result.
 *
 * @see <a href="https://stackoverflow.com/a/48177487">Using `startActivityForResult` with Flags FLAG_ACTIVITY_NEW_TASK</a>
 */
// @JvmOverloads
// @Deprecated(
//    "Using BetterActivityResult#registerForActivityResult and BetterActivityResult#launch instead",
//    ReplaceWith("BetterActivityResult", "com.leovp.androidbase.utils.ui.BetterActivityResult")
// )
// fun Activity.startActivityForResult(cls: Class<*>, requestCode: Int, extras: ((intent: Intent) -> Intent)? = null, flags: Int? = null, options: Bundle? = null) {
//    val intent = Intent(this, cls).apply { flags?.let { addFlags(it) } }
//    startActivityForResult(if (extras == null) intent else extras(intent), requestCode, options)
// }

/**
 * Launch a Activity
 *
 * Attention:
 * According to [Official document](https://developer.android.com/reference/android/content/Intent.html#FLAG_ACTIVITY_NEW_TASK):
 *
 * > This flag can not be used when the caller is requesting a result from the activity being launched.
 *
 * The flag `Intent.FLAG_ACTIVITY_NEW_TASK` is incompatible with `startActivityForResult`.
 * So any activity that is started by an `Intent` to which the flag "FLAG_ACTIVITY_NEW_TASK" was added can not return a result.
 *
 * @see <a href="https://stackoverflow.com/a/48177487">Using `startActivityForResult` with Flags FLAG_ACTIVITY_NEW_TASK</a>
 */
// @JvmOverloads
// @Deprecated(
//    "Using BetterActivityResult#registerForActivityResult and BetterActivityResult#launch instead",
//    ReplaceWith("BetterActivityResult", "com.leovp.androidbase.utils.ui.BetterActivityResult")
// )
// fun Activity.startActivityForResult(kcls: KClass<*>, requestCode: Int, extras: ((intent: Intent) -> Intent)? = null, flags: Int? = null, options: Bundle? = null) =
//    startActivityForResult(kcls.java, requestCode, extras, flags, options)

/**
 * Launch a Activity
 *
 * Attention:
 * According to [Official document](https://developer.android.com/reference/android/content/Intent.html#FLAG_ACTIVITY_NEW_TASK):
 *
 * > This flag can not be used when the caller is requesting a result from the activity being launched.
 *
 * The flag `Intent.FLAG_ACTIVITY_NEW_TASK` is incompatible with `startActivityForResult`.
 * So any activity that is started by an `Intent` to which the flag "FLAG_ACTIVITY_NEW_TASK" was added can not return a result.
 *
 * @see <a href="https://stackoverflow.com/a/48177487">Using `startActivityForResult` with Flags FLAG_ACTIVITY_NEW_TASK</a>
 */
// @JvmOverloads
// @Deprecated(
//    "Using BetterActivityResult#registerForActivityResult and BetterActivityResult#launch instead",
//    ReplaceWith("BetterActivityResult", "com.leovp.androidbase.utils.ui.BetterActivityResult")
// )
// fun Activity.startActivityForResult(clsStr: String, requestCode: Int, extras: ((intent: Intent) -> Intent)? = null, flags: Int? = null, options: Bundle? = null) =
//    startActivityForResult(Class.forName(clsStr), requestCode, extras, flags, options)

// -----

/**
 * Launch a Activity in Fragment
 *
 * Attention:
 * According to [Official document](https://developer.android.com/reference/android/content/Intent.html#FLAG_ACTIVITY_NEW_TASK):
 *
 * > This flag can not be used when the caller is requesting a result from the activity being launched.
 *
 * The flag `Intent.FLAG_ACTIVITY_NEW_TASK` is incompatible with `startActivityForResult`.
 * So any activity that is started by an `Intent` to which the flag "FLAG_ACTIVITY_NEW_TASK" was added can not return a result.
 *
 * @see <a href="https://stackoverflow.com/a/48177487">Using `startActivityForResult` with Flags FLAG_ACTIVITY_NEW_TASK</a>
 */
// @JvmOverloads
// @Deprecated(
//    "Using BetterActivityResult#registerForActivityResult and BetterActivityResult#launch instead",
//    ReplaceWith("BetterActivityResult", "com.leovp.androidbase.utils.ui.BetterActivityResult")
// )
// fun Fragment.startActivityForResult(cls: Class<*>, requestCode: Int, extras: ((intent: Intent) -> Intent)? = null, flags: Int? = null, options: Bundle? = null) {
//    val intent = Intent(requireContext(), cls).apply { flags?.let { addFlags(it) } }
//    startActivityForResult(if (extras == null) intent else extras(intent), requestCode, options)
// }

/**
 * Launch a Activity in Fragment
 *
 * Attention:
 * According to [Official document](https://developer.android.com/reference/android/content/Intent.html#FLAG_ACTIVITY_NEW_TASK):
 *
 * > This flag can not be used when the caller is requesting a result from the activity being launched.
 *
 * The flag `Intent.FLAG_ACTIVITY_NEW_TASK` is incompatible with `startActivityForResult`.
 * So any activity that is started by an `Intent` to which the flag "FLAG_ACTIVITY_NEW_TASK" was added can not return a result.
 *
 * @see <a href="https://stackoverflow.com/a/48177487">Using `startActivityForResult` with Flags FLAG_ACTIVITY_NEW_TASK</a>
 */
// @JvmOverloads
// @Deprecated(
//    "Using BetterActivityResult#registerForActivityResult and BetterActivityResult#launch instead",
//    ReplaceWith("BetterActivityResult", "com.leovp.androidbase.utils.ui.BetterActivityResult")
// )
// fun Fragment.startActivityForResult(kcls: KClass<*>, requestCode: Int, extras: ((intent: Intent) -> Intent)? = null, flags: Int? = null, options: Bundle? = null) =
//    startActivityForResult(kcls.java, requestCode, extras, flags, options)

/**
 * Launch a Activity in Fragment
 *
 * Attention:
 * According to [Official document](https://developer.android.com/reference/android/content/Intent.html#FLAG_ACTIVITY_NEW_TASK):
 *
 * > This flag can not be used when the caller is requesting a result from the activity being launched.
 *
 * The flag `Intent.FLAG_ACTIVITY_NEW_TASK` is incompatible with `startActivityForResult`.
 * So any activity that is started by an `Intent` to which the flag "FLAG_ACTIVITY_NEW_TASK" was added can not return a result.
 *
 * @see <a href="https://stackoverflow.com/a/48177487">Using `startActivityForResult` with Flags FLAG_ACTIVITY_NEW_TASK</a>
 */
// @JvmOverloads
// @Deprecated(
//    "Using BetterActivityResult#registerForActivityResult and BetterActivityResult#launch instead",
//    ReplaceWith("BetterActivityResult", "com.leovp.androidbase.utils.ui.BetterActivityResult")
// )
// fun Fragment.startActivityForResult(clsStr: String, requestCode: Int, extras: ((intent: Intent) -> Intent)? = null, flags: Int? = null, options: Bundle? = null) =
//    startActivityForResult(Class.forName(clsStr), requestCode, extras, flags, options)

// ============================================================
// ====== Open Settings ==========
// ============================================================

/**
 * Launch applications detail page
 *
 * Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Context.startAppDetailSetting() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.packageUri))
}

/**
 * Launch applications detail page
 *
 * Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Fragment.startAppDetailSetting() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.requireContext().packageUri))
}

/**
 * Launch applications detail page
 *
 * Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
@JvmOverloads
fun Activity.startAppDetailSettingForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.packageUri), requestCode, options)
}

/**
 * Launch applications detail page
 *
 * Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
// @JvmOverloads
// fun Fragment.startAppDetailSettingForResult(requestCode: Int, options: Bundle? = null) {
//    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.requireContext().packageUri), requestCode, options)
// }

// --------------------------------------------------

/**
 * Launch internal storage settings page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Context.startAppStorageSettings() {
    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, this.packageUri))
}

/**
 * Launch internal storage settings page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Fragment.startAppStorageSettings() {
    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, this.requireContext().packageUri))
}

/**
 * Launch internal storage settings page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
@JvmOverloads
fun Activity.startAppStorageSettingsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, this.packageUri), requestCode, options)
}

/**
 * Launch internal storage settings page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
// @JvmOverloads
// fun Fragment.startAppStorageSettingsForResult(requestCode: Int, options: Bundle? = null) {
//    startActivityForResult(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, this.requireContext().packageUri), requestCode, options)
// }

// --------------------------------------------------

/**
 * Launch overlays permission page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Context.startManageDrawOverlaysPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, this.packageUri))
    }
}

/**
 * Launch overlays permission page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Fragment.startManageDrawOverlaysPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, this.requireContext().packageUri))
    }
}

/**
 * Launch overlays permission page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
@JvmOverloads
fun Activity.startManageDrawOverlaysPermission(requestCode: Int, options: Bundle? = null) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, this.packageUri), requestCode, options)
    }
}

/**
 * Launch overlays permission page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
// @JvmOverloads
// fun Fragment.startManageDrawOverlaysPermission(requestCode: Int, options: Bundle? = null) {
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//        startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, this.requireContext().packageUri), requestCode, options)
//    }
// }

// --------------------------------------------------

/**
 * Launch unity setting page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Context.startUnitySettingPage(action: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivity(Intent(action, this.packageUri))
    }
}

/**
 * Launch unity setting page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
fun Fragment.startUnitySettingPage(action: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivity(Intent(action, this.requireContext().packageUri))
    }
}

/**
 * Launch unity setting page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
@JvmOverloads
fun Activity.startUnitySettingPage(action: String, requestCode: Int, options: Bundle? = null) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivityForResult(Intent(action, this.packageUri), requestCode, options)
    }
}

/**
 * Launch unity setting page
 *
 *  Attention:
 * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
 */
// @JvmOverloads
// fun Fragment.startUnitySettingPage(action: String, requestCode: Int, options: Bundle? = null) {
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//        startActivityForResult(Intent(action, this.requireContext().packageUri), requestCode, options)
//    }
// }

// --------------------------------------------------
