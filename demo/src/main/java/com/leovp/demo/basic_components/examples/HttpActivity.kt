package com.leovp.demo.basic_components.examples

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityHttpBinding
import com.leovp.http_sdk.retrofit.ApiService
import com.leovp.http_sdk.retrofit.ApiSubscribe
import com.leovp.http_sdk.retrofit.iter.ObserverOnNextListener
import com.leovp.http_sdk.retrofit.observers.NoProgressObserver
import com.leovp.lib_common_android.exts.createFile
import com.leovp.lib_common_android.exts.saveRawResourceToFile
import com.leovp.lib_common_android.exts.toFile
import com.leovp.lib_json.toJsonString
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import io.reactivex.Observable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.http.*
import java.io.File


class HttpActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    private lateinit var binding: ActivityHttpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHttpBinding.inflate(layoutInflater).apply { setContentView(root) }
    }

    interface CommonService {
        @GET("/status/{id}")
        fun getData(@Path("id") param: String): Observable<String>

        //        @FormUrlEncoded
        @Headers("Authorization:APPCODE Add-you-app-code-here")
        @POST("/post")
        fun postData(@Body body: String): Observable<String>

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
        val observer: ObserverOnNextListener<String> = object : ObserverOnNextListener<String?> {
            override fun onNext(t: String?) {
                LogContext.log.w(ITAG, "Response bean=${t?.toJsonString()}")
                binding.txtResult.text = t
            }
        }
        val service = ApiService.getService("https://postman-echo.com", CommonService::class.java)
        ApiSubscribe.subscribe(service.getData("200"), NoProgressObserver(observer))
    }

    @SuppressLint("SetTextI18n")
    fun onPostClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.txtResult.text = "It will cost several seconds. Please be patient..."
        val observer: ObserverOnNextListener<String> = object : ObserverOnNextListener<String?> {
            override fun onNext(t: String?) {
                LogContext.log.w(ITAG, "Response=$t")
                binding.txtResult.text = t
            }

            override fun onError(code: Int, msg: String, e: Throwable) {
                LogContext.log.w(ITAG, "Request error. code=$code msg=$msg")
                binding.txtResult.text = "Request error. code=$code msg=$msg"
            }
        }
        val service = ApiService.getService("https://postman-echo.com", CommonService::class.java)
        ApiSubscribe.subscribe(
            service.postData("This is expected to be sent back as part of response body."),
            NoProgressObserver(observer)
        )
    }

    @SuppressLint("SetTextI18n")
    fun onUploadClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.txtResult.text = "It may cost several seconds. Please be patient..."
        val observer: ObserverOnNextListener<String> = object : ObserverOnNextListener<String?> {
            override fun onNext(t: String?) {
                LogContext.log.w(ITAG, "Response=$t")
                binding.txtResult.text = "Upload Done"
            }

            override fun onError(code: Int, msg: String, e: Throwable) {
                LogContext.log.w(ITAG, "Request error. code=$code msg=$msg")
                binding.txtResult.text = "Request error. code=$code msg=$msg"
            }
        }

        val fileFullPath = saveRawResourceToFile(R.raw.tears_400_x265, getExternalFilesDir(null)!!.absolutePath, "h265.mp4")
        val sourceFile = File(fileFullPath)
        val mimeType = getMimeType(sourceFile)
        if (mimeType == null) {
            LogContext.log.e("File upload error", "Not able to get mime type")
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

    @SuppressLint("SetTextI18n")
    fun onDownloadClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.txtResult.text = "Downloading..."
        val observer: ObserverOnNextListener<ResponseBody> = object : ObserverOnNextListener<ResponseBody?> {
            override fun onNext(t: ResponseBody?) {
                val filePath = this@HttpActivity.createFile("download.pdf").absolutePath
                t!!.byteStream().toFile(filePath)
                LogContext.log.w(ITAG, "Downloaded to $filePath")
                binding.txtResult.text = "Downloaded to $filePath"
            }

            override fun onError(code: Int, msg: String, e: Throwable) {
                LogContext.log.w(ITAG, "Download error. code=$code msg=$msg")
                binding.txtResult.text = "Download error. code=$code msg=$msg"
            }
        }
        val service = ApiService.getService("http://temp.leovp.com", CommonService::class.java)
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
}