package com.leovp.androidbase.utils.system

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.sharedPrefs
import com.leovp.androidbase.exts.kotlin.fail
import com.leovp.androidbase.utils.log.LogContext
import java.util.*

/**
 * Author: Michael Leo
 * Date: 2021/6/4 13:05
 */
object LangUtil {
    const val INTENT_APP_LANG_CHANGE = "app-lang-change-broadcast"
    private const val PREF_KEY_LANGUAGE = "language"

    private var currentAppLang: String? = null

    @Synchronized
    fun saveLanguage(language: String) {
        app.sharedPrefs.edit().run { putString(PREF_KEY_LANGUAGE, language); apply() }
        currentAppLang = language
    }

    @Synchronized
    fun saveLanguageAndRefreshUI(language: String) {
        saveLanguage(language)
        LocalBroadcastManager.getInstance(app).sendBroadcast(Intent(INTENT_APP_LANG_CHANGE))
    }

    @Synchronized
    fun getAppLanguage(): String {
        if (currentAppLang == null) {
            currentAppLang = app.sharedPrefs.getString(PREF_KEY_LANGUAGE, "en") ?: "en"
        }
        return currentAppLang ?: fail("Unexpected exception on getLanguageFromPreference()")
    }

    /**
     * @param ctx Most of time, this context should be `Activity` context.
     */
    fun changeAppLanguage(ctx: Context) {
        val savedLang: String = getAppLanguage()
        if (savedLang.isBlank()) {
            if (LogContext.enableLog) LogContext.log.w("Can not get language from preference.")
            return
        }
        val locale: Locale = if (savedLang.contains("_")) {
            val langCountry = savedLang.split("_".toRegex())
            Locale(langCountry[0], langCountry[1])
        } else {
            Locale(savedLang)
        }
        val res: Resources = ctx.resources
        val conf = res.configuration
        conf.locale = locale
        res.updateConfiguration(conf, res.displayMetrics)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Locale.setDefault(locale)
            val localeList = LocaleList(locale)
            conf.setLocales(localeList)
            ctx.createConfigurationContext(conf)
        }
    }
}