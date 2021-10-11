package com.leovp.http_sdk.retrofit

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Author: Michael Leo
 * Date: 20-5-27 下午8:41
 *
 * Example:
 * ```kotlin
 * interface CommonService {
 *     @GET("/api/weather/city/{id}")
 *     fun getData(@Path("id") param: String): Observable<Map<String, String>>
 * }
 *
 * fun test() {
 *     val observer: ObserverOnNextListener<Map<String, String>> = object : ObserverOnNextListener<Map<String, String>> {
 *         override fun onNext(t: Map<String, String>) {
 *         t.forEach { LogContext.log.e(TAG, "Response ${it.key}=$${it.value}") }
 *         }
 *     }
 *     val service = ApiService.getService("http://t.weather.sojson.com", CommonService::class.java)
 *     ApiSubscribe.subscribe(service.getData("101070201"), NoProgressObserver(observer))
 * }
 * ```
 */
object ApiSubscribe {
    fun <T> subscribe(observable: Observable<T>, observer: Observer<T>) {
        observable.subscribeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(observer)
    }
}