@file:Suppress("unused")

package com.leovp.android.exts

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Author: Michael Leo
 * Date: 2025/9/9 16:12
 */
object LocaleUtil {
    fun changeAppLanguage(
        context: Context,
        localeName: String,
    ) {
        if (localeName == AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
            return
        }
        val locale = Locale.forLanguageTag(localeName)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = LocaleList.forLanguageTags(localeName)
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(localeName),
            )
        }

        // Do not remove this line unless you have thoroughly tested it.
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getCurrentLanguageLocale(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context
                .getSystemService(
                    LocaleManager::class.java,
                ).applicationLocales[0]
                .toLanguageTag()
        } else {
            AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag() ?: "en-US"
        }
}

fun Context.withLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
