@file:Suppress("unused")

package com.leovp.compose.composable.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.Navigator
import com.leovp.log.base.d
import com.leovp.mvvm.event.base.UiEvent

/**
 * Author: Michael Leo
 * Date: 2025/7/15 10:26
 */

private const val TAG = "Nav"

/**
 * Models the navigation actions in the app.
 *
 * DO NOT use this class directly, use [rememberNavigationActions] instead.
 *
 * Attention:
 *
 * Do NOT forget to override `fun navigate(route, arguments, extras)`
 *
 * Attention:
 * This class can't be singleton. Otherwise, it will cause the following exception
 * when you navigate to other screen after you switch the device to dark mode.
 * ```
 * java.lang.IllegalStateException:
 * no event down from INITIALIZED in component NavBackStackEntry(40f53e9f-981c-4e19-bcc0-69c85ed7ce77)
 * destination=Destination(0x88e673a4) route=app_main
 * ```
 */
open class AppNavigation(val navController: NavHostController) {
    @Suppress("unused")
    val currentRouteString: String? get() = navController.currentDestination?.route

    fun upPress() {
        navController.navigateUp()
    }

    fun popBackStack() {
        navController.popBackStack()
    }

    /**
     * Set extras to the current back stack entry's SavedStateHandle.
     * This allows passing complex objects (Parcelable, String, etc.) between screens.
     */
    private fun setExtrasToCurrentEntry(extras: UiEvent.NavExtras?) {
        extras?.data?.forEach { (key, value) ->
            navController.currentBackStackEntry?.savedStateHandle?.set(key, value)
        }
    }

    open fun navigateString(
        route: String,
        arguments: String? = null,
        extras: UiEvent.NavExtras? = null,
    ) {
        d(TAG) { "-> navigate to: $route" }
        d(TAG) { outputGraphInfo(route, navController) }
    }

    open fun <T : Any> navigate(
        route: T,
        builder: NavOptionsBuilder.() -> Unit,
    ) {
        d(TAG) { "-> navigate to: $route" }
        d(TAG) { outputGraphInfo(route, navController) }
    }

    open fun <T : Any> navigate(
        route: T,
        navOptions: NavOptions? = null,
        navigatorExtras: Navigator.Extras? = null,
    ) {
        d(TAG) { "-> navigate to: $route" }
        d(TAG) { outputGraphInfo(route, navController) }
    }

    open fun relogin() {
        // Default no-op, override in subclass to handle relogin
    }
}

/**
 * Usage:
 * ```
 * val navActions: MyAppNavigation = rememberNavigationActions(navController) {
 *     MyAppNavigation(it)
 * }
 * navActions.navigateToHome()
 * ```
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T : AppNavigation> rememberNavigationActions(
    navController: NavHostController,
    factory: (NavHostController) -> T = { AppNavigation(it) as T },
): T = remember { factory(navController) }
