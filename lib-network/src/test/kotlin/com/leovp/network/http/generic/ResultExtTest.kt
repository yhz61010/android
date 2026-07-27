package com.leovp.network.http.generic

import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * Author: Michael Leo
 *
 * Regression test for remediation H6: [result] must rethrow [CancellationException] rather than
 * wrapping it into a Result.Failure, so a cancelled caller coroutine actually cancels.
 */
class ResultExtTest {

    @Test
    fun `result rethrows CancellationException instead of wrapping it`() = runTest {
        val error = runCatching {
            result<String> { throw CancellationException("cancelled") }
        }.exceptionOrNull()

        error shouldBeInstanceOf CancellationException::class
    }
}
