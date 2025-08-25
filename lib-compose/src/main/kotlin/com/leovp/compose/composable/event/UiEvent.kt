package com.leovp.compose.composable.event

/**
 * Author: Michael Leo
 * Date: 2025/8/21 10:33
 */

sealed interface UiEvent {
    data class ShowToast(
        val message: String,
        val isError: Boolean = false,
        val longDuration: Boolean = false,
    ) : UiEvent

    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : UiEvent

    data class Navigate(
        val route: String,
        val arguments: String? = null,
    ) : UiEvent

    object NavigateBack : UiEvent

    object ShowLoading : UiEvent

    object HideLoading : UiEvent

    data class ShowDialog(
        val title: String,
        val message: String,
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
