package com.leovp.demo

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.leovp.androidbase.exts.android.buildConfigInDebug
import com.leovp.androidbase.utils.ui.ForegroundComponent
import com.leovp.demo.basic_components.examples.koin.*
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
class CustomApplication : MultiDexApplication() {
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
        buildConfigInDebug = BuildConfig.DEBUG

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
        super.attachBaseContext(LangUtil.getInstance(base).setLocale(base))
        Reflection.unseal(base)
        MultiDex.install(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.e("LEO-Application", "=====> onConfigurationChanged setLocale()")
        LangUtil.getInstance(this).setLocale(this)
    }
}