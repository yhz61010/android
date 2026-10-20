package com.leovp.network.http.net

import com.drake.net.exception.RequestParamsException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ResultExtTest {
    @Test
    fun `result rethrows Error instead of wrapping it`() = runTest {
        val error = runCatching {
            result<String> { throw AssertionError("fatal") }
        }.exceptionOrNull()

        error shouldBeInstanceOf AssertionError::class
    }

    @Test
    fun `response body reader does not swallow Error`() = runTest {
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.test/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(400)
            .message("Bad Request")
            .body(
                object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength(): Long = -1L
                    override fun source(): BufferedSource =
                        throw AssertionError("fatal response body failure")
                }
            )
            .build()
        val responseError = RequestParamsException(
            response = response,
            message = "bad request"
        )

        val error = runCatching {
            result<String> { throw responseError }
        }.exceptionOrNull()

        error shouldBeInstanceOf AssertionError::class
    }
}
