@file:Suppress("unused", "LongParameterList", "CyclomaticComplexMethod")

package com.leovp.compose.composable.event.base

import android.app.Activity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.audio.SoundEffectPlayer
import com.leovp.compose.composable.nav.AppNavigation
import com.leovp.compose.composable.navigationBarVisibility
import com.leovp.mvvm.event.base.UiEvent
import kotlinx.coroutines.flow.Flow

/**
 * Author: Michael Leo
 * Date: 2025/8/22 13:29
 */
@Suppress("FunctionNaming")
@Composable
fun GenericEventHandler(
    events: Flow<UiEvent>,
    navController: AppNavigation? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    loadingDialogContent: @Composable (() -> Unit)? = null,
//    hideLoadingContent: @Composable (() -> Unit)? = null,
    dialogContent: @Composable (
        (dialogState: MutableState<UiEvent.ShowDialog?>) -> Unit
    )? = null,
    onCustomEvent: (suspend (UiEvent) -> Unit)? = null,
) {
    val localizedCtx = LocalContext.current
    val localRes = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Hide system navigation bar and keep it hidden even when keyboard appears
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        navigationBarVisibility(window = window, view = view, isShow = false)
        onDispose {
            navigationBarVisibility(window = window, view = view, isShow = false)
        }
    }

    var showLoadingDialog by remember { mutableStateOf(false) }
    val dialogState = remember { mutableStateOf<UiEvent.ShowDialog?>(null) }

    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { event ->
                when (event) {
                    is UiEvent.ShowToast -> {
                        event.message?.let { msg ->
                            localizedCtx.toast(
                                msg = msg,
                                error = event.isError,
                                longDuration = event.longDuration
                            )
                        }
                        event.resId?.let { msgRes ->
                            localizedCtx.toast(
                                resId = msgRes,
                                error = event.isError,
                                longDuration = event.longDuration
                            )
                        }
                    }

                    is UiEvent.ShowSnackbar -> {
                        val msg = event.message ?: event.resId?.let { localRes.getString(it) } ?: ""
                        snackbarHostState
                            .showSnackbar(
                                message = msg,
                                actionLabel = event.actionLabel,
                                duration = SnackbarDuration.Short
                            ).let { result ->
                                if (result == SnackbarResult.ActionPerformed) {
                                    event.onAction?.invoke()
                                }
                            }
                    }

                    is UiEvent.NavigateString ->
                        navController?.navigateString(
                            event.route,
                            event.arguments,
                            event.extras
                        )

                    is UiEvent.NavigateRouteBuilder<*> ->
                        navController?.navigate(
                            event.route as Any,
                            event.builder
                        )

                    is UiEvent.Navigate<*> ->
                        navController?.navigate(
                            event.route as Any,
                            event.navOptions,
                            event.navigatorExtras
                        )

                    UiEvent.NavigateBack -> navController?.popBackStack()

                    UiEvent.FinishActivity -> (localizedCtx as? Activity)?.finish()

                    is UiEvent.NavigationBar -> {
                        val window = (localizedCtx as? Activity)?.window
                        navigationBarVisibility(
                            window = window,
                            view = view,
                            isShow = event.isShow
                        )
                    }

                    UiEvent.ShowLoading -> showLoadingDialog = true

                    UiEvent.HideLoading -> showLoadingDialog = false

                    is UiEvent.ShowDialog -> dialogState.value = event

                    UiEvent.DismissDialog -> dialogState.value = null

                    is UiEvent.PlayEffect -> SoundEffectPlayer.play(
                        localizedCtx,
                        event.fileName
                    )

                    UiEvent.Relogin -> navController?.relogin()

                    else -> onCustomEvent?.invoke(event)
                }
            }
        }
    }

    // Loading Dialog
    if (showLoadingDialog) {
        loadingDialogContent?.invoke()
    }
    // else {
    //     hideLoadingContent?.invoke()
    // }

    // Custom Dialog
    dialogState.value?.let {
        dialogContent?.invoke(dialogState)
    }
}
