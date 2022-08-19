package com.leovp.androidbase.framework

import android.app.Service
import android.content.Context
import com.leovp.lib_common_android.utils.LangUtil

/**
 * This class has already enabled Custom Language feature.
 *
 * Author: Michael Leo
 * Date: 2022/6/29 10:05
 */
abstract class BaseService : Service() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LangUtil.getInstance(base).setAppLanguage(base))
    }
}
