package com.leovp.network.http.generic

import com.leovp.network.exception.ApiException
import com.leovp.network.exception.ApiResponseException
import com.leovp.network.exception.ApiServerException
import com.leovp.network.http.Result
import com.leovp.network.http.exception.ResultResponseException
import com.leovp.network.http.payloadOperationOrNull
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `result rethrows Error instead of wrapping it`() = runTest {
        val error = runCatching {
            result<String> { throw AssertionError("fatal") }
        }.exceptionOrNull()

        error shouldBeInstanceOf AssertionError::class
    }

    @Test
    fun `generic ApiException preserves its tag and original cause`() = runTest {
        val apiError = ApiException(code = "E1", message = "failed", tag = "request-tag")

        val failure = result<String> { throw apiError } as Result.Failure

        failure.exception.tag shouldBeEqualTo "request-tag"
        assertSame(apiError, failure.exception.cause)
    }

    @Test
    fun `server exception keeps the wrapper as its cause`() = runTest {
        val root = IllegalStateException("root")
        val apiError = ApiServerException(
            statusCode = 500,
            message = "server failed",
            cause = root,
            tag = "request-tag"
        )

        val failure = result<String> { throw apiError } as Result.Failure

        assertSame(apiError, failure.exception.cause)
        failure.exception.tag shouldBeEqualTo "request-tag"
    }

    @Test
    fun `response exception keeps request tag separate from decoded error data`() = runTest {
        val apiError = ApiResponseException(
            statusCode = 400,
            code = "BAD_REQUEST",
            message = "bad request",
            responseBodyString = "\"decoded-error\"",
            tag = "request-tag"
        )

        val failure = result<String> { throw apiError } as Result.Failure
        val exception = failure.exception as ResultResponseException

        exception.tag shouldBeEqualTo "request-tag"
        exception.errorData shouldBeEqualTo "decoded-error"
        assertSame(apiError, exception.cause)
    }

    @Test
    fun `response error payload decoder does not swallow Error`() = runTest {
        val error = runCatching {
            payloadOperationOrNull<String>("decode failed") {
                throw AssertionError("fatal payload decoder failure")
            }
        }.exceptionOrNull()

        error shouldBeInstanceOf AssertionError::class
    }
}
