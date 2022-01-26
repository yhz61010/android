package com.leovp.leoandroidbaseutil

import android.content.Context
import android.content.res.Configuration
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.leovp.androidbase.exts.android.buildConfigInDebug
import com.leovp.androidbase.utils.pref.LPref
import com.leovp.androidbase.utils.pref.PrefContext
import com.leovp.androidbase.utils.ui.ForegroundComponent
import com.leovp.leoandroidbaseutil.basic_components.examples.koin.*
import com.leovp.lib_common_android.utils.LangUtil
import com.leovp.log_sdk.LLog
import com.leovp.log_sdk.LogContext
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

        //        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler { }

        LogContext.setLogImp(LLog("LEO")) //        LogContext.setLogImp(CLog().apply { init(this@CustomApplication) })
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