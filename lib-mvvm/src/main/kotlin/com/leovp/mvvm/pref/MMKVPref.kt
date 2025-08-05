package com.leovp.mvvm.pref

import android.content.Context
import com.leovp.log.base.i
import com.leovp.pref.base.AbsPref
import com.tencent.mmkv.MMKV

/**
 * Author: Michael Leo
 * Date: 2025/3/24 08:48
 */
@Suppress("TooManyFunctions")
class MMKVPref(ctx: Context) : AbsPref() {
    init {
        val mmkvRootDir: String = MMKV.initialize(ctx)
        i("MMKV") { "mmkvRootDir=$mmkvRootDir" }
    }

    private val mmkv = MMKV.defaultMMKV()

    @Synchronized
    override fun put(key: String, v: Int) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun put(key: String, v: Long) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun put(key: String, v: Boolean) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun put(key: String, v: Float) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun put(key: String, v: String?) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun putSet(key: String, v: Set<String>?) {
        mmkv.encode(key, v)
    }

    override fun getInt(key: String, default: Int) = mmkv.decodeInt(key, default)

    override fun getLong(key: String, default: Long) = mmkv.decodeLong(key, default)

    override fun getBool(key: String, default: Boolean) = mmkv.decodeBool(key, default)

    override fun getFloat(key: String, default: Float) = mmkv.decodeFloat(key, default)

    override fun getString(key: String, default: String?): String? = mmkv.decodeString(key, default)

    override fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        mmkv.decodeStringSet(key, default)
}
