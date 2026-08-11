package com.leovp.http.okhttp

import com.leovp.log.LogContext
import com.leovp.log.base.LogOutType
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.ForwardingSink
import okio.buffer

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 */
class HttpLoggingInterceptor(private val logger: Logger = Logger.DEFAULT) : Interceptor {
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
        fun log(message: String?, outputType: LogOutType = LogOutType.COMMON)

        companion object {
            /**
             * A [Logger] defaults output appropriate for the current platform.
             */
            val DEFAULT: Logger = object : Logger {
                override fun log(message: String?, outputType: LogOutType) {
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
        logger.log(
            "─".repeat(98)
        )
        var requestStartMessage = "--> ${request.method} ${request.url} $protocol"
        if (!logHeaders && hasRequestBody) {
            requestStartMessage =
                "$requestStartMessage (${requestBody.contentLength()}-byte body)"
        }
        logger.log(requestStartMessage)
        if (logHeaders) {
            if (hasRequestBody) {
                // Request body headers are only present when installed as a network interceptor.
                // Force
                // them to be included (when available) so there values are known.
                requestBody.contentType()?.let {
                    logger.log("Content-Type: $it")
                    if (it.toString().contains("boundary=")) {
                        hasBoundary = true
                    }
                }
                if (requestBody.contentLength() != -1L) {
                    logger.log("Content-Length: ${requestBody.contentLength()}")
                }
            }
            val headers = request.headers
            for (i in 0 until headers.size) {
                val name = headers.name(i)
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equals(name, ignoreCase = true) &&
                    !"Content-Length".equals(name, ignoreCase = true)
                ) {
                    logger.log(
                        "$name: ${redactHeader(name, headers.value(i))}",
                        outputType = LogOutType.HTTP_HEADER
                    )
                }
            }
            logRequestEnd(request, requestBody, logBody, hasBoundary)
            logger.log(
                "─".repeat(98)
            )
        }
        val startNs = System.nanoTime()
        val response: Response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            logger.log(
                "─".repeat(98)
            )
            logger.log("<-- HTTP FAILED: $e")
            logger.log(
                "─".repeat(98)
            )
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        logger.log(
            "─".repeat(98)
        )
        val responseBody = response.body
        val contentLength = responseBody.contentLength()
        val bodySize = if (contentLength != -1L) "$contentLength-byte" else "unknown-length"
        logger.log(
            "<-- ${response.code} ${response.message} ${response.request.url} " +
                "(${tookMs}ms${if (!logHeaders) " , $bodySize body" else ""})"
        )
        if (logHeaders) {
            val headers = response.headers
            var hasInlineFile = false
            for (i in 0 until headers.size) {
                logger.log(
                    "${headers.name(i)}: ${redactHeader(headers.name(i), headers.value(i))}",
                    outputType = LogOutType.HTTP_HEADER
                )
                if ("Content-Disposition".contentEquals(headers.name(i)) &&
                    headers.value(i).startsWith("inline; filename")
                ) {
                    hasInlineFile = true
                }
            }
            if (!logBody || !response.hasReadableBody()) {
                logger.log("<-- END HTTP")
            } else if (bodyEncoded(response.headers)) {
                logger.log("<-- END HTTP (encoded body omitted)")
            } else if (hasInlineFile) {
                logger.log("<-- END HTTP (inline file omitted)")
            } else {
                val source = responseBody.source()
                // Buffer at most the log limit (+1 to detect truncation) instead of the whole body.
                source.request(MAX_LOGGABLE_BODY_BYTES + 1)
                val buffer = source.buffer
                val charset =
                    responseBody.contentType()?.charset(DEFAULT_CHARSET) ?: DEFAULT_CHARSET
                if (!isPlaintext(buffer)) {
                    logger.log(" \n<-- END HTTP (binary ${buffer.size}-byte body omitted)")
                    return response
                }
                if (contentLength != 0L) {
                    val logged = minOf(buffer.size, MAX_LOGGABLE_BODY_BYTES)
                    logger.log(" \n${buffer.clone().readString(logged, charset)}")
                    if (buffer.size > logged) {
                        logger.log("<-- (body truncated to $logged bytes for logging)")
                    }
                }
                logger.log("<-- END HTTP (${buffer.size}-byte buffered for logging)")
            }
        }
        logger.log(
            "─".repeat(98)
        )
        return response
    }

    private fun logRequestEnd(
        request: Request,
        requestBody: RequestBody?,
        logBody: Boolean,
        hasBoundary: Boolean,
    ) {
        if (!logBody || requestBody == null) {
            logger.log("--> END ${request.method}")
        } else if (bodyEncoded(request.headers)) {
            logger.log("--> END ${request.method} (encoded body omitted)")
        } else if (hasBoundary) {
            logger.log(
                "--> END ${request.method} (Found boundary " +
                    "${requestBody.contentLength()}-byte body omitted)"
            )
        } else if (requestBody.isDuplex() || requestBody.isOneShot()) {
            logger.log("--> END ${request.method} (duplex/one-shot body omitted)")
        } else if (requestBody.contentLength() !in 0..MAX_LOGGABLE_BODY_BYTES) {
            logger.log(
                "--> END ${request.method} " +
                    "(${requestBody.contentLength()}-byte body omitted, exceeds log limit)"
            )
        } else {
            logCapturedRequestBody(request, requestBody)
        }
    }

    private fun logCapturedRequestBody(request: Request, requestBody: RequestBody) {
        val capturedBody = captureRequestBodyForLogging(requestBody)
        val buffer = capturedBody.buffer
        val charset = requestBody.contentType()?.charset(DEFAULT_CHARSET) ?: DEFAULT_CHARSET
        logger.log("")
        if (isPlaintext(buffer)) {
            val logged = minOf(buffer.size, MAX_LOGGABLE_BODY_BYTES)
            logger.log(buffer.clone().readString(logged, charset))
            val suffix = if (capturedBody.truncated) {
                " (truncated to $logged bytes for logging)"
            } else {
                ""
            }
            logger.log(
                "--> END ${request.method} (${requestBody.contentLength()}-byte body$suffix)"
            )
        } else {
            logger.log(
                "--> END ${request.method} (binary ${requestBody.contentLength()}-byte " +
                    "body omitted)"
            )
        }
    }

    private fun bodyEncoded(headers: Headers): Boolean {
        val contentEncoding = headers["Content-Encoding"]
        return contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true)
    }

