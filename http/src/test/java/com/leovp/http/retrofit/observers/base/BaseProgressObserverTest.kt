package com.leovp.http.retrofit.observers.base

import com.google.gson.stream.MalformedJsonException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

class BaseProgressObserverTest {
    @Test
    fun `observer errors are classified without losing http status`() {
        assertEquals(
            "Can not connect to server. ConnectException",
            classifyObserverError(ConnectException()).logMessage
        )
        assertEquals("Connect timeout.", classifyObserverError(SocketTimeoutException()).logMessage)
        assertEquals(
            "Can not connect to server. UnknownHostException",
            classifyObserverError(UnknownHostException()).logMessage
        )
        assertEquals(
            "MalformedJsonException",
            classifyObserverError(MalformedJsonException("bad json")).logMessage
        )

        val httpError = classifyObserverError(
            HttpException(Response.error<Any>(503, "unavailable".toResponseBody()))
        )
        assertEquals(503, httpError.statusCode)
        assertEquals("Response status code: 503", httpError.logMessage)

        val unknown = classifyObserverError(IllegalStateException("unknown"))
        assertEquals(-1, unknown.statusCode)
        assertNull(unknown.logMessage)
    }
}
