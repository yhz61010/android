@file:Suppress("unused")

package com.leovp.compose.composable.nav

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
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
open class AppNavigation(private val navController: NavHostController) {
    @Suppress("unused")
    val currentRoute: String? get() = navController.currentDestination?.route

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

    open fun navigate(
        route: String,
        arguments: String? = null,
        extras: UiEvent.NavExtras? = null,
    ) {
        d(TAG) { "-> navigate to: $route" }
        d(TAG) { outputGraphInfo(route, navController) }
    }

    open fun relogin() {
        // Default no-op, override in subclass to handle relogin
    }
}

private fun NavHostController.navigateSingleTopTo(
    route: String,
    arguments: String? = null,
) {
    val arg: String? = arguments?.trimStart('/')
    this.navigate(route.takeIf { arguments == null } ?: "$route/$arg") {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(route) { inclusive = false }
        // Avoid multiple copies of the same destination when re-selecting the same item
        launchSingleTop = true
        // Whether to restore state when re-selecting a previously selected item
        restoreState = false
    }
}

private fun NavHostController.navigateTo(
    route: String,
    arguments: String? = null,
) {
    val arg: String? = arguments?.trimStart('/')
    this.navigate(route.takeIf { arguments == null } ?: "$route/$arg") {
        // Whether to restore state when re-selecting a previously selected item
        restoreState = true
    }
}

@Composable
fun rememberNavigationActions(navController: NavHostController): AppNavigation =
    remember { AppNavigation(navController) }

@SuppressLint("RestrictedApi")
private fun outputGraphInfo(route: String, navController: NavHostController) {
    d(TAG) {
        "  current: $route  previous=${navController.currentDestination?.route}"
    }
    for ((i, dest) in navController.currentBackStack.value.withIndex()) {
        d(TAG) { "    Stack $i: ${dest.destination.route}" }
    }
}
