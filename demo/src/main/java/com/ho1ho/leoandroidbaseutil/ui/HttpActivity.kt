package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.http.retrofit.ApiService
import com.ho1ho.androidbase.http.retrofit.ApiSubscribe
import com.ho1ho.androidbase.http.retrofit.iter.ObserverOnNextListener
import com.ho1ho.androidbase.http.retrofit.observers.NoProgressObserver
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import io.reactivex.Observable
import kotlinx.android.synthetic.main.activity_http.*
import retrofit2.http.GET
import retrofit2.http.Path

class HttpActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_http)
    }

    interface CommonService {
        @GET("/data/sk/{id}.html")
        fun getData(@Path("id") param: String): Observable<WeatherInfoResult>
    }

    fun onGetClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val observer: ObserverOnNextListener<WeatherInfoResult> = object : ObserverOnNextListener<WeatherInfoResult> {
            override fun onNext(t: WeatherInfoResult) {
                LLog.w(ITAG, "Response bean=${t.toJsonString()}")
                txtResult.text = t.toJsonString()
            }
        }
        val service = ApiService.getService("http://www.weather.com.cn", CommonService::class.java)
        ApiSubscribe.subscribe(service.getData("101070201"), NoProgressObserver(observer))
    }

    @Keep
    data class WeatherInfoResult(val weatherinfo: WeatherInfo)

    @Keep
    data class WeatherInfo(
        val city: String,
        val cityid: String,
        val temp: String,
        val WD: String,
        val WS: String,
        val SD: String,
        val AP: String,
        val njd: String,
        val WSE: String,
        val time: String,
        val sm: String
    )
}