package com.leovp.http.okhttp

import java.io.IOException
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpLoggingInterceptorTest {
    @Test
    fun `response body semantics cover head and bodyless status codes`() {
        assertFalse(response(method = "HEAD", code = 200).hasReadableBody())
        assertFalse(response(code = 204).hasReadableBody())
        assertFalse(response(code = 304).hasReadableBody())
        assertTrue(response(code = 200).hasReadableBody())
        assertTrue(
            response(code = 204, headers = mapOf("Content-Length" to "1")).hasReadableBody()
        )
        assertTrue(
            response(code = 304, headers = mapOf("Transfer-Encoding" to "chunked"))
                .hasReadableBody()
        )
    }

    @Test
    fun `request body capture stays bounded when content length lies`() {
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun contentLength(): Long = 1

            override fun writeTo(sink: BufferedSink) {
                sink.write(ByteArray(1024 * 1024) { 1 })
            }
        }

        val captured = HttpLoggingInterceptor.captureRequestBodyForLogging(body)

        assertTrue(captured.truncated)
        assertEquals(256L * 1024 + 1, captured.buffer.size)
    }

    @Test
    fun `request body capture preserves a small body`() {
        val expected = "small request body"
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8(expected)
            }
        }

        val captured = HttpLoggingInterceptor.captureRequestBodyForLogging(body)

        assertFalse(captured.truncated)
        assertEquals(expected, captured.buffer.readUtf8())
    }

    @Test
    fun `request body capture marks the exact probe-byte boundary as truncated`() {
        val body = requestBody(ByteArray(256 * 1024 + 1))

        val captured = HttpLoggingInterceptor.captureRequestBodyForLogging(body)

        assertTrue(captured.truncated)
        assertEquals(256L * 1024 + 1, captured.buffer.size)
    }

    @Test
    fun `request body cannot hide truncation by catching IOException`() {
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun contentLength(): Long = 1

            override fun writeTo(sink: BufferedSink) {
                try {
                    sink.write(ByteArray(256 * 1024 + 1))
                } catch (_: IOException) {
                    // Simulate a third-party body that swallows sink failures.
                }
            }
        }

        val captured = HttpLoggingInterceptor.captureRequestBodyForLogging(body)

        assertTrue(captured.truncated)
    }

    @Test
    fun `request body capture aborts further body generation at the limit`() {
        var generatedChunks = 0
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null

            override fun contentLength(): Long = 1

            override fun writeTo(sink: BufferedSink) {
                repeat(1_024) {
                    sink.write(ByteArray(8 * 1024))
                    generatedChunks++
                }
            }
        }

        val captured = HttpLoggingInterceptor.captureRequestBodyForLogging(body)

        assertTrue(captured.truncated)
        assertTrue(generatedChunks < 1_024, "Logging must stop the body producer at the cap")
    }

    private fun requestBody(bytes: ByteArray): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType? = null

        override fun writeTo(sink: BufferedSink) {
            sink.write(bytes)
        }
    }

    private fun response(
        method: String = "GET",
        code: Int,
        headers: Map<String, String> = emptyMap(),
    ): Response = Response.Builder()
        .request(
            Request.Builder()
                .url("https://example.com/")
                .method(method, null)
                .build()
        )
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Test")
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
}
