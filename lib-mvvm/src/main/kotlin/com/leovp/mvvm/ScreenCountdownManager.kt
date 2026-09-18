@file:Suppress("unused")

package com.leovp.mvvm

import com.leovp.log.base.i
import com.leovp.log.base.userOp
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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
                enableWarning = false
            )
        }
    }

    private companion object {
        /** Countdown tick. Backs both the delay and the millisecond decrement below. */
        private val TICK_INTERVAL = 1.seconds
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

    /** Milliseconds of monotonic time since [originNanos]. */
    private fun elapsedMillisSince(originNanos: Long): Long =
        (System.nanoTime() - originNanos) / 1_000_000L

    /**
     * What is left of the countdown, rounded up to a whole tick.
     *
     * Rounding up keeps the exposed value stepping 60000, 59000, 58000 the way the old counter
     * did. Reporting the raw remainder would hand the UI 59997 one tick in, and a caller
     * dividing by 1000 would show a second less than it should.
     */
    private fun remainingMillisAt(startedAtNanos: Long): Long {
        val tickMillis = TICK_INTERVAL.inWholeMilliseconds
        val raw = (countdownDurationMillis - elapsedMillisSince(startedAtNanos)).coerceAtLeast(0L)
        return (raw + tickMillis - 1) / tickMillis * tickMillis
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

            // Both the deadline and the tick boundaries come from a monotonic clock. Subtracting
            // a flat TICK_INTERVAL from a counter assumed each pass through this loop took
            // exactly that long, but the state update and the (suspending) effect emit below
            // cost time on top of the delay, and nothing ever gave it back. The countdown
            // finished progressively later than its own duration, and the value handed to the
            // UI drifted away from the wall clock with it.
            val startedAtNanos = System.nanoTime()
            val tickMillis = TICK_INTERVAL.inWholeMilliseconds
            var nextTickAtMillis = 0L
            var warningEmitted = false

            while (remaining > 0) {
                nextTickAtMillis += tickMillis
                val waitMillis = nextTickAtMillis - elapsedMillisSince(startedAtNanos)
                if (waitMillis > 0) delay(waitMillis.milliseconds)
                remaining = remainingMillisAt(startedAtNanos)
                // d(tag) { "-----> Screen countdown remaining=${remaining.div(1000)}s" }

                _countdownState.update {
                    it.copy(remainingTimeMillis = remaining)
                }

                // A crossing, not an equality. `remaining` now comes from a clock, so it lands on
                // the threshold only by accident; `<=` plus a one-shot flag fires exactly once.
                // The emitted value is the threshold rather than `remaining`, which is what the
                // equality used to guarantee and what a caller configured.
                if (enableWarning && !warningEmitted && remaining <= warningThresholdMillis) {
                    warningEmitted = true
                    // d(tag) { "-----> Countdown warning=${warningThresholdMillis / 1000}s" }
                    _countdownEffect.emit(
                        CountdownEffect.ShowWarning(warningThresholdMillis / 1000)
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
