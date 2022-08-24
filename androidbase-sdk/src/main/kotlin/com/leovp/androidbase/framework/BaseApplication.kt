package com.leovp.androidbase.framework

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.leovp.android.utils.LangUtil

/**
 * This class has already enabled Custom Language feature.
 *
 * You should import the following dependency:
 * ```
 * implementation "androidx.multidex:multidex:$rootProject.ext.multidexVersion"
 * ```
 *
 * and enable it in you module `build.gradle`:
 * ```
 * android {
 *      defaultConfig {
 *          // Other settings
 *
 *          multiDexEnabled true
 *      }
 *
 *      // Other settings
 * }
 * ```
 *
 *
 * Author: Michael Leo
 * Date: 2022/6/29 09:58
 */
open class BaseApplication : MultiDexApplication() {
    companion object {
        private const val TAG = "BaseApplication"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangUtil.getInstance(base).setAppLanguage(base))
        MultiDex.install(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LangUtil.getInstance(this).setAppLanguage(this)
    }

    override fun onLowMemory() {
        Log.w(TAG, "=====> onLowMemory() <=====")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "=====> onTrimMemory($level) <=====")
        super.onTrimMemory(level)
    }
}
