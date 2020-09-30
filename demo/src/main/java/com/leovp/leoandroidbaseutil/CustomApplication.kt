package com.leovp.leoandroidbaseutil

import androidx.multidex.MultiDexApplication
import com.leovp.androidbase.utils.ui.ForegroundComponent
import com.leovp.androidbase.utils.ui.ToastUtil
import io.reactivex.plugins.RxJavaPlugins
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.x.androidXModule

/**
 * Author: Michael Leo
 * Date: 20-5-18 下午5:33
 */
class CustomApplication : MultiDexApplication(), DIAware {
    override val di = DI.lazy {
        import(androidXModule(this@CustomApplication))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        ForegroundComponent.init(this, 0L)

//        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler { }

        ToastUtil.init(this)

//        val file = File(FileUtil.getBaseDirString(this, "output"))
//        file.mkdirs()
//        videoH264OsForDebug = BufferedOutputStream(FileOutputStream(File(file, "screen.h264")))
//        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
//            override fun onActivityResumed(activity: Activity) {
//                (screenProcessor as ScreenshotStrategy).startRecord(activity.window)
//            }
//
//            override fun onActivityPaused(activity: Activity) {
//                (screenProcessor as ScreenshotStrategy).onStop()
//            }
//
//            override fun onActivityStarted(activity: Activity) = Unit
//            override fun onActivityDestroyed(activity: Activity) = Unit
//            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
//            override fun onActivityStopped(activity: Activity) = Unit
//            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
//        })
    }

//    private lateinit var videoH264OsForDebug: BufferedOutputStream
//    val screenProcessor: ScreenProcessor by lazy {
//        val screenInfo = DeviceUtil.getResolution(this)
//        val setting = ScreenShareSetting(
//            (screenInfo.x * 0.8F).toInt() / 16 * 16,
//            (screenInfo.y * 0.8F).toInt() / 16 * 16,
//            DeviceUtil.getDensity(this)
//        )
//        // Seems does not work. Check bellow setKeyFrameRate
//        setting.fps = 3f
//
//        ScreenCapture.Builder(
//            setting.width, // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
//            setting.height, // 800 1024 1280
//            setting.dpi,
//            null,
//            ScreenCapture.SCREEN_CAPTURE_TYPE_IMAGE,
//            screenDataListener
//        ).setFps(setting.fps)
//            .setKeyFrameRate(3)
//            .setQuality(80)
//            .setSampleSize(1)
//            .build()
//    }
//
//    fun closeDebugOutputFile() {
//        videoH264OsForDebug.flush()
//        videoH264OsForDebug.close()
//        screenProcessor.onRelease()
//    }
//
//    private val screenDataListener = object : ScreenDataListener {
//        override fun onDataUpdate(buffer: Any, flags: Int) {
//            val buf = buffer as ByteArray
//            when (flags) {
//                MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> LLog.i(ITAG, "Get h264 data[${buf.size}]=${buf.toHexString(",")}")
//                MediaCodec.BUFFER_FLAG_KEY_FRAME -> LLog.i(ITAG, "Get h264 data Key-Frame[${buf.size}]")
//                else -> LLog.i(ITAG, "Get h264 data[${buf.size}]")
//            }
//            videoH264OsForDebug.write(buf)
//        }
//    }

    companion object {
        lateinit var instance: CustomApplication
            private set
    }
}