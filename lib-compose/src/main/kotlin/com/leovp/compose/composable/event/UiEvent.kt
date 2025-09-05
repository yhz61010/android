@file:Suppress("unused")

package com.leovp.compose.composable.event

import androidx.annotation.Keep
import androidx.annotation.StringRes

/**
 * Author: Michael Leo
 * Date: 2025/8/21 10:33
 */

sealed interface UiEvent {
    @Keep
    data class ShowToast(
        val message: String,
        val isError: Boolean = false,
        val longDuration: Boolean = false,
        val isDebug: Boolean = false,
    ) : UiEvent

    @Keep
    data class ShowResToast(
        @param:StringRes val resId: Int,
        val isError: Boolean = false,
        val longDuration: Boolean = false,
        val isDebug: Boolean = false,
    ) : UiEvent

    @Keep
    data class ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null,) :
        UiEvent

    @Keep
    data class ShowResSnackbar(
        @param:StringRes val resId: Int,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : UiEvent

    @Keep
    data class Navigate(val route: String, val arguments: String? = null) : UiEvent

    object NavigateBack : UiEvent

    object ShowLoading : UiEvent

    object HideLoading : UiEvent

    @Keep
    data class ShowDialog(
        val title: String,
        val message: String,
        val positiveButton: String,
        val negativeButton: String? = null,
        val onPositive: () -> Unit,
        val onNegative: () -> Unit = {},
    ) : UiEvent

    @Keep
    data class ShowResDialog(
        @param:StringRes val titleResId: Int,
        @param:StringRes val messageResId: Int,
        val positiveButton: String,
        val negativeButton: String? = null,
        val onPositive: () -> Unit,
        val onNegative: () -> Unit = {},
    ) : UiEvent
}

// enum class ToastDuration {
//     SHORT,
//     LONG,
// }
