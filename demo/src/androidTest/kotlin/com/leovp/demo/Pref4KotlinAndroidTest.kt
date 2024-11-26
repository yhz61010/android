package com.leovp.demo

import androidx.test.platform.app.InstrumentationRegistry
import com.leovp.demo.basiccomponents.examples.pref.MMKVPref
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.pref.PrefContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class Pref4KotlinAndroidTest {
    inner class NullObject

    @Test
    fun mmkvPref() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        PrefContext.setPrefImpl(MMKVPref(appContext))

        PrefContext.pref.put("mmkv_string", "this is a string")
        PrefContext.pref.put("mmkv_boolean", true)
        PrefContext.pref.put("mmkv_long", 1234567L)
        PrefContext.pref.put("mmkv_int", 10)
        PrefContext.pref.put("mmkv_float", 3.14f)
        PrefContext.pref.put("mmkv_object_int", mapOf("k_int1" to 1, "k_int2" to 2))
        PrefContext.pref.put("mmkv_object_float", mapOf("k_float1" to 11.1f, "k_float2" to 22.2f))
        val nullString: String? = null
        PrefContext.pref.put("mmkv_null_str", nullString)
        val nullObj: NullObject? = null
        PrefContext.pref.put("mmkv_null_obj", nullObj)
        PrefContext.pref.put("mmkv_pure_null", null)
        PrefContext.pref.putSet("mmkv_set", setOf("s1", "s2"))

        assertEquals("this is a string", PrefContext.pref.getString("mmkv_string", null))
        assertEquals("this is a string", PrefContext.pref.getString("mmkv_string"))
        assertEquals(true, PrefContext.pref.get("mmkv_boolean", false))
        assertEquals(1234567L, PrefContext.pref.get("mmkv_long", 0L))
        assertEquals(10, PrefContext.pref.get("mmkv_int", 0))
        assertEquals(3.14f, PrefContext.pref.get("mmkv_float", 0f))
        val mapIntObj: Map<String, Int>? = PrefContext.pref.getObject("mmkv_object_int")
        LogContext.log.d(ITAG, "mapIntObj=${mapIntObj.toJsonString()}")
        assertEquals(mapOf("k_int1" to 1, "k_int2" to 2), mapIntObj)
        val mapFloatObj: Map<String, Float>? = PrefContext.pref.getObject("mmkv_object_float")
        LogContext.log.d(ITAG, "mapFloatObj=${mapFloatObj.toJsonString()}")
        assertEquals(mapOf("k_float1" to 11.1f, "k_float2" to 22.2f), mapFloatObj)
        assertEquals("<null string>", PrefContext.pref.getString("mmkv_null_str", "<null string>"))
        assertEquals("<null object>", PrefContext.pref.getString("mmkv_null_obj", "<null object>"))
        assertNull(PrefContext.pref.getString("mmkv_null_str"))
        assertNull(PrefContext.pref.getString("mmkv_null_obj"))
        assertNull(PrefContext.pref.getString("mmkv_null_str", null))
        assertNull(PrefContext.pref.getString("mmkv_null_obj", null))
        assertNull(PrefContext.pref.getObject<NullObject?>("mmkv_null_obj"))
        assertNull(PrefContext.pref.getObject<Any?>("mmkv_pure_null"))
        assertNull(PrefContext.pref.getString("mmkv_pure_null", null))
        assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("mmkv_set", emptySet()))
        assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("mmkv_set"))
    }
}
