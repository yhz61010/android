package com.leovp.demo.basiccomponents.examples.pref

import android.content.Context
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.pref.base.AbsPref
import com.tencent.mmkv.MMKV

/**
 * Author: Michael Leo
 * Date: 20-12-10 下午1:47
 */
class MMKVPref(ctx: Context) : AbsPref() {
    init {
        val mmkvRootDir: String = MMKV.initialize(ctx)
        LogContext.log.i(ITAG, "mmkvRootDir=$mmkvRootDir")
    }

    private val mmkv = MMKV.defaultMMKV(MMKV.SINGLE_PROCESS_MODE, "sk-leo-crypt-key")

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

    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = mmkv.decodeStringSet(key, default)

    override fun cleanAll() {
        mmkv.clearAll()
    }
}
