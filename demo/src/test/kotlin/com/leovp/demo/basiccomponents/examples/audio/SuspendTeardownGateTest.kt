package com.leovp.demo.basiccomponents.examples.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SuspendTeardownGateTest {
    @Test
    fun `concurrent callers share one teardown`() = runTest {
        val gate = SuspendTeardownGate()
        val entered = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        var calls = 0

        val first = async {
            gate.run {
                calls += 1
                entered.complete(Unit)
                allowCompletion.await()
            }
        }
        entered.await()
        val second = async { gate.run { error("must not run twice") } }
        runCurrent()

        assertFalse(second.isCompleted)
        allowCompletion.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, calls)
    }

    @Test
    fun `failure is shared with later callers`() = runTest {
        val gate = SuspendTeardownGate()
        val expected = IllegalStateException("teardown failed")

        val first = runCatching { gate.run { throw expected } }.exceptionOrNull()
        val second = runCatching { gate.run { error("must not run twice") } }.exceptionOrNull()

        // Not assertSame(expected, first): with kotlinx.coroutines debug mode on - which the
        // Gradle test JVM switches on through -ea - withContext() rethrows a stack-trace-recovered
        // copy whose cause is the original. The contract is that followers get the very instance
        // the leader threw out of run(), and that holds either way.
        val leaderFailure = assertIs<IllegalStateException>(first)
        assertEquals(expected.message, leaderFailure.message)
        assertSame(leaderFailure, second)
        // A failed teardown is still a completed one, and waiting for it must not rethrow.
        gate.awaitCompletion()
    }

    @Test
    fun `awaitCompletion waits for the leader without becoming one`() = runTest {
        val gate = SuspendTeardownGate()
        val allowCompletion = CompletableDeferred<Unit>()
        var calls = 0

        val waiter = async { gate.awaitCompletion() }
        runCurrent()
        assertFalse(waiter.isCompleted)

        val leader = async {
            gate.run {
                calls += 1
                allowCompletion.await()
            }
        }
        runCurrent()
        assertFalse(waiter.isCompleted)

        allowCompletion.complete(Unit)
        leader.await()
        waiter.await()
        assertEquals(1, calls)
    }

    @Test
    fun `caller cancellation does not interrupt active teardown`() = runTest {
        val gate = SuspendTeardownGate()
        val entered = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()

        val first = launch {
            gate.run {
                entered.complete(Unit)
                allowCompletion.await()
                finished.complete(Unit)
            }
        }
        entered.await()
        first.cancel()
        val waiter = async { gate.run { error("must not run twice") } }
        runCurrent()

        assertFalse(waiter.isCompleted)
        allowCompletion.complete(Unit)
        waiter.await()
        first.join()
        assertTrue(finished.isCompleted)
    }

    @Test
    fun `cancellation from the teardown body is not shared with later callers`() = runTest {
        val gate = SuspendTeardownGate()

        // StreamPlayerStopper.stopAndJoin() and MicRecorder.finishRecorderReleaseAndJoin() both
        // rethrow CancellationException on purpose, so a teardown body really can end this way.
        val first = runCatching {
            gate.run { throw CancellationException("teardown cancelled") }
        }.exceptionOrNull()
        val second = runCatching { gate.run { error("must not run twice") } }.exceptionOrNull()

        assertIs<CancellationException>(first)
        assertNull(second)
    }
}
