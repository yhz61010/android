package com.leovp.http.retrofit.observers.base

import com.google.gson.stream.MalformedJsonException
import com.leovp.http.retrofit.iter.ObserverOnNextListener
import com.leovp.log.LogContext
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/**
 * Author: Michael Leo
 * Date: 19-7-24 下午5:22
 */
abstract class BaseProgressObserver<T>(private val mListener: ObserverOnNextListener<T>) : Observer<T> {
    private var mDisposable: Disposable? = null
    override fun onSubscribe(d: Disposable) {
        LogContext.log.d(javaClass.simpleName, "onSubscribe()")
        mDisposable = d
    }

    override fun onNext(t: T & Any) {
        LogContext.log.d(javaClass.simpleName, "onNext()")
        mListener.onNext(t)
    }

    override fun onError(e: Throwable) {
        LogContext.log.e(javaClass.simpleName, "onError: ${e.message}")
        // ----------------------
        // Connection timeout
        // java.net.SocketTimeoutException: connect timed out
        // java.net.ConnectException: Failed to connect to /192.168.21.189:8081
        // ----------------------
        // Read timeout
        // java.net.SocketTimeoutException: Read timed out
        // java.net.SocketTimeoutException: timeout
        // java.net.SocketTimeoutException: SSL handshake timed out
        // ----------------------
        var statusCode = -1
        when (e) {
            is ConnectException -> {
                // Can not connect to server
                LogContext.log.e(javaClass.simpleName, "Can not connect to server. ConnectException")
            }
            is SocketTimeoutException -> {
                // Timeout
                LogContext.log.e(javaClass.simpleName, "Connect timeout.")
            }
            is UnknownHostException -> {
                // java.net.UnknownHostException: Unable to resolve host "dummy.dummy": No address associated with hostname
                LogContext.log.e(javaClass.simpleName, "Can not connect to server. UnknownHostException")
            }
            is MalformedJsonException -> {
                // Malformed JSON
                LogContext.log.e(javaClass.simpleName, "MalformedJsonException")
            }
            is HttpException -> {
                statusCode = e.code()
                LogContext.log.e(javaClass.simpleName, "Response status code: $statusCode")
                //                when (statusCode) {
                //                    in 400..499 -> {
                //                        LogContext.log.e(javaClass.simpleName, "Response status code[$statusCode]")
                //                    }
                //                    in 500..599 -> {
                //                        LogContext.log.e(javaClass.simpleName, "Response status code[$statusCode]")
                //                    }
                //                    else -> {
                //                        LogContext.log.e(
                //                            javaClass.simpleName,
                //                            "Response status code[neither 4xx nor 5xx]: $statusCode"
                //                        )
                //                    }
                //                }
            }
        }
        mListener.onError(statusCode, e.message ?: "", e)
    }

    override fun onComplete() {
        LogContext.log.d(javaClass.simpleName, "onComplete()")
        mListener.onComplete()
    }
}
