package com.leovp.http.retrofit.iter

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 */
interface ObserverOnNextListener<in T> {
    fun onNext(t: T?)
    fun onError(code: Int, msg: String, e: Throwable) {}
    fun onComplete() {}
}
