package com.leovp.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.leovp.demo.basiccomponents.examples.pref.MMKVPref
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.pref.PrefContext
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class Pref4KotlinAndroid {
    inner class NullObject

    @Test
    fun mmkvPref() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        PrefContext.setPrefImp(MMKVPref(appContext))

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

        Assert.assertEquals("this is a string", PrefContext.pref.getString("mmkv_string", null))
        Assert.assertEquals("this is a string", PrefContext.pref.getString("mmkv_string"))
        Assert.assertEquals(true, PrefContext.pref.get("mmkv_boolean", false))
        Assert.assertEquals(1234567L, PrefContext.pref.get("mmkv_long", 0L))
        Assert.assertEquals(10, PrefContext.pref.get<Int>("mmkv_int", 0))
        Assert.assertEquals(3.14f, PrefContext.pref.get("mmkv_float", 0f))
        val mapIntObj: Map<String, Int>? = PrefContext.pref.getObject("mmkv_object_int")
        LogContext.log.d("mapIntObj=${mapIntObj.toJsonString()}")
        Assert.assertEquals(mapOf("k_int1" to 1, "k_int2" to 2), mapIntObj)
        val mapFloatObj: Map<String, Float>? = PrefContext.pref.getObject("mmkv_object_float")
        LogContext.log.d("mapFloatObj=${mapFloatObj.toJsonString()}")
        Assert.assertEquals(mapOf("k_float1" to 11.1f, "k_float2" to 22.2f), mapFloatObj)
        Assert.assertEquals("<null string>", PrefContext.pref.getString("mmkv_null_str", "<null string>"))
        Assert.assertEquals("<null object>", PrefContext.pref.getString("mmkv_null_obj", "<null object>"))
        Assert.assertNull(PrefContext.pref.getString("mmkv_null_str"))
        Assert.assertNull(PrefContext.pref.getString("mmkv_null_obj"))
        Assert.assertNull(PrefContext.pref.getString("mmkv_null_str", null))
        Assert.assertNull(PrefContext.pref.getString("mmkv_null_obj", null))
        Assert.assertNull(PrefContext.pref.getObject<NullObject?>("mmkv_null_obj"))
        Assert.assertNull(PrefContext.pref.getObject<Any?>("mmkv_pure_null"))
        Assert.assertNull(PrefContext.pref.getString("mmkv_pure_null", null))
        Assert.assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("mmkv_set", emptySet()))
        Assert.assertEquals(setOf("s1", "s2"), PrefContext.pref.getStringSet("mmkv_set"))
    }
}