    companion object {
        private val DEFAULT_CHARSET = Charsets.UTF_8
        private const val TAG = "HTTP"

        /** Maximum number of body bytes buffered/emitted per direction when logging (256 KiB). */
        private const val MAX_LOGGABLE_BODY_BYTES = 256L * 1024

        internal data class CapturedRequestBody(val buffer: Buffer, val truncated: Boolean)

        /**
         * Captures at most [MAX_LOGGABLE_BODY_BYTES] plus one probe byte. Uses a non-throwing
         * discarding sink (keep up to the cap, silently drop the rest, record whether anything was
         * dropped) instead of a control-flow exception, so a dishonest contentLength() cannot make
         * the logging path allocate the whole body, a RequestBody whose writeTo() catches
         * IOException cannot swallow the truncation signal, and no stack trace is paid per capture
         * (remediation R-10 / HTTP-3).
         */
        internal fun captureRequestBodyForLogging(requestBody: RequestBody): CapturedRequestBody {
            val output = Buffer()
            val cap = MAX_LOGGABLE_BODY_BYTES + 1
            var truncated = false
            val limitedSink = object : ForwardingSink(output) {
                override fun write(source: Buffer, byteCount: Long) {
                    val remaining = cap - output.size
                    when {
                        remaining <= 0 -> {
                            source.skip(byteCount)
                            truncated = true
                        }
                        byteCount > remaining -> {
                            super.write(source, remaining)
                            source.skip(byteCount - remaining)
                            truncated = true
                        }
                        else -> super.write(source, byteCount)
                    }
                }
            }.buffer()
            limitedSink.use { requestBody.writeTo(it) }
            return CapturedRequestBody(output, truncated)
        }

        /** Header names whose values are redacted in logs to avoid leaking credentials. */
        private val SENSITIVE_HEADERS = setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie"
        )

        /** Redacts the value of a sensitive header (matched case-insensitively by [name]). */
        private fun redactHeader(name: String, value: String): String =
            if (name.lowercase() in SENSITIVE_HEADERS) "██" else value

        /**
         * Returns true if the body in question probably contains human readable text. Uses a small
         * sample
         * of code points to detect unicode control characters commonly used in binary file
         * signatures.
         */
        fun isPlaintext(buffer: Buffer): Boolean {
            return runCatching {
                val prefix = Buffer()
                val byteCount = if (buffer.size < 64) buffer.size else 64
                buffer.copyTo(prefix, 0, byteCount)
                for (i in 0..15) {
                    if (prefix.exhausted() || i >= prefix.size) {
                        break
                    }
                    val codePoint = prefix.readUtf8CodePoint()
                    if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                        return false
                    }
                }
                true
            }.getOrElse {
                // Truncated UTF-8 sequence.
                false
            }
        }
    }
}

internal fun Response.hasReadableBody(): Boolean {
    if (request.method == "HEAD") return false
    if ((code < 100 || code >= 200) && code != 204 && code != 304) return true

    val contentLength = header("Content-Length")?.toLongOrNull() ?: -1L
    return contentLength != -1L || header("Transfer-Encoding").equals("chunked", ignoreCase = true)
}
