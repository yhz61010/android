package com.leovp.compress

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/**
 * Author: Michael Leo
 *
 * Regression test for remediation H19: [decompress] must bound its output to avoid decompression
 * bombs (small input inflating to a huge output).
 */
class FlaterExtLimitTest {

    @Test
    fun `decompress throws when the output exceeds the size cap`() {
        // 1 MiB of zeros compresses to a tiny payload but inflates back to 1 MiB.
        val bomb = ByteArray(1 shl 20).compress()

        assertThrows<IOException> { bomb.decompress(maxOutputSize = 1024) }
    }

    @Test
    fun `decompress within the cap round-trips normally`() {
        val data = "hello world".encodeToByteArray()

        data.compress().decompress(maxOutputSize = 1024).decodeToString() shouldBeEqualTo "hello world"
    }
}
