@file:Suppress("unused")

package com.leovp.mvvm.event.base

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes

/**
 * Author: Michael Leo
 * Date: 2025/8/21 10:33
 */

interface UiEvent {
    @Keep
    data class ShowToast(
        val message: String? = null,
        @param:StringRes val resId: Int? = null,

        val isError: Boolean = false,
        val longDuration: Boolean = false,
        val isDebug: Boolean = false,
        val origin: Boolean = false,
    ) : UiEvent

    @Keep
    data class ShowSnackbar(
        val message: String? = null,
        @param:StringRes val resId: Int? = null,

        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : UiEvent

    @Keep
    data class Navigate(
        val route: String,
        val arguments: String? = null,
        val extras: NavExtras? = null,
    ) : UiEvent

    data object NavigateBack : UiEvent

    data object FinishActivity : UiEvent

    @Keep
    data class NavigationBar(val isShow: Boolean) : UiEvent

    data object ShowLoading : UiEvent

    data object HideLoading : UiEvent

    data object DismissDialog : UiEvent

    data object Relogin : UiEvent

    @Keep
    data class ShowDialog(
        @param:DrawableRes val iconResId: Int? = null,

        val title: String? = null,
        val message: String? = null,
        val positiveButtonText: String? = null,
        val negativeButtonText: String? = null,

        @param:StringRes val titleResId: Int? = null,
        @param:StringRes val messageResId: Int? = null,
        @param:StringRes val positiveButtonTextResId: Int? = null,
        @param:StringRes val negativeButtonTextResId: Int? = null,

        val onPositive: () -> Unit,
        val onNegative: (() -> Unit)? = null,
        val countdownSeconds: Int? = null,
        val onCountdownFinished: (() -> Unit)? = null,
    ) : UiEvent

    @Keep
    data class PlayEffect(val fileName: String) : UiEvent

    // ==========

    /**
     * Extra data for navigation.
     * Key-value pairs where value can be Parcelable, String, or other serializable types.
     */
    @Keep
    data class NavExtras(val data: Map<String, Any> = emptyMap()) {
        constructor(vararg pairs: Pair<String, Any>) : this(pairs.toMap())
    }
}
