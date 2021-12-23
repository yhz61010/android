package com.leovp.http_sdk.okhttp


import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ILog
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 */
class HttpLoggingInterceptor constructor(private val logger: Logger = Logger.DEFAULT) : Interceptor {
    enum class Level {
        /**
         * No logs.
         */
        NONE,

        /**
         * Logs request and response lines.
         *
         *
         * Example:
         * <pre>
         * --> POST /greeting http/1.1 (3-byte body)
         *
         * <-- 200 OK (22ms, 6-byte body)
         * </pre>
         */
        @Suppress("unused")
        BASIC,

        /**
         * Logs request and response lines and their respective headers.
         *
         *
         * Example:
         * <pre>
         * --> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         * --> END POST
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         * <-- END HTTP
         * </pre>
         */
        HEADERS,

        /**
         * Logs request and response lines and their respective headers and bodies (if present).
         *
         *
         * Example:
         * <pre>
         * --> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         *
         * Hi?
         * --> END POST
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         *
         * Hello!
         * <-- END HTTP
         * </pre>
         */
        BODY
    }

    interface Logger {
        fun log(message: String?, outputType: Int = -1)

        companion object {
            /**
             * A [Logger] defaults output appropriate for the current platform.
             */
            val DEFAULT: Logger = object : Logger {
                override fun log(message: String?, outputType: Int) {
                    LogContext.log.w(TAG, message, outputType = outputType)
                }
            }
        }
    }

    /**
     * Change the level at which this interceptor logs.
     */
    @Volatile
    var level = Level.NONE

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val level = level
        val request = chain.request()
        if (level == Level.NONE) {
            return chain.proceed(request)
        }
        val logBody = level == Level.BODY
        val logHeaders = logBody || level == Level.HEADERS
        val requestBody = request.body
        val hasRequestBody = requestBody != null
        val connection = chain.connection()
        val protocol = connection?.protocol() ?: Protocol.HTTP_1_1
        var hasBoundary = false
        logger.log("─────────────────────────────────────────────────────────────────────────────────────────────────────────────────")
        var requestStartMessage = "--> ${request.method} ${request.url} $protocol"
        if (!logHeaders && hasRequestBody) requestStartMessage =
            "$requestStartMessage (${requestBody?.contentLength()}-byte body)"
        logger.log(requestStartMessage)
        if (logHeaders) {
            if (hasRequestBody) {
                // Request body headers are only present when installed as a network interceptor. Force
                // them to be included (when available) so there values are known.
                requestBody?.contentType()?.let {
                    logger.log("Content-Type: $it")
                    if (it.toString().contains("boundary=")) {
                        hasBoundary = true
                    }
                }
                if (requestBody?.contentLength() ?: -1 != -1L) {
                    logger.log("Content-Length: ${requestBody?.contentLength()}")
                }
            }
            val headers = request.headers
            var i = 0
            val count = headers.size
            while (i < count) {
                val name = headers.name(i)
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equals(name, ignoreCase = true) && !"Content-Length".equals(
                        name,
                        ignoreCase = true
                    )
                ) {
                    logger.log("$name: ${headers.value(i)}", outputType = ILog.OUTPUT_TYPE_HTTP_HEADER_COOKIE)
                }
                i++
            }
            if (!logBody || !hasRequestBody) {
                logger.log("--> END ${request.method}")
            } else if (bodyEncoded(request.headers)) {
                logger.log("--> END ${request.method} (encoded body omitted)")
            } else if (hasBoundary) {
                logger.log("--> END ${request.method} (Found boundary ${requestBody?.contentLength()}-byte body omitted)")
            } else {
                val buffer = Buffer()
                requestBody?.writeTo(buffer)
                var charset = DEFAULT_CHARSET
                requestBody?.contentType()?.also {
                    charset = it.charset(DEFAULT_CHARSET)!!
                }
                logger.log("")
                if (isPlaintext(buffer)) {
                    val content = buffer.readString(charset)
                    logger.log(content)
                    logger.log("--> END ${request.method} (${requestBody?.contentLength()}-byte body)")
                } else {
                    logger.log("--> END ${request.method} (binary ${requestBody?.contentLength()}-byte body omitted)")
                }
            }
            logger.log("─────────────────────────────────────────────────────────────────────────────────────────────────────────────────")
        }
        val startNs = System.nanoTime()
        val response: Response
        response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            logger.log("─────────────────────────────────────────────────────────────────────────────────────────────────────────────────")
            logger.log("<-- HTTP FAILED: $e")
            logger.log("─────────────────────────────────────────────────────────────────────────────────────────────────────────────────")
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        logger.log("─────────────────────────────────────────────────────────────────────────────────────────────────────────────────")
        val responseBody = response.body
        val contentLength = responseBody?.contentLength() ?: -1
        val bodySize = if (contentLength != -1L) "$contentLength-byte" else "unknown-length"
        logger.log(
            "<-- ${response.code} ${response.message} ${response.request.url} (${tookMs}ms${if (!logHeaders) " , $bodySize body" else ""})"
        )
        if (logHeaders) {
            val headers = response.headers
            var i = 0
            val count = headers.size
            var hasInlineFile = false
            while (i < count) {
                logger.log("${headers.name(i)}: ${headers.value(i)}", outputType = ILog.OUTPUT_TYPE_HTTP_HEADER_COOKIE)
                if ("Content-Disposition".contentEquals(headers.name(i)) && headers.value(i).startsWith("inline; filename")) {
                    hasInlineFile = true
                }
                i++
            }
            if (!logBody || !response.promisesBody()) {
                logger.log("<-- END HTTP")
            } else if (bodyEncoded(response.headers)) {
                logger.log("<-- END HTTP (encoded body omitted)")
            } else if (hasInlineFile) {
                logger.log("<-- END HTTP (inline file omitted)")
            } else {
                val source = responseBody?.source()
                source?.request(Long.MAX_VALUE)
                // Buffer the entire body.
                val buffer = source?.buffer
                var charset = DEFAULT_CHARSET
                val contentType = responseBody?.contentType()
                if (contentType != null) {
                    charset = contentType.charset(DEFAULT_CHARSET)!!
                }
                if (buffer != null && !isPlaintext(buffer)) {
                    logger.log(" \n<-- END HTTP (binary ${buffer.size}-byte body omitted)")
                    return response
                }
                if (contentLength != 0L) {
                    logger.log(" \n${buffer?.clone()?.readString(charset)}")
                }
                logger.log("<-- END HTTP (${buffer?.size}-byte body)")
            }
        }
        logger.log("─────────────────────────────────────────────────────────────────────────────────────────────────────────────────")
        return response
    }

    private fun bodyEncoded(headers: Headers): Boolean {
        val contentEncoding = headers["Content-Encoding"]
        return contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true)
    }

    companion object {
        private val DEFAULT_CHARSET = Charsets.UTF_8
        private const val TAG = "HTTP"

        /**
         * Returns true if the body in question probably contains human readable text. Uses a small sample
         * of code points to detect unicode control characters commonly used in binary file signatures.
         */
        fun isPlaintext(buffer: Buffer): Boolean {
            return try {
                val prefix = Buffer()
                val byteCount = if (buffer.size < 64) buffer.size else 64
                buffer.copyTo(prefix, 0, byteCount)
                for (i in 0..15) {
                    if (prefix.exhausted()) {
                        break
                    }
                    val codePoint = prefix.readUtf8CodePoint()
                    if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                        return false
                    }
                }
                true
            } catch (e: EOFException) {
                false // Truncated UTF-8 sequence.
            }
        }
    }
}