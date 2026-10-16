package com.leovp.demo.basiccomponents.examples.audio

import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * One-shot teardown coordination for one owner instance.
 *
 * This gate is deliberately not resettable. AudioSender and AudioReceiver are single-session
 * owners: every new start creates a new owner and therefore a new gate. Reusing either owner for
 * another start/stop cycle would make later stop calls observe the first result without running
 * cleanup and is outside this contract.
 *
 * Followers await the same completion and receive the very Throwable instance the leader threw
 * out of [run].
 *
 * Cancellation is the exception, and the asymmetry is deliberate: the body runs under
 * [NonCancellable], so a CancellationException leaving it is a real teardown failure rather than
 * a cancelled caller - but republished into an unrelated follower it would read as "you were
 * cancelled" and be swallowed without a trace. The leader therefore logs it and rethrows, while
 * followers are told the teardown completed. Worth knowing at the call sites: after such a
 * failure a follower - and AudioActivity.launchCleanup - reports success, and the leader's log
 * line is the only record of it.
 *
 * There is no gate timeout: timing out here would let a caller proceed while
 * cleanup was still mutating resources. Every operation inside the block must therefore be
 * bounded itself; a stuck operation stalls all followers and must be diagnosed at that resource
 * boundary.
 *
 * Not re-entrant: the block must not call [run] or [awaitCompletion] synchronously on its own
 * coroutine - `started` is already set, so it would await its own completion for good. A callback
 * fired from inside the teardown that wants a teardown launches it on another scope, which is
 * what every current call site does.
 */
internal class SuspendTeardownGate {
    companion object {
        private const val TAG = "TeardownGate"
    }

    private val started = AtomicBoolean(false)
    private val completed = CompletableDeferred<Throwable?>()

    /**
     * Suspends until the teardown has run to completion, without becoming its leader and without
     * rethrowing its failure.
     *
     * For code that lost a race with [run]: it built a resource after the teardown had already
     * read the field as absent, so it has to release that resource itself. Waiting here first
     * keeps that release from overlapping the teardown's own steps. Waits forever if nobody ever
     * calls [run]; callers check the owner's `stopping` flag before coming here.
     */
    suspend fun awaitCompletion() {
        completed.await()
    }

    suspend fun run(block: suspend () -> Unit) {
        if (!started.compareAndSet(false, true)) {
            completed.await()?.let { throw it }
            return
        }
        var failure: Throwable? = null
        try {
            withContext(NonCancellable) { block() }
        } catch (cancellation: CancellationException) {
            // Not published to followers: thrown into an unrelated follower coroutine it reads as
            // "this caller was cancelled" and is swallowed - AudioActivity.launchCleanup rethrows
            // that branch without a log, and a launch on cleanupScope never routes it to the
            // exception handler. But it is not a cancelled caller here either: the body runs
            // under NonCancellable, so this is a real teardown failure that
            // StreamPlayerStopper.stopAndJoin() or MicRecorder.finishRecorderReleaseAndJoin()
            // rethrew on purpose. Log it or it leaves no trace anywhere.
            LogContext.log.e(TAG, "Teardown body was cancelled", cancellation)
            throw cancellation
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            completed.complete(failure)
        }
    }
}
