package com.ho1ho.leoandroidbaseutil.common_components.examples

import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import androidx.annotation.Keep
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toJsonString
import com.ho1ho.androidbase.http.retrofit.ApiService
import com.ho1ho.androidbase.http.retrofit.ApiSubscribe
import com.ho1ho.androidbase.http.retrofit.iter.ObserverOnNextListener
import com.ho1ho.androidbase.http.retrofit.observers.NoProgressObserver
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.file.FileUtil
import com.ho1ho.androidbase.utils.system.ResourcesUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.common_components.examples.base.BaseDemonstrationActivity
import io.reactivex.Observable
import kotlinx.android.synthetic.main.activity_http.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.http.*
import java.io.File


class HttpActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_http)
    }

    interface CommonService {
        @GET("/data/sk/{id}.html")
        fun getData(@Path("id") param: String): Observable<WeatherInfoResult>

        @FormUrlEncoded
        @Headers("Authorization:APPCODE Add-you-app-code-here")
        @POST("/ai_market/ai_face_position")
        fun postData(@Field("VIDEO") video: String): Observable<String>

        @Multipart
        @POST("/fileTransfer/uploadFile")
        fun uploadFile(@QueryMap parameters: Map<String, String>, @Part file: MultipartBody.Part): Observable<String>

        @Streaming
        @GET("/{urlPath}")
        // Values are URL encoded by default. Disable with encoded=true.
        fun downloadFile(@Path("urlPath", encoded = true) urlPath: String): Observable<ResponseBody>
//        fun downloadFile(@Url fileUrl: String): Observable<ResponseBody>
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

    fun onPostClick(@Suppress("UNUSED_PARAMETER") view: View) {
        txtResult.text = "It will cost several seconds. Please be patient..."
        val observer: ObserverOnNextListener<String> = object : ObserverOnNextListener<String> {
            override fun onNext(t: String) {
                LLog.w(ITAG, "Response=$t")
                txtResult.text = t
            }

            override fun onError(code: Int, msg: String, e: Throwable) {
                LLog.w(ITAG, "Request error. code=$code msg=$msg")
                txtResult.text = "Request error. code=$code msg=$msg"
            }
        }
        val service = ApiService.getService("https://iface.market.alicloudapi.com", CommonService::class.java)
        ApiSubscribe.subscribe(
            service.postData("https://icredit-api-market.oss-cn-hangzhou.aliyuncs.com/%E8%89%BE%E7%A7%91%E7%91%9E%E7%89%B9_%E6%99%BA%E8%83%BD%E5%9B%BE%E5%83%8F%E8%AF%86%E5%88%AB_%E6%99%BA%E8%83%BD%E4%BA%BA%E8%84%B8%E5%A7%BF%E6%80%81%E6%A3%80%E6%B5%8B/%E4%BA%BA%E8%84%B8%E5%A7%BF%E6%80%81.mp4"),
            NoProgressObserver(observer)
        )
    }

    fun onUploadClick(@Suppress("UNUSED_PARAMETER") view: View) {
        txtResult.text = "It may cost several seconds. Please be patient..."
        val observer: ObserverOnNextListener<String> = object : ObserverOnNextListener<String> {
            override fun onNext(t: String) {
                LLog.w(ITAG, "Response=$t")
                txtResult.text = "Upload Done"
            }

            override fun onError(code: Int, msg: String, e: Throwable) {
                LLog.w(ITAG, "Request error. code=$code msg=$msg")
                txtResult.text = "Request error. code=$code msg=$msg"
            }
        }

        val fileFullPath = ResourcesUtil.saveRawResourceToFile(resources, R.raw.tears_400_x265, getExternalFilesDir(null)!!.absolutePath, "h265.mp4")
        val sourceFile = File(fileFullPath)
        val mimeType = getMimeType(sourceFile)
        if (mimeType == null) {
            LLog.e("File upload error", "Not able to get mime type")
            return
        }
        val requestBody: RequestBody =
            MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", sourceFile.name, sourceFile.asRequestBody(mimeType.toMediaTypeOrNull()))
                .build()
        val body: MultipartBody.Part = MultipartBody.Part.create(requestBody)

        val parameters = mapOf(
            "your_parameter1" to "1",
            "your_parameter2" to "2"
        )

        val service = ApiService.getService("server_url", CommonService::class.java)
        ApiSubscribe.subscribe(service.uploadFile(parameters, body), NoProgressObserver(observer))
    }

    fun onDownloadClick(@Suppress("UNUSED_PARAMETER") view: View) {
        txtResult.text = "Downloading..."
        val observer: ObserverOnNextListener<ResponseBody> = object : ObserverOnNextListener<ResponseBody> {
            override fun onNext(t: ResponseBody) {
                val filePath = FileUtil.createFile(this@HttpActivity, "download.pdf").absolutePath
                FileUtil.copyInputStreamToFile(t.byteStream(), filePath)
                LLog.w(ITAG, "Downloaded to $filePath")
                txtResult.text = "Downloaded to $filePath"
            }

            override fun onError(code: Int, msg: String, e: Throwable) {
                LLog.w(ITAG, "Download error. code=$code msg=$msg")
                txtResult.text = "Download error. code=$code msg=$msg"
            }
        }
        val service = ApiService.getService("http://temp.ho1ho.com", CommonService::class.java)
        ApiSubscribe.subscribe(
            service.downloadFile("%E6%96%B0%E4%B8%9C%E6%96%B9%E6%89%98%E4%B8%9A%E8%80%83%E8%AF%95%E5%AE%98%E6%96%B9%E6%8C%87%E5%8D%97.pdf"),
            NoProgressObserver(observer)
        )
    }

    private fun getMimeType(file: File): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.path)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
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