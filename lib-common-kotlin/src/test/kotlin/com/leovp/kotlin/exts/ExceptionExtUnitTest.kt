package com.leovp.kotlin.exts

import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Author: Michael Leo
 *
 * Unit tests for remediation H16: [multiCatch] must not silently swallow exceptions (rethrow when
 * no handler is supplied), must not catch [Error], and must always rethrow [CancellationException].
 */
class ExceptionExtUnitTest {

    @Test
    fun `matched exception without catchBlock is rethrown, not swallowed`() {
        assertThrows<IOException> {
            multiCatch(
                runBlock = { throw IOException("boom") },
                exceptions = arrayOf(IOException::class)
            )
        }
    }

    @Test
    fun `matched exception invokes catchBlock`() {
        var handled = false

        multiCatch(
            runBlock = { throw IOException("boom") },
            exceptions = arrayOf(IOException::class),
            catchBlock = { handled = true }
        )

        handled.shouldBeTrue()
    }

    @Test
    fun `unmatched exception without uncaughtBlock is rethrown`() {
        assertThrows<IllegalStateException> {
            multiCatch(
                runBlock = { error("nope") },
                exceptions = arrayOf(IOException::class)
            )
        }
    }

    @Test
    fun `CancellationException is always rethrown even if listed`() {
        assertThrows<CancellationException> {
            multiCatch(
                runBlock = { throw CancellationException("c") },
                exceptions = arrayOf(CancellationException::class),
                catchBlock = { error("must not run") }
            )
        }
    }
}
