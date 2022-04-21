package com.leovp.camerax_sdk.utils

import android.content.Context
import com.leovp.lib_common_android.exts.sharedPrefs
import com.leovp.lib_common_kotlin.utils.SingletonHolder

class SharedPrefsManager private constructor(context: Context) {
    companion object : SingletonHolder<SharedPrefsManager, Context>(::SharedPrefsManager)

    private val preferences = context.sharedPrefs("CameraX")

    fun putBoolean(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()

    fun putInt(key: String, value: Int) = preferences.edit().putInt(key, value).apply()

    fun getBoolean(key: String, defValue: Boolean) = preferences.getBoolean(key, defValue)

    fun getInt(key: String, defValue: Int) = preferences.getInt(key, defValue)
}
