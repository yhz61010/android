package com.leovp.androidbase.http.retrofit.observers.base

import com.google.gson.stream.MalformedJsonException
import com.leovp.androidbase.http.retrofit.iter.ObserverOnNextListener
import com.leovp.androidbase.utils.LLog
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Author: Michael Leo
 * Date: 19-7-24 下午5:22
 */
abstract class BaseProgressObserver<T>(private val mListener: ObserverOnNextListener<T>) :
    Observer<T> {
    private var mDisposable: Disposable? = null
    override fun onSubscribe(d: Disposable) {
        LLog.d(javaClass.simpleName, "onSubscribe()")
        mDisposable = d
    }

    override fun onNext(t: T) {
        LLog.d(javaClass.simpleName, "onNext()")
        mListener.onNext(t)
    }

    override fun onError(e: Throwable) {
        LLog.e(javaClass.simpleName, "onError: ${e.message}")
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
                LLog.e(javaClass.simpleName, "Can not connect to server. ConnectException")
            }
            is SocketTimeoutException -> {
                // Timeout
                LLog.e(javaClass.simpleName, "Connect timeout.")
            }
            is UnknownHostException -> {
                // java.net.UnknownHostException: Unable to resolve host "dummy.dummy": No address associated with hostname
                LLog.e(javaClass.simpleName, "Can not connect to server. UnknownHostException")
            }
            is MalformedJsonException -> {
                // Malformed JSON
                LLog.e(javaClass.simpleName, "MalformedJsonException")
            }
            is HttpException -> {
                statusCode = e.code()
                when (statusCode) {
                    in 400..499 -> {
                        LLog.e(javaClass.simpleName, "Response status code[4xx]: $statusCode")
                    }
                    in 500..599 -> {
                        LLog.e(javaClass.simpleName, "Response status code[5xx]: $statusCode")
                    }
                    else -> {
                        LLog.e(
                            javaClass.simpleName,
                            "Response status code[neither 4xx nor 5xx]: $statusCode"
                        )
                    }
                }
            }
        }
        mListener.onError(statusCode, e.message ?: "", e)
    }

    override fun onComplete() {
        LLog.d(javaClass.simpleName, "onComplete()")
        mListener.onComplete()
    }
}