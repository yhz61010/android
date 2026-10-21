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
class ScreenCountdownManager internal constructor(
    private val scope: CoroutineScope,
    private val tag: String,
    val countdownDurationMillis: Long,
    val warningThresholdMillis: Long,
    private val enableAutoReset: Boolean = true,
    private val enableWarning: Boolean = false,
    private val nowNanos: () -> Long,
) {
    constructor(
        scope: CoroutineScope,
        tag: String,
        countdownDurationMillis: Long,
        warningThresholdMillis: Long,
        enableAutoReset: Boolean = true,
        enableWarning: Boolean = false,
    ) : this(
        scope,
        tag,
        countdownDurationMillis,
        warningThresholdMillis,
        enableAutoReset,
        enableWarning,
        System::nanoTime
    )

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
        (nowNanos() - originNanos) / 1_000_000L

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

            // The monotonic clock schedules logical tick deadlines, so work performed in the
            // loop cannot accumulate delay. Published values come from the tick index itself:
            // an early clock reading may delay the next tick, but cannot repeat the prior value.
            val startedAtNanos = nowNanos()
            val tickMillis = TICK_INTERVAL.inWholeMilliseconds
            var nextTickAtMillis = 0L
            var warningEmitted = false

            while (remaining > 0) {
                nextTickAtMillis += tickMillis
                val waitMillis = nextTickAtMillis - elapsedMillisSince(startedAtNanos)
                if (waitMillis > 0) delay(waitMillis.milliseconds)
                remaining = (countdownDurationMillis - nextTickAtMillis).coerceAtLeast(0L)
                // d(tag) { "-----> Screen countdown remaining=${remaining.div(1000)}s" }

                _countdownState.update {
                    it.copy(remainingTimeMillis = remaining)
                }

                // A crossing, not an equality, supports thresholds that are not exact tick
                // multiples. The one-shot flag guarantees a single warning.
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
