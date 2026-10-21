package com.leovp.network.interceptors

import com.leovp.log.base.LogOutType
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpLoggingInterceptorTest {
    @Test
    fun `large response logging is truncated while application body remains complete`() {
        val body = "r".repeat(1024 * 1024)
        val messages = mutableListOf<String>()
        val interceptor = interceptor(messages)
        val request = Request.Builder().url("https://example.test/").build()
        val chain = chain(request, body.toResponseBody())

        val response = interceptor.intercept(chain)

        assertTrue(messages.any { it.contains("body truncated to 262144 bytes") })
        assertTrue(messages.filter { it.startsWith(" \n") }.sumOf(String::length) < 300_000)
        assertEquals(body, response.body.string())
    }

    @Test
    fun `large request logging is truncated even when declared length is small`() {
        val messages = mutableListOf<String>()
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 1L
            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("q".repeat(1024 * 1024))
            }
        }
        val request = Request.Builder().url("https://example.test/").post(body).build()

        interceptor(messages).intercept(chain(request, "ok".toResponseBody()))

        assertTrue(messages.any { it.contains("truncated to 262144 bytes") })
        assertTrue(messages.none { it.length > 300_000 })
    }

    @Test
    fun `one shot request body is not consumed for logging`() {
        val writes = AtomicInteger()
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                writes.incrementAndGet()
                sink.writeUtf8("payload")
            }
        }
        val request = Request.Builder().url("https://example.test/").post(body).build()

        interceptor(mutableListOf()).intercept(chain(request, "ok".toResponseBody()))

        assertEquals(0, writes.get())
    }

    @Test
    fun `duplex and unknown length request bodies are not consumed for logging`() {
        val writes = AtomicInteger()
        fun body(duplex: Boolean, length: Long) = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun isDuplex(): Boolean = duplex
            override fun contentLength(): Long = length
            override fun writeTo(sink: BufferedSink) {
                writes.incrementAndGet()
                sink.writeUtf8("payload")
            }
        }

        listOf(body(duplex = true, length = 7L), body(duplex = false, length = -1L))
            .forEach { requestBody ->
                val request = Request.Builder()
                    .url("https://example.test/")
                    .post(requestBody)
                    .build()
                interceptor(mutableListOf()).intercept(chain(request, "ok".toResponseBody()))
            }

        assertEquals(0, writes.get())
    }

    @Test
    fun `request body cannot exceed capture limit by swallowing sink exceptions`() {
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 1L
            override fun writeTo(sink: BufferedSink) {
                repeat(8) {
                    try {
                        sink.writeUtf8("q".repeat(128 * 1024))
                        sink.flush()
                    } catch (_: IOException) {
                        // A hostile RequestBody may swallow the logger's bounded-sink sentinel.
                    }
                }
            }
        }

        val captured = HttpLoggingInterceptor.captureRequestBodyForLogging(body)

        assertTrue(captured.truncated)
        assertTrue(captured.buffer.size <= 256L * 1024 + 1)
    }

    private fun interceptor(messages: MutableList<String>) = HttpLoggingInterceptor(
        object : HttpLoggingInterceptor.Logger {
            override fun log(msg: String?, logType: LogOutType) {
                messages += msg.orEmpty()
            }
        }
    ).apply { level = HttpLoggingInterceptor.Level.BODY }

    private fun chain(request: Request, body: okhttp3.ResponseBody): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.connection() } returns null
        every { chain.proceed(request) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
        return chain
    }
}
