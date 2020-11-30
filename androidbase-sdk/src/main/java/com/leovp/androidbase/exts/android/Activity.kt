package com.leovp.androidbase.exts.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import kotlin.reflect.KClass

/** Launch a Activity */
fun Context.startActivity(cls: KClass<*>) = this.startActivity(Intent(this, cls.java))

/** Launch a Activity */
fun Fragment.startActivity(cls: KClass<*>) = this.startActivity(Intent(context, cls.java))

/** Launch a Activity */
@JvmOverloads
fun Activity.startActivityForResult(cls: KClass<*>, requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(this, cls.java), requestCode, options)
}

/** Launch a Activity */
@JvmOverloads
fun Fragment.startActivityForResult(cls: KClass<*>, requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(context, cls.java), requestCode, options)
}

/** Launch applications detail page */
fun Context.startAppDetails() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.uri))

/** Launch applications detail page */
fun Fragment.startAppDetails() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.requireContext().uri))
}

/** Launch applications detail page */
@JvmOverloads
fun Activity.startAppDetailsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.uri), requestCode, options)
}

/** Launch applications detail page */
@JvmOverloads
fun Fragment.startAppDetailsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.requireContext().uri), requestCode, options)
}

/** Launch internal storage settings page */
fun Context.startStorageSettings() {
    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
}

/** Launch internal storage settings page */
fun Fragment.startStorageSettings() {
    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
}

/** Launch internal storage settings page */
@JvmOverloads
fun Activity.startStorageSettingsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), requestCode, options)
}

/** Launch internal storage settings page */
@JvmOverloads
fun Fragment.startStorageSettingsForResult(requestCode: Int, options: Bundle? = null) {
    startActivityForResult(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), requestCode, options)
}