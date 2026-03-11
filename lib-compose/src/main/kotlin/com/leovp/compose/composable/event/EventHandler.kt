package com.leovp.compose.composable.event

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import com.leovp.compose.composable.event.base.UiEvent
import com.leovp.compose.composable.nav.AppNavigation
import kotlinx.coroutines.flow.Flow

/**
 * Author: Michael Leo
 * Date: 2025/8/21 10:43
 */
@Suppress("FunctionNaming")
@Composable
fun EventHandler(
    events: Flow<UiEvent>,
    navController: AppNavigation? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    loadingDialog: @Composable (() -> Unit)? = null,
    dialogContent: @Composable ((dialogState: MutableState<UiEvent.ShowDialog?>) -> Unit)? = null,
) {
    BackHandler(enabled = true, onBack = {})
    GenericEventHandler(
        events = events,
        navController = navController,
        snackbarHostState = snackbarHostState,
        loadingDialogContent = loadingDialog,
        // hideLoadingContent = null,
        dialogContent = dialogContent
    )
}
