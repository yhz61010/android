@file:Suppress("unused")

package com.leovp.compose.composable

import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Author: Michael Leo
 * Date: 2023/8/31 15:31
 */

/**
 * Determine the drawer state to pass to the modal drawer.
 */
@Composable
fun rememberSizeAwareDrawerState(isExpandedScreen: Boolean): DrawerState {
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    return if (!isExpandedScreen) {
        // If we want to allow showing the drawer, we use a real, remembered drawer
        // state defined above
        drawerState
    } else {
        // If we don't want to allow the drawer to be shown, we provide a drawer state
        // that is locked closed. This is intentionally not remembered, because we
        // don't want to keep track of any changes and always keep it closed
        DrawerState(DrawerValue.Closed)
    }
}

/**
 * Usage:
 *
 * ```
 * Modifier.padding(paddingValues.withoutNavigationBar())
 * ```
 */
@Composable
fun PaddingValues.withoutNavigationBar(): PaddingValues {
    val navBarHeight =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val bottomWithoutNav = (calculateBottomPadding() - navBarHeight).coerceAtLeast(0.dp)

    return PaddingValues(
        start = calculateStartPadding(LayoutDirection.Ltr),
        top = calculateTopPadding(),
        end = calculateEndPadding(LayoutDirection.Ltr),
        bottom = bottomWithoutNav
    )
}

fun navigationBarVisibility(window: Window?, view: View, isShow: Boolean) {
    val insetsController = window?.let {
        WindowCompat.getInsetsController(it, view)
    }
    insetsController?.apply {
        // Hide navigation bar
        if (isShow) {
            show(WindowInsetsCompat.Type.navigationBars())
        } else {
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        // Set behavior to show transient bars by swipe
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
