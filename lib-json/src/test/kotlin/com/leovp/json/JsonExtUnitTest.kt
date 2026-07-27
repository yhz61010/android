package com.leovp.json

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test

private data class JsonSample(val a: Int, val b: String)

/**
 * Author: Michael Leo
 *
 * Unit tests for remediation H17: JSON parse/serialize failures must not be silently swallowed —
 * they return null/"" but report through the optional onError hook rather than vanishing.
 */
class JsonExtUnitTest {

    @Test
    fun `toObject parses valid json`() {
        val obj = """{"a":1,"b":"x"}""".toObject<JsonSample>()

        obj?.a shouldBeEqualTo 1
        obj?.b shouldBeEqualTo "x"
    }

    @Test
    fun `toObject returns null and reports error on malformed json`() {
        var reported = false

        val obj = """{"a": not-json""".toObject<JsonSample>(onError = { reported = true })

        obj.shouldBeNull()
        reported.shouldBeTrue()
    }

    @Test
    fun `toJsonString serializes an object`() {
        JsonSample(1, "x").toJsonString() shouldBeEqualTo """{"a":1,"b":"x"}"""
    }
}
