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
import java.util.*

/**
 * Author: Michael Leo
 * Date: 2021/6/4 13:05
 */
object LangUtil {
    const val INTENT_APP_LANG_CHANGE = "app-lang-change-broadcast"
    private const val PREF_KEY_LANGUAGE = "language"

    @Volatile
    private var currentAppLang: Locale? = null

    fun getLocale(languageAndCountry: String): Locale? {
        return runCatching {
            if (languageAndCountry.contains("_")) {
                val langCountry = languageAndCountry.split("_".toRegex())
                Locale(langCountry[0], langCountry[1])
            } else {
                Locale(languageAndCountry)
            }
        }.getOrNull()
    }

    /**
     * Most of time, you should call the handy method [saveLanguageAndRefreshUI].
     */
    @Synchronized
    fun saveLanguage(language: Locale) {
        app.sharedPrefs.edit().run { putString(PREF_KEY_LANGUAGE, language.toString()); apply() }
        currentAppLang = language
    }

    @Synchronized
    fun saveLanguageAndRefreshUI(language: Locale) {
        saveLanguage(language)
        LocalBroadcastManager.getInstance(app).sendBroadcast(Intent(INTENT_APP_LANG_CHANGE))
    }

    @Synchronized
    fun getAppLanguage(): Locale {
        if (currentAppLang == null) {
            currentAppLang = getLocale(app.sharedPrefs.getString(PREF_KEY_LANGUAGE, "en") ?: "en")
        }
        return currentAppLang ?: fail("Unexpected exception on getLanguageFromPreference()")
    }

    /**
     * @param ctx Most of time, this context should be `Activity` context.
     */
    fun changeAppLanguage(ctx: Context) {
        val savedLang: Locale = getAppLanguage()
        val res: Resources = ctx.resources
        val conf = res.configuration
        conf.locale = savedLang
        res.updateConfiguration(conf, res.displayMetrics)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Locale.setDefault(savedLang)
            val localeList = LocaleList(savedLang)
            conf.setLocales(localeList)
            ctx.createConfigurationContext(conf)
        }
    }
}