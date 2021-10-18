package com.leovp.androidbase.utils.system

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.leovp.androidbase.exts.android.sharedPrefs
import com.leovp.min_base_sdk.fail
import java.util.*

/**
 * Attention:
 * For Chinese language(Simplified Chinese, Traditional Chinese),
 * - If you set language in `zh_CN`, you should create `values-zh-rCN` folder in `values` folder.
 * - If you set language in `zh`, you should create `values-zh` folder in `values` folder.
 *
 * - Locale.getDefault().getLanguage()       ---> en
 * - Locale.getDefault().getISO3Language()   ---> eng
 * - Locale.getDefault().getCountry()        ---> US
 * - Locale.getDefault().getISO3Country()    ---> USA
 * - Locale.getDefault().getDisplayCountry() ---> United States
 * - Locale.getDefault().getDisplayName()    ---> English (United States)
 * - Locale.getDefault().toString()          ---> en_US
 * - Locale.getDefault().getDisplayLanguage()---> English
 * - Locale.getDefault().toLanguageTag()     ---> en-US
 *
 * **HOW TO USE**
 *
 * Add these codes in your custom Application:
 * ```kotlin
 * override fun attachBaseContext(base: Context) {
 *      super.attachBaseContext(LangUtil.setLocale(base))
 * }
 *
 *   override fun onConfigurationChanged(newConfig: Configuration) {
 *      super.onConfigurationChanged(newConfig)
 *      LangUtil.setLocale(this)
 * }
 * ```
 *
 * Add the following codes into your base activity:
 *
 * ```kotlin
 * private val appLangChangeReceiver = object : BroadcastReceiver() {
 *     override fun onReceive(context: Context, intent: Intent?) {
 *         recreate()
 *     }
 * }
 *
 * override fun attachBaseContext(base: Context) {
 *      super.attachBaseContext(LangUtil.setLocale(base))
 * }
 *
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     LocalBroadcastManager.getInstance(this).registerReceiver(appLangChangeReceiver, IntentFilter(LangUtil.INTENT_APP_LANG_CHANGE))
 *     LangUtil.setLocale(this)
 * }
 *
 * override fun onDestroy() {
 *     LocalBroadcastManager.getInstance(this).unregisterReceiver(appLangChangeReceiver)
 *     super.onDestroy()
 * }
 * ```
 *
 * If you use text string in your Service, add these lines in it:
 * ```kotlin
 * override fun attachBaseContext(base: Context) {
 *     super.attachBaseContext(LangUtil.setLocale(base))
 * }
 * ```
 *
 * When you want to change app language, it's easy:
 * ```kotlin
 * LangUtil.setLocale(context, LangUtil.getLocale("zh_CN")!!, refreshUI = true)
 * ```
 *
 * Author: Michael Leo
 * Date: 2021/6/4 13:05
 */
object LangUtil {
    const val INTENT_APP_LANG_CHANGE = "app-lang-change-broadcast"
    private const val PREF_KEY_LANGUAGE = "language"

    @Volatile
    private var currentAppLang: Locale? = null

    /**
     * Most of time, just call this method is enough.
     */
    @Synchronized
    fun setLocale(ctx: Context, targetLocale: Locale = getAppLanguage(ctx), refreshUI: Boolean = false): Context {
        saveLanguage(ctx, targetLocale)
        val context = updateResources(ctx, targetLocale)
        if (refreshUI) LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(INTENT_APP_LANG_CHANGE))
        return context
    }

    /**
     * This method should be called on `onCreate`.
     *
     * @param ctx Most of time, this context should be `Activity` context.
     */
    private fun updateResources(ctx: Context, targetLocale: Locale): Context {
        val res: Resources = ctx.resources
        val conf = res.configuration
        // There two line codes(although they have already deprecated) are the magic.
        // If I comment them, the text string in Service will not be shown in correct language.
        conf.locale = targetLocale
        res.updateConfiguration(conf, res.displayMetrics)
        Locale.setDefault(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setLocaleForApi24(conf, targetLocale)
        }
        ctx.createConfigurationContext(conf)
        return ctx
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun setLocaleForApi24(config: Configuration, targetLocale: Locale) {
        val set: MutableSet<Locale> = LinkedHashSet()
        // Bring the target locale to the front of the list
        set.add(targetLocale)
        val all = LocaleList.getDefault()
        for (i in 0 until all.size()) {
            // Append other locales supported by the user
            set.add(all[i])
        }
        val locales = set.toTypedArray()
        config.setLocales(LocaleList(*locales))
    }

    // ================================

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
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }

    /**
     * @return The result is only contains language and country, just like this: `zh_CN`. Please notice the difference with [getDefaultLanguageFullCode].
     */
    fun getDeviceLanguageCountryCode(): String = getLanguageCountryCode(getDeviceLocale())

    @Synchronized
    fun saveLanguage(ctx: Context, language: Locale) {
        // Use commit() instead of apply(), because sometimes we kill the application process
        // immediately that prevents apply() from finishing
        // https://github.com/YarikSOffice/LanguageTest/blob/master/app/src/main/java/com/yariksoffice/languagetest/LocaleManager.java
        ctx.sharedPrefs.edit().run { putString(PREF_KEY_LANGUAGE, language.toString()); commit() }
        currentAppLang = language
    }

    @Synchronized
    fun getAppLanguage(ctx: Context): Locale {
        if (currentAppLang == null) {
            currentAppLang = getLocale(ctx.sharedPrefs.getString(PREF_KEY_LANGUAGE, "en") ?: "en")
        }
        return currentAppLang ?: fail("Unexpected exception on getLanguageFromPreference()")
    }
}