package com.leovp.network.http.net

import com.drake.net.exception.RequestParamsException
import com.drake.net.exception.ConvertException
import com.leovp.network.http.Result
import com.leovp.network.http.exception.ResultConvertException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ResultExtTest {
    @Test
    fun `convert exception preserves the already consumed successful response body`() = runTest {
        val rawBody = "{not-json}"
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.test/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(rawBody.toResponseBody())
            .build()
        response.body.string()
        val convertError = ConvertException(
            response = response,
            message = rawBody,
            cause = IllegalArgumentException("invalid json"),
            tag = "request-tag"
        )

        val failure = result<String> { throw convertError } as Result.Failure
        val exception = failure.exception as ResultConvertException

        assertEquals(rawBody, exception.responseBodyString)
        assertEquals("invalid json", exception.message)
    }

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
