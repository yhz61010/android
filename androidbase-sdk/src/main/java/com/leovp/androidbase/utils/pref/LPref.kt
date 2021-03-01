package com.leovp.androidbase.utils.pref

import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.sharedPrefs
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.exts.kotlin.toObject
import com.leovp.androidbase.utils.pref.base.IPref

/**
 * Author: Michael Leo
 * Date: 20-12-10 上午10:01
 */
class LPref(name: String = app.packageName) : IPref {
    private val pref = app.sharedPrefs(name)

    @Synchronized
    override fun put(key: String, v: Int) {
        pref.edit().apply {
            putInt(key, v)
            apply()
        }
    }

    @Synchronized
    override fun put(key: String, v: Long) {
        pref.edit().apply {
            putLong(key, v)
            apply()
        }
    }

    @Synchronized
    override fun put(key: String, v: Float) {
        pref.edit().apply {
            putFloat(key, v)
            apply()
        }
    }

    @Synchronized
    override fun put(key: String, v: Boolean) {
        pref.edit().apply {
            putBoolean(key, v)
            apply()
        }
    }

    @Synchronized
    override fun put(key: String, v: String) {
        pref.edit().apply {
            putString(key, v)
            apply()
        }
    }

    @Synchronized
    override fun put(key: String, v: Any) {
        pref.edit().apply {
            putString(key, v.toJsonString())
            apply()
        }
    }

    @Synchronized
    override fun put(key: String, v: Set<String>) {
        pref.edit().apply {
            putStringSet(key, v)
            apply()
        }
    }

    override fun getInt(key: String, default: Int) = pref.getInt(key, default)

    override fun getLong(key: String, default: Long) = pref.getLong(key, default)

    override fun getBool(key: String, default: Boolean) = pref.getBoolean(key, default)

    override fun getFloat(key: String, default: Float) = pref.getFloat(key, default)

    override fun getString(key: String, default: String?): String? = pref.getString(key, default)

    override fun <T> getObject(key: String, clazz: Class<T>): T? = getString(key, null)?.toObject(clazz)

    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = pref.getStringSet(key, default)
}