package com.ho1ho.androidbase.http.retrofit.observers.base

import com.google.gson.stream.MalformedJsonException
import com.ho1ho.androidbase.http.retrofit.iter.ObserverOnNextListener
import com.ho1ho.androidbase.utils.CLog
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
        CLog.d(javaClass.simpleName, "onSubscribe()")
        mDisposable = d
    }

    override fun onNext(t: T) {
        CLog.d(javaClass.simpleName, "onNext()")
        mListener.onNext(t)
    }

    override fun onError(e: Throwable) {
        CLog.e(javaClass.simpleName, "onError: ${e.message}")
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
        val errMsg: String? = null
        var statusCode = -1
        when (e) {
            is ConnectException -> {
                // 无法连接服务器
                CLog.e(javaClass.simpleName, "Can not connect to server. ConnectException")
            }
            is SocketTimeoutException -> {
                // 连接超时
                CLog.e(javaClass.simpleName, "Connect timeout.")
            }
            is UnknownHostException -> {
                // java.net.UnknownHostException: Unable to resolve host "dummy.dummy": No address associated with hostname
                // 无法连接服务器
                CLog.e(javaClass.simpleName, "Can not connect to server. UnknownHostException")
            }
            is MalformedJsonException -> {
                // 数据格式不正确。
                CLog.e(javaClass.simpleName, "MalformedJsonException")
            }
            is HttpException -> {
                statusCode = e.code()
                when (statusCode) {
                    in 400..499 -> {
                        CLog.e(javaClass.simpleName, "Response status code[4xx]: $statusCode")
                    }
                    in 500..599 -> {
                        CLog.e(javaClass.simpleName, "Response status code[5xx]: $statusCode")
                    }
                    else -> {
                        CLog.e(
                            javaClass.simpleName,
                            "Response status code[neither 4xx nor 5xx]: $statusCode"
                        )
                    }
                }
            }
        }
        mListener.onError(statusCode, errMsg, e)
    }

    override fun onComplete() {
        CLog.d(javaClass.simpleName, "onComplete()")
        mListener.onComplete()
    }
}