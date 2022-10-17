package com.leovp.demo

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.leovp.json.toJsonString
import com.leovp.log.LLog
import com.leovp.log.LogContext
import com.leovp.pref.LPref
import com.leovp.pref.PrefContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
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
@Config(sdk = [32], shadows = [ShadowLog::class])
class Pref4KotlinTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    inner class NullObject

    @BeforeEach
    fun setUp() {
        stopKoin() // To remove 'A Koin Application has already been started'
        ShadowLog.stream = System.out
        LogContext.setLogImpl(LLog("LEO"))
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun defaultPref() {
        PrefContext.setPrefImpl(LPref(context))

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

        assertEquals("this is a string", PrefContext.pref.getString("string", null))
        assertEquals("this is a string", PrefContext.pref.getString("string"))
        assertTrue(PrefContext.pref.get("boolean", false))
        assertEquals(1234567L, PrefContext.pref.get("long", 0L))
        assertEquals(10, PrefContext.pref.get("int", 0))
        assertEquals(3.14f, PrefContext.pref.get("float", 0f))
        val mapIntObj: Map<String, Int>? = PrefContext.pref.getObject("object_int")
        LogContext.log.d("mapIntObj=${mapIntObj.toJsonString()}")
        assertEquals(mapOf("k_int1" to 1, "k_int2" to 2), mapIntObj)
        val mapFloatObj: Map<String, Float>? = PrefContext.pref.getObject("object_float")
        LogContext.log.d("mapFloatObj=${mapFloatObj.toJsonString()}")
        assertEquals(mapOf("k_float1" to 11.1f, "k_float2" to 22.2f), mapFloatObj)
        assertEquals("<null string>", PrefContext.pref.getString("null_str", "<null string>"))
        assertEquals("<null object>", PrefContext.pref.getString("null_obj", "<null object>"))
        assertNull(PrefContext.pref.getString("null_str"))
        assertNull(PrefContext.pref.getString("null_obj"))
        assertNull(PrefContext.pref.getString("null_str", null))
        assertNull(PrefContext.pref.getString("null_obj", null))
        assertNull(PrefContext.pref.getObject<NullObject?>("null_obj"))
        assertNull(PrefContext.pref.getObject<Any?>("pure_null"))
        assertNull(PrefContext.pref.getString("pure_null", null))
        assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("set", emptySet()))
        assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("set"))
    }
}
