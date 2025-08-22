@file:Suppress("unused")

package com.leovp.compose.utils

import android.annotation.SuppressLint
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.leovp.log.base.LogOutType
import com.leovp.log.base.d

/**
 * Author: Michael Leo
 * Date: 2025/8/22 08:55
 */

private const val TAG = "Nav"

fun NavHostController.navigateTo(
    route: String,
    arguments: String? = null,
    builder: (NavOptionsBuilder.() -> Unit)? = null,
) {
    val arg: String? = getNavArgs(arguments)
    val realRoute = route.takeIf { arguments == null } ?: "$route/$arg"
    if (builder == null) {
        this.navigate(route = realRoute)
    } else {
        this.navigate(route = realRoute, builder = builder)
    }
}

fun NavHostController.navigateToInRestoreState(route: String, arguments: String? = null) {
    navigateTo(route, arguments) {
        // Whether to restore state when re-selecting a previously selected item
        restoreState = true
    }
}

fun NavHostController.navigateSingleTopTo(route: String, arguments: String? = null) {
    navigateTo(route, arguments) {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        //
        // navController.graph.findStartDestination().id

        // Avoid multiple copies of the same destination when re-selecting the same item
        launchSingleTop = true
        // Whether to restore state when re-selecting a previously selected item
        restoreState = true
    }
}

@SuppressLint("RestrictedApi")
fun outputGraphInfo(route: String, navController: NavHostController) {
    d {
        tag = TAG
        outputType = LogOutType.FRAMEWORK
        block = { "  current: $route  previous=${navController.currentDestination?.route}" }
    }
    for ((i, dest) in navController.currentBackStack.value.withIndex()) {
        d {
            tag = TAG
            outputType = LogOutType.FRAMEWORK
            block = { "    Stack $i: ${dest.destination.route}" }
        }
    }
}

fun getNavArgs(arguments: String? = null): String? = arguments?.trimStart('/')
