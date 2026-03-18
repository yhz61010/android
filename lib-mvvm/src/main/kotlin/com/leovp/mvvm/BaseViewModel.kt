@file:Suppress("unused")

package com.leovp.mvvm

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.Navigator
import com.leovp.log.base.d
import com.leovp.log.base.i
import com.leovp.mvvm.ScreenCountdownManager.CountdownEvent
import com.leovp.mvvm.event.base.UiEvent
import com.leovp.mvvm.event.base.UiEventManager
import com.leovp.mvvm.viewmodel.lifecycle.LifecycleAware
import kotlin.properties.Delegates
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Note that: If you don't need the countdown timer reset functionality,
 * set `countdownParam = null` in the parent class constructor within the subclass.
 * Or, using the predefined parameter `ScreenCountdownManager.CountdownParam.DEFAULT` if you wish.
 *
 *
 * Author: Michael Leo
 * Date: 2025/7/7 10:55
 */
abstract class BaseViewModel<State : BaseState, Action : BaseAction<State>>(
    initialState: State,
    val uiEventManager: UiEventManager? = null,
    countdownParam: ScreenCountdownManager.CountdownParam? = null,
) : ViewModel(), LifecycleAware {

    abstract fun getTagName(): String

    @Suppress("WeakerAccess")
    protected val tag: String by lazy { getTagName() }

    private val _uiStateFlow = MutableStateFlow(initialState)
    val uiStateFlow = _uiStateFlow.asStateFlow()

    private val _isDialogShowing = MutableStateFlow(false)
    val isDialogShowing = _isDialogShowing.asStateFlow()

    private var stateDebugger: StateTimeTravelDebugger? = null

    /**
     * Enable for debugging purposes in each view model.
     * ```
     * if (BuildConfig.DEBUG) {
     *     enableStateDebugger()
     * }
     * ```
     */
    fun enableStateDebugger() {
        stateDebugger = StateTimeTravelDebugger(this::class.java.simpleName)
    }

    /**
     * DO NOT forget to enable LifecycleAware component to use this feature.
     * ```
     * // In your screen file, add the following line:
     * LifecycleViewModelBridge(viewModel)
     * ```
     */
    var screenCountdownComponent: ScreenCountdownManager? = null

    val requiredScreenCountdownComponent get() = requireNotNull(screenCountdownComponent)

    // init {
    //     @Suppress("SENSELESS_COMPARISON")
    //     if (BuildConfig.DEBUG) {
    //         stateDebugger = StateTimeTravelDebugger(this::class.java.simpleName)
    //     }
    // }

    init {
        if (countdownParam != null) {
            screenCountdownComponent = ScreenCountdownManager(
                scope = viewModelScope,
                tag = tag,
                countdownDurationMillis = countdownParam.countdownDurationMillis,
                warningThresholdMillis = countdownParam.warningThresholdMillis,
                enableAutoReset = countdownParam.enableAutoReset,
            )

            // observeCountdownEffects()
        }
    }

    private var state by Delegates.observable(initialState) { _, old, new ->
        if (old != new) {
            viewModelScope.launch {
                _uiStateFlow.value = new
            }

            stateDebugger?.apply {
                addStateTransition(old, new)
                logLast()
            }
        }
    }

    fun resetTimer() {
        screenCountdownComponent?.handleCountdownEvent(CountdownEvent.Reset)
    }

    fun executeResetTimerByInteraction() {
        screenCountdownComponent?.handleCountdownEvent(CountdownEvent.OnUserInteraction)
    }

    // private fun observeCountdownEffects() {
    //     viewModelScope.launch {
    //         screenCountdownComponent?.countdownEffect?.collect { effect ->
    //             when (effect) {
    //                 is CountdownEffect.Timeout -> {
    //                     d(tag) { "Show Timeout on screen" }
    //                     onCountdownTimeout()
    //                 }
    //
    //                 is CountdownEffect.ShowWarning -> {
    //                     d(tag) { "Show ShowWarning on screen" }
    //                 }
    //             }
    //         }
    //     }
    // }

    // protected open fun onCountdownTimeout() {
    //     i(tag) { "=====> onCountdownTimeout() <===== Back to Preview" }
    // }

    override fun onStart() {
        i(tag) { "=====> onStart() <=====" }
        super.onStart()
    }

    override fun onResume() {
        i(tag) { "=====> onResume() <=====" }
        super.onResume()
        screenCountdownComponent?.handleCountdownEvent(CountdownEvent.Start)
    }

    override fun onPause() {
        i(tag) { "=====> onPause() <=====" }
        screenCountdownComponent?.handleCountdownEvent(CountdownEvent.Stop)
        super.onPause()
    }

    override fun onStop() {
        i(tag) { "=====> onStop() <=====" }
        super.onStop()
    }

    override fun onDestroy() {
        i(tag) { "=====> onDestroy() <=====" }
        super.onDestroy()
    }

    // ===== UI Event - Start ==========
    val uiEvents: Flow<UiEvent>? = uiEventManager?.events
    val requireUiEvents: Flow<UiEvent> by lazy {
        uiEvents ?: throw NullPointerException("uiEventManager is null")
    }

    fun showToast(
        message: String? = null,
        @StringRes resId: Int? = null,
        isError: Boolean = false,
        longDuration: Boolean = true,
        isDebug: Boolean = false,
        origin: Boolean = false,
    ) {
        viewModelScope.launch {
            uiEventManager?.sendEvent(
                UiEvent.ShowToast(
                    message = message,
                    resId = resId,
                    isError = isError,
                    longDuration = longDuration,
                    isDebug = isDebug,
                    origin = origin
                )
            )
        }
    }

    fun showSnackbar(
        message: String? = null,
        @StringRes resId: Int? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            uiEventManager?.sendEvent(
                UiEvent.ShowSnackbar(
                    message = message,
                    resId = resId,
                    actionLabel = actionLabel,
                    onAction = onAction
                ),
            )
        }
    }

    fun navigateString(
        route: String,
        arguments: String? = null,
        extras: UiEvent.NavExtras? = null,
    ) {
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.NavigateString(route, arguments, extras))
        }
    }

    fun <T : Any> navigate(route: T, builder: NavOptionsBuilder.() -> Unit) {
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.NavigateRouteBuilder(route, builder))
        }
    }

    fun <T : Any> navigate(
        route: T,
        navOptions: NavOptions? = null,
        navigatorExtras: Navigator.Extras? = null,
    ) {
        viewModelScope.launch {
            uiEventManager?.sendEvent(
                UiEvent.Navigate(
                    route,
                    navOptions,
                    navigatorExtras
                )
            )
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.NavigateBack)
        }
    }

    fun finishActivity() {
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.FinishActivity)
        }
    }

    fun hideNavigationBar() {
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.NavigationBar(false))
        }
    }

    fun showLoading() {
        d(tag) { "showLoading()" }
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.ShowLoading)
        }
    }

    fun hideLoading() {
        d(tag) { "hideLoading()" }
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.HideLoading)
        }
    }

    @Suppress("LongParameterList")
    fun showDialog(
        @DrawableRes iconResId: Int? = null,

        title: String? = null,
        message: String? = null,
        positiveButtonText: String? = null,
        negativeButtonText: String? = null,

        @StringRes titleResId: Int? = null,
        @StringRes messageResId: Int? = null,
        @StringRes positiveButtonTextResId: Int? = null,
        @StringRes negativeButtonTextResId: Int? = null,

        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null,
        countdownSeconds: Int? = null,
        onCountdownFinished: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _isDialogShowing.value = true
            uiEventManager?.sendEvent(
                UiEvent.ShowDialog(
                    iconResId = iconResId,

                    title = title,
                    message = message,
                    positiveButtonText = positiveButtonText,
                    negativeButtonText = negativeButtonText,

                    titleResId = titleResId,
                    messageResId = messageResId,
                    positiveButtonTextResId = positiveButtonTextResId,
                    negativeButtonTextResId = negativeButtonTextResId,

                    onPositive = {
                        hideNavigationBar()
                        _isDialogShowing.value = false
                        onPositive()
                    },
                    onNegative = {
                        hideNavigationBar()
                        _isDialogShowing.value = false
                        onNegative?.invoke()
                    },
                    countdownSeconds = countdownSeconds,
                    onCountdownFinished = onCountdownFinished?.let {
                        {
                            hideNavigationBar()
                            _isDialogShowing.value = false
                            it()
                        }
                    }
                ),
            )
        }
    }

    fun dismissDialog() {
        viewModelScope.launch {
            _isDialogShowing.value = false
            uiEventManager?.sendEvent(UiEvent.DismissDialog)
        }
    }

    fun playEffect(fileName: String) {
        viewModelScope.launch {
            uiEventManager?.sendEvent(UiEvent.PlayEffect(fileName))
        }
    }
    // ===== UI Event - End ==========

    override fun onCleared() {
        i(tag) { "=====> onCleared() <=====" }
        screenCountdownComponent?.handleCountdownEvent(CountdownEvent.Stop)
        super.onCleared()
    }

    fun sendAction(action: BaseAction.Simple<State>) {
        val cost = measureTimeMillis {
            stateDebugger?.addAction(action)
            state = action.reduce(state)
        }
        d(tag) { "---> sendAction cost=${cost}ms" }
    }

    fun <T : Any> sendAction(action: BaseAction.WithExtra<State, T>, obj: T) {
        val cost = measureTimeMillis {
            stateDebugger?.addAction(action)
            state = action.reduce(state, obj)
        }
        d(tag) { "---> sendAction cost=${cost}ms" }
    }

    fun <T> sendAction(action: BaseAction.WithOptional<State, T>, obj: T? = null) {
        val cost = measureTimeMillis {
            stateDebugger?.addAction(action)
            state = action.reduce(state, obj)
        }
        d(tag) { "---> sendAction cost=${cost}ms" }
    }
}
