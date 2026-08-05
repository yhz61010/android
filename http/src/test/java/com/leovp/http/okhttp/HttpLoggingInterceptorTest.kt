package com.leovp.http.okhttp

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpLoggingInterceptorTest {
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
}
