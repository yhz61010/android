package com.leovp.demo

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.leovp.androidbase.framework.BaseApplication
import com.leovp.demo.basic_components.examples.koin.*
import com.leovp.lib_common_android.exts.LeoToast
import com.leovp.lib_common_android.exts.ToastConfig
import com.leovp.lib_common_android.ui.ForegroundComponent
import com.leovp.lib_common_android.utils.LangUtil
import com.leovp.log_sdk.LLog
import com.leovp.log_sdk.LogContext
import com.leovp.pref_sdk.LPref
import com.leovp.pref_sdk.PrefContext
import io.reactivex.plugins.RxJavaPlugins
import me.weishu.reflection.Reflection
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Author: Michael Leo
 * Date: 20-5-18 下午5:33
 */
class CustomApplication : BaseApplication() {
    companion object {
        private const val TAG_PREFIX = "LEO"
    }

    private val appModules = module {
        // single instance of HelloRepository
        single<HelloRepository> { HelloRepositoryImpl() }

        // Simple Presenter Factory
        factory { MySimplePresenter(repo = get()) }

        factory { (name: String, type: String) -> Engine(name, type) }
        factory { params -> Wheel(identity = params.get()) }
        factory { (engine: Engine, wheels: List<Wheel>) -> Car(engine, wheels) }
    }

    override fun onCreate() {
        super.onCreate()

        LeoToast.getInstance(this).apply {
            config = ToastConfig(BuildConfig.DEBUG, R.mipmap.ic_launcher_round)
            initForegroundComponentForToast(this@CustomApplication)
        }

        startKoin {
            //            androidLogger(if (buildConfigInDebug) Level.DEBUG else Level.INFO)
            androidContext(this@CustomApplication)
            modules(appModules)
        }

        //        SslUtils.hostnames = arrayOf("postman-echo.com")
        //        SslUtils.certificateInputStream = assets.open("cert/postman-echo.com.crt")

        ForegroundComponent.init(this, 0L)

        // InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler { }

        // LogContext.setLogImp(CLog().apply { init(this@CustomApplication) })
        LogContext.setLogImp(LLog(TAG_PREFIX))
        PrefContext.setPrefImp(LPref(this))
    }

    override fun attachBaseContext(base: Context) {
        Log.i("$TAG_PREFIX-Application", "=====> attachBaseContext setLocale()")
        super.attachBaseContext(LangUtil.getInstance(base).setAppLanguage(base))
        Reflection.unseal(base)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i("LEO-Application", "=====> onConfigurationChanged setLocale()")
    }
}
