package com.leovp.pref.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AbsPrefTest {
    @Test
    fun `failed object serialization does not overwrite stored value`() {
        val pref = MemoryPref().apply { put("key", "existing") }

        pref.put("key", AbsPrefTest::class.java)

        assertEquals("existing", pref.getString("key", null))
    }

    private class MemoryPref : AbsPref() {
        private val values = mutableMapOf<String, Any?>()

        override fun put(key: String, v: Int) = store(key, v)
        override fun put(key: String, v: Long) = store(key, v)
        override fun put(key: String, v: Boolean) = store(key, v)
        override fun put(key: String, v: Float) = store(key, v)
        override fun put(key: String, v: String?) = store(key, v)
        override fun putSet(key: String, v: Set<String>?) = store(key, v)
        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
        override fun getBool(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun getFloat(key: String, default: Float) = values[key] as? Float ?: default
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, default: Set<String>?): Set<String>? =
            (values[key] as? Set<String>) ?: default

        private fun store(key: String, value: Any?) {
            values[key] = value
        }
    }
}
