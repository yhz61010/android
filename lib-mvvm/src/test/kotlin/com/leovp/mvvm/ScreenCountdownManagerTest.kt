package com.leovp.mvvm

import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenCountdownManagerTest {
    @Test
    fun `sixty second countdown publishes every tick and emits each effect once`() = runTest {
        val manager = ScreenCountdownManager(
            scope = backgroundScope,
            tag = "test",
            countdownDurationMillis = 60_000L,
            warningThresholdMillis = 10_000L,
            enableWarning = true,
            nowNanos = { testScheduler.currentTime * 1_000_000L }
        )
        val values = mutableListOf<Long>()
        val effects = mutableListOf<ScreenCountdownManager.CountdownEffect>()
        val stateCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.countdownState
                .map { it.remainingTimeMillis }
                .filterNotNull()
                .take(61)
                .toList(values)
        }
        val effectCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.countdownEffect.take(2).toList(effects)
        }

        manager.handleCountdownEvent(ScreenCountdownManager.CountdownEvent.Start)
        advanceUntilIdle()
        stateCollector.join()
        effectCollector.join()

        assertEquals((60 downTo 0).map { it * 1_000L }, values)
        assertEquals(
            listOf(
                ScreenCountdownManager.CountdownEffect.ShowWarning(10),
                ScreenCountdownManager.CountdownEffect.Timeout
            ),
            effects
        )
    }

    @Test
    fun `countdown publishes one value per logical tick even after an early wakeup`() = runTest {
        var nowMillis = 0L
        val manager = ScreenCountdownManager(
            scope = backgroundScope,
            tag = "test",
            countdownDurationMillis = 3_000L,
            warningThresholdMillis = 1_000L,
            nowNanos = { nowMillis * 1_000_000L }
        )
        val values = mutableListOf<Long>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.countdownState
                .map { it.remainingTimeMillis }
                .filterNotNull()
                .take(4)
                .collect(values::add)
        }

        manager.handleCountdownEvent(ScreenCountdownManager.CountdownEvent.Start)
        nowMillis = 999L
        testScheduler.advanceTimeBy(1_000L)
        nowMillis = 2_000L
        testScheduler.advanceTimeBy(1_001L)
        nowMillis = 3_000L
        advanceUntilIdle()
        collector.join()

        assertEquals(listOf(3_000L, 2_000L, 1_000L, 0L), values)
    }
}
