package com.ho1ho.leoandroidbaseutil

import androidx.multidex.MultiDexApplication
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.ui.ForegroundComponent
import com.ho1ho.androidbase.utils.ui.ToastUtil
import io.reactivex.plugins.RxJavaPlugins

/**
 * Author: Michael Leo
 * Date: 20-5-18 下午5:33
 */
class CustomApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        CLog.init(this)
        ForegroundComponent.init(this, 0L)

//        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler { }

        ToastUtil.init(this)
    }

    companion object {
        lateinit var instance: CustomApplication
            private set
    }
}