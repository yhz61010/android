package com.leovp.androidbase.utils.system

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.sharedPrefs
import com.leovp.min_base_sdk.fail
import java.util.*

/**
 * Attention:
 * For Chinese language(Simplified Chinese, Traditional Chinese),
 * - If you set language in `zh_CN`, you should create `values-zh-rCN` folder in `values` folder.
 * - If you set language in `zh`, you should create `values-zh` folder in `values` folder.
 *
 * Locale.getDefault().getLanguage()       ---> en
 * Locale.getDefault().getISO3Language()   ---> eng
 * Locale.getDefault().getCountry()        ---> US
 * Locale.getDefault().getISO3Country()    ---> USA
 * Locale.getDefault().getDisplayCountry() ---> United States
 * Locale.getDefault().getDisplayName()    ---> English (United States)
 * Locale.getDefault().toString()          ---> en_US
 * Locale.getDefault().getDisplayLanguage()---> English
 * Locale.getDefault().toLanguageTag()     ---> en-US
 *
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

    fun getLanguageCountryCode(locale: Locale): String {
        val country = locale.country
        return if (country.isBlank()) {
            locale.language
        } else {
            "${locale.language}_$country"
        }
    }

    /**
     * @return The result is like: `zh_CN` or `zh_CN_#Hans`. Please notice the difference with [getDeviceLanguageCountryCode].
     */
    fun getDefaultLanguageFullCode(): String = Locale.getDefault().toString()

    /**
     * @return The result is only contains language and country, just like this: `zh_CN`.
     */
    fun getDefaultLanguageCountryCode(): String = getLanguageCountryCode(Locale.getDefault())

    /**
     * @return The result is like: `中文`
     */
    fun getDefaultDisplayLanguage(): String = Locale.getDefault().displayLanguage

    /**
     * @return If you call `toString()` on result, you will get something like: zh_CN_#Hans
     */
    fun getDeviceLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Resources.getSystem().configuration.locale
        }
    }

    /**
     * @return The result is only contains language and country, just like this: `zh_CN`. Please notice the difference with [getDefaultLanguageFullCode].
     */
    fun getDeviceLanguageCountryCode(): String = getLanguageCountryCode(getDeviceLocale())

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
     * This method should be called on `onCreate`.
     *
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