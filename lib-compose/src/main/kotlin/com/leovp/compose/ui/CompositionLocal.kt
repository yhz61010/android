@file:Suppress("unused")

package com.leovp.compose.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.leovp.android.exts.withLocale
import com.leovp.compose.composable.nav.AppNavigation
import java.util.Locale

/**
 * Author: Michael Leo
 * Date: 2025/9/15 10:53
 */

val LocalNavigationActions =
    compositionLocalOf<AppNavigation> {
        error("No NavigationActions provided")
    }

// val LocalUserSession =
//     compositionLocalOf<UserSession> { error("No store session provided") }

val LocalLocalizedContext =
    staticCompositionLocalOf<Context> {
        error("No LocalizedContext provided")
    }

/**
 * ```
 * const val LANGUAGE_DEFAULT = "ja"
 * const val LANGUAGE_ZH = "zh"
 * const val LANGUAGE_EN = "en"
 * const val LANGUAGE_KO = "ko"
 * ```
 */
val LocalAppLocale =
    staticCompositionLocalOf { Locale.forLanguageTag("en") }

@Suppress("FunctionNaming")
@Composable
fun LocalizedApp(
    locale: Locale,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val localizedContext = remember(locale) { context.withLocale(locale) }

    CompositionLocalProvider(
        LocalAppLocale provides locale,
        LocalLocalizedContext provides localizedContext,
    ) {
        content()
    }
}
