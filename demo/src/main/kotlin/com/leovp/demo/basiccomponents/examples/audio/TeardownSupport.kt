package com.leovp.demo.basiccomponents.examples.audio

import kotlinx.coroutines.Job

/**
 * Runs every teardown step even if an earlier one fails, then rethrows the first failure with
 * the later ones attached as suppressed.
 *
 * Nested try/finally would do the first half only: a throwing finally discards the throwable
 * the try had already raised, so the gate would publish a late, unrelated failure to its
 * followers while the root cause vanished.
 *
 * Each step must do exactly one thing. Two actions in one lambda share one try, so a failure in
 * the first silently skips the second - which is the behaviour this helper exists to prevent.
 */
internal suspend fun teardownSteps(vararg steps: suspend () -> Unit) {
    var failure: Throwable? = null
    for (step in steps) {
        try {
            step()
        } catch (e: Throwable) {
            val first = failure
            if (first == null) {
                failure = e
            } else if (first !== e) {
                // Guarded: addSuppressed(itself) throws IllegalArgumentException, and that
                // throwable would escape this catch and skip every remaining step - exactly
                // what this helper exists to prevent. Two steps report one instance when a
                // resource stores its failure and republishes it to every caller.
                first.addSuppressed(e)
            }
        }
    }
    failure?.let { throw it }
}

/**
 * True when [target] is a descendant of this job. A job does not contain itself.
 *
 * A teardown entry point that cancel-and-joins its own scope must reject callers running
 * anywhere inside that scope, not just callers holding the scope's own Job - launch() always
 * hands a child Job, so an identity check would never fire. Mirrors the private helper in
 * StreamPlayerStopper.
 */
internal fun Job.containsJob(target: Job): Boolean =
    children.any { child -> child === target || child.containsJob(target) }
