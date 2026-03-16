@file:Suppress("unused")

package com.leovp.mvvm

import com.leovp.log.base.i
import com.leovp.log.base.userOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * DO NOT forget to enable LifecycleAware component to use countdown feature.
 * ```
 * // In your screen file, add the following line:
 * LifecycleViewModelBridge(viewModel)
 * ```
 *
 * Author: Michael Leo
 * Date: 2025/11/18 11:01
 */
class ScreenCountdownManager(
    private val scope: CoroutineScope,
    private val tag: String,
    val countdownDurationMillis: Long,
    val warningThresholdMillis: Long,
    private val enableAutoReset: Boolean = true,
    private val enableWarning: Boolean = false,
) {
    data class CountdownParam(
        val countdownDurationMillis: Long,
        val warningThresholdMillis: Long,
        val enableAutoReset: Boolean,
        val enableWarning: Boolean,
    ) {
        companion object {
            val DEFAULT = CountdownParam(
                countdownDurationMillis = 60_000L,
                warningThresholdMillis = 10_000L,
                enableAutoReset = true,
                enableWarning = false,
            )
        }
    }

    private var countdownJob: Job? = null

    private val _countdownState = MutableStateFlow(CountdownState())

    @Suppress("UNUSED_PARAMETER")
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    private val _countdownEffect = MutableSharedFlow<CountdownEffect>()

    /**
     * Example:
     * ```
     * @Composable
     * fun CountdownHandler(
     *     // interactionSource: MutableInteractionSource,
     *     viewModel: BaseViewModel<*, *>,
     *     onCountdownEvent: (CountdownEffect) -> Unit,
     * ) {
     *     val tag = viewModel.getTagName()
     *     // DispatchTouchEvent(interactionSource, viewModel)
     *     LaunchedEffect(Unit) {
     *         viewModel.requiredScreenCountdownComponent.countdownEffect.collect { effect ->
     *             onCountdownEvent(effect)
     *         }
     *     }
     *     // val countdownState by viewModel
     *     //     .requiredScreenCountdownComponent
     *     //     .countdownState
     *     //     .collectAsStateWithLifecycle()
     *     // countdownState.let {
     *     //     it.remainingSeconds?.let { sec ->
     *     //         d(tag) { "--> Screen countdown remaining: ${sec}s" }
     *     //     }
     *     // }
     * }
     * ```
     */
    val countdownEffect: SharedFlow<CountdownEffect> = _countdownEffect.asSharedFlow()

    fun handleCountdownEvent(event: CountdownEvent) {
        when (event) {
            is CountdownEvent.Start -> startCountdown()
            is CountdownEvent.Stop -> stopCountdown()
            is CountdownEvent.Reset -> resetCountdown()
            is CountdownEvent.OnUserInteraction -> {
                if (enableAutoReset) {
                    userOp(tag) { "OnUserInteraction resetCountdown" }
                    resetCountdown(false)
                }
            }
        }
    }

    private fun startCountdown() {
        i(tag) { "=====> startCountdown() <=====" }
        stopCountdown()

        countdownJob = scope.launch {
            var remaining = countdownDurationMillis
            // d(tag) { "-----> Screen countdown remaining=${remaining.div(1000)}s" }
            _countdownState.update {
                it.copy(
                    remainingTimeMillis = remaining,
                    isCountingDown = true
                )
            }

            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                // d(tag) { "-----> Screen countdown remaining=${remaining.div(1000)}s" }

                _countdownState.update {
                    it.copy(remainingTimeMillis = remaining)
                }

                if (enableWarning && remaining == warningThresholdMillis) {
                    // d(tag) { "-----> Countdown warning=${remaining.div(1000)}s" }
                    _countdownEffect.emit(
                        CountdownEffect.ShowWarning(remaining / 1000)
                    )
                }
            }

            _countdownState.update {
                it.copy(
                    remainingTimeMillis = null,
                    isCountingDown = false
                )
            }

            _countdownEffect.emit(CountdownEffect.Timeout)
            // setOnCountdownTimeout?.invoke()
        }
    }

    private fun stopCountdown() {
        i(tag) { "=====> stopCountdown() <=====" }
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun resetCountdown(showLog: Boolean = true) {
        if (showLog) {
            i(tag) { "=====> resetCountdown() <=====" }
        }
        startCountdown()
    }

    data class CountdownState(
        val remainingTimeMillis: Long? = null,
        val isCountingDown: Boolean = false,
    ) {
        @Suppress("UNUSED_PARAMETER")
        val remainingSeconds: Long?
            get() = remainingTimeMillis?.div(1000)
    }

    sealed class CountdownEvent {
        data object Start : CountdownEvent()
        data object Stop : CountdownEvent()
        data object Reset : CountdownEvent()
        data object OnUserInteraction : CountdownEvent()
    }

    sealed class CountdownEffect {
        object Timeout : CountdownEffect()
        data class ShowWarning(val secondsRemaining: Long) : CountdownEffect()
    }
}
