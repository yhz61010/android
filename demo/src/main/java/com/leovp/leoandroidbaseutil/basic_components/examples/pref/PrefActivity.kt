package com.leovp.leoandroidbaseutil.basic_components.examples.pref

import android.os.Bundle
import com.leovp.androidbase.utils.pref.LPref
import com.leovp.androidbase.utils.pref.PrefContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class PrefActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pref)

        defaultPref()
        mmkvPref()
    }

    private fun defaultPref() {
        PrefContext.setPrefImp(LPref())

        PrefContext.pref.put("string", "this is a string")
        PrefContext.pref.put("boolean", true)
        PrefContext.pref.put("long", 1234567L)
        PrefContext.pref.put("int", 10)
        PrefContext.pref.put("float", 3.14f)
        PrefContext.pref.putObject("object", mapOf("k1" to 1, "k2" to 2))
        PrefContext.pref.putSet("set", setOf("s1", "s2"))

        LogContext.log.d(ITAG, "pref string=${PrefContext.pref.getString("string", null)}")
        LogContext.log.d(ITAG, "pref boolean=${PrefContext.pref.getBool("boolean", false)}")
        LogContext.log.d(ITAG, "pref long=${PrefContext.pref.getLong("long", 0L)}")
        LogContext.log.d(ITAG, "pref int=${PrefContext.pref.getInt("int", 0)}")
        LogContext.log.d(ITAG, "pref float=${PrefContext.pref.getFloat("float", 0f)}")
        LogContext.log.d(ITAG, "pref object=${PrefContext.pref.getObject("object", Map::class.java)}")
        LogContext.log.d(ITAG, "pref set=${PrefContext.pref.getStringSet("set", emptySet())}")

        LogContext.log.d(ITAG, "-----")

        LogContext.log.d(ITAG, "pref string=${PrefContext.pref.getString("string")}")
        LogContext.log.d(ITAG, "pref boolean=${PrefContext.pref.getBool("boolean")}")
        LogContext.log.d(ITAG, "pref long=${PrefContext.pref.getLong("long")}")
        LogContext.log.d(ITAG, "pref int=${PrefContext.pref.getInt("int")}")
        LogContext.log.d(ITAG, "pref float=${PrefContext.pref.getFloat("float")}")
        LogContext.log.d(ITAG, "pref set=${PrefContext.pref.getStringSet("set")}")
    }

    private fun mmkvPref() {
        PrefContext.setPrefImp(MMKVPref())

        PrefContext.pref.put("mmkv_string", "mmkv_this is a string")
        PrefContext.pref.put("mmkv_boolean", true)
        PrefContext.pref.put("mmkv_long", 1234567L)
        PrefContext.pref.put("mmkv_int", 10)
        PrefContext.pref.put("mmkv_float", 3.14f)
        PrefContext.pref.putObject("mmkv_object", mapOf("mmkv_k1" to 1, "mmkv_k2" to 2))
        PrefContext.pref.putSet("mmkv_set", setOf("mmkv_s1", "mmkv_s2"))

        LogContext.log.d(ITAG, "mmkv pref string=${PrefContext.pref.getString("mmkv_string", null)}")
        LogContext.log.d(ITAG, "mmkv pref boolean=${PrefContext.pref.getBool("mmkv_boolean", false)}")
        LogContext.log.d(ITAG, "mmkv pref long=${PrefContext.pref.getLong("mmkv_long", 0L)}")
        LogContext.log.d(ITAG, "mmkv pref int=${PrefContext.pref.getInt("mmkv_int", 0)}")
        LogContext.log.d(ITAG, "mmkv pref float=${PrefContext.pref.getFloat("mmkv_float", 0f)}")
        LogContext.log.d(ITAG, "mmkv pref object=${PrefContext.pref.getObject("mmkv_object", Map::class.java)}")
        LogContext.log.d(ITAG, "mmkv pref set=${PrefContext.pref.getStringSet("mmkv_set", emptySet())}")

        LogContext.log.d(ITAG, "-----")

        LogContext.log.d(ITAG, "mmkv pref string=${PrefContext.pref.getString("mmkv_string")}")
        LogContext.log.d(ITAG, "mmkv pref boolean=${PrefContext.pref.getBool("mmkv_boolean")}")
        LogContext.log.d(ITAG, "mmkv pref long=${PrefContext.pref.getLong("mmkv_long")}")
        LogContext.log.d(ITAG, "mmkv pref int=${PrefContext.pref.getInt("mmkv_int")}")
        LogContext.log.d(ITAG, "mmkv pref float=${PrefContext.pref.getFloat("mmkv_float")}")
        LogContext.log.d(ITAG, "mmkv pref set=${PrefContext.pref.getStringSet("mmkv_set")}")
    }
}