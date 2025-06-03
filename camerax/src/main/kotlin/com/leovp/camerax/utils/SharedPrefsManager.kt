package com.leovp.camerax.utils

import android.content.Context
import com.leovp.android.exts.sharedPrefs
import com.leovp.kotlin.utils.SingletonHolder

class SharedPrefsManager private constructor(context: Context) {
    companion object : SingletonHolder<SharedPrefsManager, Context>(::SharedPrefsManager)

    private val preferences = context.sharedPrefs("com-leovp-camerax")

    // ==========

    fun putBoolean(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()

    fun putInt(key: String, value: Int) = preferences.edit().putInt(key, value).apply()

    fun putString(key: String, value: String) = preferences.edit().putString(key, value).apply()

    // ==========

    fun getBoolean(key: String, defValue: Boolean) = preferences.getBoolean(key, defValue)

    fun getInt(key: String, defValue: Int) = preferences.getInt(key, defValue)

    fun getString(key: String, defValue: String) = preferences.getString(key, defValue)
}
