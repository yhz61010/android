package com.leovp.leoandroidbaseutil

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.pref.LPref
import com.leovp.androidbase.utils.pref.PrefContext
import com.leovp.log_sdk.LLog
import com.leovp.log_sdk.LogContext
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Author: Michael Leo
 * Date: 2022/2/8 09:32
 */
@RunWith(RobolectricTestRunner::class)
@PrepareForTest(Log::class)
@Config(shadows = [ShadowLog::class])
class Pref4Kotlin {
    private val context: Application = ApplicationProvider.getApplicationContext()

    inner class NullObject

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        LogContext.setLogImp(LLog("LEO"))
    }

    @Test
    fun defaultPref() {
        PrefContext.setPrefImp(LPref(context))

        PrefContext.pref.put("string", "this is a string")
        PrefContext.pref.put("boolean", true)
        PrefContext.pref.put("long", 1234567L)
        PrefContext.pref.put("int", 10)
        PrefContext.pref.put("float", 3.14f)
        PrefContext.pref.put("object_int", mapOf("k_int1" to 1, "k_int2" to 2))
        PrefContext.pref.put("object_float", mapOf("k_float1" to 11.1f, "k_float2" to 22.2f))
        val nullString: String? = null
        PrefContext.pref.put("null_str", nullString)
        val nullObj: NullObject? = null
        PrefContext.pref.put("null_obj", nullObj)
        PrefContext.pref.put("pure_null", null)
        PrefContext.pref.putSet("set", setOf("s1", "s2"))

        Assert.assertEquals("this is a string", PrefContext.pref.getString("string", null))
        Assert.assertEquals("this is a string", PrefContext.pref.getString("string"))
        Assert.assertTrue(PrefContext.pref.get("boolean", false))
        Assert.assertEquals(1234567L, PrefContext.pref.get("long", 0L))
        Assert.assertEquals(10, PrefContext.pref.get<Int>("int", 0))
        Assert.assertEquals(3.14f, PrefContext.pref.get("float", 0f))
        val mapIntObj: Map<String, Int>? = PrefContext.pref.getObject("object_int")
        LogContext.log.d("mapIntObj=${mapIntObj.toJsonString()}")
        Assert.assertEquals(mapOf("k_int1" to 1, "k_int2" to 2), mapIntObj)
        val mapFloatObj: Map<String, Float>? = PrefContext.pref.getObject("object_float")
        LogContext.log.d("mapFloatObj=${mapFloatObj.toJsonString()}")
        Assert.assertEquals(mapOf("k_float1" to 11.1f, "k_float2" to 22.2f), mapFloatObj)
        Assert.assertEquals("<null string>", PrefContext.pref.getString("null_str", "<null string>"))
        Assert.assertEquals("<null object>", PrefContext.pref.getString("null_obj", "<null object>"))
        Assert.assertNull(PrefContext.pref.getString("null_str"))
        Assert.assertNull(PrefContext.pref.getString("null_obj"))
        Assert.assertNull(PrefContext.pref.getString("null_str", null))
        Assert.assertNull(PrefContext.pref.getString("null_obj", null))
        Assert.assertNull(PrefContext.pref.getObject<NullObject?>("null_obj"))
        Assert.assertNull(PrefContext.pref.getObject<Any?>("pure_null"))
        Assert.assertNull(PrefContext.pref.getString("pure_null", null))
        Assert.assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("set", emptySet()))
        Assert.assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("set"))
    }
}