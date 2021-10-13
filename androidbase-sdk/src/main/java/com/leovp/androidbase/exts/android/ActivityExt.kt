package com.leovp.androidbase.exts.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import kotlin.reflect.KClass

fun Activity.ignoreDuplicateStartSplash(): Boolean {
    return if (this.intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT > 0) {
        this.finish()
        true
    } else false
}

// ============================================================================

/** Launch a Activity */
fun Context.startActivity(kcls: KClass<*>, flags: Int? = null, options: Bundle? = null) =
    this.startActivity(Intent(this, kcls.java).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity */
fun Context.startActivity(cls: Class<*>, flags: Int? = null, options: Bundle? = null) =
    this.startActivity(Intent(this, cls).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity */
fun Context.startActivity(clsStr: String, flags: Int? = null, options: Bundle? = null) =
    this.startActivity(Intent(this, Class.forName(clsStr)).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity */
@JvmOverloads
fun Activity.startActivityForResult(kcls: KClass<*>, requestCode: Int, flags: Int? = null, options: Bundle? = null) =
    startActivityForResult(Intent(this, kcls.java).apply { flags?.let { addFlags(it) } }, requestCode, options)

/** Launch a Activity */
@JvmOverloads
fun Activity.startActivityForResult(cls: Class<*>, requestCode: Int, flags: Int? = null, options: Bundle? = null) =
    startActivityForResult(Intent(this, cls).apply { flags?.let { addFlags(it) } }, requestCode, options)

/** Launch a Activity */
@JvmOverloads
fun Activity.startActivityForResult(clsStr: String, requestCode: Int, flags: Int? = null, options: Bundle? = null) =
    startActivityForResult(Intent(this, Class.forName(clsStr)).apply { flags?.let { addFlags(it) } }, requestCode, options)

// ----------

/** Launch a Activity in Fragment */
fun Fragment.startActivity(kcls: KClass<*>, flags: Int? = null, options: Bundle? = null) =
    this.startActivity(Intent(requireContext(), kcls.java).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity in Fragment */
fun Fragment.startActivity(cls: Class<*>, flags: Int? = null, options: Bundle? = null) =
    this.startActivity(Intent(requireContext(), cls).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity in Fragment */
fun Fragment.startActivity(clsStr: String, flags: Int? = null, options: Bundle? = null) =
    this.startActivity(Intent(requireContext(), Class.forName(clsStr)).apply { flags?.let { addFlags(it) } }, options)

/** Launch a Activity in Fragment */
@JvmOverloads
fun Fragment.startActivityForResult(kcls: KClass<*>, requestCode: Int, options: Bundle? = null) =
    startActivityForResult(Intent(requireContext(), kcls.java), requestCode, options)

/** Launch a Activity in Fragment */
@JvmOverloads
fun Fragment.startActivityForResult(cls: Class<*>, requestCode: Int, options: Bundle? = null) =
    startActivityForResult(Intent(requireContext(), cls), requestCode, options)

/** Launch a Activity in Fragment */
@JvmOverloads
fun Fragment.startActivityForResult(clsStr: String, requestCode: Int, options: Bundle? = null) =
    startActivityForResult(Intent(requireContext(), Class.forName(clsStr)), requestCode, options)

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
@JvmOverloads
fun Fragment.startAppDetailSettingForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.requireContext().packageUri), requestCode, options)
}

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
@JvmOverloads
fun Fragment.startAppStorageSettingsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, this.requireContext().packageUri), requestCode, options)
}

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
@JvmOverloads
fun Fragment.startManageDrawOverlaysPermission(requestCode: Int, options: Bundle? = null) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, this.requireContext().packageUri), requestCode, options)
    }
}

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
@JvmOverloads
fun Fragment.startUnitySettingPage(action: String, requestCode: Int, options: Bundle? = null) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActivityForResult(Intent(action, this.requireContext().packageUri), requestCode, options)
    }
}

// --------------------------------------------------