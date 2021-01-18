package com.leovp.leoandroidbaseutil

import androidx.multidex.MultiDexApplication
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.utils.log.LLog
import com.leovp.androidbase.utils.log.LogContext
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
        app = this
        instance = this

//        SslUtils.hostnames = arrayOf("postman-echo.com")
//        SslUtils.certificateInputStream = assets.open("cert/postman-echo.com.crt")

        ForegroundComponent.init(this, 0L)

//        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler { }

        ToastUtil.init(this)

        LogContext.setLogImp(LLog("LEO"))
//        LogContext.setLogImp(CLog().apply { init(this@CustomApplication) })
    }

    companion object {
        lateinit var instance: CustomApplication
            private set
    }
}