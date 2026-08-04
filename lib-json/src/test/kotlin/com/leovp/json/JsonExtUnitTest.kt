package com.leovp.json

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test

private data class JsonSample(val a: Int, val b: String)

/**
 * Author: Michael Leo
 *
 * Unit tests for JSON parse/serialize helpers.
 */
class JsonExtUnitTest {

    @Test
    fun `toObject parses valid json`() {
        val obj = """{"a":1,"b":"x"}""".toObject<JsonSample>()

        obj?.a shouldBeEqualTo 1
        obj?.b shouldBeEqualTo "x"
    }

    @Test
    fun `toObject returns null on malformed json`() {
        val obj = """{"a": not-json""".toObject<JsonSample>()

        obj.shouldBeNull()
    }

    @Test
    fun `toJsonString serializes an object`() {
        JsonSample(1, "x").toJsonString() shouldBeEqualTo """{"a":1,"b":"x"}"""
    }

    @Test
    fun `toObject preserves generic list element type`() {
        // Regression for remediation H18: a reified List<JsonSample> used to deserialize into
        // LinkedTreeMap elements because T::class.java erased the type argument, so accessing a
        // typed field threw ClassCastException at runtime.
        val items: List<JsonSample>? = """[{"a":1,"b":"x"},{"a":2,"b":"y"}]""".toObject()

        items?.size shouldBeEqualTo 2
        items!![0] shouldBeInstanceOf JsonSample::class
        items[0].a shouldBeEqualTo 1
        items[1].b shouldBeEqualTo "y"
    }
}
