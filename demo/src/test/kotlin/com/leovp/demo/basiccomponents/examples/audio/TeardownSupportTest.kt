package com.leovp.demo.basiccomponents.examples.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class TeardownSupportTest {
    @Test
    fun `every step runs and the first failure wins`() = runTest {
        val ran = mutableListOf<String>()
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")

        val thrown = runCatching {
            teardownSteps(
                { ran += "a" },
                {
                    ran += "b"
                    throw first
                },
                {
                    ran += "c"
                    throw second
                },
                { ran += "d" }
            )
        }.exceptionOrNull()

        // Every step ran: that is the whole reason teardownSteps() exists.
        assertContentEquals(listOf("a", "b", "c", "d"), ran)
        // The root cause is what propagates; the later failure rides along as suppressed.
        assertSame(first, thrown)
        assertContentEquals(listOf(second), thrown?.suppressedExceptions)
    }

    @Test
    fun `two steps reporting one instance do not abort the run`() = runTest {
        val ran = mutableListOf<String>()
        val shared = IllegalStateException("shared")

        val thrown = runCatching {
            teardownSteps(
                { throw shared },
                {
                    ran += "b"
                    throw shared
                },
                { ran += "c" }
            )
        }.exceptionOrNull()

        // Without the identity guard, addSuppressed(itself) throws IllegalArgumentException out
        // of the catch and step "c" never runs.
        assertContentEquals(listOf("b", "c"), ran)
        assertSame(shared, thrown)
        assertContentEquals(emptyList(), thrown?.suppressedExceptions)
    }

    @Test
    fun `nothing is thrown when every step succeeds`() = runTest {
        val ran = mutableListOf<String>()

        teardownSteps({ ran += "a" }, { ran += "b" })

        assertContentEquals(listOf("a", "b"), ran)
    }

    @Test
    fun `containsJob accepts children and grandchildren but not strangers`() = runTest {
        val owner = Job()
        val scope = CoroutineScope(owner)
        val child = CompletableDeferred<Job>()
        val grandChild = CompletableDeferred<Job>()
        val hold = CompletableDeferred<Unit>()

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            child.complete(currentCoroutineContext()[Job]!!)
            launch(start = CoroutineStart.UNDISPATCHED) {
                grandChild.complete(currentCoroutineContext()[Job]!!)
                hold.await()
            }
            hold.await()
        }

        // The identity check this replaced would fail both of these: launch() never hands back
        // the scope's own Job, so `caller !== ownerJob` passes for exactly the callers that
        // deadlock on cancelAndJoin().
        assertTrue(owner.containsJob(child.await()))
        assertTrue(owner.containsJob(grandChild.await()))
        assertFalse(owner.containsJob(Job()))

        hold.complete(Unit)
        owner.cancelAndJoin()
    }
}
