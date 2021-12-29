package com.leovp.leoandroidbaseutil

import android.content.Context
import android.content.res.Configuration
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.leovp.androidbase.exts.android.buildConfigInDebug
import com.leovp.androidbase.utils.pref.LPref
import com.leovp.androidbase.utils.pref.PrefContext
import com.leovp.androidbase.utils.ui.ForegroundComponent
import com.leovp.lib_common_android.utils.LangUtil
import com.leovp.log_sdk.LLog
import com.leovp.log_sdk.LogContext
import io.reactivex.plugins.RxJavaPlugins
import me.weishu.reflection.Reflection
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
        buildConfigInDebug = BuildConfig.DEBUG

//        SslUtils.hostnames = arrayOf("postman-echo.com")
//        SslUtils.certificateInputStream = assets.open("cert/postman-echo.com.crt")

        ForegroundComponent.init(this, 0L)

//        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler { }

        LogContext.setLogImp(LLog("LEO"))
//        LogContext.setLogImp(CLog().apply { init(this@CustomApplication) })
        PrefContext.setPrefImp(LPref(this))
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangUtil.getInstance(base).setLocale(base))
        Reflection.unseal(base)
        MultiDex.install(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LangUtil.getInstance(this).setLocale(this)
    }
}