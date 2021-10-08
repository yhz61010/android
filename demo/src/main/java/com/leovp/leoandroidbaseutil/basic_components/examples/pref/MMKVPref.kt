package com.leovp.leoandroidbaseutil.basic_components.examples.pref

import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.exts.kotlin.toObject
import com.leovp.androidbase.utils.pref.base.IPref
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import com.tencent.mmkv.MMKV
import java.io.File

/**
 * Author: Michael Leo
 * Date: 20-12-10 下午1:47
 */
class MMKVPref : IPref {
    init {
        val mmkvRootDir: String = MMKV.initialize(app.filesDir.absolutePath + File.separator + "mmkv")
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
    override fun put(key: String, v: Float) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun put(key: String, v: Boolean) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun put(key: String, v: String?) {
        mmkv.encode(key, v)
    }

    @Synchronized
    override fun putObject(key: String, v: Any?) {
        mmkv.encode(key, v?.toJsonString())
    }

    override fun putSet(key: String, v: Set<String>?) {
        mmkv.encode(key, v)
    }

    override fun getInt(key: String, default: Int) = mmkv.decodeInt(key, default)

    override fun getLong(key: String, default: Long) = mmkv.decodeLong(key, default)

    override fun getBool(key: String, default: Boolean) = mmkv.decodeBool(key, default)

    override fun getFloat(key: String, default: Float) = mmkv.decodeFloat(key, default)

    override fun getString(key: String, default: String?): String? = mmkv.decodeString(key, default)

    override fun <T> getObject(key: String, clazz: Class<T>) = getString(key, null)?.toObject(clazz)

    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = mmkv.decodeStringSet(key, default)
}